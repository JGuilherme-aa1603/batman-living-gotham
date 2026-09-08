"""Data-driven, auditable surface material categorization."""

from __future__ import annotations

import json
from pathlib import Path


class BlockCategories:
    def __init__(self, path: Path):
        data = json.loads(path.read_text(encoding="utf-8"))
        self.exact = data["exact"]
        self.contains = list(data["contains"].items())
        self.fallback = data["fallback_by_namespace"]

    def classify(self, name: str) -> str:
        if name in self.exact:
            return self.exact[name]
        local = name.partition(":")[2]
        for needle, category in self.contains:
            if needle in local:
                return category
        namespace = name.partition(":")[0]
        return self.fallback.get(namespace, self.fallback["default"])
