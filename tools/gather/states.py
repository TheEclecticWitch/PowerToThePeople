"""
State officials: each state's governor and other statewide officers, and every current state legislator,
from the Open States project's public data (github.com/openstates/people, kept from state legislature and
government websites). The whole collection is one ~5 MB download, read once a week, and published as one
small file per state: states/md.json and so on.
"""
import datetime
import io
import tarfile
import urllib.request

import yaml

ARCHIVE = "https://codeload.github.com/openstates/people/tar.gz/refs/heads/main"
SOURCE = "https://github.com/openstates/people"
AGENT = "PowerToThePeople-gatherer (https://github.com/TheEclecticWitch/PowerToThePeople)"

EXECUTIVE_TITLES = {
    "governor": "Governor",
    "lt_governor": "Lieutenant Governor",
    "attorney general": "Attorney General",
    "secretary of state": "Secretary of State",
    "treasurer": "Treasurer",
    "auditor": "Auditor",
    "chief election officer": "Chief Election Officer",
}
EXECUTIVE_ORDER = list(EXECUTIVE_TITLES)

# What each state calls a member of its lower chamber; "State Representative" everywhere else.
LOWER_TITLES = {
    "md": "Delegate", "va": "Delegate", "wv": "Delegate",
    "ca": "Assemblymember", "nv": "Assemblymember", "ny": "Assemblymember", "wi": "Assemblymember", "nj": "Assemblymember",
}


def _current(role, today):
    start, end = role.get("start_date"), role.get("end_date")
    return (start is None or str(start) <= today) and (end is None or str(end) >= today)


def _person(data, role, title, state):
    offices = data.get("offices") or []
    capitol = next((o for o in offices if o.get("classification") == "capitol"), None) or (offices[0] if offices else {})
    district = next((o for o in offices if o.get("classification") == "district"), None)
    links = data.get("links") or []
    form = next((l["url"] for l in links if (l.get("note") or "").lower() in ("webform", "contact form")), None)
    website = next((l["url"] for l in links if l.get("url") != form), None)
    party = (data.get("party") or [{}])[-1].get("name")
    out = {
        "id": f"state:{state}:{data['id']}",
        "name": data["name"],
        "title": title,
        "party": party,
        "chamber": role["type"] if role["type"] in ("upper", "lower", "legislature") else None,
        "district": role.get("district"),
        "start": str(role["start_date"]) if role.get("start_date") else None,
        "end": str(role["end_date"]) if role.get("end_date") else None,
        "email": data.get("email"),
        "image": data.get("image"),
        "phone": capitol.get("voice"),
        "address": (capitol.get("address") or "").replace("; ;", ";").replace("; ", "\n") or None,
        "website": website,
        "contactForm": form,
    }
    ids = data.get("ids") or {}
    social = {k: ids[k] for k in ("twitter", "facebook", "instagram", "youtube") if ids.get(k)}
    if social:
        out["social"] = social
    if district and (district.get("address") or district.get("voice")):
        out["districtOffice"] = {"address": (district.get("address") or "").replace("; ", "\n") or None,
                                 "phone": district.get("voice")}
    return {k: v for k, v in out.items() if v is not None}


def gather(net, store, state, log):
    today = datetime.date.today()
    last = state.get("statesChecked")
    if last and (today - datetime.date.fromisoformat(last)).days < 7:
        return
    req = urllib.request.Request(ARCHIVE, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(req, timeout=120) as r:
        archive = tarfile.open(fileobj=io.BytesIO(r.read()), mode="r:gz")
    iso = today.isoformat()
    found = {}
    for member in archive.getmembers():
        parts = member.name.split("/")
        # <repo>-main/data/<state>/<legislature|executive>/<Name-uuid>.yml
        if len(parts) != 5 or parts[1] != "data" or parts[3] not in ("legislature", "executive") or not parts[4].endswith(".yml"):
            continue
        st = parts[2]
        if st == "us":
            continue
        data = yaml.safe_load(archive.extractfile(member))
        bucket = found.setdefault(st, {"executives": [], "legislators": []})
        for role in data.get("roles") or []:
            if not _current(role, iso):
                continue
            kind = role.get("type")
            if kind in EXECUTIVE_TITLES:
                bucket["executives"].append(_person(data, role, EXECUTIVE_TITLES[kind], st))
            elif kind in ("upper", "lower", "legislature"):
                if kind == "lower":
                    title = LOWER_TITLES.get(st, "State Representative")
                else:
                    title = "State Senator"
                bucket["legislators"].append(_person(data, role, title, st))
    for st, bucket in found.items():
        bucket["executives"].sort(key=lambda p: (EXECUTIVE_ORDER.index(next(k for k, v in EXECUTIVE_TITLES.items() if v == p["title"])), p["name"]))
        bucket["legislators"].sort(key=lambda p: (p.get("chamber", ""), p.get("district", ""), p["name"]))
        store.write(f"states/{st}.json", {"state": st.upper(), "source": SOURCE, **bucket})
    state["statesChecked"] = iso
    legislators = sum(len(b["legislators"]) for b in found.values())
    log(f"State officials: {len(found)} states and territories, {legislators} legislators")
