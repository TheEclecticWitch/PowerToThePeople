"""
What's coming up, so people can speak up before a vote rather than after:

  upcoming.json
    house       the House floor schedule for this week and next (the Clerk's "Bills This Week", docs.house.gov)
    senate      the Senate's next meeting and its plan (senate.gov's floor schedule)
    hearings    committee meetings and hearings in the next few weeks (Congress.gov)
    comments    federal proposed rules open for public comment, soonest closing first (Regulations.gov)

Each part is gathered separately; one source failing leaves the others, and the last good copy of that part.
"""
import datetime
import html
import json
import re
import urllib.parse
import xml.etree.ElementTree as ET

from congress import parse_bill_name
from net import BudgetSpent

HOUSE_WEEK = "https://docs.house.gov/floor/Download.aspx?file=/billsthisweek/{d}/{d}.xml"
HOUSE_PAGE = "https://docs.house.gov/floor/Default.aspx?date={iso}"
SENATE = "https://www.senate.gov/legislative/schedule/floor_schedule.htm"
REGULATIONS = "https://api.regulations.gov/v4/documents"
MAX_MEETING_DETAILS = 120


def _house(net, congress, today):
    monday = today - datetime.timedelta(days=today.weekday())
    weeks = []
    for week in (monday, monday + datetime.timedelta(days=7)):
        d = week.strftime("%Y%m%d")
        try:
            text = net.text(HOUSE_WEEK.format(d=d), browser=True)
        except Exception:
            continue
        if not text.lstrip().startswith("<floorschedule"):
            continue  # no schedule published for that week: the House isn't meeting, or it isn't out yet
        root = ET.fromstring(text)
        items = []
        for category in root.iter("category"):
            for item in category.iter("floor-item"):
                if (item.get("remove-date") or "").strip():
                    continue
                number = " ".join((item.findtext("legis-num") or "").split())
                items.append({
                    "number": number,
                    "bill": parse_bill_name(congress, number),
                    "title": " ".join((item.findtext("floor-text") or "").split()),
                    "how": category.get("type"),
                })
        weeks.append({"week": week.isoformat(), "items": items, "url": HOUSE_PAGE.format(iso=week.isoformat())})
    return weeks


def _senate(net):
    page = net.text(SENATE, browser=True)

    def article(name):
        m = re.search(rf'<article id="{name}".*?</article>', page, re.S)
        return m.group(0) if m else ""

    def clean(fragment):
        return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", fragment)).split())

    nxt = article("proceedings_schedule")
    prev = article("proceedings_schedule_2")
    day = re.search(r"<h3>(.*?)</h3>", nxt, re.S)
    plan = re.search(r'<span class="floor-schedule">(.*?)</span>', nxt, re.S)
    prev_text = re.search(r'<span class="floor-schedule">(.*?)</span>', prev, re.S)
    return {
        "next": clean(day.group(1)) if day else None,
        "plan": clean(plan.group(1)) if plan else None,
        "previous": clean(prev_text.group(1)) if prev_text else None,
        "url": SENATE,
    }


def _hearings(net, store, congress, today):
    saved = {m["id"]: m for m in store.read("upcoming.json", {}).get("hearings", [])}
    since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=21)).strftime("%Y-%m-%dT%H:%M:%SZ")
    fetched = 0
    for chamber in ("house", "senate"):
        offset = 0
        while True:
            page = net.congress(f"/committee-meeting/{congress}/{chamber}", limit=250, offset=offset, fromDateTime=since)
            items = page.get("committeeMeetings", [])
            for m in items:
                key = f"{chamber}/{m.get('eventId')}"
                if key in saved and saved[key].get("updated") == m.get("updateDate"):
                    continue
                if fetched >= MAX_MEETING_DETAILS:
                    break
                d = net.congress(f"/committee-meeting/{congress}/{chamber}/{m.get('eventId')}").get("committeeMeeting", {})
                fetched += 1
                loc = d.get("location") or {}
                saved[key] = {
                    "id": key,
                    "chamber": chamber,
                    "date": d.get("date"),
                    "title": " ".join((d.get("title") or "").split()),
                    "type": d.get("type"),
                    "status": d.get("meetingStatus"),
                    "committees": [c.get("name") for c in d.get("committees", []) if c.get("name")],
                    "room": ", ".join(x for x in (loc.get("room"), loc.get("building")) if x) or None,
                    "bills": [b for b in (parse_bill_name(congress, f"{x.get('type', '')} {x.get('number', '')}")
                                          for x in (d.get("relatedItems") or {}).get("bills", [])) if b],
                    "url": f"https://www.congress.gov/event/{congress}th-congress/{chamber}-event/{m.get('eventId')}",
                    "updated": m.get("updateDate"),
                }
            if len(items) < 250 or fetched >= MAX_MEETING_DETAILS:
                break
            offset += 250
    # Only what's still ahead, soonest first; cancelled meetings stay, marked, so no one shows up to nothing.
    upcoming = [m for m in saved.values() if (m.get("date") or "")[:10] >= today.isoformat()]
    return sorted(upcoming, key=lambda m: m.get("date") or "")


def _comments(net, today):
    docs, page = [], 1
    while page <= 4:
        params = {
            "filter[documentType]": "Proposed Rule",
            "filter[commentEndDate][ge]": today.isoformat(),
            "sort": "commentEndDate",
            "page[size]": 250,
            "page[number]": page,
        }
        data = json.loads(net.regulations(f"{REGULATIONS}?{urllib.parse.urlencode(params)}"))
        for d in data.get("data", []):
            a = d.get("attributes", {})
            docs.append({
                "id": d.get("id"),
                "title": " ".join((a.get("title") or "").split()),
                "agency": a.get("agencyId"),
                "posted": (a.get("postedDate") or "")[:10] or None,
                "closes": (a.get("commentEndDate") or "")[:10] or None,
                "url": f"https://www.regulations.gov/document/{d.get('id')}",
                "comment": f"https://www.regulations.gov/commenton/{d.get('id')}",
            })
        if not data.get("meta", {}).get("hasNextPage"):
            break
        page += 1
    return docs


def gather(net, store, congress, log):
    today = datetime.date.today()
    previous = store.read("upcoming.json", {})
    out = dict(previous)
    parts = {"house": lambda: _house(net, congress, today), "senate": lambda: _senate(net),
             "hearings": lambda: _hearings(net, store, congress, today), "comments": lambda: _comments(net, today)}
    failed = []
    for name, fn in parts.items():
        try:
            out[name] = fn()
        except BudgetSpent:
            failed.append(f"{name} (budget)")
        except Exception as e:  # keep the last good copy of this part
            failed.append(f"{name} ({type(e).__name__})")
    out["checked"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    store.write("upcoming.json", out)
    house_items = sum(len(w["items"]) for w in out.get("house", []))
    log(f"Coming up: House {house_items} items in {len(out.get('house', []))} weeks; Senate next {(out.get('senate') or {}).get('next')}; "
        f"{len(out.get('hearings', []))} hearings; {len(out.get('comments', []))} rules open for comment"
        + (f"; not refreshed: {', '.join(failed)}" if failed else ""))
