package com.example.saferoute.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, id ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<EmergencyContactEntity>)

    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)

    @Query("DELETE FROM emergency_contacts")
    suspend fun clearContacts()
}

@Dao
interface TripHistoryDao {
    @Query("SELECT * FROM trip_history ORDER BY timestamp DESC")
    fun getAllTrips(): Flow<List<TripHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripHistoryEntity>)

    @Update
    suspend fun updateTrip(trip: TripHistoryEntity)

    @Query("SELECT * FROM trip_history WHERE id = :tripId")
    suspend fun getTripById(tripId: Long): TripHistoryEntity?

    @Query("DELETE FROM trip_history")
    suspend fun clearHistory()
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY id ASC")
    fun getAllSavedPlaces(): Flow<List<SavedPlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: SavedPlaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<SavedPlaceEntity>)

    @Delete
    suspend fun deletePlace(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places")
    suspend fun clearPlaces()
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET completedOnboarding = :completed WHERE id = 1")
    suspend fun setOnboardingCompleted(completed: Boolean)

    @Query("UPDATE user_profile SET isPlusMember = :isPlus WHERE id = 1")
    suspend fun setPlusMembership(isPlus: Boolean)

    @Query("UPDATE user_profile SET safetyPreference = :pref WHERE id = 1")
    suspend fun setSafetyPreference(pref: String)

    @Query("UPDATE user_profile SET alsoCall112 = :enabled WHERE id = 1")
    suspend fun setAlsoCall112(enabled: Boolean)
}

@Dao
interface UserOnboardingDao {
    @Query("SELECT completedOnboarding FROM user_onboarding WHERE userId = :userId LIMIT 1")
    suspend fun getOnboardingCompleted(userId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(status: UserOnboardingEntity)

    @Query("DELETE FROM user_onboarding WHERE userId = :userId")
    suspend fun clear(userId: String)
}
