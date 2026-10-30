package com.example.saferoute.risk

import android.content.Context
import android.util.Log
import com.example.saferoute.data.model.IncidentZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min

class RiskRepository(
    private val context: Context,
    private val riskDao: RiskDao
) {

    private val prefs = context.getSharedPreferences("saferoute_risk_prefs", Context.MODE_PRIVATE)

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    private var cachedMeta: ModelMeta? = null
    private var cachedHourlyWeights: Map<Int, Float>? = null
    private var cachedBlackspots: List<IncidentZone>? = null

    init {
        loadModelMeta()
    }

    fun getModelMeta(): ModelMeta {
        if (cachedMeta == null) {
            loadModelMeta()
        }
        return cachedMeta ?: ModelMeta(
            isTrained = false,
            reason = "Model metadata not available."
        )
    }

    private fun loadModelMeta() {
        try {
            val jsonStr = context.assets.open("model_meta.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val trained = obj.optBoolean("trained", false)
            val reason = obj.optString("reason", null)
            val trDate = obj.optString("training_date", null)
            val nSegs = obj.optInt("n_segments", 0)
            val nPos = obj.optInt("n_positive", 0)
            val limits = obj.optString("limitations", "Estimates based on past accident records and OpenStreetMap. Not a guarantee of safety.")

            cachedMeta = ModelMeta(
                isTrained = trained,
                reason = reason,
                trainingDate = trDate,
                nSegments = nSegs,
                nPositive = nPos,
                limitations = limits
            )
        } catch (e: Exception) {
            Log.w("RiskRepository", "No model_meta.json found or failed to parse: ${e.message}")
            cachedMeta = ModelMeta(
                isTrained = false,
                reason = "Predicted accident risk is not available yet - showing OpenStreetMap facts only."
            )
        }
    }

    fun getHourlyWeights(): Map<Int, Float>? {
        if (cachedHourlyWeights != null) return cachedHourlyWeights
        try {
            val jsonStr = context.assets.open("hourly_weights.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(jsonStr)
            val map = mutableMapOf<Int, Float>()
            for (h in 0..23) {
                if (obj.has(h.toString())) {
                    map[h] = obj.getDouble(h.toString()).toFloat()
                }
            }
            cachedHourlyWeights = map
            return map
        } catch (_: Exception) {
            return null
        }
    }

    suspend fun getPublicBlackspots(): List<IncidentZone> = withContext(Dispatchers.IO) {
        if (cachedBlackspots != null) return@withContext cachedBlackspots!!

        val list = mutableListOf<IncidentZone>()

        // 1. Try blackspots_public.csv.gz
        try {
            context.assets.open("blackspots_public.csv.gz").use { rawIn ->
                GZIPInputStream(rawIn).use { gzIn ->
                    BufferedReader(InputStreamReader(gzIn)).use { reader ->
                        var line = reader.readLine() // Header: lat,lng,severity,year,road_name
                        var idx = 0
                        while (reader.readLine()?.also { line = it } != null) {
                            val parts = line.split(",")
                            if (parts.size >= 5) {
                                val lat = parts[0].toDoubleOrNull() ?: continue
                                val lng = parts[1].toDoubleOrNull() ?: continue
                                val sev = parts[2].toIntOrNull() ?: 3
                                val yr = parts[3].toIntOrNull() ?: 2023
                                val road = parts[4]
                                list.add(
                                    IncidentZone(
                                        id = "pub_bs_$idx",
                                        latitude = lat,
                                        longitude = lng,
                                        radiusMeters = 150.0,
                                        severity = sev,
                                        category = "$road ($yr)",
                                        timestamp = yr.toString()
                                    )
                                )
                                idx++
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to blackspots.json if gz not available
            try {
                val jsonStr = context.assets.open("blackspots.json").bufferedReader().use { it.readText() }
                val array = org.json.JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        IncidentZone(
                            id = obj.optString("id", "bs_$i"),
                            latitude = obj.optDouble("lat", 0.0),
                            longitude = obj.optDouble("lng", 0.0),
                            radiusMeters = obj.optDouble("radiusMeters", 150.0),
                            severity = obj.optInt("severity", 3),
                            category = obj.optString("description", "Accident Black-spot"),
                            timestamp = obj.optString("year", "2023")
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        cachedBlackspots = list
        list
    }

    suspend fun importAssetsIfFirstTime() = withContext(Dispatchers.IO) {
        val meta = getModelMeta()
        if (!meta.isTrained) {
            Log.i("RiskRepository", "Model not trained; skipping risk segments asset import.")
            return@withContext
        }

        val isImported = prefs.getBoolean("risk_imported_v1", false)
        if (isImported && riskDao.getSegmentCount() > 0) {
            Log.i("RiskRepository", "Risk segments already imported.")
            return@withContext
        }

        try {
            _importProgress.value = 0.0f
            val segments = mutableListOf<RiskSegmentEntity>()
            context.assets.open("risk_segments.csv.gz").use { rawIn ->
                GZIPInputStream(rawIn).use { gzIn ->
                    BufferedReader(InputStreamReader(gzIn)).use { reader ->
                        var line = reader.readLine() // Header: segment_id,mid_lat,mid_lng,heading,length_m,risk,level,reasons
                        var count = 0
                        while (reader.readLine()?.also { line = it } != null) {
                            val parts = line.split(",")
                            if (parts.size >= 8) {
                                val sId = parts[0]
                                val lat = parts[1].toDoubleOrNull() ?: continue
                                val lng = parts[2].toDoubleOrNull() ?: continue
                                val hdg = parts[3].toFloatOrNull() ?: 0f
                                val len = parts[4].toFloatOrNull() ?: 200f
                                val rsk = parts[5].toFloatOrNull() ?: 0.5f
                                val lvl = parts[6]
                                val reas = parts[7]
                                val gh = GeohashUtils.encode(lat, lng, 6)

                                segments.add(
                                    RiskSegmentEntity(
                                        segmentId = sId,
                                        geohash6 = gh,
                                        midLat = lat,
                                        midLng = lng,
                                        heading = hdg,
                                        lengthM = len,
                                        risk = rsk,
                                        level = lvl,
                                        reasons = reas
                                    )
                                )
                                count++
                                if (segments.size >= 1000) {
                                    riskDao.insertAll(segments)
                                    segments.clear()
                                }
                            }
                        }
                        if (segments.isNotEmpty()) {
                            riskDao.insertAll(segments)
                            segments.clear()
                        }
                    }
                }
            }
            prefs.edit().putBoolean("risk_imported_v1", true).apply()
            Log.i("RiskRepository", "Successfully imported risk segments into Room DB.")
        } catch (e: Exception) {
            Log.w("RiskRepository", "Could not import risk_segments.csv.gz: ${e.message}")
        } finally {
            _importProgress.value = null
        }
    }

    suspend fun scoreRoute(
        routeCoordinates: List<Pair<Double, Double>>,
        currentHour: Int? = null
    ): RoadRiskScoreDetails = withContext(Dispatchers.IO) {
        val meta = getModelMeta()
        if (!meta.isTrained) {
            return@withContext RoadRiskScoreDetails(
                roadRiskLevel = RoadRiskLevel.LOW,
                meanRisk = 0f,
                p90Risk = 0f,
                highRiskShare = 0f,
                blackSpotsCount = 0,
                confidence = 0f,
                topReasons = emptyList(),
                explanationText = "Predicted risk unavailable - not enough accident data",
                isAvailable = false,
                isHourlyWeightApplied = false,
                hourlyWeightAssumption = null
            )
        }

        if (routeCoordinates.size < 2) {
            return@withContext RoadRiskScoreDetails(
                roadRiskLevel = RoadRiskLevel.LOW,
                meanRisk = 0f,
                p90Risk = 0f,
                highRiskShare = 0f,
                blackSpotsCount = 0,
                confidence = 0f,
                topReasons = emptyList(),
                explanationText = "Limited route geometry for accident scoring",
                isAvailable = true
            )
        }

        // Calculate total route length
        var totalRouteLen = 0.0
        for (i in 0 until routeCoordinates.size - 1) {
            totalRouteLen += GeohashUtils.distanceMeters(
                routeCoordinates[i].first, routeCoordinates[i].second,
                routeCoordinates[i + 1].first, routeCoordinates[i + 1].second
            )
        }

        // Query all relevant geohash cells along route
        val neededGeohashes = mutableSetOf<String>()
        for (pt in routeCoordinates) {
            val gh = GeohashUtils.encode(pt.first, pt.second, 6)
            neededGeohashes.addAll(GeohashUtils.getNeighbors(gh))
        }

        val candidateSegments = riskDao.getSegmentsByGeohashes(neededGeohashes.toList())
        if (candidateSegments.isEmpty()) {
            return@withContext RoadRiskScoreDetails(
                roadRiskLevel = RoadRiskLevel.LOW,
                meanRisk = 0f,
                p90Risk = 0f,
                highRiskShare = 0f,
                blackSpotsCount = 0,
                confidence = 0f,
                topReasons = emptyList(),
                explanationText = "No accident data for this area",
                isAvailable = true
            )
        }

        // Match polyline points to segments within 40m and heading within 45 degrees
        val matchedSegments = mutableListOf<RiskSegmentEntity>()
        var matchedLengthM = 0.0
        val usedSegmentIds = mutableSetOf<String>()

        for (i in 0 until routeCoordinates.size) {
            val pt = routeCoordinates[i]
            val ptHdg = if (i < routeCoordinates.size - 1) {
                val next = routeCoordinates[i + 1]
                GeohashUtils.bearingDegrees(pt.first, pt.second, next.first, next.second)
            } else {
                val prev = routeCoordinates[i - 1]
                GeohashUtils.bearingDegrees(prev.first, prev.second, pt.first, pt.second)
            }

            var bestDist = 40.0 // 40m radius threshold
            var bestSeg: RiskSegmentEntity? = null

            for (seg in candidateSegments) {
                val dist = GeohashUtils.distanceMeters(pt.first, pt.second, seg.midLat, seg.midLng)
                if (dist <= bestDist) {
                    val hdgDiff = GeohashUtils.headingDifference(ptHdg, seg.heading)
                    if (hdgDiff <= 45f) {
                        bestDist = dist
                        bestSeg = seg
                    }
                }
            }

            if (bestSeg != null && usedSegmentIds.add(bestSeg.segmentId)) {
                matchedSegments.add(bestSeg)
                matchedLengthM += bestSeg.lengthM
            }
        }

        val confidence = if (totalRouteLen > 0) (matchedLengthM / totalRouteLen).coerceIn(0.0, 1.0).toFloat() else 0f
        if (matchedSegments.isEmpty()) {
            return@withContext RoadRiskScoreDetails(
                roadRiskLevel = RoadRiskLevel.LOW,
                meanRisk = 0f,
                p90Risk = 0f,
                highRiskShare = 0f,
                blackSpotsCount = 0,
                confidence = confidence,
                topReasons = emptyList(),
                explanationText = "No accident data for this area",
                isAvailable = true
            )
        }

        // Compute metrics
        val risks = matchedSegments.map { it.risk }.sorted()
        val meanRisk = risks.average().toFloat()
        val p90Idx = ((risks.size - 1) * 0.90).toInt()
        val p90Risk = risks[p90Idx]

        val highRiskSegs = matchedSegments.filter { it.level.equals("High", ignoreCase = true) }
        val highRiskShare = (highRiskSegs.sumOf { it.lengthM.toDouble() } / max(matchedLengthM, 1.0)).toFloat()

        // Known black-spots within 100m
        val blackspots = getPublicBlackspots()
        val blackSpotsCount = blackspots.count { bs ->
            routeCoordinates.any { pt ->
                GeohashUtils.distanceMeters(pt.first, pt.second, bs.latitude, bs.longitude) <= 100.0
            }
        }

        // Hourly weight
        val hourlyWeights = getHourlyWeights()
        val isHourlyApplied: Boolean
        val hourlyAssumption: String?
        val timeAdjustedMeanRisk: Float

        if (hourlyWeights != null && currentHour != null && hourlyWeights.containsKey(currentHour)) {
            val weight = hourlyWeights[currentHour] ?: 1.0f
            timeAdjustedMeanRisk = (meanRisk * weight).coerceIn(0.0f, 1.0f)
            isHourlyApplied = true
            hourlyAssumption = null
        } else {
            timeAdjustedMeanRisk = meanRisk
            isHourlyApplied = false
            hourlyAssumption = if (currentHour != null && (currentHour < 6 || currentHour >= 21)) {
                "Night-time personal safety adjustment is an assumption (no hourly accident data available)"
            } else null
        }

        // Determine Level
        val level = when {
            p90Risk >= 0.65f || highRiskShare >= 0.25f || blackSpotsCount >= 2 -> RoadRiskLevel.HIGH
            timeAdjustedMeanRisk >= 0.35f || highRiskShare >= 0.10f || blackSpotsCount >= 1 -> RoadRiskLevel.MEDIUM
            else -> RoadRiskLevel.LOW
        }

        // Aggregate top reasons
        val reasonCounts = mutableMapOf<String, Int>()
        if (blackSpotsCount > 0) {
            reasonCounts["NEAR_KNOWN_BLACKSPOT"] = blackSpotsCount * 5
        }
        matchedSegments.forEach { seg ->
            seg.reasons.split(";", ",").map { it.trim() }.filter { it.isNotBlank() }.forEach { code ->
                reasonCounts[code] = (reasonCounts[code] ?: 0) + 1
            }
        }
        val topCodes = reasonCounts.entries.sortedByDescending { it.value }.take(3).map { it.key }

        val humanReasons = topCodes.map { humanReadableReason(it) }
        val explanation = humanReasons.joinToString(" • ")

        RoadRiskScoreDetails(
            roadRiskLevel = level,
            meanRisk = timeAdjustedMeanRisk,
            p90Risk = p90Risk,
            highRiskShare = highRiskShare,
            blackSpotsCount = blackSpotsCount,
            confidence = confidence,
            topReasons = humanReasons,
            explanationText = explanation,
            isAvailable = true,
            isHourlyWeightApplied = isHourlyApplied,
            hourlyWeightAssumption = hourlyAssumption
        )
    }

    private fun humanReadableReason(code: String): String = when (code) {
        "SHARP_CURVES" -> "Sharp curves & ghat bends"
        "NO_LIGHTING_DATA" -> "Unlit or missing lighting data"
        "HIGH_SPEED_LIMIT" -> "High speed limit section"
        "NEAR_KNOWN_BLACKSPOT" -> "Near documented accident hotspot"
        "STEEP_SLOPE" -> "Steep road elevation change"
        "MANY_JUNCTIONS" -> "High junction density"
        "FAR_FROM_HELP" -> "Far from emergency medical care"
        "FAR_FROM_POLICE" -> "Distance to nearest police station"
        "LOW_NIGHT_LIGHT" -> "Low night radiance intensity"
        else -> code.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}
