"""
Campaign money for each member of Congress, from the Federal Election Commission (OpenFEC API): what their
campaign committee raised for their current race, where it came from, what it spent, and the cash it had
at its last report. Copied as reported; the FEC is the source of record.

  finance/{congress}.json   {"members": {bioguide: {...}}}

Members are refreshed oldest first, up to the run's FEC budget, so each is checked about weekly.
"""
import datetime
import json
import urllib.request

from net import BudgetSpent

LEGISLATORS = "https://unitedstates.github.io/congress-legislators/legislators-current.json"
STALE_DAYS = 7


def _candidate_id(person):
    """The FEC id for the seat they hold now: House ids start with H, Senate ids with S."""
    term = person["terms"][-1]
    prefix = "S" if term["type"] == "sen" else "H"
    ids = [i for i in person["id"].get("fec", []) if i.startswith(prefix) and term.get("state", "") in i[2:4]]
    ids = ids or [i for i in person["id"].get("fec", []) if i.startswith(prefix)]
    # The project lists ids oldest first, so the last is the newest.
    return ids[-1] if ids else None


def _totals(net, fec_id):
    page = net.fec(f"/candidate/{fec_id}/totals/", election_full="true", sort="-candidate_election_year", per_page=1)
    rows = page.get("results") or []
    if not rows:
        return None
    r = rows[0]

    def money(key):
        v = r.get(key)
        return round(v) if isinstance(v, (int, float)) else None

    out = {
        "fecId": fec_id,
        "electionYear": r.get("candidate_election_year") or r.get("cycle"),
        "from": (r.get("coverage_start_date") or "")[:10] or None,
        "through": (r.get("coverage_end_date") or "")[:10] or None,
        "lastReport": r.get("last_report_type_full"),
        "receipts": money("receipts"),
        "individuals": money("individual_contributions"),
        "individualsItemized": money("individual_itemized_contributions"),
        "individualsUnitemized": money("individual_unitemized_contributions"),
        "pacs": money("other_political_committee_contributions"),
        "parties": money("political_party_committee_contributions"),
        "candidate": money("candidate_contribution"),
        "candidateLoans": money("loans_made_by_candidate"),
        "transfers": money("transfers_from_other_authorized_committee"),
        "disbursements": money("disbursements"),
        "cashOnHand": money("last_cash_on_hand_end_period"),
        "debts": money("last_debts_owed_by_committee"),
        "source": f"https://www.fec.gov/data/candidate/{fec_id}/",
    }
    return {k: v for k, v in out.items() if v is not None}


def gather(net, store, congress, log):
    path = f"finance/{congress}.json"
    saved = store.read(path, {}).get("members", {})
    req = urllib.request.Request(LEGISLATORS, headers={"User-Agent": "PowerToThePeople-gatherer"})
    with urllib.request.urlopen(req, timeout=60) as r:
        people = json.loads(r.read())
    current = {p["id"]["bioguide"]: p for p in people if p["id"].get("bioguide")}
    # Members who have left are dropped; the rest are refreshed, longest unchecked first.
    saved = {k: v for k, v in saved.items() if k in current}
    today = datetime.date.today()

    def age(bioguide):
        checked = saved.get(bioguide, {}).get("checked")
        return (today - datetime.date.fromisoformat(checked)).days if checked else 10_000

    due = sorted((b for b in current if age(b) >= STALE_DAYS), key=age, reverse=True)
    done = 0
    try:
        for bioguide in due:
            fec_id = _candidate_id(current[bioguide])
            totals = _totals(net, fec_id) if fec_id else None
            found = totals or ({"fecId": fec_id} if fec_id else {})
            saved[bioguide] = {**found, "checked": today.isoformat()}
            done += 1
    except BudgetSpent:
        pass
    finally:
        store.write(path, {"congress": congress, "source": "https://www.fec.gov/data/", "members": saved})
    have = sum(1 for v in saved.values() if "receipts" in v)
    log(f"Campaign money: {done} refreshed, {have} of {len(current)} members have FEC totals, {len(due) - done} still due")
