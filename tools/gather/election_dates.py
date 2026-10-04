"""
Each state's deadlines for a general election: registering, early voting and mail ballots. These aren't in any
open feed (vote.gov turns away programs), so they are copied by hand from each state's election office into
tools/gather/election_dates/{year}.json, with the office's page as the source, and published as they are.

  elections/dates/{year}.json   {"election", "checked", "states": {"PA": {"allMail", "sameDay", "items", "notes", "sources"}}}

Check them again before each general election; states can move a date.
"""
import json
import os

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "election_dates")


def gather(store, log):
    for name in sorted(os.listdir(HERE)):
        if not name.endswith(".json"):
            continue
        with open(os.path.join(HERE, name), encoding="utf-8") as f:
            data = json.load(f)
        store.write(f"elections/dates/{name}", data)
        log(f"Election dates {name[:-5]}: {len(data.get('states', {}))} states, checked {data.get('checked')}")
