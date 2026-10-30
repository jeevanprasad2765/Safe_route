package com.example.saferoute.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.saferoute.risk.RiskDao
import com.example.saferoute.risk.RiskSegmentEntity

@Database(
    entities = [
        EmergencyContactEntity::class,
        TripHistoryEntity::class,
        SavedPlaceEntity::class,
        UserProfileEntity::class,
        RouteOverpassCacheEntity::class,
        RiskSegmentEntity::class,
        UserOnboardingEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun tripHistoryDao(): TripHistoryDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun routeOverpassCacheDao(): RouteOverpassCacheDao
    abstract fun riskDao(): RiskDao
    abstract fun userOnboardingDao(): UserOnboardingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "saferoute_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
