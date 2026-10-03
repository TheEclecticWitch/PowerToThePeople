"""
Who is running for Congress this cycle: every House and Senate candidate registered with the Federal Election
Commission as an active candidate (one who has raised or spent more than $5,000), grouped by race. Copied as
the FEC lists them. The FEC doesn't record primary results, so a list can include people who lost a primary or
stopped campaigning; the app says so, and shows the certified ballot from the state when there is one.

  candidates/{cycle}.json   {"cycle", "races": {"MD-05" | "MD-SEN": [candidate, ...]}, "checked"}

Refreshed about weekly; a few dozen FEC requests.
"""
import datetime
import re

STALE_DAYS = 7
SMALL_WORDS = {"de", "la", "van", "von", "der", "da", "del", "di"}


def _cycle(today):
    return today.year if today.year % 2 == 0 else today.year + 1


def _word(w, first):
    if not w:
        return w
    lower = w.lower()
    if not first and lower in SMALL_WORDS:
        return lower.capitalize() if lower == "van" else lower
    if lower.startswith("mc") and len(lower) > 2:
        return "Mc" + lower[2:].capitalize()
    if lower.startswith("o'") and len(lower) > 2:
        return "O'" + lower[2:].capitalize()
    return "-".join(p.capitalize() for p in lower.split("-"))


def display_name(fec_name):
    """'VAN HOLLEN, CHRIS' -> 'Chris Van Hollen'; 'SMITH, JOHN Q. JR.' -> 'John Q. Smith Jr.'."""
    last, _, rest = fec_name.partition(",")
    rest = rest.strip()
    suffix = ""
    m = re.search(r"\b(JR|SR|II|III|IV)\.?$", rest, re.I)
    if m:
        suffix = " " + (m.group(1).upper() if m.group(1).upper() in ("II", "III", "IV") else m.group(1).capitalize() + ".")
        rest = rest[:m.start()].strip()
    for title in ("MR.", "MRS.", "MS.", "DR.", "HON."):
        rest = re.sub(rf"\b{re.escape(title)}\s*", "", rest, flags=re.I)
    first = " ".join(_word(w, True) for w in rest.split())
    last = " ".join(_word(w, i == 0) for i, w in enumerate(last.split()))
    return f"{first} {last}{suffix}".strip()


def _race(c):
    if c.get("office") == "S":
        return f"{c['state']}-SEN"
    district = c.get("district") or "00"
    return f"{c['state']}-{district}"


def gather(net, store, log):
    today = datetime.date.today()
    cycle = _cycle(today)
    path = f"candidates/{cycle}.json"
    saved = store.read(path, {})
    checked = saved.get("checked")
    if checked and (today - datetime.date.fromisoformat(checked[:10])).days < STALE_DAYS:
        log(f"Candidates {cycle}: {sum(len(r) for r in saved.get('races', {}).values())} (checked {checked[:10]})")
        return
    races = {}
    for office in ("H", "S"):
        page = 1
        while True:
            data = net.fec("/candidates/", election_year=cycle, office=office, candidate_status="C",
                           is_active_candidate="true", per_page=100, page=page, sort="name")
            for c in data.get("results", []):
                if cycle not in (c.get("election_years") or []):
                    continue
                races.setdefault(_race(c), []).append({
                    "fecId": c["candidate_id"],
                    "name": display_name(c.get("name") or ""),
                    "party": c.get("party_full"),
                    "status": {"I": "incumbent", "C": "challenger", "O": "open"}.get(c.get("incumbent_challenge")),
                    "url": f"https://www.fec.gov/data/candidate/{c['candidate_id']}/",
                })
            pages = (data.get("pagination") or {}).get("pages") or 1
            if page >= pages:
                break
            page += 1
    store.write(path, {
        "cycle": cycle,
        "races": races,
        "checked": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    })
    log(f"Candidates {cycle}: {sum(len(r) for r in races.values())} in {len(races)} races")
