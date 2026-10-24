"""
The Census Bureau's American Community Survey (5-year estimates), for every area the app can place a
reader in: their state, congressional district, state legislative districts, county, town and, in the
states where townships are governments, their township.

One file per state (census/acs/MD.json) and one for the whole country (census/acs/US.json). The
survey comes out once a year, so a run only fetches when a newer year is published or this file's
VERSION changes.

Needs CENSUS_API_KEY (free from api.census.gov/data/key_signup.html); without it the Census API
turns every request away, and this step is skipped.
"""
import json
import os
import urllib.parse

API = "https://api.census.gov/data"
VERSION = 3
# Citizenship by year from the 1-year survey, which began in 2005. 2020's was never released as
# usual: the pandemic cut its responses, so the Census Bureau published it only as experimental.
HISTORY_START = 2005
HISTORY_SKIP = {2020}
HISTORY_VARIABLES = ["B05001_001E", "B05001_002E", "B05001_003E", "B05001_004E", "B05001_005E", "B05001_006E"]

# Areas the app shows, as the API names them, with the key prefix used in the published file.
AREAS = [
    ("congressional district", "cd"),
    ("state legislative district (upper chamber)", "sldu"),
    ("state legislative district (lower chamber)", "sldl"),
    ("county", "county"),
    ("place", "place"),
]
# The states whose townships (county subdivisions) are real local governments.
TOWNSHIP_STATES = {"09", "23", "25", "26", "27", "33", "34", "36", "42", "44", "50", "55"}
# Country of birth is only kept for the larger areas: it is 180 numbers per area.
BIRTH_AREAS = {"cd", "county"}

# Each published field and the survey's variables behind it. A list is a sum.
FIELDS = {
    "pop": ["B01003_001E"],
    "age": ["B01002_001E"],
    "income": ["B19013_001E"],
    "povertyTotal": ["B17001_001E"],
    "poverty": ["B17001_002E"],
    "homes": ["B25003_001E"],
    "owned": ["B25003_002E"],
    "rented": ["B25003_003E"],
    "adults25": ["B15003_001E"],
    "bachelors": ["B15003_022E", "B15003_023E", "B15003_024E", "B15003_025E"],
    "adults18": ["B21001_001E"],
    "veterans": ["B21001_002E"],
    "insuranceTotal": ["B27010_001E"],
    "uninsured": ["B27010_017E", "B27010_033E", "B27010_050E", "B27010_066E"],
    "speakers": ["C16001_001E"],
    "englishOnly": ["C16001_002E"],
    "spanish": ["C16001_003E"],
    # Nativity and citizenship (B05001): everyone, by how they are or aren't a citizen.
    "citTotal": ["B05001_001E"],
    "bornUS": ["B05001_002E"],
    "bornTerritories": ["B05001_003E"],
    "bornAbroadToCitizens": ["B05001_004E"],
    "naturalized": ["B05001_005E"],
    "notCitizen": ["B05001_006E"],
}
VARIABLES = sorted({v for vs in FIELDS.values() for v in vs})

STATES = {
    "01": "AL", "02": "AK", "04": "AZ", "05": "AR", "06": "CA", "08": "CO", "09": "CT", "10": "DE", "11": "DC",
    "12": "FL", "13": "GA", "15": "HI", "16": "ID", "17": "IL", "18": "IN", "19": "IA", "20": "KS", "21": "KY",
    "22": "LA", "23": "ME", "24": "MD", "25": "MA", "26": "MI", "27": "MN", "28": "MS", "29": "MO", "30": "MT",
    "31": "NE", "32": "NV", "33": "NH", "34": "NJ", "35": "NM", "36": "NY", "37": "NC", "38": "ND", "39": "OH",
    "40": "OK", "41": "OR", "42": "PA", "44": "RI", "45": "SC", "46": "SD", "47": "TN", "48": "TX", "49": "UT",
    "50": "VT", "51": "VA", "53": "WA", "54": "WV", "55": "WI", "56": "WY", "72": "PR",
}


class Census:
    def __init__(self, net, key):
        self.net = net
        self.key = key

    def get(self, vintage, get, geo, within=None, survey="acs5"):
        """Rows of the survey as dicts. The key goes last so a logged URL can be cut before it."""
        params = [("get", get), ("for", geo)]
        if within:
            params.append(("in", within))
        url = f"{API}/{vintage}/acs/{survey}?{urllib.parse.urlencode(params)}&key={self.key}"
        text = self.net.text(url).strip()
        if not text:
            return []  # 204: no such areas here (Nebraska has one house, D.C. no districts).
        if text.startswith("<"):
            raise RuntimeError("the Census API refused the key")
        rows = json.loads(text)
        return [dict(zip(rows[0], r)) for r in rows[1:]]

    def get_2000(self, get, geo, within):
        """The 2000 census long form (Summary File 3), the last census to ask where people were born."""
        params = [("get", get), ("for", geo), ("in", within)]
        url = f"{API}/2000/dec/sf3?{urllib.parse.urlencode(params)}&key={self.key}"
        text = self.net.text(url).strip()
        if not text or text.startswith("<"):
            return []
        rows = json.loads(text)
        return [dict(zip(rows[0], r)) for r in rows[1:]]


