"""Two-pass extraction implementation."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import resource
import sqlite3
import time
from typing import Iterable

import numpy as np
from amulet_nbt import load

from . import SCHEMA_VERSION, TOOL_VERSION
from .anvil import ChunkLocation, available_slots, decode_heightmap, inventory, read_chunk_nbt, reconstruct_surface, region_coords, section_surface_names
from .categories import BlockCategories
from .cache import RegionSignature, reusable, signature
from .coords import spatial_chunk_bounds
from .geometry import component_bounds, connected_components, graph_nodes
from .identity import stable_id
from .storage import connect, start_scan, transition_scan


DIMENSION = "minecraft:overworld"
CELL_BLOCKS = 4
CATEGORY_NAMES = [
    "missing", "air", "terrain", "natural", "vegetation", "water", "road_candidate",
    "sidewalk_candidate", "building_candidate", "roof_candidate", "transparent",
    "fence_barrier", "door", "container", "unknown_modded",
]
CATEGORY_CODE = {name: index for index, name in enumerate(CATEGORY_NAMES)}
ARTIFICIAL = {"road_candidate", "sidewalk_candidate", "building_candidate", "roof_candidate",
              "fence_barrier", "door", "container"}


def rss_mb() -> float:
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024.0


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def protect_world(world: Path, project_root: Path) -> Path:
    resolved = world.expanduser().resolve()
    protected = (project_root / "LosPerrito2.0").resolve()
    try:
        resolved.relative_to(protected)
    except ValueError:
        pass
    else:
        raise SystemExit("Refusing to scan a path under immutable LosPerrito2.0; use an analysis copy.")
    if not (resolved / "level.dat").is_file() or not (resolved / "region").is_dir():
        raise SystemExit(f"Not an overworld Java save: {resolved}")
    return resolved


def fingerprint(world: Path, source_level_hash: str | None = None) -> dict:
    root = load(str(world / "level.dat"), compressed=True).compound["Data"]
    chunks = inventory(world / "region")
    region_files = sorted((world / "region").glob("r.*.*.mca"), key=region_coords)
    dimensions = ["minecraft:overworld"]
    for name, rel in (("minecraft:the_nether", "DIM-1"), ("minecraft:the_end", "DIM1")):
        if (world / rel).exists():
            dimensions.append(name)
    custom = world / "dimensions"
    if custom.is_dir():
        for namespace in sorted(custom.iterdir()):
            if namespace.is_dir():
                for dimension in sorted(namespace.iterdir()):
                    if dimension.is_dir():
                        dimensions.append(f"{namespace.name}:{dimension.name}")
    min_cx = min(c.chunk_x for c in chunks)
    max_cx = max(c.chunk_x for c in chunks)
    min_cz = min(c.chunk_z for c in chunks)
    max_cz = max(c.chunk_z for c in chunks)
    analysis_hash = sha256(world / "level.dat")
    if source_level_hash is not None and source_level_hash != analysis_hash:
        raise SystemExit(
            "Analysis copy level.dat does not match --source-level-hash; "
            "refusing to assign the wrong baseline identity."
        )
    return {
        "world_name": str(root["LevelName"]),
        "dimension": DIMENSION,
        "dimensions_present_not_scanned": [d for d in dimensions if d != DIMENSION],
        "data_version": int(root["DataVersion"]),
        "level_dat_sha256": source_level_hash or analysis_hash,
        "analysis_copy_level_dat_sha256": analysis_hash,
        "world_bounds": {
            "min_x": min_cx * 16, "min_z": min_cz * 16,
            "max_x": max_cx * 16 + 15, "max_z": max_cz * 16 + 15,
            "min_chunk_x": min_cx, "min_chunk_z": min_cz,
            "max_chunk_x": max_cx, "max_chunk_z": max_cz,
        },
        "region_inventory": {
            "count": len(region_files), "chunks": len(chunks),
            "bounds": {
                "min_region_x": min(region_coords(p)[0] for p in region_files),
                "max_region_x": max(region_coords(p)[0] for p in region_files),
                "min_region_z": min(region_coords(p)[1] for p in region_files),
                "max_region_z": max(region_coords(p)[1] for p in region_files),
            },
            "files": [{"name": p.name, "size": p.stat().st_size, "mtime_ns": p.stat().st_mtime_ns}
                      for p in region_files],
        },
        "scan_timestamp": datetime.now(timezone.utc).isoformat(),
        "city_index_schema_version": SCHEMA_VERSION,
        "tool_version": TOOL_VERSION,
    }


@dataclass
class ChunkMetrics:
    location: ChunkLocation
    data_version: int
    status: str
    heights: np.ndarray
    categories: np.ndarray
    block_names: list[str]
    raw_bytes: int
    poi: bool
    entities: bool

    def values(self) -> dict:
        h = self.heights.astype(np.float32)
        classes = self.categories
        fractions = {name: float(np.mean(classes == code)) for name, code in CATEGORY_CODE.items()}
        artificial = sum(fractions[name] for name in ARTIFICIAL)
        road = fractions["road_candidate"] + fractions["sidewalk_candidate"] * 0.35
        roof = fractions["roof_candidate"] + fractions["building_candidate"] * 0.55
        differences = np.concatenate((np.abs(np.diff(h, axis=0)).ravel(), np.abs(np.diff(h, axis=1)).ravel()))
        roughness = float(np.mean(differences))
        vertical = min(float(np.percentile(h, 90) - np.percentile(h, 10)) / 40.0, 1.0)
        # Natural mountains may be tall and rough, but are not urban without
        # artificial coverage. Height/roughness only amplify built evidence.
        density = max(0.0, min(1.0, artificial * (0.70 + 0.15 * vertical +
                                                   0.15 * min(roughness / 8.0, 1.0)) +
                                    0.08 * road))
        density_class = ("very_high" if density >= .72 else "high" if density >= .55 else
                         "medium" if density >= .36 else "low" if density >= .18 else "open_natural")
        return {
            "min_height": int(h.min()), "max_height": int(h.max()), "mean_height": float(h.mean()),
            "height_variance": float(h.var()), "roughness": roughness,
            "water_fraction": fractions["water"], "artificial_fraction": artificial,
            "road_fraction": road, "roof_fraction": roof,
            "vegetation_fraction": fractions["vegetation"],
            "unknown_fraction": fractions["unknown_modded"], "urban_density": density,
            "density_class": density_class,
        }


def load_chunk(location: ChunkLocation, categories: BlockCategories, poi_slots: set[int], entity_slots: set[int]) -> ChunkMetrics:
    root, raw_bytes = read_chunk_nbt(location)
    heightmaps = root.get("Heightmaps", {})
    # Generated-but-not-fully-promoted chunks at the map envelope legitimately
    # expose only the world-generation variant. Keep them in the inventory and
    # coarse surface map, while preserving their Status for downstream filters.
    chosen = (heightmaps.get("WORLD_SURFACE") or heightmaps.get("MOTION_BLOCKING")
              or heightmaps.get("WORLD_SURFACE_WG") or heightmaps.get("OCEAN_FLOOR_WG"))
    if chosen is None:
        fallback_heights, names = reconstruct_surface(root)
        heights = np.asarray(fallback_heights, dtype=np.int16).reshape((16, 16))
    else:
        heights = np.asarray(decode_heightmap(chosen), dtype=np.int16).reshape((16, 16))
        names = section_surface_names(root, heights.ravel().tolist())
    class_values = np.asarray([CATEGORY_CODE[categories.classify(name)] for name in names], dtype=np.uint8).reshape((16, 16))
    return ChunkMetrics(location, int(root.get("DataVersion", 0)), str(root.get("Status", "unknown")),
                        heights, class_values, names, raw_bytes,
                        location.slot in poi_slots, location.slot in entity_slots)


def companion_slots(world: Path, location: ChunkLocation, kind: str, memo: dict) -> set[int]:
    key = (kind, location.region_x, location.region_z)
    if key not in memo:
        path = world / kind / f"r.{location.region_x}.{location.region_z}.mca"
        memo[key] = available_slots(path)
    return memo[key]


def benchmark(world: Path, config: Path, sample_sizes: Iterable[int]) -> dict:
    categories = BlockCategories(config)
    chunks = inventory(world / "region")
    results = []
    for requested in sample_sizes:
        count = min(requested, len(chunks))
        # Evenly distributed samples avoid benchmarking only downtown or only ocean.
        indices = np.linspace(0, len(chunks) - 1, count, dtype=np.int64)
        sample = [chunks[int(i)] for i in indices]
        memo = {}
        started = time.perf_counter()
        raw = 0
        failures = 0
        for location in sample:
            try:
                metric = load_chunk(location, categories,
                                    companion_slots(world, location, "poi", memo),
                                    companion_slots(world, location, "entities", memo))
                raw += metric.raw_bytes
            except Exception:
                failures += 1
        elapsed = time.perf_counter() - started
        results.append({
            "requested_chunks": requested, "processed_chunks": count - failures,
            "failed_chunks": failures, "elapsed_seconds": round(elapsed, 3),
            "chunks_per_second": round((count - failures) / elapsed, 2),
            "milliseconds_per_chunk": round(elapsed * 1000 / max(count - failures, 1), 3),
            "peak_rss_mb": round(rss_mb(), 1), "decoded_nbt_bytes": raw,
            "decoded_bytes_per_chunk": round(raw / max(count - failures, 1)),
            "estimated_full_seconds": round(elapsed / count * len(chunks), 1),
            "selection": "evenly distributed over deterministic chunk inventory",
        })
    return {"world": str(world), "available_chunks": len(chunks), "samples": results}


def _aggregate_cells(metric: ChunkMetrics) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    shapes = (4, CELL_BLOCKS, 4, CELL_BLOCKS)
    heights = metric.heights.reshape(shapes).transpose(0, 2, 1, 3)
    classes = metric.categories.reshape(shapes).transpose(0, 2, 1, 3)
    mean_h = heights.mean(axis=(2, 3))
    var_h = heights.var(axis=(2, 3))
    artificial = np.isin(classes, [CATEGORY_CODE[n] for n in ARTIFICIAL]).mean(axis=(2, 3))
    water = (classes == CATEGORY_CODE["water"]).mean(axis=(2, 3))
    road = np.isin(classes, [CATEGORY_CODE["road_candidate"], CATEGORY_CODE["sidewalk_candidate"]]).mean(axis=(2, 3))
    roof = np.isin(classes, [CATEGORY_CODE["roof_candidate"], CATEGORY_CODE["building_candidate"]]).mean(axis=(2, 3))
    dominant = np.zeros((4, 4), dtype=np.uint8)
    for z in range(4):
        for x in range(4):
            dominant[z, x] = np.bincount(classes[z, x].ravel(), minlength=len(CATEGORY_NAMES)).argmax()
    return mean_h, var_h, dominant, artificial, water, road, roof


def pass1(world: Path, output: Path, db_path: Path, manifest: dict, config: Path) -> dict:
    categories = BlockCategories(config)
    all_chunks = inventory(world / "region")
    bounds = manifest["world_bounds"]
    width_chunks = bounds["max_chunk_x"] - bounds["min_chunk_x"] + 1
    depth_chunks = bounds["max_chunk_z"] - bounds["min_chunk_z"] + 1
    shape = (depth_chunks * 4, width_chunks * 4)
    cache_dir = output / "cache"
    cache_file = cache_dir / "pass1_surface.npz"
    connection = connect(db_path)
    start_scan(connection, len(all_chunks))
    previous = {}
    for row in connection.execute("SELECT region_x,region_z,size_bytes,mtime_ns,sha256 FROM regions"):
        previous[(row[0],row[1])] = RegionSignature(row[2],row[3],row[4])
    region_paths = {region_coords(path): path for path in (world / "region").glob("r.*.*.mca")}
    current = {key: signature(path) for key,path in region_paths.items()}
    cache_shapes_ok = False
    cached_arrays = None
    if cache_file.is_file() and previous:
        try:
            cached_arrays = np.load(cache_file)
            cache_shapes_ok = cached_arrays["height"].shape == shape
        except Exception:
            cache_shapes_ok = False
    unchanged = {key for key,value in current.items() if cache_shapes_ok and reusable(previous.get(key),value)}
    changed = set(current) - unchanged
    if unchanged and cached_arrays is not None:
        cell_height = cached_arrays["height"].copy(); cell_variance = cached_arrays["variance"].copy()
        cell_class = cached_arrays["category"].copy(); cell_artificial = cached_arrays["artificial"].copy()
        cell_water = cached_arrays["water"].copy(); cell_road = cached_arrays["road"].copy()
        cell_roof = cached_arrays["roof"].copy(); cell_density = cached_arrays["density"].copy()
    else:
        cell_height = np.full(shape, np.nan, dtype=np.float32)
        cell_variance = np.full(shape, np.nan, dtype=np.float32)
        cell_class = np.zeros(shape, dtype=np.uint8)
        cell_artificial = np.zeros(shape, dtype=np.float32)
        cell_water = np.zeros(shape, dtype=np.float32)
        cell_road = np.zeros(shape, dtype=np.float32)
        cell_roof = np.zeros(shape, dtype=np.float32)
        cell_density = np.zeros(shape, dtype=np.float32)
        unchanged.clear(); changed = set(current)
    chunks = [chunk for chunk in all_chunks if (chunk.region_x,chunk.region_z) in changed]
    unknown_by_region: dict[tuple[int,int], Counter] = {key: Counter() for key in changed}
    failures = []
    memo = {}
    started = time.perf_counter()
    try:
        connection.execute("DELETE FROM world")
        connection.execute("INSERT INTO world VALUES (1,?,?,?,?,?,?,?,?,?,?,?,?)", (
            manifest["world_name"], manifest["dimension"], manifest["data_version"],
            manifest["level_dat_sha256"], bounds["min_x"], bounds["min_z"],
            bounds["max_x"], bounds["max_z"], manifest["scan_timestamp"],
            manifest["city_index_schema_version"], manifest["tool_version"], json.dumps(manifest)))
        if not unchanged:
            connection.execute("DELETE FROM chunks")
            connection.execute("DELETE FROM surface_cells")
            connection.execute("DELETE FROM unknown_blocks")
            connection.execute("DELETE FROM unknown_region_blocks")
        else:
            for rx,rz in changed:
                connection.execute("DELETE FROM chunks WHERE region_x=? AND region_z=?",(rx,rz))
                connection.execute("DELETE FROM unknown_region_blocks WHERE region_x=? AND region_z=?",(rx,rz))
                cell_x0=(rx*512-bounds["min_x"])//4; cell_z0=(rz*512-bounds["min_z"])//4
                connection.execute("DELETE FROM surface_cells WHERE cell_x BETWEEN ? AND ? AND cell_z BETWEEN ? AND ?",
                                   (cell_x0,cell_x0+127,cell_z0,cell_z0+127))
        batch = []
        for index, location in enumerate(chunks, 1):
            try:
                metric = load_chunk(location, categories,
                                    companion_slots(world, location, "poi", memo),
                                    companion_slots(world, location, "entities", memo))
            except Exception as exc:
                failures.append({"chunk": [location.chunk_x, location.chunk_z], "error": str(exc)})
                continue
            values = metric.values()
            batch.append((location.chunk_x, location.chunk_z, location.region_x, location.region_z,
                          location.timestamp, location.sector_count, metric.data_version, metric.status,
                          values["min_height"], values["max_height"], values["mean_height"], values["height_variance"],
                          values["roughness"], values["water_fraction"], values["artificial_fraction"],
                          values["road_fraction"], values["roof_fraction"], values["vegetation_fraction"],
                          values["unknown_fraction"], values["urban_density"], values["density_class"],
                          int(metric.poi), int(metric.entities)))
            for name in metric.block_names:
                if categories.classify(name) == "unknown_modded":
                    unknown_by_region[(location.region_x,location.region_z)][name] += 1
            mean_h, var_h, dominant, artificial, water, road, roof = _aggregate_cells(metric)
            gz = (location.chunk_z - bounds["min_chunk_z"]) * 4
            gx = (location.chunk_x - bounds["min_chunk_x"]) * 4
            cell_height[gz:gz + 4, gx:gx + 4] = mean_h
            cell_variance[gz:gz + 4, gx:gx + 4] = var_h
            cell_class[gz:gz + 4, gx:gx + 4] = dominant
            cell_artificial[gz:gz + 4, gx:gx + 4] = artificial
            cell_water[gz:gz + 4, gx:gx + 4] = water
            cell_road[gz:gz + 4, gx:gx + 4] = road
            cell_roof[gz:gz + 4, gx:gx + 4] = roof
            cell_density[gz:gz + 4, gx:gx + 4] = values["urban_density"]
            if len(batch) >= 2000:
                connection.executemany("INSERT OR REPLACE INTO chunks VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
                batch.clear()
                connection.commit()
        if batch:
            connection.executemany("INSERT OR REPLACE INTO chunks VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", batch)
        for (rx,rz), counter in unknown_by_region.items():
            connection.executemany("INSERT INTO unknown_region_blocks VALUES (?,?,?,?,?)",
                                   [(rx,rz,name,count,name.partition(":")[0]) for name,count in counter.items()])
        connection.execute("DELETE FROM unknown_blocks")
        connection.execute("INSERT INTO unknown_blocks SELECT block_name,sum(count),namespace FROM unknown_region_blocks GROUP BY block_name,namespace")
        # Surface cells are canonical aggregated features, not a block-by-block copy.
        rows = []
        changed_cell_mask=np.zeros(shape,dtype=bool)
        for rx,rz in changed:
            x0=max(0,(rx*512-bounds["min_x"])//4); z0=max(0,(rz*512-bounds["min_z"])//4)
            x1=min(shape[1],x0+128); z1=min(shape[0],z0+128)
            changed_cell_mask[z0:z1,x0:x1]=True
        for z, x in zip(*np.nonzero(np.isfinite(cell_height) & changed_cell_mask)):
            wx = bounds["min_x"] + x * CELL_BLOCKS
            wz = bounds["min_z"] + z * CELL_BLOCKS
            density = float(cell_density[z, x])
            rows.append((int(x), int(z), wx, wz, wx + 3, wz + 3, float(cell_height[z, x]),
                         float(cell_variance[z, x]), CATEGORY_NAMES[int(cell_class[z, x])],
                         float(cell_water[z, x]), float(cell_artificial[z, x]), float(cell_road[z, x]),
                         float(cell_roof[z, x]), density))
            if len(rows) >= 20000:
                connection.executemany("INSERT OR REPLACE INTO surface_cells VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
                rows.clear()
        if rows:
            connection.executemany("INSERT OR REPLACE INTO surface_cells VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
        connection.execute("DELETE FROM regions")
        connection.executemany("INSERT INTO regions VALUES (?,?,?,?,?,?,?,?)",
                               [(rx,rz,str(region_paths[(rx,rz)].relative_to(world)),value.size_bytes,value.mtime_ns,
                                 value.sha256,sum(1 for c in all_chunks if (c.region_x,c.region_z)==(rx,rz)),int((rx,rz) in unchanged))
                                for (rx,rz),value in current.items()])
        connection.commit()
        recorded_chunks = connection.execute("SELECT count(*) FROM chunks").fetchone()[0]
        expected_cells = int(np.count_nonzero(np.isfinite(cell_height)))
        recorded_cells = connection.execute("SELECT count(*) FROM surface_cells").fetchone()[0]
        complete = (not failures and recorded_chunks == len(all_chunks)
                    and recorded_cells == expected_cells)
        transition_scan(
            connection,
            "PASS1_COMPLETE" if complete else "PARTIAL",
            recorded_chunks=recorded_chunks,
            expected_surface_cells=expected_cells,
            recorded_surface_cells=recorded_cells,
            failure_count=len(failures),
        )
        connection.commit()
        if not complete:
            raise RuntimeError(
                "Pass 1 integrity failure: "
                f"chunks={recorded_chunks}/{len(all_chunks)}, "
                f"surface_cells={recorded_cells}/{expected_cells}, failures={len(failures)}"
            )
    finally:
        connection.close()
    cache_dir.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(cache_dir / "pass1_surface.npz", height=cell_height, variance=cell_variance,
                        category=cell_class, artificial=cell_artificial, water=cell_water,
                        road=cell_road, roof=cell_roof, density=cell_density)
    elapsed = time.perf_counter() - started
    unknown = Counter()
    for counter in unknown_by_region.values(): unknown.update(counter)
    return {"chunks_available": len(all_chunks), "chunks_processed": len(all_chunks) - len(failures),
            "chunks_scanned": len(chunks) - len(failures), "chunks_reused": len(all_chunks)-len(chunks),
            "regions_scanned": len(changed), "regions_reused": len(unchanged),
            "failures": failures[:100], "failure_count": len(failures), "elapsed_seconds": elapsed,
            "chunks_per_second": (len(chunks) - len(failures)) / elapsed if chunks else 0, "peak_rss_mb": rss_mb(),
            "unknown_surface_blocks": dict(unknown.most_common()), "grid_shape": list(shape),
            "cell_blocks": CELL_BLOCKS}
