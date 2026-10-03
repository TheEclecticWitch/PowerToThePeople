"""
This Week in Congress: a short spoken briefing, written each Saturday from the data gathered here and read
by a computer voice (Piper, voice "Norman", public domain).
  briefing/latest.json       the week, the script paragraph by paragraph, and the audio file's name
  briefing/{date}.mp3        the reading; the last four weeks are kept

Only what the records say: who voted how many to how many, what passed, what became law, what is scheduled.
No adjectives about bills or people, no opinions, no ranking beyond the kind of vote. The script is built
from fixed sentence patterns, so the same week always reads the same way.
"""
import datetime
import os
import re

KEEP_WEEKS = 4
MAX_VOTES_SPOKEN = 8   # per chamber; the rest are counted
MAX_NAMED = 3          # bills named among those passed without a recorded vote
SENTENCE_PAUSE = 0.35  # seconds after each sentence
PARAGRAPH_PAUSE = 1.1  # seconds between paragraphs

SPOKEN_TYPES = {
    "hr": "House bill", "s": "Senate bill",
    "hres": "House resolution", "sres": "Senate resolution",
    "hjres": "House joint resolution", "sjres": "Senate joint resolution",
    "hconres": "House concurrent resolution", "sconres": "Senate concurrent resolution",
}
LONG_TITLE = re.compile(r"^(to |a bill|a resolution|a joint resolution|a concurrent resolution|providing|expressing|"
                        r"recognizing|supporting|designating|honoring|condemning|calling|requesting|relating)", re.I)


def ordinal(n):
    return f"{n}{'th' if 11 <= n % 100 <= 13 else {1: 'st', 2: 'nd', 3: 'rd'}.get(n % 10, 'th')}"


def long_date(d):
    return f"{d.strftime('%A, %B')} {ordinal(d.day)}, {d.year}"


def bill_name(key, index):
    """'the Protect College Sports Act' when the bill has a short name, else 'Senate bill 4668'."""
    _, t, number = key.split("/")
    title = ((index.get(key) or {}).get("title") or "").strip().rstrip(".")
    if title and len(title) <= 70 and not LONG_TITLE.match(title):
        return title if title.lower().startswith("the ") else f"the {title}"
    return f"{SPOKEN_TYPES.get(t, 'bill')} {number}"


def _counts(v):
    t = v.get("totals") or {}
    yes = t.get("Yea", 0) + t.get("Aye", 0) + t.get("Guilty", 0)
    no = t.get("Nay", 0) + t.get("No", 0) + t.get("Not Guilty", 0)
    return f"{yes} to {no}"


def _passed(result):
    r = (result or "").lower()
    if any(w in r for w in ("rejected", "failed", "not invoked", "not agreed", "not sustained", "defeated")):
        return False
    return any(w in r for w in ("passed", "agreed", "confirmed", "invoked", "adopted"))


def _nominee(title):
    """('Keith Sonderling', 'Secretary of Labor') from 'Confirmation: Keith Sonderling, of F.L., to be Secretary of Labor'."""
    m = re.search(r":\s*(.+?)(?:,\s*of [^,]+,)?\s+to be (?:an? |the )?(.+)$", title or "")
    return (m.group(1).strip(), m.group(2).strip().rstrip(".")) if m else None


def describe(v, chamber, index):
    """One sentence for a roll call, and how much it matters for choosing which to read (lower is first),
    or None for amendments and procedure, which are only counted."""
    q = (v.get("question") or "").lower()
    title = v.get("title") or ""
    thing = bill_name(v["bill"], index) if v.get("bill") else None
    ok, counts, house = _passed(v.get("result")), _counts(v), chamber.capitalize()
    if "amendment" in q and "concur" not in q:
        return None
    if "nomination" in q and "cloture" not in q:
        who = _nominee(title)
        if who:
            name, role = who
            return (f"the {house} confirmed {name} to be {role}, {counts}." if ok
                    else f"the {house} rejected the nomination of {name} to be {role}, {counts}."), 0
        return None
    if "cloture" in q:
        who = _nominee(title) if "nomination" in q or "PN" in (v.get("question") or "") else None
        target = f"the nomination of {who[0]}" if who else thing
        if not target:
            return None
        step = "begin debate on" if "proceed" in q else "end debate on"
        return (f"the {house} voted to {step} {target}, {counts}." if ok
                else f"a vote to {step} {target} fell short of the votes needed, {counts}."), 1
    if not thing:
        return None
    if "suspend the rules" in q:
        if ok:
            # Several of these on one day are read as one sentence with a list.
            return f"the {house} passed {thing} under a fast-track rule that needs two thirds, {counts}.", 0, f"{thing}, {counts}"
        return f"{thing} did not get the two thirds needed to pass under a fast-track rule, {counts}.", 0
    if "passage" in q:
        return (f"the {house} passed {thing}, {counts}." if ok else f"{thing} failed in the {house}, {counts}."), 0
    if "concur" in q:
        return (f"the {house} agreed to the other chamber's changes to {thing}, {counts}." if ok
                else f"the {house} rejected the other chamber's changes to {thing}, {counts}."), 0
    if "agreeing to the resolution" in q or "on the resolution" in q or "joint resolution" in q:
        return (f"the {house} adopted {thing}, {counts}." if ok else f"the {house} rejected {thing}, {counts}."), 0
    if "discharge" in q:
        return f"a motion to bring {thing} out of committee {'succeeded' if ok else 'fell short'}, {counts}.", 1
    if "motion to proceed" in q:
        return (f"the {house} voted to take up {thing}, {counts}." if ok
                else f"a motion to take up {thing} fell short, {counts}."), 1
    return None


