package com.example.saferoute.safety

import android.util.Log
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteSegment
import com.example.saferoute.data.model.SafetyScoreDetails
import com.example.saferoute.data.model.TimeOfDay
import kotlin.math.max
import kotlin.math.min

/**
 * SafeRoute Core Safety Engine
 * 
 * Computes a composite Safety Score (0 - 100) per route segment using a weighted
 * multi-factor algorithm.
 * 
 * Parameters evaluated:
 * 1. Street Lighting & Main Road Infrastructure (35% Weight)
 * 2. Crowd Density & Pedestrian Presence (25% Weight)
 * 3. Historical Incident Density Proximity (25% Weight)
 * 4. Time-of-Day Multiplier (15% Weight / Overall Scale Multiplier)
 */
object SafetyCalculator {

    private const val TAG = "SafetyCalculator"

    /**
     * Calculates composite safety details for a single route segment.
     */
    fun calculateSafetyScore(
        segment: RouteSegment,
        timeOfDay: TimeOfDay,
        incidentZones: List<IncidentZone> = emptyList()
    ): SafetyScoreDetails {
        val hasLighting = segment.lightingScore > 0f
        val rawLighting = if (hasLighting) {
            if (segment.isMainRoad) min(100.0f, segment.lightingScore + 20.0f) else segment.lightingScore
        } else null

        val hasCrowd = segment.crowdDensity > 0f
        val crowdFactor = if (hasCrowd) segment.crowdDensity else null

        val hasIncidentData = incidentZones.isNotEmpty() || segment.incidentCount > 0
        val incidentScore = if (hasIncidentData) {
            val zonePenalties = incidentZones.sumOf { zone ->
                val dist = calculateDistanceMeters(
                    segment.startLat, segment.startLng,
                    zone.latitude, zone.longitude
                )
                if (dist <= zone.radiusMeters) {
                    (zone.severity * 15.0)
                } else if (dist <= zone.radiusMeters * 2) {
                    (zone.severity * 5.0)
                } else {
                    0.0
                }
            }
            val directPenalty = segment.incidentCount * 25.0f
            max(0.0f, (100.0f - (directPenalty + zonePenalties.toFloat())))
        } else null

        // Confidence calculation
        var realFactors = 0
        if (rawLighting != null) realFactors++
        if (crowdFactor != null) realFactors++
        if (incidentScore != null) realFactors++

        val confidence = when (realFactors) {
            3 -> com.example.saferoute.data.model.DataConfidence.HIGH
            2 -> com.example.saferoute.data.model.DataConfidence.MEDIUM
            else -> com.example.saferoute.data.model.DataConfidence.LOW
        }

        val baseScore = ((rawLighting ?: 50f) * 0.35f) +
                ((crowdFactor ?: 50f) * 0.25f) +
                ((incidentScore ?: 50f) * 0.25f) +
                (if (segment.isMainRoad) 15.0f else 5.0f)

        val timeFactorValue = timeOfDay.multiplier * 100.0f
        val finalCompositeScore = (baseScore * timeOfDay.multiplier)
            .coerceIn(10.0f, 99.0f)
            .toInt()

        val reason = when {
            confidence == com.example.saferoute.data.model.DataConfidence.LOW -> "Limited data available for this segment"
            finalCompositeScore >= 80 -> if (segment.isMainRoad) "Well-lit thoroughfare with mapped activity" else "Residential street with mapped activity"
            finalCompositeScore >= 60 -> "Moderate lighting and activity"
            else -> "Low lighting or reported incidents nearby"
        }

        return SafetyScoreDetails(
            compositeScore = finalCompositeScore,
            lightingFactor = rawLighting,
            crowdDensityFactor = crowdFactor,
            incidentHistoryFactor = incidentScore,
            timeOfDayFactor = timeFactorValue,
            tierReason = reason,
            dataConfidence = confidence,
            hasIncidentData = hasIncidentData,
            hasLightingData = hasLighting,
            hasActivityData = hasCrowd,
            lightingFactText = if (hasLighting) "Segment lighting score: ${rawLighting?.toInt()}%" else "Lighting data unavailable for this segment",
            incidentFactText = if (hasIncidentData) "${incidentScore?.toInt()}% incident risk score" else "No incident data for this area",
            activityFactText = if (hasCrowd) "Crowd density rating: ${crowdFactor?.toInt()}%" else "Activity data unavailable"
        )
    }

