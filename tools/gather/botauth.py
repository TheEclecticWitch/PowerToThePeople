"""
Signs the gatherer's requests to senate.gov so its bot manager (Akamai) can recognize them (Web Bot Auth:
draft-ietf-webbotauth-httpsig-protocol, built on HTTP Message Signatures, RFC 9421). The Senate webmaster
asked for this registration on 2026-10-28 after senate.gov began refusing the gatherer.

The private key is the GitHub secret P2P_BOT_SIGNING_KEY (a base64 Ed25519 seed; Rod's copy is in
C:/Users/rodkc/StoreKeys). The public key is published by the developer website at
https://00theeclecticwitch00.com/.well-known/http-message-signatures-directory
Without the secret (a run on a PC) requests go out unsigned, with the same bot name.

  python botauth.py directory <seed file>   prints the website's key directory and its signature headers
"""
import base64
import hashlib
import json
import os
import secrets
import sys
import time
import urllib.parse

AGENT = "https://00theeclecticwitch00.com"
USER_AGENT = "P2P-PowerToThePeople-gatherer/1.0 (+https://00theeclecticwitch00.com/p2p-gatherer/)"
LABEL = "sig1"
# The draft recommends a short window; each request gets a fresh signature anyway.
LIFETIME_SECONDS = 300


def _b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _load(seed_b64):
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    return Ed25519PrivateKey.from_private_bytes(base64.b64decode(seed_b64.strip()))


def _public_x(key):
    from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
    return _b64url(key.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw))


def thumbprint(x):
    """The key's id: its JWK SHA-256 thumbprint (RFC 7638, with RFC 8037's members for Ed25519)."""
    canonical = json.dumps({"crv": "Ed25519", "kty": "OKP", "x": x}, separators=(",", ":"), sort_keys=True)
    return _b64url(hashlib.sha256(canonical.encode("ascii")).digest())


class Signer:
    def __init__(self, seed_b64=None):
        seed_b64 = seed_b64 if seed_b64 is not None else os.environ.get("P2P_BOT_SIGNING_KEY")
        self.key = _load(seed_b64) if seed_b64 else None
        self.keyid = thumbprint(_public_x(self.key)) if self.key else None

    def headers(self, url, body=None):
        """
        The bot's name, plus the Signature-Agent, Signature-Input and Signature headers when a key is set.
        A request with a body (the eFD search form) also sends and signs its Content-Digest, as the draft asks.
        """
        out = {"User-Agent": USER_AGENT}
        if not self.key:
            return out
        authority = urllib.parse.urlsplit(url).netloc.lower()
        created = int(time.time())
        components = f'"@authority" "signature-agent";key="{LABEL}"'
        base = f'"@authority": {authority}\n"signature-agent";key="{LABEL}": "{AGENT}"\n'
        if body is not None:
            out["Content-Digest"] = "sha-256=:" + base64.b64encode(hashlib.sha256(body).digest()).decode("ascii") + ":"
            components += ' "content-digest"'
            base += f'"content-digest": {out["Content-Digest"]}\n'
        params = (f'({components});created={created};'
                  f'expires={created + LIFETIME_SECONDS};keyid="{self.keyid}";alg="ed25519";'
                  f'nonce="{_b64url(secrets.token_bytes(32))}";tag="web-bot-auth"')
        base += f'"@signature-params": {params}'
        signature = base64.b64encode(self.key.sign(base.encode("ascii"))).decode("ascii")
        out["Signature-Agent"] = f'{LABEL}="{AGENT}"'
        out["Signature-Input"] = f"{LABEL}={params}"
        out["Signature"] = f"{LABEL}=:{signature}:"
        return out


def directory(seed_b64, years=3):
    """The key directory's body and the headers that sign it (Appendix B.1), valid for `years`."""
    key = _load(seed_b64)
    x = _public_x(key)
    kid = thumbprint(x)
    body = json.dumps({"keys": [{"kty": "OKP", "crv": "Ed25519", "kid": kid, "x": x, "use": "sig"}]},
                      indent=2) + "\n"
    digest = "sha-256=:" + base64.b64encode(hashlib.sha256(body.encode("ascii")).digest()).decode("ascii") + ":"
    created = int(time.time())
    authority = urllib.parse.urlsplit(AGENT).netloc
    params = (f'("@authority";req "content-digest");created={created};expires={created + years * 365 * 86400};'
              f'keyid="{kid}";alg="ed25519";tag="http-message-signatures-directory"')
    base = (f'"@authority";req: {authority}\n'
            f'"content-digest": {digest}\n'
            f'"@signature-params": {params}')
    signature = base64.b64encode(key.sign(base.encode("ascii"))).decode("ascii")
    return body, {"Content-Digest": digest, "Signature-Input": f"binding={params}",
                  "Signature": f"binding=:{signature}:"}


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "directory":
        sys.exit(__doc__)
    with open(sys.argv[2], encoding="ascii") as f:
        body, headers = directory(f.read())
    print(body)
    for name, value in headers.items():
        print(f"{name}: {value}")
