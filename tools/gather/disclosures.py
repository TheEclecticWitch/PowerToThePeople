"""
Members' financial disclosure filings under the Ethics in Government Act and the STOCK Act: each periodic
transaction report (a stock, bond or other trade of more than $1,000, due within 45 days) and each annual
report. Listed as filed, with a link to the official copy; the app never interprets or ranks them.

  disclosures.json   {"members": {bioguide: [{"kind": "trade"|"annual", "title", "filed", "url"}]}, "checked"}

House: the Clerk's yearly index (financial-pdfs/{year}FD.zip). Senate: the Secretary of the Senate's eFD
search, which asks every visitor to accept its terms first; this project's use (sharing the public record with
the public, free) is one the terms allow.
"""
import datetime
import http.cookiejar
import io
import json
import re
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

LEGISLATORS = "https://unitedstates.github.io/congress-legislators/legislators-current.json"
HOUSE_INDEX = "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/{year}FD.zip"
HOUSE_PTR = "https://disclosures-clerk.house.gov/public_disc/ptr-pdfs/{year}/{doc}.pdf"
HOUSE_ANNUAL = "https://disclosures-clerk.house.gov/public_disc/financial-pdfs/{year}/{doc}.pdf"
EFD = "https://efdsearch.senate.gov"
UA = {"User-Agent": "Mozilla/5.0 (compatible; PowerToThePeople-gatherer; +https://github.com/TheEclecticWitch/PowerToThePeople)"}
SENATE_TYPES = {"11": "trade", "7": "annual"}  # eFD report type ids: periodic transaction, annual


def _norm(name):
    """'Justice, II' -> 'justice'; 'Van Hollen' -> 'vanhollen'."""
    name = name.split(",")[0]
    return re.sub(r"[^a-z]", "", name.lower())


def _legislators():
    req = urllib.request.Request(LEGISLATORS, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def _iso(us_date):
    """'4/15/2026' -> '2026-04-15'."""
    m, d, y = (int(x) for x in us_date.split("/"))
    return datetime.date(y, m, d).isoformat()


def _house(net, people, years):
    reps = {}
    for p in people:
        t = p["terms"][-1]
        if t["type"] == "rep":
            reps.setdefault((t["state"], t.get("district") or 0), []).append(p)
    out = {}
    for year in years:
        net.check_time()
        net.other_requests += 1
        req = urllib.request.Request(HOUSE_INDEX.format(year=year), headers=UA)
        with urllib.request.urlopen(req, timeout=120) as r:
            z = zipfile.ZipFile(io.BytesIO(r.read()))
        name = next(n for n in z.namelist() if n.endswith(".xml"))
        root = ET.fromstring(z.read(name).decode("utf-8-sig"))
        for f in root.findall("Member"):
            kind = {"P": "trade", "O": "annual"}.get(f.findtext("FilingType") or "")
            sd = f.findtext("StateDst") or ""
            if not kind or len(sd) < 4:
                continue
            district = 0 if sd[2:] in ("AL", "00") else int(sd[2:]) if sd[2:].isdigit() else -1
            last = _norm(f.findtext("Last") or "")
            # The index also lists candidates for the same seat; only the member whose surname matches counts.
            match = [p for p in reps.get((sd[:2], district), []) if _norm(p["name"]["last"]) == last]
            if len(match) != 1:
                continue
            doc = f.findtext("DocID")
            filed = _iso(f.findtext("FilingDate"))
            url = (HOUSE_PTR if kind == "trade" else HOUSE_ANNUAL).format(year=f.findtext("Year") or year, doc=doc)
            out.setdefault(match[0]["id"]["bioguide"], []).append({
                "kind": kind,
                "title": "Periodic transaction report" if kind == "trade" else f"Annual report for {f.findtext('Year') or year}",
                "filed": filed,
                "url": url,
            })
    return out


def _senate(net, people, since):
    senators = [p for p in people if p["terms"][-1]["type"] == "sen"]
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))

    def fetch(url, data=None, headers=None):
        net.check_time()
        net.other_requests += 1
        req = urllib.request.Request(url, data=data, headers={**UA, **(headers or {})})
        with opener.open(req, timeout=60) as r:
            return r.read().decode("utf-8")

    home = f"{EFD}/search/home/"
    token = re.search(r'name="csrfmiddlewaretoken" value="([^"]+)"', fetch(home)).group(1)
    fetch(home, urllib.parse.urlencode({"prohibition_agreement": "1", "csrfmiddlewaretoken": token}).encode(), {"Referer": home})
    csrf = next(c.value for c in jar if c.name == "csrftoken")
    out = {}
    for type_id, kind in SENATE_TYPES.items():
        start = 0
        while True:
            form = {
                "start": str(start), "length": "100", "report_types": f"[{type_id}]", "filer_types": "[1]",
                "submitted_start_date": since.strftime("%m/%d/%Y 00:00:00"), "submitted_end_date": "",
                "candidate_state": "", "senator_state": "", "office_id": "", "first_name": "", "last_name": "",
                "csrfmiddlewaretoken": csrf,
            }
            page = json.loads(fetch(f"{EFD}/search/report/data/", urllib.parse.urlencode(form).encode(),
                                    {"Referer": f"{EFD}/search/", "X-CSRFToken": csrf}))
            rows = page.get("data", [])
            for first, last, _, link, filed in rows:
                href = re.search(r'href="([^"]+)"', link)
                title = re.sub(r"<[^>]+>", "", link).strip()
                match = [p for p in senators if _norm(p["name"]["last"]) == _norm(last)]
                if len(match) > 1:
                    match = [p for p in match if p["name"]["first"].lower() in first.lower()]
                if len(match) != 1 or not href:
                    continue
                out.setdefault(match[0]["id"]["bioguide"], []).append({
                    "kind": kind,
                    "title": title,
                    "filed": _iso(filed),
                    "url": EFD + href.group(1),
                })
            start += len(rows)
            if not rows or start >= page.get("recordsTotal", 0):
                break
    return out


def gather(net, store, congress, log):
    people = _legislators()
    start_year = 1789 + 2 * (congress - 1)
    years = list(range(start_year, datetime.date.today().year + 1))
    previous = store.read("disclosures.json", {}).get("members", {})
    members, failed = {}, []
    for name, fn in (("House", lambda: _house(net, people, years)),
                     ("Senate", lambda: _senate(net, people, datetime.date(start_year, 1, 3)))):
        try:
            for bioguide, filings in fn().items():
                members.setdefault(bioguide, []).extend(filings)
        except Exception as e:  # keep the last good list for that chamber
            failed.append(f"{name} ({type(e).__name__})")
            chamber = "rep" if name == "House" else "sen"
            ids = {p["id"]["bioguide"] for p in people if p["terms"][-1]["type"] == chamber}
            for b in ids & previous.keys():
                members[b] = previous[b]
    for filings in members.values():
        filings.sort(key=lambda f: f["filed"], reverse=True)
    store.write("disclosures.json", {
        "members": members,
        "checked": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    })
    trades = sum(1 for fs in members.values() for f in fs if f["kind"] == "trade")
    log(f"Disclosures: {len(members)} members, {trades} trade reports"
        + (f"; not refreshed: {', '.join(failed)}" if failed else ""))
