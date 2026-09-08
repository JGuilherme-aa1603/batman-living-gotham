"""Technical overviews, inspection tiles and semantic candidate crops."""

from __future__ import annotations

import json
import math
from pathlib import Path
import sqlite3

import numpy as np
from PIL import Image, ImageDraw

from .extract import CATEGORY_NAMES, CELL_BLOCKS


COLORS = {
    "missing": (15, 15, 20), "air": (10, 10, 15), "terrain": (105, 92, 62), "natural": (105, 112, 70),
    "vegetation": (45, 115, 55), "water": (35, 95, 165), "road_candidate": (55, 55, 60),
    "sidewalk_candidate": (125, 125, 125), "building_candidate": (170, 130, 105),
    "roof_candidate": (185, 165, 145), "transparent": (125, 185, 205),
    "fence_barrier": (95, 80, 70), "door": (120, 75, 40), "container": (165, 105, 35),
    "unknown_modded": (235, 40, 220),
}
OVERLAY_COLORS = {"building": (255, 70, 50), "road": (245, 215, 60), "rooftop": (70, 240, 240),
                  "alley": (180, 70, 255), "open_area": (100, 235, 120), "water": (30, 110, 255),
                  "spatial_cluster": (255, 140, 30)}


def fit(image: Image.Image, maximum: int = 1800) -> Image.Image:
    scale = min(1.0, maximum / max(image.size))
    if scale == 1:
        return image
    return image.resize((max(1, round(image.width*scale)), max(1, round(image.height*scale))), Image.Resampling.NEAREST)


def surface_rgb(category: np.ndarray) -> np.ndarray:
    rgb = np.zeros((*category.shape, 3), dtype=np.uint8)
    for code, name in enumerate(CATEGORY_NAMES):
        rgb[category == code] = COLORS[name]
    return rgb


def grayscale(array: np.ndarray, missing: np.ndarray) -> Image.Image:
    valid = array[~missing]
    low, high = (float(np.percentile(valid, 2)), float(np.percentile(valid, 98))) if valid.size else (0,1)
    scaled = np.clip((np.nan_to_num(array, nan=low)-low)/max(high-low, 1e-6)*255, 0, 255).astype(np.uint8)
    scaled[missing] = 0
    return Image.fromarray(scaled, "L")


def draw_features(image: Image.Image, rows, bounds: dict, scale_x: float = 1.0, scale_z: float = 1.0) -> None:
    draw = ImageDraw.Draw(image)
    for row in rows:
        x0=(row["min_x"]-bounds["min_x"])/CELL_BLOCKS*scale_x
        z0=(row["min_z"]-bounds["min_z"])/CELL_BLOCKS*scale_z
        x1=(row["max_x"]-bounds["min_x"]+1)/CELL_BLOCKS*scale_x
        z1=(row["max_z"]-bounds["min_z"]+1)/CELL_BLOCKS*scale_z
        draw.rectangle((x0,z0,x1,z1), outline=OVERLAY_COLORS.get(row["kind"],(255,255,255)), width=1)