def chamber_paragraphs(chamber, votes, index):
    house = chamber.capitalize()
    if not votes:
        return [f"The {house} held no recorded votes this week."]
    spoken = []
    for v in votes:
        d = describe(v, chamber, index)
        if d:
            spoken.append((d[1], v["date"], v["roll"], d[0], d[2] if len(d) > 2 else None))
    chosen = sorted(sorted(spoken)[:MAX_VOTES_SPOKEN], key=lambda s: (s[1], s[2]))
    n = len(votes)
    out = [f"The {house} held {words(n)} recorded vote{'s' if n != 1 else ''} this week."]
    # A day's fast-track passages are grouped into one sentence; everything else is a sentence of its own.
    groups = []
    for _, date, _, sentence, item in chosen:
        if item and groups and groups[-1][0] == date and groups[-1][2]:
            groups[-1][2].append(item)
        else:
            groups.append([date, sentence, [item] if item else None])
    last_day = None
    for date, sentence, items in groups:
        day = datetime.date.fromisoformat(date).strftime("%A")
        lead = f"On {day}, " if day != last_day else f"Also on {day}, "
        last_day = day
        if items and len(items) > 1:
            sentence = (f"the {house} passed {words(len(items))} bills under a fast-track rule that needs two thirds: "
                        + _join(items, "; ") + ".")
        out.append(lead + sentence)
    rest = n - len(chosen)
    if rest:
        out.append(f"The {house} also held {words(rest)} other vote{'s' if rest != 1 else ''}. Every vote is listed in the app.")
    return out


def _passed_quietly(text, chamber):
    t = (text or "").lower()
    return (t.startswith(f"passed {chamber}") or t.startswith("submitted in the senate, considered, and agreed")
            or (chamber == "senate" and "agreed to in senate" in t)) and "record vote" not in t and "yea-nay" not in t


def build_script(store, congress, today):
    start = today - datetime.timedelta(days=6)
    index = store.read(f"bills/{congress}/index.json", {}).get("bills", {})
    in_week = lambda d: d and start.isoformat() <= d <= today.isoformat()

    paragraphs = [
        f"This is This Week in Congress, from Power to the People, for the week ending {long_date(today)}.",
        "This briefing is read by a computer voice. Every fact in it comes from public records at Congress.gov, "
        "the House Clerk and Senate.gov.",
    ]
    for chamber in ("senate", "house"):
        votes = []
        for session in (1, 2):
            votes += [v for v in store.read(f"votes/{chamber}/{congress}-{session}.json", {}).get("votes", [])
                      if in_week(v.get("date"))]
        votes.sort(key=lambda v: (v["date"], v["roll"]))
        paragraphs += chamber_paragraphs(chamber, votes, index)

    for chamber in ("senate", "house"):
        quiet = [(b["latestAction"]["date"], k) for k, b in index.items()
                 if in_week((b.get("latestAction") or {}).get("date")) and _passed_quietly(b["latestAction"].get("text"), chamber)]
        if not quiet:
            continue
        quiet.sort(reverse=True)
        named = [bill_name(k, index) for _, k in quiet]
        named = [n for n in named if n.startswith("the ")][:MAX_NAMED]
        n = len(quiet)
        line = (f"The {chamber.capitalize()} also passed {words(n)} measures without a recorded vote."
                if n > 1 else f"The {chamber.capitalize()} also passed one measure without a recorded vote.")
        if named:
            line += " Among them " + ("was " if len(named) == 1 else "were ") + _join(named) + "."
        paragraphs.append(line)

    laws = sorted(k for k, b in index.items()
                  if in_week((b.get("latestAction") or {}).get("date")) and (b["latestAction"].get("text") or "").startswith("Became Public Law"))
    if laws:
        names = [bill_name(k, index) for k in laws]
        paragraphs.append(("One bill became law: " if len(laws) == 1 else f"{words(len(laws)).capitalize()} bills became law: ") + _join(names) + ".")
    else:
        paragraphs.append("No bills became law this week.")

    up = store.read("upcoming.json", {})
    senate = up.get("senate") or {}
    coming = []
    if senate.get("next"):
        try:
            when = long_date(datetime.datetime.strptime(senate["next"], "%A, %b %d, %Y").date())
        except ValueError:
            when = senate["next"]
        plan = (senate.get("plan") or "").rstrip(".")
        coming.append(f"Coming up, the Senate next meets {when}" + (f". The plan: {plan[0].lower() + plan[1:]}." if plan else "."))
    weeks = [w for w in up.get("house") or [] if w.get("items")]
    if weeks:
        w = weeks[0]
        n = len(w["items"])
        coming.append(f"The House has {n} bill{'s' if n != 1 else ''} on its schedule for the week of "
                      f"{long_date(datetime.date.fromisoformat(w['week']))}.")
    else:
        coming.append("The House has not posted a floor schedule.")
    paragraphs.append(" ".join(coming))
    paragraphs.append("That's this week in Congress. You can read every bill and every vote in the app. Thanks for listening.")
    return start, paragraphs


