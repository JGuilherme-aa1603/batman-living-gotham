"""Selective geometry detection, semantic ranking and metadata extraction."""

from __future__ import annotations

from collections import Counter
import json
import math
from pathlib import Path
import sqlite3
import time

import numpy as np

from .anvil import inventory, read_chunk_nbt
from .coords import euclidean_distance, spatial_chunk_bounds
from .extract import CELL_BLOCKS, DIMENSION, rss_mb
from .geometry import component_bounds, connected_components, connected_components_by_value
from .identity import stable_id
from .scoring import weighted_score
from .storage import connect, transition_scan


def shifted(mask: np.ndarray, dx: int, dz: int) -> np.ndarray:
    result = np.zeros_like(mask)
    src_z0, src_z1 = max(0, -dz), min(mask.shape[0], mask.shape[0] - dz)
    src_x0, src_x1 = max(0, -dx), min(mask.shape[1], mask.shape[1] - dx)
    result[src_z0 + dz:src_z1 + dz, src_x0 + dx:src_x1 + dx] = mask[src_z0:src_z1, src_x0:src_x1]
    return result


def ring_values(array: np.ndarray, points: list[tuple[int, int]], padding: int = 2) -> np.ndarray:
    x0, z0, x1, z1 = component_bounds(points)
    xa, za = max(0, x0-padding), max(0, z0-padding)
    xb, zb = min(array.shape[1]-1, x1+padding), min(array.shape[0]-1, z1+padding)
    crop = array[za:zb+1, xa:xb+1].copy()
    for x, z in points:
        crop[z-za, x-xa] = np.nan
    return crop[np.isfinite(crop)]


def feature_row(kind: str, points: list[tuple[int, int]], height_grid: np.ndarray,
                density: np.ndarray, bounds: dict, confidence: float, metrics: dict,
                min_y: int | None = None, max_y: int | None = None) -> tuple:
    x0, z0, x1, z1 = component_bounds(points)
    wx0, wz0 = bounds["min_x"] + x0 * CELL_BLOCKS, bounds["min_z"] + z0 * CELL_BLOCKS
    wx1, wz1 = bounds["min_x"] + (x1 + 1) * CELL_BLOCKS - 1, bounds["min_z"] + (z1 + 1) * CELL_BLOCKS - 1
    values = np.asarray([height_grid[z, x] for x, z in points], dtype=float)
    surface_min = int(np.nanmin(values)) if values.size else 0
    surface_max = int(np.nanmax(values)) if values.size else 0
    min_y = surface_min if min_y is None else min_y
    max_y = surface_max if max_y is None else max_y
    area = len(points) * CELL_BLOCKS * CELL_BLOCKS
    height = max_y - min_y + 1
    volume = area * max(height, 1)
    centroid_x = sum(bounds["min_x"] + (x + .5) * CELL_BLOCKS for x, _ in points) / len(points)
    centroid_z = sum(bounds["min_z"] + (z + .5) * CELL_BLOCKS for _, z in points) / len(points)
    fid = stable_id(kind, DIMENSION, points, (wx0, min_y, wz0, wx1, max_y, wz1))
    cb = spatial_chunk_bounds(wx0, wz0, wx1, wz1)
    tile_id = f"tile_{(wx0-bounds['min_x'])//512:03d}_{(wz0-bounds['min_z'])//512:03d}"
    by_z={}
    for x,z in points: by_z.setdefault(z,[]).append(x)
    runs=[]
    for z,xs in sorted(by_z.items()):
        xs=sorted(xs); start=previous=xs[0]
        for x in xs[1:]:
            if x != previous+1:
                runs.append([start,z,previous,z]); start=x
            previous=x
        runs.append([start,z,previous,z])
    geometry = {"type": "grid_run_length", "cell_blocks": CELL_BLOCKS,
                "cell_count": len(points), "bounds": [wx0, wz0, wx1, wz1],
                "runs_global_cell_coordinates": runs}
    metrics = dict(metrics)
    metrics.setdefault("mean_surface_y", float(np.nanmean(values)))
    metrics.setdefault("urban_density", float(np.mean([density[z, x] for x, z in points])))
    return (fid, kind, DIMENSION, wx0, min_y, wz0, wx1, max_y, wz1,
            centroid_x, (min_y + max_y) / 2, centroid_z, cb[0], cb[1], cb[2], cb[3],
            area, height, volume, confidence, "DETECTED", json.dumps(metrics, sort_keys=True),
            json.dumps(geometry, sort_keys=True), tile_id)


