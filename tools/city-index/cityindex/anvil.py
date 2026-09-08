"""Physically read-only Minecraft Anvil/NBT reader.

It opens files with ``rb`` only and never acquires Minecraft's session lock.
"""

from __future__ import annotations

from dataclasses import dataclass
import gzip
from pathlib import Path
import re
import struct
import zlib
from typing import Iterator

from amulet_nbt import load
import numpy as np


REGION_RE = re.compile(r"r\.(-?\d+)\.(-?\d+)\.mca$")


@dataclass(frozen=True)
class ChunkLocation:
    chunk_x: int
    chunk_z: int
    region_x: int
    region_z: int
    slot: int
    offset_sectors: int
    sector_count: int
    timestamp: int
    path: Path


def region_coords(path: Path) -> tuple[int, int]:
    match = REGION_RE.match(path.name)
    if not match:
        raise ValueError(f"Not an Anvil region filename: {path}")
    return int(match.group(1)), int(match.group(2))


def read_header(path: Path) -> list[ChunkLocation]:
    rx, rz = region_coords(path)
    with path.open("rb") as stream:
        header = stream.read(8192)
    if len(header) < 8192:
        return []
    result = []
    for slot in range(1024):
        pos = slot * 4
        offset = int.from_bytes(header[pos:pos + 3], "big")
        sectors = header[pos + 3]
        if not offset:
            continue
        lx, lz = slot % 32, slot // 32
        timestamp = int.from_bytes(header[4096 + pos:4100 + pos], "big")
        result.append(ChunkLocation(rx * 32 + lx, rz * 32 + lz, rx, rz, slot,
                                    offset, sectors, timestamp, path))
    return result


def inventory(region_dir: Path) -> list[ChunkLocation]:
    chunks = []
    for path in sorted(region_dir.glob("r.*.*.mca"), key=lambda p: region_coords(p)):
        chunks.extend(read_header(path))
    return sorted(chunks, key=lambda c: (c.chunk_z, c.chunk_x))


def read_chunk_nbt(location: ChunkLocation):
    with location.path.open("rb") as stream:
        stream.seek(location.offset_sectors * 4096)
        length_raw = stream.read(4)
        if len(length_raw) != 4:
            raise ValueError(f"Truncated chunk length in {location.path} slot {location.slot}")
        length = int.from_bytes(length_raw, "big")
        compression = stream.read(1)
        payload = stream.read(length - 1)
    if compression == b"\x01":
        payload = gzip.decompress(payload)
    elif compression == b"\x02":
        payload = zlib.decompress(payload)
    elif compression != b"\x03":
        raise ValueError(f"Unsupported compression {compression!r}")
    return load(payload, compressed=False).compound, len(payload)


def decode_packed(values, count: int, bits: int, padded: bool = False) -> list[int]:
    """Decode Mojang packed longs, accepting signed LongTag values."""
    mask = (1 << bits) - 1
    longs = [int(value) & ((1 << 64) - 1) for value in values]
    output = []
    if padded:
        per_long = 64 // bits
        for index in range(count):
            li, shift_index = divmod(index, per_long)
            output.append((longs[li] >> (shift_index * bits)) & mask)
    else:
        for index in range(count):
            start = index * bits
            li, shift = divmod(start, 64)
            value = longs[li] >> shift
            if shift + bits > 64:
                value |= longs[li + 1] << (64 - shift)
            output.append(value & mask)
    return output


def decode_heightmap(values, min_y: int = -64, height: int = 384) -> list[int]:
    bits = (height + 1).bit_length()
    # Since 1.16 Mojang pads entries so a value never spans two longs.
    encoded = decode_packed(values, 256, bits, padded=True)
    return [min_y + value - 1 for value in encoded]


def block_name(state) -> str:
    return str(state.get("Name", "minecraft:air"))


def section_surface_names(root, heights: list[int]) -> list[str]:
    sections = {int(section["Y"]): section for section in root.get("sections", [])}
    decoded: dict[int, tuple[list[str], list[int] | None]] = {}
    names = []
    data_version = int(root.get("DataVersion", 0))
    for z in range(16):
        for x in range(16):
            y = heights[z * 16 + x]
            sy = y // 16
            section = sections.get(sy)
            if section is None or "block_states" not in section:
                names.append("minecraft:air")
                continue
            if sy not in decoded:
                states = section["block_states"]
                palette = [block_name(item) for item in states.get("palette", [])]
                if len(palette) <= 1 or "data" not in states:
                    indices = None
                else:
                    bits = max(4, (len(palette) - 1).bit_length())
                    indices = decode_packed(states["data"], 4096, bits, padded=data_version >= 2529)
                decoded[sy] = palette, indices
            palette, indices = decoded[sy]
            if not palette:
                names.append("minecraft:air")
            elif indices is None:
                names.append(palette[0])
            else:
                index = (y % 16) * 256 + z * 16 + x
                palette_index = indices[index]
                names.append(palette[palette_index] if palette_index < len(palette) else "minecraft:air")
    return names


def reconstruct_surface(root, min_y: int = -64) -> tuple[list[int], list[str]]:
    """Fallback for chunks with absent heightmaps.

    This is intentionally used only for the minority of chunks without saved
    heightmaps; it vectorizes section decoding instead of calling per-block
    APIs.
    """
    heights = np.full((16, 16), min_y - 1, dtype=np.int16)
    names = np.full((16, 16), "minecraft:air", dtype=object)
    unresolved = np.ones((16, 16), dtype=bool)
    data_version = int(root.get("DataVersion", 0))
    for section in sorted(root.get("sections", []), key=lambda item: int(item["Y"]), reverse=True):
        if not unresolved.any() or "block_states" not in section:
            continue
        states = section["block_states"]
        palette = [block_name(item) for item in states.get("palette", [])]
        if not palette:
            continue
        if len(palette) == 1:
            if palette[0] in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}:
                continue
            values = np.zeros((16, 16, 16), dtype=np.int16)
        else:
            bits = max(4, (len(palette) - 1).bit_length())
            values = np.asarray(decode_packed(states["data"], 4096, bits,
                                              padded=data_version >= 2529), dtype=np.int16).reshape((16, 16, 16))
        air_ids = {index for index, name in enumerate(palette)
                   if name in {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}}
        solid = ~np.isin(values, list(air_ids))
        sy = int(section["Y"])
        for z, x in zip(*np.nonzero(unresolved)):
            ys = np.flatnonzero(solid[:, z, x])
            if ys.size:
                y = int(ys[-1])
                palette_index = int(values[y, z, x])
                heights[z, x] = sy * 16 + y
                names[z, x] = palette[palette_index]
                unresolved[z, x] = False
    return heights.ravel().tolist(), names.ravel().tolist()


def decode_section(root, section_y: int) -> tuple[list[str], np.ndarray] | None:
    """Decode one section to a [y,z,x] palette-index array."""
    data_version=int(root.get("DataVersion",0))
    for section in root.get("sections",[]):
        if int(section["Y"]) != section_y or "block_states" not in section: continue
        states=section["block_states"]; palette=[block_name(item) for item in states.get("palette",[])]
        if not palette: return None
        if len(palette)==1:
            values=np.zeros((16,16,16),dtype=np.int16)
        else:
            bits=max(4,(len(palette)-1).bit_length())
            values=np.asarray(decode_packed(states["data"],4096,bits,padded=data_version>=2529),dtype=np.int16).reshape((16,16,16))
        return palette,values
    return None


def available_slots(path: Path) -> set[int]:
    return {location.slot for location in read_header(path)} if path.is_file() else set()
