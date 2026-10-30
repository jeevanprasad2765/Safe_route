package com.example.saferoute.risk

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SafetyRed

enum class RoadRiskLevel(val label: String, val color: Color) {
    LOW("Low", SafetyGreen),
    MEDIUM("Medium", SafetyAmber),
    HIGH("High", SafetyRed)
}

data class ModelMeta(
    val isTrained: Boolean,
    val reason: String? = null,
    val trainingDate: String? = null,
    val nSegments: Int = 0,
    val nPositive: Int = 0,
    val limitations: String = "Estimates based on past accident records and OpenStreetMap. Not a guarantee of safety."
)

data class RoadRiskScoreDetails(
    val roadRiskLevel: RoadRiskLevel,
    val meanRisk: Float,
    val p90Risk: Float,
    val highRiskShare: Float, // 0.0 to 1.0 (share of route length at High risk)
    val blackSpotsCount: Int,
    val confidence: Float, // 0.0 to 1.0 (share of route matched to scored segments)
    val topReasons: List<String> = emptyList(),
    val explanationText: String = "",
    val isAvailable: Boolean = true,
    val isHourlyWeightApplied: Boolean = false,
    val hourlyWeightAssumption: String? = null
)
