"""
The data gatherer. GitHub Actions runs it on a schedule (.github/workflows/gather.yml) and publishes
what it saves to GitHub Pages, where the app downloads it. Everything is copied from official
sources as published, with a link back to each one; nothing is scored or summarized here.

Python's standard library plus PyYAML (pip install pyyaml), for Open States' state officials.

Run locally from the project root (uses the public DEMO_KEY unless API_DATA_GOV_KEY is set):
  python tools/gather/gather.py --out site --max-requests 40

Each run does as much as its budget allows and saves as it goes; the next run picks up where this
one stopped. It exits with an error only if a source failed, so GitHub emails a warning.
"""
import argparse
import datetime
import os
import sys
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bills  # noqa: E402
import candidates  # noqa: E402
import contacts  # noqa: E402
import disclosures  # noqa: E402
import doomsday  # noqa: E402
import finance  # noqa: E402
import members  # noqa: E402
import orders  # noqa: E402
import press  # noqa: E402
import sessions as session_days  # noqa: E402
import states  # noqa: E402
import summaries  # noqa: E402
import upcoming  # noqa: E402
import votes  # noqa: E402
from congress import current_congress, sessions_of  # noqa: E402
from net import BudgetSpent, Net  # noqa: E402
from store import Store  # noqa: E402

SOURCES = [
    {"name": "Congress.gov API (Library of Congress)", "url": "https://api.congress.gov/",
     "covers": "Bills, House roll-call votes"},
    {"name": "Office of the Clerk, U.S. House of Representatives", "url": "https://clerk.house.gov/Votes",
     "covers": "House roll-call votes (the original record behind the Congress.gov copy)"},
    {"name": "U.S. Senate roll call votes", "url": "https://www.senate.gov/legislative/votes_new.htm",
     "covers": "Senate roll-call votes"},
    {"name": "Federal Election Commission (OpenFEC API)", "url": "https://www.fec.gov/data/",
     "covers": "Campaign money raised and spent by members' campaign committees, and this cycle's registered candidates"},
    {"name": "Federal Register", "url": "https://www.federalregister.gov/presidential-documents/executive-orders",
     "covers": "Executive orders"},
    {"name": "Members' and the White House's own news feeds", "url": "https://www.whitehouse.gov/news/",
     "covers": "Press releases and office news, as published by each office"},
    {"name": "Office of the Clerk and the Majority Leader, docs.house.gov", "url": "https://docs.house.gov/floor/",
     "covers": "The House's weekly floor schedule"},
    {"name": "U.S. Senate floor schedule", "url": "https://www.senate.gov/legislative/schedule/floor_schedule.htm",
     "covers": "The Senate's next meeting"},
    {"name": "Financial disclosures: the House Clerk and the Senate's eFD", "url": "https://disclosures-clerk.house.gov/FinancialDisclosure",
     "covers": "Members' stock trade (STOCK Act) and annual financial disclosure reports, as filed"},
    {"name": "Regulations.gov", "url": "https://www.regulations.gov/",
     "covers": "Proposed federal rules open for public comment"},
    {"name": "Bulletin of the Atomic Scientists", "url": "https://thebulletin.org/doomsday-clock/",
     "covers": "The Doomsday Clock"},
    {"name": "Open States", "url": "https://github.com/openstates/people",
     "covers": "Governors, statewide officers and state legislators"},
    {"name": "@unitedstates congress-legislators", "url": "https://github.com/unitedstates/congress-legislators",
     "covers": "Matching Senate ids to bioguide ids"},
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True, help="folder that is published to GitHub Pages")
    ap.add_argument("--max-requests", type=int, default=3500,
                    help="Congress.gov calls per run; api.data.gov allows 5,000 an hour")
    ap.add_argument("--max-minutes", type=float, default=40)
    args = ap.parse_args()

    net = Net(args.max_requests, args.max_minutes)
    store = Store(args.out)
    state = store.read("state.json", {})
    congress = current_congress()
    today = datetime.date.today()
    errors, lines = [], []

    def log(msg):
        print(msg, flush=True)
        lines.append(msg)

    def step(name, fn, *a):
        """Runs one source. A spent budget stops only the fetching; a failure is recorded and skipped."""
        try:
            return fn(*a)
        except BudgetSpent:
            log(f"{name}: request budget for this run used up; the next run continues")
            return None
        except Exception as e:
            traceback.print_exc()
            errors.append(f"{name}: {type(e).__name__}: {e}")
            return None

    # Newest session first, and the Senate (a separate site) before the House, so no one source can
    # use up a run's time before the others get a turn.
    senate_ids = votes.SenateIds(net)
    sessions = [s for s, year in sessions_of(congress) if year <= today.year][::-1]
    for session in sessions:
        step(f"Senate votes {congress}-{session}", votes.gather_senate, net, store, congress, session, senate_ids, log)
    for session in sessions:
        step(f"House votes {congress}-{session}", votes.gather_house, net, store, congress, session, log)
    for session in sessions:
        for chamber in ("house", "senate"):
            votes.rebuild_list(store, chamber, congress, session)

    index = step(f"Bill list {congress}", bills.list_bills, net, store, state, congress, log)
    if index is None:
        index = store.read(f"bills/{congress}/index.json", {}).get("bills", {})
    if index:
        bills.write_recent(store, congress, index)
        step(f"Bill details {congress}", bills.gather_details, net, store, congress, index, log)
        step(f"Bill summaries {congress}", summaries.gather, net, store, state, congress, log)

    step(f"Session days {congress}", session_days.gather, net, store, congress, log)
    member_count = step("Members", members.build, store, congress, log)
    step("Candidates", candidates.gather, net, store, log)
    step(f"Campaign money {congress}", finance.gather, net, store, congress, log)
    step("Doomsday Clock", doomsday.gather, net, store, log)
    step("Executive orders", orders.gather, net, store, log)
    step("Coming up", upcoming.gather, net, store, congress, log)
    step("Disclosures", disclosures.gather, net, store, congress, log)
    step("Office news", press.gather, net, store, state, log)
    step("Contact pages", contacts.gather, net, store, state, log)
    step("State officials", states.gather, net, store, state, log)

    store.write("state.json", state)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    store.write("manifest.json", {
        "schema": 1,
        "congress": congress,
        "generatedAt": now,
        "counts": {
            "bills": len(index or {}),
            "members": member_count,
            **{f"{c}Votes": sum(len(store.read(f"votes/{c}/{congress}-{s}.json", {}).get("votes", []))
                                for s in (1, 2)) for c in ("house", "senate")},
        },
        "sources": SOURCES,
        "notes": [
            "Records are copied from the sources as published. House 'Aye'/'No' votes are stored as "
            "'Yea'/'Nay', the Senate's words for the same votes; nothing else is changed.",
        ],
        "lastRun": {"log": lines, "errors": errors,
                    "congressRequests": net.congress_requests, "fecRequests": net.fec_requests,
                    "otherRequests": net.other_requests,
                    "network": net.stats()},
    })
    print(f"Done: {net.congress_requests} Congress.gov requests, {net.other_requests} other, "
          f"{store.written} files written, {len(errors)} errors")
    print(f"Network: {net.stats()}")
    for e in errors:
        print("ERROR", e)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
