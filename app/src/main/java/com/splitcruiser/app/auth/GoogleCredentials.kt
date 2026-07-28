package com.splitcruiser.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Obtaining a Google ID token on Android, which is the only part of Google sign-in that cannot be
 * shared: the exchange for a Firebase session is REST, and lives in `:shared`.
 *
 * Credential Manager, not `GoogleSignInClient` — the play-services-auth API is deprecated, and
 * the deprecation matters here because the replacement is also the only one that keeps working on
 * devices where Play Services updates independently of the app.
 */

/** Distinguishes "the user backed out" from a real failure, so the UI can stay silent about it. */
class GoogleSignInCancelledException : Exception("Google sign-in was cancelled.")

/**
 * Shows the account picker and returns the ID token it produces.
 *
 * [context] must be an Activity — Credential Manager renders a bottom sheet, and an application
 * context throws at the point of display rather than at the call.
 */
suspend fun requestGoogleIdToken(context: Context, serverClientId: String): String {
    require(serverClientId.isNotBlank()) {
        "GOOGLE_WEB_CLIENT_ID is not set in this build, so Google sign-in cannot be offered."
    }

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
        .build()

    val response = try {
        CredentialManager.create(context).getCredential(context, request)
    } catch (e: GetCredentialCancellationException) {
        throw GoogleSignInCancelledException()
    } catch (e: NoCredentialException) {
        throw GoogleSignInException(
            "No Google account is available on this device. Add one in Settings and try again.",
            e,
        )
    } catch (e: GetCredentialException) {
        // The one worth naming: Play Services reports an unregistered signing certificate as a
        // generic failure whose message mentions the developer console, and nothing about it
        // suggests the actual fix.
        val hint = if (e.message?.contains("console", ignoreCase = true) == true) {
            " Check that this build's signing SHA-1 is registered on the Android app in the " +
                "Firebase console, and that Google sign-in is enabled there."
        } else {
            ""
        }
        throw GoogleSignInException("Google sign-in failed.$hint", e)
    }

    val credential = response.credential
    if (credential !is CustomCredential ||
        credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        throw GoogleSignInException("Google returned an unexpected credential type.", null)
    }

    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

class GoogleSignInException(message: String, cause: Throwable?) : Exception(message, cause)
