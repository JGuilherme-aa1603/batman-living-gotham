"""Simple region-file cache signatures."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path


@dataclass(frozen=True)
class RegionSignature:
    size_bytes: int
    mtime_ns: int
    sha256: str


def signature(path: Path) -> RegionSignature:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    stat = path.stat()
    return RegionSignature(stat.st_size, stat.st_mtime_ns, digest.hexdigest())


def reusable(previous: RegionSignature | None, current: RegionSignature) -> bool:
    return previous == current
