package com.example.saferoute.risk

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "risk_segments",
    indices = [Index(value = ["geohash6"])]
)
data class RiskSegmentEntity(
    @PrimaryKey
    val segmentId: String,
    val geohash6: String,
    val midLat: Double,
    val midLng: Double,
    val heading: Float,
    val lengthM: Float,
    val risk: Float,
    val level: String, // "Low", "Medium", "High"
    val reasons: String, // Semicolon or comma-separated reason codes
    val importedAt: Long = System.currentTimeMillis()
)
