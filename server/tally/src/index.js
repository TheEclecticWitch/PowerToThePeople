/**
 * Counts how app users answered "How would you have voted?" on each roll call. Anonymous and opt-in:
 * the app sends an answer only when the reader ticks "add my answer to the app-wide count".
 *
 *   GET  /tally?vote=119/senate/2/256        -> {"vote":..., "total":12, "yea":7, "nay":5}
 *   GET  /tally?vote=bill/119/hr/1           -> the same, for support (yea) and opposition (nay) to a bill
 *   GET  /tally?votes=119/senate/2/256,...   -> {"tallies":[...]} (up to 50 at once)
 *   POST /answer {"vote":..., "install":"<random code>", "answer":"Yea"|"Nay"|null}
 *   GET  /state-bills?state=md&q=paid leave  -> a state's bills matching words or a number ("HB 123"),
 *        from Open States. The Open States key stays here as a secret, never in the app.
 *   GET  /state-bill?id=ocd-bill/...         -> one state bill: its actions, sponsors and every recorded vote with
 *        how each legislator voted, from Open States.
 *   GET  /lobbying?bill=119/s/2403            -> lobbying reports (Lobbying Disclosure Act) filed during that
 *        Congress that name the bill, from lda.gov.
 *   GET  /elections                          -> upcoming elections (Google Civic Information)
 *   POST /voter-info {"address", "electionId"?} -> polling places, ballot contests and the state's official
 *        election links for an address. The address is passed to Google and not kept.
 *
 * Counts under MIN_SHOWN are reported as a total only: with three answers, someone who knows who uses the
 * app could guess how each of them voted.
 */

const MIN_SHOWN = 10;
// A roll call ("119/senate/2/256") or a bill itself ("bill/119/hr/1"). On a bill, Yea means the reader
// supports it and Nay that they oppose it.
const VOTE_KEY = /^(1\d\d\/(house|senate)\/[12]\/\d{1,4}|bill\/1\d\d\/(hr|s|hres|sres|hjres|sjres|hconres|sconres)\/\d{1,5})$/;
const INSTALL = /^[0-9a-f-]{32,40}$/i;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/tally") return await readTallies(url, env);
      if (request.method === "POST" && url.pathname === "/answer") return await recordAnswer(request, env);
      if (request.method === "GET" && url.pathname === "/state-bills") return await stateBills(request, url, env, ctx);
      if (request.method === "GET" && url.pathname === "/state-bill") return await stateBill(request, url, env, ctx);
      if (request.method === "GET" && url.pathname === "/lobbying") return await lobbying(request, url, env, ctx);
      if (request.method === "GET" && url.pathname === "/elections") return await elections(env, ctx);
      if (request.method === "POST" && url.pathname === "/voter-info") return await voterInfo(request, env);
      if (request.method === "GET" && url.pathname === "/") {
        return json({ service: "Power to the People vote counts", source: "https://github.com/TheEclecticWitch/PowerToThePeople" });
      }
      return json({ error: "not found" }, 404);
    } catch (e) {
      return json({ error: "server error" }, 500);
    }
  },
};

async function readTallies(url, env) {
  const one = url.searchParams.get("vote");
  const many = url.searchParams.get("votes");
  const keys = (many ? many.split(",") : [one]).filter((k) => k && VOTE_KEY.test(k)).slice(0, 50);
  if (keys.length === 0) return json({ error: "no valid vote" }, 400);
  const marks = keys.map(() => "?").join(",");
  const { results } = await env.DB.prepare(`SELECT vote, yea, nay FROM totals WHERE vote IN (${marks})`).bind(...keys).all();
  const found = new Map(results.map((r) => [r.vote, r]));
  const tallies = keys.map((k) => shown(k, found.get(k)));
  return json(many ? { tallies, minShown: MIN_SHOWN } : { ...tallies[0], minShown: MIN_SHOWN }, 200, true);
}

function shown(vote, row) {
  const yea = row?.yea ?? 0;
  const nay = row?.nay ?? 0;
  const total = yea + nay;
  return total >= MIN_SHOWN ? { vote, total, yea, nay } : { vote, total };
}

