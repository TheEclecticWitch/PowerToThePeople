"""
One file per member of Congress, built from the saved votes and bills, so the app can show an
official's record with one download:
  members/{bioguide}.json   every roll-call position they took this Congress, the bills they sponsored, and
                            the ten of those that saw action most recently, with where each stands
Each vote is pointed to by chamber, session and roll number; the details live in the vote lists.
"""


RECENT_SPONSORED = 10


def build(store, congress, log):
    people = {}

    def person(pid, name, party, state):
        return people.setdefault(pid, {
            "id": pid, "name": name, "party": party, "state": state,
            "congress": congress, "votes": [], "sponsored": [], "recentSponsored": [],
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
                    who = person(s["id"], s["name"], s["party"], s["state"])
                    who["sponsored"].append(b["bill"])
                    who["recentSponsored"].append({"bill": b["bill"], "title": b.get("title"), "action": b.get("latestAction")})

    for p in people.values():
        p["votes"].sort(key=lambda x: (x["session"], x["chamber"], x["roll"]), reverse=True)
        p["sponsored"].sort(key=lambda k: (k.split("/")[1], int(k.split("/")[2])))
        # Newest step first, for "Members I follow" in the app.
        p["recentSponsored"].sort(key=lambda r: ((r["action"] or {}).get("date") or "", r["bill"]), reverse=True)
        del p["recentSponsored"][RECENT_SPONSORED:]
        store.write(f"members/{p['id']}.json", p)
    log(f"Members: {len(people)} records built")
    return len(people)
