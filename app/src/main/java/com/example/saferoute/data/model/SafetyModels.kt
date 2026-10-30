package com.example.saferoute.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed

enum class TimeOfDay(val label: String, val multiplier: Float, val icon: String) {
    DAYTIME("Daytime (Sunlit)", 1.0f, "☀️"),
    DUSK("Dusk (Transition)", 0.85f, "🌅"),
    NIGHT("Night (Dark)", 0.70f, "🌙"),
    LATE_NIGHT("Late Night (High Alert)", 0.55f, "🌌")
}

data class IncidentZone(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val severity: Int, // 1 to 5
    val category: String, // e.g., "Poor Lighting", "Reported Theft", "Narrow Alleyway"
    val timestamp: String
)

data class RouteSegment(
    val id: String,
    val name: String,
    val distanceMeters: Int,
    val isMainRoad: Boolean,
    val lightingScore: Float, // 0 to 100
    val crowdDensity: Float,  // 0 to 100
    val incidentCount: Int,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double
)

enum class DataConfidence(val label: String, val color: Color) {
    HIGH("High", SafetyGreen),
    MEDIUM("Medium", SafetyAmber),
    LOW("Low", SafetyRed)
}

data class SafetyScoreDetails(
    val compositeScore: Int, // 0 to 100
    val lightingFactor: Float? = null, // 0 to 100 or null if no data
    val crowdDensityFactor: Float? = null, // 0 to 100 or null if no data
    val incidentHistoryFactor: Float? = null, // 0 to 100 or null if no data
    val timeOfDayFactor: Float = 85f, // 0 to 100
    val tierReason: String,
    val helpCoverageFactor: Float? = null, // 0 to 100 or null if no data
    val isLightingEstimated: Boolean = false,
    val longestStretchWithoutHelpMeters: Double = 0.0,
    val openPlacesCount: Int = 0,
    val totalPlacesCount: Int = 0,
    val hospitalsCount: Int = 0,
    val pharmaciesCount: Int = 0,
    val petrolPumpsCount: Int = 0,
    val policeCount: Int = 0,
    val shopsCount: Int = 0,
    val foodCount: Int = 0,
    val lightingFactText: String = "",
    val incidentFactText: String = "",
    val activityFactText: String = "",
    val roadTypeFactText: String = "",
    val dataConfidence: DataConfidence = DataConfidence.LOW,
    val hasIncidentData: Boolean = false,
    val hasLightingData: Boolean = false,
    val hasActivityData: Boolean = false,
    val reliabilityWarning: String? = null
) {
    val tier: SafetyTier
        get() = when {
            compositeScore >= 80 -> SafetyTier.HIGH_SAFETY
            compositeScore >= 60 -> SafetyTier.MEDIUM_SAFETY
            else -> SafetyTier.CAUTION_REQUIRED
        }
}

enum class SafetyTier(val title: String, val color: Color) {
    HIGH_SAFETY("High Safety", SafetyGreen),
    MEDIUM_SAFETY("Moderate Safety", SafetyAmber),
    CAUTION_REQUIRED("High Risk - Caution", SafetyRed)
}

data class Waypoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val isSource: Boolean = false,
    val isDestination: Boolean = false
)

data class RouteLeg(
    val legIndex: Int,
    val fromName: String,
    val toName: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val safetyScore: Int,
    val safetyTier: SafetyTier,
    val startLat: Double = 11.0,
    val startLng: Double = 76.95,
    val endLat: Double = 11.02,
    val endLng: Double = 77.00
)

data class RouteOption(
    val id: String,
    val name: String,
    val summary: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val safetyDetails: SafetyScoreDetails,
    val segments: List<RouteSegment>,
    val pathCoordinates: List<Pair<Double, Double>>,
    val isRecommended: Boolean = false,
    val warningNote: String = "",
    val sourceName: String = "My Location",
    val destinationName: String = "TIDEL Park, Peelamedu",
    val waypoints: List<Waypoint> = emptyList(),
    val legs: List<RouteLeg> = emptyList(),
    val turnInstructions: List<TurnInstruction> = emptyList(),
    val places: List<RoutePlace> = emptyList(),
    val corridorData: RouteCorridorData = RouteCorridorData(),
    val roadRiskDetails: com.example.saferoute.risk.RoadRiskScoreDetails? = null
)

data class TurnInstruction(
    val distanceMeters: Int,
    val text: String,
    val streetName: String,
    val iconType: String, // "turn_left", "turn_right", "straight", "arrive"
    val safetyStatus: String = "Well Lit"
)

data class GroupMember(
    val id: String,
    val name: String,
    val phone: String,
    val avatarInitials: String,
    val currentSafetyScore: Int,
    val distanceProgressMeters: Float = 0f,
    val isOffRoute: Boolean = false,
    val isStationaryAlert: Boolean = false,
    val relativeX: Float = 0f,
    val relativeY: Float = 0f
)

data class GroupTripSession(
    val inviteCode: String = "SAFEROUTE-CBE-9842",
    val isActive: Boolean = false,
    val members: List<GroupMember> = emptyList()
)

