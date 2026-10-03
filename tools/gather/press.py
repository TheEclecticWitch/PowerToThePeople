"""
Official news: members' press releases and office news, from the feeds their own websites publish, and the
White House's own news feed. Only what the offices themselves publish; no social media.

Each member's feed is looked for about once a week: the address listed in the @unitedstates data if it works,
else the usual places House and Senate sites keep one. Between times, only feeds known to work are read.

  press/{bioguide}.json    a member's latest press releases
  press/whitehouse.json    the White House's latest news
  press/index.json         which members have a feed that worked this run

A feed that fails is skipped quietly: many offices change sites without updating their feed address.
"""
import concurrent.futures
import datetime
import email.utils
import json
import urllib.request
import xml.etree.ElementTree as ET

LEGISLATORS = "https://unitedstates.github.io/congress-legislators/legislators-current.json"
WHITE_HOUSE = "https://www.whitehouse.gov/news/feed/"
AGENT = "Mozilla/5.0 (compatible; PowerToThePeople-gatherer)"
KEEP = 15
# Where member sites usually keep a feed, press releases first.
FEED_PATHS = ["/rss/feeds/?type=press", "/rss.xml", "/feed"]
# House and Senate sites sit behind protection that blocks a burst of requests, so feeds are looked for a
# few members at a time: 80 a run, four runs a day, so each member is rechecked about weekly.
DISCOVER_PER_RUN = 80
REDISCOVER_DAYS = 7


def _date(text):
    """RSS dates ("Wed, 01 Oct 2026 14:00:00 -0400") -> "2026-10-01"; ISO dates pass through."""
    if not text:
        return None
    text = text.strip()
    try:
        return email.utils.parsedate_to_datetime(text).date().isoformat()
    except (TypeError, ValueError):
        return text[:10] if text[:4].isdigit() else None


def _items(xml_bytes):
    root = ET.fromstring(xml_bytes)
    out = []
    # RSS 2.0 <item>, or Atom <entry>.
    for item in root.iter():
        tag = item.tag.rsplit("}", 1)[-1]
        if tag not in ("item", "entry"):
            continue
        fields = {c.tag.rsplit("}", 1)[-1]: c for c in item}
        title = (fields.get("title").text or "").strip() if fields.get("title") is not None else ""
        link_el = fields.get("link")
        link = None
        if link_el is not None:
            link = (link_el.text or "").strip() or link_el.get("href")
        when = None
        for key in ("pubDate", "published", "updated", "date"):
            if fields.get(key) is not None:
                when = _date(fields[key].text)
                break
        if title and link and link.startswith("http"):
            out.append({"title": " ".join(title.split()), "url": link, "date": when})
    out.sort(key=lambda i: i.get("date") or "", reverse=True)
    return out[:KEEP]


# A feed with nothing this recent is a leftover (a site's default feed holding a 2022 "Test Post"), not news.
FRESH_DAYS = 183


def _fetch(url):
    try:
        req = urllib.request.Request(url.replace("&amp;", "&"), headers={"User-Agent": AGENT})
        with urllib.request.urlopen(req, timeout=20) as r:
            items = _items(r.read())
    except Exception:
        return None
    cutoff = (datetime.date.today() - datetime.timedelta(days=FRESH_DAYS)).isoformat()
    return items if items and (items[0].get("date") or "") >= cutoff else None


def _discover(listed, website):
    for url in ([listed] if listed else []) + ([website.rstrip("/") + p for p in FEED_PATHS] if website else []):
        if _fetch(url):
            return url
    return None


def gather(net, store, state, log):
    today = datetime.date.today()
    req = urllib.request.Request(LEGISLATORS, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(req, timeout=60) as r:
        people = json.loads(r.read())
    current = {p["id"]["bioguide"]: p["terms"][-1] for p in people if p["id"].get("bioguide")}
    feeds = {k: v for k, v in state.get("pressFeeds", {}).items() if k in current}
    looked = {k: v for k, v in state.get("pressLooked", {}).items() if k in current}

    def stale(b):
        return b not in looked or (today - datetime.date.fromisoformat(looked[b])).days >= REDISCOVER_DAYS

    due = sorted((b for b in current if stale(b)), key=lambda b: looked.get(b, ""))[:DISCOVER_PER_RUN]
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        for b, url in zip(due, pool.map(lambda b: _discover(current[b].get("rss_url"), current[b].get("url")), due)):
            looked[b] = today.isoformat()
            if url:
                feeds[b] = url
            else:
                feeds.pop(b, None)
    state["pressFeeds"] = feeds
    state["pressLooked"] = looked
    working = []
    with concurrent.futures.ThreadPoolExecutor(3) as pool:
        for bioguide, items in zip(feeds, pool.map(_fetch, feeds.values())):
            if items:
                store.write(f"press/{bioguide}.json", {"member": bioguide, "feed": feeds[bioguide], "items": items})
                working.append(bioguide)
    # A feed that fails this run keeps its last good file; the index lists every member who has one.
    have = sorted(set(working) | {f[:-5] for f in store.list_dir("press") if f[:-5] in feeds})
    store.write("press/index.json", {"members": have})
    white_house = _fetch(WHITE_HOUSE)
    if white_house:
        store.write("press/whitehouse.json", {"source": "https://www.whitehouse.gov/news/", "items": white_house})
    log(f"Office news: {len(working)} feeds read, {len(have)} of {len(current)} members have news, "
        f"{len(due)} looked for this run; White House: {len(white_house or [])} items")