async function recordAnswer(request, env) {
  // Twenty answers a minute per address is plenty for a person and slows down anyone stuffing the count.
  // The address is used only for this check, by Cloudflare, and is never stored.
  if (env.LIMIT) {
    const ip = request.headers.get("CF-Connecting-IP") || "unknown";
    const { success } = await env.LIMIT.limit({ key: ip });
    if (!success) return json({ error: "too many answers, try again in a minute" }, 429);
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad request" }, 400);
  }
  const { vote, install, answer } = body ?? {};
  if (!VOTE_KEY.test(vote ?? "") || !INSTALL.test(install ?? "") || !(answer === "Yea" || answer === "Nay" || answer === null)) {
    return json({ error: "bad request" }, 400);
  }
  const voter = await sha256(`${env.SALT}|${vote}|${install}`);
  const before = await env.DB.prepare("SELECT answer FROM answers WHERE vote = ? AND voter = ?").bind(vote, voter).first();
  const old = before?.answer ?? null;
  if (old !== answer) {
    const steps = [env.DB.prepare("INSERT OR IGNORE INTO totals (vote) VALUES (?)").bind(vote)];
    if (old) steps.push(env.DB.prepare(`UPDATE totals SET ${col(old)} = ${col(old)} - 1 WHERE vote = ?`).bind(vote));
    if (answer) {
      steps.push(env.DB.prepare(`UPDATE totals SET ${col(answer)} = ${col(answer)} + 1 WHERE vote = ?`).bind(vote));
      steps.push(env.DB.prepare("INSERT INTO answers (vote, voter, answer) VALUES (?, ?, ?) ON CONFLICT (vote, voter) DO UPDATE SET answer = excluded.answer").bind(vote, voter, answer));
    } else {
      steps.push(env.DB.prepare("DELETE FROM answers WHERE vote = ? AND voter = ?").bind(vote, voter));
    }
    await env.DB.batch(steps);
  }
  const row = await env.DB.prepare("SELECT vote, yea, nay FROM totals WHERE vote = ?").bind(vote).first();
  return json({ ...shown(vote, row), minShown: MIN_SHOWN });
}

const col = (answer) => (answer === "Yea" ? "yea" : "nay");

async function sha256(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function json(data, status = 200, cache = false) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": cache ? "public, max-age=60" : "no-store",
    },
  });
}

// --- State bills, searched live at Open States (v3.openstates.org) ---

const STATE = /^[a-z]{2}$/;
const BILL_NUMBER = /^\s*([a-z]{1,4})\.?\s*(\d{1,5})\s*$/i;

async function stateBills(request, url, env, ctx) {
  const state = (url.searchParams.get("state") || "").toLowerCase();
  const q = (url.searchParams.get("q") || "").trim().slice(0, 100);
  const page = Math.min(Math.max(parseInt(url.searchParams.get("page") || "1", 10) || 1, 1), 20);
  if (!STATE.test(state)) return json({ error: "bad request" }, 400);
  if (!env.OPENSTATES_KEY) return json({ error: "state bill search isn't set up yet" }, 503);

  // The same search within six hours is answered from Cloudflare's cache, which keeps well inside
  // Open States' daily allowance however many people ask.
  const cacheKey = new Request(`https://cache.ptp/state-bills?state=${state}&q=${encodeURIComponent(q.toLowerCase())}&page=${page}`);
  const cache = caches.default;
  const hit = await cache.match(cacheKey);
  if (hit) return hit;

  if (env.LIMIT) {
    const { success } = await env.LIMIT.limit({ key: request.headers.get("CF-Connecting-IP") || "unknown" });
    if (!success) return json({ error: "too many searches, try again in a minute" }, 429);
  }

  const api = new URL("https://v3.openstates.org/bills");
  api.searchParams.set("jurisdiction", state);
  api.searchParams.set("sort", "updated_desc");
  api.searchParams.set("per_page", "20");
  api.searchParams.set("page", String(page));
  const number = q.replace(/\./g, "").match(BILL_NUMBER);
  if (number) api.searchParams.set("identifier", `${number[1].toUpperCase()} ${number[2]}`);
  else if (q) api.searchParams.set("q", q);
  const upstream = await fetch(api, { headers: { "X-API-KEY": env.OPENSTATES_KEY } });
  if (upstream.status === 429) return json({ error: "the state bill service has reached its limit for now; try again later" }, 503);
  if (!upstream.ok) return json({ error: "the state bill service didn't answer" }, 502);
  const data = await upstream.json();
  const bills = (data.results || []).map((b) => ({
    id: b.id,
    number: b.identifier,
    title: b.title,
    session: b.session,
    chamber: b.from_organization?.classification ?? null,
    kind: (b.classification || [])[0] ?? null,
    subjects: (b.subject || []).slice(0, 4),
    introduced: b.first_action_date || null,
    latestAction: b.latest_action_description || null,
    latestActionDate: b.latest_action_date || null,
    url: b.openstates_url,
  }));
  const body = {
    state,
    query: q,
    page,
    pages: data.pagination?.max_page ?? 1,
    total: data.pagination?.total_items ?? bills.length,
    bills,
    source: "https://openstates.org/",
  };
  const response = new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=21600" },
  });
  ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

