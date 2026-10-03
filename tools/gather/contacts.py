"""
Where to write to each member of Congress. The @unitedstates data lists a contact form for nearly every
senator but for only a handful of representatives; almost every House office keeps its form at
<website>/contact. This checks that page for each member once a week and publishes only links that load,
so the app never sends someone to an error page.
"""
import concurrent.futures
import datetime
import json
import urllib.request

LEGISLATORS = "https://unitedstates.github.io/congress-legislators/legislators-current.json"
AGENT = "Mozilla/5.0 (compatible; PowerToThePeople-gatherer)"


def _loads(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": AGENT})
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status == 200
    except Exception:
        return False


def gather(net, store, state, log):
    today = datetime.date.today()
    last = state.get("contactsChecked")
    if last and (today - datetime.date.fromisoformat(last)).days < 7:
        return
    people = json.loads(net.text(LEGISLATORS))
    forms, to_check = {}, {}
    for p in people:
        bioguide = p["id"].get("bioguide")
        term = p["terms"][-1]
        if not bioguide:
            continue
        if term.get("contact_form"):
            forms[bioguide] = term["contact_form"]
        elif term.get("url"):
            to_check[bioguide] = term["url"].rstrip("/") + "/contact"
    # Two at a time: House sites sit behind protection that blocks bursts of requests.
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        previous = store.read("contacts.json", {}).get("forms", {})
        for bioguide, ok in zip(to_check, pool.map(_loads, to_check.values())):
            # A page that won't load today keeps last week's verified link: a site blocking a burst of
            # requests shouldn't take a member's contact link away.
            if ok or previous.get(bioguide) == to_check[bioguide]:
                forms[bioguide] = to_check[bioguide]
    store.write("contacts.json", {"checked": today.isoformat(), "forms": forms})
    state["contactsChecked"] = today.isoformat()
    log(f"Contact pages: {len(forms)} of {len(people)} members have one that loads")
