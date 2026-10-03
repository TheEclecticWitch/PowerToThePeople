"""
The Doomsday Clock, as the Bulletin of the Atomic Scientists sets it (usually once a year, in January).
Read from the sentence on the Bulletin's own page: "On January 27, 2026, the Doomsday Clock was set at 85
seconds to midnight". The file only changes when the Bulletin moves the clock.
"""
import datetime
import html
import re

PAGE = "https://thebulletin.org/doomsday-clock/"

_SET = re.compile(r"On (\w+ \d{1,2}, \d{4}), the Doomsday Clock was set at (\d+(?:\.\d+)?) (seconds|minutes) to midnight", re.I)
_NOW = re.compile(r"It is now (\d+(?:\.\d+)?) (seconds|minutes) to midnight", re.I)


def parse(page):
    """(seconds to midnight, the date it was set or None), or None if the page no longer says."""
    text = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", page)))
    m = _SET.search(text)
    if m:
        amount = float(m.group(2)) * (60 if m.group(3).lower() == "minutes" else 1)
        try:
            when = datetime.datetime.strptime(m.group(1), "%B %d, %Y").date().isoformat()
        except ValueError:
            when = None
        return round(amount), when
    m = _NOW.search(text)
    if m:
        return round(float(m.group(1)) * (60 if m.group(2).lower() == "minutes" else 1)), None
    return None


def gather(net, store, log):
    found = parse(net.text(PAGE, browser=True))
    if found is None:
        raise ValueError("the Bulletin's page no longer states the setting in a form this reader knows")
    seconds, when = found
    previous = store.read("doomsday.json", {})
    if when is None and previous.get("seconds") == seconds:
        when = previous.get("set")
    store.write("doomsday.json", {
        "seconds": seconds,
        "set": when,
        "source": PAGE,
        "sourceName": "Bulletin of the Atomic Scientists",
    })
    log(f"Doomsday Clock: {seconds} seconds to midnight" + (f", set {when}" if when else ""))
