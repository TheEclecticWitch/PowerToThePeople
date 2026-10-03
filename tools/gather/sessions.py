"""
Which days each chamber met, from the daily Congressional Record on Congress.gov. Every day the House or
the Senate meets, its proceedings are printed in that day's Record in a "House Section" or "Senate
Section"; a day with neither is a day that chamber didn't meet. Brief "pro forma" sessions count as
meeting, as they do in the Record itself.

  sessions/{congress}.json   {"days": [{"date", "house", "senate", "record"}...]}, oldest first

Each year's Record is one volume (2026 is volume 172). Only days not seen before are fetched.
"""
import datetime

from congress import sessions_of


def gather(net, store, congress, log):
    path = f"sessions/{congress}.json"
    saved = {d["date"]: d for d in store.read(path, {}).get("days", [])}
    new = 0
    try:
        # The newest year first, so recent days arrive first; a year still to come has no Record yet.
        for _session, year in sorted(sessions_of(congress), key=lambda sy: -sy[1]):
            if year > datetime.date.today().year:
                continue
            volume = year - 1854
            offset = 0
            issues = []
            while True:
                page = net.congress(f"/daily-congressional-record/{volume}", limit=250, offset=offset)
                items = page.get("dailyCongressionalRecord", [])
                issues += items
                if len(items) < 250:
                    break
                offset += 250
            for issue in issues:
                if issue.get("congress") != congress:
                    continue
                date = (issue.get("issueDate") or "")[:10]
                if not date or date in saved:
                    continue
                detail = net.congress(f"/daily-congressional-record/{volume}/{issue['issueNumber']}").get("issue", {})
                names = {s.get("name") for s in (detail.get("fullIssue") or {}).get("sections", [])}
                saved[date] = {
                    "date": date,
                    "house": "House Section" in names,
                    "senate": "Senate Section" in names,
                    "record": f"https://www.congress.gov/congressional-record/volume-{volume}/issue-{issue['issueNumber']}",
                }
                new += 1
    finally:
        # Whatever was fetched is kept, even when the run's request budget runs out part way.
        store.write(path, {"congress": congress, "days": sorted(saved.values(), key=lambda x: x["date"])})
    house = sum(1 for d in saved.values() if d["house"])
    senate = sum(1 for d in saved.values() if d["senate"])
    log(f"Session days {congress}: {new} new; the House met {house} days and the Senate {senate}")
