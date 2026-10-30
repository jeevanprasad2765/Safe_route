package com.example

import com.example.saferoute.data.model.DataConfidence
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.PlaceCategory
import com.example.saferoute.data.model.RouteCorridorData
import com.example.saferoute.data.model.RoutePlace
import com.example.saferoute.data.model.RouteSegment
import com.example.saferoute.data.model.TimeOfDay
import com.example.saferoute.safety.SafetyCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCalculatorTest {

    @Test
    fun testTwoRoutesWithDifferentGeometryGetDifferentScores() {
        val incidentZones = listOf(
            IncidentZone(
                id = "iz_1",
                latitude = 11.0210,
                longitude = 76.9560,
                radiusMeters = 200.0,
                severity = 4,
                category = "Low Lighting / Isolated",
                timestamp = "Recent"
            )
        )

        // Route A: Primary Main Arterial Road (High Lighting, High Crowd, Main Road, No Incident)
        val routeASegments = listOf(
            RouteSegment(
                id = "seg_a1",
                name = "Avinashi Road Expressway",
                distanceMeters = 2500,
                isMainRoad = true,
                lightingScore = 98f,
                crowdDensity = 92f,
                incidentCount = 0,
                startLat = 11.0168,
                startLng = 76.9558,
                endLat = 11.0250,
                endLng = 76.9800
            ),
            RouteSegment(
                id = "seg_a2",
                name = "Avinashi Road Corridor East",
                distanceMeters = 2800,
                isMainRoad = true,
                lightingScore = 95f,
                crowdDensity = 90f,
                incidentCount = 0,
                startLat = 11.0250,
                startLng = 76.9800,
                endLat = 11.0280,
                endLng = 77.0050
            )
        )

        // Route B: Local Alley & Cut-Through (Dim Lighting, Low Crowd, Not Main Road, Passes Incident Zone)
        val routeBSegments = listOf(
            RouteSegment(
                id = "seg_b1",
                name = "Back-Alley Service Track",
                distanceMeters = 1800,
                isMainRoad = false,
                lightingScore = 48f,
                crowdDensity = 35f,
                incidentCount = 1,
                startLat = 11.0205,
                startLng = 76.9555,
                endLat = 11.0230,
                endLng = 76.9700
            ),
            RouteSegment(
                id = "seg_b2",
                name = "Narrow Industrial Link",
                distanceMeters = 2200,
                isMainRoad = false,
                lightingScore = 42f,
                crowdDensity = 30f,
                incidentCount = 0,
                startLat = 11.0230,
                startLng = 76.9700,
                endLat = 11.0280,
                endLng = 77.0050
            )
        )

        val scoreA = SafetyCalculator.calculateRouteSafety(routeASegments, TimeOfDay.DUSK, incidentZones, "route_a")
        val scoreB = SafetyCalculator.calculateRouteSafety(routeBSegments, TimeOfDay.DUSK, incidentZones, "route_b")

        assertNotEquals("Composite safety scores must not be equal", scoreA.compositeScore, scoreB.compositeScore)
        assertTrue("Route A score (${scoreA.compositeScore}) should be substantially higher than Route B (${scoreB.compositeScore})", scoreA.compositeScore > scoreB.compositeScore + 15)
        assertTrue("Route A should fall into High Safety tier (>=80)", scoreA.compositeScore >= 80)
        assertTrue("Route B should fall into Caution/Risk tier (<60)", scoreB.compositeScore < 60)
    }

    @Test
    fun testRouteWithNoLitTagsYieldsNoDataForLighting() {
        val coords = listOf(Pair(11.0168, 76.9558), Pair(11.0200, 76.9600))
        val corridorWithNoLitTags = RouteCorridorData(
            places = emptyList(),
            litRatio = 0.0f,
            untaggedRatio = 1.0f,
            totalWaysCount = 10,
            litWaysCount = 0,
            unlitWaysCount = 0,
            untaggedWaysCount = 10,
            hasLightingData = false
        )

        val details = SafetyCalculator.calculateRouteSafetyFromCorridor(
            routeIndex = 0,
            routeCoordinates = coords,
            corridorData = corridorWithNoLitTags,
            timeOfDay = TimeOfDay.DUSK,
            incidentZones = emptyList(),
            routeId = "test_no_lit"
        )

        // Must NOT default to 100% or show a percentage
        assertNull("Lighting factor must be null when no lit tags exist", details.lightingFactor)
        assertEquals("Lighting data unavailable for most of this route", details.lightingFactText)
    }

    @Test
    fun testRouteWithNoBlackSpotDataYieldsNoIncidentData() {
        val coords = listOf(Pair(11.0168, 76.9558), Pair(11.0200, 76.9600))
        val corridor = RouteCorridorData(
            places = emptyList(),
            litRatio = 0.8f,
            untaggedRatio = 0.2f,
            totalWaysCount = 10,
            litWaysCount = 8,
            hasLightingData = true
        )

        val details = SafetyCalculator.calculateRouteSafetyFromCorridor(
            routeIndex = 0,
            routeCoordinates = coords,
            corridorData = corridor,
            timeOfDay = TimeOfDay.DUSK,
            incidentZones = emptyList(), // No black-spots in this area
            routeId = "test_no_incidents"
        )

        assertNull("Incident factor must be null when no black-spot data is available for area", details.incidentHistoryFactor)
        assertEquals("No incident data for this area", details.incidentFactText)
    }

    @Test
    fun testTwoDifferentRoutesYieldDifferentBreakdowns() {
        val coords1 = listOf(Pair(11.0168, 76.9558), Pair(11.0250, 76.9800))
        val coords2 = listOf(Pair(11.0110, 76.9620), Pair(11.0210, 76.9850))

        val blackspot = IncidentZone("bs_1", 11.0150, 76.9650, 200.0, 4, "Accident Zone", "2023")

        val corridor1 = RouteCorridorData(
            places = listOf(
                RoutePlace("p1", "GKNM Hospital", PlaceCategory.HOSPITAL, 11.0180, 76.9600, 30.0, 500.0, null, true, null, null, true, true),
                RoutePlace("p2", "Apollo Pharmacy", PlaceCategory.PHARMACY, 11.0190, 76.9650, 20.0, 900.0, null, true, null, null, false, true),
                RoutePlace("p3", "Nilgiris Supermarket", PlaceCategory.SHOP, 11.0200, 76.9700, 40.0, 1400.0, null, true, null, null, false, true)
            ),
            litRatio = 0.85f,
            untaggedRatio = 0.15f,
            totalWaysCount = 20,
            litWaysCount = 17,
            hasLightingData = true,
            openPlacesCount = 3,
            totalPlacesCount = 3,
            openShopsDensityPer100m = 0.8f,
            primaryHighwayClasses = listOf("primary", "trunk")
        )

        val corridor2 = RouteCorridorData(
            places = emptyList(),
            litRatio = 0.10f,
            untaggedRatio = 0.90f,
            totalWaysCount = 15,
            litWaysCount = 1,
            unlitWaysCount = 1,
            untaggedWaysCount = 13,
            hasLightingData = false,
            openPlacesCount = 0,
            totalPlacesCount = 0,
            primaryHighwayClasses = listOf("residential", "service"),
            curvySectionCount = 5
        )

        val details1 = SafetyCalculator.calculateRouteSafetyFromCorridor(
            routeIndex = 0,
            routeCoordinates = coords1,
            corridorData = corridor1,
            timeOfDay = TimeOfDay.DUSK,
            incidentZones = listOf(blackspot),
            routeId = "route_1"
        )

        val details2 = SafetyCalculator.calculateRouteSafetyFromCorridor(
            routeIndex = 1,
            routeCoordinates = coords2,
            corridorData = corridor2,
            timeOfDay = TimeOfDay.DUSK,
            incidentZones = listOf(blackspot),
            routeId = "route_2"
        )

        assertNotEquals("Route breakdowns must not have identical confidence", details1.dataConfidence, details2.dataConfidence)
        assertEquals("Route 1 should have high confidence", DataConfidence.HIGH, details1.dataConfidence)
        assertEquals("Route 2 should have low confidence due to missing lighting & activity", DataConfidence.LOW, details2.dataConfidence)
        assertTrue("Route 1 score must be > 0", details1.compositeScore > 0)
        assertEquals("Route 2 score must be withheld (0) due to low confidence", 0, details2.compositeScore)
        assertNotEquals(details1.lightingFactText, details2.lightingFactText)
        assertNotEquals(details1.activityFactText, details2.activityFactText)
    }
}
