"""
Executive orders, from the Federal Register (federalregister.gov), the government's official daily
publication of presidential documents. Every order it has online, back to 1994, in one small file the app
can search: orders/index.json. Free and keyless; a handful of requests a run.
"""
import json
import urllib.parse

API = "https://www.federalregister.gov/api/v1/documents.json"
FIELDS = ["executive_order_number", "title", "signing_date", "publication_date", "html_url", "pdf_url",
          "president", "disposition_notes", "document_number"]


def gather(net, store, log):
    orders, page = [], 1
    while True:
        params = [("conditions[presidential_document_type]", "executive_order"), ("per_page", "1000"),
                  ("page", str(page)), ("order", "newest")] + [("fields[]", f) for f in FIELDS]
        data = json.loads(net.text(f"{API}?{urllib.parse.urlencode(params)}"))
        for r in data.get("results", []):
            orders.append({k: v for k, v in {
                "number": r.get("executive_order_number"),
                "title": r.get("title"),
                "signed": r.get("signing_date"),
                "published": r.get("publication_date"),
                "president": (r.get("president") or {}).get("name"),
                "notes": r.get("disposition_notes"),
                "url": r.get("html_url"),
                "pdf": r.get("pdf_url"),
            }.items() if v})
        if page >= (data.get("total_pages") or 1):
            break
        page += 1
    store.write("orders/index.json", {"source": "https://www.federalregister.gov/presidential-documents/executive-orders",
                                      "orders": orders})
    log(f"Executive orders: {len(orders)} in the Federal Register")
