package spam.blocker.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.TELECOM_SERVICE
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import spam.blocker.ui.widgets.FloatingWindow
import spam.blocker.util.Permission
import spam.blocker.util.logi
import spam.blocker.util.spf

class CallStateReceiver : BroadcastReceiver() {
    companion object {
        // The receiver is declared in the manifest, a new instance is created for every broadcast,
        //  so the handler must be shared, or pending hang-ups can't be cancelled.
        private val handler: Handler = Handler(Looper.getMainLooper())

        // Wait for the ended call to go away before answering the next one.
        private const val ANSWER_NEXT_DELAY = 1000L
    }

    private fun extractNumber(intent: Intent) : String {
        return intent.extras?.getString("incoming_number") ?: ""
    }

    override fun onReceive(ctx: Context, intent: Intent) {

        if (intent.action == "android.intent.action.PHONE_STATE") {

            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

            when (state) {
                // This is triggered when it starts ringing
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val currNumber = extractNumber(intent)
                    logi("RINGING: $currNumber")

                    // 1. Blocked by "Answer+HangUp"?
                    // Don't block calls that are not marked to block in CallScreenService.
                    val entry = HangUpQueue.find(ctx, currNumber)
                    if (entry != null && !entry.isAnswered) {
                        if (HangUpQueue.hasAnswered(ctx)) {
                            // Another spam call is being hung up, answering now would put it on
                            //  hold. This one will be answered after that one ends.
                            logi("another call is being hung up, answer later")
                        } else {
                            muteMic(ctx)
                            answerCall(ctx)
                        }
                    }
                }

                // This is triggered when a call is answered(in call)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val currNumber = extractNumber(intent)
                    logi("IN CALL: $currNumber")

                    // 1. Dismiss the Caller ID window
                    if (spf.CallerID(ctx).isEnabled) {
                        FloatingWindow.hide(ctx)
                    }

                    // 2. Hang-up the call after "hang-up-delay" seconds
                    val entry = HangUpQueue.find(ctx, currNumber)
                    if (entry != null && !entry.isAnswered) {
                        HangUpQueue.markAnswered(ctx, entry.number)

                        // Mute again, in case it was reset when the call connected.
                        muteMic(ctx)

                        // Schedule a delayed hang-up task.
                        handler.postDelayed({
                            endCall(ctx)
                            restoreMic(ctx)
                            HangUpQueue.remove(ctx, entry.number)

                            // Another spam call may be waiting, answer it after this one ends.
                            handler.postDelayed({
                                answerNextQueued(ctx)
                            }, ANSWER_NEXT_DELAY)
                        }, entry.delaySec * 1000L)
                    } else if (entry == null && currNumber.isNotEmpty()) {
                        // Any other call must never stay muted.
                        // (Android sends each broadcast twice, once without the number, that
                        //  one can't tell which call it is.)
                        restoreMic(ctx)
                    }
                }

                // This is triggered when all calls end
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    logi("IDLE")

                    // 1. Dismiss the Caller ID window
                    if (spf.CallerID(ctx).isEnabled) {
                        FloatingWindow.hide(ctx)
                    }

                    // 2. When the call ends before the hang-up delay, kill the hang-up task,
                    //  to prevent killing any following calls by mistake.
                    handler.removeCallbacksAndMessages(null)
                    HangUpQueue.removeAnswered(ctx)

                    // 3. Make sure the microphone is unmuted, this also covers the case that
                    //  the process was killed during the hang-up delay.
                    restoreMic(ctx)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun answerNextQueued(ctx: Context) {
        if (!Permission.phoneState.isGranted)
            return
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (telephony.callState == TelephonyManager.CALL_STATE_RINGING && HangUpQueue.hasUnanswered(ctx)) {
            logi("answer next queued call")
            muteMic(ctx)
            answerCall(ctx)
        }
    }

    // Mute the microphone while a spam call is connected for the hang-up delay, so the caller
    //  can't hear anything. A flag is saved to make sure it's unmuted later, even if the
    //  process is killed in between.
    private fun muteMic(ctx: Context) {
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val spf = spf.Temporary(ctx)
        if (audio.isMicrophoneMute && !spf.micMutedByHangUp) {
            return // muted by the user, leave it alone
        }
        spf.micMutedByHangUp = true
        audio.isMicrophoneMute = true
        logi("mute mic")
    }

    private fun restoreMic(ctx: Context) {
        val spf = spf.Temporary(ctx)
        if (!spf.micMutedByHangUp) {
            return
        }
        spf.micMutedByHangUp = false
        val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.isMicrophoneMute = false
        logi("unmute mic")
    }

    @SuppressLint("MissingPermission")
    private fun answerCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.acceptRingingCall()
        logi("answer call")
    }
    @SuppressLint("MissingPermission")
    private fun endCall(ctx: Context) {
        val telMgr = ctx.getSystemService(TELECOM_SERVICE) as TelecomManager
        telMgr.endCall()
        logi("end call")
    }
}
