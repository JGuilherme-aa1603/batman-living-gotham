"""Canonical coordinate conversions used by extraction, tiles and queries."""

from __future__ import annotations

from dataclasses import dataclass
import math


CHUNK_SIZE = 16
REGION_CHUNKS = 32
REGION_SIZE = CHUNK_SIZE * REGION_CHUNKS


def world_to_chunk(value: int) -> int:
    return value // CHUNK_SIZE


def chunk_to_region(value: int) -> int:
    return value // REGION_CHUNKS


def world_to_region(value: int) -> int:
    return value // REGION_SIZE


def chunk_local(value: int) -> int:
    return value % CHUNK_SIZE


def chunk_region_local(value: int) -> int:
    return value % REGION_CHUNKS


@dataclass(frozen=True)
class GridTransform:
    """North-up grid: image X grows east, image Y grows south (+Z)."""

    min_x: int
    min_z: int
    blocks_per_pixel: int = 1

    def world_to_pixel(self, x: int, z: int) -> tuple[int, int]:
        return ((x - self.min_x) // self.blocks_per_pixel,
                (z - self.min_z) // self.blocks_per_pixel)

    def pixel_bounds(self, px: int, py: int) -> tuple[int, int, int, int]:
        x = self.min_x + px * self.blocks_per_pixel
        z = self.min_z + py * self.blocks_per_pixel
        return x, z, x + self.blocks_per_pixel - 1, z + self.blocks_per_pixel - 1


@dataclass(frozen=True)
class TileGrid:
    origin_x: int
    origin_z: int
    tile_size_blocks: int = 512

    def world_to_tile(self, x: int, z: int) -> tuple[int, int]:
        return ((x - self.origin_x) // self.tile_size_blocks,
                (z - self.origin_z) // self.tile_size_blocks)

    def tile_bounds(self, tx: int, tz: int) -> tuple[int, int, int, int]:
        x = self.origin_x + tx * self.tile_size_blocks
        z = self.origin_z + tz * self.tile_size_blocks
        return x, z, x + self.tile_size_blocks - 1, z + self.tile_size_blocks - 1


def spatial_chunk_bounds(min_x: int, min_z: int, max_x: int, max_z: int) -> tuple[int, int, int, int]:
    return world_to_chunk(min_x), world_to_chunk(min_z), world_to_chunk(max_x), world_to_chunk(max_z)


def euclidean_distance(ax: float, az: float, bx: float, bz: float) -> float:
    return math.hypot(ax - bx, az - bz)
