"""Command line interface for extraction and City Index queries."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sqlite3
import sys

from .extract import benchmark, fingerprint, pass1, protect_world
from .pass2 import run_pass2
from .visualize import render
from .answers import required_queries
from .interior import probe as interior_probe
from .storage import connect, require_complete, transition_scan


TOOL_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = TOOL_ROOT.parents[1]
DEFAULT_CONFIG = TOOL_ROOT / "config" / "block_categories.json"


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="city-index", description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    for name in ("fingerprint", "benchmark", "scan"):
        item = commands.add_parser(name)
        item.add_argument("world", type=Path)
        item.add_argument("--output", type=Path, required=True)
        item.add_argument("--source-level-hash")
    commands.choices["benchmark"].add_argument("--samples", default="64,1024,8192")
    stats = commands.add_parser("stats")
    stats.add_argument("index", type=Path)
    buildings = commands.add_parser("buildings")
    buildings.add_argument("index", type=Path)
    buildings.add_argument("--min-height", type=float, default=0)
    buildings.add_argument("--min-area", type=float, default=0)
    buildings.add_argument("--limit", type=int, default=20)
    buildings.add_argument("--sort", choices=("area", "height", "volume"), default="area")
    feature = commands.add_parser("feature")
    feature.add_argument("index", type=Path)
    feature.add_argument("id")
    rooftops = commands.add_parser("rooftops")
    rooftops.add_argument("index", type=Path)
    rooftops.add_argument("--min-area", type=float, default=0)
    rooftops.add_argument("--flatness", type=float, default=0)
    rooftops.add_argument("--limit", type=int, default=20)
    near = commands.add_parser("near")
    near.add_argument("index", type=Path)
    near.add_argument("--x", type=float, required=True)
    near.add_argument("--z", type=float, required=True)
    near.add_argument("--radius", type=float, required=True)
    near.add_argument("--limit", type=int, default=50)
    signs = commands.add_parser("signs")
    signs.add_argument("index", type=Path)
    signs.add_argument("--text", required=True)
    candidates = commands.add_parser("candidates")
    candidates.add_argument("index", type=Path)
    candidates.add_argument("type", choices=("wayne_manor", "gcpd", "harbor", "ace_chemicals", "industrial"))
    candidates.add_argument("--limit", type=int, default=10)
    alleys = commands.add_parser("alleys")
    alleys.add_argument("index", type=Path)
    alleys.add_argument("--near-building")
    alleys.add_argument("--limit", type=int, default=30)
    answers = commands.add_parser("answers")
    answers.add_argument("index", type=Path)
    answers.add_argument("--output", type=Path)
    interior = commands.add_parser("interior")
    interior.add_argument("world", type=Path)
    interior.add_argument("index", type=Path)
    interior.add_argument("--output", type=Path)
    interior.add_argument("--limit",type=int,default=3)
    return root


def rows(connection: sqlite3.Connection, sql: str, parameters=()) -> list[dict]:
    connection.row_factory = sqlite3.Row
    return [dict(row) for row in connection.execute(sql, parameters)]


def query(args) -> object:
    if args.command == "answers":
        result=required_queries(args.index)
        if args.output:
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
        return result
    connection = sqlite3.connect(args.index)
    connection.row_factory = sqlite3.Row
    try:
        require_complete(connection)
        if args.command == "stats":
            counts = {table: connection.execute(f"SELECT count(*) FROM {table}").fetchone()[0]
                      for table in ("chunks", "surface_cells", "features", "signs", "road_nodes", "candidates")}
            counts["feature_kinds"] = rows(connection, "SELECT kind,count(*) count FROM features GROUP BY kind ORDER BY kind")
            return counts
        if args.command == "buildings":
            order = {"area": "area", "height": "height", "volume": "volume"}[args.sort]
            return rows(connection, f"SELECT * FROM features WHERE kind='building' AND height>=? AND area>=? ORDER BY {order} DESC LIMIT ?",
                        (args.min_height, args.min_area, args.limit))
        if args.command == "feature":
            found = rows(connection, "SELECT * FROM features WHERE id=?", (args.id,))
            return found[0] if found else None
        if args.command == "rooftops":
            return rows(connection, "SELECT * FROM features WHERE kind='rooftop' AND area>=? AND json_extract(metrics_json,'$.flatness')>=? ORDER BY area*json_extract(metrics_json,'$.flatness') DESC LIMIT ?",
                        (args.min_area, args.flatness, args.limit))
        if args.command == "near":
            return rows(connection, "SELECT *,sqrt((centroid_x-?)*(centroid_x-?)+(centroid_z-?)*(centroid_z-?)) distance FROM features WHERE (centroid_x-?)*(centroid_x-?)+(centroid_z-?)*(centroid_z-?)<=? ORDER BY distance LIMIT ?",
                        (args.x,args.x,args.z,args.z,args.x,args.x,args.z,args.z,args.radius**2,args.limit))
        if args.command == "signs":
            pattern = f"%{args.text}%"
            return rows(connection, "SELECT * FROM signs WHERE raw_text LIKE ? COLLATE NOCASE", (pattern,))
        if args.command == "candidates":
            return rows(connection, "SELECT * FROM candidates WHERE candidate_type=? ORDER BY score DESC LIMIT ?", (args.type,args.limit))
        if args.command == "alleys":
            if args.near_building:
                building = connection.execute("SELECT centroid_x,centroid_z FROM features WHERE id=?", (args.near_building,)).fetchone()
                if not building:
                    return []
                return rows(connection, "SELECT * FROM features WHERE kind='alley' ORDER BY (centroid_x-?)*(centroid_x-?)+(centroid_z-?)*(centroid_z-?) LIMIT ?",
                            (building[0],building[0],building[1],building[1],args.limit))
            return rows(connection, "SELECT * FROM features WHERE kind='alley' ORDER BY confidence DESC,length DESC LIMIT ?".replace("length", "area"), (args.limit,))
    finally:
        connection.close()


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "interior":
        world=protect_world(args.world,PROJECT_ROOT)
        result=interior_probe(world,args.index,args.limit)
        if args.output:
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.write_text(json.dumps(result,indent=2)+"\n",encoding="utf-8")
        print(json.dumps(result,indent=2)); return 0
    if args.command in {"fingerprint", "benchmark", "scan"}:
        world = protect_world(args.world, PROJECT_ROOT)
        output = args.output.resolve()
        output.mkdir(parents=True, exist_ok=True)
        if args.command == "fingerprint":
            result = fingerprint(world, args.source_level_hash)
            (output / "manifest.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        elif args.command == "benchmark":
            result = benchmark(world, DEFAULT_CONFIG, [int(value) for value in args.samples.split(",")])
            (output / "benchmark.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        else:
            manifest = fingerprint(world, args.source_level_hash)
            (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            result = pass1(world, output, output / "city_index.sqlite", manifest, DEFAULT_CONFIG)
            (output / "pass1.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            second = run_pass2(world, output, output / "city_index.sqlite", manifest)
            (output / "pass2.json").write_text(json.dumps(second, indent=2) + "\n", encoding="utf-8")
            connection = connect(output / "city_index.sqlite")
            transition_scan(connection, "RUNNING_RENDER")
            connection.commit()
            connection.close()
            visuals = render(output, output / "city_index.sqlite", manifest)
            (output / "visualization.json").write_text(json.dumps(visuals, indent=2) + "\n", encoding="utf-8")
            connection = connect(output / "city_index.sqlite")
            transition_scan(connection, "COMPLETE")
            connection.commit()
            connection.close()
            result = {"pass1": result, "pass2": second, "visualization": visuals}
        print(json.dumps(result, indent=2))
        return 0
    print(json.dumps(query(args), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
