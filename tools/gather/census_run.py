"""
Publishes the Census Bureau figures: the yearly survey for every area (census.py), and the files in
census_data/ that were copied by hand from official tables (apportionment, voting, government
finances, local governments, and the Homeland Security estimate). Run monthly by
.github/workflows/census.yml, apart from the six-hourly gather, because these change once a year.

  python tools/gather/census_run.py --out site
"""
import argparse
import datetime
import os
import shutil
import sys
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import census  # noqa: E402
from net import Net  # noqa: E402
from store import Store  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    store = Store(args.out)
    lines, errors = [], []

    def log(msg):
        print(msg, flush=True)
        lines.append(msg)

    # The hand-copied tables, published as they are.
    folder = os.path.join(HERE, "census_data")
    for name in sorted(os.listdir(folder)) if os.path.isdir(folder) else []:
        if name.endswith(".json"):
            os.makedirs(store.path("census"), exist_ok=True)
            shutil.copyfile(os.path.join(folder, name), store.path(f"census/{name}"))
            log(f"Copied {name}")

    try:
        census.gather(Net(0, 50), store, datetime.date.today(), log)
    except Exception as e:
        traceback.print_exc()
        errors.append(f"{type(e).__name__}: {e}")

    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    store.write("census/run.json", {"at": now, "log": lines, "errors": errors})
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
