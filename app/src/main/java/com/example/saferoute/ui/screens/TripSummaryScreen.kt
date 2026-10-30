package com.example.saferoute.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.SafeRouteBlue
import com.example.ui.theme.SafeRouteTeal
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.bounceClick

@Composable
fun TripSummaryScreen(
    route: RouteOption,
    durationSeconds: Long,
    distanceKm: Double,
    avgSafetyScore: Int,
    incidentZones: List<IncidentZone>,
    onSaveFeedback: (rating: Int, feedback: String) -> Unit,
    onDone: () -> Unit
) {
    var userRating by remember { mutableIntStateOf(5) }
    var feedbackText by remember { mutableStateOf("") }
    var submittedFeedback by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .testTag("trip_summary_screen")
            .fillMaxSize()
            .background(DarkNavyBackground)
            .verticalScroll(rememberScrollState())
    ) {
        // TOP CELEBRATION HEADER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(SafetyGreen.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(SafetyGreen.copy(alpha = 0.2f))
                        .border(2.dp, SafetyGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SafetyGreen,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Arrived Safely!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Trip completed via ${route.name}",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // MAP SNAPSHOT OF ROUTE TAKEN
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            val firstCoord = route.pathCoordinates.firstOrNull() ?: Pair(11.0072, 76.9515)
            LeafletMapView(
                routes = listOf(route),
                selectedRouteId = route.id,
                incidentZones = incidentZones,
                latitude = firstCoord.first,
                longitude = firstCoord.second,
                zoomLevel = 14,
                autoDetectLocation = false
            )
        }

        Column(modifier = Modifier.padding(18.dp)) {
            // TRIP STATS METRICS ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Duration", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${durationSeconds / 60}m", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Distance", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$distanceKm km", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("Avg Safety", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(4.dp))
                        SafetyBadge(score = avgSafetyScore, showLabel = false)
                    }
                }
            }

            // MULTI-LEG SUMMARY BREAKDOWN
            if (route.legs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Leg Breakdown (${route.legs.size} Legs Traveled)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        route.legs.forEach { leg ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkNavySurface)
                                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Leg ${leg.legIndex}: ${leg.fromName} ➔ ${leg.toName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${leg.distanceKm} km • ${leg.durationMinutes} mins",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    SafetyBadge(score = leg.safetyScore, showLabel = false)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SAFETY SCORE TREND SPARKLINE — only when the trip has >= 2 real leg scores
            val trendPoints = route.legs.map { it.safetyScore.toFloat() }
            if (trendPoints.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkNavyCard)
                        .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Safety Score Segment Trend",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            val widthStep = size.width / (trendPoints.size - 1)
                            val path = Path()

                            trendPoints.forEachIndexed { i, pt ->
                                val x = i * widthStep
                                val y = size.height - ((pt - 50f) / 50f * size.height)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }

                            drawPath(
                                path = path,
                                color = SafetyGreen,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // POST TRIP MICRO SURVEY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkNavyCard)
                    .border(1.dp, GlassSurfaceBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "How safe did this route feel?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 5-Star Rating Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (star in 1..5) {
                            Box(
                                modifier = Modifier
                                    .bounceClick(scaleDown = 0.85f) { userRating = star }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= userRating) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = "$star Star",
                                    tint = if (star <= userRating) Color(0xFFFFD166) else Color(0xFF64748B),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!submittedFeedback) {
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            placeholder = { Text("Optional note (e.g. well-lit street)", fontSize = 12.sp, color = Color(0xFF64748B)) },
                            modifier = Modifier
                                .testTag("trip_feedback_input")
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 2,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkNavySurface,
                                unfocusedContainerColor = DarkNavySurface,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = SafeRouteTeal,
                                unfocusedIndicatorColor = Color(0x22FFFFFF)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.94f) {
                                    submittedFeedback = true
                                    onSaveFeedback(userRating, feedbackText)
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafeRouteTeal)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text("Submit Rating", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 13.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SafetyGreen.copy(alpha = 0.15f))
                                .border(1.dp, SafetyGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Feedback recorded • Thank you!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SafetyGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // RETURN TO HOME DASHBOARD
            Box(
                modifier = Modifier
                    .testTag("done_trip_summary_button")
                    .fillMaxWidth()
                    .height(52.dp)
                    .bounceClick(scaleDown = 0.95f) { onDone() }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(SafeRouteBlue, SafeRouteTeal)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("RETURN TO HOME DASHBOARD", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}
