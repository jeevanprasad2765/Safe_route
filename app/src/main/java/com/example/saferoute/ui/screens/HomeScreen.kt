package com.example.saferoute.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.example.saferoute.data.db.SavedPlaceEntity
import com.example.saferoute.data.db.TripHistoryEntity
import com.example.saferoute.data.model.GroupTripSession
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.network.GeoapifyService
import com.example.saferoute.ui.components.LeafletMapView
import com.example.saferoute.ui.components.SafetyBadge
import com.example.saferoute.util.LanguageManager
import com.example.saferoute.util.LocationHelper
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import com.example.ui.theme.bounceClick
import com.example.ui.theme.shimmer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    savedPlaces: List<SavedPlaceEntity>,
    recentTrips: List<TripHistoryEntity>,
    incidentZones: List<IncidentZone>,
    activeGroupTrip: GroupTripSession = GroupTripSession(),
    userLocationCoordinates: Pair<Double, Double>? = null,
    onUpdateUserCoordinates: (coords: Pair<Double, Double>, resolvedName: String?) -> Unit = { _, _ -> },
    onSelectRoute: (source: String, destination: String, waypoints: List<String>, sourceCoords: Pair<Double, Double>?, destCoords: Pair<Double, Double>?) -> Unit = { _, _, _, _, _ -> },
    onNavigateToAuth: () -> Unit = {},
    onSaveSavedPlace: (title: String, iconName: String, address: String, lat: Double, lng: Double) -> Unit = { _, _, _, _, _ -> },
    onUpdateSavedPlace: (SavedPlaceEntity) -> Unit = {},
    onDeleteSavedPlace: (SavedPlaceEntity) -> Unit = {},
    onStartGroupTrip: () -> Unit = {}
) {
    var sourceQuery by remember { mutableStateOf("My Location") }
    var destinationQuery by remember { mutableStateOf("") }
    var intermediateStops by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeSearchFocusField by remember { mutableStateOf<String?>(null) } // "source", "dest", null
    var showGroupTripDialog by remember { mutableStateOf(false) }

    // Saved-place editing: entity with id > 0 = editing existing; id == 0 = creating new
    var addressSheetEntry by remember { mutableStateOf<SavedPlaceEntity?>(null) }
    var pendingDeletePlace by remember { mutableStateOf<SavedPlaceEntity?>(null) }
    var deletedPlaceForUndo by remember { mutableStateOf<SavedPlaceEntity?>(null) }

    // Real Location Detection and Accompanist Permission States
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val isAnyLocationGranted = locationPermissionsState.permissions.any { it.status.isGranted }
    val isPreciseLocationGranted = locationPermissionsState.permissions.firstOrNull {
        it.permission == Manifest.permission.ACCESS_FINE_LOCATION
    }?.status?.isGranted == true
    val shouldShowRationale = locationPermissionsState.shouldShowRationale

    var showRationaleDialog by remember { mutableStateOf(false) }
    var isDetectingLocation by remember { mutableStateOf(true) }
    var isLocationPermissionGranted by remember { mutableStateOf(false) }
    var showPermissionPromptBanner by remember { mutableStateOf(false) }
    var locationDetectionError by remember { mutableStateOf<String?>(null) } // "PERMISSION_DENIED", "TIMEOUT"
    var userCurrentCoordinates by remember { mutableStateOf<Pair<Double, Double>?>(userLocationCoordinates) }
    var mapCenterLat by remember { mutableStateOf(userLocationCoordinates?.first ?: 11.0168) }
    var mapCenterLng by remember { mutableStateOf(userLocationCoordinates?.second ?: 76.9558) }
    var showSourceTooltip by remember { mutableStateOf(false) }

    fun acquireCoordinates() {
        isDetectingLocation = true
        locationDetectionError = null
        coroutineScope.launch {
            val coords = LocationHelper.getCurrentCoordinates(context)
            if (coords != null) {
                userCurrentCoordinates = coords
                mapCenterLat = coords.first
                mapCenterLng = coords.second
                isLocationPermissionGranted = true
                showPermissionPromptBanner = false
                locationDetectionError = null

                val resolved = GeoapifyService.reverseGeocode(coords.first, coords.second, context)
                sourceQuery = if (!resolved.isNullOrBlank()) {
                    "My Location ($resolved)"
                } else {
                    "My Location (${String.format(java.util.Locale.US, "%.4f, %.4f", coords.first, coords.second)})"
                }
                onUpdateUserCoordinates(coords, resolved)
            } else {
                // Check whether permission was denied or signal timed out
                val errorType = if (!isAnyLocationGranted) "PERMISSION_DENIED" else "TIMEOUT"
                locationDetectionError = errorType
                showPermissionPromptBanner = true
                val fallbackCoords = LocationHelper.DEFAULT_COIMBATORE_COORDINATES
                userCurrentCoordinates = fallbackCoords
                mapCenterLat = fallbackCoords.first
                mapCenterLng = fallbackCoords.second
                onUpdateUserCoordinates(fallbackCoords, null)
                isLocationPermissionGranted = isAnyLocationGranted
                sourceQuery = "My Location"
            }
            isDetectingLocation = false
        }
    }

    // React to Accompanist permissions changes
    LaunchedEffect(isAnyLocationGranted) {
        if (isAnyLocationGranted) {
            isLocationPermissionGranted = true
            showPermissionPromptBanner = false
            locationDetectionError = null
            acquireCoordinates()
        } else {
            isLocationPermissionGranted = false
            isDetectingLocation = false
            showPermissionPromptBanner = true
            locationDetectionError = if (shouldShowRationale) "RATIONALE_NEEDED" else "PERMISSION_DENIED"
            if (userCurrentCoordinates == null) {
                userCurrentCoordinates = LocationHelper.DEFAULT_COIMBATORE_COORDINATES
                sourceQuery = "My Location (Coimbatore)"
            }
        }
    }

    // Auto-request location permissions via Accompanist on initial screen load
    LaunchedEffect(Unit) {
        if (!isAnyLocationGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        } else {
            acquireCoordinates()
        }
    }

    // Timeout safety: if GPS acquisition is stuck for more than 8 seconds, fall back gracefully
    LaunchedEffect(isDetectingLocation) {
        if (isDetectingLocation) {
            kotlinx.coroutines.delay(8000)
            if (isDetectingLocation) {
                isDetectingLocation = false
                if (userCurrentCoordinates == null) {
                    locationDetectionError = "TIMEOUT"
                    showPermissionPromptBanner = true
                    userCurrentCoordinates = LocationHelper.DEFAULT_COIMBATORE_COORDINATES
                    sourceQuery = "My Location (Coimbatore)"
                }
            }
        }
    }

    // Reverse geocode detected coordinates to real location name if not already resolved
    LaunchedEffect(userCurrentCoordinates) {
        val coords = userCurrentCoordinates
        if (coords != null && isLocationPermissionGranted) {
            val resolved = GeoapifyService.reverseGeocode(coords.first, coords.second, context)
            if (!resolved.isNullOrBlank()) {
                sourceQuery = "My Location ($resolved)"
                onUpdateUserCoordinates(coords, resolved)
            } else {
                sourceQuery = "My Location (${String.format(java.util.Locale.US, "%.4f, %.4f", coords.first, coords.second)})"
            }
        }
    }

    var liveSearchResults by remember { mutableStateOf<List<com.example.saferoute.data.network.SearchResult>>(emptyList()) }
    var selectedSourceCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedDestCoord by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var geocodeErrorMessage by remember { mutableStateOf<String?>(null) }
    var isGeocodingSearching by remember { mutableStateOf(false) }

    val currentQuery = if (activeSearchFocusField == "source") sourceQuery else destinationQuery

    LaunchedEffect(currentQuery, activeSearchFocusField) {
        if (activeSearchFocusField != null && currentQuery.trim().length >= 2 && !currentQuery.startsWith("My Location")) {
            kotlinx.coroutines.delay(400) // 400 ms debounce requirement
            isGeocodingSearching = true
            val results = GeoapifyService.searchPlaces(currentQuery, userCurrentCoordinates)
            liveSearchResults = results
            isGeocodingSearching = false
        } else {
            liveSearchResults = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .testTag("home_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // LOCATION PERMISSION / DETECTION FALLBACK BANNER (Powered by Accompanist Permissions)
        if (showPermissionPromptBanner || !isAnyLocationGranted || isDetectingLocation || locationDetectionError != null) {
            val isTimeout = locationDetectionError == "TIMEOUT"
            val isDenied = locationDetectionError == "PERMISSION_DENIED" || !isAnyLocationGranted
            val isRationale = shouldShowRationale
            val bannerBg = if (isDetectingLocation) Color(0xFF0F2338) else if (isTimeout) Color(0xFF2A1C08) else Color(0xFF2D1515)
            val bannerBorder = if (isDetectingLocation) SafeRouteBlue.copy(alpha = 0.4f) else if (isTimeout) SafetyAmber.copy(alpha = 0.4f) else SafetyRed.copy(alpha = 0.4f)
            val bannerIconColor = if (isDetectingLocation) SafeRouteTeal else if (isTimeout) SafetyAmber else SafetyRed

            Box(
                modifier = Modifier
                    .testTag("location_permission_banner")
                    .fillMaxWidth()
                    .background(bannerBg)
                    .border(1.dp, bannerBorder)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isDetectingLocation -> Icons.Default.MyLocation
                                isTimeout -> Icons.Default.Refresh
                                else -> Icons.Default.MyLocation
                            },
                            contentDescription = null,
                            tint = bannerIconColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isDetectingLocation -> "Acquiring GPS location..."
                                isTimeout -> "Location detection timed out. Using Coimbatore center"
                                isDenied -> "Location access denied. Using Coimbatore fallback"
                                isRationale -> "Location permission required for real-time safety routing"
                                else -> LanguageManager.get("location_permission_banner")
                            },
                            fontSize = 11.sp,
                            color = bannerIconColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRationale) {
                            Text(
                                text = "Why?",
                                color = SafeRouteTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showRationaleDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (!isDetectingLocation) {
                            Text(
                                text = if (isTimeout || isDenied) "Retry" else LanguageManager.get("enable"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bannerIconColor.copy(alpha = 0.25f))
                                    .clickable {
                                        if (isDenied && !isAnyLocationGranted) {
                                            locationPermissionsState.launchMultiplePermissionRequest()
                                        } else {
                                            acquireCoordinates()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Accompanist Location Rationale Dialog
        if (showRationaleDialog) {
            AlertDialog(
                onDismissRequest = { showRationaleDialog = false },
                containerColor = DarkNavyCard,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SafeRouteTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Location Access",
                            tint = SafeRouteTeal,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Live Location for Safe Navigation",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "SafeRoute uses your physical coordinates to deliver proactive protection while you navigate:",
                            fontSize = 12.5.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 17.sp
                        )

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Real-Time Safety Audit: Evaluates street lighting, crowd activity, and crime reports directly around you.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 16.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeRouteBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Precision Route Departure: Computes turn-by-turn navigation departing directly from your live position.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 16.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Emergency SOS Broadcast: Dispatches your accurate live GPS coordinates to trusted emergency contacts.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRationaleDialog = false
                            locationPermissionsState.launchMultiplePermissionRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeRouteTeal)
                    ) {
                        Text("Grant Location", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showRationaleDialog = false
                            LocationHelper.openAppSettings(context)
                        }) {
                            Text("App Settings", color = SafeRouteBlue)
                        }
                        TextButton(onClick = { showRationaleDialog = false }) {
                            Text("Dismiss", color = Color(0xFF94A3B8))
                        }
                    }
                }
            )
        }

        // BRAND HEADER WITH EMBLEM LOGO & THEMED BADGE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.app_icon_saferoute),
                    contentDescription = "SafeRoute Emblem",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Safe",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Route",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Black,
                            color = com.example.ui.theme.SafeRouteCrimson,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(com.example.ui.theme.SafeRouteBlue.copy(alpha = 0.22f))
                                .border(1.dp, com.example.ui.theme.SafeRouteCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AI SAFE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = com.example.ui.theme.SafeRouteCyan,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Text(
                        text = "Every route scored for physical safety",
                        fontSize = 11.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // TOP HERO MAP SECTION
        val homeCorridorRoutes = remember { emptyList<com.example.saferoute.data.model.RouteOption>() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
        ) {
            LeafletMapView(
                routes = homeCorridorRoutes,
                selectedRouteId = "opt_safe_home",
                incidentZones = incidentZones,
                userLocation = userCurrentCoordinates ?: Pair(11.0168, 76.9558),
                latitude = mapCenterLat,
                longitude = mapCenterLng,
                autoDetectLocation = false,
                onLocationDetected = { lat, lng ->
                    val coords = Pair(lat, lng)
                    userCurrentCoordinates = coords
                    mapCenterLat = lat
                    mapCenterLng = lng
                    isDetectingLocation = false
                    isLocationPermissionGranted = true
                    showPermissionPromptBanner = false
                    locationDetectionError = null
                    coroutineScope.launch {
                        val resolved = GeoapifyService.reverseGeocode(lat, lng, context)
                        sourceQuery = if (!resolved.isNullOrBlank()) {
                            "My Location ($resolved)"
                        } else {
                            "My Location (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)})"
                        }
                        onUpdateUserCoordinates(coords, resolved)
                    }
                },
                onLocationError = { reason, msg ->
                    Log.w("HomeScreen", "Location acquisition: reason=$reason, msg=$msg")
                    isDetectingLocation = false
                    locationDetectionError = if (reason == "PERMISSION_DENIED") "PERMISSION_DENIED" else "TIMEOUT"
                    showPermissionPromptBanner = true
                    if (userCurrentCoordinates == null) {
                        sourceQuery = "My Location (Coimbatore)"
                        userCurrentCoordinates = Pair(11.0168, 76.9558)
                    }
                },
                onMapError = {
                    Log.w("HomeScreen", "Google Maps display error")
                },
                modifier = Modifier.fillMaxSize()
            )

            // SINGLE COMPACT FLOATING SEARCH & ACTION CARD OVERLAYING MAP
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkNavySurface.copy(alpha = 0.96f))
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    // Unified Search Inputs Row with Route Connector Indicator & Swap Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Visual Vertical Route Connector
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 10.dp, end = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(SafetyGreen)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(26.dp)
                                    .background(Color(0x44FFFFFF))
                            )
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = SafetyRed,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Combined Inputs (Starting Point & Destination)
                        Column(modifier = Modifier.weight(1f)) {
                            // 1. Starting Point Input ("My Location (RS Puram)")
                            Box(
                                modifier = Modifier
                                    .testTag("home_source_search_input")
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (isDetectingLocation) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 1.8.dp,
                                            color = SafeRouteTeal
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = LanguageManager.get("acquiring_location"),
                                            fontSize = 12.5.sp,
                                            lineHeight = 16.sp,
                                            color = SafeRouteTeal,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(sourceQuery) {
                                                detectTapGestures(
                                                    onLongPress = {
                                                        if (sourceQuery.isNotBlank()) {
                                                            showSourceTooltip = true
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            BasicTextField(
                                                value = sourceQuery,
                                                onValueChange = {
                                                    sourceQuery = it
                                                    selectedSourceCoord = null
                                                    geocodeErrorMessage = null
                                                    activeSearchFocusField = "source"
                                                },
                                                textStyle = TextStyle(
                                                    fontSize = 12.5.sp,
                                                    lineHeight = 16.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Normal
                                                ),
                                                singleLine = true,
                                                cursorBrush = SolidColor(SafeRouteTeal),
                                                modifier = Modifier.fillMaxWidth(),
                                                decorationBox = { innerTextField ->
                                                    if (sourceQuery.isEmpty()) {
                                                        Text(
                                                            text = LanguageManager.get("search_source_placeholder"),
                                                            fontSize = 12.sp,
                                                            lineHeight = 16.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            )
                                        }

                                        if (sourceQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    sourceQuery = ""
                                                    selectedSourceCoord = null
                                                    geocodeErrorMessage = null
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Long-press tooltip if resolved address is long
                                    if (showSourceTooltip && sourceQuery.isNotBlank()) {
                                        Popup(
                                            alignment = Alignment.BottomStart,
                                            onDismissRequest = { showSourceTooltip = false }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(bottom = 36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFF0F172A))
                                                    .border(1.dp, SafeRouteTeal.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                             ) {
                                                Text(
                                                    text = sourceQuery,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Subtle Divider between the two search inputs
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x1AFFFFFF))
                            )

                            // 2. Destination Input ("Where to?")
                            Box(
                                modifier = Modifier
                                    .testTag("home_destination_search_input")
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        BasicTextField(
                                            value = destinationQuery,
                                            onValueChange = {
                                                destinationQuery = it
                                                selectedDestCoord = null
                                                geocodeErrorMessage = null
                                                activeSearchFocusField = "dest"
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 12.5.sp,
                                                lineHeight = 16.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            singleLine = true,
                                            cursorBrush = SolidColor(SafeRouteTeal),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                if (destinationQuery.isEmpty()) {
                                                    Text(
                                                        text = LanguageManager.get("search_destination_placeholder"),
                                                        fontSize = 12.sp,
                                                        lineHeight = 16.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )
                                    }

                                    if (destinationQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                destinationQuery = ""
                                                selectedDestCoord = null
                                                geocodeErrorMessage = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Swap Button
                        Box(
                            modifier = Modifier
                                .testTag("home_swap_source_dest_button")
                                .size(34.dp)
                                .bounceClick(scaleDown = 0.88f) {
                                    val tempQ = sourceQuery
                                    sourceQuery = destinationQuery
                                    destinationQuery = tempQ
                                    val tempC = selectedSourceCoord
                                    selectedSourceCoord = selectedDestCoord
                                    selectedDestCoord = tempC
                                    geocodeErrorMessage = null
                                }
                                .clip(CircleShape)
                                .background(SafeRouteBlue.copy(alpha = 0.15f))
                                .border(1.dp, SafeRouteBlue.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = LanguageManager.get("swap_locations"),
                                tint = SafeRouteBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Intermediate stops (if any added)
                    intermediateStops.forEachIndexed { index, stopText ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedTextField(
                                value = stopText,
                                onValueChange = { newText ->
                                    val list = intermediateStops.toMutableList()
                                    list[index] = newText
                                    intermediateStops = list
                                },
                                placeholder = { Text("Stop ${index + 1}", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("home_stop_input_$index"),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            IconButton(
                                onClick = {
                                    val list = intermediateStops.toMutableList()
                                    list.removeAt(index)
                                    intermediateStops = list
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = LanguageManager.get("remove_stop"), tint = SafetyRed, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Primary Action & Stops Row: "+ Add Stop" and "Find Safe Routes"
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .testTag("home_add_stop_button")
                                .bounceClick(scaleDown = 0.94f) {
                                    intermediateStops = intermediateStops + ""
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(SafeRouteTeal.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(LanguageManager.get("add_stop"), fontSize = 11.sp, color = SafeRouteTeal, fontWeight = FontWeight.Bold)
                        }

                        // Dominant Primary Call-to-Action
                        Box(
                            modifier = Modifier
                                .testTag("home_find_routes_button")
                                .bounceClick(scaleDown = 0.94f) {
                                    coroutineScope.launch {
                                        geocodeErrorMessage = null
                                        val src = sourceQuery.ifBlank { "My Location" }
                                        val dest = destinationQuery.trim()
                                        if (dest.isBlank()) {
                                            geocodeErrorMessage = "Couldn't find that place"
                                            return@launch
                                        }

                                        // 1. Resolve source coordinates (use live GPS for 'My Location', never a fixed place)
                                        val finalSrcCoords: Pair<Double, Double>? = when {
                                            selectedSourceCoord != null -> selectedSourceCoord
                                            src.startsWith("My Location", ignoreCase = true) -> userCurrentCoordinates ?: userLocationCoordinates
                                            else -> {
                                                val res = GeoapifyService.geocodePlace(src)
                                                if (res != null) Pair(res.latitude, res.longitude) else null
                                            }
                                        }

                                        // 2. Resolve destination coordinates
                                        val finalDestCoords: Pair<Double, Double>? = when {
                                            selectedDestCoord != null -> selectedDestCoord
                                            dest.startsWith("My Location", ignoreCase = true) -> userCurrentCoordinates ?: userLocationCoordinates
                                            else -> {
                                                val res = GeoapifyService.geocodePlace(dest)
                                                if (res != null) Pair(res.latitude, res.longitude) else null
                                            }
                                        }

                                        if (finalSrcCoords == null || finalDestCoords == null) {
                                            geocodeErrorMessage = GeoapifyService.lastErrorMessage.value ?: "Couldn't find that place"
                                            return@launch
                                        }

                                        val filteredStops = intermediateStops.filter { it.isNotBlank() }
                                        onSelectRoute(src, dest, filteredStops, finalSrcCoords, finalDestCoords)
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(SafeRouteBlue, SafeRouteTeal)
                                    )
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Find Safe Routes", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Geocoding Error Banner
                    if (geocodeErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33EF4444))
                                .border(1.dp, Color(0x66EF4444), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(geocodeErrorMessage ?: "Couldn't find that place", fontSize = 11.5.sp, color = Color(0xFFFCA5A5), modifier = Modifier.weight(1f))
                            IconButton(onClick = { geocodeErrorMessage = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFFFCA5A5), modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Autocomplete Dropdown Suggestions with real coordinates
                    if (activeSearchFocusField != null && liveSearchResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavyCard)
                                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            liveSearchResults.take(5).forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(scaleDown = 0.98f) {
                                            if (activeSearchFocusField == "source") {
                                                sourceQuery = result.shortName
                                                selectedSourceCoord = Pair(result.latitude, result.longitude)
                                            } else {
                                                destinationQuery = result.shortName
                                                selectedDestCoord = Pair(result.latitude, result.longitude)
                                            }
                                            activeSearchFocusField = null
                                            liveSearchResults = emptyList()
                                            geocodeErrorMessage = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(result.shortName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                        Text(result.displayName, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // LIVE GPS LOCATION INDICATOR PILL
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassSurfaceDark)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        if (!isAnyLocationGranted) {
                            if (shouldShowRationale) {
                                showRationaleDialog = true
                            } else {
                                locationPermissionsState.launchMultiplePermissionRequest()
                            }
                        } else {
                            acquireCoordinates()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDetectingLocation) SafeRouteTeal
                                else if (isAnyLocationGranted && userCurrentCoordinates != null) SafetyGreen
                                else SafetyAmber
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDetectingLocation) "Acquiring GPS..."
                        else if (isAnyLocationGranted && userCurrentCoordinates != null) {
                            if (isPreciseLocationGranted) "Live GPS • Active" else "Live GPS • Coarse"
                        }
                        else "GPS Off • Tap to Enable",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // SECONDARY CONTENT BELOW THE MAP FOLD (Single scrollable section, 16/24px spacing scale)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            // 1. SAVED PLACES (Editable / Deletable chips, Home & Work start empty)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LanguageManager.get("saved_places"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .bounceClick(scaleDown = 0.9f) {
                            addressSheetEntry = SavedPlaceEntity(title = "", address = "", iconName = "star", latitude = 0.0, longitude = 0.0)
                        }
                        .clip(CircleShape)
                        .background(SafeRouteTeal.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Saved Place", tint = SafeRouteTeal, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Undo bar for recently deleted place
            if (deletedPlaceForUndo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF243247))
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\"${deletedPlaceForUndo?.title}\" removed",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    TextButton(onClick = {
                        val p = deletedPlaceForUndo
                        if (p != null) {
                            onSaveSavedPlace(p.title, p.iconName, p.address, p.latitude, p.longitude)
                            deletedPlaceForUndo = null
                        }
                    }) {
                        Text("UNDO", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                LaunchedEffect(deletedPlaceForUndo) {
                    if (deletedPlaceForUndo != null) {
                        kotlinx.coroutines.delay(5000)
                        deletedPlaceForUndo = null
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Home & Work placeholders when not yet set
            val missingCorePlaces = buildList {
                if (savedPlaces.none { it.iconName == "home" }) add("home")
                if (savedPlaces.none { it.iconName == "work" }) add("work")
            }
            var openChipsMenuForId by remember { mutableStateOf<Long?>(null) }

            // Compact chips row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(missingCorePlaces.size) { idx ->
                    val iconName = missingCorePlaces[idx]
                    val label = iconName.replaceFirstChar { it.uppercase() }
                    Box(
                        modifier = Modifier
                            .testTag("saved_place_placeholder_$iconName")
                            .bounceClick(scaleDown = 0.95f) {
                                addressSheetEntry = SavedPlaceEntity(title = label, address = "", iconName = iconName, latitude = 0.0, longitude = 0.0)
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkNavyCard)
                            .border(1.dp, SafetyAmber.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (iconName == "home") Icons.Default.Home else Icons.Default.Work,
                                contentDescription = label,
                                tint = SafetyAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Set address",
                                    fontSize = 9.sp,
                                    color = SafetyAmber
                                )
                            }
                        }
                    }
                }
                items(savedPlaces) { place ->
                    Box {
                        Row(
                            modifier = Modifier
                                .testTag("saved_place_card_${place.title.lowercase()}")
                                .bounceClick(scaleDown = 0.95f) {
                                    if (place.address.isBlank()) {
                                        addressSheetEntry = place
                                    } else {
                                        destinationQuery = place.title
                                        onSelectRoute(sourceQuery.ifBlank { "My Location" }, place.title, intermediateStops, selectedSourceCoord, Pair(place.latitude, place.longitude))
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkNavyCard)
                                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(12.dp))
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (place.iconName) {
                                        "home" -> Icons.Default.Home
                                        "work" -> Icons.Default.Work
                                        "fitness" -> Icons.Default.FitnessCenter
                                        else -> Icons.Default.LocationOn
                                    },
                                    contentDescription = place.title,
                                    tint = if (place.address.isBlank()) SafetyAmber else SafeRouteBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = place.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (place.address.isBlank()) {
                                        Text(text = "Set address", fontSize = 9.sp, color = SafetyAmber)
                                    }
                                }
                                IconButton(
                                    onClick = { openChipsMenuForId = place.id },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Place options",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = openChipsMenuForId == place.id,
                                    onDismissRequest = { openChipsMenuForId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (place.address.isBlank()) "Set address" else "Edit") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            openChipsMenuForId = null
                                            addressSheetEntry = place
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            openChipsMenuForId = null
                                            pendingDeletePlace = place
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SET ADDRESS BOTTOM SHEET (create / edit a saved place)
            if (addressSheetEntry != null) {
                val sheetPlace = addressSheetEntry!!
                var sheetTitle by remember(sheetPlace.id) { mutableStateOf(sheetPlace.title) }
                var sheetAddress by remember(sheetPlace.id) { mutableStateOf(sheetPlace.address) }
                var sheetLat by remember(sheetPlace.id) { mutableStateOf<Double?>(if (sheetPlace.id != 0L) sheetPlace.latitude else null) }
                var sheetLng by remember(sheetPlace.id) { mutableStateOf<Double?>(if (sheetPlace.id != 0L) sheetPlace.longitude else null) }
                var sheetResults by remember(sheetPlace.id) { mutableStateOf<List<com.example.saferoute.data.network.SearchResult>>(emptyList()) }
                var sheetSearching by remember(sheetPlace.id) { mutableStateOf(false) }
                var isLocatingForSheet by remember(sheetPlace.id) { mutableStateOf(false) }

                // Debounced Geoapify address autocomplete (Coimbatore biased)
                LaunchedEffect(sheetAddress) {
                    val q = sheetAddress.trim()
                    if (q.length >= 3) {
                        kotlinx.coroutines.delay(400)
                        sheetSearching = true
                        sheetResults = GeoapifyService.searchPlaces(q, userCurrentCoordinates)
                        sheetSearching = false
                    } else {
                        sheetResults = emptyList()
                        sheetSearching = false
                    }
                }

                val canSaveSheet = sheetTitle.isNotBlank() && !sheetAddress.isBlank() && sheetLat != null && sheetLng != null

                ModalBottomSheet(
                    onDismissRequest = { addressSheetEntry = null },
                    containerColor = DarkNavyCard
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (sheetPlace.id == 0L) "Set Address" else "Edit \"${sheetTitle.ifBlank { sheetPlace.title }}\"",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = sheetTitle,
                            onValueChange = { sheetTitle = it },
                            placeholder = { Text("Label (e.g. Home, Gym)", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = SafeRouteTeal,
                                unfocusedIndicatorColor = Color(0xFF334155)
                            )
                        )

                        OutlinedTextField(
                            value = sheetAddress,
                            onValueChange = {
                                sheetAddress = it
                                sheetLat = null
                                sheetLng = null
                            },
                            placeholder = { Text("Search address in Coimbatore", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = SafeRouteTeal,
                                unfocusedIndicatorColor = Color(0xFF334155)
                            )
                        )

                        if (sheetSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }

                        // Autocomplete results
                        sheetResults.take(5).forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bounceClick(scaleDown = 0.98f) {
                                        sheetAddress = result.displayName
                                        sheetLat = result.latitude
                                        sheetLng = result.longitude
                                        if (sheetTitle.isBlank()) sheetTitle = result.shortName
                                        sheetResults = emptyList()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(result.shortName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                                    Text(result.displayName, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Use my current location / pick-on-map shortcuts
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick(scaleDown = 0.95f) {
                                        coroutineScope.launch {
                                            isLocatingForSheet = true
                                            val coords = userCurrentCoordinates ?: LocationHelper.getCurrentCoordinates(context)
                                            isLocatingForSheet = false
                                            if (coords != null) {
                                                sheetLat = coords.first
                                                sheetLng = coords.second
                                                val resolved = GeoapifyService.reverseGeocode(coords.first, coords.second, context)
                                                sheetAddress = resolved?.ifBlank { null }
                                                    ?: String.format(java.util.Locale.US, "%.4f, %.4f", coords.first, coords.second)
                                            }
                                        }
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SafeRouteTeal.copy(alpha = 0.12f))
                                    .border(1.dp, SafeRouteTeal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isLocatingForSheet) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(13.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Use my current location", fontSize = 10.5.sp, color = SafeRouteTeal, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick(scaleDown = 0.95f) {
                                        coroutineScope.launch {
                                            sheetLat = mapCenterLat
                                            sheetLng = mapCenterLng
                                            val resolved = GeoapifyService.reverseGeocode(mapCenterLat, mapCenterLng, context)
                                            sheetAddress = resolved?.ifBlank { null }
                                                ?: String.format(java.util.Locale.US, "%.4f, %.4f", mapCenterLat, mapCenterLng)
                                        }
                                    }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SafeRouteBlue.copy(alpha = 0.12f))
                                    .border(1.dp, SafeRouteBlue.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafeRouteBlue, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick map center", fontSize = 10.5.sp, color = SafeRouteBlue, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }

                        if (sheetLat != null && sheetLng != null) {
                            Text(
                                text = "Location: ${String.format(java.util.Locale.US, "%.4f, %.4f", sheetLat!!, sheetLng!!)}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Button(
                            onClick = {
                                if (canSaveSheet) {
                                    if (sheetPlace.id != 0L) {
                                        onUpdateSavedPlace(
                                            sheetPlace.copy(
                                                title = sheetTitle.trim(),
                                                address = sheetAddress.trim(),
                                                latitude = sheetLat!!,
                                                longitude = sheetLng!!
                                            )
                                        )
                                    } else {
                                        onSaveSavedPlace(sheetTitle.trim(), sheetPlace.iconName, sheetAddress.trim(), sheetLat!!, sheetLng!!)
                                    }
                                    addressSheetEntry = null
                                }
                            },
                            enabled = canSaveSheet,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafeRouteTeal,
                                contentColor = Color(0xFF04212B)
                            )
                        ) {
                            Text(if (sheetPlace.id == 0L) "Save Place" else "Update Place", fontWeight = FontWeight.Bold)
                        }

                        if (!canSaveSheet) {
                            Text(
                                text = "Pick a location via search, GPS or map center to enable saving",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // DELETE SAVED PLACE CONFIRMATION
            if (pendingDeletePlace != null) {
                val placeToDelete = pendingDeletePlace!!
                AlertDialog(
                    onDismissRequest = { pendingDeletePlace = null },
                    title = { Text("Delete \"${placeToDelete.title}\"?") },
                    text = { Text("This saved place will be removed from your account.") },
                    confirmButton = {
                        TextButton(onClick = {
                            onDeleteSavedPlace(placeToDelete)
                            deletedPlaceForUndo = placeToDelete
                            pendingDeletePlace = null
                        }) {
                            Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeletePlace = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. RECENT TRIPS (Compact Horizontal Scroll Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LanguageManager.get("recent_trips"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("No recent trips yet — search above to start", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(recentTrips) { trip ->
                        Box(
                            modifier = Modifier
                                .width(210.dp)
                                .bounceClick(scaleDown = 0.96f) {
                                    onSelectRoute(trip.originName, trip.destinationName, emptyList(), null, null)
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkNavyCard)
                                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${trip.distanceKm} km • ${trip.durationSeconds / 60}m",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    SafetyBadge(score = trip.avgSafetyScore, showLabel = false)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = trip.destinationName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "From: ${trip.originName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. GROUP TRIP MODE (Compact Sleek Banner)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(scaleDown = 0.97f) { showGroupTripDialog = true }
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SafeRouteTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Group Trip",
                                tint = SafeRouteTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = LanguageManager.get("group_trip"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = LanguageManager.get("group_trip_subtitle"),
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeRouteBlue)
                            .clickable {
                                onStartGroupTrip()
                                showGroupTripDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(LanguageManager.get("share_code"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Generous spacing above fixed bottom navigation bar and persistent floating SOS button
            Spacer(modifier = Modifier.height(84.dp))
        }

        // GROUP TRIP INVITE & STATUS DIALOG
        if (showGroupTripDialog) {
            AlertDialog(
                onDismissRequest = { showGroupTripDialog = false },
                containerColor = DarkNavySurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = SafeRouteTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LanguageManager.get("group_trip"), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = LanguageManager.get("group_trip_subtitle"),
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkNavyCard)
                                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = LanguageManager.get("invite_code"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = activeGroupTrip.inviteCode.ifEmpty { "SAFEROUTE-CBE-9842" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = SafeRouteBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = LanguageManager.get("active_members"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val displayMembers = activeGroupTrip.members.ifEmpty {
                            listOf(
                                com.example.saferoute.data.model.GroupMember("m1", "Priya", "+91 98765 43210", "P", 92),
                                com.example.saferoute.data.model.GroupMember("m2", "Anand", "+91 98765 11223", "A", 84),
                                com.example.saferoute.data.model.GroupMember("m3", "Karthik", "+91 98765 99887", "K", 68)
                            )
                        }

                        displayMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(SafeRouteBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(member.avatarInitials, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(member.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                }
                                SafetyBadge(score = member.currentSafetyScore, showLabel = false)
                            }
                        }
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeRouteBlue)
                            .clickable {
                                onStartGroupTrip()
                                showGroupTripDialog = false
                                onSelectRoute(sourceQuery.ifBlank { "My Location" }, "TIDEL Park, Peelamedu, Coimbatore", emptyList(), selectedSourceCoord, null)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(LanguageManager.get("start_navigation"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGroupTripDialog = false }) {
                        Text(LanguageManager.get("cancel"), color = Color(0xFF94A3B8))
                    }
                }
            )
        }
    }
}
