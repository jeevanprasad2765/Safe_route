# SafeRoute ML Data Sources & Schemas

## 1. Verified Accident Black-Spots (`ml/data/blackspots.csv`)

### Schema
```csv
lat,lng,year,severity,road_name,source
```

- **`lat`** (float): Decimal latitude (WGS84), e.g. `11.0168`.
- **`lng`** (float): Decimal longitude (WGS84), e.g. `76.9558`.
- **`year`** (int): Year of recorded accident / black-spot designation (e.g. `2023`).
- **`severity`** (int 1-5):
  - `1`: Minor property damage / minor injury.
  - `3`: Grievous injury / frequent accident junction.
  - `5`: Fatal crash hotspot / official MoRTH priority black-spot.
- **`road_name`** (string): Road or intersection identifier (e.g., `Avinashi Road / Peelamedu Jn`).
- **`source`** (string): Official source (e.g., `TN Police RTI 2023`, `MoRTH Blackspot List 2022`, `Coimbatore City Traffic Police Annual Report`).

> [!NOTE]
> The source field is stripped when exporting public assets (`blackspots_public.csv.gz`) to the Android app.

---

## 2. VIIRS Night-Time Lights Raster (Optional)

- **File location**: `ml/data/raw/viirs_night_lights.tif`
- **Description**: Monthly or annual composite of Nighttime Light Radiance from the Suomi-NPP VIIRS Day/Night Band (DNB).
- **How to obtain**:
  1. Go to [NASA Earthdata](https://earthdata.nasa.gov/) or [Earth Observation Group (EOG)](https://eogdata.mines.edu/products/vnl/).
  2. Download the monthly Cloud-Free DNB Composite covering southern India (lat 10–12, lon 76–78).
  3. Place GeoTIFF as `ml/data/raw/viirs_night_lights.tif`.
- **Pipeline fallback**: If absent, the pipeline fills the `viirs_night_light` column with `null` and logs a notice.

---

## 3. Hourly Accident Distribution (Optional)

- **File location**: `ml/data/raw/hourly_accidents.csv`
- **Schema**:
  ```csv
  hour,count
  ```
- **Description**: Distribution of traffic accidents across the 24 hours of the day (0-23).
- **How to obtain**: From Tamil Nadu State Transport Authority or Coimbatore City Traffic Police annual statistical tables.
- **Pipeline fallback**: If absent, the Android app applies no time-of-day multipliers to accident risk and labels any night-time adjustments as assumptions.

---

## 4. Digital Elevation Model (SRTM 30m)

- **API**: OpenTopoData SRTM 30m endpoint: `https://api.opentopodata.org/v1/srtm30m?locations=lat1,lng1|lat2,lng2`
- **Authentication**: Free public tier, rate-limited to 1 request per second.
- **Pipeline fallback**: If offline or throttled, elevation change and mean slope default to `null`.

---

## 5. Road Network & Infrastructure POIs

- **Source**: OpenStreetMap via `osmnx` (Coimbatore district + 10 km buffer).
- **Highways extracted**: `motorway`, `trunk`, `primary`, `secondary`, `tertiary`, `residential`, `unclassified`.
- **Infrastructure POIs**: Hospitals, police stations, fuel stations, pharmacies within buffer zones.
