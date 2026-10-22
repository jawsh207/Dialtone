@file:Suppress("DEPRECATION") // CallAudioState

package app.dialtone.incall

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dialtone.telecom.CallManager
import app.dialtone.telecom.CallerLookup
import app.dialtone.telecom.stateCompat
import app.dialtone.ui.components.Avatar
import app.dialtone.ui.components.DialPad
import app.dialtone.ui.components.MorphButton
import app.dialtone.ui.components.MorphToggle
import app.dialtone.ui.theme.CallColors
import app.dialtone.util.formatDuration
import app.dialtone.util.formatNumber
import app.dialtone.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CallSnapshot(
    val number: String,
    val state: Int,
    val connectTimeMillis: Long,
    val canHold: Boolean,
)

private fun Call.snapshot() = CallSnapshot(
    number = details.handle?.schemeSpecificPart.orEmpty(),
    state = stateCompat,
    connectTimeMillis = details.connectTimeMillis,
    canHold = details.can(Call.Details.CAPABILITY_HOLD),
)

@Composable
fun InCallRoute(onFinished: () -> Unit) {
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val version by CallManager.version.collectAsStateWithLifecycle()
    val audio by CallManager.audioState.collectAsStateWithLifecycle()

    val call = remember(calls, version) {
        calls.firstOrNull { it.stateCompat == Call.STATE_RINGING }
            ?: calls.firstOrNull { it.stateCompat != Call.STATE_DISCONNECTED }
            ?: calls.firstOrNull()
    }
    LaunchedEffect(call == null) {
        if (call == null) {
            delay(900)
            onFinished()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        if (call != null) {
            val snap = remember(call, version) { call.snapshot() }
            InCallContent(call, snap, audio)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Call ended", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun InCallContent(call: Call, snap: CallSnapshot, audio: CallAudioState?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState(CallerLookup.Info(null, null), snap.number) {
        value = withContext(Dispatchers.IO) { CallerLookup.lookup(context, snap.number) }
    }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val ringing = snap.state == Call.STATE_RINGING
    val title = info.name ?: if (snap.number.isBlank()) "Unknown caller" else formatNumber(snap.number)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            statusText(snap),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.name != null && snap.number.isNotBlank()) {
            Text(
                formatNumber(snap.number),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = showKeypad && !ringing,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "center",
        ) { keypad ->
            if (keypad) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        dtmf.ifEmpty { " " },
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(12.dp))
                    DialPad(
                        onKey = { c ->
                            dtmf += c
                            call.playDtmfTone(c)
                            scope.launch {
                                delay(150)
                                call.stopDtmfTone()
                            }
                        },
                        keyHeight = 56.dp,
                    )
                }
            } else {
                CallerHalo(
                    name = info.name,
                    photoUri = info.photoUri,
                    seed = numberKey(snap.number).hashCode().toLong(),
                    animate = ringing || snap.state == Call.STATE_DIALING || snap.state == Call.STATE_CONNECTING,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        when (snap.state) {
            Call.STATE_RINGING -> IncomingActions(
                onAnswer = { CallManager.answer(call) },
                onDecline = { CallManager.reject(call) },
            )
            Call.STATE_SELECT_PHONE_ACCOUNT -> {
                SimPicker(call)
                Spacer(Modifier.height(24.dp))
                EndCallButton { CallManager.hangup(call) }
            }
            else -> {
                val muted = audio?.isMuted == true
                val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
                val holding = snap.state == Call.STATE_HOLDING
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MorphToggle(muted, { CallManager.setMuted(it) }, if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute")
                    MorphToggle(showKeypad, { showKeypad = it }, Icons.Rounded.Dialpad, "Keypad")
                    MorphToggle(speaker, { CallManager.setSpeaker(it) }, Icons.AutoMirrored.Rounded.VolumeUp, "Speaker")
                    MorphToggle(
                        holding, { CallManager.toggleHold(call) }, Icons.Rounded.Pause,
                        if (holding) "Resume" else "Hold",
                        enabled = snap.canHold || holding,
                    )
                }
                Spacer(Modifier.height(32.dp))
                EndCallButton { CallManager.hangup(call) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun statusText(snap: CallSnapshot): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snap.state) {
        while (snap.state == Call.STATE_ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return when (snap.state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_ACTIVE ->
            if (snap.connectTimeMillis > 0) formatDuration(((now - snap.connectTimeMillis) / 1000).coerceAtLeast(0))
            else "Connected"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Choose a SIM"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
        else -> "Calling…"
    }
}

/** Avatar on a slowly rotating 12-sided cookie while the call is connecting. */
@Composable
private fun CallerHalo(name: String?, photoUri: String?, seed: Long, animate: Boolean) {
    val halo = MaterialShapes.Cookie12Sided.toShape()
    val transition = rememberInfiniteTransition(label = "halo")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "angle",
    )
    val breathe by transition.animateFloat(
        0.94f, 1f, infiniteRepeatable(tween(1_200), RepeatMode.Reverse), label = "breathe",
    )
    Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(232.dp)
                .graphicsLayer {
                    rotationZ = if (animate) angle else 0f
                    val s = if (animate) breathe else 1f
                    scaleX = s
                    scaleY = s
                }
                .clip(halo)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        )
        Avatar(name, photoUri, seed, size = 156.dp)
    }
}

@Composable
private fun IncomingActions(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(Icons.Rounded.CallEnd, "Decline", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, 1f, onDecline)
        RoundAction(Icons.Rounded.Call, "Answer", CallColors.container(), CallColors.content(), pulse, onAnswer)
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    scale: Float,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MorphButton(
            onClick = onClick,
            container = container,
            content = content,
            modifier = Modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        ) { Icon(icon, label, Modifier.size(36.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EndCallButton(onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        container = MaterialTheme.colorScheme.error,
        content = MaterialTheme.colorScheme.onError,
        modifier = Modifier.width(176.dp).height(76.dp),
    ) { Icon(Icons.Rounded.CallEnd, "End call", Modifier.size(34.dp)) }
}

/** Dual-SIM: Telecom waits for us to pick an account when there's no default. */
@SuppressLint("MissingPermission")
@Composable
private fun SimPicker(call: Call) {
    val context = LocalContext.current
    val accounts = remember {
        val tm = context.getSystemService(TelecomManager::class.java)
        runCatching {
            tm.callCapablePhoneAccounts.map { handle ->
                handle to (tm.getPhoneAccount(handle)?.label?.toString() ?: "SIM")
            }
        }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { (handle, label) ->
            FilledTonalButton(
                onClick = { call.phoneAccountSelected(handle, false) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Rounded.SimCard, null)
                Spacer(Modifier.width(12.dp))
                Text(label)
            }
        }
    }
}