const OCD_BILL = /^ocd-bill\/[0-9a-f-]{36}$/;

async function stateBill(request, url, env, ctx) {
  const id = url.searchParams.get("id") || "";
  if (!OCD_BILL.test(id)) return json({ error: "bad request" }, 400);
  if (!env.OPENSTATES_KEY) return json({ error: "state bills aren't set up yet" }, 503);
  const cacheKey = new Request(`https://cache.ptp/state-bill?id=${id}`);
  const hit = await caches.default.match(cacheKey);
  if (hit) return hit;
  if (env.LIMIT) {
    const { success } = await env.LIMIT.limit({ key: request.headers.get("CF-Connecting-IP") || "unknown" });
    if (!success) return json({ error: "too many requests, try again in a minute" }, 429);
  }
  const api = new URL(`https://v3.openstates.org/bills/${id}`);
  for (const part of ["actions", "sponsorships", "votes", "sources"]) api.searchParams.append("include", part);
  const upstream = await fetch(api, { headers: { "X-API-KEY": env.OPENSTATES_KEY } });
  if (upstream.status === 404) return json({ error: "that bill wasn't found" }, 404);
  if (upstream.status === 429) return json({ error: "the state bill service has reached its limit for now; try again later" }, 503);
  if (!upstream.ok) return json({ error: "the state bill service didn't answer" }, 502);
  const b = await upstream.json();
  const body = {
    id: b.id,
    state: (b.jurisdiction?.id || "").match(/state:([a-z]{2})/)?.[1] || null,
    number: b.identifier,
    title: b.title,
    session: b.session,
    chamber: b.from_organization?.classification ?? null,
    subjects: b.subject || [],
    actions: (b.actions || []).map((a) => ({
      date: (a.date || "").slice(0, 10),
      text: a.description,
      chamber: a.organization?.classification ?? null,
    })),
    sponsors: (b.sponsorships || []).map((sp) => ({
      name: sp.name,
      primary: !!sp.primary,
      personId: sp.person?.id ?? null,
      party: sp.person?.party ?? null,
    })),
    votes: (b.votes || []).map((v) => ({
      date: (v.start_date || "").slice(0, 10),
      motion: (v.motion_text || "").replace(/\s+/g, " ").trim() || null,
      result: v.result,
      chamber: v.organization?.classification ?? null,
      counts: Object.fromEntries((v.counts || []).map((c) => [c.option, c.value])),
      voters: (v.votes || []).map((x) => ({ name: x.voter_name, personId: x.voter?.id ?? null, option: x.option })),
    })),
    url: b.openstates_url,
    sources: (b.sources || []).map((x) => x.url).filter(Boolean).slice(0, 3),
  };
  const response = new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=21600" },
  });
  ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
  return response;
}

// --- Lobbying reports that name a bill, from the Lobbying Disclosure Act database (lda.gov) ---