def _join(items, sep=", "):
    if len(items) == 1:
        return items[0]
    # With "; " even two items keep it, so "371 to 33; and the next bill" doesn't run together.
    return sep.join(items[:-1]) + (f"{sep}and " if len(items) > 2 or sep != ", " else " and ") + items[-1]


def words(n):
    """Small counts as words, which read better aloud and at the start of a sentence."""
    return ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"][n] if 0 <= n <= 10 else str(n)


def spoken(text):
    """What the voice should say for what the reader sees."""
    return text.replace(".gov", " dot gov").replace(" & ", " and ")


def render(paragraphs, path, voice_path):
    """Reads the script aloud into an MP3. Returns its length in seconds."""
    import lameenc
    from piper import PiperVoice
    voice = PiperVoice.load(voice_path)
    rate = voice.config.sample_rate
    pause = lambda s: b"\0\0" * int(rate * s)
    pcm = b""
    for p in paragraphs:
        for chunk in voice.synthesize(spoken(p)):
            pcm += chunk.audio_int16_bytes + pause(SENTENCE_PAUSE)
        pcm += pause(PARAGRAPH_PAUSE - SENTENCE_PAUSE)
    enc = lameenc.Encoder()
    enc.set_bit_rate(64)
    enc.set_in_sample_rate(rate)
    enc.set_channels(1)
    enc.set_quality(2)
    with open(path, "wb") as f:
        f.write(enc.encode(pcm) + enc.flush())
    return round(len(pcm) / 2 / rate)


def gather(store, congress, log, now=None):
    """Writes this week's briefing on Saturdays (from the midday run on), or at once if there is none yet."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    today = now.date()
    latest = store.read("briefing/latest.json")
    due = os.environ.get("BRIEFING_FORCE") == "1" or not latest or (
        today.weekday() == 5 and now.hour >= 12 and latest.get("weekEnding") != today.isoformat())
    if not due:
        return
    start, paragraphs = build_script(store, congress, today)
    voice = os.environ.get("PIPER_VOICE")
    audio, seconds = None, None
    if voice and os.path.exists(voice):
        audio = f"briefing/{today.isoformat()}.mp3"
        os.makedirs(store.path("briefing"), exist_ok=True)
        seconds = render(paragraphs, store.path(audio), voice)
        store.written += 1
    else:
        log("Briefing: no voice installed (PIPER_VOICE), so the script is published without audio")
    store.write("briefing/latest.json", {
        "weekStarting": start.isoformat(),
        "weekEnding": today.isoformat(),
        "made": now.strftime("%Y-%m-%dT%H:%MZ"),
        "paragraphs": paragraphs,
        "audio": audio,
        "seconds": seconds,
        "voice": "Norman (Piper text to speech, public domain)",
        "sources": ["https://www.congress.gov/", "https://clerk.house.gov/Votes", "https://www.senate.gov/legislative/votes_new.htm"],
    })
    # Only the last few readings are kept; the scripts live on in the data's history.
    readings = sorted(n for n in store.list_dir("briefing") if n.endswith(".mp3"))
    for old in readings[:-KEEP_WEEKS]:
        os.remove(store.path(f"briefing/{old}"))
    log(f"Briefing: week ending {today}, {len(paragraphs)} paragraphs" + (f", {seconds}s of audio" if seconds else ""))