def number(value):
    """The survey marks missing or unreliable figures with large negative numbers."""
    try:
        n = float(value)
    except (TypeError, ValueError):
        return None
    if n < 0:
        return None
    return int(n) if n == int(n) else n


def record(row):
    out = {"name": row.get("NAME")}
    for field, variables in FIELDS.items():
        values = [number(row.get(v)) for v in variables]
        out[field] = None if any(v is None for v in values) else sum(values)
    return out


def latest_vintage(net, this_year):
    """The newest 5-year survey published: it comes out each December for the year before last."""
    for year in range(this_year - 1, this_year - 4, -1):
        try:
            net.text(f"{API}/{year}/acs/acs5/variables/B01003_001E.json")
            return year
        except Exception:
            continue
    raise RuntimeError("no 5-year survey found")


def country_columns(net, vintage):
    """B05006's countries: the rows that are a single country, not a region or an 'Other' catch-all."""
    groups = json.loads(net.text(f"{API}/{vintage}/acs/acs5/groups/B05006.json"))["variables"]
    out = {}
    for var, meta in groups.items():
        if not var.endswith("E"):
            continue
        label = meta["label"].split("!!")[-1]
        if label.endswith(":") or label.startswith("Other") or label == "Total:":
            continue
        out[var] = label.replace(", excluding England and Scotland", "")
    return out


def birthplaces(census, vintage, countries, geo, within=None):
    """The ten most common countries of birth in each area, keyed by the area's code."""
    columns = list(countries)
    rows = {}
    # 49 columns at a time: the API takes at most 50.
    for start in range(0, len(columns), 49):
        for row in census.get(vintage, ",".join(columns[start:start + 49]), f"{geo}:*", within):
            rows.setdefault(row[geo], {}).update(row)
    out = {}
    for code, row in rows.items():
        counts = sorted(((countries[c], number(row.get(c))) for c in columns), key=lambda x: -(x[1] or 0))
        out[code] = [[n, c] for n, c in counts[:10] if c]
    return out


def gather(net, store, today, log):
    key = os.environ.get("CENSUS_API_KEY")
    if not key:
        log("Census: no CENSUS_API_KEY; skipped")
        return
    census = Census(net, key)
    vintage = latest_vintage(net, today.year)
    done = store.read("census/acs/state.json", {})
    if done.get("vintage") == vintage and done.get("version") == VERSION and done.get("complete"):
        log(f"Census: {vintage} survey already published")
        return

    countries = country_columns(net, vintage)
    source = {
        "name": f"U.S. Census Bureau, American Community Survey {vintage - 4}-{vintage} 5-year estimates",
        "url": f"https://data.census.gov/table?d=ACS+5-Year+Estimates+Detailed+Tables&y={vintage}",
        "tables": "B01002, B01003, B05001, B05006, B15003, B17001, B19013, B21001, B25003, B27010, C16001",
    }

    us = record(census.get(vintage, "NAME," + ",".join(VARIABLES), "us:1")[0])
    us["bornIn"] = birthplaces(census, vintage, countries, "us").get("1", [])
    store.write("census/acs/US.json", {"vintage": vintage, "source": source, "areas": {"us": us}})

    state_rows = {r["state"]: r for r in census.get(vintage, "NAME," + ",".join(VARIABLES), "state:*")}
    state_born = birthplaces(census, vintage, countries, "state")
    for fips, abbr in STATES.items():
        if fips not in state_rows:
            continue
        areas = {"state": {**record(state_rows[fips]), "bornIn": state_born.get(fips, [])}}
        geos = AREAS + ([("county subdivision", "cousub")] if fips in TOWNSHIP_STATES else [])
        for geo, prefix in geos:
            for row in census.get(vintage, "NAME," + ",".join(VARIABLES), f"{geo}:*", f"state:{fips}"):
                if geo == "county subdivision":
                    code = row["county"] + row[geo]
                else:
                    code = row[geo]
                areas[f"{prefix}:{code}"] = record(row)
        for geo, prefix in AREAS:
            if prefix in BIRTH_AREAS:
                for code, born in birthplaces(census, vintage, countries, geo, f"state:{fips}").items():
                    if f"{prefix}:{code}" in areas:
                        areas[f"{prefix}:{code}"]["bornIn"] = born
        store.write(f"census/acs/{abbr}.json", {"vintage": vintage, "source": source, "areas": areas})
        log(f"Census: {abbr} {len(areas)} areas")

    gather_history(census, store, today, log)
    store.write("census/acs/state.json", {"vintage": vintage, "version": VERSION, "complete": True})
    log(f"Census: {vintage} survey published")



