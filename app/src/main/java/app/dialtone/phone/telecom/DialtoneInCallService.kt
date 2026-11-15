package app.dialtone.phone.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import app.dialtone.phone.incall.InCallActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android's link to Dialtone during calls. Because Dialtone is the default phone app, the
 * system binds this service whenever a call starts and hands it every call (incoming,
 * outgoing or on hold) until they all end.
 *
 * It passes each call to [CallManager], which tracks call state for the in-call screen, and
 * keeps the call notification current. Outgoing calls open the in-call screen straight away;
 * incoming calls ring through the full-screen call notification instead, which Android shows
 * over the lock screen, or as a pop-up banner when the phone is in use.
 */
class DialtoneInCallService : InCallService() {

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        CallNotifications.ensureChannels(this)
        scope.launch {
            CallManager.version.collect {
                CallNotifications.update(this@DialtoneInCallService, CallManager.calls.value)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        CallManager.onCallAdded(call)
        // Ringing calls surface through the full-screen notification (heads-up when
        // the phone is in use). Outgoing calls open the in-call screen directly.
        if (call.stateCompat != Call.STATE_RINGING) {
            startActivity(InCallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.onCallRemoved(call)
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        scope.cancel()
        CallManager.service = null
        CallNotifications.cancel(this)
        super.onDestroy()
    }
}
