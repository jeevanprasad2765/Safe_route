package com.example

import android.os.Bundle
import android.util.Log
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                val isVoiceSosEnabled by viewModel.isVoiceSosEnabled.collectAsState()
                val voiceSosTriggerPhrase by viewModel.voiceSosTriggerPhrase.collectAsState()
                val activeGroupTrip by viewModel.activeGroupTrip.collectAsState()
                val sharedTripContacts by viewModel.sharedTripContacts.collectAsState()
                val isDuskRouteShiftAvailable by viewModel.isDuskRouteShiftAvailable.collectAsState()
                val firebaseStatus by viewModel.firebaseStatus.collectAsState()
                val userCoordinates by viewModel.userCoordinates.collectAsState()

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var activeDrawerRoute by remember { mutableStateOf<RouteOption?>(null) }

                val defaultProfile = profile ?: UserProfileEntity(
                    email = "alex.taylor@saferoute.io",
                    name = "Alex Taylor",
                    isPlusMember = false,
                    safetyPreference = "ALWAYS_SAFETY",
                    highContrastMode = false,
                    completedOnboarding = true
                )

                val showBottomBarAndSos = screenState is ScreenState.Main

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
                                            onLoginSuccess = { uid, email, name ->
                                                viewModel.loginUser(uid, email, name)
                                            }
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
                                                onAddSavedPlace = {
                                                    viewModel.addSavedPlace("Work (TIDEL)", "Peelamedu, Coimbatore", "work", 11.025, 76.992)
                                                },
                                                onStartGroupTrip = { viewModel.startGroupTripMode() }
                                            )
                                            "navigate" -> {
                                                val activeRoute = activeNavRoute
                                                if (activeRoute != null) {
                                                    ActiveNavigationScreen(
                                                        route = activeRoute,
                                                        instructions = viewModel.getTurnInstructions(activeRoute.id),
                                                        emergencyContactName = contacts.firstOrNull()?.name ?: "Sarah (Sister)",
                                                        contacts = contacts,
                                                        incidentZones = viewModel.getIncidentZones(),
                                                        activeGroupTrip = activeGroupTrip,
                                                        sharedTripContacts = sharedTripContacts,
                                                        isVoiceSosEnabled = isVoiceSosEnabled,
                                                        voiceSosTriggerPhrase = voiceSosTriggerPhrase,
                                                        isDuskRouteShiftAvailable = isDuskRouteShiftAvailable,
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
                                                isVoiceSosEnabled = isVoiceSosEnabled,
                                                voiceSosTriggerPhrase = voiceSosTriggerPhrase,
                                                firebaseStatus = firebaseStatus,
                                                onSetLanguage = { lang -> viewModel.setLanguage(lang) },
                                                onToggleVoiceSos = { enabled -> viewModel.toggleVoiceSos(enabled) },
                                                onSetVoiceSosTriggerPhrase = { phrase -> viewModel.setVoiceSosTriggerPhrase(phrase) },
                                                contacts = contacts,
                                                onAddContact = { n, p, r -> viewModel.addContact(n, p, r) },
                                                onDeleteContact = { c -> viewModel.deleteContact(c) },
                                                onUpdateSafetyPref = { pref -> viewModel.updateSafetyPreference(pref) },
                                                onTogglePlus = { isPlus -> viewModel.setPlusMembership(isPlus) },
                                                onTriggerSync = { onDone -> viewModel.syncWithFirebase(onDone) },
                                                onConfigureCustomFirebase = { proj, key, app, onDone ->
                                                    viewModel.configureCustomFirebase(proj, key, app, onDone)
                                                },
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
                                                onAddSavedPlace = {},
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
                                            onBack = { viewModel.navigateHome() }
                                        )
                                    }
                                    is ScreenState.ActiveNav -> {
                                        ActiveNavigationScreen(
                                            route = state.route,
                                            instructions = viewModel.getTurnInstructions(state.route.id),
                                            emergencyContactName = contacts.firstOrNull()?.name ?: "Sarah (Sister)",
                                            contacts = contacts,
                                            activeGroupTrip = activeGroupTrip,
                                            sharedTripContacts = sharedTripContacts,
                                            isVoiceSosEnabled = isVoiceSosEnabled,
                                            voiceSosTriggerPhrase = voiceSosTriggerPhrase,
                                            isDuskRouteShiftAvailable = isDuskRouteShiftAvailable,
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
                                onDismissRequest = { viewModel.closeSosDialog() },
                                onAlertTriggered = { /* Alert triggered state handled in dialog */ },
                                onStandDownAlert = { viewModel.closeSosDialog() }
                            )
                        }
                    }
                }
            }
        }
    }
}
}
