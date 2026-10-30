package com.example.saferoute.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String, val subtitle: String) {
    ENGLISH("en", "English", "English", "Default");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun appText(key: String): String {
    val lang = LocalAppLanguage.current
    return LanguageManager.get(key, lang)
}

object LanguageManager {
    private const val PREFS_NAME = "saferoute_language_prefs"
    private const val KEY_LANG = "selected_language"

    private var prefs: SharedPreferences? = null
    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCode = prefs?.getString(KEY_LANG, null)
        _currentLanguage.value = AppLanguage.fromCode(savedCode ?: AppLanguage.ENGLISH.code)
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs?.edit()?.putString(KEY_LANG, language.code)?.apply()
    }

    fun get(key: String, lang: AppLanguage = _currentLanguage.value): String {
        val translated = translations[lang]?.get(key)
        if (translated != null) return translated

        val fallback = translations[AppLanguage.ENGLISH]?.get(key)
        if (fallback != null) {
            if (lang != AppLanguage.ENGLISH) {
                android.util.Log.w("LanguageManager", "Missing translation for key: '$key' in language ${lang.displayName} — falling back to English")
            }
            return fallback
        }

        android.util.Log.w("LanguageManager", "Missing translation key in all languages: '$key'")
        return key
    }

    private val translations = mapOf(
        AppLanguage.ENGLISH to mapOf(
            "app_name" to "SafeRoute",
            "tagline" to "AI Safety-First Navigation & Emergency Companion",
            "home" to "Home",
            "navigate" to "Navigate",
            "explore" to "Explore",
            "trips" to "Trips",
            "history" to "History",
            "profile" to "Profile",
            "starting_point" to "Starting point",
            "destination" to "Where to?",
            "search_source_placeholder" to "Choose start (e.g. Peelamedu, My Location)",
            "search_destination_placeholder" to "Where to in Coimbatore? (e.g. TIDEL Park)",
            "swap_locations" to "Swap start & destination",
            "add_stop" to "Add stop",
            "add_stop_label" to "+ Add stop",
            "remove_stop" to "Remove stop",
            "stops" to "Intermediate Stops",
            "guest_mode_banner" to "Guest Mode • Sign up to sync and save your trips across all devices",
            "sign_in_or_up" to "Sign In / Register",
            "location_permission_banner" to "Enable location permission for auto-detecting your live GPS safety",
            "enable" to "Enable",
            "retry" to "Retry",
            "recenter_location" to "Recenter to My Location",
            "you_are_here" to "You are here (Live GPS)",
            "acquiring_location" to "Acquiring current location...",
            "map_attribution" to "Geoapify & OpenStreetMap (Coimbatore)",
            "network_error_timeout" to "Connection timed out. Please check your network.",
            "network_error_unauthorized" to "Invalid Geoapify API key. Please check your GEOAPIFY_API_KEY.",
            "hospitals" to "Hospitals",
            "pharmacies" to "Pharmacies",
            "petrol_pumps" to "Petrol Pumps",
            "police" to "Police",
            "shops" to "Shops",
            "food" to "Food & Dining",
            "help_points" to "Help Points",
            "all_places" to "All",
            "medical" to "Medical",
            "open" to "Open",
            "closed" to "Closed",
            "call" to "Call",
            "hours_not_listed" to "Hours not listed",
            "longest_stretch_without_help" to "Longest stretch without help",
            "corridor_shop_coverage" to "Shop & Help Coverage",
            "open_when_you_pass" to "Open when you pass",
            "geoapify_key_missing" to "Geoapify API key missing. Add GEOAPIFY_API_KEY to .env",
            "network_error_rate_limited" to "Rate limit reached. Please try again shortly.",
            "network_error_no_internet" to "No internet connection available.",
            "network_error_general" to "Unable to connect to mapping service.",
            "could_not_find_place" to "Couldn't find that place",
            "ola_maps_key_missing" to "Maps API key missing. Add OLA_MAPS_API_KEY to .env",
            "group_trip" to "Group Trip Mode",
            "group_trip_subtitle" to "Invite contacts to share & track live trip safety together",
            "start_group_trip" to "Start Group Trip",
            "invite_code" to "Group Invite Code",
            "share_code" to "Share Code",
            "copy_link" to "Copy Invite Link",
            "active_members" to "Active Group Members",
            "lighting" to "Lighting & Main Roads",
            "crowd_activity" to "Pedestrian Crowd Activity",
            "incident_history" to "Historical Incident Safety",
            "time_of_day" to "Time-of-Day Factor",
            "daylight" to "Daylight",
            "dusk" to "Dusk",
            "night" to "Night",
            "start_navigation" to "START SAFE NAVIGATION",
            "recommended_route" to "RECOMMENDED SAFE ROUTE",
            "alternative_route" to "ALTERNATIVE ROUTE",
            "fastest_route" to "FASTEST DIRECT ROUTE",
            "share_trip" to "Share Trip",
            "stop_sharing" to "Stop Sharing",
            "sharing_live_location" to "Sharing live location with",
            "switch_route" to "Switch Route",
            "keep_current" to "Keep Current",
            "emergency_sos" to "EMERGENCY SOS",
            "emergency_contacts" to "Emergency Contacts",
            "add_contact" to "Add Emergency Contact",
            "routing_preferences" to "Routing Preferences",
            "safety_first" to "Safety First",
            "balanced" to "Balanced",
            "speed_first" to "Speed First",
            "settings_transparency" to "Settings & Transparency",
            "how_scoring_works" to "How Safety Scoring Works",
            "push_alerts" to "Push Safety Alerts",
            "profile_header" to "Account & Settings",
            "language" to "Language",
            "arrived" to "ARRIVED / END TRIP",
            "next_stop" to "Next Stop ➔",
            "cancel" to "Cancel",
            "stationary_alert" to "hasn't moved in 5 min",
            "off_route_alert" to "went off the shared route",
            "call_member" to "Call Member",
            "check_route" to "Check Route",
            "next" to "NEXT",
            "skip" to "Skip",
            "get_started" to "GET STARTED WITH SAFEROUTE",
            "log_in" to "LOG IN",
            "sign_up" to "CREATE ACCOUNT",
            "create_account" to "Create SafeRoute Account",
            "welcome_back" to "Welcome Back",
            "auth_subtitle" to "Sign in to access personalized safety routing & cloud emergency sync",
            "full_name" to "Full Name",
            "email_address" to "Email Address",
            "password" to "Password",
            "confirm_password" to "Confirm Password",
            "forgot_password" to "Forgot Password?",
            "reset_password" to "Reset Password",
            "send_reset_link" to "Send Reset Link",
            "continue_as_guest" to "CONTINUE AS GUEST",
            "sign_in_with_google" to "Sign in with Google",
            "or_divider" to "OR",
            "saved_places" to "Saved Places",
            "recent_trips" to "Recent Trips",
            "trip_summary" to "Trip Summary",
            "trip_completed" to "Trip Safely Completed",
            "leg_breakdown" to "Multi-Leg Breakdown",
            "composite_safety" to "Composite Safety",
            "rate_trip_safety" to "Rate Route Safety",
            "high_safety" to "High Safety",
            "moderate_safety" to "Moderate Safety",
            "caution_required" to "Caution Advised",
            "route_intelligence" to "Route Intelligence",
            "safety_focus" to "Safety Focus",
            "speed_focus" to "Speed Focus",
            "view_safety_breakdown" to "View Safety Breakdown",
            "well_lit_roads" to "Well-lit Main Roads",
            "moderate_lighting" to "Moderate Lighting",
            "pedestrian_activity" to "Pedestrian Activity",
            "low_incident_risk" to "Low Incident Risk",
            "active_caution_zones" to "Active Caution Zones",
            "safe_corridor_mode" to "Safe Corridor Mode",
            "find_safe_routes" to "Find Safe Routes",
            "calculating_routes" to "Calculating safest route...",
            "quick_routes" to "Quick Routes",
            "no_recent_trips" to "No recent trips yet — search above to start",
            "trip_logs_audits" to "Trip Logs & Audits",
            "search_logged_routes" to "Search logged routes...",
            "date" to "Date",
            "safety" to "Safety",
            "distance" to "Distance",
            "duration" to "Duration",
            "avg_safety_score" to "Avg Safety Score",
            "save_feedback_finish" to "Save Feedback & Finish",
            "add_safety_observations" to "Add safety observations (optional)",
            "done" to "Done",
            "sos_countdown_title" to "EMERGENCY SOS BROADCAST",
            "sos_cancel_accidental" to "CANCEL (ACCIDENTAL TAP)",
            "sos_broadcast_active" to "EMERGENCY BROADCAST ACTIVE",
            "sos_dispatch_recipients" to "DISPATCH RECIPIENTS",
            "call_police" to "CALL POLICE (112)",
            "stand_down_safe" to "STAND DOWN / I AM SAFE",
            "sos_transmitting_prefix" to "Transmitting live GPS telemetry and distress alert to your emergency contacts in",
            "seconds" to "seconds...",
            "profile_settings" to "Account & Safety Settings",
            "safety_score_explainer" to "How Safety Scoring Works",
            "safety_score_desc" to "Scores are dynamically generated from street illumination levels, foot traffic density, verified incident logs, and time of day.",
            "no_active_nav_title" to "Ready to Navigate",
            "no_active_nav_desc" to "No active trip in progress. Select a destination or safe corridor from Home to start turn-by-turn navigation with live safety scores and SOS protection.",
            "search_dest_from_home" to "Plan a Safe Route",
            "instant_safe_corridors" to "Instant Safe Corridors",
            "start_safe_trip" to "Start Safe Navigation",
            "exit_navigation" to "Exit Navigation",
            "end_trip_confirm" to "End Navigation Trip?",
            "end_trip_desc" to "Are you sure you want to stop navigation and log your arrival?",
            "confirm_exit" to "Yes, End Trip",
            "keep_navigating" to "Keep Navigating",
            "grant_location" to "Grant Location",
            "app_settings" to "App Settings",
            "dismiss" to "Dismiss",
            "email_in_use" to "This email address is already in use by another account.",
            "weak_password" to "Password must be at least 6 characters.",
            "invalid_credentials" to "Incorrect password or email format.",
            "user_not_found" to "No account found with this email. Please sign up.",
            "network_error" to "Network connection error. Please check your internet connection.",
            "map_preview_title" to "Safe Corridor Map Preview (Coimbatore)",
            "where_to_in_cbe" to "Where to in Coimbatore? (e.g. TIDEL Park)",
            "selected_route" to "Currently Selected Route",
            "select_this_route" to "Select This Route",
            "lighting_metric" to "Lighting",
            "crowd_metric" to "Crowd Activity",
            "risk_metric" to "Incident Risk",
            "safe_corridor" to "SAFE CORRIDOR",
            "route_breakdown" to "ROUTE BREAKDOWN",
            "full_address" to "Full Address",
            "coimbatore_bounds_label" to "Coimbatore (11.01° N, 76.95° E)",
            "onboarding_title_1" to "Safety Scoring for Every Route",
            "onboarding_desc_1" to "Choose routes based on composite safety — computed in real-time using street lighting, crowd density, and historical incident proximity.",
            "onboarding_title_2" to "One-Tap Instant Emergency SOS",
            "onboarding_desc_2" to "Keep trusted emergency contacts updated automatically with live GPS coordinates if you ever feel unsafe.",
            "onboarding_title_3" to "Personalized Safety Trade-Offs",
            "onboarding_desc_3" to "Prioritize total safety or speed with a live toggle, and tailor your navigation preferences for daytime, dusk, or night commuting.",
            "onboarding_perm_title" to "Required Permissions",
            "onboarding_perm_desc" to "SafeRoute requires access to calculate real-time safety scores along your physical walking path.",
            "precise_location" to "Precise Location",
            "precise_location_desc" to "Detects lighting and incident proximity around you.",
            "notifications_title" to "Safety Push Notifications",
            "notifications_desc" to "Receive warnings when deviating into dark unmonitored zones.",
            "allow" to "Allow",
            "continue_to_contacts" to "CONTINUE TO CONTACT SETUP",
            "contact_setup_title" to "Add Trusted Contact",
            "contact_setup_desc" to "Add your primary emergency contact who will receive distress coordinates during SOS.",
            "contact_full_name" to "Contact Full Name",
            "contact_phone_number" to "Phone Number",
            "relationship_label" to "Relationship (e.g. Sister, Partner)",
            "primary_emergency_contact" to "Primary Emergency Contact",
            "add_trusted_contact_desc" to "Add at least 1 trusted contact who will receive automatic location broadcasts during an SOS trigger.",
            "road_accident_risk" to "Road-Accident Risk",
            "personal_safety_night" to "Personal Safety (Night)",
            "accident_risk_unavailable" to "Predicted accident risk unavailable - not enough accident data",
            "risk_low" to "Low",
            "risk_medium" to "Medium",
            "risk_high" to "High",
            "no_accident_data_area" to "No incident data for this area",
            "limited_data" to "Limited data",
            "model_not_trained_banner" to "Predicted accident risk is not available yet - showing OpenStreetMap facts only.",
            "safety_disclaimer" to "Estimates based on past accident records and OpenStreetMap. Not a guarantee of safety.",
            "toggle_blackspots" to "Black-Spots",
            "data_confidence_high" to "High",
            "data_confidence_medium" to "Medium",
            "data_confidence_low" to "Low"
        )
    )
}
