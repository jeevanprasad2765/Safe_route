package com.example.saferoute.data.network

import android.content.Context
import android.util.Log
import com.example.saferoute.data.db.AppDatabase
import com.example.saferoute.data.db.RouteOverpassCacheEntity
import com.example.saferoute.data.model.CoverageSegment
import com.example.saferoute.data.model.CoverageStatus
import com.example.saferoute.data.model.PlaceCategory
import com.example.saferoute.data.model.RouteCorridorData
import com.example.saferoute.data.model.RoutePlace
import com.example.saferoute.safety.SafetyWeightConfig
import com.example.saferoute.util.OpeningHoursHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Service and repository for querying OpenStreetMap (Overpass API) and Geoapify Places
 * along a route corridor (150m buffer). Caches results in Room database for 24 hours.
 */
class CorridorPlacesRepository(private val context: Context) {

    companion object {
        private const val TAG = "CorridorPlacesRepo"
        private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 Hours

        @Volatile
        private var INSTANCE: CorridorPlacesRepository? = null

        fun getInstance(context: Context): CorridorPlacesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = CorridorPlacesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val db = AppDatabase.getDatabase(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Retrieves places and infrastructure attributes along the provided route polyline.
     */
    suspend fun fetchCorridorData(
        routeCoordinates: List<Pair<Double, Double>>,
        durationMinutes: Int
    ): RouteCorridorData = withContext(Dispatchers.IO) {
        if (routeCoordinates.isEmpty()) return@withContext RouteCorridorData()

        val routeLengthMeters = computeTotalPolylineLengthM(routeCoordinates)
        val geometryHash = hashCoordinates(routeCoordinates)

        // 1. Check Room Database Cache (valid 24h)
        try {
            val cached = db.routeOverpassCacheDao().getCache(geometryHash)
            if (cached != null && (System.currentTimeMillis() - cached.cachedAtEpochMs) < CACHE_VALIDITY_MS) {
                Log.d(TAG, "Cache HIT in Room for route hash $geometryHash")
                return@withContext parseOverpassJson(cached.rawJson, routeCoordinates, routeLengthMeters, durationMinutes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading Room cache: ${e.message}")
        }

        // 2. Simplify polyline to < 100 points using Douglas-Peucker
        val simplifiedPoints = simplifyToMaxPoints(routeCoordinates, maxPoints = 85)
        val polylineAroundParam = simplifiedPoints.joinToString(",") { "${it.first},${it.second}" }

        // 3. Try Overpass API with around filter
        var overpassJson: String? = null
        val overpassQuery = """
            [out:json][timeout:15];
            (
              node(around:150.0,$polylineAroundParam)[shop];
              node(around:150.0,$polylineAroundParam)[amenity~'^(pharmacy|hospital|clinic|doctors|police|fire_station|restaurant|cafe|fast_food|fuel|atm|bank)$'];
              node(around:150.0,$polylineAroundParam)[healthcare];
              node(around:150.0,$polylineAroundParam)[emergency];
              way(around:150.0,$polylineAroundParam)[highway];
            );
            out tags center 150;
        """.trimIndent()

        val mirrors = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        for (endpoint in mirrors) {
            try {
                val postBody = ("data=" + java.net.URLEncoder.encode(overpassQuery, "UTF-8"))
                    .toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(endpoint)
                    .post(postBody)
                    .header("User-Agent", "SafeRoute-Android/1.0 (contact: info@saferoute.app)")
                    .header("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body.contains("\"elements\"")) {
                        overpassJson = body
                        Log.d(TAG, "Successfully fetched from Overpass mirror: $endpoint")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass mirror $endpoint failed: ${e.message}")
            }
        }

        // 4. Save to Room Cache if Overpass succeeded
        if (!overpassJson.isNullOrBlank()) {
            try {
                db.routeOverpassCacheDao().insertCache(
                    RouteOverpassCacheEntity(
                        routeGeometryHash = geometryHash,
                        rawJson = overpassJson,
                        cachedAtEpochMs = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error caching to Room: ${e.message}")
            }
            return@withContext parseOverpassJson(overpassJson, routeCoordinates, routeLengthMeters, durationMinutes)
        }

        // 5. Fallback: Query Geoapify Places API along the polyline corridor, or Coimbatore landmarks
        Log.i(TAG, "Overpass unavailable. Falling back to Geoapify / Coimbatore landmark Places...")
        val fallbackPlaces = fetchGeoapifyPlacesFallback(routeCoordinates).ifEmpty {
            fetchLandmarkPlacesFallback(routeCoordinates)
        }
        return@withContext buildCorridorFromPlaces(fallbackPlaces, routeCoordinates, routeLengthMeters, durationMinutes)
    }

    private fun fetchLandmarkPlacesFallback(
        routeCoords: List<Pair<Double, Double>>
    ): List<RoutePlace> {
        val result = mutableListOf<RoutePlace>()
        val landmarks = GeoapifyService.COIMBATORE_LANDMARKS
        for (landmark in landmarks) {
            val (dist, pos) = calculateDistancesToPolyline(Pair(landmark.latitude, landmark.longitude), routeCoords)
            if (dist <= 650.0) {
                val cat = when (landmark.type) {
                    "hospital", "medical" -> PlaceCategory.HOSPITAL
                    "police" -> PlaceCategory.POLICE
                    "fuel" -> PlaceCategory.FUEL
                    "station", "airport", "transport", "heritage", "education" -> PlaceCategory.OTHER_HELP
                    "mall", "shopping", "business", "commercial" -> PlaceCategory.SHOP
                    "food" -> PlaceCategory.FOOD
                    else -> PlaceCategory.OTHER_HELP
                }
                result.add(
                    RoutePlace(
                        id = landmark.placeId ?: "cbe_${landmark.shortName.lowercase().replace(" ", "_")}",
                        name = landmark.displayName,
                        category = cat,
                        lat = landmark.latitude,
                        lng = landmark.longitude,
                        distanceFromRouteM = dist,
                        positionAlongRouteM = pos,
                        openingHoursRaw = null,
                        isOpenNow = true,
                        isOpenWhenPassing = true,
                        phone = null
                    )
                )
            }
        }
        return result
    }

    private fun parseOverpassJson(
        jsonString: String,
        routeCoords: List<Pair<Double, Double>>,
        routeLengthMeters: Double,
        durationMinutes: Int
    ): RouteCorridorData {
        val rawPlaces = mutableListOf<RoutePlace>()
        var litWaysCount = 0
        var unlitWaysCount = 0
        var untaggedWaysCount = 0
        var totalWaysCount = 0
        val highwayClasses = mutableSetOf<String>()
        val sidewalks = mutableSetOf<String>()
        val maxspeeds = mutableSetOf<String>()
        val surfaces = mutableSetOf<String>()

        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: JSONArray()
            val nowEpoch = System.currentTimeMillis()

            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val type = el.optString("type")
                val tags = el.optJSONObject("tags") ?: JSONObject()

                if (type == "way" && tags.has("highway")) {
                    totalWaysCount++
                    val litTag = tags.optString("lit", "").trim().lowercase()
                    when {
                        litTag in listOf("yes", "24/7", "automatic") -> litWaysCount++
                        litTag == "no" -> unlitWaysCount++
                        else -> untaggedWaysCount++
                    }

                    val hClass = tags.optString("highway", "").lowercase().trim()
                    if (hClass.isNotBlank()) highwayClasses.add(hClass)

                    val sidewalk = tags.optString("sidewalk", "").lowercase().trim()
                    if (sidewalk.isNotBlank() && sidewalk != "no" && sidewalk != "none") {
                        sidewalks.add(sidewalk)
                    }

                    val maxspeed = tags.optString("maxspeed", "").trim()
                    if (maxspeed.isNotBlank()) maxspeeds.add(maxspeed)

                    val surface = tags.optString("surface", "").trim().lowercase()
                    if (surface.isNotBlank()) surfaces.add(surface)

                    continue
                }

                // Place coordinate: either lat/lon (node) or center (way/relation)
                val lat = if (el.has("lat")) el.optDouble("lat") else el.optJSONObject("center")?.optDouble("lat") ?: continue
                val lng = if (el.has("lon")) el.optDouble("lon") else el.optJSONObject("center")?.optDouble("lon") ?: continue

                val category = resolveOsmCategory(tags) ?: continue
                val name = tags.optString("name", "").ifBlank { category.displayName }
                val openingHoursRaw = tags.optString("opening_hours", null)
                val phone = tags.optString("phone", null) ?: tags.optString("contact:phone", null)

                val (distFromRoute, posAlongRoute) = calculateDistancesToPolyline(Pair(lat, lng), routeCoords)

                // ETA to this point along the route
                val progressFraction = if (routeLengthMeters > 0) (posAlongRoute / routeLengthMeters).coerceIn(0.0, 1.0) else 0.0
                val etaEpochMs = nowEpoch + (progressFraction * durationMinutes * 60 * 1000).toLong()

                val hoursStatus = OpeningHoursHelper.evaluate(openingHoursRaw, etaEpochMs)

                rawPlaces.add(
                    RoutePlace(
                        id = "${type}_${el.opt("id")}",
                        name = name,
                        category = category,
                        lat = lat,
                        lng = lng,
                        distanceFromRouteM = distFromRoute,
                        positionAlongRouteM = posAlongRoute,
                        openingHoursRaw = openingHoursRaw,
                        isOpenNow = hoursStatus.isOpenNow,
                        opensAt = hoursStatus.opensAt,
                        closesAt = hoursStatus.closesAt,
                        is24x7 = hoursStatus.is24x7,
                        isOpenWhenPassing = hoursStatus.isOpenWhenPassing,
                        phone = phone
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Overpass JSON: ${e.message}")
        }

        val hasLightingData = totalWaysCount > 0 && (litWaysCount > 0 || unlitWaysCount > 0)
        val litRatio = if (totalWaysCount > 0) litWaysCount.toFloat() / totalWaysCount.toFloat() else 0.0f
        val untaggedRatio = if (totalWaysCount > 0) untaggedWaysCount.toFloat() / totalWaysCount.toFloat() else 1.0f

        val sidewalkSummary = when {
            sidewalks.isNotEmpty() -> "Sidewalks tagged (${sidewalks.take(2).joinToString(", ")})"
            else -> "No sidewalk data tagged"
        }
        val speedSummary = when {
            maxspeeds.isNotEmpty() -> "Speed limit: ${maxspeeds.take(2).joinToString(", ")} km/h"
            else -> "Speed limit not tagged"
        }

        val (curvyCount, isGhat) = analyzeGeometryCurvature(routeCoords)
        val isOutsideCbe = routeCoords.any { pt ->
            pt.first < 10.8800 || pt.first > 11.1500 || pt.second < 76.8500 || pt.second > 77.1000
        }
        val isLongerThan25Km = routeLengthMeters > 25000.0

        return assembleCorridorMetrics(
            places = rawPlaces,
            routeCoords = routeCoords,
            routeLengthMeters = routeLengthMeters,
            litRatio = litRatio,
            untaggedRatio = untaggedRatio,
            hasLightingData = hasLightingData,
            totalWaysCount = totalWaysCount,
            litWaysCount = litWaysCount,
            unlitWaysCount = unlitWaysCount,
            untaggedWaysCount = untaggedWaysCount,
            isLitEstimated = false,
            primaryHighwayClasses = highwayClasses.toList(),
            sidewalkInfo = sidewalkSummary,
            maxSpeedInfo = speedSummary,
            surfaces = surfaces.toList(),
            curvySectionCount = curvyCount,
            isGhatSection = isGhat,
            isOutsideCoimbatoreBounds = isOutsideCbe,
            isLengthExceeding25Km = isLongerThan25Km,
            hasCorridorData = totalWaysCount > 0 || rawPlaces.isNotEmpty()
        )
    }

    private fun analyzeGeometryCurvature(coords: List<Pair<Double, Double>>): Pair<Int, Boolean> {
        if (coords.size < 4) return Pair(0, false)
        var sharpTurns = 0
        for (i in 1 until coords.size - 1) {
            val p0 = coords[i - 1]
            val p1 = coords[i]
            val p2 = coords[i + 1]
            val b1 = Math.toDegrees(Math.atan2(p1.second - p0.second, p1.first - p0.first))
            val b2 = Math.toDegrees(Math.atan2(p2.second - p1.second, p2.first - p1.first))
            var diff = Math.abs(b2 - b1)
            if (diff > 180.0) diff = 360.0 - diff
            if (diff >= 45.0) {
                sharpTurns++
            }
        }
        val isGhat = sharpTurns >= 8
        return Pair(sharpTurns, isGhat)
    }

    private suspend fun fetchGeoapifyPlacesFallback(
        routeCoords: List<Pair<Double, Double>>
    ): List<RoutePlace> {
        val apiKey = GeoapifyService.getApiKey()
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return emptyList()

        try {
            val simplified = simplifyToMaxPoints(routeCoords, maxPoints = 50)
            val coordsJson = JSONArray()
            for (p in simplified) {
                val pt = JSONArray()
                pt.put(p.second) // lon
                pt.put(p.first)  // lat
                coordsJson.put(pt)
            }

            val bodyJson = JSONObject().apply {
                put("categories", JSONArray().apply {
                    put("healthcare.hospital")
                    put("healthcare.pharmacy")
                    put("service.police")
                    put("service.vehicle.fuel")
                    put("catering.restaurant")
                    put("catering.cafe")
                    put("service.financial.atm")
                    put("commercial")
                })
                put("filter", JSONObject().apply {
                    put("type", "polyline")
                    put("geometry", JSONObject().apply {
                        put("type", "LineString")
                        put("coordinates", coordsJson)
                    })
                    put("buffer", 150)
                })
                put("limit", 40)
            }

            val request = Request.Builder()
                .url("https://api.geoapify.com/v2/places?apiKey=$apiKey")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val str = response.body?.string().orEmpty()
                val root = JSONObject(str)
                val features = root.optJSONArray("features") ?: JSONArray()
                val result = mutableListOf<RoutePlace>()

                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val geom = f.optJSONObject("geometry") ?: continue
                    val coords = geom.optJSONArray("coordinates") ?: continue
                    val lng = coords.optDouble(0)
                    val lat = coords.optDouble(1)

                    val props = f.optJSONObject("properties") ?: JSONObject()
                    val name = props.optString("name", "Local Service")
                    val catStr = props.optJSONArray("categories")?.optString(0, "") ?: ""
                    val category = resolveGeoapifyCategory(catStr)
                    val phone = props.optString("contact:phone", null)

                    val (dist, pos) = calculateDistancesToPolyline(Pair(lat, lng), routeCoords)

                    result.add(
                        RoutePlace(
                            id = "geo_$i",
                            name = name,
                            category = category,
                            lat = lat,
                            lng = lng,
                            distanceFromRouteM = dist,
                            positionAlongRouteM = pos,
                            openingHoursRaw = null,
                            isOpenNow = true,
                            isOpenWhenPassing = true,
                            phone = phone
                        )
                    )
                }
                return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geoapify fallback error: ${e.message}")
        }
        return emptyList()
    }

    private fun buildCorridorFromPlaces(
        places: List<RoutePlace>,
        routeCoords: List<Pair<Double, Double>>,
        routeLengthMeters: Double,
        durationMinutes: Int
    ): RouteCorridorData {
        val (curvyCount, isGhat) = analyzeGeometryCurvature(routeCoords)
        val isOutsideCbe = routeCoords.any { pt ->
            pt.first < 10.8800 || pt.first > 11.1500 || pt.second < 76.8500 || pt.second > 77.1000
        }
        val isLongerThan25Km = routeLengthMeters > 25000.0

        return assembleCorridorMetrics(
            places = places,
            routeCoords = routeCoords,
            routeLengthMeters = routeLengthMeters,
            litRatio = 0.0f,
            untaggedRatio = 1.0f,
            hasLightingData = false,
            totalWaysCount = 0,
            litWaysCount = 0,
            unlitWaysCount = 0,
            untaggedWaysCount = 0,
            isLitEstimated = false,
            primaryHighwayClasses = emptyList(),
            sidewalkInfo = "No sidewalk data tagged",
            maxSpeedInfo = "Speed limit not tagged",
            surfaces = emptyList(),
            curvySectionCount = curvyCount,
            isGhatSection = isGhat,
            isOutsideCoimbatoreBounds = isOutsideCbe,
            isLengthExceeding25Km = isLongerThan25Km,
            hasCorridorData = places.isNotEmpty()
        )
    }

    private fun assembleCorridorMetrics(
        places: List<RoutePlace>,
        routeCoords: List<Pair<Double, Double>>,
        routeLengthMeters: Double,
        litRatio: Float,
        untaggedRatio: Float = 1.0f,
        hasLightingData: Boolean = false,
        totalWaysCount: Int = 0,
        litWaysCount: Int = 0,
        unlitWaysCount: Int = 0,
        untaggedWaysCount: Int = 0,
        isLitEstimated: Boolean = false,
        primaryHighwayClasses: List<String> = emptyList(),
        sidewalkInfo: String = "",
        maxSpeedInfo: String = "",
        surfaces: List<String> = emptyList(),
        curvySectionCount: Int = 0,
        isGhatSection: Boolean = false,
        isOutsideCoimbatoreBounds: Boolean = false,
        isLengthExceeding25Km: Boolean = false,
        hasCorridorData: Boolean = false
    ): RouteCorridorData {
        val sortedPlaces = places.sortedBy { it.positionAlongRouteM }
        val openPlaces = sortedPlaces.filter { it.isOpenWhenPassing }
        val helpPoints = sortedPlaces.filter { it.category.isHelpPoint }

        // Calculate density of open businesses per 100 meters
        val hundredsOfMeters = (routeLengthMeters / 100.0).coerceAtLeast(1.0)
        val openBusinessDensity = (openPlaces.count { it.category == PlaceCategory.SHOP || it.category == PlaceCategory.FOOD || it.category == PlaceCategory.ATM }.toFloat() / hundredsOfMeters.toFloat())

        // Calculate longest stretch without open business or help point
        var maxGapMeters = 0.0
        var prevPos = 0.0
        val activeNodes = sortedPlaces.filter { it.isOpenWhenPassing || it.category.isHelpPoint }
        for (node in activeNodes) {
            val gap = node.positionAlongRouteM - prevPos
            if (gap > maxGapMeters) maxGapMeters = gap
            prevPos = node.positionAlongRouteM
        }
        val finalGap = routeLengthMeters - prevPos
        if (finalGap > maxGapMeters) maxGapMeters = finalGap

        // Help coverage score (0 - 100)
        val helpCoverageScore = if (helpPoints.isNotEmpty()) {
            val penalty = if (maxGapMeters > SafetyWeightConfig.MAX_UNHELPED_STRETCH_THRESHOLD_M) {
                ((maxGapMeters - SafetyWeightConfig.MAX_UNHELPED_STRETCH_THRESHOLD_M) / 25.0).coerceAtMost(35.0).toFloat()
            } else {
                0f
            }
            (95f - penalty).coerceIn(20f, 98f)
        } else {
            50f
        }

        // Build 200m strips for shop coverage strip UI
        val segmentLength = 200.0
        val numStrips = max(1, (routeLengthMeters / segmentLength).toInt())
        val strips = mutableListOf<CoverageSegment>()

        for (s in 0 until numStrips) {
            val startM = s * segmentLength
            val endM = if (s == numStrips - 1) routeLengthMeters else (s + 1) * segmentLength
            val placesInStrip = sortedPlaces.filter { it.positionAlongRouteM in startM..endM }
            val openInStrip = placesInStrip.count { it.isOpenWhenPassing }
            val hasHelp = placesInStrip.any { it.category.isHelpPoint }

            val status = when {
                hasHelp || openInStrip >= 2 -> CoverageStatus.HIGH
                openInStrip == 1 -> CoverageStatus.MEDIUM
                else -> CoverageStatus.LOW
            }

            strips.add(
                CoverageSegment(
                    index = s,
                    startMeters = startM,
                    endMeters = endM,
                    status = status,
                    openPlacesCount = openInStrip,
                    hasHelpPoint = hasHelp
                )
            )
        }

        return RouteCorridorData(
            places = sortedPlaces,
            litRatio = litRatio,
            untaggedRatio = untaggedRatio,
            hasLightingData = hasLightingData,
            totalWaysCount = totalWaysCount,
            litWaysCount = litWaysCount,
            unlitWaysCount = unlitWaysCount,
            untaggedWaysCount = untaggedWaysCount,
            isLitEstimated = isLitEstimated,
            openPlacesCount = openPlaces.size,
            totalPlacesCount = sortedPlaces.size,
            openShopsDensityPer100m = openBusinessDensity,
            longestStretchWithoutHelpM = maxGapMeters,
            helpCoverageScore = helpCoverageScore,
            coverageSegments = strips,
            primaryHighwayClasses = primaryHighwayClasses,
            sidewalkInfo = sidewalkInfo,
            maxSpeedInfo = maxSpeedInfo,
            surfaces = surfaces,
            curvySectionCount = curvySectionCount,
            isGhatSection = isGhatSection,
            isOutsideCoimbatoreBounds = isOutsideCoimbatoreBounds,
            isLengthExceeding25Km = isLengthExceeding25Km,
            hasCorridorData = hasCorridorData
        )
    }

    private fun resolveOsmCategory(tags: JSONObject): PlaceCategory? {
        val amenity = tags.optString("amenity", "").lowercase()
        val healthcare = tags.optString("healthcare", "").lowercase()
        val shop = tags.optString("shop", "").lowercase()
        val emergency = tags.optString("emergency", "").lowercase()

        return when {
            amenity in listOf("hospital", "clinic", "doctors") || healthcare in listOf("hospital", "clinic", "centre") -> PlaceCategory.HOSPITAL
            amenity == "pharmacy" || healthcare == "pharmacy" || shop == "chemist" -> PlaceCategory.PHARMACY
            amenity == "police" -> PlaceCategory.POLICE
            amenity == "fuel" -> PlaceCategory.FUEL
            amenity in listOf("restaurant", "cafe", "fast_food", "food_court", "bar") -> PlaceCategory.FOOD
            amenity in listOf("atm", "bank") -> PlaceCategory.ATM
            shop.isNotBlank() -> PlaceCategory.SHOP
            amenity == "fire_station" || emergency.isNotBlank() -> PlaceCategory.OTHER_HELP
            else -> null
        }
    }

    private fun resolveGeoapifyCategory(cat: String): PlaceCategory {
        val lower = cat.lowercase()
        return when {
            lower.contains("hospital") || lower.contains("clinic") -> PlaceCategory.HOSPITAL
            lower.contains("pharmacy") -> PlaceCategory.PHARMACY
            lower.contains("police") -> PlaceCategory.POLICE
            lower.contains("fuel") -> PlaceCategory.FUEL
            lower.contains("restaurant") || lower.contains("cafe") || lower.contains("food") -> PlaceCategory.FOOD
            lower.contains("atm") || lower.contains("bank") -> PlaceCategory.ATM
            lower.contains("shop") || lower.contains("commercial") -> PlaceCategory.SHOP
            else -> PlaceCategory.OTHER_HELP
        }
    }

    // --- Geometry & Spatial Helpers ---

    private fun computeTotalPolylineLengthM(coords: List<Pair<Double, Double>>): Double {
        var sum = 0.0
        for (i in 0 until coords.size - 1) {
            sum += haversineDistanceM(coords[i], coords[i + 1])
        }
        return sum
    }

    private fun calculateDistancesToPolyline(
        point: Pair<Double, Double>,
        routeCoords: List<Pair<Double, Double>>
    ): Pair<Double, Double> {
        var minDistance = Double.MAX_VALUE
        var positionAlongRoute = 0.0
        var accumulatedLength = 0.0

        for (i in 0 until routeCoords.size - 1) {
            val p1 = routeCoords[i]
            val p2 = routeCoords[i + 1]
            val segLength = haversineDistanceM(p1, p2)

            val d = pointToSegmentDistanceM(point, p1, p2)
            if (d < minDistance) {
                minDistance = d
                // Estimate projection fraction along segment
                val t = segmentProjectionT(point, p1, p2)
                positionAlongRoute = accumulatedLength + (t * segLength)
            }
            accumulatedLength += segLength
        }
        return Pair(minDistance, positionAlongRoute)
    }

    private fun segmentProjectionT(pt: Pair<Double, Double>, p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val dx = p2.first - p1.first
        val dy = p2.second - p1.second
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return 0.0
        val t = ((pt.first - p1.first) * dx + (pt.second - p1.second) * dy) / len2
        return t.coerceIn(0.0, 1.0)
    }

    private fun pointToSegmentDistanceM(pt: Pair<Double, Double>, p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val t = segmentProjectionT(pt, p1, p2)
        val projLat = p1.first + t * (p2.first - p1.first)
        val projLng = p1.second + t * (p2.second - p1.second)
        return haversineDistanceM(pt, Pair(projLat, projLng))
    }

    private fun haversineDistanceM(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(p2.first - p1.first)
        val dLng = Math.toRadians(p2.second - p1.second)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.first)) * cos(Math.toRadians(p2.first)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun simplifyToMaxPoints(points: List<Pair<Double, Double>>, maxPoints: Int = 85): List<Pair<Double, Double>> {
        if (points.size <= maxPoints) return points
        var epsilon = 15.0
        var simplified = douglasPeucker(points, epsilon)
        var iterations = 0
        while (simplified.size > maxPoints && iterations < 8) {
            epsilon *= 1.8
            simplified = douglasPeucker(points, epsilon)
            iterations++
        }
        return simplified
    }

    private fun douglasPeucker(points: List<Pair<Double, Double>>, epsilonM: Double): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        var dmax = 0.0
        var index = 0
        val p1 = points.first()
        val p2 = points.last()

        for (i in 1 until points.size - 1) {
            val d = pointToSegmentDistanceM(points[i], p1, p2)
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilonM) {
            val rec1 = douglasPeucker(points.subList(0, index + 1), epsilonM)
            val rec2 = douglasPeucker(points.subList(index, points.size), epsilonM)
            rec1.dropLast(1) + rec2
        } else {
            listOf(p1, p2)
        }
    }

    private fun hashCoordinates(coords: List<Pair<Double, Double>>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val stride = max(1, coords.size / 20)
        val sb = StringBuilder()
        for (i in coords.indices step stride) {
            val pt = coords[i]
            sb.append("${(pt.first * 1e4).toInt()},${(pt.second * 1e4).toInt()};")
        }
        val hash = digest.digest(sb.toString().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
