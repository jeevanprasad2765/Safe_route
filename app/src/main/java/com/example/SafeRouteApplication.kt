package com.example

import android.app.Application
import android.util.Log
import com.example.saferoute.data.db.AppDatabase
import com.example.saferoute.data.firebase.FirebaseConnectionManager
import com.example.saferoute.risk.RiskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafeRouteApplication : Application() {

    companion object {
        lateinit var appContext: Application
            private set

        lateinit var riskRepository: RiskRepository
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this

        val db = AppDatabase.getDatabase(this)
        riskRepository = RiskRepository(this, db.riskDao())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                riskRepository.importAssetsIfFirstTime()
            } catch (t: Throwable) {
                Log.w("SafeRouteApp", "Asset import error: ${t.message}")
            }
        }
        try {
            org.maplibre.android.MapLibre.getInstance(this)
            val olaKey = try { BuildConfig.OLA_MAPS_API_KEY.trim() } catch (_: Throwable) { "" }
            if (olaKey.isNotBlank() && !olaKey.startsWith("YOUR_")) {
                org.maplibre.android.storage.FileSource.getInstance(this).setResourceTransform { _, url ->
                    if (url.contains("olamaps.io") && !url.contains("api_key=")) {
                        if (url.contains("?")) "$url&api_key=$olaKey" else "$url?api_key=$olaKey"
                    } else {
                        url
                    }
                }
            }
            Log.i("SafeRouteApp", "MapLibre initialized successfully")
        } catch (t: Throwable) {
            Log.w("SafeRouteApp", "MapLibre native initialization: ${t.message}")
        }

        try {
            FirebaseConnectionManager.init(this)
            Log.i("SafeRouteApp", "SafeRouteApplication started. Firebase state: ${FirebaseConnectionManager.statusInfo.value.state}")
        } catch (e: Exception) {
            Log.e("SafeRouteApp", "Failed to initialize Firebase on app start: ${e.message}")
        }
    }
}
