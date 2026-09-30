"""
Polite HTTP for the gatherer: retries with backoff, a request budget, and the api.data.gov key
added only to Congress.gov calls. Uses the standard library so the GitHub runner needs no installs.
"""
import gzip
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "PowerToThePeople-gatherer/0.1 (+https://github.com/TheEclecticWitch/PowerToThePeople)"
CONGRESS_API = "https://api.congress.gov/v3"


class BudgetSpent(Exception):
    """Raised when this run has used its share of requests or time. The next run carries on."""


class Net:
    def __init__(self, max_congress_requests, max_minutes):
        self.key = os.environ.get("API_DATA_GOV_KEY") or "DEMO_KEY"
        self.max_congress_requests = max_congress_requests
        self.deadline = time.monotonic() + max_minutes * 60
        self.congress_requests = 0
        self.other_requests = 0
        # How the sources behaved this run, published in manifest.json so slowdowns can be traced.
        self.seconds = {"congress": 0.0, "other": 0.0}
        self.slowest = (0.0, None)
        self.retries = {}

    def stats(self):
        def avg(kind, n):
            return round(self.seconds[kind] / n, 2) if n else None
        return {
            "keySet": self.key != "DEMO_KEY",
            "avgSecondsCongress": avg("congress", self.congress_requests),
            "avgSecondsOther": avg("other", self.other_requests),
            "slowest": {"seconds": round(self.slowest[0], 1), "url": self.slowest[1]},
            "retries": self.retries,
        }

    def check_time(self):
        if time.monotonic() > self.deadline:
            raise BudgetSpent()

    def congress(self, path, **params):
        """GET a Congress.gov API path such as '/bill/119' and return the parsed JSON."""
        self.check_time()
        if self.congress_requests >= self.max_congress_requests:
            raise BudgetSpent()
        self.congress_requests += 1
        params = {"format": "json", **params}
        url = f"{CONGRESS_API}{path}?{urllib.parse.urlencode(params)}"
        # The key goes in a header so it never shows up in logged URLs.
        return json.loads(self._get(url, {"X-Api-Key": self.key}, "congress"))

    def text(self, url, browser=False):
        """GET any other public source. senate.gov turns away non-browser user agents."""
        self.check_time()
        self.other_requests += 1
        agent = "Mozilla/5.0 (compatible; PowerToThePeople-gatherer)" if browser else USER_AGENT
        return self._get(url, {"User-Agent": agent}, "other")

    def _get(self, url, headers, kind):
        headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "gzip", **headers}
        start = time.monotonic()
        delay = 2
        try:
            for attempt in range(5):
                try:
                    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as r:
                        body = r.read()
                        if r.headers.get("Content-Encoding") == "gzip":
                            body = gzip.decompress(body)
                        return body.decode("utf-8")
                except urllib.error.HTTPError as e:
                    reason = f"HTTP {e.code}"
                    # 404 is a real answer (not published yet); only busy/server errors are worth retrying.
                    if e.code not in (429, 500, 502, 503, 504) or attempt == 4:
                        raise
                except (urllib.error.URLError, TimeoutError) as e:
                    reason = type(e).__name__
                    if attempt == 4:
                        raise
                self.retries[reason] = self.retries.get(reason, 0) + 1
                print(f"  retrying after {reason}: {url.split('?')[0]}", flush=True)
                time.sleep(delay)
                delay *= 2
        finally:
            took = time.monotonic() - start
            self.seconds[kind] += took
            if took > self.slowest[0]:
                self.slowest = (took, url.split("?")[0])