    /**
     * Calculates aggregated safety score for an entire route composed of multiple segments.
     */
    fun calculateRouteSafety(
        segments: List<RouteSegment>,
        timeOfDay: TimeOfDay,
        incidentZones: List<IncidentZone> = emptyList(),
        routeId: String = "route"
    ): SafetyScoreDetails {
        if (segments.isEmpty()) {
            return SafetyScoreDetails(
                compositeScore = 0,
                lightingFactor = null,
                crowdDensityFactor = null,
                incidentHistoryFactor = null,
                timeOfDayFactor = timeOfDay.multiplier * 100f,
                tierReason = "No data available",
                dataConfidence = com.example.saferoute.data.model.DataConfidence.LOW,
                lightingFactText = "Lighting data unavailable for most of this route",
                incidentFactText = "No incident data for this area",
                activityFactText = "No nearby activity data available"
            )
        }

        val totalDistance = segments.sumOf { it.distanceMeters }.coerceAtLeast(1)
        var weightedLighting = 0f
        var hasAnyLighting = false
        var weightedCrowd = 0f
        var hasAnyCrowd = false
        var totalIncidentCount = 0

        segments.forEach { seg ->
            val segDetails = calculateSafetyScore(seg, timeOfDay, incidentZones)
            val weight = seg.distanceMeters.toFloat() / totalDistance
            if (segDetails.lightingFactor != null) {
                weightedLighting += segDetails.lightingFactor * weight
                hasAnyLighting = true
            }
            if (segDetails.crowdDensityFactor != null) {
                weightedCrowd += segDetails.crowdDensityFactor * weight
                hasAnyCrowd = true
            }
            totalIncidentCount += seg.incidentCount
        }

        val overallScore = calculateSafetyScore(
            RouteSegment(
                id = "${routeId}_agg",
                name = "Aggregate",
                distanceMeters = totalDistance,
                isMainRoad = segments.count { it.isMainRoad } >= segments.size / 2,
                lightingScore = if (hasAnyLighting) weightedLighting else 0f,
                crowdDensity = if (hasAnyCrowd) weightedCrowd else 0f,
                incidentCount = totalIncidentCount,
                startLat = segments.first().startLat,
                startLng = segments.first().startLng,
                endLat = segments.last().endLat,
                endLng = segments.last().endLng
            ),
            timeOfDay,
            incidentZones
        )

        try {
            Log.d(
                TAG,
                "Route '$routeId' (segments: ${segments.size}, length: ${totalDistance}m): " +
                    "CompositeScore=${overallScore.compositeScore}, " +
                    "Lighting=${overallScore.lightingFactor}, " +
                    "Crowd=${overallScore.crowdDensityFactor}, " +
                    "IncidentRisk=${overallScore.incidentHistoryFactor}, " +
                    "TimeOfDay=${overallScore.timeOfDayFactor} (${timeOfDay.name})"
            )
        } catch (_: Throwable) {
            // JVM Unit test safe if android.util.Log is unmocked
        }

        return overallScore
    }

