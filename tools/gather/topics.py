"""
Bills by subject, for topic alerts in the app.
  bills/{congress}/topics.json   every policy area with its bill count, and the bills that took a step
                                 in the last two weeks, each with its policy area

The policy area is the one subject the Congressional Research Service assigns each bill (Health,
Taxation, ...), read from the bill details. A new bill can wait days for one; it shows up once it has it.
The phone reads this one small file instead of thousands of bills.
"""
import datetime
import re

RECENT_DAYS = 14

# Bills that were only just introduced or sent to committee. Most bills go no further, so the app leaves
# these out unless the reader asks for them. This goes by the kind of step, never by subject or party.
# "Received in the Senate" is not one of them: it means the bill has passed the House.
_INTRODUCED = re.compile(
    r"^(introduced in (the )?(house|senate)\.?|submitted in the (house|senate)\.?"
    r"|sponsor introductory remarks on measure\.?)\s*",
    re.I,
)
_REFERRED = re.compile(r"^(read twice and )?referred to\b", re.I)


def is_early(text):
    """True when an action only introduces a bill or refers it to a committee."""
    # A trailing reference such as "(CR S1234)" says where the text is printed, not what happened.
    t = re.sub(r"\s*\([^()]*\)\.?$", "", (text or "").strip())
    rest = _INTRODUCED.sub("", t, count=1)
    if rest != t and not rest:
        return True
    return bool(_REFERRED.match(rest))


def write(store, congress, index, log, today=None):
    today = today or datetime.date.today()
    since = (today - datetime.timedelta(days=RECENT_DAYS)).isoformat()
    counts, moves = {}, []
    for key, brief in index.items():
        detail = store.read(f"bills/{key}.json") or {}
        area = detail.get("policyArea")
        if not area:
            continue
        counts[area] = counts.get(area, 0) + 1
        action = brief.get("latestAction") or {}
        if (action.get("date") or "") >= since:
            moves.append({
                "bill": key,
                "title": brief.get("title"),
                "policyArea": area,
                "action": {"date": action.get("date"), "text": action.get("text")},
                "early": is_early(action.get("text")),
            })
    moves.sort(key=lambda m: (m["action"]["date"] or "", m["bill"]), reverse=True)
    store.write(f"bills/{congress}/topics.json", {
        "congress": congress,
        "since": since,
        "topics": [{"name": n, "bills": c} for n, c in sorted(counts.items())],
        "moves": moves,
    })
    log(f"Topics {congress}: {len(counts)} policy areas, {len(moves)} bills moved since {since} "
        f"({sum(not m['early'] for m in moves)} past introduction)")


if __name__ == "__main__":
    # A quick self-check of the early-step test: python topics.py
    early = [
        "Read twice and referred to the Committee on Finance.",
        "Referred to the House Committee on Ways and Means.",
        "Referred to the Subcommittee on Health.",
        "Introduced in House",
        "Sponsor introductory remarks on measure. (CR S1234)",
    ]
    later = [
        "Introduced in the Senate, read twice, considered, read the third time, and passed without amendment by Unanimous Consent.",
        "Submitted in the Senate, considered, and agreed to without amendment and with a preamble by Unanimous Consent.",
        "Placed on Senate Legislative Calendar under General Orders. Calendar No. 412.",
        "Ordered to be Reported by the Yeas and Nays: 48 - 0.",
        "Became Public Law No: 119-117.",
        "Received in the Senate.",
        "Received in the Senate and Read twice and referred to the Committee on the Judiciary.",
    ]
    bad = [t for t in early if not is_early(t)] + [t for t in later if is_early(t)]
    print("ok" if not bad else f"wrong: {bad}")
