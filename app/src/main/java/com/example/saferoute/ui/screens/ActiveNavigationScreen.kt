package com.example.saferoute.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.model.GroupTripSession
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.TurnInstruction
import com.example.saferoute.ui.components.LeafletMapView
import com.example.saferoute.ui.components.SosFab
import com.example.saferoute.util.LanguageManager
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
import kotlinx.coroutines.delay

@Composable
fun ActiveNavigationScreen(
    route: RouteOption,
    instructions: List<TurnInstruction>,
    emergencyContactName: String,
    contacts: List<EmergencyContactEntity> = emptyList(),
    incidentZones: List<IncidentZone> = emptyList(),
    activeGroupTrip: GroupTripSession = GroupTripSession(),
    sharedTripContacts: Set<String> = emptySet(),
    onToggleContactShare: (String) -> Unit = {},
    onStopAllSharing: () -> Unit = {},
    onSwitchRoute: () -> Unit = {},
    onTriggerSos: () -> Unit,
    onCompleteTrip: () -> Unit,
    onCancelTrip: () -> Unit
) {
    var currentInstructionIndex by remember { mutableIntStateOf(0) }
    var currentLegIndex by remember { mutableIntStateOf(0) }
    val totalLegs = if (route.legs.isNotEmpty()) route.legs.size else 1
    val currentLeg = route.legs.getOrNull(currentLegIndex)

    var currentSafetyScore by remember(currentLegIndex) {
        mutableIntStateOf(currentLeg?.safetyScore ?: route.safetyDetails.compositeScore)
    }
    var showShareContactDialog by remember { mutableStateOf(false) }

    // Simulated progress auto-advance turn instructions
    LaunchedEffect(Unit) {
        while (true) {
            delay(10000)
            if (currentInstructionIndex < instructions.size - 1) {
                currentInstructionIndex += 1
            }
        }
    }

    val instruction = instructions.getOrElse(currentInstructionIndex) { instructions.first() }

    val animatedHudColor by animateColorAsState(
        targetValue = when {
            currentSafetyScore >= 80 -> SafetyGreen
            currentSafetyScore >= 60 -> SafetyAmber
            else -> SafetyRed
        },
        animationSpec = tween(durationMillis = 600),
        label = "HudColor"
    )

    Box(
        modifier = Modifier
            .testTag("active_navigation_screen")
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // FULL SCREEN BACKGROUND RASTER GOOGLE-MAPS-STYLE STREET MAP
        LeafletMapView(
            routes = listOf(route),
            selectedRouteId = route.id,
            incidentZones = incidentZones,
            userLocation = route.pathCoordinates.firstOrNull() ?: Pair(11.0168, 76.9558),
            isNavigating = true,
            activeInstructionText = null,
            showControls = true,
            destinationName = route.name,
            modifier = Modifier.fillMaxSize()
        )

        // TOP NAVIGATION CONTAINER
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(14.dp)
        ) {
            // TURN INSTRUCTION FROSTED GLASS BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassSurfaceDark)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteTeal.copy(alpha = 0.2f))
                                    .border(1.dp, SafeRouteTeal.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TurnRight,
                                    contentDescription = "Turn Instruction",
                                    tint = SafeRouteTeal,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "In ${instruction.distanceMeters} meters",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = instruction.text,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .bounceClick(scaleDown = 0.88f) { onCancelTrip() }
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Navigation", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // LIVE SEGMENT SAFETY SCORE HUD
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22000000))
                            .border(1.dp, animatedHudColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Live Safety HUD",
                                tint = animatedHudColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentLeg != null && totalLegs > 1) {
                                    "Leg ${currentLegIndex + 1}/$totalLegs: ${currentLeg.toName} (${currentSafetyScore}/100)"
                                } else {
                                    "Safety Score: $currentSafetyScore/100 (${instruction.safetyStatus})"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = animatedHudColor
                            )
                        }
                    }
                }
            }

            // GROUP MEMBER ANOMALY ALERT BANNER
            val anomalyMember = activeGroupTrip.members.find { it.isStationaryAlert || it.isOffRoute }
            if (activeGroupTrip.isActive && anomalyMember != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SafetyRed.copy(alpha = 0.92f))
                        .padding(12.dp)
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
                            Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${anomalyMember.name} ${LanguageManager.get("stationary_alert")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(LanguageManager.get("call_member"), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // LOCATION SHARING & TRACKER AVATAR STACK BAR
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurfaceDark)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShareLocation,
                        contentDescription = "Live Sharing",
                        tint = SafeRouteTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    if (sharedTripContacts.isNotEmpty()) {
                        Text(
                            text = "Sharing with ${sharedTripContacts.size} contacts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeRouteTeal
                        )
                    } else {
                        Text(
                            text = "Sharing with $emergencyContactName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeRouteTeal
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Avatar Stack
                    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                        val activeAvatars = if (sharedTripContacts.isNotEmpty()) {
                            sharedTripContacts.take(3).toList()
                        } else {
                            listOf(emergencyContactName.take(1))
                        }
                        activeAvatars.forEach { name ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(SafeRouteBlue)
                                    .border(1.5.dp, DarkNavyCard, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.take(1).uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // "+ Share" button to manage multi-contact sharing
                    Box(
                        modifier = Modifier
                            .bounceClick(scaleDown = 0.92f) { showShareContactDialog = true }
                            .clip(RoundedCornerShape(10.dp))
                            .background(SafeRouteTeal.copy(alpha = 0.2f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Tracker", tint = SafeRouteTeal, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("+ ${LanguageManager.get("share_trip")}", fontSize = 10.sp, color = SafeRouteTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


        // PERSISTENT EMERGENCY SOS BUTTON (THUMB REACHABLE BOTTOM LEFT)
        SosFab(
            onClick = onTriggerSos,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 105.dp),
            expanded = false
        )

        // BOTTOM ETA FOOTER BAR (iOS Frosted Card)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassSurfaceDark)
                .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val legDist = currentLeg?.distanceKm ?: route.distanceKm
                    val legDur = currentLeg?.durationMinutes ?: route.durationMinutes
                    Text(
                        text = if (totalLegs > 1 && currentLeg != null) "Leg ${currentLegIndex + 1}/$totalLegs: $legDur min" else "${route.durationMinutes} min remaining",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = if (totalLegs > 1 && currentLeg != null) "To ${currentLeg.toName} • $legDist km" else "${route.distanceKm} km left • ETA 10:42 PM",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                if (totalLegs > 1 && currentLegIndex < totalLegs - 1) {
                    Box(
                        modifier = Modifier
                            .testTag("next_stop_button")
                            .bounceClick(scaleDown = 0.94f) { currentLegIndex += 1 }
                            .clip(RoundedCornerShape(16.dp))
                            .background(SafeRouteTeal)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Next Stop ➔",
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .testTag("end_trip_button")
                            .bounceClick(scaleDown = 0.94f) { onCompleteTrip() }
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(SafetyGreen, Color(0xFF24A148))
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = LanguageManager.get("arrived"),
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // MULTI-CONTACT SHARE TRIP SELECTION DIALOG
        if (showShareContactDialog) {
            AlertDialog(
                onDismissRequest = { showShareContactDialog = false },
                containerColor = DarkNavySurface,
                title = {
                    Text(
                        text = LanguageManager.get("share_trip"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Select emergency contacts to share live trip tracking with:",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (contacts.isEmpty()) {
                            Text(
                                text = "No emergency contacts yet. Add a contact in Profile first so they can receive your live trip.",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        contacts.forEach { contact ->
                            val isChecked = sharedTripContacts.contains(contact.name)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleContactShare(contact.name) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleContactShare(contact.name) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SafeRouteTeal,
                                        uncheckedColor = Color(0xFF64748B)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${contact.relationship} • ${contact.phone}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeRouteBlue)
                            .clickable { showShareContactDialog = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onStopAllSharing()
                        showShareContactDialog = false
                    }) {
                        Text(LanguageManager.get("stop_sharing"), color = SafetyRed)
                    }
                }
            )
        }
    }
}
