package com.splitcruiser.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.splitcruiser.app.R
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Everything the app needs to be reachable when it is closed.
 *
 * Until this existed the app could reach nobody: `sendNotificationAlert` wrote a `notifications`
 * document, the in-app bell rendered it, and that was the whole chain — so a rider learned their
 * request had been accepted only by opening the app and looking. `fcmToken` was declared on the
 * user model and referenced by nothing on either platform, because without a messaging SDK there
 * was no way to obtain a token in the first place.
 */
object SplitCruiserNotifications {

    private const val TAG = "SplitCruiserPush"

    /**
     * Creates the channel the Cloud Function names on every message.
     *
     * Idempotent — `createNotificationChannel` on an existing id updates its name and description
     * and leaves the user's own choices alone. It has to run before any notification is posted: on
     * API 26+ a notification naming a channel that does not exist is dropped silently, with no
     * error anywhere.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            context.getString(R.string.push_channel_id),
            context.getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.push_channel_description)
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Whether the user has actually granted notification permission.
     *
     * Two separate things can switch notifications off and both look identical from the server's
     * side: the API 33+ runtime permission, and the per-app toggle in system settings. Checking
     * both is what stops the app asking the server to send something the system will discard.
     */
    fun canPostNotifications(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Fetches this device's FCM token, or `null` if it cannot be had.
     *
     * Returns null rather than throwing on the two cases that are expected rather than
     * exceptional:
     *
     * - **No Firebase configuration.** `google-services.json` is not in the repository — it is
     *   written from a secret in the release workflow — so a local debug build has no
     *   `FirebaseApp` at all and `FirebaseMessaging.getInstance()` throws `IllegalStateException`.
     *   A developer build must not crash on launch because of it.
     * - **No Play Services**, or a device that cannot reach FCM.
     *
     * Either way the cost is one device not receiving notifications, which is where every account
     * starts before the permission prompt is answered.
     */
    suspend fun currentToken(context: Context): String? {
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.i(TAG, "No Firebase configuration in this build; notifications are unavailable.")
            return null
        }
        return runCatching { awaitToken() }
            .onFailure { Log.w(TAG, "Could not obtain an FCM token", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Bridges the Play Services `Task` to a coroutine.
     *
     * Hand-written rather than pulling in `kotlinx-coroutines-play-services` for this one call.
     * `resume(null)` on failure rather than `resumeWithException`: every caller treats "no token"
     * and "token failed" identically, so the exception would only be caught and discarded a frame
     * later. The reason is still logged by the caller.
     */
    private suspend fun awaitToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (continuation.isActive) continuation.resume(token) }
            .addOnFailureListener { error ->
                Log.w(TAG, "FCM declined to issue a token", error)
                if (continuation.isActive) continuation.resume(null)
            }
    }
}
