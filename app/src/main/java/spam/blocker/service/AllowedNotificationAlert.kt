package spam.blocker.service

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.net.toUri
import spam.blocker.util.spf
import spam.blocker.util.loge

// Fires SpamBlocker's own custom alert (ringtone/vibration/flashlight/wake-screen) for a
// notification that Notification Screening explicitly ALLOWED, per the app-specific config
// set under "Basic Rules Notification" > "Notification Allowed Alert". This exists because
// the user may have muted the original app's own notification channel to silence blocked
// notifications, which would otherwise also silence legitimate allowed ones.
fun fireAllowedNotificationAlert(ctx: Context, config: spf.AppAlertConfig) {
    if (config.ringtone.isNotEmpty()) {
        try {
            val ringtone = RingtoneManager.getRingtone(ctx, config.ringtone.toUri())
            ringtone?.play()
            // This is a brief alert chime, not a continuous call ringtone: force-stop it
            //  after a few seconds in case the selected sound is long or set to loop.
            Handler(Looper.getMainLooper()).postDelayed({
                if (ringtone?.isPlaying == true) {
                    ringtone.stop()
                }
            }, 3000)
        } catch (e: Exception) {
            loge("Failed to play allowed-notification ringtone: ${e.message}")
        }
    }

    if (config.vibrate) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            loge("Failed to vibrate for allowed-notification alert: ${e.message}")
        }
    }

    if (config.flashlight) {
        flashTorch(ctx)
    }

    if (config.wakeScreen) {
        wakeScreenBriefly(ctx)
    }
}

private fun flashTorch(ctx: Context) {
    try {
        val cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return

        val handler = Handler(Looper.getMainLooper())
        val pulses = 3
        val pulseMillis = 300L
        for (i in 0 until pulses) {
            handler.postDelayed({
                try {
                    cameraManager.setTorchMode(cameraId, true)
                } catch (e: Exception) {
                    loge("Failed to turn on torch: ${e.message}")
                }
            }, i * pulseMillis * 2)
            handler.postDelayed({
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (e: Exception) {
                    loge("Failed to turn off torch: ${e.message}")
                }
            }, i * pulseMillis * 2 + pulseMillis)
        }
    } catch (e: Exception) {
        loge("Failed to flash torch for allowed-notification alert: ${e.message}")
    }
}

private fun wakeScreenBriefly(ctx: Context) {
    try {
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "spam.blocker:allowed_notification_wake"
        )
        wakeLock.acquire(3000)
        Handler(Looper.getMainLooper()).postDelayed({
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }, 3000)
    } catch (e: Exception) {
        loge("Failed to wake screen for allowed-notification alert: ${e.message}")
    }
}
