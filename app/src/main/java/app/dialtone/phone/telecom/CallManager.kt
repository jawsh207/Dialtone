@file:Suppress("DEPRECATION") // CallAudioState / setAudioRoute still work on all supported versions

package app.dialtone.phone.telecom

import android.bluetooth.BluetoothDevice
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

/**
 * Process-wide holder for the calls Telecom hands to [DialtoneInCallService], and the actions
 * the in-call screen and notifications take on them.
 *
 * Telecom does the juggling between calls itself: answering or placing a call while another
 * is active puts that one on hold, and un-holding a call holds whichever is active. So "swap"
 * is just un-holding the other call. Calls that are part of a conference also appear here as
 * children (they have a parent); the screen shows the conference, not each child.
 */
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

    /** Calls shown as their own call: not ended, and not a participant inside a conference. */
    fun topLevelCalls(): List<Call> =
        _calls.value.filter { it.parent == null && it.stateCompat != Call.STATE_DISCONNECTED }

    fun ringingCall(): Call? = _calls.value.firstOrNull { it.stateCompat == Call.STATE_RINGING }

    fun primaryCall(): Call? =
        ringingCall() ?: topLevelCalls().firstOrNull { it.stateCompat != Call.STATE_HOLDING }
            ?: topLevelCalls().firstOrNull()

    /** While on [current], the other call in progress (on hold, or the active one during call waiting). */
    fun otherCall(current: Call): Call? = topLevelCalls().firstOrNull { it != current && it.stateCompat != Call.STATE_RINGING }

    /** Call waiting: end the current call, then answer the waiting one. */
    fun endAndAnswer(waiting: Call) {
        otherCall(waiting)?.disconnect()
        answer(waiting)
    }

    /** Switch to the held call; Telecom puts the active one on hold. */
    fun swap(held: Call) = held.unhold()

    /** Joins [call] with [other] into a conference, when the network allows it. */
    fun merge(call: Call, other: Call) {
        when {
            call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> call.mergeConference()
            other in call.conferenceableCalls -> call.conference(other)
            other.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> other.mergeConference()
        }
    }

    fun canMerge(call: Call, other: Call): Boolean =
        call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
            other.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) ||
            other in call.conferenceableCalls

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call) = call.reject(false, null)

    /** Declines and texts [message] back (Telecom sends it via the default SMS app). */
    fun rejectWithMessage(call: Call, message: String) = call.reject(true, message)
    fun hangup(call: Call) = call.disconnect()

    fun toggleHold(call: Call) {
        if (call.stateCompat == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    /**
     * Where call audio plays. [route] is a CallAudioState route; for Bluetooth, [device] picks
     * the headset/car (several can be paired). Earpiece and wired headset share one route:
     * Android uses the headset when one is plugged in.
     */
    fun setAudioRoute(route: Int, device: BluetoothDevice? = null) {
        val svc = service ?: return
        if (route == CallAudioState.ROUTE_BLUETOOTH && device != null && Build.VERSION.SDK_INT >= 28) {
            svc.requestBluetoothAudio(device)
        } else {
            svc.setAudioRoute(route)
        }
    }
}
