"""Required evidence-backed Phase 2 queries, using SQLite only."""

from __future__ import annotations

import json
from pathlib import Path
import sqlite3

from .storage import require_complete


def compact_feature(row: sqlite3.Row) -> dict:
    return {"id":row["id"],"coordinates":[round(row["centroid_x"],1),round(row["centroid_y"],1),round(row["centroid_z"],1)],
            "bounds":[row["min_x"],row["min_y"],row["min_z"],row["max_x"],row["max_y"],row["max_z"]],
            "area":row["area"],"height":row["height"],"volume":row["volume"],
            "confidence":row["confidence"],"tile":f"tiles/{row['tile_id']}.png",
            "metrics":json.loads(row["metrics_json"])}


def required_queries(db_path: Path) -> dict:
    connection=sqlite3.connect(db_path); connection.row_factory=sqlite3.Row
    require_complete(connection)
    def features(where: str, order: str, limit: int=20):
        return [compact_feature(row) for row in connection.execute(
            f"SELECT * FROM features WHERE {where} ORDER BY {order} LIMIT ?",(limit,))]
    def candidates(kind: str, limit: int=10):
        result=[]
        for row in connection.execute("SELECT c.*,f.centroid_x,f.centroid_z FROM candidates c JOIN features f ON f.id=c.feature_id WHERE candidate_type=? ORDER BY score DESC LIMIT ?",(kind,limit)):
            result.append({"candidate_id":row["id"],"feature_id":row["feature_id"],"coordinates":[row["centroid_x"],row["centroid_z"]],
                           "score":row["score"],"confidence":row["confidence"],"reasons":json.loads(row["reasons_json"]),
                           "metrics":json.loads(row["metrics_json"]),"overview_tile":f"tiles/{row['tile_id']}.png",
                           "local_visual":row["local_image"],"evidence_state":row["evidence_state"]})
        return result
    answer={
        "A_urban_density": [dict(row) for row in connection.execute(
            "SELECT chunk_x,chunk_z,chunk_x*16 min_x,chunk_z*16 min_z,urban_density,density_class,mean_height,artificial_fraction,road_fraction FROM chunks ORDER BY urban_density DESC LIMIT 30")],
        "B_largest_buildings_by_footprint":features("kind='building'","area DESC",20),
        "B_largest_buildings_by_volume":features("kind='building'","volume DESC",20),
        "C_tallest_buildings":features("kind='building'","height DESC",20),
        "D_rooftops":features("kind='rooftop'","area*json_extract(metrics_json,'$.flatness') DESC",30),
        "E_isolated_large_properties":candidates("wayne_manor",20),
        "F_industrial_candidates":candidates("industrial",20),
        "G_semantic_signs":[dict(row) for row in connection.execute(
            "SELECT * FROM signs WHERE trim(raw_text)<>'' ORDER BY length(raw_text) DESC LIMIT 100")],
        "H_alley_concentrations":[dict(row) for row in connection.execute(
            "SELECT CAST(centroid_x/256 AS INTEGER)*256 region_x,CAST(centroid_z/256 AS INTEGER)*256 region_z,count(*) alley_count,avg(confidence) mean_confidence FROM features WHERE kind='alley' GROUP BY region_x,region_z ORDER BY alley_count DESC LIMIT 30")],
        "urban_core":json.loads(connection.execute("SELECT notes FROM performance WHERE stage='urban_core'").fetchone()[0]),
        "wayne_manor_candidates":candidates("wayne_manor",10),
        "gcpd_candidates":candidates("gcpd",10),
        "gotham_harbor_candidates":candidates("harbor",10),
        "ace_chemicals_candidates":candidates("ace_chemicals",10),
        "spatial_clusters":features("kind='spatial_cluster'","area DESC",30),
    }
    connection.close()
    return answer
