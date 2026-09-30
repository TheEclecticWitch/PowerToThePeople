"""
Shared facts about Congress: which Congress is sitting, bill-type names, and the public
congress.gov link for a bill, so every record the app shows can link to its source.
"""
import datetime
import re

BILL_TYPES = {
    "hr": "house-bill",
    "s": "senate-bill",
    "hjres": "house-joint-resolution",
    "sjres": "senate-joint-resolution",
    "hconres": "house-concurrent-resolution",
    "sconres": "senate-concurrent-resolution",
    "hres": "house-resolution",
    "sres": "senate-resolution",
}


def current_congress(today=None):
    """The Congress sitting on a date. A new one starts January 3 of each odd year."""
    today = today or datetime.date.today()
    year = today.year
    if year % 2 == 1 and today < datetime.date(year, 1, 3):
        year -= 1
    return (year - 1789) // 2 + 1


def sessions_of(congress):
    """Session 1 is the Congress's first year, session 2 its second."""
    first_year = 1789 + (congress - 1) * 2
    return [(1, first_year), (2, first_year + 1)]


def ordinal(n):
    suffix = "th" if 10 <= n % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f"{n}{suffix}"


def bill_key(congress, bill_type, number):
    """The app's id for a bill, e.g. '119/hr/1075'. Returns None for anything that isn't a bill."""
    t = (bill_type or "").lower().replace(".", "").replace(" ", "")
    if t not in BILL_TYPES or not str(number or "").isdigit():
        return None
    return f"{congress}/{t}/{int(number)}"


def parse_bill_name(congress, name):
    """Turns a Senate document name like 'H.R. 9340' or 'S.J.Res. 12' into a bill key."""
    m = re.fullmatch(r"([A-Za-z.\s]+?)\s*(\d+)", (name or "").strip())
    return bill_key(congress, m.group(1), m.group(2)) if m else None


def bill_url(key):
    congress, t, number = key.split("/")
    return f"https://www.congress.gov/bill/{ordinal(int(congress))}-congress/{BILL_TYPES[t]}/{number}"
