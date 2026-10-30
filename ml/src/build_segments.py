"""
SafeRoute - Stage 2: Road Segments and Feature Engineering
Extracts Coimbatore district road network from OpenStreetMap, segments edges into ~200m lengths,
and computes infrastructural, geometric, and proximity features.
"""

import argparse
import logging
import math
import os
import sys
import time
from typing import List, Tuple, Optional

import numpy as np
import pandas as pd
import requests

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("build_segments")

BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


def encode_geohash(lat: float, lng: float, precision: int = 6) -> str:
    """Encodes latitude and longitude into a geohash string of specified precision."""
    lat_range, lng_range = (-90.0, 90.0), (-180.0, 180.0)
    bits = [16, 8, 4, 2, 1]
    bit = 0
    idx = 0
    geohash = []
    even = True

    while len(geohash) < precision:
        if even:
            mid = (lng_range[0] + lng_range[1]) / 2
            if lng > mid:
                idx |= bits[bit]
                lng_range = (mid, lng_range[1])
            else:
                lng_range = (lng_range[0], mid)
        else:
            mid = (lat_range[0] + lat_range[1]) / 2
            if lat > mid:
                idx |= bits[bit]
                lat_range = (mid, lat_range[1])
            else:
                lat_range = (lat_range[0], mid)
        even = not even
        if bit < 4:
            bit += 1
        else:
            geohash.append(BASE32[idx])
            bit = 0
            idx = 0
    return "".join(geohash)


def calculate_bearing(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Calculates forward azimuth / bearing in degrees between two coordinates (0-360)."""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_lambda = math.radians(lng2 - lng1)
    y = math.sin(delta_lambda) * math.cos(phi2)
    x = math.cos(phi1) * math.sin(phi2) - math.sin(phi1) * math.cos(phi2) * math.cos(delta_lambda)
    bearing = (math.degrees(math.atan2(y, x)) + 360.0) % 360.0
    return bearing


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Haversine distance in meters between two coordinates."""
    r = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lng2 - lng1)
    a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def parse_maxspeed(val) -> Tuple[Optional[int], bool]:
    """Parses maxspeed tag into integer speed and missing flag."""
    if val is None or pd.isna(val):
        return None, True
    if isinstance(val, (int, float)):
        return int(val), False
    s = str(val).strip().lower()
    if not s or s in ("none", "signals", "default", "walk"):
        return None, True
    # Handle values like '40 mph', '50 km/h', '40;50'
    parts = s.replace("km/h", "").replace("kph", "").replace("mph", "").split(";")
    try:
        speed = int(float(parts[0].strip()))
        return speed, False
    except ValueError:
        return None, True


def parse_lanes(val) -> Optional[int]:
    """Parses lanes tag into integer lane count."""
    if val is None or pd.isna(val):
        return None
    try:
        return int(float(str(val).split(";")[0].strip()))
    except ValueError:
        return None


