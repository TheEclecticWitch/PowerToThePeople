r"""Sets up Power to the People with Apple's developer portal, as was done for Consciousness Theories and Calculator Magic:
 - the identifier com.theeclecticwitch.powertothepeople (already made for the iPhone app; iPhone and Mac share it: one App Store record,
   "P2P: Power to the People") and com.oracle.java.com.theeclecticwitch.powertothepeople (the Java runtime
   inside the Mac app, which Apple signs separately);
 - the two Mac App Store provisioning profiles, saved to desktopApp/macos/ (embedded and runtime).
Skips anything that already exists. Run:  python tools/store/apple_setup.py
"""
import base64, glob, json, os, time
import jwt, requests

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
APP_ID = "com.theeclecticwitch.powertothepeople"
RUNTIME_ID = "com.oracle.java." + APP_ID
KEYS = r"C:\Users\rodkc\StoreKeys"
ids = json.load(open(KEYS + r"\apple.json"))
p8 = open(glob.glob(KEYS + rf"\*{ids['key_id']}*.p8")[0]).read()
token = jwt.encode({"iss": ids["issuer_id"], "iat": int(time.time()), "exp": int(time.time()) + 1200, "aud": "appstoreconnect-v1"},
                   p8, algorithm="ES256", headers={"kid": ids["key_id"], "typ": "JWT"})
H = {"Authorization": "Bearer " + token}
API = "https://api.appstoreconnect.apple.com"


def call(method, path, body=None, **params):
    r = requests.request(method, API + path, headers=H, json=body, params=params, timeout=60)
    if not r.ok:
        raise SystemExit(f"{method} {path}: {r.status_code} {r.text[:600]}")
    return r.json() if r.text else {}


def bundle(identifier, name):
    found = call("GET", "/v1/bundleIds", **{"filter[identifier]": identifier})["data"]
    found = [b for b in found if b["attributes"]["identifier"] == identifier]
    if found:
        print("identifier already there:", identifier)
        return found[0]["id"]
    made = call("POST", "/v1/bundleIds", {"data": {"type": "bundleIds", "attributes": {
        "identifier": identifier, "name": name, "platform": "UNIVERSAL"}}})["data"]
    print("identifier made:", identifier)
    return made["id"]


app = bundle(APP_ID, "Power to the People")
runtime = bundle(RUNTIME_ID, "Power to the People Java runtime")
cert = next(c["id"] for c in call("GET", "/v1/certificates", limit=50)["data"]
            if c["attributes"]["certificateType"] == "MAC_APP_DISTRIBUTION")

for name, bundle_id, file in [("P2P Mac App Store", app, "embedded.provisionprofile"),
                              ("P2P Mac runtime", runtime, "runtime.provisionprofile")]:
    have = [p for p in call("GET", "/v1/profiles", **{"filter[name]": name})["data"]
            if p["attributes"]["profileState"] == "ACTIVE"]
    profile = have[0] if have else call("POST", "/v1/profiles", {"data": {"type": "profiles",
        "attributes": {"name": name, "profileType": "MAC_APP_STORE"},
        "relationships": {"bundleId": {"data": {"type": "bundleIds", "id": bundle_id}},
                          "certificates": {"data": [{"type": "certificates", "id": cert}]}}}})["data"]
    out = os.path.join(ROOT, "desktopApp", "macos", file)
    open(out, "wb").write(base64.b64decode(profile["attributes"]["profileContent"]))
    print(("profile already there, saved: " if have else "profile made and saved: ") + os.path.relpath(out, ROOT),
          "(expires", profile["attributes"]["expirationDate"][:10] + ")")
