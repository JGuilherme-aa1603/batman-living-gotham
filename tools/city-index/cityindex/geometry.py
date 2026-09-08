"""Small deterministic grid-geometry algorithms."""

from __future__ import annotations

from collections import deque
import numpy as np


def connected_components(mask: np.ndarray, minimum_cells: int = 1) -> list[list[tuple[int, int]]]:
    seen = np.zeros(mask.shape, dtype=bool)
    components = []
    height, width = mask.shape
    for z in range(height):
        for x in range(width):
            if not mask[z, x] or seen[z, x]:
                continue
            queue = deque([(x, z)])
            seen[z, x] = True
            points = []
            while queue:
                px, pz = queue.popleft()
                points.append((px, pz))
                for nx, nz in ((px - 1, pz), (px + 1, pz), (px, pz - 1), (px, pz + 1)):
                    if 0 <= nx < width and 0 <= nz < height and mask[nz, nx] and not seen[nz, nx]:
                        seen[nz, nx] = True
                        queue.append((nx, nz))
            if len(points) >= minimum_cells:
                components.append(points)
    return components


def connected_components_by_value(mask: np.ndarray, values: np.ndarray, maximum_delta: float,
                                  minimum_cells: int = 1) -> list[list[tuple[int, int]]]:
    """Four-connected components that do not cross sharp height transitions."""
    seen=np.zeros(mask.shape,dtype=bool); components=[]; height,width=mask.shape
    for z in range(height):
        for x in range(width):
            if not mask[z,x] or seen[z,x]: continue
            queue=deque([(x,z)]); seen[z,x]=True; points=[]
            while queue:
                px,pz=queue.popleft(); points.append((px,pz)); value=values[pz,px]
                for nx,nz in ((px-1,pz),(px+1,pz),(px,pz-1),(px,pz+1)):
                    if (0<=nx<width and 0<=nz<height and mask[nz,nx] and not seen[nz,nx]
                            and abs(float(values[nz,nx])-float(value))<=maximum_delta):
                        seen[nz,nx]=True; queue.append((nx,nz))
            if len(points)>=minimum_cells: components.append(points)
    return components


def component_bounds(points: list[tuple[int, int]]) -> tuple[int, int, int, int]:
    xs = [p[0] for p in points]
    zs = [p[1] for p in points]
    return min(xs), min(zs), max(xs), max(zs)


def graph_nodes(mask: np.ndarray) -> list[tuple[int, int, int]]:
    """Return cells whose 8-neighborhood suggests an endpoint/intersection."""
    result = []
    height, width = mask.shape
    for z, x in zip(*np.nonzero(mask)):
        degree = 0
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            if 0 <= x + dx < width and 0 <= z + dz < height and mask[z + dz, x + dx]:
                degree += 1
        if degree == 1 or degree >= 3:
            result.append((int(x), int(z), degree))
    return result
