package com.example.saferoute.risk

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RiskDao {

    @Query("SELECT * FROM risk_segments WHERE geohash6 IN (:geohashes)")
    suspend fun getSegmentsByGeohashes(geohashes: List<String>): List<RiskSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<RiskSegmentEntity>)

    @Query("SELECT COUNT(*) FROM risk_segments")
    suspend fun getSegmentCount(): Int

    @Query("DELETE FROM risk_segments")
    suspend fun deleteAll()
}
