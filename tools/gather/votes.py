"""
Roll-call votes for both chambers of the sitting Congress.

House votes come from the Congress.gov API (which republishes the House Clerk's records). The
Senate isn't in that API yet, so its votes come straight from senate.gov's XML files. Both are saved
in one shape:
  votes/{chamber}/{congress}-{session}/{roll}.json   one vote, with every member's position
  votes/{chamber}/{congress}-{session}.json          the list of votes, newest first
"""
import datetime
import json
import re
import xml.etree.ElementTree as ET

from congress import bill_key, bill_url, parse_bill_name

# Members' votes are copied as cast, except that the House's "Aye"/"No" are the same votes the
# Senate records as "Yea"/"Nay", so they're stored under one name to make comparisons possible.
SAME_VOTE = {"Aye": "Yea", "No": "Nay"}

LEGISLATORS = "https://unitedstates.github.io/congress-legislators"


def _totals(positions):
    totals = {}
    for p in positions:
        totals[p["vote"]] = totals.get(p["vote"], 0) + 1
    return totals


def _summary(v):
    return {k: v.get(k) for k in ("roll", "date", "question", "title", "result", "bill", "totals")}


def rebuild_list(store, chamber, congress, session):
    folder = f"votes/{chamber}/{congress}-{session}"
    votes = [store.read(f"{folder}/{name}") for name in store.list_dir(folder)]
    votes = sorted((v for v in votes if v), key=lambda v: v["roll"], reverse=True)
    store.write(f"{folder}.json", {
        "chamber": chamber, "congress": congress, "session": session,
        "votes": [_summary(v) for v in votes],
    })
    return len(votes)


# ---------------------------------------------------------------- House

def gather_house(net, store, congress, session, log):
    folder = f"votes/house/{congress}-{session}"
    listed, offset = [], 0
    while True:
        page = net.congress(f"/house-vote/{congress}/{session}", limit=250, offset=offset)
        items = page.get("houseRollCallVotes", [])
        listed += items
        if len(items) < 250 or not page.get("pagination", {}).get("next"):
            break
        offset += 250

    fetched = 0
    for item in sorted(listed, key=lambda i: i["rollCallNumber"], reverse=True):
        roll = item["rollCallNumber"]
        saved = store.read(f"{folder}/{roll}.json")
        if saved and saved.get("updated") == item.get("updateDate"):
            continue
        detail = net.congress(f"/house-vote/{congress}/{session}/{roll}/members", limit=500)
        v = detail["houseRollCallVoteMemberVotes"]
        positions = [{
            "id": r.get("bioguideID"),
            "name": f'{r.get("firstName", "")} {r.get("lastName", "")}'.strip(),
            "party": r.get("voteParty"),
            "state": r.get("voteState"),
            "vote": SAME_VOTE.get(r.get("voteCast"), r.get("voteCast")),
        } for r in v.get("results", [])]
        key = bill_key(congress, v.get("legislationType"), v.get("legislationNumber"))
        store.write(f"{folder}/{roll}.json", {
            "chamber": "house", "congress": congress, "session": session, "roll": roll,
            "date": (v.get("startDate") or "")[:10], "time": v.get("startDate"),
            "question": v.get("voteQuestion"), "title": None,
            "type": v.get("voteType"), "result": v.get("result"),
            "bill": key, "billUrl": bill_url(key) if key else v.get("legislationUrl"),
            "totals": _totals(positions), "positions": positions,
            "updated": item.get("updateDate"),
            "source": v.get("sourceDataURL") or item.get("sourceDataURL"),
        })
        fetched += 1
    log(f"House {congress}-{session}: {len(listed)} votes listed, {fetched} fetched")


# ---------------------------------------------------------------- Senate

def _text(el, path):
    found = el.find(path)
    return re.sub(r"\s+", " ", found.text).strip() if found is not None and found.text else None


def _senate_time(raw):
    """'September 30, 2026,  12:51 PM' (Eastern time) -> ('2026-09-30', '2026-09-30T12:51')."""
    try:
        t = datetime.datetime.strptime(re.sub(r"\s+", " ", raw or ""), "%B %d, %Y, %I:%M %p")
        return t.date().isoformat(), t.isoformat(timespec="minutes")
    except ValueError:
        return None, raw


class SenateIds:
    """Senate files name senators by their Senate (LIS) id; the app uses bioguide ids."""

    def __init__(self, net):
        self.net = net
        self.map = None
        self.loaded_historical = False

    def _load(self, name):
        for person in json.loads(self.net.text(f"{LEGISLATORS}/{name}")):
            ids = person.get("id", {})
            if ids.get("lis") and ids.get("bioguide"):
                self.map[ids["lis"]] = ids["bioguide"]

    def bioguide(self, lis):
        if self.map is None:
            self.map = {}
            self._load("legislators-current.json")
        if lis not in self.map and not self.loaded_historical:
            # Only needed for a senator who has since left office; it's a big file, so fetched once.
            self.loaded_historical = True
            self._load("legislators-historical.json")
        return self.map.get(lis)


def gather_senate(net, store, congress, session, ids, log):
    folder = f"votes/senate/{congress}-{session}"
    base = "https://www.senate.gov/legislative/LIS"
    try:
        menu = ET.fromstring(net.text(f"{base}/roll_call_lists/vote_menu_{congress}_{session}.xml", browser=True))
    except Exception as e:
        if getattr(e, "code", None) == 404:
            log(f"Senate {congress}-{session}: no votes published")
            return
        raise
    rolls = sorted({int(_text(v, "vote_number")) for v in menu.iter("vote")}, reverse=True)
    have = {int(n[:-5]) for n in store.list_dir(folder) if n.endswith(".json")}
    # New votes, plus the latest few again because the Senate corrects fresh records now and then.
    wanted = [r for r in rolls if r not in have] + [r for r in rolls[:5] if r in have]

    fetched = 0
    for roll in wanted:
        src = f"{base}/roll_call_votes/vote{congress}{session}/vote_{congress}_{session}_{roll:05d}.xml"
        v = ET.fromstring(net.text(src, browser=True))
        positions = [{
            "id": ids.bioguide(_text(m, "lis_member_id")),
            "senateId": _text(m, "lis_member_id"),
            "name": f'{_text(m, "first_name") or ""} {_text(m, "last_name") or ""}'.strip(),
            "party": _text(m, "party"),
            "state": _text(m, "state"),
            "vote": _text(m, "vote_cast"),
        } for m in v.iter("member")]
        date, time = _senate_time(_text(v, "vote_date"))
        key = parse_bill_name(congress, _text(v, "document/document_name"))
        store.write(f"{folder}/{roll}.json", {
            "chamber": "senate", "congress": congress, "session": session, "roll": roll,
            "date": date, "time": time,
            "question": _text(v, "vote_question_text"), "title": _text(v, "vote_title"),
            "type": _text(v, "majority_requirement"), "result": _text(v, "vote_result_text"),
            "bill": key, "billUrl": bill_url(key) if key else None,
            "document": _text(v, "document/document_name"),
            "totals": _totals(positions), "positions": positions,
            "updated": _text(v, "modify_date"),
            "source": src.replace(".xml", ".htm"),
        })
        fetched += 1
    log(f"Senate {congress}-{session}: {len(rolls)} votes listed, {fetched} fetched")
