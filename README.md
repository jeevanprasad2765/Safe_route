# 🛡️ SafeRoute — AI Safety-First Navigation & Emergency Companion

SafeRoute is a native Android application built with **Jetpack Compose**, **Kotlin Coroutines & Flow**, **Room**, and **Firebase**, designed to prioritize personal safety during travel. It dynamically scores routes using multi-factor safety intelligence—evaluating street lighting, foot traffic, verified incident density, and blackspot zones.

---

## 🚀 Key Features

| Feature | Description |
|---|---|
| **Dynamic Safety Scoring** | AI-driven scoring combining street illumination, crowd activity, real-time hours, and historical blackspot data. |
| **Sub-3s Route Calculation** | Parallel routing evaluation with Douglas-Peucker polyline simplification and multi-tier Room caching. |
| **Emergency SOS Broadcast** | Instant SOS trigger with countdown dialog, dispatching live telemetry & alerts to emergency contacts. |
| **Saved Places & Fast Navigation** | Manage favorite destinations with full CRUD (Home, Work, custom stops) and quick routing. |
| **Bilingual Localisation** | Full localization support in **English** and **Tamil (தமிழ்)** with real-time in-app language switching. |
| **Safe Corridors & Places** | Discovers open hospitals, pharmacies, police stations, and 24/7 safe havens along the route. |
| **Firebase Cloud Sync** | Sync emergency contacts and trip audits securely with Firestore. |

---

## 🛠️ Tech Stack & Architecture

- **App Framework**: Android Native, Jetpack Compose, Material 3
- **Language**: Kotlin 2.1+, Coroutines & StateFlow
- **Architecture**: MVVM with Clean Architecture principles
- **Local Persistence**: Room SQLite Database with Geohash indexing
- **Cloud & Auth**: Firebase Auth, Firestore
- **Mapping & Geocoding**: Geoapify API, OpenStreetMap, Leaflet / Google Maps integration
- **ML / Risk Pipeline**: Python pipeline (`ml/`) generating road risk segments & blackspot datasets

---

## 📱 Getting Started

### Prerequisites
- **Android Studio** (Ladybug / Koala or newer)
- **JDK 17+** or **JDK 21**
- Android SDK API 24+ (Target SDK 34 / 35)

### Setup & Run

1. **Clone the repository:**
   ```bash
   git clone https://github.com/jeevanprasad2765/Safe_route.git
   cd Safe_route
   ```

2. **Configure API Keys:**
   Copy `.env.example` to `.env` or set your Geoapify API key:
   ```properties
   GEOAPIFY_API_KEY=your_geoapify_key_here
   ```

3. **Build the Debug APK:**
   ```powershell
   ./gradlew assembleDebug
   ```

4. **Install on connected Device or Emulator:**
   ```powershell
   ./gradlew installDebug
   ```

---

## 📂 Project Structure

```
├── app/                  # Main Android Application module
│   ├── src/main/java/    # Kotlin source code (UI, Data, ViewModels, Risk)
│   ├── src/main/assets/  # Safety models, blackspots, map styles
│   └── src/main/res/     # Resources, drawables, localized strings (en, ta)
├── ml/                   # Machine learning risk analysis and segment pipeline
├── gradle/               # Gradle wrapper and version catalogs
└── README.md             # Project documentation
```

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
