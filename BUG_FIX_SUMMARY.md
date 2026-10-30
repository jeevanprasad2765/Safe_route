# SafeRoute Android App - 7 Production Bug Fixes Summary

## Build Verification
- `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL (75.9 MB APK produced)
- `./gradlew testDebugUnitTest` ✅ ALL TESTS PASS (7 robolectric + 4 SafetyCalculator)
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Bug 1: Google Sign-in via Credential Manager
**Root cause:** Missing/broken Google Credential Manager integration; sign-in always failed with "Google sign-in is not configured".

**Fix:** Rewrote `FirebaseAuthManager.kt` (`app/src/main/java/com/example/saferoute/data/firebase/FirebaseAuthManager.kt`):
- Real Google sign-in flow using `androidx.credentials.CredentialManager` + `GetGoogleIdOption` + `GoogleIdTokenCredential`
- Web client ID read from `R.string.default_web_client_id` (resolved from `google-services.json`: `602311398373-so1pljbi32gh1jn8rhhfss5qvk5l64jf.apps.googleusercontent.com`)
- Credential launched from Activity context within ViewModel coroutine scope
- Error mapping with distinct messages:
  - `DEVELOPER_ERROR` / status 10 or 12500 → "Google sign-in is misconfigured for this build. Register this app's SHA-1 in Firebase and try again."
  - status 7 → "Network error. Check your internet connection and try again."
  - status 12501 → "Google sign-in was cancelled."
  - status 12502 → "No Google account found on this device. Add one in Android Settings and try again."
  - `Resources.NotFoundException` → "Google sign-in is not configured."
- Logs real exception with `Log.e(TAG, "Google Sign-In failed: ..., e)`
- Uses `FirebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))`

**Changed files:**
- `FirebaseAuthManager.kt` — complete rewrite of `signInWithGoogle()` and `mapGoogleSignInError()`

---

## Bug 2: Real `isNewUser` + Welcome/Onboarding
**Root cause:** New-user detection always returned false; no welcome message; no onboarding flow; profile docs not created on first sign-in.

**Fix:** Fixed `isNewUser` plumbing through `AuthResult.Success.isNewUser`:
- `FirebaseAuthManager.signInWithGoogle()` now extracts `authResult.additionalUserInfo?.isNewUser` (line 124)
- New-user signal combined with Firestore existence check: `isNewUser = isNewFromAuth || documentExists == false` (line 131)
- On first sign-in (Google or email), Firestore user doc created with `completedOnboarding = false` (lines 134-140 for Google, 195-203 for email sign-up)
- `SafeRouteRepository.logInUser()` uses `isNewUser` to set `completedOnboarding = false` for new users (line 242)
- Email signup also sets `isNewUser = true` (line 211 in `FirebaseAuthManager.kt`)
- Welcome Toast in `SafeRouteViewModel.handleAuthResult()`: distinct "Welcome, {name}" vs "Welcome back, {name}" (lines 332-336)

**Changed files:**
- `FirebaseAuthManager.kt` — `isNewFromAuth` extraction + `isNewUser` resolution + Firestore doc creation
- `SafeRouteRepository.kt` — `logInUser()` now respects `isNewUser` to set `completedOnboarding = false`
- `SafeRouteViewModel.kt` — `handleAuthResult()` emits distinct welcome messages

---

## Bug 3: Startup Session Persistence & Default Screen
**Root cause:** App always landed on Auth screen on relaunch; launch-time logout/clearing of session; no persistence of onboarding state; splash had no auth-gating logic.

**Fix:**
- `SafeRouteViewModel` uses `FirebaseAuth.AuthStateListener` (`_authReady` `StateFlow`) to track auth state across restarts (lines 229-250)
- Launch-time session clearing removed; `onSplashFinished()` now checks `FirebaseAuthManager.currentUser` (lines 256-277)
- If user is signed in → restore per-user onboarding via `repository.restoreSession()` → Home if onboarding done, Onboarding if not
- If user is null → Auth screen
- Default screen remains `Splash` until `AuthStateListener` reports persisted session
- Onboarding completed persisted per user in Room (`user_onboarding` table) + Firestore backup (authoritative = Room, backup = Firestore)
- `isOnboardingCompleted()`: Room-first, Firestore second with 4 s timeout, defaults to `true` if remote unavailable (assumes completed so user isn't trapped)

**Changed files:**
- `SafeRouteViewModel.kt` — `_authReady`, `authStateListener`, `onSplashFinished()`, startup flow
- `SafeRouteRepository.kt` — `restoreSession()`, `isOnboardingCompleted()`, `logInUser()`
- `MainActivity.kt` — `defaultProfile.completedOnboarding = false` (was `true`)

---

## Bug 4: Remove Group Button from Home
**Root cause:** Group top-left chip/button visible on Home screen; Group Trip should only be reachable from Profile or search-card overflow.

**Fix:** Removed the Group top-left chip from `HomeScreen.kt`:
- Deleted `activeGroupTrip` remember state and all associated `showGroupTripDialog` logic
- Removed `Icons.Default.Group` import and associated UI
- `Group Trip` banner/button removed from Home top-left corner
- Group Trip remains reachable from Profile screen or search-card overflow (not removed, just repositioned)

**Changed files:**
- `HomeScreen.kt` — removed Group chip, `activeGroupTrip`, `showGroupTripDialog`, related imports

---

## Bug 5: Real SOS Call/SMS with Location Link
**Root cause:** SOS was fake — no real call/SMS, no location link, no permission rationale, no runtime permissions, no contact normalization.

**Fix:** Comprehensive SOS implementation across multiple files:

**`SosManager.kt`** (`app/src/main/java/com/example/saferoute/util/SosManager.kt`):
- `normalizePhoneNumber()`: strips spaces, normalizes Indian (+91, 10-digit) and international numbers to E.164 format; returns `null` for invalid
- `buildEmergencyMessage()`: produces string with user name, `https://maps.google.com/?q=lat,lng`, time, "EMERGENCY SOS" marker
- `startCall()`: `ACTION_CALL` if `CALL_PHONE` permission granted, otherwise `ACTION_DIAL` (dialer fallback)
- `sendSms()`: uses `SmsManager` with `divideMessage()` for multipart long messages; resolves default SMS subscription (dual-SIM) with fallback
- Permission checks: `hasCallPermission`, `hasSmsPermission`, `hasAnySosPermission`

**`MainActivity.kt`** (`app/src/main/java/com/example/MainActivity.kt`):
- SOS permission launcher with rationale dialog before system prompt
- `onSosCountdownFinished()` → if permissions granted, triggers `triggerSosAlerts()`; else shows permission rationale
- `triggerSosAlerts()` in ViewModel: iterates contacts, calls `SosManager.startCall()` for primary, `SosManager.sendSms()` for all; step-by-step status ("Calling Sarah…", "SMS sent to 2 contacts")
- Result screen: summary "Emergency alert sent" only if actually started; "Partial sent" otherwise; retry button
- "Also call 112" setting (default OFF) in Profile; if ON, also calls 112
- Cancel works during countdown

**`SosCountdownDialog.kt`** (`app/src/main/java/com/example/saferoute/ui/components/SosCountdownDialog.kt`):
- State-driven UI: COUNTDOWN → PERMISSION_RATIONALE → SENDING → RESULT
- Step status displayed per step (running/success/failed)
- No "Alert sent" unless actually started

**`ActiveNavigationScreen.kt`** — removed fake demo contacts ("Sarah Taylor", "Anand Kumar", "Priya Sharma"); shows "No emergency contacts yet" if none saved

**Manifest:** Added `CALL_PHONE` and `SEND_SMS` permissions

**Changed files:**
- `MainActivity.kt` — SOS permission launcher, rationale, dialog wiring
- `SosManager.kt` — full implementation of normalisation, call/SMS, message building
- `SosCountdownDialog.kt` — state-driven countdown/sending/result UI
- `ActiveNavigationScreen.kt` — removed fake demo contacts
- `AndroidManifest.xml` — `CALL_PHONE`/`SEND_SMS`

---

## Bug 6: Long-Route Scoring Hang (Peelamedu→Ooty ~90 km)
**Root cause:** Long routes (>25 km) caused infinite spinner; no chunking; no timeout; all-or-nothing scoring; >25 km routes not split; no partial-data awareness; confidence not downgraded.

**Fix:** Chunked long-route enrichment in `CorridorPlacesRepository.kt`:
- `fetchCorridorData()`: routes >25 km split into ~10 km chunks via `splitRouteIntoChunks()`
- `fetchChunkedCorridor()`: runs chunks with `Semaphore(3)` (max 3 concurrent), 10 s per-chunk timeout via `withTimeoutOrNull(10_000L)`, hard 15 s overall budget via `withTimeoutOrTotal(15_000L)`
- Successful chunks merged; failed chunks marked — their distance range reported as `CoverageStatus.UNAVAILABLE`
- `CoverageStatus` expanded with `UNAVAILABLE` enum value
- `SafetyCalculator` partial-data awareness:
  - `partialFraction` = failed chunks / total chunks
  - `coverageRatio` normalises open-place density by covered fraction
  - `effectiveConfidence`: if partial data + HIGH confidence → downgrade to MEDIUM
  - `compositeScore` computed against `effectiveConfidence`, not raw confidence
- Warnings added:
  - "Partial data: X of Y corridor sections could not be loaded. Score uses the sections that returned; tap Retry for the rest."
  - "Safety data unavailable for this route: all N corridor sections timed out. Tap Retry to recalculate."
- `GeoapifyService.kt` enrichment timeout changed from 6 s to 15 s to match repository budget
- `RouteRecommendationScreen.kt` shows pending/partial banner with "Retry" action; calls `viewModel.retryEnrichment()`
- Unit tests: `synthetic90KmRouteIsSplitIntoAboutTenKmChunks` (8–11 chunks, ~10 km each, shared boundaries) and `synthetic90KmPartialCorridorScoringCompletesWithinTimeout` (90 km route, 1 of 9 chunks fails, score completes in <5 ms, warning contains "Partial data", score > 0)

**Changed files:**
- `CorridorPlacesRepository.kt` — chunk splitter, semaphore, timeouts, partial merge, `CoverageStatus.UNAVAILABLE`
- `SafetyCalculator.kt` — partialFraction, effectiveConfidence, compositeScore against effective, warnings
- `GeoapifyService.kt` — 15 s timeout instead of 6 s
- `RouteRecommendationScreen.kt` — partial-data retry banner
- `RoutePlace.kt` — `CoverageStatus.UNAVAILABLE` added
- `ExampleRobolectricTest.kt` — 4 new tests for chunking and partial scoring

---

## Bug 7: Back-press Exit Behavior
**Root cause:** BackHandler not driven by screen state; unexpected app close; ActiveNav not "End trip?" dialog; History/Profile/Navigate tabs not → Home; Home not double-press exit.

**Fix:** Rewrote `MainActivity.kt` BackHandler:
- `showSosDialog` → back consumed while SOS on screen; sending cannot be interrupted mid-flight
- `activeDrawerRoute != null` → clear route
- `ScreenState.ActiveNav` → show "End trip?" dialog (Cancel / End trip)
- `ScreenState.RouteRecommendations` / `TripSummary` → `viewModel.navigateHome()`
- `ScreenState.Main` → double-press exit: first press shows "Press back again to exit", second within 2 s calls `finish()`; otherwise resets timer
- `ScreenState.History` / `Profile` / `Navigate` tabs → `viewModel.navigateToTab("home")`
- Splash/Auth → consume back (`else -> Unit`) so app never closes by accident
- `navTab == "navigate" && activeNavRoute != null` treated as active navigation

**Changed files:**
- `MainActivity.kt` — complete BackHandler rewrite with screen-state-driven behavior

---

## Test Results Summary
```
7 robolectric tests passed:
  - testPeelameduToGandhipuramRouteScoreDifferentiation
  - testPeelameduToSitraGeocodingAndRouting
  - synthetic90KmPartialCorridorScoringCompletesWithinTimeout
  - sosNormalisesIndianAndInternationalPhoneNumbers
  - sosMessageContainsNameMapsLinkAndTime
  - read string from context
  - synthetic90KmRouteIsSplitIntoAboutTenKmChunks

4 SafetyCalculator tests passed:
  - testTwoRoutesWithDifferentGeometryGetDifferentScores
  - testTwoDifferentRoutesYieldDifferentBreakdowns
  - testRouteWithNoLitTagsYieldsNoDataForLighting
  - testRouteWithNoBlackSpotDataYieldsNoIncidentData
```

## Files Changed (30 modified, 2 new)
Key modified files (full list in git diff):
- `app/src/main/java/com/example/MainActivity.kt` — Bug 7 (BackHandler), Bug 5 (SOS), Bug 3 (defaultProfile)
- `app/src/main/java/com/example/saferoute/data/firebase/FirebaseAuthManager.kt` — Bug 1 (Google sign-in), Bug 2 (isNewUser)
- `app/src/main/java/com/example/saferoute/data/repository/SafeRouteRepository.kt` — Bug 3 (session/onboarding)
- `app/src/main/java/com/example/saferoute/data/network/CorridorPlacesRepository.kt` — Bug 6 (chunked routing)
- `app/src/main/java/com/example/saferoute/safety/SafetyCalculator.kt` — Bug 6 (partial scoring, warnings)
- `app/src/main/java/com/example/saferoute/ui/components/SosCountdownDialog.kt` — Bug 5 (state-driven SOS UI)
- `app/src/main/java/com/example/saferoute/ui/screens/HomeScreen.kt` — Bug 4 (Group removal)
- `app/src/main/java/com/example/saferoute/util/SosManager.kt` — Bug 5 (normalisation, call/SMS)
- `app/src/main/res/values/values.xml` — google services config (existing)
- `app/src/test/java/com/example/ExampleRobolectricTest.kt` — 4 new unit tests
- `app/src/main/AndroidManifest.xml` — CALL_PHONE/SEND_SMS permissions
- `app/src/main/java/com/example/saferoute/data/model/RoutePlace.kt` — CoverageStatus.UNAVAILABLE
- `app/src/main/java/com/example/saferoute/data/model/SafetyModels.kt` — minor
- `app/src/main/java/com/example/saferoute/ui/screens/ActiveNavigationScreen.kt` — removed fake demo contacts
- `app/src/main/java/com/example/saferoute/ui/screens/RouteRecommendationScreen.kt` — partial-data retry banner
- `app/src/main/java/com/example/saferoute/data/db/Entities.kt` / `Daos.kt` / `AppDatabase.kt` — Room schema v6 (user_onboarding, alsoCall112)
- `app/src/main/java/com/example/saferoute/data/firebase/FirebaseFirestoreManager.kt` — user doc existence, onboarding backup
- `app/src/main/java/com/example/saferoute/ui/screens/ProfileScreen.kt` — alsoCall112 setting
- `app/src/main/java/com/example/saferoute/risk/RiskRepository.kt` — minor

All 7 bugs are fixed, build passes, and all unit tests pass.