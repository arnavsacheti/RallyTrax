package com.rallytrax.app.data.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.rallytrax.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) {
    private val _authState = MutableStateFlow<AuthState>(
        firebaseAuth.currentUser?.toAuthUser()?.let { AuthState.SignedIn(it) }
            ?: AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        val newState = user?.toAuthUser()?.let { AuthState.SignedIn(it) }
            ?: AuthState.SignedOut
        // Don't overwrite Loading or Error states from signInWithGoogle
        val current = _authState.value
        if (current !is AuthState.Loading && current !is AuthState.Error) {
            _authState.value = newState
        } else if (user != null) {
            // If we were Loading and Firebase now has a user, sign-in succeeded
            _authState.value = newState
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    suspend fun signInWithGoogle(activity: Activity): Result<AuthUser> {
        _authState.value = AuthState.Loading
        return try {
            val idToken = getGoogleIdToken(activity)
            Log.d(TAG, "Got Google ID token, signing in with Firebase...")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val user = authResult.user?.toAuthUser()
                ?: return Result.failure<AuthUser>(Exception("Sign-in succeeded but no user returned")).also {
                    _authState.value = AuthState.Error("No user returned from Firebase")
                }
            Log.d(TAG, "Firebase sign-in succeeded for ${user.email}")
            _authState.value = AuthState.SignedIn(user)
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign-in cancelled by user")
            _authState.value = AuthState.SignedOut
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Sign-in failed")
            Result.failure(e)
        }
    }

    private suspend fun getGoogleIdToken(activity: Activity): String {
        val credentialManager = CredentialManager.create(activity)

        // First try: returning users only
        try {
            val result = credentialManager.getCredential(
                context = activity,
                request = buildCredentialRequest(filterByAuthorizedAccounts = true),
            )
            return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
        } catch (_: NoCredentialException) {
            Log.d(TAG, "No returning credential, trying all accounts...")
        } catch (e: GetCredentialCancellationException) {
            throw e // Rethrow cancellation so signInWithGoogle handles it
        } catch (e: GetCredentialException) {
            Log.w(TAG, "First credential attempt failed: ${e.message}, trying all accounts...")
        }

        // Second try: show all accounts (new sign-up)
        val result = credentialManager.getCredential(
            context = activity,
            request = buildCredentialRequest(filterByAuthorizedAccounts = false),
        )
        return GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    }

    private fun buildCredentialRequest(filterByAuthorizedAccounts: Boolean): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .setAutoSelectEnabled(filterByAuthorizedAccounts)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    suspend fun authorizeDrive(activity: Activity): Result<AuthorizationResult> {
        return try {
            val scopes = listOf(Scope(DriveScopes.DRIVE_APPDATA))
            val request = AuthorizationRequest.Builder()
                .setRequestedScopes(scopes)
                .build()
            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed", e)
            Result.failure(e)
        }
    }

    fun getDriveCredential(activity: Activity): GoogleAccountCredential {
        val account = firebaseAuth.currentUser?.email
            ?: throw IllegalStateException("Not signed in")
        val credential = GoogleAccountCredential.usingOAuth2(
            activity,
            listOf(DriveScopes.DRIVE_APPDATA),
        )
        credential.selectedAccountName = account
        return credential
    }

    fun signOut() {
        firebaseAuth.signOut()
        _authState.value = AuthState.SignedOut
    }

    fun getCurrentUser(): AuthUser? {
        return firebaseAuth.currentUser?.toAuthUser()
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.SignedOut
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
    )

    companion object {
        private const val TAG = "AuthRepository"
    }
}