const BILL_KEY = /^(1\d\d)\/(hr|s|hres|sres|hjres|sjres|hconres|sconres)\/(\d{1,5})$/;
const BILL_LABEL = { hr: "H.R.", s: "S.", hres: "H.Res.", sres: "S.Res.", hjres: "H.J.Res.", sjres: "S.J.Res.", hconres: "H.Con.Res.", sconres: "S.Con.Res." };

async function lobbying(request, url, env, ctx) {
  const m = (url.searchParams.get("bill") || "").match(BILL_KEY);
  if (!m) return json({ error: "bad request" }, 400);
  const [, congress, type, number] = m;
  const cacheKey = new Request(`https://cache.ptp/lobbying?bill=${congress}/${type}/${number}`);
  const hit = await caches.default.match(cacheKey);
  if (hit) return hit;
  if (env.LIMIT) {
    const { success } = await env.LIMIT.limit({ key: request.headers.get("CF-Connecting-IP") || "unknown" });
    if (!success) return json({ error: "too many requests, try again in a minute" }, 429);
  }
  const label = `${BILL_LABEL[type]} ${number}`;
  // "S. 2403", "S.2403" or "S 2403", but not "H.R.S. 24031". Bill numbers start over each Congress, so only
  // reports from this Congress's two years count.
  const letters = BILL_LABEL[type].replace(/\./g, "").split("").join("\\.?\\s*");
  const pattern = new RegExp(`(^|[^A-Za-z.])${letters}\\.?\\s*${number}(?!\\d)`, "i");
  const firstYear = 1789 + 2 * (Number(congress) - 1);
  const reports = [];
  for (const year of [firstYear, firstYear + 1]) {
    let next = `https://lda.gov/api/v1/filings/?filing_specific_lobbying_issues=${encodeURIComponent(label)}&filing_year=${year}&page_size=25`;
    for (let page = 0; next && page < 4; page++) {
      const r = await fetch(next, { headers: { Accept: "application/json" } });
      if (r.status === 429) return json({ error: "the lobbying database has reached its limit for now; try again later" }, 503);
      if (!r.ok) return json({ error: "the lobbying database didn't answer" }, 502);
      const data = await r.json();
      for (const f of data.results || []) {
        const named = (f.lobbying_activities || []).filter((a) => pattern.test(a.description || ""));
        if (!named.length) continue;
        const text = named[0].description || "";
        const at = text.search(pattern);
        reports.push({
          id: f.filing_uuid,
          client: f.client?.name || null,
          registrant: f.registrant?.name || null,
          year: f.filing_year,
          period: f.filing_period_display || f.filing_period,
          type: f.filing_type_display || f.filing_type,
          posted: (f.dt_posted || "").slice(0, 10) || null,
          amount: f.income ?? f.expenses ?? null,
          issues: [...new Set(named.map((a) => a.general_issue_code_display).filter(Boolean))],
          excerpt: text.slice(Math.max(0, at - 160), at + 200).replace(/\s+/g, " ").trim(),
          url: f.filing_document_url,
        });
      }
      next = data.next;
    }
  }
  // An amended report replaces its original: keep the latest for each filer, client and quarter.
  const latest = new Map();
  for (const r of reports) {
    const key = `${r.registrant}|${r.client}|${r.year}|${r.period}`;
    const seen = latest.get(key);
    if (!seen || (r.posted || "") > (seen.posted || "")) latest.set(key, r);
  }
  const body = {
    bill: `${congress}/${type}/${number}`,
    label,
    reports: [...latest.values()].sort((a, b) => (b.posted || "").localeCompare(a.posted || "")),
    source: "https://lda.gov/",
  };
  const response = new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=86400" },
  });
  ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
  return response;
}

// --- Elections, polling places and ballots, from Google's Civic Information API (voterInfoQuery) ---
// The Google key stays here as a secret. A reader's address is passed through to Google once per lookup and is
// never stored or logged here; elections lists are cached, address lookups are not.

const GOOGLE = "https://www.googleapis.com/civicinfo/v2";

