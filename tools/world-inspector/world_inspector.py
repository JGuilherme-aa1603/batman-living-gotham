#!/usr/bin/env python3
"""Inspect a bounded chunk selection from a disposable Minecraft world copy."""

from __future__ import annotations

import argparse
from collections import Counter
import json
import os
from pathlib import Path
import tempfile
import time
from typing import Any

# Amulet creates a history database while opening a world. Keep that cache out
# of the user's global Python environment and make sandboxed runs predictable.
os.environ.setdefault(
    "XDG_CACHE_HOME", str(Path(tempfile.gettempdir()) / "living-gotham-amulet-cache")
)

import amulet  # noqa: E402  (environment must be set before importing Amulet)
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402


PROJECT_ROOT = Path(__file__).resolve().parents[2]
PROTECTED_WORLD_ROOT = (PROJECT_ROOT / "LosPerrito2.0").resolve()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path, help="Disposable world-copy path")
    parser.add_argument("--dimension", default="minecraft:overworld")
    parser.add_argument("--min-chunk-x", type=int, required=True)
    parser.add_argument("--max-chunk-x", type=int, required=True)
    parser.add_argument("--min-chunk-z", type=int, required=True)
    parser.add_argument("--max-chunk-z", type=int, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument(
        "--max-chunks",
        type=int,
        default=256,
        help="Safety ceiling for the requested rectangle (default: 256)",
    )
    return parser.parse_args()


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def json_ready(value: Any) -> Any:
    if isinstance(value, Path):
        return str(value)
    if isinstance(value, tuple):
        return list(value)
    return value


def main() -> int:
    args = parse_args()
    world_path = args.world.expanduser().resolve()
    output_dir = args.output_dir.expanduser().resolve()

    if is_relative_to(world_path, PROTECTED_WORLD_ROOT):
        raise SystemExit(
            "Refusing to open the protected LosPerrito2.0 worldbase. "
            "Create a disposable copy and inspect that copy instead."
        )
    if not (world_path / "level.dat").is_file():
        raise SystemExit(f"Not a Java world directory: {world_path}")
    if args.min_chunk_x > args.max_chunk_x or args.min_chunk_z > args.max_chunk_z:
        raise SystemExit("Minimum chunk bounds must not exceed maximum bounds.")

    width_chunks = args.max_chunk_x - args.min_chunk_x + 1
    depth_chunks = args.max_chunk_z - args.min_chunk_z + 1
    requested_chunks = width_chunks * depth_chunks
    if requested_chunks > args.max_chunks:
        raise SystemExit(
            f"Requested {requested_chunks} chunks, above --max-chunks={args.max_chunks}."
        )

    output_dir.mkdir(parents=True, exist_ok=True)
    started = time.perf_counter()
    level = amulet.load_level(str(world_path))
    try:
        dimensions = list(level.dimensions)
        if args.dimension not in dimensions:
            raise SystemExit(
                f"Dimension {args.dimension!r} not present. Available: {dimensions}"
            )

        pixel_width = width_chunks * 16
        pixel_depth = depth_chunks * 16
        heights = np.full((pixel_depth, pixel_width), np.nan, dtype=np.float32)
        block_counts: Counter[str] = Counter()
        block_entity_counts: Counter[str] = Counter()
        entity_counts: Counter[str] = Counter()
        found: list[tuple[int, int]] = []
        missing: list[tuple[int, int]] = []
        min_section_y: int | None = None
        max_section_y: int | None = None

        for cz in range(args.min_chunk_z, args.max_chunk_z + 1):
            for cx in range(args.min_chunk_x, args.max_chunk_x + 1):
                if not level.has_chunk(cx, cz, args.dimension):
                    missing.append((cx, cz))
                    continue

                chunk = level.get_chunk(cx, cz, args.dimension)
                found.append((cx, cz))
                palette = list(chunk.block_palette)
                air_ids = {
                    index
                    for index, block in enumerate(palette)
                    if block.base_name in {"air", "cave_air", "void_air"}
                }
                section_ys = sorted(chunk.blocks.sub_chunks)
                if section_ys:
                    min_section_y = (
                        min(section_ys)
                        if min_section_y is None
                        else min(min_section_y, min(section_ys))
                    )
                    max_section_y = (
                        max(section_ys)
                        if max_section_y is None
                        else max(max_section_y, max(section_ys))
                    )

                ox = (cx - args.min_chunk_x) * 16
                oz = (cz - args.min_chunk_z) * 16
                unresolved = np.ones((16, 16), dtype=bool)

                for section_y in reversed(section_ys):
                    section = chunk.blocks.get_sub_chunk(section_y)
                    ids, counts = np.unique(section, return_counts=True)
                    for block_id, count in zip(ids.tolist(), counts.tolist()):
                        block_counts[str(palette[block_id])] += count

                    if not unresolved.any():
                        continue
                    solid = ~np.isin(section, list(air_ids))
                    for local_x in range(16):
                        for local_z in range(16):
                            if not unresolved[local_x, local_z]:
                                continue
                            local_ys = np.flatnonzero(solid[local_x, :, local_z])
                            if local_ys.size:
                                heights[oz + local_z, ox + local_x] = (
                                    section_y * 16 + int(local_ys[-1])
                                )
                                unresolved[local_x, local_z] = False

                for block_entity in chunk.block_entities.values():
                    block_entity_counts[
                        f"{block_entity.namespace}:{block_entity.base_name}"
                    ] += 1
                for entity in chunk.entities:
                    entity_counts[f"{entity.namespace}:{entity.base_name}"] += 1

        finite = heights[np.isfinite(heights)]
        if finite.size:
            low = float(finite.min())
            high = float(finite.max())
            span = max(high - low, 1.0)
            pixels = np.where(np.isfinite(heights), (heights - low) * 255 / span, 0)
        else:
            low = high = None
            pixels = np.zeros(heights.shape, dtype=np.float32)
        Image.fromarray(pixels.astype(np.uint8), mode="L").save(
            output_dir / "heightmap.png"
        )

        summary = {
            "world": str(world_path),
            "warning": "Amulet opened this path read/write and rewrote session.lock.",
            "platform": level.level_wrapper.platform,
            "data_version": level.level_wrapper.version,
            "game_version": level.level_wrapper.game_version_string,
            "available_dimensions": dimensions,
            "dimension": args.dimension,
            "chunk_bounds_inclusive": {
                "min_x": args.min_chunk_x,
                "max_x": args.max_chunk_x,
                "min_z": args.min_chunk_z,
                "max_z": args.max_chunk_z,
            },
            "requested_chunks": requested_chunks,
            "loaded_chunks": len(found),
            "missing_chunks": [list(coords) for coords in missing],
            "loaded_chunk_coordinates": [list(coords) for coords in found],
            "vertical_block_bounds": {
                "min_y": None if min_section_y is None else min_section_y * 16,
                "max_y_inclusive": None
                if max_section_y is None
                else max_section_y * 16 + 15,
            },
            "heightmap": {
                "width_blocks": pixel_width,
                "depth_blocks": pixel_depth,
                "minimum_surface_y": low,
                "maximum_surface_y": high,
                "encoding": "8-bit grayscale normalized within this selection",
            },
            "block_entities": {
                "count": sum(block_entity_counts.values()),
                "types": dict(sorted(block_entity_counts.items())),
            },
            "entities": {
                "count": sum(entity_counts.values()),
                "types": dict(sorted(entity_counts.items())),
            },
            "elapsed_seconds": round(time.perf_counter() - started, 3),
        }
        block_stats = {
            "dimension": args.dimension,
            "chunks_loaded": len(found),
            "total_block_positions_in_loaded_sections": sum(block_counts.values()),
            "unique_blockstates": len(block_counts),
            "blockstates": dict(block_counts.most_common()),
        }
        (output_dir / "summary.json").write_text(
            json.dumps(summary, indent=2, ensure_ascii=False, default=json_ready) + "\n",
            encoding="utf-8",
        )
        (output_dir / "block_stats.json").write_text(
            json.dumps(block_stats, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
    finally:
        level.close()

    print(json.dumps(summary, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
