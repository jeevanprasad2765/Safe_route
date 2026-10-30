package com.example

import com.example.saferoute.risk.GeohashUtils
import com.example.saferoute.risk.ModelMeta
import com.example.saferoute.risk.RiskDao
import com.example.saferoute.risk.RiskRepository
import com.example.saferoute.risk.RiskSegmentEntity
import com.example.saferoute.risk.RoadRiskLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoadRiskTest {

    @Test
    fun testGeohashEncodingPrecision6() {
        // Coimbatore location (lat 11.0168, lng 76.9558) starts with "t9" in standard base32
        val gh = GeohashUtils.encode(11.0168, 76.9558, precision = 6)
        assertEquals(6, gh.length)
        assertTrue(gh.startsWith("t9"))
    }

    @Test
    fun testGeohashNeighborsReturns9Cells() {
        val center = GeohashUtils.encode(11.0168, 76.9558, precision = 6)
        val neighbors = GeohashUtils.getNeighbors(center)
        assertTrue("Neighbors must contain the center cell", neighbors.contains(center))
        assertTrue("Must return at least 4 unique cells (up to 9)", neighbors.size >= 4)
    }

    @Test
    fun testHeadingDifferenceCalculation() {
        // Same heading
        assertEquals(0f, GeohashUtils.headingDifference(90f, 90f), 0.001f)
        // Opposite heading
        assertEquals(180f, GeohashUtils.headingDifference(0f, 180f), 0.001f)
        // 350 vs 10 degrees is 20 degrees
        assertEquals(20f, GeohashUtils.headingDifference(350f, 10f), 0.001f)
        // 45 degree threshold test
        assertTrue(GeohashUtils.headingDifference(40f, 80f) <= 45f)
        assertFalse(GeohashUtils.headingDifference(40f, 90f) <= 45f)
    }

    @Test
    fun testTrainedFalseDisablesRiskScore() = runBlocking {
        // Mock RiskDao and test behavior when model is not trained
        val mockDao = object : RiskDao {
            override suspend fun getSegmentsByGeohashes(geohashes: List<String>): List<RiskSegmentEntity> = emptyList()
            override suspend fun insertAll(segments: List<RiskSegmentEntity>) {}
            override suspend fun getSegmentCount(): Int = 0
            override suspend fun deleteAll() {}
        }

        val testContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = RiskRepository(testContext, mockDao)

        val meta = repo.getModelMeta()
        // model_meta.json in assets has trained: false (since blackspots.csv < 100 rows)
        assertFalse("Model must be marked as not trained", meta.isTrained)

        val coords = listOf(Pair(11.0168, 76.9558), Pair(11.0250, 76.9800))
        val result = repo.scoreRoute(coords)

        assertFalse("Risk score must be marked unavailable when model is not trained", result.isAvailable)
        assertEquals("Predicted risk unavailable - not enough accident data", result.explanationText)
        assertEquals(0f, result.confidence, 0.001f)
    }

    @Test
    fun testRouteWithNoMatchedSegmentsYieldsNoData() = runBlocking {
        // Even if model were trained, empty database produces 0 confidence and no data message
        val mockDao = object : RiskDao {
            override suspend fun getSegmentsByGeohashes(geohashes: List<String>): List<RiskSegmentEntity> = emptyList()
            override suspend fun insertAll(segments: List<RiskSegmentEntity>) {}
            override suspend fun getSegmentCount(): Int = 0
            override suspend fun deleteAll() {}
        }

        val testContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = RiskRepository(testContext, mockDao)

        // Route with minimal coordinates
        val coords = listOf(Pair(11.0168, 76.9558), Pair(11.0250, 76.9800))
        val result = repo.scoreRoute(coords)

        assertEquals(0f, result.confidence, 0.001f)
    }
}
