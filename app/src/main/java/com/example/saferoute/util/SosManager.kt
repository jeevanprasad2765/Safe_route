package com.example.saferoute.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real SOS execution: phone-number normalisation, call/dialer launching and multipart SMS.
 * Kept free of Android UI so it can be unit tested (normalisation/message building).
 */
object SosManager {
    private const val TAG = "SosManager"

    data class CallOutcome(
        val started: Boolean,
        val usedDialerFallback: Boolean = false,
        val error: String? = null
    )

    data class SmsOutcome(
        val sent: Boolean,
        val error: String? = null
    )

    /**
     * Normalises Indian and international numbers to a dialable E.164-ish form.
     * Examples: "98765 43210" -> "+919876543210", "09876543210" -> "+919876543210",
     * "+1 (555) 019-2834" -> "+15550192834".
     */
    fun normalizePhoneNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val hasPlusPrefix = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val normalized = when {
            hasPlusPrefix -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 11 && digits.startsWith("91") -> "+$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            digits.length == 13 && digits.startsWith("091") -> "+${digits.drop(1)}"
            digits.length in 8..15 -> "+$digits"
            else -> return null
        }

        val digitCount = normalized.count { it.isDigit() }
        return if (digitCount in 8..15) normalized else null
    }

    fun buildEmergencyMessage(
        userName: String,
        latitude: Double?,
        longitude: Double?,
        timeMillis: Long = System.currentTimeMillis()
    ): String {
        val safeName = userName.trim().ifBlank { "A SafeRoute user" }
        val time = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timeMillis))
        val locationLine = if (latitude != null && longitude != null) {
            "My live location: https://maps.google.com/?q=$latitude,$longitude"
        } else {
            "My live location is unavailable (location permission off or GPS not ready)."
        }
        return buildString {
            append("EMERGENCY SOS from ")
            append(safeName)
            append(". I need immediate help.\n")
            append(locationLine)
            append("\nTime: ")
            append(time)
            append("\nPlease call me and contact local emergency services if I don't answer.")
        }
    }

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasSmsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasAnySosPermission(context: Context): Boolean =
        hasCallPermission(context) || hasSmsPermission(context)

    /**
     * ACTION_CALL when CALL_PHONE is granted, otherwise opens the dialer with ACTION_DIAL.
     * A dialer fallback still counts as the alert being started (the call UI is shown), but it
     * tells the UI it needs a tap to connect.
     */
    fun startCall(
        context: Context,
        rawPhone: String,
        forceDialer: Boolean = false
    ): CallOutcome {
        val phone = normalizePhoneNumber(rawPhone)
            ?: return CallOutcome(false, error = "Invalid phone number: $rawPhone")

        val canCall = hasCallPermission(context) && !forceDialer
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$phone")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CallOutcome(started = true, usedDialerFallback = !canCall)
        } catch (security: SecurityException) {
            Log.w(TAG, "ACTION_CALL denied, falling back to dialer: ${security.message}")
            try {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                CallOutcome(started = true, usedDialerFallback = true)
            } catch (e: Exception) {
                Log.e(TAG, "Could not open dialer", e)
                CallOutcome(false, error = "Could not open the dialer.")
            }
        } catch (notFound: ActivityNotFoundException) {
            Log.e(TAG, "No calling app available", notFound)
            CallOutcome(false, error = "No phone app available on this device.")
        } catch (e: Exception) {
            Log.e(TAG, "Call failed", e)
            CallOutcome(false, error = e.message ?: "Could not start the call.")
        }
    }

    fun sendSms(context: Context, rawPhone: String, message: String): SmsOutcome {
        val phone = normalizePhoneNumber(rawPhone)
            ?: return SmsOutcome(false, "Invalid phone number: $rawPhone")
        if (!hasSmsPermission(context)) {
            return SmsOutcome(false, "SMS permission was not granted.")
        }

        return try {
            val smsManager = resolveSmsManager()
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, message, null, null)
            }
            Log.i(TAG, "SMS started to $phone (${parts.size} part(s))")
            SmsOutcome(true)
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed to $phone: ${e.message}", e)
            SmsOutcome(false, e.message ?: "SMS could not be sent.")
        }
    }

    /**
     * Uses the device's default SMS subscription (works on dual-SIM devices) and falls back to
     * the platform default manager if the subscription API is unavailable.
     */
    private fun resolveSmsManager(): SmsManager {
        return try {
            val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
            } else {
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Default SMS subscription unavailable, using platform default: ${e.message}")
            SmsManager.getDefault()
        }
    }
}
