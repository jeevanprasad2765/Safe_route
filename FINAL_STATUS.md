# SafeRoute Android App - Bug Fixes: Final Status

## Code Verification: COMPLETE ✅

- `./gradlew assembleDebug` ✅ BUILD SUCCESSFUL (75.9 MB APK)
- `./gradlew testDebugUnitTest` ✅ **11/11 tests pass**
  - 7 robolectric tests (0 failures, 0 errors)
  - 4 SafetyCalculator tests (0 failures, 0 errors)

## Bug Fixes: ALL 7 CODE-FIXED ✅

| # | Bug | Code Fixed | Key Changes |
|---|-----|-----------|-------------|
| 1 | Google sign-in | `FirebaseAuthManager.kt` | Credential Manager flow, SHA-1 error mapping, distinct cancel/network/misconfig messages |
| 2 | New-user welcome | `FirebaseAuthManager.kt`, `SafeRouteRepository.kt`, `SafeRouteViewModel.kt` | `isNewUser` from `AuthResult.additionalUserInfo?.isNewUser`; welcome Toast; Firestore doc created with `completedOnboarding=false` |
| 3 | Session persistence | `SafeRouteViewModel.kt`, `SafeRouteRepository.kt`, `MainActivity.kt` | `AuthStateListener` + `_authReady`; splash waits for auth; no launch-time logout; onboarding in Room + Firestore |
| 4 | Group button | `HomeScreen.kt` | Group top-left chip removed; Group Trip still reachable from Profile/search overflow |
| 5 | Real SOS call/SMS | `SosManager.kt`, `MainActivity.kt`, `SosCountdownDialog.kt`, `AndroidManifest.xml` | Phone normalisation, `ACTION_CALL`/`ACTION_DIAL` fallback, multipart SMS, dual-SIM, location link, "Also call 112" setting, countdown, step status, retry, permission rationale |
| 6 | Long-route scoring | `CorridorPlacesRepository.kt`, `SafetyCalculator.kt`, `GeoapifyService.kt`, `RouteRecommendationScreen.kt` | ~10 km chunks, Semaphore(3), 10 s per chunk, 15 s overall timeout; `CoverageStatus.UNAVAILABLE`; partial warnings; retry banner; 4 unit tests |
| 7 | Back-press exit | `MainActivity.kt` | Screen-state-driven BackHandler: ActiveNav → "End trip?" dialog, tab nav → Home, double-press on Home to exit, Splash/Auth consumes back |

## Device Reproduction: PENDING

Requires physical device (package `com.aistudio.saferoute.wwcnxv`) to verify acceptance criteria:

1. Google sign-in works (new user "Welcome", returning "Welcome back")
2. Reopen app → Home without login
3. SOS real call/dialer + SMS with location link
4. Peelamedu→Ooty cards <3 s, scores/partial status <15 s or clear missing-data message
5. Back never closes app except double-press on Home

**Install for device verification:**
```
.\gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then reproduce each bug and capture logcat. All code fixes are implemented and verified; device step confirms acceptance criteria.