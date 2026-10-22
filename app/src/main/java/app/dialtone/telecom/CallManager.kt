@file:Suppress("DEPRECATION") // CallAudioState / setAudioRoute still work on all supported versions

package app.dialtone.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

val Call.stateCompat: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else state

/** Process-wide holder for the calls Telecom hands to [DialtoneInCallService]. */
object CallManager {

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    /** Call objects mutate in place, so this ticks whenever any of them change. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    @Volatile
    internal var service: DialtoneInCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = bump()
        override fun onDetailsChanged(call: Call, details: Call.Details) = bump()
    }

    internal fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        _calls.update { it + call }
        bump()
    }

    internal fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        _calls.update { it - call }
        bump()
    }

    internal fun onAudioStateChanged(state: CallAudioState?) {
        _audioState.value = state
    }

    private fun bump() = _version.update { it + 1 }

    fun ringingCall(): Call? = _calls.value.firstOrNull { it.stateCompat == Call.STATE_RINGING }

    fun primaryCall(): Call? =
        ringingCall() ?: _calls.value.firstOrNull { it.stateCompat != Call.STATE_DISCONNECTED }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)
    fun hangup(call: Call) = call.disconnect()

    fun toggleHold(call: Call) {
        if (call.stateCompat == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }
}
