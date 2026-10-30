package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.saferoute.data.model.PlaceCategory
import com.example.saferoute.data.model.RouteCorridorData
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.RoutePlace
import com.example.saferoute.data.model.TimeOfDay
import com.example.saferoute.data.network.CorridorPlacesRepository
import com.example.saferoute.data.network.GeoapifyService
import com.example.saferoute.safety.SafetyCalculator
import com.example.saferoute.util.SosManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SafeRoute", appName)
  }

  @Test
  fun testPeelameduToSitraGeocodingAndRouting() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Geocode Peelamedu
    val peelamedu = GeoapifyService.geocodePlace("Peelamedu")
    if (peelamedu != null) {
      assertTrue("Peelamedu latitude should be around 11.00-11.06", peelamedu.latitude in 11.00..11.06)
      assertTrue("Peelamedu longitude should be around 76.95-77.05", peelamedu.longitude in 76.95..77.05)
    }

    // 2. Geocode Sitra
    val sitra = GeoapifyService.geocodePlace("Sitra")
    if (sitra != null) {
      assertTrue("Sitra latitude should be around 11.00-11.06", sitra.latitude in 11.00..11.06)
      assertTrue("Sitra longitude should be around 77.00-77.08", sitra.longitude in 77.00..77.08)
    }

    // 3. Test Geoapify routing from Peelamedu to Sitra
    val startPt = if (peelamedu != null) Pair(peelamedu.latitude, peelamedu.longitude) else Pair(11.0280, 77.0050)
    val endPt = if (sitra != null) Pair(sitra.latitude, sitra.longitude) else Pair(11.0330, 77.0380)
    val waypoints = listOf(startPt, endPt)
    val routes = GeoapifyService.fetchRealRoutes(
      waypoints = waypoints,
      originName = "Peelamedu",
      destinationName = "Sitra",
      context = context
    )

    if (routes.isNotEmpty()) {
      val selectedRoute: RouteOption = routes.first()
      assertFalse("Route path coordinates should not be empty", selectedRoute.pathCoordinates.isEmpty())

      val startCoord = selectedRoute.pathCoordinates.first()
      assertTrue("Start coordinate latitude should be near startPt", Math.abs(startCoord.first - startPt.first) < 0.1)
      assertTrue("Start coordinate longitude should be near startPt", Math.abs(startCoord.second - startPt.second) < 0.1)

      val endCoord = selectedRoute.pathCoordinates.last()
      assertTrue("End coordinate latitude should be near endPt", Math.abs(endCoord.first - endPt.first) < 0.1)
      assertTrue("End coordinate longitude should be near endPt", Math.abs(endCoord.second - endPt.second) < 0.1)
    } else {
      // If offline or placeholder API key, error message should be populated or graceful
      assertNotNull(GeoapifyService.lastErrorMessage.value)
    }
  }

  @Test
  fun testPeelameduToGandhipuramRouteScoreDifferentiation() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val originCoord = Pair(11.0280, 77.0050) // Peelamedu
    val destCoord = Pair(11.0168, 76.9558)   // Gandhipuram
    val waypoints = listOf(originCoord, destCoord)

    val routes = GeoapifyService.fetchRealRoutes(
      waypoints = waypoints,
      originName = "Peelamedu",
      destinationName = "Gandhipuram",
      timeOfDay = TimeOfDay.DUSK,
      context = context,
      enrichWithCorridor = true // scores are assigned during the background enrichment pass
    )

    if (routes.size >= 2) {
      val r0 = routes[0]
      val r1 = routes[1]

      println("Route 0: ${r0.name} -> Score: ${r0.safetyDetails.compositeScore}, Recommended: ${r0.isRecommended}")
      println("Route 1: ${r1.name} -> Score: ${r1.safetyDetails.compositeScore}, Recommended: ${r1.isRecommended}")

      // Verify differentiation between alternative routes
      assertNotEquals("Alternative routes should not have identical composite safety scores",
        r0.safetyDetails.compositeScore, r1.safetyDetails.compositeScore)
      assertNotEquals("Route names should be distinct", r0.name, r1.name)
    }
  }

  // ── SOS: phone normalisation + emergency message ──────────────────────────

  @Test
  fun sosNormalisesIndianAndInternationalPhoneNumbers() {
    assertEquals("+919876543210", SosManager.normalizePhoneNumber("98765 43210"))
    assertEquals("+919876543210", SosManager.normalizePhoneNumber("09876543210"))
    assertEquals("+919876543210", SosManager.normalizePhoneNumber("+91 98765-43210"))
    assertEquals("+919876543210", SosManager.normalizePhoneNumber("91 9876543210"))
    assertEquals("+15550192834", SosManager.normalizePhoneNumber("+1 (555) 019-2834"))
    assertNull(SosManager.normalizePhoneNumber(""))
    assertNull(SosManager.normalizePhoneNumber("abc"))
    assertNull(SosManager.normalizePhoneNumber("12345"))
  }

  @Test
  fun sosMessageContainsNameMapsLinkAndTime() {
    val message = SosManager.buildEmergencyMessage(
      userName = "Jeeva",
      latitude = 11.0280,
      longitude = 77.0050,
      timeMillis = 1_790_000_000_000L
    )
    assertTrue(message.contains("Jeeva"))
    assertTrue(message.contains("https://maps.google.com/?q=11.028,77.005"))
    assertTrue(message.contains("Time:"))
    assertTrue(message.contains("EMERGENCY SOS"))
  }

  // ── Long-route chunking + bounded scoring ────────────────────────────────

  @Test
  fun synthetic90KmRouteIsSplitIntoAboutTenKmChunks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = CorridorPlacesRepository.getInstance(context)

    // ~0.81 degrees of latitude is roughly 90 km.
    val coords = (0..180).map { i -> Pair(11.0 + i * (0.81 / 180.0), 77.0) }
    val chunks = repo.splitRouteIntoChunks(coords, 10_000.0)

    assertTrue("90 km route should produce several chunks, got ${chunks.size}", chunks.size in 8..11)

    var total = 0.0
    chunks.forEachIndexed { index, chunk ->
      assertTrue("Chunk $index should have at least 2 points", chunk.coords.size >= 2)
      if (index > 0) {
        assertEquals(
          "Chunks must share a boundary point",
          chunks[index - 1].coords.last(),
          chunk.coords.first()
        )
      }
      val chunkLength = chunk.coords.zipWithNext().sumOf { (a, b) -> haversineM(a, b) }
      total += chunkLength
      assertTrue(
        "Chunk $index should be about 10 km, was ${chunkLength / 1000} km",
        chunkLength <= 12_000.0
      )
    }
    assertTrue("Chunks should cover ~90 km, covered ${total / 1000} km", total in 88_000.0..92_000.0)
  }

  @Test
  fun synthetic90KmPartialCorridorScoringCompletesWithinTimeout() {
    val coords = (0..180).map { i -> Pair(11.0 + i * (0.81 / 180.0), 77.0) }

    // Simulate 8 of 9 chunks succeeding; one chunk (10 km) is marked unavailable.
    val places = (0..17).map { i ->
      RoutePlace(
        id = "poi_$i",
        name = "POI $i",
        category = if (i % 4 == 0) PlaceCategory.HOSPITAL else PlaceCategory.SHOP,
        lat = 11.0 + i * 0.04,
        lng = 77.0,
        distanceFromRouteM = 40.0,
        positionAlongRouteM = i * 5_000.0,
        openingHoursRaw = null,
        isOpenNow = true
      )
    }
    val partialCorridor = RouteCorridorData(
      places = places,
      litRatio = 0.55f,
      untaggedRatio = 0.30f,
      hasLightingData = true,
      totalWaysCount = 200,
      litWaysCount = 110,
      unlitWaysCount = 30,
      untaggedWaysCount = 60,
      openPlacesCount = places.size,
      totalPlacesCount = places.size,
      openShopsDensityPer100m = 0.6f,
      helpCoverageScore = 70f,
      isLengthExceeding25Km = true,
      hasCorridorData = true,
      failedChunkCount = 1,
      totalChunkCount = 9,
      isPartialData = true,
      unavailableDistanceM = 10_000.0
    )

    val startedAt = System.currentTimeMillis()
    val details = SafetyCalculator.calculateRouteSafetyFromCorridor(
      routeIndex = 0,
      routeCoordinates = coords,
      corridorData = partialCorridor,
      timeOfDay = TimeOfDay.DUSK,
      incidentZones = emptyList(),
      routeId = "synthetic_90km"
    )
    val elapsedMs = System.currentTimeMillis() - startedAt

    println("Synthetic 90 km partial score=${details.compositeScore} elapsedMs=$elapsedMs warning=${details.reliabilityWarning}")
    assertTrue("Scoring a 90 km route must finish within 15 s, took $elapsedMs ms", elapsedMs < 15_000)
    assertFalse("Partial data must not leave the score pending", details.isEnrichmentPending)
    assertNotNull("Partial data must carry a clear warning", details.reliabilityWarning)
    assertTrue(
      "Warning should explain the missing sections",
      details.reliabilityWarning!!.contains("Partial data")
    )
    assertTrue("Partial data still scores the available sections", details.compositeScore > 0)
  }

  private fun haversineM(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.first - a.first)
    val dLng = Math.toRadians(b.second - a.second)
    val h = sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(a.first)) * cos(Math.toRadians(b.first)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
  }
}
