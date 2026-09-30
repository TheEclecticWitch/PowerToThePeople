"""
Reads and writes the published JSON files. A file is only rewritten when its contents change, so
each data commit shows exactly what changed in Congress since the last run.
"""
import json
import os


class Store:
    def __init__(self, root):
        self.root = root
        self.written = 0

    def path(self, rel):
        return os.path.join(self.root, *rel.split("/"))

    def read(self, rel, default=None):
        try:
            with open(self.path(rel), encoding="utf-8") as f:
                return json.load(f)
        except FileNotFoundError:
            return default

    def write(self, rel, data):
        text = json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        full = self.path(rel)
        try:
            with open(full, encoding="utf-8") as f:
                if f.read() == text:
                    return
        except FileNotFoundError:
            os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        self.written += 1

    def list_dir(self, rel):
        try:
            return sorted(os.listdir(self.path(rel)))
        except FileNotFoundError:
            return []
