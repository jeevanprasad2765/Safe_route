package com.example.saferoute.data.repository

import com.example.saferoute.data.db.EmergencyContactDao
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.db.SavedPlaceDao
import com.example.saferoute.data.db.SavedPlaceEntity
import com.example.saferoute.data.db.TripHistoryDao
import com.example.saferoute.data.db.TripHistoryEntity
import com.example.saferoute.data.db.UserOnboardingDao
import com.example.saferoute.data.db.UserOnboardingEntity
import com.example.saferoute.data.db.UserProfileDao
import com.example.saferoute.data.db.UserProfileEntity
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.RouteSegment
import com.example.saferoute.data.model.SafetyScoreDetails
import com.example.saferoute.data.model.TimeOfDay
import com.example.saferoute.data.model.TurnInstruction
import com.example.saferoute.safety.SafetyCalculator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class SafeRouteRepository(
    private val contactDao: EmergencyContactDao,
    private val tripDao: TripHistoryDao,
    private val savedPlaceDao: SavedPlaceDao,
    private val userProfileDao: UserProfileDao,
    private val userOnboardingDao: UserOnboardingDao,
    private val context: android.content.Context? = null
) {

    val allContacts: Flow<List<EmergencyContactEntity>> = contactDao.getAllContacts()
    val allTripHistory: Flow<List<TripHistoryEntity>> = tripDao.getAllTrips()
    val savedPlaces: Flow<List<SavedPlaceEntity>> = savedPlaceDao.getAllSavedPlaces()
    val userProfile: Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun initDefaultDataIfEmpty() {
        val existingProfile = userProfile.firstOrNull()
        if (existingProfile == null) {
            userProfileDao.insertOrUpdateProfile(UserProfileEntity())
        }
        // Emergency contacts and trips are intentionally not seeded with demo data. New users
        // complete emergency-contact setup during onboarding; returning users restore theirs
        // from Room / Firestore.
    }

    suspend fun insertContact(name: String, phone: String, relationship: String, isPrimary: Boolean) {
        contactDao.insertContact(EmergencyContactEntity(name = name, phone = phone, relationship = relationship, isPrimary = isPrimary))
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            val all = contactDao.getAllContacts().firstOrNull() ?: emptyList()
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.syncEmergencyContacts(current.userId, all)
        }
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) {
        contactDao.deleteContact(contact)
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            val all = contactDao.getAllContacts().firstOrNull() ?: emptyList()
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.syncEmergencyContacts(current.userId, all)
        }
    }

    suspend fun savePlace(title: String, address: String, iconName: String, lat: Double, lng: Double) {
        savedPlaceDao.insertPlace(SavedPlaceEntity(title = title, address = address, iconName = iconName, latitude = lat, longitude = lng))
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            val all = savedPlaceDao.getAllSavedPlaces().firstOrNull() ?: emptyList()
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.syncSavedPlaces(current.userId, all)
        }
    }

    suspend fun updatePlace(place: SavedPlaceEntity) {
        savedPlaceDao.insertPlace(place) // REPLACE strategy updates the row with matching id
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            val all = savedPlaceDao.getAllSavedPlaces().firstOrNull() ?: emptyList()
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.syncSavedPlaces(current.userId, all)
        }
    }

    suspend fun deletePlace(place: SavedPlaceEntity) {
        savedPlaceDao.deletePlace(place)
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            val all = savedPlaceDao.getAllSavedPlaces().firstOrNull() ?: emptyList()
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.syncSavedPlaces(current.userId, all)
        }
    }

    suspend fun recordTrip(
        origin: String,
        destination: String,
        durationSeconds: Long,
        distanceKm: Double,
        avgSafetyScore: Int,
        routeTypeName: String,
        rating: Int = 0,
        feedback: String = "",
        sharedWith: String = ""
    ): Long {
        val trip = TripHistoryEntity(
            originName = origin,
            destinationName = destination,
            timestamp = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            avgSafetyScore = avgSafetyScore,
            routeTypeName = routeTypeName,
            userRating = rating,
            feedbackNote = feedback,
            sharedWithContact = sharedWith
        )
        val insertedId = tripDao.insertTrip(trip)
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.saveTripToFirestore(current.userId, trip.copy(id = insertedId))
        }
        return insertedId
    }

    suspend fun updateTripFeedback(tripId: Long, rating: Int, feedback: String) {
        val existing = tripDao.getTripById(tripId)
        if (existing != null) {
            tripDao.updateTrip(existing.copy(userRating = rating, feedbackNote = feedback))
        }
    }

    suspend fun updateSafetyPreference(pref: String) {
        userProfileDao.setSafetyPreference(pref)
        val current = userProfile.firstOrNull()
        if (current != null && current.isLoggedIn && current.userId.isNotBlank()) {
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.saveUserProfile(
                userId = current.userId,
                name = current.name,
                email = current.email,
                phone = current.phone,
                language = current.languagePreference,
                safetyPreference = pref,
                isPlusMember = current.isPlusMember
            )
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        val current = userProfile.firstOrNull() ?: UserProfileEntity()
        val uid = current.userId.ifBlank {
            com.example.saferoute.data.firebase.FirebaseAuthManager.currentUserId ?: ""
        }
        userProfileDao.setOnboardingCompleted(completed)
        if (uid.isNotBlank()) {
            setOnboardingCompletedForUser(uid, completed)
        }
    }

    suspend fun setOnboardingCompletedForUser(userId: String, completed: Boolean) {
        if (userId.isBlank()) return
        userOnboardingDao.upsert(
            UserOnboardingEntity(userId = userId, completedOnboarding = completed)
        )
        com.example.saferoute.data.firebase.FirebaseFirestoreManager.setOnboardingCompleted(userId, completed)
    }

    /**
     * Per-user onboarding state. Room is authoritative; Firestore is a best-effort backup.
     * A returning Firebase session with unreadable metadata is assumed completed so users are
     * never trapped in onboarding again.
     */
    suspend fun isOnboardingCompleted(userId: String): Boolean {
        if (userId.isBlank()) return false
        val local = userOnboardingDao.getOnboardingCompleted(userId)
        if (local != null) return local

        val remote = try {
            kotlinx.coroutines.withTimeoutOrNull(4_000) {
                com.example.saferoute.data.firebase.FirebaseFirestoreManager.fetchUserData(userId)
                    ?.completedOnboarding
            }
        } catch (_: Throwable) {
            null
        }
        val resolved = remote ?: true
        userOnboardingDao.upsert(
            UserOnboardingEntity(userId = userId, completedOnboarding = resolved)
        )
        return resolved
    }

    suspend fun setAlsoCall112(enabled: Boolean) {
        userProfileDao.setAlsoCall112(enabled)
    }

    suspend fun getAlsoCall112(): Boolean {
        return userProfile.firstOrNull()?.alsoCall112 ?: false
    }

    suspend fun setPlusMembership(isPlus: Boolean) {
        userProfileDao.setPlusMembership(isPlus)
    }

    suspend fun updateLanguage(langCode: String) {
        val current = userProfile.firstOrNull() ?: UserProfileEntity()
        userProfileDao.insertOrUpdateProfile(current.copy(languagePreference = langCode))
        if (current.isLoggedIn && current.userId.isNotBlank()) {
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.saveUserProfile(
                userId = current.userId,
                name = current.name,
                email = current.email,
                phone = current.phone,
                language = langCode,
                safetyPreference = current.safetyPreference,
                isPlusMember = current.isPlusMember
            )
        }
    }

    /**
     * Persists a Firebase-authenticated session. Returns whether onboarding is complete.
     * Newly created accounts always start onboarding; returning users keep their per-user state.
     */
    suspend fun logInUser(
        userId: String,
        email: String,
        name: String,
        isNewUser: Boolean = false
    ): Boolean {
        val current = userProfile.firstOrNull() ?: UserProfileEntity()

        val firestoreData = try {
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                com.example.saferoute.data.firebase.FirebaseFirestoreManager.fetchUserData(userId)
            }
        } catch (_: Throwable) {
            null
        }

        val localOnboarding = userOnboardingDao.getOnboardingCompleted(userId)
        val completedOnboarding = when {
            isNewUser -> false
            localOnboarding != null -> localOnboarding
            firestoreData != null -> firestoreData.completedOnboarding
            current.userId == userId && current.completedOnboarding -> true
            // Existing Firebase account with no readable metadata: assume onboarding was done.
            else -> true
        }

        val updated = current.copy(
            userId = userId,
            email = email,
            name = name,
            isLoggedIn = true,
            completedOnboarding = completedOnboarding
        )
        userProfileDao.insertOrUpdateProfile(updated)
        userOnboardingDao.upsert(
            UserOnboardingEntity(userId = userId, completedOnboarding = completedOnboarding)
        )

        if (firestoreData != null) {
            // Restore the user's contacts/places if the cloud copy has any.
            if (firestoreData.emergencyContacts.isNotEmpty()) {
                val contactEntities = firestoreData.emergencyContacts.mapNotNull { m ->
                    val cName = m["name"] as? String ?: return@mapNotNull null
                    val cPhone = m["phone"] as? String ?: ""
                    val cRel = m["relationship"] as? String ?: "Contact"
                    val cPrimary = m["isPrimary"] as? Boolean ?: false
                    EmergencyContactEntity(name = cName, phone = cPhone, relationship = cRel, isPrimary = cPrimary)
                }
                if (contactEntities.isNotEmpty()) {
                    contactDao.clearContacts()
                    contactDao.insertContacts(contactEntities)
                }
            }

            if (firestoreData.savedPlaces.isNotEmpty()) {
                val placeEntities = firestoreData.savedPlaces.mapNotNull { m ->
                    val pTitle = m["title"] as? String ?: return@mapNotNull null
                    val pAddr = m["address"] as? String ?: ""
                    val pIcon = m["iconName"] as? String ?: "home"
                    val pLat = (m["latitude"] as? Number)?.toDouble() ?: 11.0072
                    val pLng = (m["longitude"] as? Number)?.toDouble() ?: 76.9515
                    SavedPlaceEntity(title = pTitle, address = pAddr, iconName = pIcon, latitude = pLat, longitude = pLng)
                }
                if (placeEntities.isNotEmpty()) {
                    savedPlaceDao.clearPlaces()
                    savedPlaceDao.insertPlaces(placeEntities)
                }
            }
        } else if (!isNewUser) {
            // Existing account: make sure the cloud profile exists, but never overwrite contacts.
            com.example.saferoute.data.firebase.FirebaseFirestoreManager.saveUserProfile(
                userId = userId,
                name = name,
                email = email,
                phone = updated.phone,
                language = updated.languagePreference,
                safetyPreference = updated.safetyPreference,
                isPlusMember = updated.isPlusMember
            )
        }

        return completedOnboarding
    }

    /** Restores a session that Firebase already persisted across app restarts. */
    suspend fun restoreSession(userId: String, email: String, name: String): Boolean {
        val completedOnboarding = isOnboardingCompleted(userId)
        val current = userProfile.firstOrNull() ?: UserProfileEntity()
        userProfileDao.insertOrUpdateProfile(
            current.copy(
                userId = userId,
                email = email.ifBlank { current.email },
                name = name.ifBlank { current.name },
                isLoggedIn = true,
                completedOnboarding = completedOnboarding
            )
        )
        return completedOnboarding
    }

    suspend fun logOutUser() {
        com.example.saferoute.data.firebase.FirebaseAuthManager.signOut()
        val current = userProfile.firstOrNull() ?: UserProfileEntity()
        userProfileDao.insertOrUpdateProfile(
            current.copy(
                isLoggedIn = false,
                userId = "",
                completedOnboarding = false
            )
        )
    }

    // ── ACCIDENT BLACK-SPOTS & REAL INCIDENT ZONES ──

    fun getIncidentZones(): List<IncidentZone> {
        val list = mutableListOf<IncidentZone>()
        val targetContext = context ?: try {
            com.example.SafeRouteApplication.appContext
        } catch (_: Throwable) {
            null
        }

        // 1. Load from assets/blackspots.json
        if (targetContext != null) {
            try {
                val jsonStr = targetContext.assets.open("blackspots.json").bufferedReader().use { it.readText() }
                val array = org.json.JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "bs_$i")
                    val lat = obj.optDouble("lat", 0.0)
                    val lng = obj.optDouble("lng", 0.0)
                    val rad = obj.optDouble("radiusMeters", 150.0)
                    val sev = obj.optInt("severity", 3)
                    val desc = obj.optString("description", "Accident Black-spot")
                    val src = obj.optString("source", "Official")
                    val yr = obj.optInt("year", 2023)
                    list.add(
                        IncidentZone(
                            id = id,
                            latitude = lat,
                            longitude = lng,
                            radiusMeters = rad,
                            severity = sev,
                            category = "$desc ($src, $yr)",
                            timestamp = "$yr"
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("SafeRouteRepository", "Could not load blackspots.json: ${e.message}")
            }
        }

        return list
    }

    suspend fun fetchIncidentZonesWithFirestore(): List<IncidentZone> {
        val baseList = getIncidentZones().toMutableList()
        try {
            val userHazards = com.example.saferoute.data.firebase.FirebaseFirestoreManager.fetchUserHazards()
            for ((idx, map) in userHazards.withIndex()) {
                val lat = (map["latitude"] as? Number)?.toDouble() ?: continue
                val lng = (map["longitude"] as? Number)?.toDouble() ?: continue
                val desc = (map["description"] as? String) ?: (map["category"] as? String) ?: "User Reported Hazard"
                val sev = (map["severity"] as? Number)?.toInt() ?: 3
                val radius = (map["radiusMeters"] as? Number)?.toDouble() ?: 150.0
                baseList.add(
                    IncidentZone(
                        id = "user_hazard_$idx",
                        latitude = lat,
                        longitude = lng,
                        radiusMeters = radius,
                        severity = sev,
                        category = "User Report: $desc",
                        timestamp = "Verified Hazard"
                    )
                )
            }
        } catch (_: Exception) {}
        return baseList
    }

    private var lastComputedRoutes: List<RouteOption> = emptyList()

    /**
     * FAST routing pass: geocoded coordinates → real geometry & alternatives already simplified.
     * Never blocks on Overpass/POI or risk matching — returns immediately with pending safety
     * details so route cards can render, then [enrichRoutesInBackground] fills the cards in.
     */
    suspend fun computeRealRoutes(
        originName: String = "My Location",
        destinationName: String = "TIDEL Park, Peelamedu",
        originCoords: Pair<Double, Double> = Pair(11.0168, 76.9558),
        destinationCoords: Pair<Double, Double> = Pair(11.0280, 77.0050),
        waypointsData: List<Pair<String, Pair<Double, Double>>> = emptyList(),
        timeOfDay: TimeOfDay = TimeOfDay.DUSK,
        prioritizeSafety: Boolean = true
    ): List<RouteOption> {
        val allPoints = mutableListOf<Pair<Double, Double>>()
        allPoints.add(originCoords)
        waypointsData.forEach { allPoints.add(it.second) }
        allPoints.add(destinationCoords)

        val incidentZones = getIncidentZones()
        val intermediateNames = waypointsData.map { it.first }
        val targetContext = context ?: try {
            com.example.SafeRouteApplication.appContext
        } catch (_: Throwable) {
            null
        }

        if (targetContext == null) return emptyList()

        val routes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.example.saferoute.data.network.GeoapifyService.fetchRealRoutes(
                waypoints = allPoints,
                originName = originName.ifBlank { "My Location" },
                destinationName = destinationName.ifBlank { "Destination" },
                intermediateNames = intermediateNames,
                timeOfDay = timeOfDay,
                incidentZones = incidentZones,
                context = targetContext,
                enrichWithCorridor = false
            )
        }

        lastComputedRoutes = routes
        return routes
    }

    /**
     * BACKGROUND ENRICHMENT: fetches corridor (Overpass/POI) data, safety scoring and road-risk
     * matching per route — in parallel, capped at 6 s per route, never blocking route display.
     * Scoring runs on Dispatchers.Default. Calls [onRouteEnriched] progressively (routeId, route)
     * so route cards fill in as soon as each individual route finishes.
     */
    suspend fun enrichRoutesInBackground(
        routes: List<RouteOption>,
        timeOfDay: TimeOfDay = TimeOfDay.DUSK,
        prioritizeSafety: Boolean = true,
        incidentZones: List<IncidentZone> = emptyList(),
        routeCacheKey: String = "",
        onRouteEnriched: (routeId: String, enrichedRoute: RouteOption) -> Unit = { _, _ -> }
    ): List<RouteOption> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val routeList = routes
        val enriched = com.example.saferoute.data.network.GeoapifyService.enrichRoutesWithCorridor(
            context = context ?: com.example.SafeRouteApplication.appContext,
            routes = routeList,
            timeOfDay = timeOfDay,
            incidentZones = incidentZones,
            routeCacheKey = routeCacheKey,
            onRouteEnriched = onRouteEnriched
        )

        // Road-risk matching (geohash indexed) in parallel on Default dispatcher with 6 s cap
        val enrichedWithRisk = kotlinx.coroutines.coroutineScope {
            routeList.mapIndexed { idx, route ->
                async {
                    val risk = try {
                        kotlinx.coroutines.withTimeoutOrNull(6_000) {
                            com.example.SafeRouteApplication.riskRepository.scoreRoute(route.pathCoordinates)
                        } ?: route.roadRiskDetails
                    } catch (_: Throwable) {
                        route.roadRiskDetails
                    }
                    enriched.getOrNull(idx)?.copy(roadRiskDetails = risk) ?: route.copy(roadRiskDetails = risk)
                }
            }.map { it.await() }
        }

        val finalRoutes = safeEnrichedSort(enrichedWithRisk, prioritizeSafety)
        lastComputedRoutes = finalRoutes
        finalRoutes
    }

    private fun safeEnrichedSort(routes: List<RouteOption>, prioritizeSafety: Boolean): List<RouteOption> {
        val relevant = routes.filterNot { it.safetyDetails.isEnrichmentPending }
        if (relevant.isEmpty()) return routes
        return if (prioritizeSafety) {
            routes.filter { it.safetyDetails.isEnrichmentPending } +
                relevant.sortedByDescending { it.safetyDetails.compositeScore }
        } else {
            routes.filter { it.safetyDetails.isEnrichmentPending } +
                relevant.sortedBy { it.durationMinutes }
        }
    }

    fun computeRouteOptions(
        origin: String = "My Location",
        destination: String = "TIDEL Park, Peelamedu",
        waypointsNames: List<String> = emptyList(),
        timeOfDay: TimeOfDay = TimeOfDay.DUSK,
        prioritizeSafety: Boolean = true,
        originCoords: Pair<Double, Double>? = null
    ): List<RouteOption> {
        val incidentZones = getIncidentZones()

        val startLat = originCoords?.first ?: 11.0168
        val startLng = originCoords?.second ?: 76.9558

        val sourceWaypoint = com.example.saferoute.data.model.Waypoint(name = origin.ifBlank { "My Location" }, isSource = true)
        val destWaypoint = com.example.saferoute.data.model.Waypoint(name = destination.ifBlank { "Destination" }, isDestination = true)
        val stopWaypoints = waypointsNames.filter { it.isNotBlank() }.map { com.example.saferoute.data.model.Waypoint(name = it) }

        val allNodesInOrder = mutableListOf<String>()
        allNodesInOrder.add(sourceWaypoint.name)
        allNodesInOrder.addAll(stopWaypoints.map { it.name })
        allNodesInOrder.add(destWaypoint.name)

        // Route 1: Avinashi Road Safe Corridor
        val route1Segments = listOf(
            RouteSegment("r1_s1", "DB Road Illuminated Ave", 800, true, 96f, 92f, 0, startLat, startLng, 11.0168, 76.9558),
            RouteSegment("r1_s2", "Avinashi Road Arterial Corridor", 2200, true, 94f, 88f, 0, 11.0168, 76.9558, 11.0263, 77.0028),
            RouteSegment("r1_s3", "TIDEL Park Access Boulevard", 600, true, 92f, 90f, 0, 11.0263, 77.0028, 11.0280, 77.0050)
        )
        val score1 = SafetyCalculator.calculateRouteSafety(route1Segments, timeOfDay, incidentZones)

        val legs1 = mutableListOf<com.example.saferoute.data.model.RouteLeg>()
        val legCount = allNodesInOrder.size - 1
        for (i in 0 until legCount) {
            val from = allNodesInOrder[i]
            val to = allNodesInOrder[i + 1]
            val legDist = (5.2 + (i * 1.5)) / legCount
            val legDur = Math.max(2, (16 + (i * 4)) / legCount)
            val legScore = when (i % 3) {
                0 -> 94
                1 -> 88
                else -> 92
            }
            legs1.add(
                com.example.saferoute.data.model.RouteLeg(
                    legIndex = i + 1,
                    fromName = from,
                    toName = to,
                    distanceKm = Math.round(legDist * 10.0) / 10.0,
                    durationMinutes = legDur,
                    safetyScore = legScore,
                    safetyTier = if (legScore >= 80) com.example.saferoute.data.model.SafetyTier.HIGH_SAFETY else com.example.saferoute.data.model.SafetyTier.MEDIUM_SAFETY
                )
            )
        }

        // Route 2: Trichy Road Commercial Corridor
        val route2Segments = listOf(
            RouteSegment("r2_s1", "Lanka Corner Road", 900, true, 88f, 82f, 0, startLat, startLng, 10.9990, 76.9730),
            RouteSegment("r2_s2", "Trichy Road Highway", 2100, false, 76f, 68f, 1, 10.9990, 76.9730, 10.9970, 77.0180),
            RouteSegment("r2_s3", "Singanallur Peelamedu Link", 800, true, 82f, 78f, 0, 10.9970, 77.0180, 11.0263, 77.0028)
        )
        val score2 = SafetyCalculator.calculateRouteSafety(route2Segments, timeOfDay, incidentZones)

        val legs2 = mutableListOf<com.example.saferoute.data.model.RouteLeg>()
        for (i in 0 until legCount) {
            val from = allNodesInOrder[i]
            val to = allNodesInOrder[i + 1]
            val legDist = (4.8 + (i * 1.2)) / legCount
            val legDur = Math.max(2, (14 + (i * 3)) / legCount)
            val legScore = when (i % 3) {
                0 -> 82
                1 -> 68
                else -> 78
            }
            legs2.add(
                com.example.saferoute.data.model.RouteLeg(
                    legIndex = i + 1,
                    fromName = from,
                    toName = to,
                    distanceKm = Math.round(legDist * 10.0) / 10.0,
                    durationMinutes = legDur,
                    safetyScore = legScore,
                    safetyTier = if (legScore >= 80) com.example.saferoute.data.model.SafetyTier.HIGH_SAFETY else if (legScore >= 60) com.example.saferoute.data.model.SafetyTier.MEDIUM_SAFETY else com.example.saferoute.data.model.SafetyTier.CAUTION_REQUIRED
                )
            )
        }

        // Route 3: Railway Colony Cut-through
        val route3Segments = listOf(
            RouteSegment("r3_s1", "Goods Shed Road", 500, false, 68f, 48f, 0, startLat, startLng, 11.0110, 76.9620),
            RouteSegment("r3_s2", "Railway Colony Service Track", 1200, false, 32f, 18f, 3, 11.0110, 76.9620, 11.0210, 76.9850),
            RouteSegment("r3_s3", "Peelamedu Back Alley", 700, false, 38f, 28f, 2, 11.0210, 76.9850, 11.0263, 77.0028)
        )
        val score3 = SafetyCalculator.calculateRouteSafety(route3Segments, timeOfDay, incidentZones)

        val legs3 = mutableListOf<com.example.saferoute.data.model.RouteLeg>()
        for (i in 0 until legCount) {
            val from = allNodesInOrder[i]
            val to = allNodesInOrder[i + 1]
            val legDist = (3.9 + (i * 1.0)) / legCount
            val legDur = Math.max(2, (11 + (i * 3)) / legCount)
            val legScore = when (i % 3) {
                0 -> 65
                1 -> 42
                else -> 55
            }
            legs3.add(
                com.example.saferoute.data.model.RouteLeg(
                    legIndex = i + 1,
                    fromName = from,
                    toName = to,
                    distanceKm = Math.round(legDist * 10.0) / 10.0,
                    durationMinutes = legDur,
                    safetyScore = legScore,
                    safetyTier = if (legScore >= 60) com.example.saferoute.data.model.SafetyTier.MEDIUM_SAFETY else com.example.saferoute.data.model.SafetyTier.CAUTION_REQUIRED
                )
            )
        }

        val totalDist1 = Math.round(legs1.sumOf { it.distanceKm } * 10.0) / 10.0
        val totalDur1 = legs1.sumOf { it.durationMinutes }

        val totalDist2 = Math.round(legs2.sumOf { it.distanceKm } * 10.0) / 10.0
        val totalDur2 = legs2.sumOf { it.durationMinutes }

        val totalDist3 = Math.round(legs3.sumOf { it.distanceKm } * 10.0) / 10.0
        val totalDur3 = legs3.sumOf { it.durationMinutes }

        val option1 = RouteOption(
            id = "opt_safe",
            name = "Avinashi Road Safe Corridor",
            summary = "Primary arterial highway connecting city center to TIDEL Park",
            distanceKm = totalDist1,
            durationMinutes = totalDur1,
            safetyDetails = score1,
            segments = route1Segments,
            pathCoordinates = listOf(
                Pair(startLat, startLng),
                Pair((startLat + 11.0280) / 2, (startLng + 77.0050) / 2),
                Pair(11.0280, 77.0050)
            ),
            isRecommended = true,
            warningNote = "",
            sourceName = sourceWaypoint.name,
            destinationName = destWaypoint.name,
            waypoints = listOf(sourceWaypoint) + stopWaypoints + listOf(destWaypoint),
            legs = legs1
        )

        val option2 = RouteOption(
            id = "opt_balanced",
            name = "Commercial Avenue",
            summary = "Direct route via commercial district",
            distanceKm = totalDist2,
            durationMinutes = totalDur2,
            safetyDetails = score2,
            segments = route2Segments,
            pathCoordinates = listOf(
                Pair(startLat, startLng),
                Pair((startLat + 11.0280) / 2 - 0.003, (startLng + 77.0050) / 2 + 0.002),
                Pair(11.0280, 77.0050)
            ),
            isRecommended = false,
            warningNote = "",
            sourceName = sourceWaypoint.name,
            destinationName = destWaypoint.name,
            waypoints = listOf(sourceWaypoint) + stopWaypoints + listOf(destWaypoint),
            legs = legs2
        )

        val option3 = RouteOption(
            id = "opt_fast",
            name = "Shortcut Route",
            summary = "Secondary transit cut-through",
            distanceKm = totalDist3,
            durationMinutes = totalDur3,
            safetyDetails = score3,
            segments = route3Segments,
            pathCoordinates = listOf(
                Pair(startLat, startLng),
                Pair((startLat + 11.0280) / 2 + 0.003, (startLng + 77.0050) / 2 - 0.002),
                Pair(11.0280, 77.0050)
            ),
            isRecommended = false,
            warningNote = "",
            sourceName = sourceWaypoint.name,
            destinationName = destWaypoint.name,
            waypoints = listOf(sourceWaypoint) + stopWaypoints + listOf(destWaypoint),
            legs = legs3
        )

        val rawList = listOf(option1, option2, option3).map { route ->
            try {
                val riskDetails = kotlinx.coroutines.runBlocking {
                    com.example.SafeRouteApplication.riskRepository.scoreRoute(route.pathCoordinates)
                }
                route.copy(roadRiskDetails = riskDetails)
            } catch (_: Throwable) {
                route
            }
        }
        return if (prioritizeSafety) {
            rawList.sortedByDescending { it.safetyDetails.compositeScore }
        } else {
            rawList.sortedBy { it.durationMinutes }
        }
    }

    fun getTurnInstructionsForRoute(routeId: String): List<TurnInstruction> {
        val found = lastComputedRoutes.find { it.id == routeId } ?: lastComputedRoutes.firstOrNull()
        if (found != null && found.turnInstructions.isNotEmpty()) {
            return found.turnInstructions
        }
        return listOf(
            TurnInstruction(150, "Head along corridor", "Main Road", "straight", "Active Corridor"),
            TurnInstruction(400, "Continue along route", "Arterial Road", "straight", "Active Corridor"),
            TurnInstruction(100, "Arrive at destination", "Destination", "arrive", "Target Destination")
        )
    }
}
