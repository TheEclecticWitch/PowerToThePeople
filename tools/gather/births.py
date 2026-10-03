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

# One row per member: the birthplace, the U.S. state it lies in (if any) and its country.
QUERY = """
SELECT ?bioguide ?person ?placeLabel ?stateLabel ?countryLabel WHERE {
  VALUES ?bioguide { %s }
  ?person wdt:P1157 ?bioguide ; wdt:P19 ?place .
  OPTIONAL { ?place wdt:P131* ?state . ?state wdt:P31 wd:Q35657 . }
  OPTIONAL { ?place wdt:P17 ?country . }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}"""


def gather(net, store, state, log):
    today = datetime.date.today()
    last = state.get("birthplacesChecked")
    if last and (today - datetime.date.fromisoformat(last)).days < 7:
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
            v = {k: r[k]["value"] for k in ("placeLabel", "stateLabel", "countryLabel") if k in r}
            places[b] = {
                "place": v.get("placeLabel"),
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
    abroad = sum(1 for p in places.values() if p["country"] and p["country"] != "United States")
    log(f"Birthplaces: {len(places)} of {len(ids)} members found, {abroad} born outside the United States")
