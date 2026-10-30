package com.example

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.saferoute.data.db.UserProfileEntity
import com.example.saferoute.data.model.RouteOption
import com.example.saferoute.ui.components.AppErrorBoundary
import com.example.saferoute.ui.components.BottomNavBar
import com.example.saferoute.ui.components.NavTab
import com.example.saferoute.ui.components.SafetyDetailsDrawer
import com.example.saferoute.ui.components.SosCountdownDialog
import com.example.saferoute.ui.components.SosFab
import com.example.saferoute.ui.screens.ActiveNavigationScreen
import com.example.saferoute.ui.screens.AuthScreen
import com.example.saferoute.ui.screens.HomeScreen
import com.example.saferoute.ui.screens.OnboardingScreen
import com.example.saferoute.ui.screens.ProfileScreen
import com.example.saferoute.ui.screens.RouteRecommendationScreen
import com.example.saferoute.ui.screens.SplashScreen
import com.example.saferoute.ui.screens.TripHistoryScreen
import com.example.saferoute.ui.screens.TripSummaryScreen
import com.example.saferoute.ui.viewmodel.ScreenState
import com.example.saferoute.ui.viewmodel.SafeRouteViewModel
import com.example.saferoute.util.SosManager
import com.example.ui.theme.SafeRouteTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SafeRouteViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appLanguage by viewModel.appLanguage.collectAsState()
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.saferoute.util.LocalAppLanguage provides appLanguage
            ) {
                SafeRouteTheme {
                    val screenState by viewModel.currentScreen.collectAsState()
                val navTab by viewModel.currentNavTab.collectAsState()
                val contacts by viewModel.contacts.collectAsState()
                val trips by viewModel.tripHistory.collectAsState()
                val savedPlaces by viewModel.savedPlaces.collectAsState()
                val profile by viewModel.userProfile.collectAsState()
                val prioritizeSafety by viewModel.prioritizeSafety.collectAsState()
                val selectedRouteId by viewModel.selectedRouteId.collectAsState()
                val activeRoutes by viewModel.activeRouteOptions.collectAsState()
                val activeNavRoute by viewModel.activeNavRoute.collectAsState()
                val showSosDialog by viewModel.showSosDialog.collectAsState()
                val appLanguage by viewModel.appLanguage.collectAsState()
                val activeGroupTrip by viewModel.activeGroupTrip.collectAsState()
                val sharedTripContacts by viewModel.sharedTripContacts.collectAsState()
                val userCoordinates by viewModel.userCoordinates.collectAsState()
                val authUiState by viewModel.authUiState.collectAsState()
                val authMessage by viewModel.authMessage.collectAsState()
                val resetPasswordMessage by viewModel.resetPasswordMessage.collectAsState()
                val resetPasswordLoading by viewModel.resetPasswordLoading.collectAsState()
                val sosState by viewModel.sosState.collectAsState()

                val context = LocalContext.current
                val sosPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    // Start the real SOS regardless: ACTION_CALL falls back to the dialer, and a
                    // denied SMS permission is reported as a failed step instead of fake success.
                    viewModel.triggerSosAlerts()
                }

                LaunchedEffect(authMessage) {
                    authMessage?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        viewModel.clearAuthMessage()
                    }
                }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var activeDrawerRoute by remember { mutableStateOf<RouteOption?>(null) }
                var showEndTripDialog by remember { mutableStateOf(false) }
                var lastBackPressAt by remember { mutableLongStateOf(0L) }

                val defaultProfile = profile ?: UserProfileEntity(
                    name = "SafeRoute User",
                    email = "",
                    isLoggedIn = true,
                    safetyPreference = "ALWAYS_SAFETY",
                    highContrastMode = false,
                    completedOnboarding = false
                )

                val showBottomBarAndSos = screenState is ScreenState.Main

                BackHandler(enabled = true) {
                    when {
                        showSosDialog -> {
                            // The SOS dialog owns back while it is on screen; sending cannot be
                            // interrupted mid-flight.
                            if (!sosState.isSending) viewModel.closeSosDialog()
                        }

                        activeDrawerRoute != null -> activeDrawerRoute = null

                        screenState is ScreenState.ActiveNav -> showEndTripDialog = true

                        screenState is ScreenState.RouteRecommendations ||
                            screenState is ScreenState.TripSummary -> viewModel.navigateHome()

                        screenState is ScreenState.Main -> {
                            when {
                                navTab == "navigate" && activeNavRoute != null ->
                                    showEndTripDialog = true

                                navTab != "home" -> viewModel.navigateToTab("home")

                                else -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBackPressAt <= 2_000L) {
                                        finish()
                                    } else {
                                        lastBackPressAt = now
                                        Toast.makeText(
                                            context,
                                            "Press back again to exit",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }

                        // Splash / Auth / Onboarding: consume back so the app never closes by accident.
                        else -> Unit
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBarAndSos) {
                            BottomNavBar(
                                currentRoute = navTab,
                                onTabSelected = { selectedTab ->
                                    viewModel.navigateToTab(selectedTab.route)
                                }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (showBottomBarAndSos) {
                            SosFab(
                                onClick = { viewModel.openSosDialog() },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppErrorBoundary(
                            fallbackTitle = "SafeRoute Navigation Alert",
                            onReset = { viewModel.navigateHome() }
                        ) {
                            AnimatedContent(
                                targetState = screenState,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "MainScreenTransition"
                            ) { state ->
                                when (state) {
                                    is ScreenState.Splash -> {
                                        SplashScreen(onSplashFinished = { viewModel.onSplashFinished() })
                                    }
                                    is ScreenState.Onboarding -> {
                                        OnboardingScreen(
                                            onFinishOnboarding = { name, phone, rel ->
                                                viewModel.finishOnboarding(name, phone, rel)
                                            }
                                        )
                                    }
                                    is ScreenState.Auth -> {
                                        AuthScreen(
                                            authState = authUiState,
                                            resetPasswordMessage = resetPasswordMessage,
                                            resetPasswordLoading = resetPasswordLoading,
                                            onGoogleSignIn = {
                                                viewModel.signInWithGoogle(this@MainActivity)
                                            },
                                            onEmailAuth = { isSignUp, name, email, password ->
                                                viewModel.signInWithEmail(name, email, password, isSignUp)
                                            },
                                            onResetPassword = { email -> viewModel.resetPassword(email) },
                                            onClearResetMessage = { viewModel.clearResetPasswordMessage() }
                                        )
                                    }
                                    is ScreenState.Main -> {
                                        when (navTab) {
                                            "home" -> HomeScreen(
                                                savedPlaces = savedPlaces,
                                                recentTrips = trips,
                                                incidentZones = viewModel.getIncidentZones(),
                                                activeGroupTrip = activeGroupTrip,
                                                userLocationCoordinates = userCoordinates,
                                                onUpdateUserCoordinates = { coords, resolved ->
                                                    viewModel.updateUserCoordinates(coords, resolved)
                                                },
                                                onSelectRoute = { src, dest, waypoints, srcCoords, dstCoords ->
                                                    viewModel.calculateMultiStopRoute(src, dest, waypoints, srcCoords, dstCoords)
                                                },
                                                onNavigateToAuth = { viewModel.logout() },
                                                onSaveSavedPlace = { title, icon, address, lat, lng ->
                                                    viewModel.addSavedPlace(title, address, icon, lat, lng)
                                                },
                                                onUpdateSavedPlace = { viewModel.updateSavedPlace(it) },
                                                onDeleteSavedPlace = { viewModel.deleteSavedPlace(it) },
                                                onStartGroupTrip = { viewModel.startGroupTripMode() }
                                            )
                                            "navigate" -> {
                                                val activeRoute = activeNavRoute
                                                if (activeRoute != null) {
                                                    ActiveNavigationScreen(
                                                        route = activeRoute,
                                                        instructions = viewModel.getTurnInstructions(activeRoute.id),
                                                        emergencyContactName = contacts.firstOrNull()?.name ?: "Emergency contact",
                                                        contacts = contacts,
                                                        incidentZones = viewModel.getIncidentZones(),
                                                        activeGroupTrip = activeGroupTrip,
                                                        sharedTripContacts = sharedTripContacts,
                                                        onToggleContactShare = { name -> viewModel.toggleContactTripShare(name) },
                                                        onStopAllSharing = { viewModel.stopAllContactTripSharing() },
                                                        onSwitchRoute = { viewModel.calculateMultiStopRoute() },
                                                        onTriggerSos = { viewModel.openSosDialog() },
                                                        onCompleteTrip = { viewModel.completeTrip(activeRoute) },
                                                        onCancelTrip = { viewModel.cancelNavigation() }
                                                    )
                                                } else {
                                                    com.example.saferoute.ui.screens.NavigateEmptyStateScreen(
                                                        onSearchDestination = { viewModel.navigateToTab("home") },
                                                        onSelectRecentTrip = { trip -> viewModel.selectDestination(trip.destinationName) },
                                                        onSelectSavedPlace = { place -> viewModel.selectDestination(place.title) },
                                                        recentTrips = trips,
                                                        savedPlaces = savedPlaces
                                                    )
                                                }
                                            }
                                            "history" -> TripHistoryScreen(
                                                trips = trips,
                                                onSelectTrip = { trip ->
                                                    viewModel.selectDestination(trip.destinationName)
                                                },
                                                onNavigateHome = { viewModel.navigateHome() }
                                            )
                                            "profile" -> ProfileScreen(
                                                userProfile = defaultProfile,
                                                appLanguage = appLanguage,
                                                contacts = contacts,
                                                onAddContact = { n, p, r -> viewModel.addContact(n, p, r) },
                                                onDeleteContact = { c -> viewModel.deleteContact(c) },
                                                onUpdateSafetyPref = { pref -> viewModel.updateSafetyPreference(pref) },
                                                alsoCall112 = defaultProfile.alsoCall112,
                                                onToggleAlsoCall112 = { enabled -> viewModel.setAlsoCall112(enabled) },
                                                onLogout = { viewModel.logout() }
                                            )
                                            else -> HomeScreen(
                                                savedPlaces = savedPlaces,
                                                recentTrips = trips,
                                                incidentZones = viewModel.getIncidentZones(),
                                                activeGroupTrip = activeGroupTrip,
                                                userLocationCoordinates = userCoordinates,
                                                onUpdateUserCoordinates = { coords, resolved ->
                                                    viewModel.updateUserCoordinates(coords, resolved)
                                                },
                                                onSelectRoute = { src, dest, waypoints, srcCoords, dstCoords ->
                                                    viewModel.calculateMultiStopRoute(src, dest, waypoints, srcCoords, dstCoords)
                                                },
                                                onNavigateToAuth = { viewModel.logout() },
                                                onSaveSavedPlace = { title, icon, address, lat, lng ->
                                                    viewModel.addSavedPlace(title, address, icon, lat, lng)
                                                },
                                                onUpdateSavedPlace = { viewModel.updateSavedPlace(it) },
                                                onDeleteSavedPlace = { viewModel.deleteSavedPlace(it) },
                                                onStartGroupTrip = { viewModel.startGroupTripMode() }
                                            )
                                        }
                                    }
                                    is ScreenState.RouteRecommendations -> {
                                        RouteRecommendationScreen(
                                            sourceName = state.source,
                                            destinationName = state.destination,
                                            waypoints = state.waypoints,
                                            routes = activeRoutes,
                                            selectedRouteId = selectedRouteId,
                                            prioritizeSafety = prioritizeSafety,
                                            incidentZones = viewModel.getIncidentZones(),
                                            onUpdateMultiStopRoute = { src, dst, waypoints ->
                                                viewModel.calculateMultiStopRoute(src, dst, waypoints)
                                            },
                                            onPrioritizeSafetyToggle = { prioritize ->
                                                viewModel.setPrioritizeSafety(prioritize)
                                            },
                                            onRouteSelected = { id -> viewModel.selectRoute(id) },
                                            onOpenSafetyDetails = { route -> activeDrawerRoute = route },
                                            onRetryEnrichment = { viewModel.retryEnrichment() },
                                            onBack = { viewModel.navigateHome() }
                                        )
                                    }
                                    is ScreenState.ActiveNav -> {
                                        ActiveNavigationScreen(
                                            route = state.route,
                                            instructions = viewModel.getTurnInstructions(state.route.id),
                                            emergencyContactName = contacts.firstOrNull()?.name ?: "Emergency contact",
                                            contacts = contacts,
                                            activeGroupTrip = activeGroupTrip,
                                            sharedTripContacts = sharedTripContacts,
                                            onToggleContactShare = { name -> viewModel.toggleContactTripShare(name) },
                                            onStopAllSharing = { viewModel.stopAllContactTripSharing() },
                                            onSwitchRoute = {
                                                val safer = activeRoutes.find { it.id == "opt_safe" } ?: state.route
                                                viewModel.selectRoute(safer.id)
                                            },
                                            incidentZones = viewModel.getIncidentZones(),
                                            onTriggerSos = { viewModel.openSosDialog() },
                                            onCompleteTrip = { viewModel.completeTrip(state.route) },
                                            onCancelTrip = { viewModel.navigateHome() }
                                        )
                                    }
                                    is ScreenState.TripSummary -> {
                                        TripSummaryScreen(
                                            route = state.route,
                                            durationSeconds = state.durationSeconds,
                                            distanceKm = state.distanceKm,
                                            avgSafetyScore = state.avgScore,
                                            incidentZones = viewModel.getIncidentZones(),
                                            onSaveFeedback = { rating, note ->
                                                viewModel.saveTripFeedback(1L, rating, note)
                                            },
                                            onDone = { viewModel.navigateHome() }
                                        )
                                    }
                                }
                            }
                        }

                        // EXPANDABLE SAFETY DETAILS BOTTOM SHEET
                        activeDrawerRoute?.let { route ->
                            SafetyDetailsDrawer(
                                route = route,
                                onDismissRequest = { activeDrawerRoute = null },
                                onStartNavigation = { selectedRoute ->
                                    activeDrawerRoute = null
                                    viewModel.startNavigation(selectedRoute)
                                },
                                sheetState = sheetState
                            )
                        }

                        // EMERGENCY SOS COUNTDOWN DIALOG
                        if (showSosDialog) {
                            SosCountdownDialog(
                                contacts = contacts,
                                sosState = sosState,
                                onDismissRequest = { viewModel.closeSosDialog() },
                                onCountdownFinished = { viewModel.onSosCountdownFinished() },
                                onGrantPermissions = {
                                    val needed = buildList {
                                        if (!SosManager.hasCallPermission(context)) {
                                            add(Manifest.permission.CALL_PHONE)
                                        }
                                        if (!SosManager.hasSmsPermission(context)) {
                                            add(Manifest.permission.SEND_SMS)
                                        }
                                    }
                                    if (needed.isEmpty()) {
                                        viewModel.triggerSosAlerts()
                                    } else {
                                        sosPermissionLauncher.launch(needed.toTypedArray())
                                    }
                                },
                                onRetry = { viewModel.retrySosAlerts() },
                                onStandDown = { viewModel.closeSosDialog() }
                            )
                        }

                        // END TRIP CONFIRMATION (Android back while navigating)
                        if (showEndTripDialog) {
                            AlertDialog(
                                onDismissRequest = { showEndTripDialog = false },
                                title = { Text("End trip?") },
                                text = {
                                    Text(
                                        "Your active navigation and live location sharing will stop."
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showEndTripDialog = false
                                            viewModel.stopAllContactTripSharing()
                                            viewModel.cancelNavigation()
                                        }
                                    ) {
                                        Text("End trip")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEndTripDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