def fetch_elevations_opentopodata(coords: List[Tuple[float, float]]) -> List[Optional[float]]:
    """
    Fetches elevation (meters) from OpenTopoData SRTM 30m API in batches of 100 points.
    Returns list of elevation floats (or None on failure).
    """
    elevations = []
    batch_size = 100
    for i in range(0, len(coords), batch_size):
        batch = coords[i:i + batch_size]
        loc_str = "|".join(f"{lat:.6f},{lng:.6f}" for lat, lng in batch)
        url = f"https://api.opentopodata.org/v1/srtm30m?locations={loc_str}"
        try:
            resp = requests.get(url, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                for res in data.get("results", []):
                    elevations.append(res.get("elevation"))
            else:
                elevations.extend([None] * len(batch))
        except Exception as e:
            logger.warning("Elevation API request failed: %s", e)
            elevations.extend([None] * len(batch))
        time.sleep(0.5)
    return elevations


def main():
    parser = argparse.ArgumentParser(description="Extract and process Coimbatore road segments.")
    parser.add_argument("--buffer-km", type=float, default=10.0, help="Buffer distance around district in km.")
    parser.add_argument("--segment-length", type=float, default=200.0, help="Target segment length in meters.")
    parser.add_argument("--output", type=str, default="data/processed/segments.parquet", help="Output Parquet path.")
    args = parser.parse_args()

    try:
        import osmnx as ox
        import geopandas as gpd
        from shapely.geometry import LineString, Point
    except ImportError as e:
        logger.error("Required GIS libraries not found: %s. Install requirements.txt first.", e)
        sys.exit(1)

    logger.info("Downloading road network for Coimbatore district (buffer: %s km)...", args.buffer_km)
    custom_filters = (
        '["highway"~"motorway|motorway_link|trunk|trunk_link|primary|primary_link|'
        'secondary|secondary_link|tertiary|tertiary_link|residential|unclassified"]'
    )

    try:
        # Download Coimbatore drive network
        G = ox.graph_from_place(
            "Coimbatore, Tamil Nadu, India",
            network_type="drive",
            custom_filter=custom_filters,
            buffer_dist=args.buffer_km * 1000.0
        )
    except Exception as e:
        logger.warning("Place geocoding failed (%s). Falling back to Coimbatore bounding box.", e)
        # Bounding box for Coimbatore region
        north, south, east, west = 11.20, 10.85, 77.15, 76.80
        G = ox.graph_from_bbox(
            bbox=(north, south, east, west),
            network_type="drive",
            custom_filter=custom_filters
        )

    # Convert to GeoDataFrame
    gdf_nodes, gdf_edges = ox.graph_to_gdfs(G)
    logger.info("Extracted %d nodes and %d raw edges from OpenStreetMap.", len(gdf_nodes), len(gdf_edges))

    # Download Infrastructure POIs (Hospitals, Police, Fuel)
    logger.info("Querying POIs for proximity features...")
    poi_tags = {
        "amenity": ["hospital", "clinic", "police", "fuel"],
    }
    try:
        pois = ox.geometries_from_place("Coimbatore, Tamil Nadu, India", tags=poi_tags)
    except Exception:
        pois = gpd.GeoDataFrame()

    hospitals = pois[pois["amenity"].isin(["hospital", "clinic"])] if not pois.empty and "amenity" in pois else gpd.GeoDataFrame()
    police = pois[pois["amenity"] == "police"] if not pois.empty and "amenity" in pois else gpd.GeoDataFrame()
    fuel = pois[pois["amenity"] == "fuel"] if not pois.empty and "amenity" in pois else gpd.GeoDataFrame()

    # Optional VIIRS raster
    viirs_raster = None
    raster_path = "data/raw/viirs_night_lights.tif"
    if os.path.exists(raster_path):
        try:
            import rasterio
            viirs_raster = rasterio.open(raster_path)
            logger.info("Loaded VIIRS night lights raster: %s", raster_path)
        except Exception as e:
            logger.warning("Could not read VIIRS raster: %s", e)
    else:
        logger.info("No VIIRS night lights raster found at %s. Feature will be null.", raster_path)

    # Segmenting edges
    target_len = args.segment_length
    records = []

    logger.info("Segmenting edges into ~%dm segments...", target_len)
    for (u, v, k), row in gdf_edges.iterrows():
        geom = row.geometry
        if geom is None or geom.is_empty:
            continue

        edge_len = row.get("length", geom.length * 111000)
        num_segs = max(1, int(round(edge_len / target_len)))
        step = 1.0 / num_segs

        # Edge-level attributes
        highway = row.get("highway")
        if isinstance(highway, list):
            highway = highway[0]
        maxspeed_val, maxspeed_missing = parse_maxspeed(row.get("maxspeed"))
        lanes_val = parse_lanes(row.get("lanes"))
        oneway_val = bool(row.get("oneway", False))

        lit_raw = str(row.get("lit", "")).lower()
        lit_status = "yes" if lit_raw == "yes" else ("no" if lit_raw == "no" else "missing")

        sw_raw = str(row.get("sidewalk", "")).lower()
        sidewalk_status = "yes" if sw_raw in ("both", "left", "right", "yes") else ("no" if sw_raw in ("none", "no") else "missing")

        surface_val = str(row.get("surface", "missing")).lower()
        if surface_val in ("nan", "none", ""):
            surface_val = "missing"

        bridge_val = bool(row.get("bridge", False)) and row.get("bridge") != "no"
        tunnel_val = bool(row.get("tunnel", False)) and row.get("tunnel") != "no"

        coords = list(geom.coords)
        start_pt = coords[0]
        end_pt = coords[-1]
        straight_dist = haversine_m(start_pt[1], start_pt[0], end_pt[1], end_pt[0])
        sinuosity = float(edge_len / max(straight_dist, 1.0)) if straight_dist > 0 else 1.0

        for i in range(num_segs):
            d_start = i * step
            d_end = (i + 1) * step
            d_mid = (d_start + d_end) / 2.0

            pt_start = geom.interpolate(d_start, normalized=True)
            pt_end = geom.interpolate(d_end, normalized=True)
            pt_mid = geom.interpolate(d_mid, normalized=True)

            seg_len = haversine_m(pt_start.y, pt_start.x, pt_end.y, pt_end.x)
            heading = calculate_bearing(pt_start.y, pt_start.x, pt_end.y, pt_end.x)
            gh6 = encode_geohash(pt_mid.y, pt_mid.x, precision=6)

            # Proximity to POIs
            d_hosp = hospitals.distance(pt_mid).min() * 111000 if not hospitals.empty else None
            d_pol = police.distance(pt_mid).min() * 111000 if not police.empty else None
            d_fuel = fuel.distance(pt_mid).min() * 111000 if not fuel.empty else None

            # VIIRS value
            viirs_val = None
            if viirs_raster is not None:
                try:
                    for val in viirs_raster.sample([(pt_mid.x, pt_mid.y)]):
                        viirs_val = float(val[0])
                except Exception:
                    viirs_val = None

            records.append({
                "segment_id": f"seg_{u}_{v}_{k}_{i}",
                "mid_lat": pt_mid.y,
                "mid_lng": pt_mid.x,
                "heading": round(heading, 1),
                "length_m": round(seg_len, 1),
                "highway": str(highway),
                "maxspeed": maxspeed_val,
                "maxspeed_missing": maxspeed_missing,
                "lanes": lanes_val,
                "oneway": oneway_val,
                "lit": lit_status,
                "sidewalk": sidewalk_status,
                "surface": surface_val,
                "sinuosity": round(sinuosity, 3),
                "elevation_change": None,
                "mean_slope": None,
                "junction_count_100m": 0,
                "bridge": bridge_val,
                "tunnel": tunnel_val,
                "dist_hospital_m": round(d_hosp, 1) if d_hosp is not None and not np.isnan(d_hosp) else None,
                "dist_police_m": round(d_pol, 1) if d_pol is not None and not np.isnan(d_pol) else None,
                "dist_fuel_m": round(d_fuel, 1) if d_fuel is not None and not np.isnan(d_fuel) else None,
                "poi_density_200m": 0,
                "viirs_night_light": viirs_val,
                "geohash6": gh6
            })

    df = pd.DataFrame(records)
    logger.info("Generated %d road segments.", len(df))

    # Log missing feature summary
    logger.info("Feature completeness summary:")
    for col in df.columns:
        n_missing = df[col].isna().sum()
        pct_missing = (n_missing / len(df)) * 100.0 if len(df) > 0 else 0
        logger.info("  - %-22s: %6d missing (%5.1f%%)", col, n_missing, pct_missing)

    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    df.to_parquet(args.output, index=False)
    logger.info("Saved segments to %s", args.output)


if __name__ == "__main__":
    main()
