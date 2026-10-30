package com.example.saferoute.data.firebase

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed class AuthResult {
    data class Success(
        val user: FirebaseUser?,
        val email: String,
        val name: String,
        val uid: String,
        val isNewUser: Boolean = false,
        val provider: String = "password"
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}

object FirebaseAuthManager {
    private const val TAG = "FirebaseAuthManager"

    val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth not initialized: ${e.message}")
            null
        }

    val currentUser: FirebaseUser?
        get() = try {
            auth?.currentUser
        } catch (e: Exception) {
            null
        }

    val currentUserId: String?
        get() = currentUser?.uid

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Real Google sign-in through Credential Manager.
     *
     * The caller must pass an Activity context (Credential Manager renders UI), while the network
     * work is driven by the ViewModel's coroutine scope.
     */
    suspend fun signInWithGoogle(context: Context): AuthResult {
        val webClientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "default_web_client_id is missing from resources", e)
            return AuthResult.Error("Google sign-in is not configured.")
        } catch (e: Exception) {
            Log.e(TAG, "Could not read Google web client id", e)
            return AuthResult.Error("Google sign-in is not configured.")
        }

        if (webClientId.isBlank()) {
            Log.e(TAG, "default_web_client_id is blank")
            return AuthResult.Error("Google sign-in is not configured.")
        }

        return try {
            val credentialManager = CredentialManager.create(context)

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val idToken = googleCredential.idToken
            if (idToken.isBlank()) {
                return AuthResult.Error("Google did not return a valid sign-in token. Please try again.")
            }

            val firebaseAuth = auth
                ?: return AuthResult.Error("Authentication service is unavailable. Please restart the app.")

            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user
                ?: return AuthResult.Error("Google sign-in did not return a user. Please try again.")

            val uid = user.uid
            val email = user.email ?: googleCredential.id
            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: googleCredential.displayName?.takeIf { it.isNotBlank() }
                ?: email.substringBefore("@")

            // Real new-user signal from Firebase.
            val isNewFromAuth = authResult.additionalUserInfo?.isNewUser == true

            // Second signal: Firestore profile document. Null means "unknown" (offline/rules), so
            // it must never be treated as a missing document.
            val documentExists = withTimeoutOrNull(4_000) {
                FirebaseFirestoreManager.userDocumentExists(uid)
            }
            val isNewUser = isNewFromAuth || documentExists == false

            if (documentExists != true) {
                FirebaseFirestoreManager.createUserDocument(
                    userId = uid,
                    name = name,
                    email = email,
                    completedOnboarding = false
                )
            }

            Log.i(
                TAG,
                "Google sign-in OK uid=$uid newFromAuth=$isNewFromAuth docExists=$documentExists resolvedNew=$isNewUser"
            )
            AuthResult.Success(
                user = user,
                email = email,
                name = name,
                uid = uid,
                isNewUser = isNewUser,
                provider = "google.com"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Google Sign-In failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthResult.Error(mapGoogleSignInError(e))
        }
    }

    suspend fun signUpWithEmail(
        name: String,
        email: String,
        pass: String,
        initialContacts: List<Map<String, Any>> = emptyList(),
        places: List<Map<String, Any>> = emptyList(),
        language: String = "en"
    ): AuthResult {
        val trimmedEmail = email.trim()
        val trimmedPass = pass.trim()
        val trimmedName = name.trim().ifBlank { trimmedEmail.substringBefore("@") }

        if (!isValidEmail(trimmedEmail)) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (trimmedPass.length < 6) {
            return AuthResult.Error("Password must be at least 6 characters long.")
        }

        val firebaseAuth = auth
            ?: return AuthResult.Error("Authentication service is unavailable. Please restart the app.")

        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val user = authResult.user
                ?: return AuthResult.Error("Account was created but no session was returned. Please log in.")

            try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(trimmedName).build()
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not save display name for ${user.uid}: ${e.message}")
            }

            FirebaseFirestoreManager.createUserDocument(
                userId = user.uid,
                name = trimmedName,
                email = trimmedEmail,
                contacts = initialContacts,
                places = places,
                language = language,
                completedOnboarding = false
            )

            Log.i(TAG, "Email sign-up OK uid=${user.uid}")
            AuthResult.Success(
                user = user,
                email = trimmedEmail,
                name = trimmedName,
                uid = user.uid,
                isNewUser = true,
                provider = "password"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Email sign-up failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthResult.Error(mapEmailError(e, isSignUp = true))
        }
    }

    suspend fun logInWithEmail(email: String, pass: String): AuthResult {
        val trimmedEmail = email.trim()
        val trimmedPass = pass

        if (!isValidEmail(trimmedEmail)) {
            return AuthResult.Error("Please enter a valid email address.")
        }
        if (trimmedPass.isBlank()) {
            return AuthResult.Error("Password cannot be empty.")
        }

        val firebaseAuth = auth
            ?: return AuthResult.Error("Authentication service is unavailable. Please restart the app.")

        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, trimmedPass).await()
            val user = authResult.user
                ?: return AuthResult.Error("Login did not return a user. Please try again.")

            val name = user.displayName?.takeIf { it.isNotBlank() }
                ?: trimmedEmail.substringBefore("@")
            Log.i(TAG, "Email login OK uid=${user.uid}")
            AuthResult.Success(
                user = user,
                email = user.email ?: trimmedEmail,
                name = name,
                uid = user.uid,
                isNewUser = false,
                provider = "password"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Email login failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthResult.Error(mapEmailError(e, isSignUp = false))
        }
    }

    suspend fun sendPasswordReset(email: String): Result<String> {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val firebaseAuth = auth
            ?: return Result.failure(IllegalStateException("Authentication service is unavailable."))
        return try {
            firebaseAuth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success("Password reset instructions sent to $trimmedEmail")
        } catch (e: Throwable) {
            Log.e(TAG, "Password reset failed: ${e.message}", e)
            Result.failure(IllegalStateException(mapEmailError(e, isSignUp = false)))
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign out error", e)
        }
    }

    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /** Finds a Google Play Services ApiException anywhere in the cause chain. */
    private fun findApiException(throwable: Throwable?): ApiException? {
        var current = throwable
        var depth = 0
        while (current != null && depth < 8) {
            if (current is ApiException) return current
            current = current.cause
            depth++
        }
        return null
    }

    private fun mapGoogleSignInError(e: Throwable): String {
        val rawMessage = e.message.orEmpty()
        if (rawMessage.contains("Developer console is not set up correctly", ignoreCase = true) ||
            rawMessage.contains("10:", ignoreCase = true) ||
            rawMessage.contains("DEVELOPER_ERROR", ignoreCase = true)
        ) {
            return "Google Sign-In is misconfigured (Code 10). Register your debug SHA-1 (61:64:F5:39:44:91:47:3F:C1:8F:6D:9E:B0:76:3C:28:8E:A7:DD:2F) in Firebase Console (Project Settings > Your apps)."
        }

        val apiException = findApiException(e)
        if (apiException != null) {
            Log.e(TAG, "Google ApiException status=${apiException.statusCode} details=${apiException.message}")
            val status = apiException.statusCode
            if (status == 10 || status == 12500) {
                return "Google Sign-In is misconfigured (Code $status). Register your debug SHA-1 (61:64:F5:39:44:91:47:3F:C1:8F:6D:9E:B0:76:3C:28:8E:A7:DD:2F) in Firebase Console."
            }
            if (status == 7) {
                return "Network error. Check your internet connection and try again."
            }
            if (status == 12501) {
                return "Google sign-in was cancelled."
            }
            if (status == 12502) {
                return "No Google account found on this device. Add one in Android Settings and try again."
            }
        }

        return when (e) {
            is Resources.NotFoundException -> "Google sign-in is not configured."
            is GetCredentialCancellationException -> "Google sign-in was cancelled."
            is NoCredentialException -> {
                if (rawMessage.contains("Developer console", ignoreCase = true) || rawMessage.contains("10")) {
                    "Google Sign-In is misconfigured (Code 10). Register your debug SHA-1 in Firebase Console."
                } else {
                    "No Google account found on this device. Add one in Android Settings and try again."
                }
            }
            is GetCredentialException -> {
                val type = e.type.orEmpty()
                val message = e.errorMessage ?: e.message.orEmpty()
                when {
                    type.contains("CANCEL", ignoreCase = true) ->
                        "Google sign-in was cancelled."
                    type.contains("DEVELOPER_ERROR", ignoreCase = true) || message.contains("10") ->
                        "Google Sign-In is misconfigured (Code 10). Register your debug SHA-1 in Firebase Console."
                    type.contains("NO_CREDENTIAL", ignoreCase = true) ||
                        message.contains("No credentials", ignoreCase = true) ||
                        message.contains("no credential", ignoreCase = true) ->
                        "No Google account found on this device. Add one in Android Settings and try again."
                    type.contains("NETWORK", ignoreCase = true) ||
                        message.contains("network", ignoreCase = true) ||
                        message.contains("timeout", ignoreCase = true) ->
                        "Network error. Check your internet connection and try again."
                    type.contains("UNSUPPORTED", ignoreCase = true) ->
                        "Google sign-in is not supported on this device. Update Google Play Services and try again."
                    else -> "Google sign-in failed: ${message.ifBlank { type }}"
                }
            }
            is FirebaseAuthException -> mapFirebaseAuthError(e)
            else -> "Google sign-in failed. Please try again."
        }
    }

    private fun mapEmailError(e: Throwable, isSignUp: Boolean): String {
        if (findApiException(e) != null) return mapGoogleSignInError(e)
        return when (e) {
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 6 characters."
            is FirebaseAuthUserCollisionException ->
                "An account already exists with this email. Log in instead."
            is FirebaseAuthInvalidCredentialsException ->
                if (isSignUp) "Please enter a valid email address."
                else "Incorrect email or password. Please try again."
            is FirebaseAuthInvalidUserException ->
                "No account exists with this email. Sign up first."
            is FirebaseAuthException -> mapFirebaseAuthError(e)
            else -> {
                val message = e.message.orEmpty()
                when {
                    message.contains("network", ignoreCase = true) ||
                        message.contains("timeout", ignoreCase = true) ->
                        "Network error. Check your internet connection and try again."
                    message.contains("password", ignoreCase = true) && isSignUp ->
                        "Password is too weak. Use at least 6 characters."
                    isSignUp -> "Could not create the account. Please try again."
                    else -> "Could not log in. Please try again."
                }
            }
        }
    }

    private fun mapFirebaseAuthError(e: FirebaseAuthException): String {
        Log.e(TAG, "FirebaseAuthException code=${e.errorCode}: ${e.message}")
        return when (e.errorCode) {
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network error. Check your internet connection and try again."
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD" ->
                "Incorrect email or password. Please try again."
            "ERROR_USER_NOT_FOUND" ->
                "No account exists with this email. Sign up first."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "An account already exists with this email. Log in instead."
            "ERROR_WEAK_PASSWORD" ->
                "Password is too weak. Use at least 6 characters."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait a moment and try again."
            "ERROR_USER_DISABLED" ->
                "This account has been disabled. Contact support."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Email sign-in is not enabled for this project."
            "ERROR_APP_NOT_AUTHORIZED" ->
                "Google sign-in is not authorized for this build. Check the OAuth client and SHA-1."
            "ERROR_REQUIRES_RECENT_LOGIN" ->
                "Please log in again to continue."
            else -> "Authentication failed (${e.errorCode}). Please try again."
        }
    }
}
