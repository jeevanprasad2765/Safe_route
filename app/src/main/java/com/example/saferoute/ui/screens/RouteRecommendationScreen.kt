package com.example.saferoute.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.saferoute.data.model.PlaceCategory
import com.example.saferoute.util.appText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.ui.components.LeafletMapView
import com.example.saferoute.ui.components.SafetyBadge
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed
import com.example.ui.theme.bounceClick

private fun truncateAtWordBoundary(text: String, maxChars: Int = 26): String {
    if (text.length <= maxChars) return text
    val sub = text.take(maxChars)
    val lastSpace = sub.lastIndexOf(' ')
    val clean = if (lastSpace > 10) sub.substring(0, lastSpace) else sub
    return clean.trimEnd(',', '.', '-', ' ', '(', ')') + "…"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteRecommendationScreen(
    sourceName: String = "My Location",
    destinationName: String = "TIDEL Park, Peelamedu",
    waypoints: List<String> = emptyList(),
    routes: List<RouteOption>,
    selectedRouteId: String,
    prioritizeSafety: Boolean,
    incidentZones: List<IncidentZone>,
    onUpdateMultiStopRoute: (source: String, destination: String, waypoints: List<String>) -> Unit = { _, _, _ -> },
    onPrioritizeSafetyToggle: (Boolean) -> Unit,
    onRouteSelected: (String) -> Unit,
    onOpenSafetyDetails: (RouteOption) -> Unit,
    onBack: () -> Unit
) {
    var currentSource by remember { mutableStateOf(sourceName) }
    var currentDestination by remember { mutableStateOf(destinationName) }
    var currentStops by remember { mutableStateOf(waypoints) }

    var isSourceFocused by remember { mutableStateOf(false) }
    var isDestFocused by remember { mutableStateOf(false) }
    var fullAddressDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    val selectedRoute = routes.find { it.id == selectedRouteId } ?: routes.firstOrNull()

    // Dialog showing full untruncated address when tapped
    fullAddressDialogInfo?.let { (title, address) ->
        AlertDialog(
            onDismissRequest = { fullAddressDialogInfo = null },
            containerColor = DarkNavyCard,
            title = {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = address,
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { fullAddressDialogInfo = null }) {
                    Text("OK", color = SafeRouteTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .testTag("route_recommendation_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
    ) {
        // TOP HEADER BAR WITH SOURCE, DESTINATION & MULTI-STOP CONTROLS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkNavySurface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick(scaleDown = 0.9f) { onBack() }
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Route Intelligence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Source field: Word-boundary truncation with ellipsis when not focused; full text editable when focused
                                OutlinedTextField(
                                    value = if (isSourceFocused) currentSource else truncateAtWordBoundary(currentSource, 26),
                                    onValueChange = {
                                        currentSource = it
                                        onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                    },
                                    label = { Text("Starting point", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = SafetyGreen, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { fullAddressDialogInfo = "Starting point" to currentSource },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "View full address",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("source_input_field")
                                        .onFocusChanged { isSourceFocused = it.isFocused },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = SafeRouteTeal,
                                        unfocusedIndicatorColor = Color(0x22FFFFFF)
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Destination field: Word-boundary truncation with ellipsis when not focused; full text editable when focused
                                OutlinedTextField(
                                    value = if (isDestFocused) currentDestination else truncateAtWordBoundary(currentDestination, 26),
                                    onValueChange = {
                                        currentDestination = it
                                        onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                    },
                                    label = { Text("Destination", fontSize = 11.sp, color = Color(0xFF64748B)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = SafetyRed, modifier = Modifier.size(16.dp))
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { fullAddressDialogInfo = "Destination" to currentDestination },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "View full address",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("destination_input_field")
                                        .onFocusChanged { isDestFocused = it.isFocused },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = SafeRouteBlue,
                                        unfocusedIndicatorColor = Color(0x22FFFFFF)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Swap Source and Destination button
                            Box(
                                modifier = Modifier
                                    .testTag("swap_source_dest_button")
                                    .size(38.dp)
                                    .bounceClick(scaleDown = 0.88f) {
                                        val temp = currentSource
                                        currentSource = currentDestination
                                        currentDestination = temp
                                        onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                    }
                                    .clip(CircleShape)
                                    .background(SafeRouteBlue.copy(alpha = 0.15f))
                                    .border(1.dp, SafeRouteBlue.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Swap source and destination",
                                    tint = SafeRouteBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Intermediate Stops (Waypoints) List
                        currentStops.forEachIndexed { index, stopText ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(SafeRouteBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${index + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = stopText,
                                    onValueChange = { newText ->
                                        val mutable = currentStops.toMutableList()
                                        mutable[index] = newText
                                        currentStops = mutable
                                        onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                    },
                                    placeholder = { Text("Stop ${index + 1}", fontSize = 12.sp, color = Color(0xFF64748B)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("stop_input_$index"),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedIndicatorColor = SafeRouteTeal,
                                        unfocusedIndicatorColor = Color(0x22FFFFFF)
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentStops.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index - 1, item)
                                            currentStops = mutable
                                            onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                                    }
                                }
                                if (index < currentStops.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val mutable = currentStops.toMutableList()
                                            val item = mutable.removeAt(index)
                                            mutable.add(index + 1, item)
                                            currentStops = mutable
                                            onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val mutable = currentStops.toMutableList()
                                        mutable.removeAt(index)
                                        currentStops = mutable
                                        onUpdateMultiStopRoute(currentSource, currentDestination, currentStops)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Stop", tint = SafetyRed, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // "+ Add stop" Button
                        Row(
                            modifier = Modifier
                                .testTag("add_stop_button")
                                .bounceClick(scaleDown = 0.94f) {
                                    val mutable = currentStops.toMutableList()
                                    mutable.add("")
                                    currentStops = mutable
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(SafeRouteTeal.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SafeRouteTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add stop", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SafeRouteTeal)
                        }
                    }
                }
            }
        }

        // BOUNDED MAP PREVIEW CARD (Properly constrained within its card boundary, no overflow, clipped)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DarkNavyCard)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
                .clipToBounds()
        ) {
            LeafletMapView(
                routes = routes,
                selectedRouteId = selectedRouteId,
                incidentZones = incidentZones,
                userLocation = routes.firstOrNull()?.pathCoordinates?.firstOrNull() ?: Pair(11.0168, 76.9558),
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )

            // SAFETY VS SPEED TOGGLE PILL (Contained cleanly inside map card at top)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurfaceDark)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Safety Toggle
                    Box(
                        modifier = Modifier
                            .testTag("toggle_prioritize_safety")
                            .bounceClick(scaleDown = 0.92f) { onPrioritizeSafetyToggle(true) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (prioritizeSafety) SafeRouteTeal else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (prioritizeSafety) Color.Black else Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Safety",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (prioritizeSafety) Color.Black else Color.White
                            )
                        }
                    }

                    // Speed Toggle
                    Box(
                        modifier = Modifier
                            .testTag("toggle_prioritize_speed")
                            .bounceClick(scaleDown = 0.92f) { onPrioritizeSafetyToggle(false) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!prioritizeSafety) SafeRouteBlue else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Speed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // CONDENSED, HIGH-READABILITY ROUTE CARDS LIST (Fills remaining height, ample spacing)
        val modelMeta = remember {
            try {
                com.example.SafeRouteApplication.riskRepository.getModelMeta()
            } catch (_: Throwable) {
                null
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (modelMeta?.isTrained == false) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SafeRouteTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = appText("model_not_trained_banner"),
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            items(routes) { route ->
                val isSelected = route.id == selectedRouteId

                Box(
                    modifier = Modifier
                        .testTag("route_card_${route.id}")
                        .fillMaxWidth()
                        .bounceClick(scaleDown = 0.97f) {
                            onRouteSelected(route.id)
                            onOpenSafetyDetails(route)
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) SafeRouteBlue.copy(alpha = 0.15f) else DarkNavyCard)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) SafeRouteBlue else GlassSurfaceBorder,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        // 1 & 2: Time + Distance (large, primary) & Safety Badge (top-right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${route.durationMinutes} mins",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = (-0.3).sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${route.distanceKm} km)",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            SafetyBadge(
                                score = route.safetyDetails.compositeScore,
                                confidence = route.safetyDetails.dataConfidence
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Data Confidence Badge
                        com.example.saferoute.ui.components.DataConfidenceBadge(
                            confidence = route.safetyDetails.dataConfidence
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 3: Route / Road name (bold, one line, ellipsis)
                        Text(
                            text = route.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Road-Accident Risk (Distinct from personal safety)
                        val roadRisk = route.roadRiskDetails
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Road-accident risk: ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8)
                            )
                            if (roadRisk != null && roadRisk.isAvailable) {
                                Text(
                                    text = roadRisk.roadRiskLevel.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = roadRisk.roadRiskLevel.color
                                )
                                if (roadRisk.topReasons.isNotEmpty()) {
                                    Text(
                                        text = " (${roadRisk.topReasons.joinToString(", ")})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "Unavailable (limited data)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 4: Personal safety night descriptor (one line max, truncate with ellipsis)
                        Text(
                            text = "Personal safety (night): " + route.safetyDetails.tierReason,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 4b: Real Places Along Route Summary Chips
                        val sd = route.safetyDetails
                        val summaryChips = remember(sd, route.places) {
                            val list = mutableListOf<String>()
                            if (sd.hospitalsCount > 0) list.add("${sd.hospitalsCount} ${if (sd.hospitalsCount == 1) "hospital" else "hospitals"}")
                            if (sd.pharmaciesCount > 0) list.add("${sd.pharmaciesCount} ${if (sd.pharmaciesCount == 1) "pharmacy" else "pharmacies"}")
                            if (sd.petrolPumpsCount > 0) list.add("${sd.petrolPumpsCount} ${if (sd.petrolPumpsCount == 1) "petrol pump" else "petrol pumps"}")
                            if (sd.shopsCount > 0) {
                                val openShopCount = route.places.count { it.category == PlaceCategory.SHOP && it.isOpenNow }
                                list.add("${sd.shopsCount} ${if (sd.shopsCount == 1) "shop" else "shops"} ($openShopCount open)")
                            } else if (sd.openPlacesCount > 0) {
                                list.add("${sd.openPlacesCount} open places")
                            }
                            if (sd.policeCount > 0) list.add("${sd.policeCount} police")
                            if (sd.foodCount > 0) list.add("${sd.foodCount} food")
                            list
                        }

                        if (summaryChips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                summaryChips.forEach { chipText ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        border = BorderStroke(0.5.dp, Color(0xFF334155))
                                    ) {
                                        Text(
                                            text = chipText,
                                            fontSize = 11.sp,
                                            color = Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        route.safetyDetails.reliabilityWarning?.let { warning ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = com.example.ui.theme.SafetyRed.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, com.example.ui.theme.SafetyRed.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = warning,
                                    fontSize = 11.sp,
                                    color = com.example.ui.theme.SafetyRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 5 & Highlight: Recommended Safe Route badge + Safety Intelligence link
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (route.isRecommended) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SafetyGreen.copy(alpha = 0.2f))
                                        .border(1.dp, SafetyGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "RECOMMENDED SAFE ROUTE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SafetyGreen
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    onRouteSelected(route.id)
                                    onOpenSafetyDetails(route)
                                }
                            ) {
                                Text(
                                    text = "Safety Intelligence",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SafeRouteTeal
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = SafeRouteTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
