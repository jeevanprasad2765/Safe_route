# SafeRoute Road-Accident Risk ML Pipeline (Coimbatore)

This pipeline extracts road network geometry from OpenStreetMap (OSM) for the Coimbatore district area, engineers physical road safety features, trains a LightGBM classification model using spatial cross-validation on verified accident black-spot locations, and exports compact assets directly into the Android application.

> [!IMPORTANT]
> **Hard Rule: Real Data Only.**
> This pipeline and the SafeRoute Android app will NEVER invent, simulate, or seed accident data. If real accident data is missing, the pipeline refuses to train and writes `reports/status.json` with `{"trained": false}`. The Android app then displays OpenStreetMap facts only with a persistent transparency banner.
> Synthetic test fixtures are allowed **ONLY** inside `ml/tests/` (named `synthetic_*`), and are never packaged into the Android application or used in model training.

## Road-Accident Risk vs. Personal Night Safety

- **Road-Accident Risk**: Predicts the likelihood of vehicular crashes based on road infrastructure features (sinuosity, lane count, speed limits, slopes, junction density, verified historical black-spots).
- **Personal Safety (Night)**: Evaluates pedestrian safety after dark based on OSM street lighting tags (`lit=yes/no`), open 24/7 businesses, pharmacy/hospital access, and police proximity.

These two dimensions are strictly separated in both the ML pipeline and the Android UI.

## 100-Row Guard

If `ml/data/blackspots.csv` has fewer than 100 verified entries:
1. `src/train.py` halts execution without training.
2. `reports/status.json` is generated with `{"trained": false, "reason": "Fewer than 100 verified black-spot records."}`.
3. `src/export_assets.py` writes `app/src/main/assets/model_meta.json` with `trained: false`.
4. The Android app disables road-accident risk predictions and displays:
   > *"Predicted accident risk is not available yet - showing OpenStreetMap facts only."*

## Quickstart & Pipeline Execution

```bash
cd ml

# 1. Install dependencies
pip install -r requirements.txt

# 2. Extract road network & compute spatial features (~200m segments)
python src/build_segments.py --buffer-km 10 --segment-length 200

# 3. Train LightGBM with spatial cross-validation & SHAP explanations
python src/train.py

# 4. Export compact assets to Android app/src/main/assets
python src/export_assets.py

# 5. Run pipeline unit tests
pytest tests/ -v
```
