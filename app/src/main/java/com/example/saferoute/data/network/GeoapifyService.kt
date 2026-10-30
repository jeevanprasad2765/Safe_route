package com.example.saferoute.data.network

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteLeg
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.RouteSegment
import com.example.saferoute.data.model.SafetyTier
import com.example.saferoute.data.model.TimeOfDay
import com.example.saferoute.data.model.TurnInstruction
import com.example.saferoute.data.model.Waypoint
import com.example.saferoute.safety.SafetyCalculator
import com.example.saferoute.util.LanguageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.maps.Style
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ── Search & Geocode Models ──

data class SearchResult(
    val displayName: String,
    val shortName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "place",
    val placeId: String? = null
)

/**
 * Service for Geoapify APIs:
 * - Routing API (generating balanced, short, and less_maneuvers routes with deduplication)
 * - Address Autocomplete (biased to Coimbatore, TN)
 * - Geocoding & Reverse Geocoding
 * - MapLibre OSM-Bright style configuration
 */
object GeoapifyService {

    private const val TAG = "GeoapifyService"
    private const val BASE_URL = "https://api.geoapify.com"
    private const val MAPS_STYLE_URL = "https://maps.geoapify.com/v1/styles/osm-bright/style.json"

    // Coimbatore Coordinates for Location Bias
    private const val COIMBATORE_LAT = 11.0168
    private const val COIMBATORE_LNG = 76.9558

