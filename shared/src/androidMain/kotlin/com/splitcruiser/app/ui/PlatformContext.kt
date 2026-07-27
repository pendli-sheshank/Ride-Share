package com.splitcruiser.app.ui

import android.app.Application
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

private var appContext: Application? = null

fun initializePlatformContext(app: Application) {
    appContext = app
}

actual object PlatformContext {
    actual fun vibrate(duration: Long) {
        appContext?.let { context ->
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        }
    }

    actual fun showMessage(message: String) {
        appContext?.let { context ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
