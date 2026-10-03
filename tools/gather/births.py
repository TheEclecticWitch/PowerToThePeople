"""
Where each member of Congress was born, from Wikidata, matched by their Biographical Directory (bioguide) id.
  birthplaces.json   bioguide -> place, state (U.S. births) or country, and the Wikidata item it came from

The official Biographical Directory has birthplaces too, but sits behind a bot check that turns away the
gatherer, so Wikidata is used and named as the source on every entry. Birthplaces don't change, so this
runs once a week.
"""
import datetime
import json
import urllib.parse

ENDPOINT = "https://query.wikidata.org/sparql"
BATCH = 100
VERSION = 2  # raise to rebuild on the next run instead of waiting a week (2: hospitals give their town)

# One row per member: the birthplace, the U.S. state it lies in (if any) and its country.
QUERY = """
SELECT ?bioguide ?person ?placeLabel ?townLabel ?stateLabel ?countryLabel WHERE {
  VALUES ?bioguide { %s }
  ?person wdt:P1157 ?bioguide ; wdt:P19 ?place .
  OPTIONAL { ?place wdt:P131* ?state . ?state wdt:P31 wd:Q35657 . }
  OPTIONAL { ?place wdt:P17 ?country . }
  OPTIONAL { ?place wdt:P31/wdt:P279* wd:Q16917 ; wdt:P131 ?town . }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}"""


def gather(net, store, state, log):
    today = datetime.date.today()
    last = state.get("birthplacesChecked")
    if last and (today - datetime.date.fromisoformat(last)).days < 7 and state.get("birthplacesVersion") == VERSION:
        return
    ids = [name[:-5] for name in store.list_dir("members") if name.endswith(".json")]
    places = {}
    for i in range(0, len(ids), BATCH):
        values = " ".join(f'"{b}"' for b in ids[i:i + BATCH])
        url = ENDPOINT + "?" + urllib.parse.urlencode({"query": QUERY % values, "format": "json"})
        rows = json.loads(net.text(url))["results"]["bindings"]
        for r in rows:
            b = r["bioguide"]["value"]
            if b in places:
                continue  # A place in two counties or countries: the first is enough.
            v = {k: r[k]["value"] for k in ("placeLabel", "townLabel", "stateLabel", "countryLabel") if k in r}
            places[b] = {
                # A hospital is recorded for some; the town it stands in reads better ("Reading", not "Reading Hospital").
                "place": v.get("townLabel") or v.get("placeLabel"),
                "state": v.get("stateLabel"),
                "country": v.get("countryLabel"),
                "wikidata": r["person"]["value"].replace("http://", "https://"),
            }
    store.write("birthplaces.json", {
        "checked": today.isoformat(),
        "source": "Wikidata",
        "sourceUrl": "https://www.wikidata.org/",
        "members": places,
    })
    state["birthplacesChecked"] = today.isoformat()
    state["birthplacesVersion"] = VERSION
    abroad = sum(1 for p in places.values() if p["country"] and p["country"] != "United States")
    log(f"Birthplaces: {len(places)} of {len(ids)} members found, {abroad} born outside the United States")
