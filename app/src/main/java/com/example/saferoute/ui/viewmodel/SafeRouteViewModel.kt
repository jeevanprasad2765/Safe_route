package com.example.saferoute.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.saferoute.data.db.AppDatabase
import com.example.saferoute.data.db.EmergencyContactEntity
import com.example.saferoute.data.db.SavedPlaceEntity
import com.example.saferoute.data.db.TripHistoryEntity
import com.example.saferoute.data.db.UserProfileEntity
import com.example.saferoute.data.model.GroupMember
import com.example.saferoute.data.model.GroupTripSession
import com.example.saferoute.data.model.IncidentZone
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.data.model.TimeOfDay
import com.example.saferoute.data.model.TurnInstruction
import com.example.saferoute.data.firebase.FirebaseConnectionManager
import com.example.saferoute.data.firebase.FirebaseStatusInfo
import com.example.saferoute.data.repository.SafeRouteRepository
import com.example.saferoute.util.AppLanguage
import com.example.saferoute.util.LanguageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class ScreenState {
    object Splash : ScreenState()
    object Onboarding : ScreenState()
    object Auth : ScreenState()
    object Main : ScreenState()
    data class RouteRecommendations(
        val source: String = "My Location",
        val destination: String = "TIDEL Park, Peelamedu",
        val waypoints: List<String> = emptyList()
    ) : ScreenState()
    data class ActiveNav(val route: RouteOption) : ScreenState()
    data class TripSummary(
        val route: RouteOption,
        val durationSeconds: Long,
        val distanceKm: Double,
        val avgScore: Int
    ) : ScreenState()
}

class SafeRouteViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = SafeRouteRepository(
        db.emergencyContactDao(),
        db.tripHistoryDao(),
        db.savedPlaceDao(),
        db.userProfileDao(),
        application
    )

    val contacts: StateFlow<List<EmergencyContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tripHistory: StateFlow<List<TripHistoryEntity>> = repository.allTripHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = repository.savedPlaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfileEntity?> = repository.userProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentScreen = MutableStateFlow<ScreenState>(ScreenState.Splash)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    private val _currentNavTab = MutableStateFlow("home")
    val currentNavTab: StateFlow<String> = _currentNavTab.asStateFlow()

    private val _prioritizeSafety = MutableStateFlow(true)
    val prioritizeSafety: StateFlow<Boolean> = _prioritizeSafety.asStateFlow()

    private val _timeOfDay = MutableStateFlow(TimeOfDay.DUSK)
    val timeOfDay: StateFlow<TimeOfDay> = _timeOfDay.asStateFlow()

    private val _selectedRouteId = MutableStateFlow("opt_safe")
    val selectedRouteId: StateFlow<String> = _selectedRouteId.asStateFlow()

    private val _showSosDialog = MutableStateFlow(false)
    val showSosDialog: StateFlow<Boolean> = _showSosDialog.asStateFlow()

    private val _activeRouteOptions = MutableStateFlow<List<RouteOption>>(emptyList())
    val activeRouteOptions: StateFlow<List<RouteOption>> = _activeRouteOptions.asStateFlow()

    // Active Navigation Route for the Navigate Tab
    private val _activeNavRoute = MutableStateFlow<RouteOption?>(null)
    val activeNavRoute: StateFlow<RouteOption?> = _activeNavRoute.asStateFlow()

    // Multi-Language Support
    private val _appLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // Voice SOS Settings
    private val _isVoiceSosEnabled = MutableStateFlow(false)
    val isVoiceSosEnabled: StateFlow<Boolean> = _isVoiceSosEnabled.asStateFlow()

    private val _voiceSosTriggerPhrase = MutableStateFlow("Help me SafeRoute")
    val voiceSosTriggerPhrase: StateFlow<String> = _voiceSosTriggerPhrase.asStateFlow()

    // Group Trip Mode
    private val _activeGroupTrip = MutableStateFlow(GroupTripSession())
    val activeGroupTrip: StateFlow<GroupTripSession> = _activeGroupTrip.asStateFlow()

    // Multiple Contact Trip Sharing (Names of contacts receiving live location)
    private val _sharedTripContacts = MutableStateFlow<Set<String>>(emptySet())
    val sharedTripContacts: StateFlow<Set<String>> = _sharedTripContacts.asStateFlow()

    // Time-based Dusk Route Shift Banner State
    private val _isDuskRouteShiftAvailable = MutableStateFlow(false)
    val isDuskRouteShiftAvailable: StateFlow<Boolean> = _isDuskRouteShiftAvailable.asStateFlow()

    private val _routeErrorMessage = MutableStateFlow<String?>(null)
    val routeErrorMessage: StateFlow<String?> = _routeErrorMessage.asStateFlow()

    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    fun clearRouteError() {
        _routeErrorMessage.value = null
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        LanguageManager.setLanguage(language)
    }

    fun toggleVoiceSos(enabled: Boolean) {
        _isVoiceSosEnabled.value = enabled
    }

    fun setVoiceSosTriggerPhrase(phrase: String) {
        if (phrase.isNotBlank()) {
            _voiceSosTriggerPhrase.value = phrase
        }
    }

    fun startGroupTripMode(inviteCode: String = "SAFEROUTE-CBE-9842") {
        val mockMembers = listOf(
            GroupMember(
                id = "m1",
                name = "Priya",
                phone = "+91 98765 43210",
                avatarInitials = "P",
                currentSafetyScore = 92,
                relativeX = -0.08f,
                relativeY = 0.10f
            ),
            GroupMember(
                id = "m2",
                name = "Anand",
                phone = "+91 98765 11223",
                avatarInitials = "A",
                currentSafetyScore = 84,
                relativeX = 0.05f,
                relativeY = -0.12f
            ),
            GroupMember(
                id = "m3",
                name = "Karthik",
                phone = "+91 98765 99887",
                avatarInitials = "K",
                currentSafetyScore = 68,
                isStationaryAlert = true,
                relativeX = -0.15f,
                relativeY = -0.05f
            )
        )
        _activeGroupTrip.value = GroupTripSession(
            inviteCode = inviteCode,
            isActive = true,
            members = mockMembers
        )
    }

    fun stopGroupTripMode() {
        _activeGroupTrip.value = GroupTripSession(isActive = false, members = emptyList())
    }

    fun toggleContactTripShare(contactName: String) {
        val current = _sharedTripContacts.value.toMutableSet()
        if (current.contains(contactName)) {
            current.remove(contactName)
        } else {
            current.add(contactName)
        }
        _sharedTripContacts.value = current
    }

    fun stopAllContactTripSharing() {
        _sharedTripContacts.value = emptySet()
    }

    fun triggerDuskRouteShiftBanner(show: Boolean) {
        _isDuskRouteShiftAvailable.value = show
    }

    fun setTimeOfDay(time: TimeOfDay) {
        _timeOfDay.value = time
    }


    init {
        viewModelScope.launch {
            repository.initDefaultDataIfEmpty()
        }
    }

    fun onSplashFinished() {
        val profile = userProfile.value
        val firebaseUser = com.example.saferoute.data.firebase.FirebaseAuthManager.currentUser
        if (firebaseUser != null && profile?.isLoggedIn == true) {
            _currentScreen.value = ScreenState.Main
        } else if (profile?.completedOnboarding == true) {
            _currentScreen.value = ScreenState.Auth
        } else {
            _currentScreen.value = ScreenState.Onboarding
        }
    }

    fun finishOnboarding(contactName: String, contactPhone: String, contactRel: String) {
        viewModelScope.launch {
            repository.insertContact(contactName, contactPhone, contactRel, true)
            repository.setOnboardingCompleted(true)
            _currentScreen.value = ScreenState.Auth
        }
    }

    fun loginUser(userId: String, email: String, name: String) {
        viewModelScope.launch {
            repository.logInUser(userId, email, name)
            repository.setOnboardingCompleted(true)
            _currentScreen.value = ScreenState.Main
        }
    }

    // Real User Location Coordinates (Acquired via Accompanist Permission Flow)
    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    private val _userLocationDisplayName = MutableStateFlow("My Location")
    val userLocationDisplayName: StateFlow<String> = _userLocationDisplayName.asStateFlow()

    fun updateUserCoordinates(coords: Pair<Double, Double>, resolvedName: String? = null) {
        _userCoordinates.value = coords
        if (!resolvedName.isNullOrBlank()) {
            _userLocationDisplayName.value = "My Location ($resolvedName)"
        } else {
            viewModelScope.launch {
                val resolved = com.example.saferoute.data.network.GeoapifyService.reverseGeocode(coords.first, coords.second, getApplication())
                if (!resolved.isNullOrBlank()) {
                    _userLocationDisplayName.value = "My Location ($resolved)"
                } else {
                    _userLocationDisplayName.value = "My Location (${String.format(java.util.Locale.US, "%.4f, %.4f", coords.first, coords.second)})"
                }
            }
        }
    }

    fun calculateMultiStopRoute(
        source: String = "My Location",
        destination: String = "TIDEL Park, Peelamedu",
        waypoints: List<String> = emptyList(),
        sourceCoordinates: Pair<Double, Double>? = null,
        destinationCoordinates: Pair<Double, Double>? = null,
        onGeocodeError: ((String) -> Unit)? = null
    ) {
        val src = source.ifBlank { _userLocationDisplayName.value }
        val dst = destination.ifBlank { "TIDEL Park, Peelamedu" }
        _routeErrorMessage.value = null

        viewModelScope.launch {
            _isLoadingRoute.value = true

            // 1. Resolve source coordinates (use live GPS for 'My Location', never a fixed place)
            val resolvedSourceCoords: Pair<Double, Double>? = when {
                sourceCoordinates != null -> sourceCoordinates
                src.startsWith("My Location", ignoreCase = true) -> _userCoordinates.value ?: com.example.saferoute.util.LocationHelper.getCurrentCoordinates(getApplication())
                else -> {
                    val res = com.example.saferoute.data.network.GeoapifyService.geocodePlace(src)
                    if (res != null) Pair(res.latitude, res.longitude) else null
                }
            }

            // 2. Resolve destination coordinates
            val resolvedDestCoords: Pair<Double, Double>? = when {
                destinationCoordinates != null -> destinationCoordinates
                dst.startsWith("My Location", ignoreCase = true) -> _userCoordinates.value ?: com.example.saferoute.util.LocationHelper.getCurrentCoordinates(getApplication())
                else -> {
                    val res = com.example.saferoute.data.network.GeoapifyService.geocodePlace(dst)
                    if (res != null) Pair(res.latitude, res.longitude) else null
                }
            }

            if (resolvedSourceCoords == null || resolvedDestCoords == null) {
                val errorMsg = com.example.saferoute.data.network.GeoapifyService.lastErrorMessage.value ?: "Couldn't find that place"
                android.util.Log.w("SafeRouteViewModel", "Geocoding failed: source='$src' resolved=$resolvedSourceCoords, dest='$dst' resolved=$resolvedDestCoords")
                _routeErrorMessage.value = errorMsg
                _isLoadingRoute.value = false
                onGeocodeError?.invoke(errorMsg)
                return@launch
            }

            // CRITICAL REQUIREMENT: Print resolved source and destination coordinates to logcat
            android.util.Log.d(
                "SafeRouteViewModel",
                "Resolved source coordinates: lat=${resolvedSourceCoords.first}, lng=${resolvedSourceCoords.second}; destination coordinates: lat=${resolvedDestCoords.first}, lng=${resolvedDestCoords.second}"
            )

            val resolvedWaypointsData = mutableListOf<Pair<String, Pair<Double, Double>>>()
            for (wp in waypoints.filter { it.isNotBlank() }) {
                val res = com.example.saferoute.data.network.GeoapifyService.geocodePlace(wp)
                if (res != null) {
                    resolvedWaypointsData.add(Pair(wp, Pair(res.latitude, res.longitude)))
                }
            }

            val routes = repository.computeRealRoutes(
                originName = src,
                destinationName = dst,
                originCoords = resolvedSourceCoords,
                destinationCoords = resolvedDestCoords,
                waypointsData = resolvedWaypointsData,
                timeOfDay = _timeOfDay.value,
                prioritizeSafety = _prioritizeSafety.value
            )

            val finalRoutes = if (routes.isNotEmpty()) {
                routes
            } else {
                repository.computeRouteOptions(
                    origin = src,
                    destination = dst,
                    waypointsNames = waypoints,
                    timeOfDay = _timeOfDay.value,
                    prioritizeSafety = _prioritizeSafety.value,
                    originCoords = resolvedSourceCoords
                )
            }

            // Step 1: Diagnose & log route point counts, lengths, and safety score components
            finalRoutes.forEachIndexed { idx, r ->
                android.util.Log.d(
                    "SafeRouteViewModel",
                    "Route [$idx] ID='${r.id}' Name='${r.name}' Points=${r.pathCoordinates.size} " +
                        "Length=${r.distanceKm}km (${(r.distanceKm * 1000).toInt()}m) " +
                        "Duration=${r.durationMinutes}m -> Score=${r.safetyDetails.compositeScore}/100 " +
                        "[Lighting=${r.safetyDetails.lightingFactor}, Crowd=${r.safetyDetails.crowdDensityFactor}, " +
                        "Incident=${r.safetyDetails.incidentHistoryFactor}, TimeOfDayFactor=${r.safetyDetails.timeOfDayFactor}, " +
                        "IsRecommended=${r.isRecommended}]"
                )
            }

            if (finalRoutes.isEmpty()) {
                val errorMsg = com.example.saferoute.data.network.GeoapifyService.lastErrorMessage.value ?: "Couldn't find that place"
                _routeErrorMessage.value = errorMsg
                _isLoadingRoute.value = false
                onGeocodeError?.invoke(errorMsg)
                return@launch
            }

            _activeRouteOptions.value = finalRoutes
            _selectedRouteId.value = finalRoutes.firstOrNull()?.id ?: "opt_safe"
            _currentScreen.value = ScreenState.RouteRecommendations(
                source = src,
                destination = dst,
                waypoints = waypoints
            )
            _isLoadingRoute.value = false
        }
    }

    fun selectDestination(destination: String) {
        calculateMultiStopRoute(
            source = _userLocationDisplayName.value,
            destination = destination,
            waypoints = emptyList()
        )
    }

    fun setPrioritizeSafety(prioritize: Boolean) {
        _prioritizeSafety.value = prioritize
        val currentRoutes = _activeRouteOptions.value
        if (currentRoutes.isNotEmpty()) {
            val sorted = if (prioritize) {
                currentRoutes.sortedByDescending { it.safetyDetails.compositeScore }
            } else {
                currentRoutes.sortedBy { it.durationMinutes }
            }
            _activeRouteOptions.value = sorted
            _selectedRouteId.value = sorted.firstOrNull()?.id ?: "opt_safe"
        }
    }

    fun selectRoute(routeId: String) {
        _selectedRouteId.value = routeId
    }

    fun startNavigation(route: RouteOption) {
        _activeNavRoute.value = route
        _currentNavTab.value = "navigate"
        _currentScreen.value = ScreenState.Main
    }

    fun cancelNavigation() {
        _activeNavRoute.value = null
        _currentNavTab.value = "home"
        _currentScreen.value = ScreenState.Main
    }

    fun completeTrip(route: RouteOption) {
        _activeNavRoute.value = null
        val durationSec = (route.durationMinutes * 60).toLong()
        viewModelScope.launch {
            val primaryContactName = contacts.value.firstOrNull()?.name ?: "Sister"
            repository.recordTrip(
                origin = _userLocationDisplayName.value,
                destination = route.name,
                durationSeconds = durationSec,
                distanceKm = route.distanceKm,
                avgSafetyScore = route.safetyDetails.compositeScore,
                routeTypeName = route.name,
                rating = 5,
                feedback = "Safe illuminate route taken.",
                sharedWith = primaryContactName
            )
            _currentScreen.value = ScreenState.TripSummary(
                route = route,
                durationSeconds = durationSec,
                distanceKm = route.distanceKm,
                avgScore = route.safetyDetails.compositeScore
            )
        }
    }

    fun saveTripFeedback(tripId: Long, rating: Int, feedback: String) {
        viewModelScope.launch {
            repository.updateTripFeedback(tripId, rating, feedback)
        }
    }

    fun navigateToTab(tabRoute: String) {
        _currentNavTab.value = tabRoute
        _currentScreen.value = ScreenState.Main
    }

    fun openSosDialog() {
        _showSosDialog.value = true
    }

    fun closeSosDialog() {
        _showSosDialog.value = false
    }

    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            repository.insertContact(name, phone, relationship, false)
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun addSavedPlace(title: String, address: String, icon: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            repository.savePlace(title, address, icon, lat, lng)
        }
    }

    fun updateSafetyPreference(pref: String) {
        viewModelScope.launch {
            repository.updateSafetyPreference(pref)
        }
    }

    fun setPlusMembership(isPlus: Boolean) {
        viewModelScope.launch {
            repository.setPlusMembership(isPlus)
        }
    }

    fun getTurnInstructions(routeId: String): List<TurnInstruction> {
        return repository.getTurnInstructionsForRoute(routeId)
    }

    fun getIncidentZones(): List<IncidentZone> {
        return repository.getIncidentZones()
    }

    // Firebase Cloud Sync
    val firebaseStatus: StateFlow<FirebaseStatusInfo> = FirebaseConnectionManager.statusInfo

    fun syncWithFirebase(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val profile = userProfile.firstOrNull() ?: UserProfileEntity()
            val contactList = contacts.firstOrNull() ?: emptyList()
            val placeList = savedPlaces.firstOrNull() ?: emptyList()
            val tripList = tripHistory.firstOrNull() ?: emptyList()

            val userId = profile.userId.ifBlank { "user_default" }
            val result = FirebaseConnectionManager.triggerFullCloudSync(
                userId = userId,
                profile = profile,
                contacts = contactList,
                places = placeList,
                trips = tripList
            )

            result.fold(
                onSuccess = { count ->
                    onComplete(true, "Successfully synced $count records to Firebase Firestore!")
                },
                onFailure = { ex ->
                    onComplete(false, ex.localizedMessage ?: "Sync failed. Please check connection.")
                }
            )
        }
    }

    fun configureCustomFirebase(
        projectId: String,
        apiKey: String,
        appId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = FirebaseConnectionManager.configureCustomFirebase(
                context = getApplication(),
                projectId = projectId,
                apiKey = apiKey,
                applicationId = appId
            )
            result.fold(
                onSuccess = { msg -> onResult(true, msg) },
                onFailure = { ex -> onResult(false, ex.localizedMessage ?: "Failed to configure Firebase.") }
            )
        }
    }

    fun navigateHome() {
        _currentScreen.value = ScreenState.Main
        _currentNavTab.value = "home"
    }

    fun logout() {
        viewModelScope.launch {
            com.example.saferoute.data.firebase.FirebaseAuthManager.signOut()
            repository.logOutUser()
            _activeNavRoute.value = null
            _currentScreen.value = ScreenState.Auth
        }
    }
}
