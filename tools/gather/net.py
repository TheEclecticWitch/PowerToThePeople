"""
Polite HTTP for the gatherer: retries with backoff, a request budget, and the api.data.gov key
added only to Congress.gov calls. Uses the standard library so the GitHub runner needs no installs.
"""
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
        return json.loads(self._get(url, {"X-Api-Key": self.key}))

    def text(self, url, browser=False):
        """GET any other public source. senate.gov turns away non-browser user agents."""
        self.check_time()
        self.other_requests += 1
        agent = "Mozilla/5.0 (compatible; PowerToThePeople-gatherer)" if browser else USER_AGENT
        return self._get(url, {"User-Agent": agent})

    def _get(self, url, headers):
        headers = {"User-Agent": USER_AGENT, "Accept-Encoding": "identity", **headers}
        delay = 2
        for attempt in range(5):
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as r:
                    return r.read().decode("utf-8")
            except urllib.error.HTTPError as e:
                # 404 is a real answer (not published yet); only busy/server errors are worth retrying.
                if e.code not in (429, 500, 502, 503, 504) or attempt == 4:
                    raise
            except (urllib.error.URLError, TimeoutError):
                if attempt == 4:
                    raise
            time.sleep(delay)
            delay *= 2
