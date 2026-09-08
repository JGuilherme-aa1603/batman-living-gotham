"""Stable geometry-derived identifiers."""

from __future__ import annotations

import hashlib
import json
from typing import Iterable


def geometry_hash(kind: str, dimension: str, points: Iterable[tuple[int, int]], bounds: tuple[int, ...]) -> str:
    payload = {
        "kind": kind,
        "dimension": dimension,
        "bounds": list(bounds),
        "points": sorted([list(point) for point in points]),
    }
    encoded = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode()
    return hashlib.sha256(encoded).hexdigest()


def stable_id(kind: str, dimension: str, points: Iterable[tuple[int, int]], bounds: tuple[int, ...]) -> str:
    return f"{kind}_{geometry_hash(kind, dimension, points, bounds)[:16]}"
