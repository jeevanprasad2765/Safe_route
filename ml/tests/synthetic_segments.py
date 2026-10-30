"""
SYNTHETIC TEST FIXTURES - FOR UNIT TESTING ONLY.
NEVER SHIP IN THE APP OR USE FOR REAL TRAINING.
"""

import numpy as np
import pandas as pd


def make_synthetic_segments(n: int = 500, seed: int = 42) -> pd.DataFrame:
    """Generate synthetic road segments for unit testing only."""
    rng = np.random.default_rng(seed)
    lats = rng.uniform(10.95, 11.10, n)
    lngs = rng.uniform(76.90, 77.05, n)
    return pd.DataFrame({
        "segment_id": [f"synthetic_seg_{i}" for i in range(n)],
        "mid_lat": lats,
        "mid_lng": lngs,
        "heading": rng.uniform(0, 360, n),
        "length_m": rng.uniform(50, 400, n),
        "highway": rng.choice(["primary", "secondary", "tertiary", "residential"], n),
        "maxspeed": rng.choice([30, 40, 50, 60, 80, None], n),
        "maxspeed_missing": rng.choice([True, False], n),
        "lanes": rng.choice([1, 2, 3, None], n),
        "oneway": rng.choice([True, False], n),
        "lit": rng.choice(["yes", "no", "missing"], n),
        "sidewalk": rng.choice(["yes", "no", "missing"], n),
        "surface": rng.choice(["asphalt", "concrete", "unpaved", "missing"], n),
        "sinuosity": rng.uniform(1.0, 2.5, n),
        "elevation_change": rng.uniform(-20, 20, n),
        "mean_slope": rng.uniform(0, 15, n),
        "junction_count_100m": rng.integers(0, 8, n),
        "bridge": rng.choice([True, False], n),
        "tunnel": rng.choice([True, False], n),
        "dist_hospital_m": rng.uniform(50, 5000, n),
        "dist_police_m": rng.uniform(50, 5000, n),
        "dist_fuel_m": rng.uniform(50, 5000, n),
        "poi_density_200m": rng.integers(0, 20, n),
        "viirs_night_light": rng.uniform(0, 50, n),
        "geohash6": ["tcb000"] * n,
    })


def make_synthetic_blackspots(n: int = 50, seed: int = 42) -> pd.DataFrame:
    """Generate synthetic blackspots for unit testing only."""
    rng = np.random.default_rng(seed)
    lats = rng.uniform(10.95, 11.10, n)
    lngs = rng.uniform(76.90, 77.05, n)
    return pd.DataFrame({
        "lat": lats,
        "lng": lngs,
        "year": rng.integers(2018, 2024, n),
        "severity": rng.integers(1, 5, n),
        "road_name": [f"Synthetic Road {i}" for i in range(n)],
        "source": ["SYNTHETIC_TEST_DATA"] * n,
    })
