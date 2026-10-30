package com.example.saferoute.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saferoute.data.model.GroupMember
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption

/**
 * High-Performance Google Maps Aubergine / Night Component for SafeRoute.
 * Implements strict Coimbatore coordinate boundary restrictions, custom dark
 * Night/Aubergine theme style, #00E676 safe corridor polylines, #FF5252 cautionary areas,
 * and clean Google Maps controls without CartoDB attribution or synthetic heatmap grids.
 */
@Composable
fun LeafletMapView(
    modifier: Modifier = Modifier,
    latitude: Double = 11.0168,
    longitude: Double = 76.9558,
    zoomLevel: Int = 13,
    isDarkTheme: Boolean = true,
    userLocation: Pair<Double, Double>? = Pair(11.0168, 76.9558),
    routes: List<RouteOption> = emptyList(),
    selectedRouteId: String? = null,
    incidentZones: List<IncidentZone> = emptyList(),
    groupMembers: List<GroupMember> = emptyList(),
    autoDetectLocation: Boolean = false,
    onLocationDetected: ((Double, Double) -> Unit)? = null,
    onLocationError: ((reason: String, message: String) -> Unit)? = null,
    onMapError: (() -> Unit)? = null,
    onMapClicked: ((Double, Double) -> Unit)? = null,
    onRouteSelected: ((String) -> Unit)? = null,
    isNavigating: Boolean = false,
    activeInstructionText: String? = null,
    showControls: Boolean = true,
    destinationName: String? = null,
    onCenterChanged: ((Double, Double) -> Unit)? = null
) {
    GoogleMapView(
        routes = routes,
        selectedRouteId = selectedRouteId,
        onRouteSelected = { routeId ->
            onRouteSelected?.invoke(routeId)
        },
        incidentZones = incidentZones,
        modifier = modifier.fillMaxSize(),
        latitude = latitude,
        longitude = longitude,
        userLocation = userLocation,
        isNavigating = isNavigating,
        activeInstructionText = activeInstructionText,
        groupMembers = groupMembers,
        showControls = showControls,
        destinationName = destinationName,
        onCenterChanged = onCenterChanged
    )
}

