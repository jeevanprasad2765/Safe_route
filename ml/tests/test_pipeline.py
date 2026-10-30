"""
Unit tests for SafeRoute Python ML Pipeline.
Uses synthetic fixtures only.
"""

import math
import os
import sys
import numpy as np
import pandas as pd
import pytest

from synthetic_segments import make_synthetic_segments, make_synthetic_blackspots


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371000.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    delta_phi = math.radians(lat2 - lat1)
    delta_lambda = math.radians(lng2 - lng1)
    a = math.sin(delta_phi / 2.0) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(delta_lambda / 2.0) ** 2
    return 2.0 * r * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


class TestSegmentLengths:
    def test_all_segments_within_50_to_400m(self):
        segs = make_synthetic_segments(n=200)
        assert (segs["length_m"] >= 50).all(), "Some segments below 50m"
        assert (segs["length_m"] <= 400).all(), "Some segments above 400m"

    def test_geohash6_format(self):
        from build_segments import encode_geohash
        gh = encode_geohash(11.0168, 76.9558, precision=6)
        assert len(gh) == 6, f"Geohash precision 6 should be 6 chars, got {len(gh)}"
        assert isinstance(gh, str)
        # Check standard base32 characters
        valid_chars = set("0123456789bcdefghjkmnpqrstuvwxyz")
        assert all(c in valid_chars for c in gh)


class TestLabellingDistance:
    def test_point_over_100m_away_not_labelled_positive(self):
        bs_lat, bs_lng = 11.0000, 77.0000
        # ~200m away north
        seg_lat, seg_lng = 11.0018, 77.0000
        dist = haversine_m(seg_lat, seg_lng, bs_lat, bs_lng)
        assert dist > 100.0, f"Expected >100m, got {dist:.1f}m"

        from train import assign_labels
        df_seg = pd.DataFrame([{"mid_lat": seg_lat, "mid_lng": seg_lng}])
        df_bs = pd.DataFrame([{"lat": bs_lat, "lng": bs_lng}])
        labels, near = assign_labels(df_seg, df_bs, radius_m=100.0)
        assert labels[0] == 0
        assert not near[0]

    def test_point_within_100m_is_labelled_positive(self):
        bs_lat, bs_lng = 11.0000, 77.0000
        # ~50m away north
        seg_lat, seg_lng = 11.00045, 77.0000
        dist = haversine_m(seg_lat, seg_lng, bs_lat, bs_lng)
        assert dist < 100.0, f"Expected <100m, got {dist:.1f}m"

        from train import assign_labels
        df_seg = pd.DataFrame([{"mid_lat": seg_lat, "mid_lng": seg_lng}])
        df_bs = pd.DataFrame([{"lat": bs_lat, "lng": bs_lng}])
        labels, near = assign_labels(df_seg, df_bs, radius_m=100.0)
        assert labels[0] == 1
        assert near[0]


class TestGuardUnder100Rows:
    def test_guard_triggers_with_fewer_than_100_blackspots(self, tmp_path):
        from train import check_training_guard
        bs_path = tmp_path / "blackspots.csv"
        bs = make_synthetic_blackspots(n=50, seed=1)
        bs.to_csv(bs_path, index=False)

        assert not check_training_guard(str(bs_path), min_rows=100)

    def test_guard_passes_with_100_or_more_blackspots(self, tmp_path):
        from train import check_training_guard
        bs_path = tmp_path / "blackspots.csv"
        bs = make_synthetic_blackspots(n=100, seed=2)
        bs.to_csv(bs_path, index=False)

        assert check_training_guard(str(bs_path), min_rows=100)


class TestSpatialCVNoOverlap:
    def test_no_test_segment_shares_cell_with_train_group(self):
        """Verify spatial cross-validation guarantees zero 2km cell overlap."""
        from sklearn.model_selection import GroupKFold

        segs = make_synthetic_segments(n=300, seed=3)
        groups = (np.floor(segs["mid_lat"].values / 0.02) * 1000 + np.floor(segs["mid_lng"].values / 0.02)).astype(int)

        n_splits = min(5, len(np.unique(groups)))
        kf = GroupKFold(n_splits=n_splits)
        X_dummy = np.zeros((len(segs), 1))

        for fold, (train_idx, test_idx) in enumerate(kf.split(X_dummy, groups=groups)):
            train_groups = set(groups[train_idx])
            test_groups = set(groups[test_idx])
            overlap = train_groups & test_groups
            assert len(overlap) == 0, f"Spatial CV leak: cells {overlap} in both train and test in fold {fold}"
