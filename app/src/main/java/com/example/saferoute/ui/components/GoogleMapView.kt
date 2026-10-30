package com.example.saferoute.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.saferoute.data.model.GroupMember
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.PlaceCategory
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.RoutePlace
import com.example.saferoute.data.network.GeoapifyService
import com.example.saferoute.util.LanguageManager
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polygon
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Coimbatore Geographic Center and Configuration constants.
 */
object CoimbatoreMapConfig {
    const val CENTER_LAT = 11.0168
    const val CENTER_LNG = 76.9558
    const val INITIAL_ZOOM = 14.0

    const val MIN_ZOOM = 8.0
    const val MAX_ZOOM = 19.0

    val SafeCorridorGreen = Color(0xFF00E676)
    val CautionaryRed = Color(0xFFFF5252)
    val GoogleBlueDot = Color(0xFF1A73E8)
    val GooglePulseHalo = Color(0xFF4285F4)
}

/**
 * Helper to compute geodesic circle polygon vertices for incident zones.
 */
private fun createCirclePoints(center: LatLng, radiusMeters: Double, numPoints: Int = 36): List<LatLng> {
    val points = ArrayList<LatLng>(numPoints)
    val earthRadius = 6378137.0
    val latRad = Math.toRadians(center.latitude)
    val lngRad = Math.toRadians(center.longitude)
    val dR = radiusMeters / earthRadius

    for (i in 0 until numPoints) {
        val bearing = Math.toRadians(i * (360.0 / numPoints))
        val pLat = Math.asin(Math.sin(latRad) * Math.cos(dR) + Math.cos(latRad) * Math.sin(dR) * Math.cos(bearing))
        val pLng = lngRad + Math.atan2(
            Math.sin(bearing) * Math.sin(dR) * Math.cos(latRad),
            Math.cos(dR) - Math.sin(latRad) * Math.sin(pLat)
        )
        points.add(LatLng(Math.toDegrees(pLat), Math.toDegrees(pLng)))
    }
    return points
}

/**
 * Generates circular marker bitmap with text badge and shadow for MapLibre IconFactory.
 */
private fun createCircularMarkerBitmap(
    context: Context,
    outerColor: Int,
    innerColor: Int,
    text: String? = null,
    radiusDp: Float = 16f
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (radiusDp * 2 * density).toInt().coerceAtLeast(32)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val center = sizePx / 2f
    val outerRadius = sizePx / 2f - 2f * density

    // Drop shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(80, 0, 0, 0)
    }
    canvas.drawCircle(center, center, outerRadius + 1.5f * density, shadowPaint)

    // Outer ring
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = outerColor
    }
    canvas.drawCircle(center, center, outerRadius, outerPaint)

    // White ring casing
    val whiteRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(center, center, outerRadius - 3.5f * density, whiteRingPaint)

    // Inner core
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = innerColor
    }
    canvas.drawCircle(center, center, outerRadius - 5.5f * density, innerPaint)

    // Text badge (e.g. 'A', 'B', '1', '2')
    if (!text.isNullOrBlank()) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(text, center, textY, textPaint)
    }

    return bitmap
}

/**
 * Generates live user location GPS blue dot bitmap with soft pulse halo.
 */
private fun createUserLocationBitmap(
    context: Context,
    pulseRadiusPx: Float,
    pulseAlpha: Float
): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (48f * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    // 1. Soft glowing outer pulse halo
    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor("#4285F4")
        alpha = (pulseAlpha.coerceIn(0f, 1f) * 255).toInt()
    }
    canvas.drawCircle(center, center, pulseRadiusPx.coerceIn(10f, center - 2f), haloPaint)

    // 2. White outer casing
    val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(center, center, 9f * density, whiteBorderPaint)

    // 3. Crisp GPS Blue center core
    val blueCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor("#1A73E8")
    }
    canvas.drawCircle(center, center, 6.5f * density, blueCorePaint)

    return bitmap
}

/**
 * Shared Map component powered by MapLibre Android SDK and Ola Maps Vector Tiles.
 * Keeps original GoogleMapView signature so all screens require no parameter adjustments.
 */
