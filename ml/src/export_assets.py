"""
SafeRoute - Stage 4: Asset Exporter
Packages model outputs and black-spots into compact assets for the Android application.
If model training was skipped or guarded, exports model_meta.json with trained=false.
"""

import datetime
import gzip
import json
import logging
import os
import shutil
import sys
import pandas as pd

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("export_assets")


def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    reports_dir = os.path.join(base_dir, "reports")
    status_file = os.path.join(reports_dir, "status.json")
    
    # Target Android assets directory
    assets_dir = os.path.abspath(os.path.join(base_dir, "..", "app", "src", "main", "assets"))
    os.makedirs(assets_dir, exist_ok=True)

    meta_target = os.path.join(assets_dir, "model_meta.json")

    # Read status
    trained = False
    status_data = {}
    if os.path.exists(status_file):
        try:
            with open(status_file, "r") as f:
                status_data = json.load(f)
            trained = status_data.get("trained", False)
        except Exception as e:
            logger.warning("Could not read %s: %s", status_file, e)

    if not trained:
        reason = status_data.get(
            "reason",
            "Accident risk model has not been trained. Verified black-spot dataset contains fewer than 100 rows."
        )
        logger.warning("Training status: trained=false. Reason: %s", reason)
        meta_payload = {
            "trained": False,
            "reason": reason,
            "generated_at": datetime.datetime.now().isoformat()
        }
        with open(meta_target, "w") as f:
            json.dump(meta_payload, f, indent=2)
        logger.info("Exported guarded metadata to %s", meta_target)
        print("Model training was not completed. Exported model_meta.json with trained=false.")
        return

    # Trained = True
    logger.info("Model training confirmed. Exporting compact assets to %s...", assets_dir)

    # 1. Export risk_segments.csv.gz
    risk_parquet = os.path.join(base_dir, "data", "processed", "segments_with_risk.parquet")
    if os.path.exists(risk_parquet):
        df_risk = pd.read_parquet(risk_parquet)
        cols = ["segment_id", "mid_lat", "mid_lng", "heading", "length_m", "risk", "level", "reasons"]
        df_export = df_risk[cols]
        target_gz = os.path.join(assets_dir, "risk_segments.csv.gz")
        with gzip.open(target_gz, "wt", encoding="utf-8") as gz:
            df_export.to_csv(gz, index=False)
        sz_kb = os.path.getsize(target_gz) / 1024.0
        logger.info("Exported %d segments to %s (%.1f KB)", len(df_export), target_gz, sz_kb)

    # 2. Export blackspots_public.csv.gz
    blackspots_csv = os.path.join(base_dir, "data", "blackspots.csv")
    if os.path.exists(blackspots_csv):
        df_bs = pd.read_csv(blackspots_csv)
        bs_cols = [c for c in ["lat", "lng", "severity", "year", "road_name"] if c in df_bs.columns]
        if bs_cols and len(df_bs) > 0:
            df_bs_pub = df_bs[bs_cols]
            target_bs_gz = os.path.join(assets_dir, "blackspots_public.csv.gz")
            with gzip.open(target_bs_gz, "wt", encoding="utf-8") as gz:
                df_bs_pub.to_csv(gz, index=False)
            logger.info("Exported %d black-spots to %s", len(df_bs_pub), target_bs_gz)

    # 3. Read metrics
    metrics_file = os.path.join(reports_dir, "metrics.json")
    metrics_data = {}
    if os.path.exists(metrics_file):
        try:
            with open(metrics_file, "r") as f:
                metrics_data = json.load(f)
        except Exception:
            pass

    # 4. Export model_meta.json
    meta_payload = {
        "trained": True,
        "training_date": status_data.get("training_date", datetime.datetime.now().isoformat()),
        "n_segments": status_data.get("n_segments", 0),
        "n_positive": status_data.get("n_positive", 0),
        "metrics": metrics_data,
        "data_sources": [
            "OpenStreetMap road network via osmnx",
            "blackspots.csv (user-provided from official sources)",
            "SRTM 30m elevation via OpenTopoData"
        ],
        "limitations": "Estimates based on past accident records and OpenStreetMap. Not a guarantee of safety.",
        "generated_at": datetime.datetime.now().isoformat()
    }
    with open(meta_target, "w") as f:
        json.dump(meta_payload, f, indent=2)
    logger.info("Exported model metadata to %s", meta_target)

    # 5. Export hourly weights if present
    hourly_src = os.path.join(reports_dir, "hourly_weights.json")
    if os.path.exists(hourly_src):
        hourly_dest = os.path.join(assets_dir, "hourly_weights.json")
        shutil.copyfile(hourly_src, hourly_dest)
        logger.info("Copied hourly weights to %s", hourly_dest)


if __name__ == "__main__":
    main()