    val COIMBATORE_LANDMARKS = listOf(
        SearchResult("TIDEL Park, Coimbatore", "TIDEL Park", 11.0280, 77.0050, "business", "cbe_tidel"),
        SearchResult("Peelamedu, Coimbatore", "Peelamedu", 11.0250, 77.0080, "suburb", "cbe_peelamedu"),
        SearchResult("PSG Tech, Peelamedu, Coimbatore", "PSG Tech", 11.0245, 77.0028, "education", "cbe_psg"),
        SearchResult("Gandhipuram, Coimbatore", "Gandhipuram", 11.0168, 76.9670, "commercial", "cbe_gandhipuram"),
        SearchResult("RS Puram, Coimbatore", "RS Puram", 11.0089, 76.9507, "suburb", "cbe_rspuram"),
        SearchResult("Coimbatore Junction Railway Station", "Railway Station", 10.9990, 76.9630, "station", "cbe_junction"),
        SearchResult("Sitra, Coimbatore", "Sitra", 11.0330, 77.0380, "suburb", "cbe_sitra"),
        SearchResult("Coimbatore International Airport", "Airport", 11.0300, 77.0434, "airport", "cbe_airport"),
        SearchResult("Singanallur, Coimbatore", "Singanallur", 10.9970, 77.0180, "suburb", "cbe_singanallur"),
        SearchResult("Saravanampatti, Coimbatore", "Saravanampatti", 11.0780, 76.9980, "suburb", "cbe_saravanampatti"),
        SearchResult("Town Hall, Coimbatore", "Town Hall", 10.9925, 76.9610, "heritage", "cbe_townhall"),
        SearchResult("Ukkadam, Coimbatore", "Ukkadam", 10.9880, 76.9580, "transport", "cbe_ukkadam"),
        SearchResult("Hope College, Coimbatore", "Hope College", 11.0270, 77.0200, "landmark", "cbe_hopecollege"),
        SearchResult("Lakshmi Mills, Coimbatore", "Lakshmi Mills", 11.0185, 76.9820, "landmark", "cbe_lakshmi"),
        SearchResult("Nava India, Coimbatore", "Nava India", 11.0210, 76.9910, "education", "cbe_navaindia"),
        SearchResult("DB Road, RS Puram, Coimbatore", "DB Road", 11.0110, 76.9510, "shopping", "cbe_dbroad"),
        SearchResult("Brookefields Mall, Coimbatore", "Brookefields Mall", 11.0135, 76.9575, "mall", "cbe_brookefields"),
        SearchResult("Fun Republic Mall, Peelamedu, Coimbatore", "Fun Republic Mall", 11.0245, 77.0025, "mall", "cbe_funrepublic"),
        SearchResult("Prozone Mall, Saravanampatti, Coimbatore", "Prozone Mall", 11.0545, 76.9920, "mall", "cbe_prozone"),
        SearchResult("Eachanari, Coimbatore", "Eachanari", 10.9320, 76.9720, "suburb", "cbe_eachanari"),
        SearchResult("Vadavalli, Coimbatore", "Vadavalli", 11.0230, 76.9020, "suburb", "cbe_vadavalli"),
        SearchResult("Marudhamalai, Coimbatore", "Marudhamalai", 11.0450, 76.8520, "temple", "cbe_marudhamalai"),
        SearchResult("Coimbatore, Tamil Nadu", "Coimbatore", 11.0168, 76.9558, "city", "cbe_city")
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-memory caches to prevent redundant network requests
    private val autocompleteCache = mutableMapOf<String, List<SearchResult>>()
    private val geocodeCache = mutableMapOf<String, SearchResult?>()
    private val reverseGeocodeCache = mutableMapOf<String, String>()
    private val routeCache = mutableMapOf<String, List<RouteOption>>()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    fun getApiKey(): String {
        return try {
            BuildConfig.GEOAPIFY_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    fun getOlaMapsApiKey(): String {
        return try {
            BuildConfig.OLA_MAPS_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    fun isOlaMapsConfigured(): Boolean {
        val key = getOlaMapsApiKey()
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    fun isGeoapifyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && !key.startsWith("YOUR_")
    }

    fun isApiKeyConfigured(): Boolean {
        return isOlaMapsConfigured() || isGeoapifyConfigured()
    }

    fun getStyleUrl(): String {
        if (isOlaMapsConfigured()) {
            return "https://api.olamaps.io/tiles/vector/v1/styles/default-light-standard/style.json?api_key=${getOlaMapsApiKey()}"
        }
        val key = getApiKey()
        return if (isGeoapifyConfigured()) {
            "$MAPS_STYLE_URL?apiKey=$key"
        } else {
            "asset://map_style_dark.json"
        }
    }

    fun getLocalStyleBuilder(context: Context): Style.Builder {
        val json = try {
            context.assets.open("map_style_dark.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            """{"version":8,"name":"CartoDark","sources":{"carto-dark":{"type":"raster","tiles":["https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png","https://b.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"carto-dark-layer","type":"raster","source":"carto-dark","minzoom":0,"maxzoom":20}]}"""
        }
        return Style.Builder().fromJson(json)
    }

    fun getStyleBuilder(context: Context): Style.Builder {
        if (isOlaMapsConfigured()) {
            return Style.Builder().fromUri(getStyleUrl())
        }
        return if (isGeoapifyConfigured()) {
            Style.Builder().fromUri(getStyleUrl())
        } else {
            getLocalStyleBuilder(context)
        }
    }

    fun clearError() {
        _lastErrorMessage.value = null
    }

    /**
     * Autocomplete suggestions for starting point and destination fields, biased to Coimbatore.
     */
    suspend fun searchPlaces(
        query: String,
        userLocation: Pair<Double, Double>? = null
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()

        val cacheKey = q.lowercase()
        synchronized(autocompleteCache) {
            autocompleteCache[cacheKey]?.let { return@withContext it }
        }

        if (isOlaMapsConfigured()) {
            val olaKey = getOlaMapsApiKey()
            val encodedQuery = URLEncoder.encode(q, "UTF-8")
            val url = "https://api.olamaps.io/places/v1/autocomplete?input=$encodedQuery&api_key=$olaKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val predictions = root.optJSONArray("predictions") ?: JSONArray()
                    val results = mutableListOf<SearchResult>()
                    for (i in 0 until predictions.length()) {
                        val item = predictions.getJSONObject(i)
                        val desc = item.optString("description", "")
                        val struct = item.optJSONObject("structured_formatting")
                        val mainText = struct?.optString("main_text", desc)?.ifBlank { desc } ?: desc
                        val geom = item.optJSONObject("geometry")
                        val loc = geom?.optJSONObject("location")
                        val lat = loc?.optDouble("lat") ?: 0.0
                        val lng = loc?.optDouble("lng") ?: 0.0
                        val placeId = item.optString("place_id", "ola_$i")
                        if (lat != 0.0 && lng != 0.0) {
                            results.add(SearchResult(desc, mainText, lat, lng, "place", placeId))
                        }
                    }
                    if (results.isNotEmpty()) {
                        synchronized(autocompleteCache) {
                            autocompleteCache[cacheKey] = results
                        }
                        clearError()
                        return@withContext results
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ola autocomplete query failed: ${e.message}")
            }
        }

        if (!isGeoapifyConfigured()) {
            val fallbackMatches = COIMBATORE_LANDMARKS.filter {
                it.displayName.contains(q, ignoreCase = true) || it.shortName.contains(q, ignoreCase = true)
            }.ifEmpty {
                listOf(SearchResult("$q, Coimbatore", q, COIMBATORE_LAT, COIMBATORE_LNG, "place", "cbe_custom"))
            }
            return@withContext fallbackMatches
        }

        val biasLat = userLocation?.first ?: COIMBATORE_LAT
        val biasLng = userLocation?.second ?: COIMBATORE_LNG
        val apiKey = getApiKey()

        val encodedQuery = URLEncoder.encode(q, "UTF-8")
        val url = "$BASE_URL/v1/geocode/autocomplete?text=$encodedQuery" +
                "&bias=proximity:$biasLng,$biasLat" +
                "&filter=countrycode:in" +
                "&limit=8" +
                "&apiKey=$apiKey"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                handleHttpError(response.code, response.message)
                return@withContext emptyList()
            }

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: JSONArray()
            val results = mutableListOf<SearchResult>()

            for (i in 0 until features.length()) {
                val item = features.getJSONObject(i)
                val props = item.optJSONObject("properties") ?: continue
                val placeId = props.optString("place_id", "geo_$i")
                val formatted = props.optString("formatted", "").ifBlank { props.optString("address_line1", q) }
                val mainText = props.optString("address_line1", formatted)

                val geom = item.optJSONObject("geometry")
                val coords = geom?.optJSONArray("coordinates")
                val lng = coords?.optDouble(0) ?: 0.0
                val lat = coords?.optDouble(1) ?: 0.0

                results.add(
                    SearchResult(
                        displayName = formatted,
                        shortName = mainText,
                        latitude = lat,
                        longitude = lng,
                        type = props.optString("category", "place"),
                        placeId = placeId
                    )
                )
            }

            synchronized(autocompleteCache) {
                autocompleteCache[cacheKey] = results
            }
            clearError()
            return@withContext results
        } catch (e: Exception) {
            handleNetworkException(e)
            return@withContext emptyList()
        }
    }

    /**
     * Resolves a place name or address query into exact coordinates.
     */
    suspend fun geocodePlace(placeName: String): SearchResult? = withContext(Dispatchers.IO) {
        val q = placeName.trim()
        if (q.isBlank()) return@withContext null

        val cacheKey = q.lowercase()
        synchronized(geocodeCache) {
            if (geocodeCache.containsKey(cacheKey)) {
                return@withContext geocodeCache[cacheKey]
            }
        }

        val landmark = COIMBATORE_LANDMARKS.find {
            it.shortName.equals(q, ignoreCase = true) ||
            it.displayName.contains(q, ignoreCase = true) ||
            q.contains(it.shortName, ignoreCase = true)
        }
        if (landmark != null) {
            synchronized(geocodeCache) { geocodeCache[cacheKey] = landmark }
            return@withContext landmark
        }

        if (isOlaMapsConfigured()) {
            val olaKey = getOlaMapsApiKey()
            val encodedQuery = URLEncoder.encode("$q, Coimbatore", "UTF-8")
            val url = "https://api.olamaps.io/places/v1/geocode?address=$encodedQuery&api_key=$olaKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val results = root.optJSONArray("geocodingResults") ?: JSONArray()
                    if (results.length() > 0) {
                        val item = results.getJSONObject(0)
                        val formatted = item.optString("formatted_address", q)
                        val name = item.optString("name", q)
                        val geom = item.optJSONObject("geometry")
                        val loc = geom?.optJSONObject("location")
                        val lat = loc?.optDouble("lat")
                        val lng = loc?.optDouble("lng")
                        val placeId = item.optString("place_id")
                        if (lat != null && lng != null) {
                            val result = SearchResult(formatted, name, lat, lng, "place", placeId)
                            synchronized(geocodeCache) { geocodeCache[cacheKey] = result }
                            clearError()
                            return@withContext result
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ola geocode failed: ${e.message}")
            }
        }

        if (!isGeoapifyConfigured()) {
            val custom = SearchResult("$q, Coimbatore", q, COIMBATORE_LAT, COIMBATORE_LNG, "place", "cbe_custom")
            return@withContext custom
        }

        val apiKey = getApiKey()
        val encodedQuery = URLEncoder.encode(q, "UTF-8")
        val url = "$BASE_URL/v1/geocode/search?text=$encodedQuery" +
                "&bias=proximity:$COIMBATORE_LNG,$COIMBATORE_LAT" +
                "&filter=countrycode:in" +
                "&limit=1" +
                "&apiKey=$apiKey"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                handleHttpError(response.code, response.message)
                return@withContext null
            }

            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: JSONArray()
            if (features.length() == 0) {
                _lastErrorMessage.value = LanguageManager.get("could_not_find_place")
                return@withContext null
            }

            val feat = features.getJSONObject(0)
            val props = feat.optJSONObject("properties")
            val geom = feat.optJSONObject("geometry")
            val coords = geom?.optJSONArray("coordinates")
            val lng = coords?.optDouble(0) ?: return@withContext null
            val lat = coords.optDouble(1)

            val formatted = props?.optString("formatted", q) ?: q
            val mainText = props?.optString("address_line1", q) ?: q

            val result = SearchResult(
                displayName = formatted,
                shortName = mainText,
                latitude = lat,
                longitude = lng,
                type = props?.optString("category", "place") ?: "place",
                placeId = props?.optString("place_id")
            )

            synchronized(geocodeCache) { geocodeCache[cacheKey] = result }
            clearError()
            return@withContext result
        } catch (e: Exception) {
            handleNetworkException(e)
            return@withContext null
        }
    }

    /**
     * Reverse geocodes live coordinates into a friendly locality name for "My Location (<locality>)".
     */
    suspend fun reverseGeocode(lat: Double, lng: Double, context: Context): String = withContext(Dispatchers.IO) {
        val cacheKey = "${(lat * 1000).toInt()},${(lng * 1000).toInt()}"
        synchronized(reverseGeocodeCache) {
            reverseGeocodeCache[cacheKey]?.let { return@withContext it }
        }

        if (isOlaMapsConfigured()) {
            val olaKey = getOlaMapsApiKey()
            val url = "https://api.olamaps.io/places/v1/reverse-geocode?latlng=$lat,$lng&api_key=$olaKey"
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val results = root.optJSONArray("results") ?: JSONArray()
                    if (results.length() > 0) {
                        val item = results.getJSONObject(0)
                        val components = item.optJSONArray("address_components")
                        var localityName: String? = null
                        if (components != null) {
                            for (i in 0 until components.length()) {
                                val c = components.getJSONObject(i)
                                val types = c.optJSONArray("types") ?: JSONArray()
                                val typeList = (0 until types.length()).map { types.getString(it) }
                                if (typeList.contains("sublocality")) {
                                    localityName = c.optString("long_name")
                                    break
                                } else if (typeList.contains("locality") && localityName == null) {
                                    localityName = c.optString("long_name")
                                }
                            }
                        }
                        val finalLocality = localityName ?: item.optString("name", "Coimbatore")
                        synchronized(reverseGeocodeCache) { reverseGeocodeCache[cacheKey] = finalLocality }
                        return@withContext finalLocality
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ola reverse geocode failed: ${e.message}")
            }
        }

        if (!isGeoapifyConfigured()) {
            return@withContext "Coimbatore"
        }

        val apiKey = getApiKey()
        val url = "$BASE_URL/v1/geocode/reverse?lat=$lat&lon=$lng&apiKey=$apiKey"

        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val root = JSONObject(body)
                val features = root.optJSONArray("features") ?: JSONArray()
                if (features.length() > 0) {
                    val props = features.getJSONObject(0).optJSONObject("properties")
                    val locality = props?.optString("suburb", "")?.ifBlank {
                        props.optString("district", "")
                    }?.ifBlank {
                        props.optString("city", "Coimbatore")
                    } ?: "Coimbatore"

                    synchronized(reverseGeocodeCache) { reverseGeocodeCache[cacheKey] = locality }
                    return@withContext locality
                }
            }
        } catch (_: Exception) {}

        return@withContext "Coimbatore"
    }

    /**
     * Fetches real directions routes from Geoapify Routing API with alternatives,
     * fetches OpenStreetMap corridor data along each route, and calculates differentiated safety scores.
     */
    suspend fun fetchRealRoutes(
        waypoints: List<Pair<Double, Double>>,
        originName: String,
        destinationName: String,
        intermediateNames: List<String> = emptyList(),
        timeOfDay: TimeOfDay = TimeOfDay.DUSK,
        incidentZones: List<IncidentZone> = emptyList(),
        context: Context
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext emptyList()

        val sourceCoord = waypoints.first()
        val destCoord = waypoints.last()

        val cacheKey = waypoints.joinToString(";") { "${(it.first * 1e4).toInt()},${(it.second * 1e4).toInt()}" } +
                ":${timeOfDay.name}:${incidentZones.size}"

        synchronized(routeCache) {
            routeCache[cacheKey]?.let { return@withContext it }
        }

        if (!isApiKeyConfigured()) {
            _lastErrorMessage.value = LanguageManager.get("ola_maps_key_missing")
            return@withContext emptyList()
        }

        val intermediatePoints = if (waypoints.size > 2) waypoints.subList(1, waypoints.size - 1) else emptyList()
        val corridorRepo = CorridorPlacesRepository.getInstance(context)
        val distinctRoutes = mutableListOf<ParsedGeoapifyRoute>()

        // 1. Try Ola Maps Directions API first if configured
        if (isOlaMapsConfigured()) {
            val olaKey = getOlaMapsApiKey()
            val olaUrl = "https://api.olamaps.io/routing/v1/directions?origin=${sourceCoord.first},${sourceCoord.second}&destination=${destCoord.first},${destCoord.second}&alternatives=true&api_key=$olaKey"
            try {
                val request = Request.Builder()
                    .url(olaUrl)
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .header("Accept", "application/json")
                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val olaParsed = parseOlaRoutingJson(body)
                    if (olaParsed.isNotEmpty()) {
                        distinctRoutes.addAll(olaParsed)
                    }
                } else {
                    handleHttpError(response.code, response.message)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ola directions request failed: ${e.message}")
            }
        }

        // 2. Fall back to Geoapify Routing API if Ola Maps had no routes and Geoapify is configured
        if (distinctRoutes.isEmpty() && isGeoapifyConfigured()) {
            val apiKey = getApiKey()
            val waypointsParam = buildString {
                append("${sourceCoord.first},${sourceCoord.second}")
                for (p in intermediatePoints) {
                    append("|${p.first},${p.second}")
                }
                append("|${destCoord.first},${destCoord.second}")
            }

            // Generate routes with different routing strategies: balanced, short, less_maneuvers
            val routingTypes = listOf("balanced", "short", "less_maneuvers")
            val rawCandidates = mutableListOf<ParsedGeoapifyRoute>()

            for (rType in routingTypes) {
                val url = "$BASE_URL/v1/routing?waypoints=$waypointsParam&mode=drive&type=$rType&apiKey=$apiKey"
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val parsed = parseGeoapifyRoutingJson(body, rType)
                        if (parsed != null && parsed.coordinates.isNotEmpty()) {
                            rawCandidates.add(parsed)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Routing type $rType query failed: ${e.message}")
                }
            }

            // Deduplicate near-identical geometries
            val geoapifyDistinct = deduplicateRoutes(rawCandidates)
            distinctRoutes.addAll(geoapifyDistinct)

            // If fewer than 2 distinct routes, apply the "via" waypoint trick to guarantee a genuine alternative
            if (distinctRoutes.size < 2 && intermediatePoints.isEmpty()) {
                val midLat = (sourceCoord.first + destCoord.first) / 2.0
                val midLng = (sourceCoord.second + destCoord.second) / 2.0
                val offsetLat = midLat + 0.012
                val offsetLng = midLng - 0.012

                val viaWaypoints = "${sourceCoord.first},${sourceCoord.second}|$offsetLat,$offsetLng|${destCoord.first},${destCoord.second}"
                val viaUrl = "$BASE_URL/v1/routing?waypoints=$viaWaypoints&mode=drive&type=balanced&apiKey=$apiKey"
                try {
                    val req = Request.Builder().url(viaUrl).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val parsed = parseGeoapifyRoutingJson(resp.body?.string().orEmpty(), "alternative_via")
                        if (parsed != null && isGeometricallyDistinct(parsed.coordinates, distinctRoutes)) {
                            distinctRoutes.add(parsed)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Via route alternative failed: ${e.message}")
                }
            }
        }

        if (distinctRoutes.isEmpty()) {
            _lastErrorMessage.value = LanguageManager.get("could_not_find_place")
            return@withContext emptyList()
        }

        // Construct waypoints models
        val sourceWaypoint = Waypoint(name = originName, isSource = true, lat = sourceCoord.first, lng = sourceCoord.second)
        val destWaypoint = Waypoint(name = destinationName, isDestination = true, lat = destCoord.first, lng = destCoord.second)
        val stopWaypoints = intermediateNames.mapIndexed { idx, name ->
            val wCoord = intermediatePoints.getOrNull(idx) ?: sourceCoord
            Waypoint(name = name, lat = wCoord.first, lng = wCoord.second)
        }
        val allWaypoints = listOf(sourceWaypoint) + stopWaypoints + listOf(destWaypoint)

        // Fetch corridor data and calculate safety scores in parallel
        val corridorDeferred = distinctRoutes.map { candidate ->
            async {
                val durationMins = max(1, (candidate.durationSeconds / 60.0).toInt())
                corridorRepo.fetchCorridorData(candidate.coordinates, durationMins)
            }
        }
        val corridors = corridorDeferred.awaitAll()

        val finalOptions = mutableListOf<RouteOption>()

        for (i in distinctRoutes.indices) {
            val candidate = distinctRoutes[i]
            val corridor = corridors[i]
            val coords = candidate.coordinates
            val distKm = Math.round((candidate.distanceMeters / 1000.0) * 10.0) / 10.0
            val durMins = max(1, (candidate.durationSeconds / 60.0).toInt())

            val routeId = when (i) {
                0 -> "opt_safe"
                1 -> "opt_balanced"
                else -> "opt_alt_$i"
            }

            val safetyDetails = SafetyCalculator.calculateRouteSafetyFromCorridor(
                routeIndex = i,
                routeCoordinates = coords,
                corridorData = corridor,
                timeOfDay = timeOfDay,
                incidentZones = incidentZones,
                routeId = routeId
            )

            val prominentRoad = candidate.prominentStreet.ifBlank {
                if (i == 0) "Arterial Expressway" else "Commercial Avenue"
            }

            val routeTitle = when (i) {
                0 -> "via $prominentRoad (Primary Route)"
                1 -> "via $prominentRoad (Alternative 1)"
                else -> "via $prominentRoad (Alternative $i)"
            }

            val summaryText = "Route via $prominentRoad (${distKm} km, ${durMins} mins)"
            val warningNote = safetyDetails.reliabilityWarning ?: ""

            val segments = createVisualSegments(coords, corridor, routeTitle)
            val legs = buildLegs(allWaypoints, distKm, durMins, safetyDetails.compositeScore)

            finalOptions.add(
                RouteOption(
                    id = routeId,
                    name = routeTitle,
                    summary = summaryText,
                    distanceKm = distKm,
                    durationMinutes = durMins,
                    safetyDetails = safetyDetails,
                    segments = segments,
                    pathCoordinates = coords,
                    isRecommended = (i == 0),
                    warningNote = warningNote,
                    sourceName = originName,
                    destinationName = destinationName,
                    waypoints = allWaypoints,
                    legs = legs,
                    turnInstructions = candidate.instructions,
                    places = corridor.places,
                    corridorData = corridor
                )
            )
        }

        synchronized(routeCache) {
            routeCache[cacheKey] = finalOptions
        }
        clearError()
        return@withContext finalOptions
    }

    private data class ParsedGeoapifyRoute(
        val type: String,
        val distanceMeters: Double,
        val durationSeconds: Double,
        val prominentStreet: String,
        val coordinates: List<Pair<Double, Double>>,
        val instructions: List<TurnInstruction>
    )

    fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val poly = ArrayList<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val pLat = lat.toDouble() / 1E5
            val pLng = lng.toDouble() / 1E5
            poly.add(Pair(pLat, pLng))
        }
        return poly
    }

    private fun parseOlaRoutingJson(json: String): List<ParsedGeoapifyRoute> {
        val list = mutableListOf<ParsedGeoapifyRoute>()
        try {
            val root = JSONObject(json)
            val routes = root.optJSONArray("routes") ?: return emptyList()
            for (i in 0 until routes.length()) {
                val routeObj = routes.getJSONObject(i)
                val polyline = routeObj.optString("overview_polyline", "")
                val coords = if (polyline.isNotBlank()) decodePolyline(polyline) else emptyList()
                if (coords.isEmpty()) continue

                val legs = routeObj.optJSONArray("legs")
                var totalDist = 0.0
                var totalDur = 0.0
                val instructions = mutableListOf<TurnInstruction>()
                val streetCounts = mutableMapOf<String, Double>()

                if (legs != null) {
                    for (l in 0 until legs.length()) {
                        val leg = legs.getJSONObject(l)
                        totalDist += leg.optDouble("distance", 0.0)
                        totalDur += leg.optDouble("duration", 0.0)
                        val steps = leg.optJSONArray("steps") ?: continue
                        for (s in 0 until steps.length()) {
                            val step = steps.getJSONObject(s)
                            val instr = step.optString("instructions", "").trim()
                            val dist = step.optDouble("distance", 0.0)
                            val maneuver = step.optString("maneuver", "")

                            val roadName = if (instr.contains("onto ", ignoreCase = true)) {
                                instr.substringAfter("onto ", "").trim()
                            } else if (instr.contains("on ", ignoreCase = true)) {
                                instr.substringAfter("on ", "").trim()
                            } else ""

                            if (roadName.isNotBlank()) {
                                streetCounts[roadName] = (streetCounts[roadName] ?: 0.0) + dist
                            }

                            val iconType = when {
                                maneuver.contains("arrive", ignoreCase = true) -> "arrive"
                                maneuver.contains("left", ignoreCase = true) -> "turn_left"
                                maneuver.contains("right", ignoreCase = true) -> "turn_right"
                                maneuver.contains("u-turn", ignoreCase = true) -> "u_turn"
                                else -> "straight"
                            }

                            instructions.add(
                                TurnInstruction(
                                    distanceMeters = dist.toInt(),
                                    text = instr.ifBlank { "Continue on route" },
                                    streetName = roadName.ifBlank { "Main Road" },
                                    iconType = iconType,
                                    safetyStatus = "Active Corridor"
                                )
                            )
                        }
                    }
                }

                val prominentRoad = streetCounts.maxByOrNull { it.value }?.key ?: ""
                list.add(
                    ParsedGeoapifyRoute(
                        type = if (i == 0) "ola_primary" else "ola_alternative_$i",
                        distanceMeters = if (totalDist > 0) totalDist else 1000.0,
                        durationSeconds = if (totalDur > 0) totalDur else 300.0,
                        prominentStreet = prominentRoad,
                        coordinates = coords,
                        instructions = instructions
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Ola routing response: ${e.message}")
        }
        return list
    }

    private fun parseGeoapifyRoutingJson(json: String, type: String): ParsedGeoapifyRoute? {
        try {
            val root = JSONObject(json)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null

            val feat = features.getJSONObject(0)
            val geom = feat.optJSONObject("geometry") ?: return null
            val geomType = geom.optString("type")
            val rawCoords = geom.optJSONArray("coordinates") ?: return null

            val pathCoords = mutableListOf<Pair<Double, Double>>()
            if (geomType.equals("MultiLineString", ignoreCase = true)) {
                for (l in 0 until rawCoords.length()) {
                    val line = rawCoords.optJSONArray(l) ?: continue
                    for (c in 0 until line.length()) {
                        val pt = line.optJSONArray(c) ?: continue
                        val lng = pt.optDouble(0)
                        val lat = pt.optDouble(1)
                        pathCoords.add(Pair(lat, lng))
                    }
                }
            } else {
                for (c in 0 until rawCoords.length()) {
                    val pt = rawCoords.optJSONArray(c) ?: continue
                    val lng = pt.optDouble(0)
                    val lat = pt.optDouble(1)
                    pathCoords.add(Pair(lat, lng))
                }
            }

            val props = feat.optJSONObject("properties") ?: JSONObject()
            val distance = props.optDouble("distance", 0.0)
            val time = props.optDouble("time", 0.0)

            val instructions = mutableListOf<TurnInstruction>()
            val streetLengths = mutableMapOf<String, Double>()

            val legs = props.optJSONArray("legs")
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val leg = legs.getJSONObject(l)
                    val steps = leg.optJSONArray("steps") ?: continue
                    for (s in 0 until steps.length()) {
                        val step = steps.getJSONObject(s)
                        val dist = step.optDouble("distance", 0.0)
                        val street = step.optString("street", "").trim()
                        val instrObj = step.optJSONObject("instruction")
                        val text = instrObj?.optString("text", "")?.ifBlank { "Continue on route" } ?: "Continue on route"
                        val stepType = instrObj?.optString("type", "straight") ?: "straight"

                        if (street.isNotBlank()) {
                            streetLengths[street] = (streetLengths[street] ?: 0.0) + dist
                        }

                        val iconType = when {
                            stepType.contains("arrive", ignoreCase = true) -> "arrive"
                            stepType.contains("left", ignoreCase = true) -> "turn_left"
                            stepType.contains("right", ignoreCase = true) -> "turn_right"
                            else -> "straight"
                        }

                        instructions.add(
                            TurnInstruction(
                                distanceMeters = dist.toInt(),
                                text = text,
                                streetName = street.ifBlank { "Main Road" },
                                iconType = iconType,
                                safetyStatus = "Active Corridor"
                            )
                        )
                    }
                }
            }

            val prominent = streetLengths.maxByOrNull { it.value }?.key ?: ""

            return ParsedGeoapifyRoute(
                type = type,
                distanceMeters = distance,
                durationSeconds = time,
                prominentStreet = prominent,
                coordinates = pathCoords,
                instructions = instructions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Geoapify routing JSON: ${e.message}")
            return null
        }
    }

    private fun deduplicateRoutes(routes: List<ParsedGeoapifyRoute>): MutableList<ParsedGeoapifyRoute> {
        val unique = mutableListOf<ParsedGeoapifyRoute>()
        for (r in routes) {
            if (isGeometricallyDistinct(r.coordinates, unique)) {
                unique.add(r)
            }
        }
        return unique
    }

    private fun isGeometricallyDistinct(
        coords: List<Pair<Double, Double>>,
        existing: List<ParsedGeoapifyRoute>,
        thresholdMeters: Double = 120.0
    ): Boolean {
        if (coords.isEmpty()) return false
        if (existing.isEmpty()) return true

        val stride = max(1, coords.size / 15)
        for (other in existing) {
            var diffCount = 0
            var sampleCount = 0
            for (i in coords.indices step stride) {
                val pt = coords[i]
                val otherStride = max(1, other.coordinates.size / 15)
                val minDist = other.coordinates.filterIndexed { idx, _ -> idx % otherStride == 0 }
                    .minOfOrNull { haversineDistanceM(pt, it) } ?: 0.0

                if (minDist > thresholdMeters) {
                    diffCount++
                }
                sampleCount++
            }
            if (sampleCount > 0 && (diffCount.toDouble() / sampleCount.toDouble()) < 0.25) {
                // More than 75% identical -> not distinct
                return false
            }
        }
        return true
    }

    private fun createVisualSegments(
        coords: List<Pair<Double, Double>>,
        corridor: com.example.saferoute.data.model.RouteCorridorData,
        title: String
    ): List<RouteSegment> {
        if (coords.isEmpty()) return emptyList()
        val numSegments = 3.coerceAtMost(coords.size - 1).coerceAtLeast(1)
        val stepSize = (coords.size - 1) / numSegments
        val result = mutableListOf<RouteSegment>()

        for (i in 0 until numSegments) {
            val startIdx = i * stepSize
            val endIdx = if (i == numSegments - 1) coords.size - 1 else (i + 1) * stepSize
            val p1 = coords[startIdx]
            val p2 = coords[endIdx]
            val dist = haversineDistanceM(p1, p2).toInt().coerceAtLeast(150)

            result.add(
                RouteSegment(
                    id = "seg_${i + 1}",
                    name = "$title Segment ${i + 1}",
                    distanceMeters = dist,
                    isMainRoad = (i == 0 || i == 1),
                    lightingScore = if (corridor.hasLightingData) (corridor.litRatio * 100f) else 0f,
                    crowdDensity = if (corridor.openPlacesCount > 0) (corridor.openShopsDensityPer100m * 60f).coerceIn(20f, 95f) else 0f,
                    incidentCount = 0,
                    startLat = p1.first,
                    startLng = p1.second,
                    endLat = p2.first,
                    endLng = p2.second
                )
            )
        }
        return result
    }

    private fun buildLegs(waypoints: List<Waypoint>, totalDist: Double, totalDur: Int, baseScore: Int): List<RouteLeg> {
        val count = max(1, waypoints.size - 1)
        val legs = mutableListOf<RouteLeg>()
        for (i in 0 until count) {
            val from = waypoints[i].name
            val to = waypoints[i + 1].name
            val legDist = Math.round((totalDist / count) * 10.0) / 10.0
            val legDur = max(1, totalDur / count)
            val legScore = when (i % 3) {
                0 -> baseScore
                1 -> (baseScore - 4).coerceAtLeast(35)
                else -> (baseScore - 2).coerceAtLeast(40)
            }
            legs.add(
                RouteLeg(
                    legIndex = i + 1,
                    fromName = from,
                    toName = to,
                    distanceKm = legDist,
                    durationMinutes = legDur,
                    safetyScore = legScore,
                    safetyTier = if (legScore >= 80) SafetyTier.HIGH_SAFETY else if (legScore >= 60) SafetyTier.MEDIUM_SAFETY else SafetyTier.CAUTION_REQUIRED
                )
            )
        }
        return legs
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

    private fun handleHttpError(code: Int, message: String) {
        when (code) {
            401 -> _lastErrorMessage.value = LanguageManager.get("api_key_invalid")
            429 -> _lastErrorMessage.value = LanguageManager.get("rate_limit_exceeded")
            else -> _lastErrorMessage.value = LanguageManager.get("could_not_find_place")
        }
    }

    private fun handleNetworkException(e: Exception) {
        if (e is IOException) {
            _lastErrorMessage.value = LanguageManager.get("network_offline")
        } else {
            _lastErrorMessage.value = LanguageManager.get("could_not_find_place")
        }
    }
}
