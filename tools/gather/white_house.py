"""
The White House's own record of official business, from the section feeds whitehouse.gov publishes, for the
three White House cards on the app's Home screen. Opinion pieces and promotional videos are left out by
reading only these sections:

  press/wh-briefings.json   the Press Secretary's briefings and press conferences (videos; no transcripts are posted)
  press/wh-actions.json     presidential actions: executive orders, proclamations, memoranda, nominations
  press/wh-signed.json      bills signed into law, each with the app's ids for the bills it names

The Videos feed covers only about ten days, and briefings are a few of its items, so each file keeps what
earlier runs found as well, newest first. The first run also reads the Videos feed's older pages, about two
months back, so the card starts with more than a briefing or two.
"""
import datetime
import re

from congress import current_congress, parse_bill_name
from press import _fetch

SITE = "https://www.whitehouse.gov"
KEEP = 20
FEED_SIZE = 50
BACKFILL_PAGES = 8
# The Press Secretary "Briefs Members of the Media"; officials "Hold a Press Conference". The President's
# informal gaggles are left out: there are several a week and they would crowd the briefings off the card.
BRIEFING = re.compile(r"\bbriefs\b|press briefing|press conference", re.I)
# "Congressional Bills H.R. 5345 and S. 766 Signed into Law"
BILL = re.compile(r"\b(H\.\s?R\.|S\.|H\.\s?J\.\s?Res\.|S\.\s?J\.\s?Res\.)\s?(\d+)", re.I)
# Every presidential action is filed under "Presidential Actions" plus what kind it is.
ACTION_KINDS = {"Executive Orders": "Executive order", "Proclamations": "Proclamation",
                "Presidential Memoranda": "Memorandum", "Nominations & Appointments": "Nominations"}


def _merge(store, rel, source, new):
    """This run's items before the ones kept from earlier runs, without repeats."""
    old = store.read(rel, {}).get("items", [])
    seen, items = set(), []
    for item in sorted(new + old, key=lambda i: i.get("date") or "", reverse=True):
        if item["url"] not in seen:
            seen.add(item["url"])
            items.append(item)
    store.write(rel, {"source": source, "items": items[:KEEP]})
    return len(new)


def _briefings(store, state):
    pages = 1 if state.get("whBriefingsBackfilled") else BACKFILL_PAGES
    found = []
    for page in range(1, pages + 1):
        url = f"{SITE}/videos/feed/" + (f"?paged={page}" if page > 1 else "")
        found += [i for i in _fetch(url, FEED_SIZE) or [] if BRIEFING.search(i["title"])]
    state["whBriefingsBackfilled"] = True
    return _merge(store, "press/wh-briefings.json", f"{SITE}/videos/", found)


def _actions(store):
    found = []
    for i in _fetch(f"{SITE}/presidential-actions/feed/", FEED_SIZE, kinds=True) or []:
        kind = next((ACTION_KINDS[k] for k in i.pop("kinds", []) if k in ACTION_KINDS), None)
        found.append({**i, "kind": kind} if kind else i)
    return _merge(store, "press/wh-actions.json", f"{SITE}/presidential-actions/", found)


def _signed(store):
    found, indexes = [], {}
    for i in _fetch(f"{SITE}/briefings-statements/feed/", FEED_SIZE) or []:
        if "signed into law" not in i["title"].lower():
            continue
        congress = current_congress(_day(i.get("date")))
        if congress not in indexes:
            indexes[congress] = store.read(f"bills/{congress}/index.json", {}).get("bills", {})
        index = indexes[congress]
        bills = []
        for m in BILL.finditer(i["title"]):
            key = parse_bill_name(congress, f"{m.group(1)} {m.group(2)}")
            if key and key not in (b["id"] for b in bills):
                title = (index.get(key) or {}).get("title")
                bills.append({"id": key, "title": title} if title else {"id": key})
        found.append({**i, "bills": bills})
    return _merge(store, "press/wh-signed.json", f"{SITE}/briefings-statements/", found)


def _day(iso):
    try:
        return datetime.date.fromisoformat(iso)
    except (TypeError, ValueError):
        return None


def gather(store, state, log):
    counts = [_briefings(store, state), _actions(store), _signed(store)]
    log("White House: {} briefings, {} presidential actions, {} bill signings in this run's feeds".format(*counts))