def history_row(year, row):
    """[year, everyone, born a citizen, naturalized, not a citizen]."""
    n = [number(row.get(v)) for v in HISTORY_VARIABLES]
    if any(v is None for v in n):
        return None
    return [year, n[0], n[1] + n[2] + n[3], n[4], n[5]]


def gather_history(census, store, today, log):
    """
    How many people were born citizens, became citizens, or are not citizens, year by year: the
    country and every state from the 1-year survey. Every county gets the 2000 census, then the
    1-year survey if it has 65,000 people or more, or else the 5-year survey, whose figure for a year
    pools the five years ending then.
    """
    us, states, big, small = [], {}, {}, {}
    get = ",".join(HISTORY_VARIABLES)
    for year in range(HISTORY_START, today.year):
        if year in HISTORY_SKIP:
            continue
        try:
            us_rows = census.get(year, get, "us:1", survey="acs1")
        except Exception:
            break  # Not published yet.
        row = history_row(year, us_rows[0]) if us_rows else None
        if row:
            us.append(row)
        for r in census.get(year, get, "state:*", survey="acs1"):
            abbr = STATES.get(r["state"])
            row = history_row(year, r)
            if abbr and row:
                states.setdefault(abbr, []).append(row)
        for r in census.get(year, get, "county:*", "state:*", survey="acs1"):
            abbr = STATES.get(r["state"])
            row = history_row(year, r)
            if abbr and row:
                big.setdefault(abbr, {}).setdefault(r["county"], []).append(row)
        log(f"Census: citizenship {year}")

    # The 5-year survey began with 2005-2009.
    for year in range(2009, today.year):
        try:
            rows = census.get(year, get, "county:*", "state:*")
        except Exception:
            break
        for r in rows:
            abbr = STATES.get(r["state"])
            row = history_row(year, r)
            if abbr and row:
                small.setdefault(abbr, {}).setdefault(r["county"], []).append(row)
        log(f"Census: 5-year citizenship {year}")

    census2000 = gather_2000(census, log)

    counties, five_year = {}, {}
    for abbr in set(big) | set(small) | set(census2000):
        out = {}
        for county in set(big.get(abbr, {})) | set(small.get(abbr, {})) | set(census2000.get(abbr, {})):
            yearly = big.get(abbr, {}).get(county)
            # A county counts as "big" only if the 1-year survey covers it in most years.
            rows = yearly if yearly and len(yearly) >= 5 else small.get(abbr, {}).get(county, [])
            if rows is not yearly:
                five_year.setdefault(abbr, []).append(county)
            first = census2000.get(abbr, {}).get(county)
            out[county] = ([first] if first else []) + rows
        counties[abbr] = out

    source = {
        "name": "U.S. Census Bureau, American Community Survey 1-year estimates, table B05001",
        "url": "https://data.census.gov/table/ACSDT1Y2024.B05001",
        "columns": ["year", "population", "bornCitizens", "naturalized", "notCitizens"],
        "notes": ["No 1-year figures for 2020: the pandemic cut that year's survey responses, so the Census "
                  "Bureau released 2020 only as experimental estimates."],
    }
    store.write("census/history.json", {"source": source, "us": us, "states": states})
    county_source = {
        **source,
        "name": "U.S. Census Bureau: Census 2000 (Summary File 3, table P021), then the American Community Survey "
                "(table B05001), 1-year estimates for counties of 65,000 or more and 5-year estimates for the rest",
        "notes": source["notes"] + [
            "Counties listed in fiveYear use the 5-year survey: the figure for a year pools the five years ending then."],
    }
    for abbr, by_county in counties.items():
        store.write(f"census/history-counties/{abbr}.json",
                    {"source": county_source, "counties": by_county, "fiveYear": sorted(five_year.get(abbr, []))})


def gather_2000(census, log):
    """Every county in the 2000 census (Summary File 3, P021): native, naturalized, not a citizen."""
    out = {}
    get = "P021001,P021002,P021014,P021015"
    for fips, abbr in STATES.items():
        try:
            rows = census.get_2000(get, "county:*", f"state:{fips}")
        except Exception as e:
            log(f"Census: 2000 counties {abbr} not available ({type(e).__name__})")
            continue
        for r in rows:
            n = [number(r.get(v)) for v in get.split(",")]
            if None not in n:
                out.setdefault(abbr, {})[r["county"]] = [2000, n[0], n[1], n[2], n[3]]
    log(f"Census: 2000 counties for {len(out)} states")
    return out