async function elections(env, ctx) {
  if (!env.GOOGLE_CIVIC_KEY) return json({ error: "election information isn't set up yet" }, 503);
  const cacheKey = new Request("https://cache.ptp/elections");
  const hit = await caches.default.match(cacheKey);
  if (hit) return hit;
  const r = await fetch(`${GOOGLE}/elections?key=${env.GOOGLE_CIVIC_KEY}`);
  if (!r.ok) return json({ error: "the election service didn't answer" }, 502);
  const data = await r.json();
  const body = { elections: (data.elections || []).filter((e) => e.id !== "2000") };  // 2000 is Google's test election
  const response = new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json", "Cache-Control": "public, max-age=21600" },
  });
  ctx.waitUntil(caches.default.put(cacheKey, response.clone()));
  return response;
}

const place = (p) => ({
  name: p.address?.locationName || null,
  address: [p.address?.line1, p.address?.line2, p.address?.line3, [p.address?.city, p.address?.state, p.address?.zip].filter(Boolean).join(" ")]
    .filter(Boolean).join(", "),
  hours: p.pollingHours || null,
  notes: p.notes || null,
  start: p.startDate || null,
  end: p.endDate || null,
});

async function voterInfo(request, env) {
  if (!env.GOOGLE_CIVIC_KEY) return json({ error: "election information isn't set up yet" }, 503);
  if (env.LIMIT) {
    const { success } = await env.LIMIT.limit({ key: request.headers.get("CF-Connecting-IP") || "unknown" });
    if (!success) return json({ error: "too many lookups, try again in a minute" }, 429);
  }
  let body;
  try { body = await request.json(); } catch { return json({ error: "bad request" }, 400); }
  const address = String(body?.address || "").slice(0, 200);
  if (address.length < 5) return json({ error: "bad request" }, 400);
  const url = new URL(`${GOOGLE}/voterinfo`);
  url.searchParams.set("key", env.GOOGLE_CIVIC_KEY);
  url.searchParams.set("address", address);
  if (/^\d{1,6}$/.test(String(body?.electionId || ""))) url.searchParams.set("electionId", body.electionId);
  const r = await fetch(url);
  const data = await r.json().catch(() => ({}));
  if (!r.ok) {
    // Google answers 400 when it has no election data for that address yet.
    const reason = data?.error?.message || "";
    const error = /election unknown|no information|not found/i.test(reason)
      ? "There's no election information for your address yet. It usually appears a few weeks before an election."
      : "the election service didn't answer";
    return json({ error, detail: reason.slice(0, 200) }, r.status === 400 ? 404 : 502);
  }
  const admin = (data.state || [])[0]?.electionAdministrationBody || {};
  return json({
    election: data.election ? { id: data.election.id, name: data.election.name, day: data.election.electionDay } : null,
    mailOnly: !!data.mailOnly,
    polling: (data.pollingLocations || []).map(place),
    early: (data.earlyVoteSites || []).map(place),
    dropOff: (data.dropOffLocations || []).map(place),
    contests: (data.contests || []).map((c) => ({
      office: c.office || null,
      district: c.district?.name || null,
      level: (c.level || [])[0] || null,
      type: c.type || null,
      candidates: (c.candidates || []).map((p) => ({ name: p.name, party: p.party || null, url: p.candidateUrl || null })),
      measure: c.referendumTitle ? {
        title: c.referendumTitle, subtitle: c.referendumSubtitle || null, text: c.referendumText || null,
        url: c.referendumUrl || null, choices: c.referendumBallotResponses || [],
      } : null,
    })),
    links: {
      info: admin.electionInfoUrl || null,
      register: admin.electionRegistrationUrl || null,
      checkRegistration: admin.electionRegistrationConfirmationUrl || null,
      absentee: admin.absenteeVotingInfoUrl || null,
      findPollingPlace: admin.votingLocationFinderUrl || null,
      ballot: admin.ballotInfoUrl || null,
      rules: admin.electionRulesUrl || null,
    },
    office: admin.name || null,
    source: "Google Civic Information API, from state and local election offices",
  });
}
