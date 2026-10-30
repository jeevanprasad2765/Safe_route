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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
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
        private const val LONG_ROUTE_THRESHOLD_M = 25_000.0
        private const val CHUNK_TARGET_M = 10_000.0
        private const val MAX_CONCURRENT_CHUNKS = 3
        private const val CHUNK_TIMEOUT_MS = 10_000L
        private const val TOTAL_CHUNKED_TIMEOUT_MS = 15_000L
        private const val MAX_PLACES_PER_CATEGORY = 40
        private const val MAX_QUERY_POINTS = 60

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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS) // explicit 6 s enrichment budget
        .build()

    /**
     * Retrieves places and infrastructure attributes along the provided route polyline.
     *
     * Short routes use one Overpass corridor query. Routes longer than 25 km are split into
     * ~10 km chunks, fetched with at most 3 concurrent requests and a 10 s timeout each, with a
     * hard 15 s budget for the whole enrichment. Whatever returns is merged; failed chunks are
     * reported as partial data instead of leaving the UI spinning forever.
     */
    suspend fun fetchCorridorData(
        routeCoordinates: List<Pair<Double, Double>>,
        durationMinutes: Int
    ): RouteCorridorData = withContext(Dispatchers.IO) {
        if (routeCoordinates.isEmpty()) return@withContext RouteCorridorData()

        val routeLengthMeters = computeTotalPolylineLengthM(routeCoordinates)
        if (routeLengthMeters > LONG_ROUTE_THRESHOLD_M) {
            fetchChunkedCorridor(routeCoordinates, routeLengthMeters, durationMinutes)
        } else {
            fetchSingleCorridor(routeCoordinates, routeLengthMeters, durationMinutes)
        }
    }

    private suspend fun fetchSingleCorridor(
        routeCoordinates: List<Pair<Double, Double>>,
        routeLengthMeters: Double,
        durationMinutes: Int
    ): RouteCorridorData {
        val geometryHash = hashCoordinates(routeCoordinates)

        // 1. Check Room Database Cache (valid 24h)
        loadCachedJson(geometryHash)?.let { cached ->
            Log.d(TAG, "Cache HIT in Room for route hash $geometryHash")
            return parseOverpassJson(cached, routeCoordinates, routeLengthMeters, durationMinutes)
        }

        // 2. Query Overpass (simplified polyline, capped per-category results)
        val overpassJson = queryOverpass(routeCoordinates)
        if (!overpassJson.isNullOrBlank()) {
            saveCachedJson(geometryHash, overpassJson)
            return parseOverpassJson(overpassJson, routeCoordinates, routeLengthMeters, durationMinutes)
        }

        // 3. Fallback: Query Geoapify Places API along the polyline corridor, or Coimbatore landmarks
        Log.i(TAG, "Overpass unavailable. Falling back to Geoapify / Coimbatore landmark Places...")
        val fallbackPlaces = fetchGeoapifyPlacesFallback(routeCoordinates).ifEmpty {
            fetchLandmarkPlacesFallback(routeCoordinates)
        }
        return buildCorridorFromPlaces(
            capPlacesByCategory(fallbackPlaces),
            routeCoordinates,
            routeLengthMeters,
            durationMinutes
        )
    }

    /**
     * Long-route enrichment: ~10 km chunks, max 3 in flight, 10 s per chunk, 15 s overall.
     * Successful chunks are merged; failed chunks only mark their own stretch as unavailable.
     */
    private suspend fun fetchChunkedCorridor(
        routeCoordinates: List<Pair<Double, Double>>,
        routeLengthMeters: Double,
        durationMinutes: Int
    ): RouteCorridorData = coroutineScope {
        val chunks = splitRouteIntoChunks(routeCoordinates, CHUNK_TARGET_M)
        if (chunks.isEmpty()) {
            return@coroutineScope RouteCorridorData()
        }

        val semaphore = Semaphore(MAX_CONCURRENT_CHUNKS)
        val completed = ConcurrentHashMap<Int, RouteCorridorData?>()
        val startedAt = System.currentTimeMillis()
        Log.d(
            TAG,
            "LONG_ROUTE_ENRICH chunks=${chunks.size} lengthKm=${"%.1f".format(routeLengthMeters / 1000)}"
        )

        // Hard overall budget. Completed chunks are kept even when the timeout cancels the rest.
        withTimeoutOrNull(TOTAL_CHUNKED_TIMEOUT_MS) {
            chunks.map { chunk ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val elapsed = System.currentTimeMillis() - startedAt
                        if (elapsed >= TOTAL_CHUNKED_TIMEOUT_MS - 500L) {
                            completed[chunk.index] = null
                            return@withPermit
                        }
                        val result = withTimeoutOrNull(CHUNK_TIMEOUT_MS) {
                            fetchChunkCorridor(
                                chunk.coords,
                                routeCoordinates,
                                routeLengthMeters,
                                durationMinutes
                            )
                        }
                        completed[chunk.index] = result
                        if (result == null) {
                            Log.w(TAG, "Chunk ${chunk.index} failed or timed out")
                        }
                    }
                }
            }.awaitAll()
        }

        val failedChunks = chunks.filter { completed[it.index] == null }
        val successful = chunks.mapNotNull { completed[it.index] }
        val unavailableDistanceM = failedChunks.sumOf { (it.endM - it.startM).coerceAtLeast(0.0) }
        Log.d(
            TAG,
            "LONG_ROUTE_ENRICH_DONE elapsedMs=${System.currentTimeMillis() - startedAt} " +
                "ok=${successful.size}/${chunks.size} failedDistanceKm=${"%.1f".format(unavailableDistanceM / 1000)}"
        )

        val mergedPlaces = capPlacesByCategory(successful.flatMap { it.places }.distinctBy { it.id })
        val totalWays = successful.sumOf { it.totalWaysCount }
        val litWays = successful.sumOf { it.litWaysCount }
        val unlitWays = successful.sumOf { it.unlitWaysCount }
        val untaggedWays = successful.sumOf { it.untaggedWaysCount }

        val merged = assembleCorridorMetrics(
            places = mergedPlaces,
            routeCoords = routeCoordinates,
            routeLengthMeters = routeLengthMeters,
            litRatio = if (totalWays > 0) litWays.toFloat() / totalWays.toFloat() else 0.0f,
            untaggedRatio = if (totalWays > 0) untaggedWays.toFloat() / totalWays.toFloat() else 1.0f,
            hasLightingData = totalWays > 0 && (litWays > 0 || unlitWays > 0),
            totalWaysCount = totalWays,
            litWaysCount = litWays,
            unlitWaysCount = unlitWays,
            untaggedWaysCount = untaggedWays,
            isLitEstimated = false,
            primaryHighwayClasses = successful.flatMap { it.primaryHighwayClasses }.distinct(),
            sidewalkInfo = combineFact(
                successful.map { it.sidewalkInfo },
                "No sidewalk data tagged"
            ),
            maxSpeedInfo = combineFact(
                successful.map { it.maxSpeedInfo },
                "Speed limit not tagged"
            ),
            surfaces = successful.flatMap { it.surfaces }.distinct(),
            curvySectionCount = successful.maxOfOrNull { it.curvySectionCount } ?: 0,
            isGhatSection = successful.any { it.isGhatSection },
            isOutsideCoimbatoreBounds = successful.any { it.isOutsideCoimbatoreBounds },
            isLengthExceeding25Km = true,
            hasCorridorData = successful.any { it.hasCorridorData }
        )

        val failedRanges = failedChunks.map { it.startM to it.endM }
        merged.copy(
            failedChunkCount = failedChunks.size,
            totalChunkCount = chunks.size,
            isPartialData = failedChunks.isNotEmpty(),
            unavailableDistanceM = unavailableDistanceM,
            coverageSegments = markUnavailableSegments(merged.coverageSegments, failedRanges)
        )
    }

    /** Fetches one ~10 km chunk and parses it against the full route so positions stay global. */
    private suspend fun fetchChunkCorridor(
        chunkCoords: List<Pair<Double, Double>>,
        fullRouteCoords: List<Pair<Double, Double>>,
        fullRouteLengthMeters: Double,
        durationMinutes: Int
    ): RouteCorridorData? {
        if (chunkCoords.size < 2) return null
        val chunkHash = hashCoordinates(chunkCoords)

        loadCachedJson(chunkHash)?.let { cached ->
            return parseOverpassJson(cached, fullRouteCoords, fullRouteLengthMeters, durationMinutes)
        }

        val json = queryOverpass(chunkCoords)
        if (!json.isNullOrBlank()) {
            saveCachedJson(chunkHash, json)
            return parseOverpassJson(json, fullRouteCoords, fullRouteLengthMeters, durationMinutes)
        }

        // Cheap landmark fallback (no extra per-chunk network fan-out). Empty => chunk failed.
        val landmarks = fetchLandmarkPlacesFallback(chunkCoords)
            .map { place ->
                val (dist, pos) = calculateDistancesToPolyline(
                    Pair(place.lat, place.lng),
                    fullRouteCoords
                )
                place.copy(distanceFromRouteM = dist, positionAlongRouteM = pos)
            }
        if (landmarks.isEmpty()) return null
        return buildCorridorFromPlaces(
            capPlacesByCategory(landmarks),
            fullRouteCoords,
            fullRouteLengthMeters,
            durationMinutes
        )
    }

    internal data class CorridorChunk(
        val index: Int,
        val startM: Double,
        val endM: Double,
        val coords: List<Pair<Double, Double>>
    )

    internal fun splitRouteIntoChunks(
        coords: List<Pair<Double, Double>>,
        targetMeters: Double
    ): List<CorridorChunk> {
        if (coords.size < 2) return emptyList()
        val chunks = mutableListOf<CorridorChunk>()
        var startIndex = 0
        var accumulatedM = 0.0
        var chunkIndex = 0

        for (i in 0 until coords.size - 1) {
            accumulatedM += haversineDistanceM(coords[i], coords[i + 1])
            val isLastSegment = i == coords.size - 2
            if (accumulatedM >= targetMeters || isLastSegment) {
                val endIndex = i + 1
                val startM = distanceAlongPolylineM(coords, startIndex)
                chunks.add(
                    CorridorChunk(
                        index = chunkIndex,
                        startM = startM,
                        endM = startM + accumulatedM,
                        coords = coords.subList(startIndex, endIndex + 1).toList()
                    )
                )
                chunkIndex += 1
                startIndex = endIndex
                accumulatedM = 0.0
            }
        }
        return chunks
    }

    private fun distanceAlongPolylineM(
        coords: List<Pair<Double, Double>>,
        endIndex: Int
    ): Double {
        if (endIndex <= 0) return 0.0
        var sum = 0.0
        val safeEnd = endIndex.coerceAtMost(coords.size - 1)
        for (i in 0 until safeEnd) {
            sum += haversineDistanceM(coords[i], coords[i + 1])
        }
        return sum
    }

    private fun capPlacesByCategory(
        places: List<RoutePlace>,
        perCategory: Int = MAX_PLACES_PER_CATEGORY
    ): List<RoutePlace> {
        if (places.isEmpty()) return places
        return places
            .groupBy { it.category }
            .flatMap { (_, categoryPlaces) ->
                categoryPlaces.sortedBy { it.positionAlongRouteM }.take(perCategory)
            }
            .sortedBy { it.positionAlongRouteM }
    }

    private fun markUnavailableSegments(
        segments: List<CoverageSegment>,
        failedRanges: List<Pair<Double, Double>>
    ): List<CoverageSegment> {
        if (segments.isEmpty() || failedRanges.isEmpty()) return segments
        return segments.map { segment ->
            val midpoint = (segment.startMeters + segment.endMeters) / 2.0
            val overlapsFailure = failedRanges.any { (start, end) -> midpoint in start..end }
            if (overlapsFailure) {
                segment.copy(
                    status = CoverageStatus.UNAVAILABLE,
                    openPlacesCount = 0,
                    hasHelpPoint = false
                )
            } else {
                segment
            }
        }
    }

    private fun combineFact(values: List<String>, fallback: String): String {
        val meaningful = values.filter { it.isNotBlank() && !it.contains("No ", true) && !it.contains("not tagged", true) }
        return meaningful.distinct().take(2).joinToString(" • ").ifBlank { fallback }
    }

    private suspend fun queryOverpass(routeCoordinates: List<Pair<Double, Double>>): String? {
        val simplifiedPoints = simplifyToMaxPoints(routeCoordinates, maxPoints = MAX_QUERY_POINTS)
        val polylineAroundParam = simplifiedPoints.joinToString(",") { "${it.first},${it.second}" }

        val overpassQuery = """
            [out:json][timeout:8];
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

                val response = executeRequest(request)
                if (response != null) {
                    response.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (!body.isNullOrBlank() && body.contains("\"elements\"")) {
                                Log.d(TAG, "Successfully fetched from Overpass mirror: $endpoint")
                                return body
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass mirror $endpoint failed: ${e.message}")
            }
        }
        return null
    }

    private suspend fun loadCachedJson(geometryHash: String): String? {
        try {
            val cached = db.routeOverpassCacheDao().getCache(geometryHash)
            if (cached != null &&
                (System.currentTimeMillis() - cached.cachedAtEpochMs) < CACHE_VALIDITY_MS
            ) {
                return cached.rawJson
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading Room cache: ${e.message}")
        }
        return null
    }

    private suspend fun saveCachedJson(geometryHash: String, json: String) {
        try {
            db.routeOverpassCacheDao().insertCache(
                RouteOverpassCacheEntity(
                    routeGeometryHash = geometryHash,
                    rawJson = json,
                    cachedAtEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error caching to Room: ${e.message}")
        }
    }

    /**
     * OkHttp async bridge so coroutine timeouts (chunk 10 s / total 15 s) actually cancel the
     * HTTP call instead of waiting for a blocking execute() to finish.
     */
    private suspend fun executeRequest(request: Request): Response? =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
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
            places = capPlacesByCategory(rawPlaces),
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

            val response = executeRequest(request) ?: return emptyList()
            response.use { resp ->
                if (resp.isSuccessful) {
                val str = resp.body?.string().orEmpty()
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
            places = capPlacesByCategory(places),
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