@Composable
fun GoogleMapView(
    routes: List<RouteOption>,
    selectedRouteId: String?,
    onRouteSelected: (String) -> Unit,
    incidentZones: List<IncidentZone> = emptyList(),
    modifier: Modifier = Modifier,
    latitude: Double = CoimbatoreMapConfig.CENTER_LAT,
    longitude: Double = CoimbatoreMapConfig.CENTER_LNG,
    userLocation: Pair<Double, Double>? = Pair(CoimbatoreMapConfig.CENTER_LAT, CoimbatoreMapConfig.CENTER_LNG),
    isNavigating: Boolean = false,
    activeInstructionText: String? = null,
    groupMembers: List<GroupMember> = emptyList(),
    showControls: Boolean = true,
    destinationName: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentLang = com.example.saferoute.util.LocalAppLanguage.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize MapLibre instance once per context
    remember {
        try {
            MapLibre.getInstance(context)
            val olaKey = GeoapifyService.getOlaMapsApiKey()
            if (olaKey.isNotBlank() && !olaKey.startsWith("YOUR_")) {
                org.maplibre.android.storage.FileSource.getInstance(context).setResourceTransform { _, url ->
                    if (url.contains("olamaps.io") && !url.contains("api_key=")) {
                        if (url.contains("?")) "$url&api_key=$olaKey" else "$url?api_key=$olaKey"
                    } else {
                        url
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("GoogleMapView", "MapLibre JVM init: ${t.message}")
        }
    }

    val apiKey = GeoapifyService.getApiKey()
    val isApiKeyMissing = !GeoapifyService.isApiKeyConfigured()

    // Track inspected route for popover breakdown card
    var inspectedRouteId by remember { mutableStateOf<String?>(null) }
    val inspectedRoute = routes.find { it.id == inspectedRouteId }

    // Track category filter chip for POIs along the active route
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    // Track clicked place POI for floating details card
    var selectedPlaceOnMap by remember { mutableStateOf<RoutePlace?>(null) }
    // Toggle for accident black-spots visibility
    var showBlackSpots by remember { mutableStateOf(true) }

    // Pulsing transition for live user location dot
    val infiniteTransition = rememberInfiniteTransition(label = "UserPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    // MapView & MapLibreMap references
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var mapLibreMapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // Lifecycle forwarding
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewInstance?.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance?.onPause()
                Lifecycle.Event.ON_STOP -> mapViewInstance?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapViewInstance?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewInstance?.onDestroy()
        }
    }

    // Camera animation when external coordinates change
    var lastCenterLat by remember { mutableDoubleStateOf(latitude) }
    var lastCenterLng by remember { mutableDoubleStateOf(longitude) }
    LaunchedEffect(latitude, longitude) {
        if (latitude != lastCenterLat || longitude != lastCenterLng) {
            lastCenterLat = latitude
            lastCenterLng = longitude
            mapLibreMapInstance?.animateCamera(
                CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)),
                500
            )
        }
    }

    val activeRouteLayerIds = remember { mutableSetOf<String>() }
    val activeRouteSourceIds = remember { mutableSetOf<String>() }

    // Fit camera to selected route or follow user location in navigation
    fun adjustCamera(map: MapLibreMap, animated: Boolean = true) {
        if (isNavigating && userLocation != null) {
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(userLocation.first, userLocation.second))
                .zoom(17.0)
                .tilt(45.0)
                .build()
            val update = CameraUpdateFactory.newCameraPosition(cameraPosition)
            if (animated) map.animateCamera(update, 700) else map.moveCamera(update)
        } else {
            val active = routes.find { it.id == selectedRouteId } ?: routes.firstOrNull()
            if (active != null && active.pathCoordinates.size >= 2) {
                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    active.pathCoordinates.forEach { boundsBuilder.include(LatLng(it.first, it.second)) }
                    val update = CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
                    if (animated) map.animateCamera(update, 800) else map.moveCamera(update)
                } catch (e: Exception) {
                    val first = active.pathCoordinates.first()
                    val update = CameraUpdateFactory.newLatLngZoom(LatLng(first.first, first.second), 14.5)
                    if (animated) map.animateCamera(update, 600) else map.moveCamera(update)
                }
            }
        }
    }

    // Modern GeoJsonSource + LineLayer route rendering directly on MapLibre Style
    fun updateRouteGeoJsonLayers(map: MapLibreMap) {
        map.getStyle { style ->
            // Clean up previous route layers
            activeRouteLayerIds.forEach { layerId ->
                try { style.getLayer(layerId)?.let { style.removeLayer(it) } } catch (_: Exception) {}
            }
            activeRouteLayerIds.clear()

            // Clean up previous route sources
            activeRouteSourceIds.forEach { sourceId ->
                try { style.getSource(sourceId)?.let { style.removeSource(it) } } catch (_: Exception) {}
            }
            activeRouteSourceIds.clear()

            if (routes.isEmpty()) return@getStyle

            val sortedRoutes = routes.sortedBy { it.id == selectedRouteId }
            for ((idx, route) in sortedRoutes.withIndex()) {
                val isSelected = (route.id == selectedRouteId) || (selectedRouteId == null && route == routes.firstOrNull())
                // GeoJSON Point requires (longitude, latitude) strictly
                val points = route.pathCoordinates.map { Point.fromLngLat(it.second, it.first) }
                if (points.size < 2) continue

                val lineString = LineString.fromLngLats(points)
                val feature = Feature.fromGeometry(lineString)

                if (isSelected) {
                    val sourceId = "source_route_selected"
                    val glowLayerId = "layer_route_selected_glow"
                    val mainLayerId = "layer_route_selected_main"

                    val source = GeoJsonSource(sourceId, feature)
                    style.addSource(source)
                    activeRouteSourceIds.add(sourceId)

                    val safetyGlowColor = when {
                        route.safetyDetails.compositeScore >= 80 -> android.graphics.Color.parseColor("#00E676") // Green
                        route.safetyDetails.compositeScore >= 60 -> android.graphics.Color.parseColor("#FFB300") // Amber
                        else -> android.graphics.Color.parseColor("#FF5252") // Red
                    }

                    // 1. Glow Layer underneath (wider, colored by route safety tier)
                    val glowLayer = LineLayer(glowLayerId, sourceId).apply {
                        setProperties(
                            PropertyFactory.lineColor(safetyGlowColor),
                            PropertyFactory.lineWidth(16f),
                            PropertyFactory.lineOpacity(0.85f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        )
                    }
                    style.addLayer(glowLayer)
                    activeRouteLayerIds.add(glowLayerId)

                    // 2. Main Selected Blue Line (#1A73E8)
                    val mainLayer = LineLayer(mainLayerId, sourceId).apply {
                        setProperties(
                            PropertyFactory.lineColor(android.graphics.Color.parseColor("#1A73E8")),
                            PropertyFactory.lineWidth(8.5f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        )
                    }
                    style.addLayer(mainLayer)
                    activeRouteLayerIds.add(mainLayerId)
                } else {
                    val altSourceId = "source_route_alt_$idx"
                    val altLayerId = "layer_route_alt_$idx"

                    val source = GeoJsonSource(altSourceId, feature)
                    style.addSource(source)
                    activeRouteSourceIds.add(altSourceId)

                    // Thinner grey (#9AA0A6) for alternatives
                    val altLayer = LineLayer(altLayerId, altSourceId).apply {
                        setProperties(
                            PropertyFactory.lineColor(android.graphics.Color.parseColor("#9AA0A6")),
                            PropertyFactory.lineWidth(5.5f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        )
                    }
                    style.addLayer(altLayer)
                    activeRouteLayerIds.add(altLayerId)
                }
            }
        }
    }

    // Rerender MapLibre markers and incident polygons
    val density = context.resources.displayMetrics.density
    fun updateMarkersAndPolygons(map: MapLibreMap) {
        map.clear()

        // 1. INCIDENT ZONES (Semi-transparent circle polygons with severity colors)
        if (showBlackSpots) {
            incidentZones.forEach { zone ->
                val circlePoints = createCirclePoints(
                    center = LatLng(zone.latitude, zone.longitude),
                    radiusMeters = zone.radiusMeters.coerceAtLeast(120.0)
                )
                val (fillColor, strokeColor) = when {
                    zone.severity >= 4 -> Pair(
                        android.graphics.Color.argb(70, 239, 68, 68),  // Red
                        android.graphics.Color.argb(190, 239, 68, 68)
                    )
                    zone.severity >= 3 -> Pair(
                        android.graphics.Color.argb(60, 245, 158, 11), // Amber
                        android.graphics.Color.argb(180, 245, 158, 11)
                    )
                    else -> Pair(
                        android.graphics.Color.argb(50, 234, 179, 8),  // Yellow
                        android.graphics.Color.argb(170, 234, 179, 8)
                    )
                }
                map.addPolygon(
                    PolygonOptions()
                        .addAll(circlePoints)
                        .fillColor(fillColor)
                        .strokeColor(strokeColor)
                )
            }
        }

        // 2. SOURCE, STOP AND DESTINATION MARKERS (Green 'A', Numbered Blue '1', Red 'B')
        val activeRoute = routes.find { it.id == selectedRouteId } ?: routes.firstOrNull()
        if (activeRoute != null && activeRoute.pathCoordinates.isNotEmpty()) {
            val originCoord = activeRoute.pathCoordinates.first()
            val destCoord = activeRoute.pathCoordinates.last()

            // Source Marker (Green 'A')
            val sourceBmp = createCircularMarkerBitmap(
                context = context,
                outerColor = android.graphics.Color.parseColor("#00E676"),
                innerColor = android.graphics.Color.parseColor("#10B981"),
                text = "A",
                radiusDp = 16f
            )
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(originCoord.first, originCoord.second))
                    .title(activeRoute.sourceName.ifBlank { "Starting Point" })
                    .icon(IconFactory.getInstance(context).fromBitmap(sourceBmp))
            )

            // Destination Marker (Red 'B')
            val destBmp = createCircularMarkerBitmap(
                context = context,
                outerColor = android.graphics.Color.parseColor("#EF4444"),
                innerColor = android.graphics.Color.parseColor("#DC2626"),
                text = "B",
                radiusDp = 16f
            )
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(destCoord.first, destCoord.second))
                    .title(destinationName ?: activeRoute.destinationName.ifBlank { "Destination" })
                    .icon(IconFactory.getInstance(context).fromBitmap(destBmp))
            )

            // Numbered Intermediate Stops (Blue '1', '2')
            val waypoints = activeRoute.waypoints.filter { !it.isSource && !it.isDestination }
            waypoints.forEachIndexed { idx, wp ->
                val wpPoint = if (wp.lat != 0.0 && wp.lng != 0.0) {
                    LatLng(wp.lat, wp.lng)
                } else {
                    val fraction = (idx + 1).toFloat() / (waypoints.size + 1)
                    val wpLat = originCoord.first + (destCoord.first - originCoord.first) * fraction
                    val wpLng = originCoord.second + (destCoord.second - originCoord.second) * fraction
                    LatLng(wpLat, wpLng)
                }

                val stopBmp = createCircularMarkerBitmap(
                    context = context,
                    outerColor = android.graphics.Color.parseColor("#1A73E8"),
                    innerColor = android.graphics.Color.parseColor("#1A73E8"),
                    text = "${idx + 1}",
                    radiusDp = 14f
                )
                map.addMarker(
                    MarkerOptions()
                        .position(wpPoint)
                        .title("Stop ${idx + 1}: ${wp.name}")
                        .icon(IconFactory.getInstance(context).fromBitmap(stopBmp))
                )
            }
        }

        // 3. GROUP MEMBERS (If active)
        groupMembers.forEach { member ->
            val memColor = when {
                member.currentSafetyScore >= 80 -> android.graphics.Color.parseColor("#00E676")
                member.currentSafetyScore >= 60 -> android.graphics.Color.parseColor("#FFB300")
                else -> android.graphics.Color.parseColor("#EF4444")
            }
            val memLat = CoimbatoreMapConfig.CENTER_LAT + (member.relativeY * 0.02)
            val memLng = CoimbatoreMapConfig.CENTER_LNG + (member.relativeX * 0.02)

            val memberBmp = createCircularMarkerBitmap(
                context = context,
                outerColor = memColor,
                innerColor = memColor,
                text = member.avatarInitials.take(2),
                radiusDp = 13f
            )
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(memLat, memLng))
                    .title("${member.name} (${member.currentSafetyScore}%)")
                    .icon(IconFactory.getInstance(context).fromBitmap(memberBmp))
            )
        }

        // 4. LIVE USER LOCATION (Blue Dot with soft halo) - Real live coordinates from LocationHelper
        val userPos = userLocation?.let { LatLng(it.first, it.second) } ?: LatLng(latitude, longitude)
        val userDotBmp = createUserLocationBitmap(
            context = context,
            pulseRadiusPx = 22f * density,
            pulseAlpha = 0.45f
        )
        map.addMarker(
            MarkerOptions()
                .position(userPos)
                .title(LanguageManager.get("you_are_here", currentLang))
                .icon(IconFactory.getInstance(context).fromBitmap(userDotBmp))
        )

        // 5. PLACES ALONG ACTIVE ROUTE (Filtered by Category Filter Chips)
        if (activeRoute != null && activeRoute.places.isNotEmpty()) {
            val placesToShow = when (selectedCategoryFilter) {
                "HELP" -> activeRoute.places.filter { it.category.isHelpPoint }
                "MEDICAL" -> activeRoute.places.filter { it.category == PlaceCategory.HOSPITAL || it.category == PlaceCategory.PHARMACY }
                "FUEL" -> activeRoute.places.filter { it.category == PlaceCategory.FUEL }
                "FOOD" -> activeRoute.places.filter { it.category == PlaceCategory.FOOD }
                "SHOPS" -> activeRoute.places.filter { it.category == PlaceCategory.SHOP }
                else -> activeRoute.places
            }.take(40)

            for (place in placesToShow) {
                val (outerColor, innerColor, badgeText) = if (!place.isOpenNow) {
                    Triple(
                        android.graphics.Color.parseColor("#64748B"),
                        android.graphics.Color.parseColor("#475569"),
                        when (place.category) {
                            PlaceCategory.HOSPITAL -> "H"
                            PlaceCategory.PHARMACY -> "Rx"
                            PlaceCategory.POLICE -> "P"
                            PlaceCategory.FUEL -> "F"
                            PlaceCategory.FOOD -> "R"
                            PlaceCategory.SHOP -> "S"
                            PlaceCategory.ATM -> "A"
                            PlaceCategory.OTHER_HELP -> "+"
                        }
                    )
                } else {
                    when (place.category) {
                        PlaceCategory.HOSPITAL -> Triple(android.graphics.Color.parseColor("#EF4444"), android.graphics.Color.parseColor("#DC2626"), "H")
                        PlaceCategory.PHARMACY -> Triple(android.graphics.Color.parseColor("#10B981"), android.graphics.Color.parseColor("#059669"), "Rx")
                        PlaceCategory.POLICE -> Triple(android.graphics.Color.parseColor("#3B82F6"), android.graphics.Color.parseColor("#2563EB"), "P")
                        PlaceCategory.FUEL -> Triple(android.graphics.Color.parseColor("#F59E0B"), android.graphics.Color.parseColor("#D97706"), "F")
                        PlaceCategory.FOOD -> Triple(android.graphics.Color.parseColor("#EC4899"), android.graphics.Color.parseColor("#DB2777"), "R")
                        PlaceCategory.SHOP -> Triple(android.graphics.Color.parseColor("#8B5CF6"), android.graphics.Color.parseColor("#7C3AED"), "S")
                        PlaceCategory.ATM -> Triple(android.graphics.Color.parseColor("#06B6D4"), android.graphics.Color.parseColor("#0891B2"), "A")
                        PlaceCategory.OTHER_HELP -> Triple(android.graphics.Color.parseColor("#14B8A6"), android.graphics.Color.parseColor("#0D9488"), "+")
                    }
                }

                val placeBmp = createCircularMarkerBitmap(
                    context = context,
                    outerColor = outerColor,
                    innerColor = innerColor,
                    text = badgeText,
                    radiusDp = 12f
                )

                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(place.lat, place.lng))
                        .title("PLACE:${place.id}")
                        .snippet(place.name)
                        .icon(IconFactory.getInstance(context).fromBitmap(placeBmp))
                )
            }
        }
    }

    fun updateMapOverlays(map: MapLibreMap) {
        updateRouteGeoJsonLayers(map)
        updateMarkersAndPolygons(map)
    }

    // Re-draw overlays and adjust camera whenever routes, selection, location, category filter, or navigation state updates
    LaunchedEffect(routes, selectedRouteId, incidentZones, groupMembers, userLocation, isNavigating, selectedCategoryFilter, showBlackSpots, mapLibreMapInstance) {
        val map = mapLibreMapInstance ?: return@LaunchedEffect
        updateMapOverlays(map)
        adjustCamera(map, animated = true)
    }

    Box(
        modifier = modifier
            .testTag("google_map_view")
            .clipToBounds()
            .fillMaxSize()
    ) {
        // MapLibre MapView hosting Ola Maps light vector tiles
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { maplibreMap ->
                        mapLibreMapInstance = maplibreMap

                        maplibreMap.setMinZoomPreference(CoimbatoreMapConfig.MIN_ZOOM)
                        maplibreMap.setMaxZoomPreference(CoimbatoreMapConfig.MAX_ZOOM)
                        maplibreMap.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(CoimbatoreMapConfig.INITIAL_ZOOM)
                            .build()

                        // Marker click listener for POIs along route
                        maplibreMap.setOnMarkerClickListener { marker ->
                            val title = marker.title
                            if (title != null && title.startsWith("PLACE:")) {
                                val placeId = title.removePrefix("PLACE:")
                                val active = routes.find { it.id == selectedRouteId } ?: routes.firstOrNull()
                                val found = active?.places?.find { it.id == placeId }
                                if (found != null) {
                                    selectedPlaceOnMap = found
                                    return@setOnMarkerClickListener true
                                }
                            }
                            false
                        }

                        // Tap-to-select route click listener
                        maplibreMap.addOnMapClickListener { clickLatLng ->
                            selectedPlaceOnMap = null
                            var closestRouteId: String? = null
                            var minDistanceMeters = Double.MAX_VALUE

                            for (route in routes) {
                                for (coord in route.pathCoordinates) {
                                    val dist = FloatArray(1)
                                    android.location.Location.distanceBetween(
                                        clickLatLng.latitude, clickLatLng.longitude,
                                        coord.first, coord.second,
                                        dist
                                    )
                                    if (dist[0] < minDistanceMeters) {
                                        minDistanceMeters = dist[0].toDouble()
                                        closestRouteId = route.id
                                    }
                                }
                            }

                            if (closestRouteId != null && minDistanceMeters <= 100.0) {
                                inspectedRouteId = closestRouteId
                                onRouteSelected(closestRouteId)
                                true
                            } else {
                                false
                            }
                        }

                        // Load Ola Maps vector style with automatic CartoDB local fallback
                        addOnDidFailLoadingMapListener { errorMessage ->
                            android.util.Log.w("GoogleMapView", "Map load failed: $errorMessage. Loading local fallback.")
                            maplibreMap.setStyle(GeoapifyService.getLocalStyleBuilder(context)) {
                                updateMapOverlays(maplibreMap)
                                adjustCamera(maplibreMap, animated = false)
                            }
                        }

                        coroutineScope.launch {
                            val styleBuilder = GeoapifyService.getStyleBuilder(context)

                            maplibreMap.setStyle(styleBuilder) {
                                updateMapOverlays(maplibreMap)
                                adjustCamera(maplibreMap, animated = false)
                            }
                        }
                    }
                    mapViewInstance = this
                }
            },
            update = { mapView ->
                mapViewInstance = mapView
            },
            modifier = Modifier.fillMaxSize()
        )



        // CATEGORY FILTER CHIPS ROW (Toggles POI markers on map: All, Help, Medical, Fuel, Food, Shops)
        if (showControls && !isNavigating && routes.isNotEmpty()) {
            val filterItems = listOf(
                Pair("ALL", LanguageManager.get("all_places", currentLang)),
                Pair("HELP", LanguageManager.get("help_points", currentLang)),
                Pair("MEDICAL", LanguageManager.get("medical", currentLang)),
                Pair("FUEL", LanguageManager.get("petrol_pumps", currentLang)),
                Pair("FOOD", LanguageManager.get("food", currentLang)),
                Pair("SHOPS", LanguageManager.get("shops", currentLang))
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isApiKeyMissing) 72.dp else 12.dp)
                    .padding(horizontal = 14.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xDD0F172A),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Black-spots toggle chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (showBlackSpots) SafetyRed.copy(alpha = 0.25f) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (showBlackSpots) SafetyRed else Color(0xFF334155)),
                        modifier = Modifier.clickable { showBlackSpots = !showBlackSpots }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (showBlackSpots) SafetyRed else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = LanguageManager.get("toggle_blackspots", currentLang),
                                fontSize = 11.5.sp,
                                fontWeight = if (showBlackSpots) FontWeight.Bold else FontWeight.Medium,
                                color = if (showBlackSpots) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }

                    filterItems.forEach { (key, label) ->
                        val isSelected = selectedCategoryFilter == key
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SafeRouteTeal else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isSelected) SafeRouteTeal else Color(0xFF334155)),
                            modifier = Modifier.clickable {
                                selectedCategoryFilter = key
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // TAP-TO-INSPECT FLOATING ROUTE BREAKDOWN POPOVER OVERLAY
        if (showControls) {
            AnimatedVisibility(
                visible = inspectedRoute != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp, start = 14.dp, end = 14.dp)
            ) {
                inspectedRoute?.let { route ->
                    val tierColor = route.safetyDetails.tier.color

                    Card(
                        modifier = Modifier
                            .testTag("tap_to_inspect_popover_${route.id}")
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                        elevation = CardDefaults.cardElevation(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (route.isRecommended) LanguageManager.get("safe_corridor", currentLang) else LanguageManager.get("route_breakdown", currentLang),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (route.isRecommended) CoimbatoreMapConfig.SafeCorridorGreen else SafetyAmber,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = route.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { inspectedRouteId = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Inspection",
                                        tint = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Safety Score Badge Pill
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = tierColor.copy(alpha = 0.20f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = tierColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val hasScore = route.safetyDetails.compositeScore > 0 && route.safetyDetails.dataConfidence != com.example.saferoute.data.model.DataConfidence.LOW
                                        Text(
                                            text = if (hasScore) "${route.safetyDetails.tier.title} (${route.safetyDetails.compositeScore}/100)" else "Safety score unavailable",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasScore) tierColor else Color(0xFF94A3B8)
                                        )
                                    }
                                    Text(
                                        text = "${route.durationMinutes} mins • ${route.distanceKm} km",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Segment Metrics Breakdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFFD166), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(LanguageManager.get("lighting_metric", currentLang), fontSize = 10.sp, color = Color.Gray)
                                    val litText = route.safetyDetails.lightingFactor?.let { "${it.toInt()}%" } ?: "No data"
                                    Text(litText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (route.safetyDetails.lightingFactor != null) Color.White else Color.Gray)
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(LanguageManager.get("crowd_metric", currentLang), fontSize = 10.sp, color = Color.Gray)
                                    val crowdText = route.safetyDetails.crowdDensityFactor?.let { "${it.toInt()}%" } ?: "No data"
                                    Text(crowdText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (route.safetyDetails.crowdDensityFactor != null) Color.White else Color.Gray)
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = SafetyAmber, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(LanguageManager.get("risk_metric", currentLang), fontSize = 10.sp, color = Color.Gray)
                                    val incText = route.safetyDetails.incidentHistoryFactor?.let { "${it.toInt()}%" } ?: "No data"
                                    Text(incText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (route.safetyDetails.incidentHistoryFactor != null) Color.White else Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    onRouteSelected(route.id)
                                    inspectedRouteId = null
                                },
                                modifier = Modifier
                                    .testTag("select_inspected_route_button")
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (route.id == selectedRouteId) CoimbatoreMapConfig.SafeCorridorGreen else SafeRouteBlue
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (route.id == selectedRouteId) Icons.Default.CheckCircle else Icons.Default.Navigation,
                                        contentDescription = null,
                                        tint = if (route.id == selectedRouteId) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (route.id == selectedRouteId) LanguageManager.get("selected_route", currentLang) else LanguageManager.get("select_this_route", currentLang),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (route.id == selectedRouteId) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ACTIVE TURN INSTRUCTION OVERLAY (If Navigating)
        if (isNavigating && activeInstructionText != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Nav Instruction",
                        tint = CoimbatoreMapConfig.SafeCorridorGreen,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = activeInstructionText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // FLOATING CONTROLS CLUSTER (Recenter, Compass, Segmented Zoom)
        if (showControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Floating Compass / North-Up Button
                Surface(
                    modifier = Modifier
                        .testTag("map_compass_button")
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            val map = mapLibreMapInstance ?: return@clickable
                            map.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
                        },
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "North-Up Compass",
                            tint = Color(0xFFEA4335),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Recenter-to-My-Location Button
                Surface(
                    modifier = Modifier
                        .testTag("recenter_my_location_button")
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            val target = userLocation ?: Pair(CoimbatoreMapConfig.CENTER_LAT, CoimbatoreMapConfig.CENTER_LNG)
                            mapLibreMapInstance?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(target.first, target.second), 15.5),
                                500
                            )
                        },
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Recenter to My Location",
                            tint = CoimbatoreMapConfig.GoogleBlueDot,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Segmented Zoom In / Zoom Out (+ / -)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp
                ) {
                    Column {
                        IconButton(
                            onClick = { mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomIn(), 250) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        IconButton(
                            onClick = { mapLibreMapInstance?.animateCamera(CameraUpdateFactory.zoomOut(), 250) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // FLOATING PLACE POI DETAILS INFO CARD (When a place marker is clicked)
        AnimatedVisibility(
            visible = selectedPlaceOnMap != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 14.dp, end = 14.dp)
        ) {
            selectedPlaceOnMap?.let { place ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                    elevation = CardDefaults.cardElevation(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${place.category.displayName} • ${place.distanceFromRouteM.toInt()}m from route",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            IconButton(
                                onClick = { selectedPlaceOnMap = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Place Card",
                                    tint = Color.LightGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (place.isOpenNow) SafetyGreen.copy(alpha = 0.15f) else SafetyRed.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (place.isOpenNow) LanguageManager.get("open", currentLang) else LanguageManager.get("closed", currentLang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (place.isOpenNow) SafetyGreen else SafetyRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            val hoursStatus = when {
                                place.is24x7 -> "Open 24/7"
                                place.isOpenNow && !place.closesAt.isNullOrBlank() -> "Closes ${place.closesAt}"
                                !place.isOpenNow && !place.opensAt.isNullOrBlank() -> "Opens ${place.opensAt}"
                                else -> LanguageManager.get("hours_not_listed", currentLang)
                            }

                            Text(
                                text = hoursStatus,
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.Medium
                            )

                            if (!place.isOpenWhenPassing && place.isOpenNow) {
                                Text(
                                    text = "• Closed at ETA",
                                    fontSize = 11.sp,
                                    color = SafetyAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!place.phone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}")).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeRouteBlue)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${LanguageManager.get("call", currentLang)} (${place.phone})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