def render(output: Path, db_path: Path, manifest: dict) -> dict:
    arrays=np.load(output/"cache"/"pass1_surface.npz")
    height, category, density = arrays["height"], arrays["category"], arrays["density"]
    water, road, roof = arrays["water"], arrays["road"], arrays["roof"]
    missing=~np.isfinite(height)
    maps=output/"maps"; tiles=output/"tiles"; candidates_dir=output/"candidates"
    maps.mkdir(parents=True,exist_ok=True); tiles.mkdir(parents=True,exist_ok=True); candidates_dir.mkdir(parents=True,exist_ok=True)
    base=Image.fromarray(surface_rgb(category),"RGB")
    fit(grayscale(height,missing)).save(maps/"heightmap-overview.png")
    fit(Image.fromarray(np.where(missing,0,np.clip(water*255,0,255)).astype(np.uint8),"L")).save(maps/"water-overview.png")
    fit(base).save(maps/"surface-classes-overview.png")
    density_rgb=np.zeros((*density.shape,3),dtype=np.uint8)
    density_rgb[...,0]=np.clip(density*255,0,255).astype(np.uint8)
    density_rgb[...,1]=np.clip((1-density)*130,0,255).astype(np.uint8)
    density_rgb[missing]=(10,10,15)
    fit(Image.fromarray(density_rgb,"RGB")).save(maps/"urban-density-overview.png")
    fit(Image.fromarray(np.where(missing,0,np.clip(road*255,0,255)).astype(np.uint8),"L")).save(maps/"roads-signal-overview.png")
    fit(Image.fromarray(np.where(missing,0,np.clip(roof*255,0,255)).astype(np.uint8),"L")).save(maps/"rooftops-signal-overview.png")

    connection=sqlite3.connect(db_path); connection.row_factory=sqlite3.Row
    features=list(connection.execute("SELECT * FROM features"))
    for kind,filename in (("building","buildings-overview.png"),("road","roads-overview.png"),
                          ("rooftop","rooftops-overview.png"),("alley","alleys-overview.png"),
                          ("open_area","open-areas-overview.png"),("spatial_cluster","spatial-clusters-overview.png")):
        image=fit(base.copy())
        sx=image.width/base.width; sz=image.height/base.height
        draw_features(image,[row for row in features if row["kind"]==kind],manifest["world_bounds"],sx,sz)
        image.save(maps/filename)

    tile_cells=512//CELL_BLOCKS
    metadata=[]
    tiles_x=math.ceil(base.width/tile_cells); tiles_z=math.ceil(base.height/tile_cells)
    bounds=manifest["world_bounds"]
    for tz in range(tiles_z):
        for tx in range(tiles_x):
            x0,z0=tx*tile_cells,tz*tile_cells; x1,z1=min(base.width,x0+tile_cells),min(base.height,z0+tile_cells)
            crop=base.crop((x0,z0,x1,z1)).resize(((x1-x0)*2,(z1-z0)*2),Image.Resampling.NEAREST)
            wx0,wz0=bounds["min_x"]+x0*4,bounds["min_z"]+z0*4
            wx1,wz1=bounds["min_x"]+x1*4-1,bounds["min_z"]+z1*4-1
            local=[row for row in features if row["max_x"]>=wx0 and row["min_x"]<=wx1 and row["max_z"]>=wz0 and row["min_z"]<=wz1]
            draw=ImageDraw.Draw(crop)
            for row in local:
                draw.rectangle(((row["min_x"]-wx0)/2,(row["min_z"]-wz0)/2,
                                (row["max_x"]-wx0+1)/2,(row["max_z"]-wz0+1)/2),
                               outline=OVERLAY_COLORS.get(row["kind"],(255,255,255)),width=1)
            tile_id=f"tile_{tx:03d}_{tz:03d}"; crop.save(tiles/f"{tile_id}.png")
            metadata.append({"tile_id":tile_id,"image":f"tiles/{tile_id}.png",
                             "block_bounds":[wx0,wz0,wx1,wz1],
                             "chunk_bounds":[wx0//16,wz0//16,wx1//16,wz1//16],
                             "pixel_to_world":{"origin_x":wx0,"origin_z":wz0,"blocks_per_pixel":2,"z_direction":"south/+Z"},
                             "building_ids":[row["id"] for row in local if row["kind"]=="building"],
                             "road_ids":[row["id"] for row in local if row["kind"]=="road"]})
    (tiles/"tiles.json").write_text(json.dumps(metadata,indent=2)+"\n",encoding="utf-8")

    generated_candidates=0
    selected_candidates=[]
    for candidate_type in ("wayne_manor","gcpd","industrial","harbor","ace_chemicals"):
        selected_candidates.extend(connection.execute(
            "SELECT * FROM candidates WHERE candidate_type=? ORDER BY score DESC LIMIT 10",(candidate_type,)).fetchall())
    for candidate in selected_candidates:
        feature=connection.execute("SELECT * FROM features WHERE id=?",(candidate["feature_id"],)).fetchone()
        if feature is None: continue
        cx=int((feature["centroid_x"]-bounds["min_x"])/4); cz=int((feature["centroid_z"]-bounds["min_z"])/4)
        radius=96; xa,za=max(0,cx-radius),max(0,cz-radius); xb,zb=min(base.width,cx+radius),min(base.height,cz+radius)
        crop=base.crop((xa,za,xb,zb)).resize((512,512),Image.Resampling.NEAREST)
        d=ImageDraw.Draw(crop); sx=512/max(1,xb-xa); sz=512/max(1,zb-za)
        geometry=json.loads(feature["geometry_json"])
        for run in geometry.get("runs_global_cell_coordinates",[]):
            rx0,rz0,rx1,rz1=run
            d.rectangle(((rx0-xa)*sx,(rz0-za)*sz,(rx1+1-xa)*sx,(rz1+1-za)*sz),
                        outline=(255,30,30),width=max(1,round(min(sx,sz))))
        crop.save(output/candidate["local_image"]); generated_candidates+=1
    # Deterministic contact sheet supports small manual ground truth review.
    unique=[]; seen=set()
    for candidate in selected_candidates:
        if candidate["feature_id"] in seen or not (output/candidate["local_image"]).is_file(): continue
        seen.add(candidate["feature_id"]); unique.append(candidate)
        if len(unique)>=20: break
    sheet=Image.new("RGB",(5*260,4*280),(20,20,24)); draw=ImageDraw.Draw(sheet)
    validation_rows=[]
    for index,candidate in enumerate(unique):
        image=Image.open(output/candidate["local_image"]).convert("RGB").resize((256,256),Image.Resampling.NEAREST)
        x=(index%5)*260; y=(index//5)*280; sheet.paste(image,(x,y))
        label=f"{index+1:02d} {candidate['feature_id'][-8:]}"
        draw.text((x+3,y+259),label,fill=(240,240,240))
        validation_rows.append({"number":index+1,"feature_id":candidate["feature_id"],
                                "source":candidate["local_image"],"manual_review":"pending"})
    validation_dir=output/"validation"; validation_dir.mkdir(exist_ok=True)
    sheet.save(validation_dir/"building-ground-truth-sheet.png")
    (validation_dir/"building-ground-truth-sample.json").write_text(json.dumps(validation_rows,indent=2)+"\n",encoding="utf-8")
    connection.close()
    return {"overview_maps":len(list(maps.glob("*.png"))),"tiles":len(metadata),
            "candidate_crops":generated_candidates,"ground_truth_sheet_samples":len(unique),
            "tile_blocks":512,"source_blocks_per_cell":CELL_BLOCKS}