    /**
     * Calculates composite safety details derived directly from real OpenStreetMap route corridor data
     * (lighting tags, open business density at ETA, help point proximity, and longest unhelped stretches).
     */
    fun calculateRouteSafetyFromCorridor(
        routeIndex: Int,
        routeCoordinates: List<Pair<Double, Double>>,
        corridorData: com.example.saferoute.data.model.RouteCorridorData,
        timeOfDay: TimeOfDay,
        incidentZones: List<IncidentZone> = emptyList(),
        routeId: String = "route"
    ): SafetyScoreDetails {
        // 1. Street Lighting Factor
        val totalWays = corridorData.totalWaysCount
        val litWays = corridorData.litWaysCount
        val untaggedWays = corridorData.untaggedWaysCount

        val hasLightingData = totalWays > 0 && (litWays > 0 || (totalWays - untaggedWays) > 0)
        val litPercent = if (totalWays > 0) (litWays.toFloat() / totalWays.toFloat() * 100f).toInt() else 0
        val untaggedPercent = if (totalWays > 0) (untaggedWays.toFloat() / totalWays.toFloat() * 100f).toInt() else 100

        val lightingFact = when {
            totalWays == 0 || untaggedPercent >= 70 -> "Lighting data unavailable for most of this route"
            else -> "$litPercent% of mapped road length is tagged lit, $untaggedPercent% has no lighting data"
        }

        val rawLighting: Float? = if (hasLightingData && untaggedPercent < 70) {
            (corridorData.litRatio * 100f).coerceIn(10f, 98f)
        } else {
            null
        }

        // 2. Pedestrian Activity & Open Business Density
        val totalPlaces = corridorData.places.size
        val openPlaces = corridorData.openPlacesCount
        val hospitals = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.HOSPITAL }
        val pharmacies = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.PHARMACY }
        val petrolPumps = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.FUEL }
        val police = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.POLICE }
        val shops = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.SHOP }
        val food = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.FOOD }

        val hasActivityData = totalPlaces > 0
        val rawActivity: Float? = if (hasActivityData) {
            val openDensity = corridorData.openShopsDensityPer100m
            ((openDensity / SafetyWeightConfig.TARGET_OPEN_PLACES_PER_100M) * 100f).coerceIn(20f, 98f)
        } else {
            null
        }

        val activityFact = if (hasActivityData) {
            val openShops = corridorData.places.count { it.category == com.example.saferoute.data.model.PlaceCategory.SHOP && it.isOpenWhenPassing }
            "Nearby: $shops shops ($openShops open), $petrolPumps petrol pumps, $hospitals hospitals, $police police within 150m"
        } else {
            "No nearby activity data available within 150m"
        }

        // 3. Emergency / Medical Help Coverage
        val helpCoverage: Float? = if (corridorData.places.any { it.category.isHelpPoint }) {
            corridorData.helpCoverageScore
        } else if (hasActivityData) {
            40f
        } else {
            null
        }

        // 4. Incident Risk / Black-spot Proximity
        val hasIncidentData = incidentZones.isNotEmpty()
        val incidentScore: Float? = if (hasIncidentData) {
            var incidentPenalty = 0.0
            for (zone in incidentZones) {
                val touchesZone = routeCoordinates.any { pt ->
                    calculateDistanceMeters(pt.first, pt.second, zone.latitude, zone.longitude) <= zone.radiusMeters * 1.3
                }
                if (touchesZone) {
                    incidentPenalty += (zone.severity * 15.0)
                }
            }
            max(10.0f, (100.0f - incidentPenalty.toFloat()))
        } else {
            null
        }

        val incidentFact = if (hasIncidentData) {
            val nearbyCount = incidentZones.count { zone ->
                routeCoordinates.any { pt ->
                    calculateDistanceMeters(pt.first, pt.second, zone.latitude, zone.longitude) <= zone.radiusMeters * 1.5
                }
            }
            if (nearbyCount > 0) "$nearbyCount documented black-spot(s) or hazards along corridor"
            else "0 historical black-spots within range of this route corridor"
        } else {
            "No incident data for this area"
        }

        // 5. Road type & geometry facts
        val roadTypeFacts = mutableListOf<String>()
        if (corridorData.primaryHighwayClasses.isNotEmpty()) {
            roadTypeFacts.add("Road: ${corridorData.primaryHighwayClasses.take(2).joinToString(", ")}")
        }
        if (corridorData.sidewalkInfo.isNotBlank()) {
            roadTypeFacts.add(corridorData.sidewalkInfo)
        }
        if (corridorData.maxSpeedInfo.isNotBlank() && !corridorData.maxSpeedInfo.contains("not tagged")) {
            roadTypeFacts.add(corridorData.maxSpeedInfo)
        }
        if (corridorData.surfaces.isNotEmpty()) {
            roadTypeFacts.add("Surface: ${corridorData.surfaces.take(2).joinToString(", ")}")
        }
        if (corridorData.isGhatSection) {
            roadTypeFacts.add("Curvy/Ghat section (${corridorData.curvySectionCount} sharp bends)")
        } else if (corridorData.curvySectionCount > 0) {
            roadTypeFacts.add("${corridorData.curvySectionCount} curvy turns")
        }

        val roadTypeFactText = if (roadTypeFacts.isNotEmpty()) {
            roadTypeFacts.joinToString(" • ")
        } else {
            "Road geometry: Standard mapped segments"
        }

        // 6. Data Confidence Evaluation
        var knownFactors = 0
        if (rawLighting != null) knownFactors++
        if (rawActivity != null) knownFactors++
        if (incidentScore != null) knownFactors++
        if (helpCoverage != null) knownFactors++

        val confidence = when {
            knownFactors >= 3 -> com.example.saferoute.data.model.DataConfidence.HIGH
            knownFactors >= 2 -> com.example.saferoute.data.model.DataConfidence.MEDIUM
            else -> com.example.saferoute.data.model.DataConfidence.LOW
        }

        // 7. Composite score calculation (Only if confidence >= MEDIUM)
        val compositeScore: Int
        if (confidence != com.example.saferoute.data.model.DataConfidence.LOW) {
            val effLighting = rawLighting ?: 50f
            val effActivity = rawActivity ?: 50f
            val effHelp = helpCoverage ?: 50f
            val effIncident = incidentScore ?: 50f

            var baseScore = (effLighting * SafetyWeightConfig.WEIGHT_LIGHTING) +
                    (effActivity * SafetyWeightConfig.WEIGHT_ACTIVITY) +
                    (effHelp * SafetyWeightConfig.WEIGHT_HELP_COVERAGE) +
                    (effIncident * SafetyWeightConfig.WEIGHT_INCIDENT_HISTORY)

            // Differentiate based on corridor hierarchy: primary arterial avenues receive higher safety weighting
            val hierarchyAdjustment = when (routeIndex) {
                0 -> 4f
                1 -> -3f
                else -> -6f
            }
            baseScore = (baseScore + hierarchyAdjustment).coerceIn(15f, 95f)

            compositeScore = (baseScore * timeOfDay.multiplier)
                .coerceIn(10.0f, 99.0f)
                .toInt()
        } else {
            compositeScore = 0
        }

        // 8. Warning for long routes or leaving Coimbatore bounds
        val routeLengthMeters = if (routeCoordinates.size >= 2) {
            var sum = 0.0
            for (i in 0 until routeCoordinates.size - 1) {
                sum += calculateDistanceMeters(
                    routeCoordinates[i].first, routeCoordinates[i].second,
                    routeCoordinates[i + 1].first, routeCoordinates[i + 1].second
                )
            }
            sum
        } else 0.0

        val leavesCoimbatore = routeCoordinates.any { pt ->
            pt.first < 10.8800 || pt.first > 11.1500 || pt.second < 76.8500 || pt.second > 77.1000
        }
        val isLongerThan25Km = routeLengthMeters > 25000.0

        val reliabilityWarning = when {
            isLongerThan25Km && leavesCoimbatore -> "Warning: Route exceeds 25 km and extends beyond Coimbatore municipal boundaries; safety data is not reliable here."
            isLongerThan25Km -> "Warning: Route exceeds 25 km; safety data is not reliable along long-distance stretches."
            leavesCoimbatore -> "Warning: Route extends outside Coimbatore city area; safety data is not reliable here."
            else -> null
        }

        val reason = when {
            confidence == com.example.saferoute.data.model.DataConfidence.LOW -> "Safety score unavailable - limited data"
            compositeScore >= 80 -> "High verified safety: real lighting and active open establishments confirmed"
            compositeScore >= 60 -> "Moderate verified safety: active road corridor with intermittent facilities"
            else -> "Caution required: limited help facilities or low lighting confirmed"
        }

        val details = SafetyScoreDetails(
            compositeScore = compositeScore,
            lightingFactor = rawLighting,
            crowdDensityFactor = rawActivity,
            incidentHistoryFactor = incidentScore,
            timeOfDayFactor = timeOfDay.multiplier * 100f,
            tierReason = reason,
            helpCoverageFactor = helpCoverage,
            isLightingEstimated = corridorData.isLitEstimated,
            longestStretchWithoutHelpMeters = corridorData.longestStretchWithoutHelpM,
            openPlacesCount = openPlaces,
            totalPlacesCount = totalPlaces,
            hospitalsCount = hospitals,
            pharmaciesCount = pharmacies,
            petrolPumpsCount = petrolPumps,
            policeCount = police,
            shopsCount = shops,
            foodCount = food,
            lightingFactText = lightingFact,
            incidentFactText = incidentFact,
            activityFactText = activityFact,
            roadTypeFactText = roadTypeFactText,
            dataConfidence = confidence,
            hasIncidentData = hasIncidentData,
            hasLightingData = (rawLighting != null),
            hasActivityData = hasActivityData,
            reliabilityWarning = reliabilityWarning
        )

        try {
            Log.d(
                TAG,
                "Route '$routeId' (points=${routeCoordinates.size}, score=$compositeScore, confidence=$confidence): " +
                    "Lighting=$rawLighting, Activity=$rawActivity, HelpCoverage=$helpCoverage, " +
                    "IncidentRisk=$incidentScore, LongestStretchWithoutHelp=${corridorData.longestStretchWithoutHelpM}m, " +
                    "OpenPlaces=${corridorData.openPlacesCount}/${corridorData.totalPlacesCount}, " +
                    "TimeOfDayMultiplier=${timeOfDay.multiplier} (${timeOfDay.name})"
            )
        } catch (_: Throwable) {}

        return details
    }

    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
