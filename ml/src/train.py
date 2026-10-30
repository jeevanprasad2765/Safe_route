"""
SafeRoute - Stage 3: Accident-Risk Model Training
Trains a LightGBM classifier with spatial cross-validation on verified accident black-spots.
Includes a strict guard: training is refused if fewer than 100 real records are provided.
"""

import argparse
import datetime
import json
import logging
import math
import os
import sys
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("train")

REASON_MAP = {
    "sinuosity": "SHARP_CURVES",
    "lit_code": "NO_LIGHTING_DATA",
    "maxspeed": "HIGH_SPEED_LIMIT",
    "dist_hospital_m": "FAR_FROM_HELP",
    "mean_slope": "STEEP_SLOPE",
    "junction_count_100m": "MANY_JUNCTIONS",
    "dist_police_m": "FAR_FROM_POLICE",
    "viirs_night_light": "LOW_NIGHT_LIGHT",
    "elevation_change": "STEEP_SLOPE",
}


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Calculates haversine distance in meters."""
    r = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lng2 - lng1)
    a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


def check_training_guard(blackspots_path: str, min_rows: int = 100) -> bool:
    """
    Returns True if training should proceed, False if the guard fires.
    Training requires at least `min_rows` verified real black-spots.
    """
    if not os.path.exists(blackspots_path):
        return False
    try:
        df = pd.read_csv(blackspots_path)
        return len(df) >= min_rows
    except Exception:
        return False


def assign_labels(df_segments: pd.DataFrame, df_blackspots: pd.DataFrame, radius_m: float = 100.0) -> Tuple[np.ndarray, np.ndarray]:
    """
    Labels each segment as positive (1) if within radius_m of any verified black-spot.
    All other segments are labelled as unlabelled (0) - NOTE: unlabelled != safe.
    Also returns a boolean array indicating if segment is within 100m of a blackspot.
    """
    labels = np.zeros(len(df_segments), dtype=int)
    near_blackspot = np.zeros(len(df_segments), dtype=bool)

    if df_blackspots.empty:
        return labels, near_blackspot

    bs_lats = df_blackspots["lat"].values
    bs_lngs = df_blackspots["lng"].values

    seg_lats = df_segments["mid_lat"].values
    seg_lngs = df_segments["mid_lng"].values

    # Approximate quick bounding box filter (100m ~ 0.001 deg)
    box_deg = radius_m / 111000.0

    for i in range(len(df_segments)):
        s_lat = seg_lats[i]
        s_lng = seg_lngs[i]

        mask = (np.abs(bs_lats - s_lat) <= box_deg) & (np.abs(bs_lngs - s_lng) <= box_deg)
        if not np.any(mask):
            continue

        c_lats = bs_lats[mask]
        c_lngs = bs_lngs[mask]

        for b_lat, b_lng in zip(c_lats, c_lngs):
            dist = haversine_m(s_lat, s_lng, b_lat, b_lng)
            if dist <= radius_m:
                labels[i] = 1
                near_blackspot[i] = True
                break

    return labels, near_blackspot


def compute_hourly_weights(hourly_csv_path: str, output_path: str) -> None:
    """Computes hourly relative accident risk weights from historical distribution."""
    if not os.path.exists(hourly_csv_path):
        logger.info("No hourly accident distribution found at %s. Skipping hourly weights.", hourly_csv_path)
        return

    try:
        df = pd.read_csv(hourly_csv_path)
        if "hour" in df.columns and "count" in df.columns:
            mean_count = df["count"].mean()
            weights = {}
            for _, row in df.iterrows():
                h = int(row["hour"])
                weights[str(h)] = round(float(row["count"] / mean_count), 3) if mean_count > 0 else 1.0
            
            os.makedirs(os.path.dirname(output_path), exist_ok=True)
            with open(output_path, "w") as f:
                json.dump(weights, f, indent=2)
            logger.info("Saved hourly risk weights to %s", output_path)
    except Exception as e:
        logger.warning("Failed to compute hourly risk weights: %s", e)


def main():
    parser = argparse.ArgumentParser(description="Train SafeRoute accident risk model.")
    parser.add_argument("--segments", type=str, default="data/processed/segments.parquet")
    parser.add_argument("--blackspots", type=str, default="data/blackspots.csv")
    parser.add_argument("--min-rows", type=int, default=100)
    args = parser.parse_args()

    os.makedirs("reports", exist_ok=True)
    status_file = "reports/status.json"

    # GUARD CHECK: Refuse to train if blackspots.csv has fewer than min-rows
    blackspots_path = args.blackspots
    blackspots_count = 0
    if os.path.exists(blackspots_path):
        try:
            df_bs = pd.read_csv(blackspots_path)
            blackspots_count = len(df_bs)
        except Exception:
            blackspots_count = 0
    else:
        df_bs = pd.DataFrame()

    if blackspots_count < args.min_rows:
        reason = (
            f"Only {blackspots_count} real black-spot rows found. Minimum {args.min_rows} required. "
            f"Fill {args.blackspots} with verified accident locations from official sources "
            f"(Tamil Nadu Traffic Police, MoRTH, RTI reports)."
        )
        logger.warning("=" * 70)
        logger.warning("TRAINING GUARD TRIGGERED: %s", reason)
        logger.warning("SafeRoute will degrade gracefully and show OpenStreetMap facts only.")
        logger.warning("=" * 70)

        with open(status_file, "w") as f:
            json.dump({"trained": False, "reason": reason}, f, indent=2)
        sys.exit(0)

    # If guard passes, proceed with training
    logger.info("Guard passed: Found %d verified accident black-spot records. Starting training...", blackspots_count)
    if not os.path.exists(args.segments):
        logger.error("Segments file not found at %s. Run build_segments.py first.", args.segments)
        sys.exit(1)

    df_segments = pd.read_parquet(args.segments)
    logger.info("Loaded %d road segments.", len(df_segments))

    # Positive-Unlabelled Labelling
    y, near_bs = assign_labels(df_segments, df_bs, radius_m=100.0)
    n_pos = int(np.sum(y))
    logger.info("Labelled %d positive segments within 100m of black-spots (%d unlabelled).", n_pos, len(y) - n_pos)

    # Feature Engineering & Encoding
    df = df_segments.copy()
    lit_encoding = {"yes": 1, "no": -1, "missing": 0}
    sw_encoding = {"yes": 1, "no": -1, "missing": 0}

    df["lit_code"] = df["lit"].map(lit_encoding).fillna(0)
    df["sidewalk_code"] = df["sidewalk"].map(sw_encoding).fillna(0)
    df["oneway_code"] = df["oneway"].astype(int)
    df["bridge_code"] = df["bridge"].astype(int)
    df["tunnel_code"] = df["tunnel"].astype(int)
    df["maxspeed_missing_code"] = df["maxspeed_missing"].astype(int)

    # Highway label encoding
    highways = df["highway"].astype("category").cat.codes
    df["highway_code"] = highways

    feature_cols = [
        "length_m", "maxspeed", "maxspeed_missing_code", "lanes", "sinuosity",
        "elevation_change", "mean_slope", "junction_count_100m", "dist_hospital_m",
        "dist_police_m", "dist_fuel_m", "poi_density_200m", "viirs_night_light",
        "lit_code", "sidewalk_code", "oneway_code", "bridge_code", "tunnel_code",
        "highway_code"
    ]

    # Fill NaN for features
    X = df[feature_cols].copy()
    for col in X.columns:
        if X[col].isna().any():
            X[col] = X[col].fillna(X[col].median() if not pd.isna(X[col].median()) else 0)

    # Spatial Cross-Validation: GroupKFold on 2km grid cells
    # 0.02 degrees latitude ~ 2.2 km, 0.02 degrees longitude ~ 2.1 km
    cell_groups = (np.floor(df["mid_lat"].values / 0.02) * 1000 + np.floor(df["mid_lng"].values / 0.02)).astype(int)

    from sklearn.model_selection import GroupKFold
    from sklearn.metrics import roc_auc_score, precision_recall_curve, auc
    import lightgbm as lgb
    from sklearn.calibration import CalibratedClassifierCV

    n_splits = min(5, len(np.unique(cell_groups)))
    gkf = GroupKFold(n_splits=n_splits)

    roc_scores = []
    pr_scores = []
    prec_top5_scores = []

    scale_pos = max(1.0, (len(y) - n_pos) / max(1, n_pos))

    for fold, (train_idx, val_idx) in enumerate(gkf.split(X, y, groups=cell_groups)):
        X_tr, y_tr = X.iloc[train_idx], y[train_idx]
        X_val, y_val = X.iloc[val_idx], y[val_idx]

        clf = lgb.LGBMClassifier(
            n_estimators=150,
            learning_rate=0.05,
            scale_pos_weight=scale_pos,
            random_state=42,
            verbosity=-1
        )
        clf.fit(X_tr, y_tr)

        val_probs = clf.predict_proba(X_val)[:, 1]
        if len(np.unique(y_val)) > 1:
            roc = roc_auc_score(y_val, val_probs)
            p_curve, r_curve, _ = precision_recall_curve(y_val, val_probs)
            pr_val = auc(r_curve, p_curve)

            top5_k = max(1, int(len(y_val) * 0.05))
            top5_idx = np.argsort(val_probs)[-top5_k:]
            prec_top5 = np.mean(y_val[top5_idx])

            roc_scores.append(roc)
            pr_scores.append(pr_val)
            prec_top5_scores.append(prec_top5)
            logger.info("Fold %d: ROC-AUC=%.3f, PR-AUC=%.3f, Precision@top5%%=%.3f", fold + 1, roc, pr_val, prec_top5)

    metrics = {
        "roc_auc": round(float(np.mean(roc_scores)), 3) if roc_scores else 0.5,
        "pr_auc": round(float(np.mean(pr_scores)), 3) if pr_scores else 0.1,
        "precision_top5pct": round(float(np.mean(prec_top5_scores)), 3) if prec_top5_scores else 0.1,
        "n_folds": n_splits,
        "n_segments": len(df_segments),
        "n_positive": n_pos,
        "n_unlabelled": len(y) - n_pos
    }

    with open("reports/metrics.json", "w") as f:
        json.dump(metrics, f, indent=2)
    logger.info("Metrics: %s", metrics)

    # Train final calibrated model on all segments
    base_clf = lgb.LGBMClassifier(
        n_estimators=150,
        learning_rate=0.05,
        scale_pos_weight=scale_pos,
        random_state=42,
        verbosity=-1
    )
    calibrated_clf = CalibratedClassifierCV(estimator=base_clf, method="isotonic", cv=3)
    calibrated_clf.fit(X, y)

    all_risks = calibrated_clf.predict_proba(X)[:, 1]

    # SHAP explanations
    try:
        import shap
        import matplotlib.pyplot as plt

        base_clf.fit(X, y)
        explainer = shap.TreeExplainer(base_clf)
        shap_values = explainer.shap_values(X)
        if isinstance(shap_values, list):
            shap_values = shap_values[1]  # positive class

        # Save feature importance plot
        plt.figure(figsize=(10, 6))
        shap.summary_plot(shap_values, X, show=False)
        plt.tight_layout()
        plt.savefig("reports/shap_summary.png", dpi=150)
        plt.close()
        logger.info("Saved SHAP summary to reports/shap_summary.png")
    except Exception as e:
        logger.warning("SHAP computation failed: %s. Using tree feature importances.", e)
        shap_values = None

    # Quantile levels for risk
    q_low = np.percentile(all_risks, 50)
    q_high = np.percentile(all_risks, 80)

    levels = []
    reasons_list = []

    for idx, risk in enumerate(all_risks):
        if risk < q_low:
            lvl = "Low"
        elif risk < q_high:
            lvl = "Medium"
        else:
            lvl = "High"
        levels.append(lvl)

        seg_reasons = []
        if near_bs[idx]:
            seg_reasons.append("NEAR_KNOWN_BLACKSPOT")

        if shap_values is not None:
            top_feat_indices = np.argsort(np.abs(shap_values[idx]))[::-1]
            for fi in top_feat_indices:
                fn = feature_cols[fi]
                reason_code = REASON_MAP.get(fn, fn.upper())
                if reason_code not in seg_reasons:
                    seg_reasons.append(reason_code)
                if len(seg_reasons) >= 3:
                    break
        else:
            if df.iloc[idx]["sinuosity"] > 1.3:
                seg_reasons.append("SHARP_CURVES")
            if df.iloc[idx]["lit"] != "yes":
                seg_reasons.append("NO_LIGHTING_DATA")
            if df.iloc[idx]["maxspeed"] and df.iloc[idx]["maxspeed"] >= 60:
                seg_reasons.append("HIGH_SPEED_LIMIT")

        reasons_list.append(";".join(seg_reasons[:3]))

    df_segments["risk"] = np.round(all_risks, 4)
    df_segments["level"] = levels
    df_segments["reasons"] = reasons_list

    # Save segments with risk
    out_parquet = "data/processed/segments_with_risk.parquet"
    df_segments.to_parquet(out_parquet, index=False)
    logger.info("Saved risk-annotated segments to %s", out_parquet)

    # Compute hourly weights if available
    compute_hourly_weights("data/raw/hourly_accidents.csv", "reports/hourly_weights.json")

    # Update status
    with open(status_file, "w") as f:
        json.dump({
            "trained": True,
            "training_date": datetime.datetime.now().isoformat(),
            "n_segments": len(df_segments),
            "n_positive": n_pos
        }, f, indent=2)
    logger.info("Training complete. Status written to %s", status_file)


if __name__ == "__main__":
    main()
