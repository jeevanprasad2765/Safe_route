package com.example.saferoute.data.firebase

import android.util.Log
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.db.SavedPlaceEntity
import com.example.saferoute.data.db.TripHistoryEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class FirestoreUserData(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val language: String = "en",
    val safetyPreference: String = "ALWAYS_SAFETY",
    val isPlusMember: Boolean = false,
    val completedOnboarding: Boolean = true,
    val emergencyContacts: List<Map<String, Any>> = emptyList(),
    val savedPlaces: List<Map<String, Any>> = emptyList(),
    val trips: List<Map<String, Any>> = emptyList()
)

object FirebaseFirestoreManager {
    private const val TAG = "FirestoreManager"
    const val DATABASE_ID = "ai-studio-android-remixsaf-bdc1654c-3901-4e7a-90ca-3f4feca85e73"

    private val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance(DATABASE_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore not initialized: ${e.message}")
            null
        }

    suspend fun createUserDocument(
        userId: String,
        name: String,
        email: String,
        phone: String = "",
        contacts: List<Map<String, Any>> = emptyList(),
        language: String = "en",
        places: List<Map<String, Any>> = emptyList()
    ): Boolean {
        if (userId.isBlank()) return false
        return try {
            val db = firestore ?: return false
            val now = System.currentTimeMillis()
            val data = mapOf(
                "uid" to userId,
                "name" to name,
                "email" to email,
                "phone" to phone,
                "language" to language,
                "languagePreference" to language,
                "safetyPreference" to "ALWAYS_SAFETY",
                "isPlusMember" to false,
                "completedOnboarding" to true,
                "emergencyContacts" to contacts,
                "savedPlaces" to places,
                "createdAt" to now,
                "lastUpdated" to now
            )
            db.collection("users").document(userId).set(data, SetOptions.merge()).await()
            Log.i(TAG, "Successfully created Firestore user document for $userId")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create user document in Firestore: ${e.message}")
            false
        }
    }

    suspend fun saveUserProfile(
        userId: String,
        name: String,
        email: String,
        phone: String = "",
        language: String = "en",
        safetyPreference: String = "ALWAYS_SAFETY",
        isPlusMember: Boolean = false
    ): Boolean {
        if (userId.isBlank()) return false
        return try {
            val db = firestore ?: return false
            val data = mapOf(
                "uid" to userId,
                "name" to name,
                "email" to email,
                "phone" to phone,
                "language" to language,
                "languagePreference" to language,
                "safetyPreference" to safetyPreference,
                "isPlusMember" to isPlusMember,
                "lastUpdated" to System.currentTimeMillis()
            )
            db.collection("users").document(userId).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save user profile to Firestore: ${e.message}")
            false
        }
    }

    suspend fun fetchUserData(userId: String): FirestoreUserData? {
        if (userId.isBlank()) return null
        return try {
            val db = firestore ?: return null
            val doc = try {
                db.collection("users").document(userId).get().await()
            } catch (offlineEx: Exception) {
                Log.d(TAG, "Firestore offline, attempting cache: ${offlineEx.message}")
                try {
                    db.collection("users").document(userId).get(com.google.firebase.firestore.Source.CACHE).await()
                } catch (_: Exception) {
                    return null
                }
            }
            if (!doc.exists()) return null

            val name = doc.getString("name") ?: ""
            val email = doc.getString("email") ?: ""
            val phone = doc.getString("phone") ?: ""
            val language = doc.getString("language") ?: doc.getString("languagePreference") ?: "en"
            val safetyPreference = doc.getString("safetyPreference") ?: "ALWAYS_SAFETY"
            val isPlusMember = doc.getBoolean("isPlusMember") ?: false
            val completedOnboarding = doc.getBoolean("completedOnboarding") ?: true

            @Suppress("UNCHECKED_CAST")
            val contacts = (doc.get("emergencyContacts") as? List<Map<String, Any>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val places = (doc.get("savedPlaces") as? List<Map<String, Any>>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val trips = (doc.get("trips") as? List<Map<String, Any>>) ?: emptyList()

            FirestoreUserData(
                uid = userId,
                name = name,
                email = email,
                phone = phone,
                language = language,
                safetyPreference = safetyPreference,
                isPlusMember = isPlusMember,
                completedOnboarding = completedOnboarding,
                emergencyContacts = contacts,
                savedPlaces = places,
                trips = trips
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch user data from Firestore: ${e.message}")
            null
        }
    }

    suspend fun syncEmergencyContacts(userId: String, contacts: List<EmergencyContactEntity>): Boolean {
        if (userId.isBlank()) return false
        return try {
            val db = firestore ?: return false
            val list = contacts.map {
                mapOf(
                    "name" to it.name,
                    "phone" to it.phone,
                    "relationship" to it.relationship,
                    "isPrimary" to it.isPrimary
                )
            }
            db.collection("users").document(userId).set(mapOf("emergencyContacts" to list), SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync contacts to Firestore: ${e.message}")
            false
        }
    }

    suspend fun syncSavedPlaces(userId: String, places: List<SavedPlaceEntity>): Boolean {
        if (userId.isBlank()) return false
        return try {
            val db = firestore ?: return false
            val list = places.map {
                mapOf(
                    "title" to it.title,
                    "address" to it.address,
                    "iconName" to it.iconName,
                    "latitude" to it.latitude,
                    "longitude" to it.longitude
                )
            }
            db.collection("users").document(userId).set(mapOf("savedPlaces" to list), SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync places to Firestore: ${e.message}")
            false
        }
    }

    suspend fun saveTripToFirestore(userId: String, trip: TripHistoryEntity): Boolean {
        if (userId.isBlank()) return false
        return try {
            val db = firestore ?: return false
            val tripData = mapOf(
                "tripId" to trip.id,
                "originName" to trip.originName,
                "destinationName" to trip.destinationName,
                "timestamp" to trip.timestamp,
                "durationSeconds" to trip.durationSeconds,
                "distanceKm" to trip.distanceKm,
                "avgSafetyScore" to trip.avgSafetyScore,
                "routeTypeName" to trip.routeTypeName,
                "userRating" to trip.userRating,
                "feedbackNote" to trip.feedbackNote,
                "sharedWithContact" to trip.sharedWithContact
            )
            db.collection("users").document(userId).collection("trips").document("trip_${trip.timestamp}").set(tripData).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save trip to Firestore: ${e.message}")
            false
        }
    }

    suspend fun fetchUserHazards(): List<Map<String, Any>> {
        return try {
            val db = firestore ?: return emptyList()
            val snapshot = db.collection("hazards").get().await()
            snapshot.documents.mapNotNull { it.data }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch hazards from Firestore: ${e.message}")
            emptyList()
        }
    }
}
