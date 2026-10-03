"""
Plain-English bill summaries, written by the Congressional Research Service (CRS) of the Library of Congress,
which is nonpartisan, from the Congress.gov API. CRS writes a new summary as a bill moves (when introduced,
reported, passed by a chamber, enacted), so each summary is labelled with the stage it describes.

Each bill's latest summary goes into its own file, bills/{congress}/{type}/{number}.json, under "summary",
with "stages": every stage CRS has summarized. Listed in bulk, 250 at a time, and only what changed since
the last run.
"""
import datetime
import html
import re

from congress import bill_key


def plain_text(markup):
    """CRS summaries are HTML. Paragraphs and list items become lines; tags and entities go."""
    text = re.sub(r"(?i)<\s*(br|/p|/li|/ul|/ol|/h\d)\s*/?>", "\n", markup or "")
    text = re.sub(r"(?i)<\s*li[^>]*>", "\n• ", text)
    text = html.unescape(re.sub(r"<[^>]+>", "", text))
    lines = [" ".join(line.split()) for line in text.split("\n")]
    return "\n".join(line for line in lines if line).strip()


def gather(net, store, state, congress, log):
    started = datetime.datetime.now(datetime.timezone.utc)
    since = state.get("summariesListedThrough", {}).get(str(congress))
    params = {"limit": 250, "sort": "updateDate asc"}
    if since:
        params["fromDateTime"] = since
    offset, updated, missing = 0, 0, 0
    last_seen, finished = None, False
    try:
        while True:
            page = net.congress(f"/summaries/{congress}", offset=offset, **params)
            items = page.get("summaries", [])
            for s in items:
                last_seen = s.get("updateDate") or last_seen
                bill = s.get("bill") or {}
                key = bill_key(congress, bill.get("type"), bill.get("number"))
                if not key:
                    continue
                path = f"bills/{key}.json"
                record = store.read(path)
                if record is None:
                    # The bill's own details haven't been fetched yet; a later run will add its summary.
                    missing += 1
                    continue
                stage = s.get("actionDesc")
                stages = list(dict.fromkeys((record.get("stages") or []) + ([stage] if stage else [])))
                current = record.get("summary") or {}
                date = s.get("actionDate") or ""
                # Keep the summary of the furthest stage: the latest action date wins.
                if date >= (current.get("date") or ""):
                    record["summary"] = {
                        "text": plain_text(s.get("text")),
                        "stage": stage,
                        "date": date,
                        "version": s.get("versionCode"),
                        "updated": s.get("updateDate"),
                    }
                record["stages"] = stages
                store.write(path, record)
                updated += 1
            if len(items) < 250 or not page.get("pagination", {}).get("next"):
                break
            offset += 250
        finished = True
    finally:
        # Read oldest change first, so a run cut short can pick up from the last one it saw.
        if finished:
            through = (started - datetime.timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
        else:
            through = last_seen[:19] + "Z" if last_seen else since
        if through:
            state.setdefault("summariesListedThrough", {})[str(congress)] = through
        log(f"Bill summaries {congress}: {updated} added or updated" + (f", {missing} waiting for bill details" if missing else "")
            + ("" if finished else "; the rest next run"))
