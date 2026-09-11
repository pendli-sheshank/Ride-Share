package com.splitcruiser.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.splitcruiser.app.MainActivity
import com.splitcruiser.app.R
import kotlin.random.Random

/**
 * Receives pushes and token rotations.
 *
 * Two jobs, and the second is the one that is easy to forget: **a token is not permanent.** FCM
 * rotates it when the app is restored to a new device, when its data is cleared, and periodically
 * on its own. A registration that is never refreshed goes stale and then fails silently at send
 * time — nothing surfaces to the user, and the account simply stops receiving notifications.
 */
class SplitCruiserMessagingService : FirebaseMessagingService() {

    /**
     * A rotated token has to reach the server, but this callback can fire with nobody signed in
     * (at install, or after a logout).
     *
     * `registerPushToken` needs a session, so there is nothing useful to do here without one. The
     * token is re-read and re-registered on the next sign-in — see `MainViewModel.syncPushToken`,
     * which runs on every login rather than only on first launch for exactly this reason.
     */
    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token rotated; it will be re-registered on the next sign-in.")
    }

    /**
     * Draws a notification for a message that arrived while the app is in the foreground.
     *
     * The system draws `notification` payloads itself when the app is backgrounded, but silently
     * hands them to this callback instead when it is foregrounded — so without this, a chat message
     * that arrived while the user was on another screen produced nothing at all.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        if (!SplitCruiserNotifications.canPostNotifications(this)) return
        SplitCruiserNotifications.ensureChannel(this)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            // IMMUTABLE is required on API 31+ and is correct here regardless: nothing needs to
            // fill anything into this intent.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, getString(R.string.push_channel_id))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        // Re-checked here, inline, rather than relying on the `canPostNotifications` call above.
        // Two reasons, and lint flagged the second: the permission can be revoked between the two
        // calls, and a check behind a helper is invisible to lint's MissingPermission analysis —
        // the same analysis that caught the undeclared VIBRATE permission, which had made every
        // haptic call in the app throw into a bare catch since it was written.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // A random id rather than a fixed one: a fixed id means each notification replaces the
        // last, so two riders accepting in quick succession would show as one.
        runCatching {
            NotificationManagerCompat.from(this).notify(Random.nextInt(), notification)
        }.onFailure { Log.w(TAG, "Could not post a notification", it) }
    }

    private companion object {
        const val TAG = "SplitCruiserPush"
    }
}
