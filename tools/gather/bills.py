"""
Bills and resolutions of the sitting Congress, from the Congress.gov API.
  bills/{congress}/index.json          every bill, in brief (title, dates, latest action)
  bills/{congress}/recent.json         the 200 most recently updated, for the app's first screen
  bills/{congress}/{type}/{number}.json   one bill: sponsor, cosponsor count, subject area, laws

The full index is listed once, then only what changed since the last run. Details for thousands of
bills take several runs to fill in the first time; the newest-changed bills go first.
"""
import datetime

from congress import bill_key, bill_url


def _brief(b):
    action = b.get("latestAction") or {}
    return {
        "title": b.get("title"),
        "introduced": b.get("introducedDate"),
        "updated": b.get("updateDateIncludingText") or b.get("updateDate"),
        "origin": b.get("originChamber"),
        "latestAction": {"date": action.get("actionDate"), "text": action.get("text")},
    }


def list_bills(net, store, state, congress, log):
    index = store.read(f"bills/{congress}/index.json", {}).get("bills", {})
    since = state.get("billsListedThrough", {}).get(str(congress)) if index else None
    started = datetime.datetime.now(datetime.timezone.utc)
    params = {"limit": 250, "sort": "updateDate desc"}
    if since:
        params["fromDateTime"] = since

    offset, changed = 0, 0
    while True:
        page = net.congress(f"/bill/{congress}", offset=offset, **params)
        items = page.get("bills", [])
        for b in items:
            key = bill_key(congress, b.get("type"), b.get("number"))
            if key:
                index[key] = _brief(b)
                changed += 1
        # Saved every page, so a run that runs out of budget mid-list keeps what it got.
        store.write(f"bills/{congress}/index.json", {"congress": congress, "bills": index})
        if len(items) < 250 or not page.get("pagination", {}).get("next"):
            break
        offset += 250

    # Only marked done once the whole list is read. A day's overlap covers late-posted updates.
    through = (started - datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    state.setdefault("billsListedThrough", {})[str(congress)] = through
    log(f"Bills {congress}: {changed} listed as new or changed, {len(index)} in all")
    return index


def write_recent(store, congress, index):
    newest = sorted(index.items(), key=lambda kv: (kv[1]["updated"] or "", kv[0]), reverse=True)[:200]
    store.write(f"bills/{congress}/recent.json", {
        "congress": congress,
        "bills": [{"bill": k, **v} for k, v in newest],
    })


def gather_details(net, store, congress, index, log):
    todo = []
    for key, brief in index.items():
        saved = store.read(f"bills/{key}.json")
        if not saved or saved.get("listUpdated") != brief["updated"]:
            todo.append((brief["updated"] or "", key))
    todo.sort(reverse=True)

    fetched = 0
    try:
        for _, key in todo:
            _, t, number = key.split("/")
            b = net.congress(f"/bill/{congress}/{t}/{number}")["bill"]
            action = b.get("latestAction") or {}
            store.write(f"bills/{key}.json", {
                "bill": key, "congress": congress, "type": t, "number": int(number),
                "title": b.get("title"),
                "introduced": b.get("introducedDate"),
                "origin": b.get("originChamber"),
                "policyArea": (b.get("policyArea") or {}).get("name"),
                "sponsors": [{
                    "id": s.get("bioguideId"), "name": s.get("fullName"),
                    "party": s.get("party"), "state": s.get("state"), "district": s.get("district"),
                } for s in b.get("sponsors", [])],
                "cosponsorCount": (b.get("cosponsors") or {}).get("count", 0),
                "latestAction": {"date": action.get("actionDate"), "text": action.get("text")},
                "laws": [{"type": l.get("type"), "number": l.get("number")} for l in b.get("laws", [])],
                "updated": b.get("updateDateIncludingText") or b.get("updateDate"),
                "listUpdated": index[key]["updated"],
                "url": bill_url(key),
                "source": f"https://api.congress.gov/v3/bill/{congress}/{t}/{number}",
            })
            fetched += 1
    finally:
        log(f"Bill details {congress}: {fetched} fetched, {len(todo) - fetched} still to fetch")
