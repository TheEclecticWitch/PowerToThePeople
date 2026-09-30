"""
One file per member of Congress, built from the saved votes and bills, so the app can show an
official's record with one download:
  members/{bioguide}.json   every roll-call position they took this Congress, and the bills they sponsored
Each vote is pointed to by chamber, session and roll number; the details live in the vote lists.
"""


def build(store, congress, log):
    people = {}

    def person(pid, name, party, state):
        return people.setdefault(pid, {
            "id": pid, "name": name, "party": party, "state": state,
            "congress": congress, "votes": [], "sponsored": [],
        })

    for chamber in ("house", "senate"):
        for session in (1, 2):
            folder = f"votes/{chamber}/{congress}-{session}"
            for name in store.list_dir(folder):
                v = store.read(f"{folder}/{name}")
                for p in v["positions"]:
                    if p.get("id"):
                        person(p["id"], p["name"], p["party"], p["state"])["votes"].append({
                            "chamber": chamber, "session": session, "roll": v["roll"], "vote": p["vote"],
                        })

    for t in store.list_dir(f"bills/{congress}"):
        if t.endswith(".json"):
            continue
        for name in store.list_dir(f"bills/{congress}/{t}"):
            b = store.read(f"bills/{congress}/{t}/{name}")
            for s in b.get("sponsors", []):
                if s.get("id"):
                    person(s["id"], s["name"], s["party"], s["state"])["sponsored"].append(b["bill"])

    for p in people.values():
        p["votes"].sort(key=lambda x: (x["session"], x["chamber"], x["roll"]), reverse=True)
        p["sponsored"].sort(key=lambda k: (k.split("/")[1], int(k.split("/")[2])))
        store.write(f"members/{p['id']}.json", p)
    log(f"Members: {len(people)} records built")
    return len(people)