def _insert_features(connection: sqlite3.Connection, rows: list[tuple]) -> None:
    connection.executemany("INSERT OR REPLACE INTO features VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)


def decode_text(value) -> str:
    raw = str(value)
    try:
        parsed = json.loads(raw)
    except (json.JSONDecodeError, TypeError):
        return raw
    if isinstance(parsed, str):
        return parsed
    if isinstance(parsed, dict):
        pieces = [str(parsed.get("text", ""))]
        pieces.extend(str(extra.get("text", "")) for extra in parsed.get("extra", []) if isinstance(extra, dict))
        return "".join(pieces)
    return raw


def extract_block_entities(world: Path, connection: sqlite3.Connection, selected_chunks: set[tuple[int, int]], buildings: list[sqlite3.Row]) -> dict:
    counts = Counter()
    signs = []
    scanned = 0
    for location in inventory(world / "region"):
        if (location.chunk_x, location.chunk_z) not in selected_chunks:
            continue
        root, _ = read_chunk_nbt(location)
        scanned += 1
        for entity in root.get("block_entities", []):
            entity_type = str(entity.get("id", "minecraft:unknown"))
            counts[entity_type] += 1
            if "sign" not in entity_type:
                continue
            x, y, z = int(entity.get("x", 0)), int(entity.get("y", 0)), int(entity.get("z", 0))
            texts = []
            for side in ("front_text", "back_text"):
                for message in entity.get(side, {}).get("messages", []):
                    text = decode_text(message).strip()
                    if text:
                        texts.append(text)
            for key in ("Text1", "Text2", "Text3", "Text4"):
                if key in entity:
                    text = decode_text(entity[key]).strip()
                    if text:
                        texts.append(text)
            raw_text = " | ".join(texts)
            building_id = None
            best = 48.0
            for building in buildings:
                distance = euclidean_distance(x, z, building["centroid_x"], building["centroid_z"])
                if distance < best:
                    best, building_id = distance, building["id"]
            sid = stable_id("sign", DIMENSION, [(x, z)], (x, y, z, x, y, z))
            signs.append((sid, x, y, z, raw_text, entity_type, building_id, "MEASURED"))
    connection.execute("DELETE FROM block_entity_stats")
    connection.executemany("INSERT INTO block_entity_stats VALUES (?,?,?)",
                           [(name, count, name.partition(":")[0]) for name, count in counts.items()])
    connection.execute("DELETE FROM signs")
    connection.executemany("INSERT INTO signs VALUES (?,?,?,?,?,?,?,?)", signs)
    return {"selected_chunks_scanned": scanned, "signs": len(signs), "block_entity_types": dict(counts)}


def run_pass2(world: Path, output: Path, db_path: Path, manifest: dict) -> dict:
    started = time.perf_counter()
    arrays = np.load(output / "cache" / "pass1_surface.npz")
    height = arrays["height"]
    variance = arrays["variance"]
    artificial = arrays["artificial"]
    water = arrays["water"]
    road = arrays["road"]
    roof = arrays["roof"]
    density = arrays["density"]
    finite = np.isfinite(height)
    bounds = manifest["world_bounds"]

    # Height discontinuities and road/open gaps prevent the worst pure-component merges.
    building_mask = finite & (artificial >= .50) & (roof >= .38) & (road < .55) & (water < .20)
    # Remove isolated 4x4 noise, retain compact structures with adjacent roof cells.
    neighbors = sum(shifted(building_mask, dx, dz) for dx, dz in ((-1,0),(1,0),(0,-1),(0,1)))
    building_mask &= neighbors >= 1
    building_components = connected_components_by_value(building_mask, height, maximum_delta=3.5,
                                                         minimum_cells=4)
    building_rows = []
    roof_rows = []
    for points in building_components:
        component_box = component_bounds(points)
        width_cells = component_box[2] - component_box[0] + 1
        depth_cells = component_box[3] - component_box[1] + 1
        # Reject linear road markings, guard rails and elevated viaduct edges.
        # Phase 2 knowingly sacrifices tiny kiosks for materially fewer false buildings.
        aspect_ratio = max(width_cells, depth_cells) / max(1, min(width_cells, depth_cells))
        if width_cells < 3 or depth_cells < 3 or aspect_ratio > 10:
            continue
        values = np.asarray([height[z, x] for x, z in points])
        rings = ring_values(height, points, 2)
        ground = float(np.percentile(rings, 25)) if rings.size else float(np.min(values))
        top = float(np.percentile(values, 90))
        estimated_height = max(1.0, top - ground)
        if estimated_height < 3:
            continue
        flatness = max(0.0, 1.0 - float(np.std(values)) / 8.0)
        compactness = len(points) / max(1, width_cells * depth_cells)
        # Very sparse city-spanning components are typically awnings, markings
        # and paths that happen to touch; they are not single buildings.
        if compactness < .18 and width_cells * depth_cells > 400:
            continue
        confidence = min(1.0, .35 + .30 * flatness + .25 * compactness + .10 * min(len(points)/40, 1))
        bmetrics = {"footprint_area": len(points)*16, "roof_area": len(points)*16,
                    "roof_flatness": flatness, "compactness": compactness,
                    "aspect_ratio": aspect_ratio,
                    "ground_estimate": ground, "estimated_floors": max(1, round(estimated_height/4)),
                    "segmentation_resolution_blocks": CELL_BLOCKS}
        building_rows.append(feature_row("building", points, height, density, bounds, confidence,
                                         bmetrics, int(round(ground)), int(round(top))))
        if len(points) >= 8 and flatness >= .45:
            roofmetrics = {"flatness": flatness, "obstacle_density": 1-flatness,
                           "roof_elevation": float(np.mean(values)), "building_id": building_rows[-1][0]}
            roof_rows.append(feature_row("rooftop", points, height, density, bounds,
                                         min(1.0, confidence*.95), roofmetrics,
                                         int(np.min(values)), int(np.max(values))))

    road_mask = finite & (road >= .50) & (variance <= 12) & (water < .2)
    road_components = connected_components(road_mask, minimum_cells=12)
    road_rows = []
    road_nodes = []
    for points in road_components:
        widths = []
        pset = set(points)
        for x, z in points[::max(1, len(points)//100)]:
            horizontal = 1 + sum((x+d,z) in pset for d in range(1,8)) + sum((x-d,z) in pset for d in range(1,8))
            vertical = 1 + sum((x,z+d) in pset for d in range(1,8)) + sum((x,z-d) in pset for d in range(1,8))
            widths.append(min(horizontal, vertical)*CELL_BLOCKS)
        row = feature_row("road", points, height, density, bounds, .62,
                          {"approx_width": float(np.median(widths)), "connectivity_cells": len(points)})
        road_rows.append(row)
        rid = row[0]
        pset = set(points)
        candidates = []
        for x, z in points:
            degree = sum((x+dx,z+dz) in pset for dx,dz in ((-1,0),(1,0),(0,-1),(0,1)))
            if degree >= 3:
                candidates.append((x,z,degree))
        # Coalesce thick-road node clouds spatially by 6-cell bins.
        bins = {}
        for x,z,degree in candidates:
            bins.setdefault((x//6,z//6), []).append((x,z,degree))
        for values_in_bin in bins.values():
            x = round(sum(v[0] for v in values_in_bin)/len(values_in_bin))
            z = round(sum(v[1] for v in values_in_bin)/len(values_in_bin))
            wx, wz = bounds["min_x"]+(x+.5)*4, bounds["min_z"]+(z+.5)*4
            nid = stable_id("road_node", DIMENSION, [(x,z)], (int(wx),int(wz),int(wx),int(wz)))
            road_nodes.append((nid, wx, wz, max(v[2] for v in values_in_bin), rid))

    water_components = connected_components(finite & (water >= .5), minimum_cells=16)
    water_rows = [feature_row("water", points, height, density, bounds, .90,
                              {"large_water_body": len(points) >= 256}) for points in water_components]

    # Narrow open corridors bounded by structures on opposing sides and near a road.
    near_left = shifted(building_mask, -1, 0) | shifted(building_mask, -2, 0)
    near_right = shifted(building_mask, 1, 0) | shifted(building_mask, 2, 0)
    near_up = shifted(building_mask, 0, -1) | shifted(building_mask, 0, -2)
    near_down = shifted(building_mask, 0, 1) | shifted(building_mask, 0, 2)
    near_road = road_mask.copy()
    for dx,dz in ((-1,0),(1,0),(0,-1),(0,1),(-2,0),(2,0),(0,-2),(0,2)):
        near_road |= shifted(road_mask, dx, dz)
    alley_mask = finite & ~building_mask & (water < .1) & near_road & ((near_left & near_right) | (near_up & near_down))
    alley_components = connected_components(alley_mask, minimum_cells=3)
    alley_rows = []
    for points in alley_components:
        x0,z0,x1,z1 = component_bounds(points)
        length = max(x1-x0+1,z1-z0+1)*4
        width = min(x1-x0+1,z1-z0+1)*4
        if length < 12 or width > 16:
            continue
        alley_rows.append(feature_row("alley", points, height, density, bounds, .55,
                                      {"length": length, "approx_width": width,
                                       "near_road": True, "between_structures": True}))

    open_mask = finite & ~building_mask & ~road_mask & (water < .15) & (variance <= 2.5) & (artificial < .55)
    open_components = connected_components(open_mask, minimum_cells=64)
    open_rows = []
    for points in open_components:
        urban = float(np.mean([density[z,x] for x,z in points]))
        inference = "park_like" if urban > .18 else "large_flat_open_area"
        open_rows.append(feature_row("open_area", points, height, density, bounds, .65,
                                     {"geometry_fact": "large_flat_open_area", "semantic_inference": inference}))

    # Density/road/water gaps yield geometric cluster candidates, never gameplay districts.
    cluster_mask = finite & (density >= .28) & (water < .4)
    cluster_components = connected_components(cluster_mask, minimum_cells=100)
    cluster_rows = [feature_row("spatial_cluster", points, height, density, bounds, .50,
                                {"basis": "density_water_and_urban_gaps", "gameplay_district": False})
                    for points in cluster_components]

    connection = connect(db_path)
    try:
        transition_scan(connection, "RUNNING_PASS2")
        connection.commit()
        connection.execute("DELETE FROM features")
        connection.execute("DELETE FROM road_nodes")
        _insert_features(connection, building_rows + roof_rows + road_rows + water_rows + alley_rows + open_rows + cluster_rows)
        connection.executemany("INSERT INTO road_nodes VALUES (?,?,?,?,?)", road_nodes)
        connection.row_factory = sqlite3.Row
        buildings = list(connection.execute("SELECT * FROM features WHERE kind='building'"))
        selected_chunks = {(row[0], row[1]) for row in connection.execute(
            "SELECT chunk_x,chunk_z FROM chunks WHERE urban_density>=.20 OR poi_present=1")}
        block_entities = extract_block_entities(world, connection, selected_chunks, buildings)
        semantic = rank_candidates(connection, height, artificial, water, road, density, bounds)
        transition_scan(connection, "PASS2_COMPLETE")
        connection.commit()
    finally:
        connection.close()
    elapsed = time.perf_counter() - started
    return {"elapsed_seconds": elapsed, "peak_rss_mb": rss_mb(),
            "buildings": len(building_rows), "roads": len(road_rows), "road_intersections": len(road_nodes),
            "rooftops": len(roof_rows), "alleys": len(alley_rows), "water_bodies": len(water_rows),
            "open_areas": len(open_rows), "spatial_clusters": len(cluster_rows),
            "block_entities": block_entities, "semantic_candidates": semantic,
            "selected_detailed_chunks": len(selected_chunks)}


def rank_candidates(connection: sqlite3.Connection, height: np.ndarray, artificial: np.ndarray,
                    water: np.ndarray, road: np.ndarray, density: np.ndarray, bounds: dict) -> dict:
    connection.row_factory = sqlite3.Row
    buildings = list(connection.execute("SELECT * FROM features WHERE kind='building'"))
    if not buildings:
        return {}
    # Technical urban core: weighted centroid of top density chunks/features.
    core_mask=density >= np.nanpercentile(density[density > 0],95)
    core_components=connected_components(core_mask,minimum_cells=20)
    core_points=max(core_components,key=len) if core_components else [(int(x),int(z)) for z,x in np.argwhere(core_mask)]
    core_x = bounds["min_x"] + (sum(point[0] for point in core_points)/len(core_points) + .5) * 4
    core_z = bounds["min_z"] + (sum(point[1] for point in core_points)/len(core_points) + .5) * 4
    max_distance = math.hypot(bounds["max_x"]-bounds["min_x"], bounds["max_z"]-bounds["min_z"])
    max_area = max(row["area"] for row in buildings)
    max_height = max(row["height"] for row in buildings)
    connection.execute("DELETE FROM candidates")
    rows = []
    for building in buildings:
        cx = int((building["centroid_x"] - bounds["min_x"]) // 4)
        cz = int((building["centroid_z"] - bounds["min_z"]) // 4)
        z0,z1 = max(0,cz-8),min(density.shape[0],cz+9)
        x0,x1 = max(0,cx-8),min(density.shape[1],cx+9)
        local_density = float(np.mean(density[z0:z1,x0:x1]))
        open_land = float(np.mean(artificial[z0:z1,x0:x1] < .25))
        road_access = 1.0 if np.any(road[z0:z1,x0:x1] >= .5) else .25
        water_access = min(1.0, float(np.mean(water[z0:z1,x0:x1] >= .5)) * 8.0)
        core_distance = euclidean_distance(building["centroid_x"], building["centroid_z"], core_x, core_z) / max_distance
        size = min(1.0, math.sqrt(building["area"] / max_area))
        building_metrics=json.loads(building["metrics_json"])
        institutional_size=size*math.sqrt(max(0.0,float(building_metrics.get("compactness",0))))
        elevation = min(1.0, max(0.0, (building["max_y"] - 64) / 120))
        base = {"property_size": size, "isolation": 1-local_density, "road_access": road_access,
                "elevation": elevation, "open_land": open_land, "core_distance": core_distance,
                "building_size": institutional_size, "urbanity": local_density, "centrality": 1-core_distance,
                "open_access": open_land, "large_footprints": size, "open_yards": open_land,
                "water_access": water_access, "low_roof": 1-min(1.0,building["height"]/max_height),
                "material_signal": .35, "industrial_signal": .35, "large_structures": size}
        eligible={
            "wayne_manor": building["area"]>=1000 and local_density<.60 and
                           building["height"]<=60 and float(building_metrics.get("compactness",0))>=.60,
            "gcpd": building["area"]>=500 and building["height"]>=6 and
                    float(building_metrics.get("compactness",0))>=.35,
            "industrial": building["area"]>=1000 and building["height"]<=80,
            "ace_chemicals": building["area"]>=1000 and building["height"]<=80 and local_density<.75,
        }
        for kind in ("wayne_manor", "gcpd", "industrial", "ace_chemicals"):
            if not eligible[kind]:
                continue
            score,reasons = weighted_score(kind,base)
            cid=f"{kind}_{building['id'].split('_',1)[1]}"
            local_image=f"candidates/{cid}.png"
            rows.append((cid,kind,building["id"],score,min(.8,building["confidence"]),json.dumps(reasons),
                         json.dumps(base,sort_keys=True),building["tile_id"],local_image,"INFERRED"))
    # Harbor candidates are coastal open/industrial regions rather than single buildings.
    for feature in connection.execute("SELECT * FROM features WHERE kind='open_area' ORDER BY area DESC LIMIT 200"):
        cx=int((feature["centroid_x"]-bounds["min_x"])//4); cz=int((feature["centroid_z"]-bounds["min_z"])//4)
        z0,z1=max(0,cz-16),min(water.shape[0],cz+17); x0,x1=max(0,cx-16),min(water.shape[1],cx+17)
        base={"water_access":float(np.mean(water[z0:z1,x0:x1]>.25)),
              "open_yards":min(1.0,feature["area"]/10000),"road_access":float(np.mean(road[z0:z1,x0:x1]>.25)),
              "large_structures":float(np.mean(artificial[z0:z1,x0:x1]>.5)),"industrial_signal":.35}
        if base["water_access"]<.02 or base["road_access"]<.01 or base["large_structures"]<.02:
            continue
        score,reasons=weighted_score("harbor",base)
        cid=f"harbor_{feature['id'].split('_',1)[1]}"
        rows.append((cid,"harbor",feature["id"],score,.58,json.dumps(reasons),json.dumps(base,sort_keys=True),
                     feature["tile_id"],f"candidates/{cid}.png","INFERRED"))
    connection.executemany("INSERT INTO candidates VALUES (?,?,?,?,?,?,?,?,?,?)", rows)
    connection.execute("INSERT OR REPLACE INTO performance(stage,chunks,elapsed_seconds,chunks_per_second,peak_rss_mb,output_bytes,notes) VALUES('urban_core',0,0,0,0,0,?)",
                       (json.dumps({"centroid_x":core_x,"centroid_z":core_z,"cells":len(core_points),
                                    "definition":"centroid of largest connected component among top 5% nonzero Pass 1 density cells"}),))
    return {"urban_core": {"x":core_x,"z":core_z}, "ranked_rows":len(rows)}
