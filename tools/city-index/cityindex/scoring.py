"""Explicit, reproducible semantic candidate scoring."""

from __future__ import annotations


WEIGHTS = {
    "wayne_manor": {"property_size": .25, "isolation": .30, "road_access": .15,
                    "elevation": .12, "open_land": .10, "core_distance": .08},
    "gcpd": {"building_size": .25, "urbanity": .25, "road_access": .20,
             "centrality": .20, "open_access": .10},
    "industrial": {"large_footprints": .24, "open_yards": .22, "road_access": .18,
                   "water_access": .12, "low_roof": .12, "material_signal": .12},
    "harbor": {"water_access": .35, "open_yards": .20, "road_access": .20,
               "large_structures": .15, "industrial_signal": .10},
    "ace_chemicals": {"industrial_signal": .30, "large_footprints": .20,
                      "open_yards": .18, "road_access": .14, "isolation": .10,
                      "water_access": .08},
}


def weighted_score(kind: str, normalized_features: dict[str, float]) -> tuple[float, list[str]]:
    weights = WEIGHTS[kind]
    values = {name: max(0.0, min(1.0, float(normalized_features.get(name, 0.0))))
              for name in weights}
    score = sum(values[name] * weight for name, weight in weights.items())
    reasons = [f"{name}={values[name]:.3f} × {weight:.2f}"
               for name, weight in sorted(weights.items(), key=lambda item: item[1], reverse=True)]
    return score, reasons
