package com.example.saferoute.ui.viewmodel

import android.app.Application
import android.content.Context
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
import com.example.saferoute.data.repository.SafeRouteRepository
import com.example.saferoute.data.firebase.AuthResult
import com.example.saferoute.data.firebase.FirebaseAuthManager
import com.example.saferoute.util.SosManager
import com.example.saferoute.util.AppLanguage
import com.example.saferoute.util.LanguageManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
        db.userOnboardingDao(),
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

    // ── Authentication state ──
    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private val _resetPasswordMessage = MutableStateFlow<String?>(null)
    val resetPasswordMessage: StateFlow<String?> = _resetPasswordMessage.asStateFlow()

    private val _resetPasswordLoading = MutableStateFlow(false)
    val resetPasswordLoading: StateFlow<Boolean> = _resetPasswordLoading.asStateFlow()

    private val _authReady = MutableStateFlow(false)
    val authReady: StateFlow<Boolean> = _authReady.asStateFlow()

    // ── SOS execution state ──
    private val _sosState = MutableStateFlow(SosUiState())
    val sosState: StateFlow<SosUiState> = _sosState.asStateFlow()

    private val _activeRouteOptions = MutableStateFlow<List<RouteOption>>(emptyList())
    val activeRouteOptions: StateFlow<List<RouteOption>> = _activeRouteOptions.asStateFlow()

    // Active Navigation Route for the Navigate Tab
    private val _activeNavRoute = MutableStateFlow<RouteOption?>(null)
    val activeNavRoute: StateFlow<RouteOption?> = _activeNavRoute.asStateFlow()

    // Multi-Language Support
    private val _appLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()


    // Group Trip Mode
    private val _activeGroupTrip = MutableStateFlow(GroupTripSession())
    val activeGroupTrip: StateFlow<GroupTripSession> = _activeGroupTrip.asStateFlow()

    // Multiple Contact Trip Sharing (Names of contacts receiving live location)
    private val _sharedTripContacts = MutableStateFlow<Set<String>>(emptySet())
    val sharedTripContacts: StateFlow<Set<String>> = _sharedTripContacts.asStateFlow()

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

    fun setTimeOfDay(time: TimeOfDay) {
        _timeOfDay.value = time
    }


    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        _authReady.value = true
        if (auth.currentUser == null && _currentScreen.value is ScreenState.Main) {
            // Session was revoked/signed out externally: return to Auth, never leave a ghost session.
            _currentScreen.value = ScreenState.Auth
        }
    }

    init {
        viewModelScope.launch {
            repository.initDefaultDataIfEmpty()
        }

        try {
            FirebaseAuthManager.auth?.addAuthStateListener(authStateListener)
        } catch (e: Exception) {
            android.util.Log.w("SafeRouteViewModel", "AuthStateListener unavailable: ${e.message}")
        }

        // FirebaseAuth already knows the persisted session synchronously in most launches.
        _authReady.value = FirebaseAuthManager.auth != null
    }

    /**
     * Splash is allowed to finish only after Firebase has reported the persisted session.
     * This guarantees reopening the app goes straight to Home for a signed-in user.
     */
    fun onSplashFinished() {
        viewModelScope.launch {
            if (!_authReady.value) {
                withTimeoutOrNull(5_000) {
                    _authReady.first { it }
                }
            }
            val user = FirebaseAuthManager.currentUser
            if (user == null) {
                _currentScreen.value = ScreenState.Auth
                return@launch
            }

            val completed = repository.restoreSession(
                userId = user.uid,
                email = user.email.orEmpty(),
                name = user.displayName.orEmpty()
            )
            _currentScreen.value = if (completed) ScreenState.Main else ScreenState.Onboarding
            _currentNavTab.value = "home"
        }
    }

    fun finishOnboarding(contactName: String, contactPhone: String, contactRel: String) {
        viewModelScope.launch {
            if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                repository.insertContact(contactName, contactPhone, contactRel.ifBlank { "Contact" }, true)
            }

            val user = FirebaseAuthManager.currentUser
            if (user != null) {
                repository.setOnboardingCompleted(true)
                repository.setOnboardingCompletedForUser(user.uid, true)
                _currentScreen.value = ScreenState.Main
                _currentNavTab.value = "home"
            } else {
                repository.setOnboardingCompleted(true)
                _currentScreen.value = ScreenState.Auth
            }
        }
    }

    // ── Real authentication actions (launched from the ViewModel scope) ──

    fun signInWithGoogle(activityContext: Context) {
        if (_authUiState.value is AuthUiState.Loading) return
        _authUiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = FirebaseAuthManager.signInWithGoogle(activityContext)
            handleAuthResult(result)
        }
    }

    fun signInWithEmail(name: String, email: String, password: String, isSignUp: Boolean) {
        if (_authUiState.value is AuthUiState.Loading) return
        _authUiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = if (isSignUp) {
                FirebaseAuthManager.signUpWithEmail(name, email, password)
            } else {
                FirebaseAuthManager.logInWithEmail(email, password)
            }
            handleAuthResult(result)
        }
    }

    private suspend fun handleAuthResult(result: AuthResult) {
        when (result) {
            is AuthResult.Success -> {
                val completedOnboarding = repository.logInUser(
                    userId = result.uid,
                    email = result.email,
                    name = result.name,
                    isNewUser = result.isNewUser
                )
                _authUiState.value = AuthUiState.Idle
                _authMessage.value = if (result.isNewUser) {
                    "Welcome, ${result.name}"
                } else {
                    "Welcome back, ${result.name}"
                }
                _currentScreen.value = if (completedOnboarding) ScreenState.Main else ScreenState.Onboarding
                _currentNavTab.value = "home"
            }
            is AuthResult.Error -> {
                _authUiState.value = AuthUiState.Error(result.message)
            }
        }
    }

    fun clearAuthMessage() {
        _authMessage.value = null
    }

    fun clearAuthError() {
        if (_authUiState.value is AuthUiState.Error) {
            _authUiState.value = AuthUiState.Idle
        }
    }

    fun resetPassword(email: String) {
        if (_resetPasswordLoading.value) return
        _resetPasswordLoading.value = true
        _resetPasswordMessage.value = null
        viewModelScope.launch {
            val result = FirebaseAuthManager.sendPasswordReset(email)
            _resetPasswordLoading.value = false
            _resetPasswordMessage.value = result.getOrNull()
                ?: result.exceptionOrNull()?.message
                ?: "Could not send the reset email."
        }
    }

    fun clearResetPasswordMessage() {
        _resetPasswordMessage.value = null
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

    // Last request parameters for one-tap retry after timeout
    private data class LastRouteRequest(
        val source: String,
        val destination: String,
        val waypoints: List<String>,
        val sourceCoordinates: Pair<Double, Double>?,
        val destinationCoordinates: Pair<Double, Double>?
    )
    private var lastRouteRequest: LastRouteRequest? = null

    fun retryLastRouteRequest() {
        lastRouteRequest?.let { req ->
            calculateMultiStopRoute(
                source = req.source,
                destination = req.destination,
                waypoints = req.waypoints,
                sourceCoordinates = req.sourceCoordinates,
                destinationCoordinates = req.destinationCoordinates
            )
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
        lastRouteRequest = LastRouteRequest(source, destination, waypoints, sourceCoordinates, destinationCoordinates)
        val requestStart = System.currentTimeMillis()

        viewModelScope.launch {
            _isLoadingRoute.value = true

            val geocodeStart = System.currentTimeMillis()

            // 1+2+3: Resolve source / destination / waypoints coordinates IN PARALLEL
            // (live GPS for 'My Location', never a fixed place)
            suspend fun coordOf(name: String, override: Pair<Double, Double>?, useLiveGps: Boolean): Pair<Double, Double>? {
                if (override != null) return override
                if (useLiveGps) {
                    val stored = _userCoordinates.value
                    if (stored != null) return stored
                    return com.example.saferoute.util.LocationHelper.getCurrentCoordinates(getApplication())
                }
                return geocodePlaceCached(name)?.let { geocoded -> Pair(geocoded.latitude, geocoded.longitude) }
            }

            val (resolvedSourceCoords, resolvedDestCoords, resolvedWaypointsData) = kotlinx.coroutines.coroutineScope {
                val deferredSource = async {
                    coordOf(src, sourceCoordinates, src.startsWith("My Location", ignoreCase = true))
                }
                val deferredDest = async {
                    coordOf(dst, destinationCoordinates, dst.startsWith("My Location", ignoreCase = true))
                }
                val deferredWaypoints = waypoints.filter { it.isNotBlank() }.map { wp ->
                    async { Pair(wp, geocodePlaceCached(wp)) }
                }
                Triple(
                    deferredSource.await(),
                    deferredDest.await(),
                    deferredWaypoints.mapNotNull { def ->
                        val (wp, res) = def.await()
                        res?.let { Pair(wp, Pair(it.latitude, it.longitude)) }
                    }
                )
            }

            android.util.Log.d(
                "ROUTE_PERF",
                "geocoding=${System.currentTimeMillis() - geocodeStart}ms (source=${resolvedSourceCoords != null}, dest=${resolvedDestCoords != null}, stops=${resolvedWaypointsData.size})"
            )

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

            val routingStart = System.currentTimeMillis()
            val routes = repository.computeRealRoutes(
                originName = src,
                destinationName = dst,
                originCoords = resolvedSourceCoords,
                destinationCoords = resolvedDestCoords,
                waypointsData = resolvedWaypointsData,
                timeOfDay = _timeOfDay.value,
                prioritizeSafety = _prioritizeSafety.value
            )
            android.util.Log.d("ROUTE_PERF", "routing=${System.currentTimeMillis() - routingStart}ms routes=${routes.size}")

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
                        "IsRecommended=${r.isRecommended}, Pending=${r.safetyDetails.isEnrichmentPending}]"
                )
            }

            if (finalRoutes.isEmpty()) {
                val errorMsg = com.example.saferoute.data.network.GeoapifyService.lastErrorMessage.value ?: "Couldn't find that place"
                _routeErrorMessage.value = errorMsg
                _isLoadingRoute.value = false
                onGeocodeError?.invoke(errorMsg)
                return@launch
            }

            // ── SHOW ROUTES IMMEDIATELY — enrichment continues in the background ──
            _activeRouteOptions.value = finalRoutes
            _selectedRouteId.value = finalRoutes.firstOrNull()?.id ?: "opt_safe"
            _currentScreen.value = ScreenState.RouteRecommendations(
                source = src,
                destination = dst,
                waypoints = waypoints
            )
            _isLoadingRoute.value = false
            android.util.Log.d("ROUTE_PERF", "routes_visible_after=${System.currentTimeMillis() - requestStart}ms from request start")

            // ── BACKGROUND ENRICHMENT (corridor/POI + risk matching + scoring) ──
            val enrichStart = System.currentTimeMillis()
            val enriched = repository.enrichRoutesInBackground(
                routes = finalRoutes,
                timeOfDay = _timeOfDay.value,
                prioritizeSafety = _prioritizeSafety.value,
                incidentZones = repository.getIncidentZones(),
                routeCacheKey = com.example.saferoute.data.network.GeoapifyService.buildRouteCacheKey(
                    listOf(resolvedSourceCoords) + resolvedWaypointsData.map { it.second } + listOf(resolvedDestCoords),
                    _timeOfDay.value,
                    repository.getIncidentZones().size
                ),
                onRouteEnriched = { routeId, enrichedRoute ->
                    // Progressive fill: replace the matching entry so chips populate one by one
                    isActiveRouteStale(enrichedRoute.id).also { isSameRequest ->
                        if (isSameRequest) {
                            _activeRouteOptions.value = _activeRouteOptions.value.map {
                                if (it.id == enrichedRoute.id) enrichedRoute else it
                            }
                        }
                    }
                }
            )
            android.util.Log.d("ROUTE_PERF", "enrichment=${System.currentTimeMillis() - enrichStart}ms total_request=${System.currentTimeMillis() - requestStart}ms")

            // Only replace the final enriched list if a new request hasn't already replaced it
            if (_currentScreen.value is ScreenState.RouteRecommendations) {
                _activeRouteOptions.value = enriched
            }
        }
    }

    /** Guards progressive updates: only apply enrichment to the request the screen is currently showing. */
    private fun isActiveRouteStale(routeId: String): Boolean {
        val idsCurrent = _activeRouteOptions.value.map { it.id }
        return idsCurrent.contains(routeId)
    }

    /** Cached geocode wrapper (GeoapifyService already memoizes; keeps call sites clean). */
    private suspend fun geocodePlaceCached(name: String) =
        com.example.saferoute.data.network.GeoapifyService.geocodePlace(name)

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
        _sosState.value = SosUiState(phase = SosPhase.COUNTDOWN)
        _showSosDialog.value = true
    }

    fun closeSosDialog() {
        if (_sosState.value.isSending) return
        _showSosDialog.value = false
        _sosState.value = SosUiState()
    }

    fun showSosPermissionRationale() {
        _sosState.value = _sosState.value.copy(phase = SosPhase.PERMISSION_RATIONALE)
    }

    /**
     * Called when the 5-second countdown reaches zero. If call/SMS permissions were already
     * granted we start immediately; otherwise we show our own explanation before the system
     * permission prompt appears.
     */
    fun onSosCountdownFinished() {
        val context = getApplication<Application>()
        if (SosManager.hasCallPermission(context) && SosManager.hasSmsPermission(context)) {
            triggerSosAlerts()
        } else {
            showSosPermissionRationale()
        }
    }

    /** Called after the countdown and (if needed) after the runtime permission result. */
    fun triggerSosAlerts() {
        if (_sosState.value.isSending) return
        val contactSnapshot = contacts.value
        val context = getApplication<Application>()

        viewModelScope.launch {
            val alsoCall112 = repository.getAlsoCall112()
            val userName = userProfile.value?.name
                ?.takeIf { it.isNotBlank() }
                ?: FirebaseAuthManager.currentUser?.displayName
                ?: "A SafeRoute user"

            val primaryContact = contactSnapshot.firstOrNull()
            val initialSteps = buildList {
                add(
                    SosStep(
                        id = "call",
                        title = if (primaryContact != null) "Calling ${primaryContact.name}" else "Calling emergency contact"
                    )
                )
                add(SosStep(id = "sms", title = "Sending SMS to ${contactSnapshot.size} contacts"))
                if (alsoCall112) add(SosStep(id = "112", title = "Calling 112"))
            }
            _sosState.value = SosUiState(
                phase = SosPhase.SENDING,
                steps = initialSteps,
                isSending = true
            )

            val latitude = _userCoordinates.value?.first
            val longitude = _userCoordinates.value?.second
            val effectiveLocation = _userCoordinates.value ?: try {
                withTimeoutOrNull(2_500) {
                    com.example.saferoute.util.LocationHelper.getCurrentCoordinates(context)
                }
            } catch (_: Throwable) {
                null
            }
            val message = SosManager.buildEmergencyMessage(
                userName = userName,
                latitude = effectiveLocation?.first,
                longitude = effectiveLocation?.second
            )

            var callStarted = false
            if (primaryContact == null) {
                updateSosStep(
                    "call",
                    SosStepStatus.FAILED,
                    "No emergency contact saved. Add one in Profile first."
                )
            } else {
                updateSosStep("call", SosStepStatus.RUNNING, "Dialing ${primaryContact.phone}…")
                val outcome = SosManager.startCall(context, primaryContact.phone)
                callStarted = outcome.started
                if (outcome.started) {
                    updateSosStep(
                        "call",
                        SosStepStatus.SUCCESS,
                        if (outcome.usedDialerFallback) {
                            "Dialer opened — tap the call button to connect."
                        } else {
                            "Call started to ${primaryContact.name}."
                        }
                    )
                } else {
                    updateSosStep(
                        "call",
                        SosStepStatus.FAILED,
                        outcome.error ?: "Could not start the call."
                    )
                }
            }

            var smsSent = 0
            val smsFailures = mutableListOf<String>()
            if (contactSnapshot.isEmpty()) {
                updateSosStep(
                    "sms",
                    SosStepStatus.FAILED,
                    "No emergency contacts saved, so no SMS could be sent."
                )
            } else {
                updateSosStep("sms", SosStepStatus.RUNNING, "Sending location link…")
                for (contact in contactSnapshot) {
                    val result = SosManager.sendSms(context, contact.phone, message)
                    if (result.sent) {
                        smsSent++
                    } else {
                        smsFailures.add("${contact.name}: ${result.error ?: "failed"}")
                    }
                }
                when {
                    smsSent > 0 && smsFailures.isEmpty() ->
                        updateSosStep(
                            "sms",
                            SosStepStatus.SUCCESS,
                            "SMS sent to $smsSent ${if (smsSent == 1) "contact" else "contacts"}."
                        )
                    smsSent > 0 ->
                        updateSosStep(
                            "sms",
                            SosStepStatus.SUCCESS,
                            "SMS sent to $smsSent of ${contactSnapshot.size} contacts. Failed — ${smsFailures.joinToString("; ")}"
                        )
                    else ->
                        updateSosStep(
                            "sms",
                            SosStepStatus.FAILED,
                            "SMS failed for all contacts. ${smsFailures.joinToString("; ")}"
                        )
                }
            }

            var call112Started = false
            if (alsoCall112) {
                updateSosStep("112", SosStepStatus.RUNNING, "Dialing 112…")
                val policeOutcome = SosManager.startCall(context, "112")
                call112Started = policeOutcome.started
                if (policeOutcome.started) {
                    updateSosStep(
                        "112",
                        SosStepStatus.SUCCESS,
                        if (policeOutcome.usedDialerFallback) "Dialer opened for 112." else "112 call started."
                    )
                } else {
                    updateSosStep("112", SosStepStatus.FAILED, policeOutcome.error ?: "Could not call 112.")
                }
            }

            val allSmsSent = contactSnapshot.isNotEmpty() && smsSent == contactSnapshot.size
            val summary = when {
                callStarted && allSmsSent -> "Emergency alert sent"
                callStarted || smsSent > 0 || call112Started ->
                    "Emergency alert partially sent — review the steps above"
                else -> "Emergency alert could not be sent"
            }
            val alertStarted = callStarted || smsSent > 0 || call112Started

            _sosState.value = _sosState.value.copy(
                phase = SosPhase.RESULT,
                isSending = false,
                summary = summary,
                canRetry = !(callStarted && allSmsSent && (!alsoCall112 || call112Started)),
                alertStarted = alertStarted
            )
        }
    }

    fun retrySosAlerts() {
        if (_sosState.value.isSending) return
        _sosState.value = SosUiState(phase = SosPhase.SENDING)
        triggerSosAlerts()
    }

    private fun updateSosStep(id: String, status: SosStepStatus, detail: String?) {
        _sosState.value = _sosState.value.copy(
            steps = _sosState.value.steps.map { step ->
                if (step.id == id) step.copy(status = status, detail = detail) else step
            }
        )
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

    fun updateSavedPlace(place: SavedPlaceEntity) {
        viewModelScope.launch {
            repository.updatePlace(place)
        }
    }

    fun deleteSavedPlace(place: SavedPlaceEntity) {
        viewModelScope.launch {
            repository.deletePlace(place)
        }
    }

    fun updateSafetyPreference(pref: String) {
        viewModelScope.launch {
            repository.updateSafetyPreference(pref)
        }
    }

    fun setAlsoCall112(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAlsoCall112(enabled)
        }
    }

    /** Re-runs the last route request, which also retries any chunks that timed out. */
    fun retryEnrichment() {
        retryLastRouteRequest()
    }

    fun getTurnInstructions(routeId: String): List<TurnInstruction> {
        return repository.getTurnInstructionsForRoute(routeId)
    }

    fun getIncidentZones(): List<IncidentZone> {
        return repository.getIncidentZones()
    }

    fun navigateHome() {
        _currentScreen.value = ScreenState.Main
        _currentNavTab.value = "home"
    }

    fun logout() {
        viewModelScope.launch {
            repository.logOutUser()
            _activeNavRoute.value = null
            _currentNavTab.value = "home"
            _showSosDialog.value = false
            _sosState.value = SosUiState()
            _authUiState.value = AuthUiState.Idle
            _authMessage.value = null
            _currentScreen.value = ScreenState.Auth
        }
    }

    override fun onCleared() {
        try {
            FirebaseAuthManager.auth?.removeAuthStateListener(authStateListener)
        } catch (_: Throwable) {
        }
        super.onCleared()
    }
}
