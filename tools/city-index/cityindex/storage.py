"""SQLite schema and serialization helpers."""

from __future__ import annotations

from datetime import datetime, timezone
import sqlite3
from pathlib import Path


SCHEMA = """
PRAGMA journal_mode=WAL;
PRAGMA synchronous=NORMAL;
CREATE TABLE IF NOT EXISTS world (
  id INTEGER PRIMARY KEY CHECK(id=1), world_name TEXT NOT NULL, dimension TEXT NOT NULL,
  data_version INTEGER NOT NULL, level_dat_sha256 TEXT NOT NULL,
  min_x INTEGER, min_z INTEGER, max_x INTEGER, max_z INTEGER,
  scan_timestamp TEXT NOT NULL, schema_version INTEGER NOT NULL, tool_version TEXT NOT NULL,
  manifest_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS regions (
  region_x INTEGER, region_z INTEGER, path TEXT, size_bytes INTEGER, mtime_ns INTEGER,
  sha256 TEXT, chunk_count INTEGER, reused INTEGER DEFAULT 0,
  PRIMARY KEY(region_x, region_z)
);
CREATE TABLE IF NOT EXISTS chunks (
  chunk_x INTEGER, chunk_z INTEGER, region_x INTEGER, region_z INTEGER,
  timestamp INTEGER, compressed_sectors INTEGER, data_version INTEGER, status TEXT,
  min_height INTEGER, max_height INTEGER, mean_height REAL, height_variance REAL,
  roughness REAL, water_fraction REAL, artificial_fraction REAL, road_fraction REAL,
  roof_fraction REAL, vegetation_fraction REAL, unknown_fraction REAL,
  urban_density REAL, density_class TEXT, poi_present INTEGER, entities_present INTEGER,
  PRIMARY KEY(chunk_x, chunk_z)
);
CREATE TABLE IF NOT EXISTS surface_cells (
  cell_x INTEGER, cell_z INTEGER, min_x INTEGER, min_z INTEGER, max_x INTEGER, max_z INTEGER,
  mean_height REAL, height_variance REAL, dominant_class TEXT, water_fraction REAL,
  artificial_fraction REAL, road_fraction REAL, roof_fraction REAL, urban_density REAL,
  PRIMARY KEY(cell_x, cell_z)
);
CREATE TABLE IF NOT EXISTS features (
  id TEXT PRIMARY KEY, kind TEXT NOT NULL, dimension TEXT NOT NULL,
  min_x INTEGER, min_y INTEGER, min_z INTEGER, max_x INTEGER, max_y INTEGER, max_z INTEGER,
  centroid_x REAL, centroid_y REAL, centroid_z REAL,
  chunk_min_x INTEGER, chunk_min_z INTEGER, chunk_max_x INTEGER, chunk_max_z INTEGER,
  area REAL, height REAL, volume REAL, confidence REAL, evidence_state TEXT,
  metrics_json TEXT NOT NULL, geometry_json TEXT NOT NULL, tile_id TEXT
);
CREATE INDEX IF NOT EXISTS features_kind_area ON features(kind, area DESC);
CREATE TABLE IF NOT EXISTS signs (
  id TEXT PRIMARY KEY, x INTEGER, y INTEGER, z INTEGER, raw_text TEXT,
  block_entity_type TEXT, building_id TEXT, evidence_state TEXT DEFAULT 'MEASURED'
);
CREATE INDEX IF NOT EXISTS signs_text ON signs(raw_text);
CREATE TABLE IF NOT EXISTS block_entity_stats (type TEXT PRIMARY KEY, count INTEGER, namespace TEXT);
CREATE TABLE IF NOT EXISTS unknown_blocks (block_name TEXT PRIMARY KEY, count INTEGER, namespace TEXT);
CREATE TABLE IF NOT EXISTS unknown_region_blocks (
  region_x INTEGER, region_z INTEGER, block_name TEXT, count INTEGER, namespace TEXT,
  PRIMARY KEY(region_x,region_z,block_name)
);
CREATE TABLE IF NOT EXISTS candidates (
  id TEXT PRIMARY KEY, candidate_type TEXT, feature_id TEXT, score REAL, confidence REAL,
  reasons_json TEXT, metrics_json TEXT, tile_id TEXT, local_image TEXT,
  evidence_state TEXT DEFAULT 'INFERRED'
);
CREATE INDEX IF NOT EXISTS candidates_type_score ON candidates(candidate_type, score DESC);
CREATE TABLE IF NOT EXISTS road_nodes (
  id TEXT PRIMARY KEY, x REAL, z REAL, degree INTEGER, road_id TEXT
);
CREATE TABLE IF NOT EXISTS performance (
  stage TEXT PRIMARY KEY, chunks INTEGER, elapsed_seconds REAL, chunks_per_second REAL,
  peak_rss_mb REAL, output_bytes INTEGER, notes TEXT
);
CREATE TABLE IF NOT EXISTS cache_regions (
  path TEXT PRIMARY KEY, size_bytes INTEGER, mtime_ns INTEGER, sha256 TEXT, extracted_at TEXT
);
CREATE TABLE IF NOT EXISTS scan_state (
  id INTEGER PRIMARY KEY CHECK(id=1), state TEXT NOT NULL,
  expected_chunks INTEGER NOT NULL DEFAULT 0, recorded_chunks INTEGER NOT NULL DEFAULT 0,
  expected_surface_cells INTEGER NOT NULL DEFAULT 0,
  recorded_surface_cells INTEGER NOT NULL DEFAULT 0,
  failure_count INTEGER NOT NULL DEFAULT 0, updated_at TEXT NOT NULL
);
"""


def connect(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    connection.executescript(SCHEMA)
    return connection


def start_scan(connection: sqlite3.Connection, expected_chunks: int) -> None:
    """Commit an incomplete marker before any batch can mutate canonical rows."""
    connection.execute(
        "INSERT OR REPLACE INTO scan_state VALUES (1,'RUNNING_PASS1',?,0,0,0,0,?)",
        (expected_chunks, datetime.now(timezone.utc).isoformat()),
    )
    connection.commit()


def transition_scan(
    connection: sqlite3.Connection,
    state: str,
    *,
    recorded_chunks: int | None = None,
    expected_surface_cells: int | None = None,
    recorded_surface_cells: int | None = None,
    failure_count: int | None = None,
) -> None:
    assignments = ["state=?", "updated_at=?"]
    values: list[object] = [state, datetime.now(timezone.utc).isoformat()]
    for column, value in (
        ("recorded_chunks", recorded_chunks),
        ("expected_surface_cells", expected_surface_cells),
        ("recorded_surface_cells", recorded_surface_cells),
        ("failure_count", failure_count),
    ):
        if value is not None:
            assignments.append(f"{column}=?")
            values.append(value)
    values.append(1)
    cursor = connection.execute(
        f"UPDATE scan_state SET {', '.join(assignments)} WHERE id=?", values
    )
    if cursor.rowcount != 1:
        raise RuntimeError("City Index scan state is missing")


def require_complete(connection: sqlite3.Connection) -> sqlite3.Row:
    row = connection.execute("SELECT * FROM scan_state WHERE id=1").fetchone()
    if row is None:
        raise RuntimeError("City Index has no completion marker; run a fresh scan")
    if row["state"] != "COMPLETE":
        raise RuntimeError(
            f"City Index is not queryable: scan state is {row['state']} "
            f"({row['recorded_chunks']}/{row['expected_chunks']} chunks)"
        )
    return row
