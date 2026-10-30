package com.example.saferoute.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.model.GroupTripSession
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.TurnInstruction
import com.example.saferoute.util.LanguageManager
import com.example.saferoute.util.LocalAppLanguage
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.GlassSurfaceBorder
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.bounceClick

@Composable
fun NavigateScreen(
    activeRoute: RouteOption?,
    instructions: List<TurnInstruction> = emptyList(),
    emergencyContactName: String = "Emergency Contact",
    contacts: List<EmergencyContactEntity> = emptyList(),
    incidentZones: List<IncidentZone> = emptyList(),
    activeGroupTrip: GroupTripSession = GroupTripSession(),
    sharedTripContacts: Set<String> = emptySet(),
    onToggleContactShare: (String) -> Unit = {},
    onStopAllSharing: () -> Unit = {},
    onSwitchRoute: () -> Unit = {},
    onTriggerSos: () -> Unit = {},
    onCompleteTrip: () -> Unit = {},
    onCancelTrip: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onStartRoute: (RouteOption) -> Unit = {}
) {
    val lang = LocalAppLanguage.current

    if (activeRoute != null) {
        // Active Turn-by-Turn Navigation Screen
        ActiveNavigationScreen(
            route = activeRoute,
            instructions = if (instructions.isNotEmpty()) instructions else listOf(
                TurnInstruction(150, "Head east on current corridor", "Avinashi Corridor", "straight", "Well Lit"),
                TurnInstruction(350, "Turn right onto Avinashi Road Safe Corridor", "Avinashi Road", "turn_right", "High Illumination"),
                TurnInstruction(1200, "Continue straight towards TIDEL Park", "IT Corridor", "straight", "Monitored Zone"),
                TurnInstruction(50, "Arrived at Destination", "TIDEL Park", "arrive", "Safe Zone")
            ),
            emergencyContactName = emergencyContactName,
            contacts = contacts,
            incidentZones = incidentZones,
            activeGroupTrip = activeGroupTrip,
            sharedTripContacts = sharedTripContacts,
            onToggleContactShare = onToggleContactShare,
            onStopAllSharing = onStopAllSharing,
            onSwitchRoute = onSwitchRoute,
            onTriggerSos = onTriggerSos,
            onCompleteTrip = onCompleteTrip,
            onCancelTrip = onCancelTrip
        )
    } else {
        // Distinct Empty State for Navigate Tab when no trip is active
        Box(
            modifier = Modifier
                .testTag("navigate_empty_state_screen")
                .fillMaxSize()
                .background(DarkNavyBackground)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(modifier = Modifier.height(30.dp))

                    // Animated Glowing Navigation Radar Icon
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SafeRouteTeal.copy(alpha = 0.25f),
                                        SafeRouteBlue.copy(alpha = 0.10f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.5.dp, SafeRouteTeal.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(SafeRouteBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = LanguageManager.get("no_active_nav_title", lang),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = LanguageManager.get("no_active_nav_desc", lang),
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Primary Call-To-Action: Go to Home Search
                    Button(
                        onClick = onNavigateToHome,
                        modifier = Modifier
                            .testTag("navigate_search_home_cta")
                            .fillMaxWidth()
                            .height(52.dp)
                            .bounceClick(scaleDown = 0.95f) {},
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(SafeRouteBlue, SafeRouteTeal)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = LanguageManager.get("search_dest_from_home", lang),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // Quick-Start Corridor Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SafetyGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = LanguageManager.get("instant_safe_corridors", lang),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Preset Quick-Start Corridors
                val quickCorridors = listOf(
                    RouteOption(
                        id = "opt_quick_avinashi",
                        name = "Avinashi Road Safe Corridor",
                        summary = "Gandhipuram ➔ TIDEL Park, Peelamedu",
                        distanceKm = 5.4,
                        durationMinutes = 16,
                        safetyDetails = com.example.saferoute.data.model.SafetyScoreDetails(
                            compositeScore = 0,
                            lightingFactor = null,
                            crowdDensityFactor = null,
                            incidentHistoryFactor = null,
                            tierReason = "Select route to query live OpenStreetMap data",
                            dataConfidence = com.example.saferoute.data.model.DataConfidence.LOW,
                            lightingFactText = "Lighting data unavailable until route queried",
                            incidentFactText = "No incident data loaded",
                            activityFactText = "Activity data unavailable"
                        ),
                        segments = emptyList(),
                        pathCoordinates = listOf(
                            Pair(11.0089, 76.9507),
                            Pair(11.0150, 76.9720),
                            Pair(11.0185, 76.9820),
                            Pair(11.0210, 76.9910),
                            Pair(11.0250, 77.0080),
                            Pair(11.0270, 77.0200),
                            Pair(11.0253, 77.0345)
                        ),
                        isRecommended = true,
                        sourceName = "Gandhipuram",
                        destinationName = "TIDEL Park, Peelamedu"
                    ),
                    RouteOption(
                        id = "opt_quick_racecourse",
                        name = "Race Course Loop",
                        summary = "Promenade ➔ Railway Central",
                        distanceKm = 3.2,
                        durationMinutes = 10,
                        safetyDetails = com.example.saferoute.data.model.SafetyScoreDetails(
                            compositeScore = 0,
                            lightingFactor = null,
                            crowdDensityFactor = null,
                            incidentHistoryFactor = null,
                            tierReason = "Select route to query live OpenStreetMap data",
                            dataConfidence = com.example.saferoute.data.model.DataConfidence.LOW,
                            lightingFactText = "Lighting data unavailable until route queried",
                            incidentFactText = "No incident data loaded",
                            activityFactText = "Activity data unavailable"
                        ),
                        segments = emptyList(),
                        pathCoordinates = listOf(
                            Pair(11.0016, 76.9740),
                            Pair(11.0040, 76.9760),
                            Pair(10.9995, 76.9670)
                        ),
                        isRecommended = true,
                        sourceName = "Race Course",
                        destinationName = "Railway Central"
                    ),
                    RouteOption(
                        id = "opt_quick_dbroad",
                        name = "DB Road Boulevard",
                        summary = "RS Puram ➔ Saibaba Colony",
                        distanceKm = 2.8,
                        durationMinutes = 9,
                        safetyDetails = com.example.saferoute.data.model.SafetyScoreDetails(
                            compositeScore = 0,
                            lightingFactor = null,
                            crowdDensityFactor = null,
                            incidentHistoryFactor = null,
                            tierReason = "Select route to query live OpenStreetMap data",
                            dataConfidence = com.example.saferoute.data.model.DataConfidence.LOW,
                            lightingFactText = "Lighting data unavailable until route queried",
                            incidentFactText = "No incident data loaded",
                            activityFactText = "Activity data unavailable"
                        ),
                        segments = emptyList(),
                        pathCoordinates = listOf(
                            Pair(11.0072, 76.9515),
                            Pair(11.0180, 76.9500),
                            Pair(11.0280, 76.9450)
                        ),
                        isRecommended = false,
                        sourceName = "RS Puram",
                        destinationName = "Saibaba Colony"
                    )
                )

                items(quickCorridors) { corridor ->
                    Card(
                        modifier = Modifier
                            .testTag("quick_corridor_${corridor.id}")
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .bounceClick(scaleDown = 0.97f) {
                                onStartRoute(corridor)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GlassSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = corridor.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (corridor.safetyDetails.compositeScore > 0 && corridor.safetyDetails.dataConfidence != com.example.saferoute.data.model.DataConfidence.LOW) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(SafetyGreen.copy(alpha = 0.20f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${corridor.safetyDetails.compositeScore}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = SafetyGreen
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = corridor.summary,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteTeal.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Start",
                                    tint = SafeRouteTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
