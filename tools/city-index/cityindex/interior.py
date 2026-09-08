"""Small on-demand interior feasibility probe; never a global scan."""

from __future__ import annotations

from collections import deque
import json
from pathlib import Path
import sqlite3
import time

import numpy as np

from .anvil import decode_section, inventory, read_chunk_nbt
from .extract import rss_mb
from .storage import require_complete


AIR={"minecraft:air","minecraft:cave_air","minecraft:void_air"}


def analyze_volume(world: Path, feature: sqlite3.Row) -> dict:
    # Cap the proof to keep malformed/merged candidates from allocating huge volumes.
    min_x,max_x=feature["min_x"]-1,feature["max_x"]+1
    min_z,max_z=feature["min_z"]-1,feature["max_z"]+1
    min_y,max_y=feature["min_y"],feature["max_y"]+1
    if max_x-min_x+1>128 or max_z-min_z+1>128 or max_y-min_y+1>80:
        return {"id":feature["id"],"skipped":"volume exceeds 128x80x128 safety cap"}
    shape=(max_y-min_y+1,max_z-min_z+1,max_x-min_x+1)
    solid=np.zeros(shape,dtype=bool); doors=0; chunks_loaded=0
    locations={(c.chunk_x,c.chunk_z):c for c in inventory(world/"region")
               if min_x//16<=c.chunk_x<=max_x//16 and min_z//16<=c.chunk_z<=max_z//16}
    for (cx,cz),location in locations.items():
        root,_=read_chunk_nbt(location); chunks_loaded+=1
        for sy in range(min_y//16,max_y//16+1):
            decoded=decode_section(root,sy)
            if decoded is None: continue
            palette,values=decoded
            for ly in range(16):
                wy=sy*16+ly
                if not min_y<=wy<=max_y: continue
                for lz in range(16):
                    wz=cz*16+lz
                    if not min_z<=wz<=max_z: continue
                    for lx in range(16):
                        wx=cx*16+lx
                        if not min_x<=wx<=max_x: continue
                        name=palette[int(values[ly,lz,lx])]
                        solid[wy-min_y,wz-min_z,wx-min_x]=name not in AIR
                        if "door" in name: doors+=1
    air=~solid; exterior=np.zeros(shape,dtype=bool); queue=deque()
    # Seed all boundary air cells.
    for y,z,x in zip(*np.nonzero(air)):
        if y in (0,shape[0]-1) or z in (0,shape[1]-1) or x in (0,shape[2]-1):
            exterior[y,z,x]=True; queue.append((y,z,x))
    while queue:
        y,z,x=queue.popleft()
        for dy,dz,dx in ((-1,0,0),(1,0,0),(0,-1,0),(0,1,0),(0,0,-1),(0,0,1)):
            ny,nz,nx=y+dy,z+dz,x+dx
            if 0<=ny<shape[0] and 0<=nz<shape[1] and 0<=nx<shape[2] and air[ny,nz,nx] and not exterior[ny,nz,nx]:
                exterior[ny,nz,nx]=True; queue.append((ny,nz,nx))
    enclosed=air & ~exterior
    floor_levels=[]
    for y in range(1,shape[0]-2):
        navigable=enclosed[y] & enclosed[y+1] & solid[y-1]
        if int(navigable.sum())>=9: floor_levels.append(min_y+y)
    return {"id":feature["id"],"bounds":[min_x,min_y,min_z,max_x,max_y,max_z],
            "volume_blocks":int(np.prod(shape)),"chunks_loaded":chunks_loaded,"doors":doors,
            "enclosed_air_blocks":int(enclosed.sum()),"navigable_floor_levels":floor_levels,
            "room_detection":"enclosed-air feasibility only; doorway-separated room graph not yet reliable"}


def probe(world: Path, db_path: Path, limit: int=3) -> dict:
    started=time.perf_counter(); connection=sqlite3.connect(db_path); connection.row_factory=sqlite3.Row
    require_complete(connection)
    features=list(connection.execute("SELECT * FROM features WHERE kind='building' AND area BETWEEN 500 AND 3000 AND height BETWEEN 6 AND 30 ORDER BY confidence DESC LIMIT ?",(limit,)))
    results=[analyze_volume(world,feature) for feature in features]
    connection.close()
    return {"strategy":"on-demand only","buildings":results,"elapsed_seconds":time.perf_counter()-started,
            "peak_rss_mb":rss_mb(),"global_interior_scan_recommended":False}
