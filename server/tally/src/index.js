/**
 * Counts how app users answered "How would you have voted?" on each roll call. Anonymous and opt-in:
 * the app sends an answer only when the reader ticks "add my answer to the app-wide count".
 *
 *   GET  /tally?vote=119/senate/2/256        -> {"vote":..., "total":12, "yea":7, "nay":5}
 *   GET  /tally?vote=bill/119/hr/1           -> the same, for support (yea) and opposition (nay) to a bill
 *   GET  /tally?votes=119/senate/2/256,...   -> {"tallies":[...]} (up to 50 at once)
 *   POST /answer {"vote":..., "install":"<random code>", "answer":"Yea"|"Nay"|null}
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
  async fetch(request, env) {
    const url = new URL(request.url);
    try {
      if (request.method === "GET" && url.pathname === "/tally") return await readTallies(url, env);
      if (request.method === "POST" && url.pathname === "/answer") return await recordAnswer(request, env);
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
