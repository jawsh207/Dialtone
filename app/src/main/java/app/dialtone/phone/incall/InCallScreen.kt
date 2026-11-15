@file:Suppress("DEPRECATION") // CallAudioState

package app.dialtone.phone.incall

import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import app.dialtone.phone.telecom.Dialer
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import app.dialtone.phone.telecom.QuickResponses
import app.dialtone.phone.ui.components.segmentShape
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
import app.dialtone.phone.telecom.CallManager
import app.dialtone.phone.telecom.CallerLookup
import app.dialtone.phone.telecom.stateCompat
import app.dialtone.phone.ui.components.Avatar
import app.dialtone.phone.ui.components.DialPad
import app.dialtone.phone.ui.components.MorphButton
import app.dialtone.phone.ui.components.MorphToggle
import app.dialtone.phone.ui.theme.CallColors
import app.dialtone.phone.util.formatDuration
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The in-call screen: who's calling, the call timer, and the controls (mute, keypad, audio
 * output, hold, add call, end). It follows Telecom's calls through [CallManager]:
 *
 *  - an incoming call shows Answer/Decline (and Reply with message);
 *  - a second call arriving during a call ("call waiting") offers Hold & answer, End & answer
 *    or Decline;
 *  - with one call on hold, a card shows it with Swap (and Merge when the network allows a
 *    conference);
 *  - Audio picks the phone's earpiece, speaker, a wired headset or a Bluetooth device.
 */
private data class CallSnapshot(
    val number: String,
    val state: Int,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val isConference: Boolean,
    val participants: Int,
)

private fun Call.snapshot() = CallSnapshot(
    number = details.handle?.schemeSpecificPart.orEmpty(),
    state = stateCompat,
    connectTimeMillis = details.connectTimeMillis,
    canHold = details.can(Call.Details.CAPABILITY_HOLD),
    isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
    participants = children.size,
)

@Composable
fun InCallRoute(onFinished: () -> Unit) {
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val version by CallManager.version.collectAsStateWithLifecycle()
    val audio by CallManager.audioState.collectAsStateWithLifecycle()

    // The call shown: a ringing one first, then the active one (not a held call or a
    // conference participant). The other call, if any, gets its own card.
    val call = remember(calls, version) { CallManager.primaryCall() ?: calls.firstOrNull() }
    val other = remember(calls, version, call) { call?.let(CallManager::otherCall) }
    LaunchedEffect(call == null) {
        if (call == null) {
            delay(900)
            onFinished()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (call != null) {
            val snap = remember(call, version) { call.snapshot() }
            InCallContent(call, snap, audio, other, version)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Call ended", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun InCallContent(call: Call, snap: CallSnapshot, audio: CallAudioState?, other: Call?, version: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by produceState(CallerLookup.Info(null, null), snap.number) {
        value = withContext(Dispatchers.IO) { CallerLookup.lookup(context, snap.number) }
    }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var dtmf by rememberSaveable { mutableStateOf("") }
    val ringing = snap.state == Call.STATE_RINGING
    val title = when {
        snap.isConference -> "Conference call"
        else -> info.name ?: if (snap.number.isBlank()) "Unknown caller" else formatNumber(snap.number)
    }

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
        if (snap.isConference && snap.participants > 0) {
            Text(
                "${snap.participants} people",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (info.name != null && snap.number.isNotBlank()) {
            Text(
                formatNumber(snap.number),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The other call: on hold while this one is active, or the current call during call waiting.
        val canMerge = remember(call, other, version) { other != null && CallManager.canMerge(call, other) }
        if (other != null) {
            Spacer(Modifier.height(16.dp))
            OtherCallCard(
                other = other,
                version = version,
                waiting = ringing,
                canMerge = !ringing && canMerge,
                onSwap = { CallManager.swap(other) },
                onMerge = { CallManager.merge(call, other) },
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
                // Texting back needs a number to text.
                onReply = if (snap.number.isNotBlank()) ({ text: String -> CallManager.rejectWithMessage(call, text) }) else null,
                // Call waiting: answering holds the current call; or end it first.
                onEndAndAnswer = if (other != null) ({ CallManager.endAndAnswer(call) }) else null,
            )
            Call.STATE_SELECT_PHONE_ACCOUNT -> {
                SimPicker(call)
                Spacer(Modifier.height(24.dp))
                EndCallButton { CallManager.hangup(call) }
            }
            else -> {
                val muted = audio?.isMuted == true
                val holding = snap.state == Call.STATE_HOLDING
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MorphToggle(muted, { CallManager.setMuted(it) }, if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute")
                    MorphToggle(showKeypad, { showKeypad = it }, Icons.Rounded.Dialpad, "Keypad")
                    AudioRouteButton(audio)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MorphToggle(
                        holding, { CallManager.toggleHold(call) }, Icons.Rounded.Pause,
                        if (holding) "Resume" else "Hold",
                        enabled = snap.canHold || holding,
                    )
                    // Opens Dialtone's keypad; Telecom holds this call when the new one is placed.
                    MorphToggle(
                        false, { context.startActivity(Dialer.addCallIntent(context)) }, Icons.Rounded.AddIcCall, "Add call",
                        enabled = other == null && snap.state == Call.STATE_ACTIVE,
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
private fun IncomingActions(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onReply: ((String) -> Unit)?,
    onEndAndAnswer: (() -> Unit)?,
) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale",
    )
    var replying by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Rounded.CallEnd, "Decline", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, 1f, onDecline)
            if (onEndAndAnswer != null) {
                RoundAction(
                    Icons.AutoMirrored.Rounded.PhoneForwarded, "End & answer",
                    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, 1f, onEndAndAnswer,
                )
            }
            RoundAction(
                Icons.Rounded.Call, if (onEndAndAnswer != null) "Hold & answer" else "Answer",
                CallColors.container(), CallColors.content(), pulse, onAnswer,
            )
        }
        if (onReply != null) {
            FilledTonalButton(onClick = { replying = true }, modifier = Modifier.padding(bottom = 24.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Message, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reply with message")
            }
        } else {
            Spacer(Modifier.height(24.dp))
        }
    }
    if (replying && onReply != null) {
        QuickReplySheet(
            onSend = { text ->
                replying = false
                onReply(text)
            },
            onDismiss = { replying = false },
        )
    }
}

/** Decline with a text: the quick responses, or one written now. */
@Composable
private fun QuickReplySheet(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val responses by QuickResponses.responses(context).collectAsState()
    var custom by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Decline and send",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            val list = responses.orEmpty()
            list.forEachIndexed { i, text ->
                Surface(
                    onClick = { onSend(text) },
                    shape = segmentShape(i, list.size),
                    color = scheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                label = { Text("Write your own…") },
                trailingIcon = {
                    IconButton(enabled = custom.isNotBlank(), onClick = { onSend(custom.trim()) }) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "Send")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

/** The other call: on hold (Swap, Merge), or the call you're on while a new one rings. */
@Composable
private fun OtherCallCard(
    other: Call,
    version: Int,
    waiting: Boolean,
    canMerge: Boolean,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
) {
    val context = LocalContext.current
    val snap = remember(other, version) { other.snapshot() }
    val info by produceState(CallerLookup.Info(null, null), snap.number) {
        value = withContext(Dispatchers.IO) { CallerLookup.lookup(context, snap.number) }
    }
    val name = when {
        snap.isConference -> "Conference call"
        else -> info.name ?: if (snap.number.isBlank()) "Unknown caller" else formatNumber(snap.number)
    }
    val status = when {
        waiting -> "Current call"
        snap.state == Call.STATE_HOLDING -> "On hold"
        else -> "Calling…"
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!waiting) {
                if (canMerge) {
                    FilledTonalButton(onClick = onMerge) {
                        Icon(Icons.AutoMirrored.Rounded.CallMerge, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Merge")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (snap.state == Call.STATE_HOLDING) {
                    FilledTonalButton(onClick = onSwap) {
                        Icon(Icons.Rounded.SwapCalls, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Swap")
                    }
                }
            }
        }
    }
}

/**
 * Where call audio plays. With only the earpiece and speaker available it's a simple Speaker
 * toggle. With a wired headset or Bluetooth device it becomes "Audio", showing the current
 * output and offering every available one, so turning the speaker off never drops a headset.
 */
@Composable
private fun AudioRouteButton(audio: CallAudioState?) {
    val mask = audio?.supportedRouteMask ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val extra = mask and (CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET) != 0
    if (!extra) {
        MorphToggle(
            route == CallAudioState.ROUTE_SPEAKER,
            { on -> CallManager.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE) },
            Icons.AutoMirrored.Rounded.VolumeUp, "Speaker",
        )
        return
    }
    var menu by remember { mutableStateOf(false) }
    val icon = when (route) {
        CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
        CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
        CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
        else -> Icons.Rounded.PhoneInTalk
    }
    // Names of paired devices need Bluetooth permission on Android 12+, which Dialtone doesn't
    // ask for; without it they show as "Bluetooth".
    val devices: List<BluetoothDevice> = if (Build.VERSION.SDK_INT >= 28) audio?.supportedBluetoothDevices?.toList().orEmpty() else emptyList()
    val active: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 28) audio?.activeBluetoothDevice else null
    Box {
        MorphToggle(route != CallAudioState.ROUTE_EARPIECE, { menu = true }, icon, "Audio")
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            @Composable
            fun option(label: String, optionIcon: ImageVector, selected: Boolean, onPick: () -> Unit) = DropdownMenuItem(
                text = { Text(label) },
                leadingIcon = { Icon(optionIcon, null) },
                trailingIcon = if (selected) ({ Icon(Icons.Rounded.Check, "Selected") }) else null,
                onClick = {
                    menu = false
                    onPick()
                },
            )
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) {
                option("Phone", Icons.Rounded.PhoneInTalk, route == CallAudioState.ROUTE_EARPIECE) {
                    CallManager.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
                }
            }
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) {
                option("Wired headset", Icons.Rounded.Headset, route == CallAudioState.ROUTE_WIRED_HEADSET) {
                    CallManager.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
                }
            }
            option("Speaker", Icons.AutoMirrored.Rounded.VolumeUp, route == CallAudioState.ROUTE_SPEAKER) {
                CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
            if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                if (devices.size > 1) {
                    devices.forEachIndexed { i, device ->
                        @SuppressLint("MissingPermission")
                        val label = runCatching { device.name }.getOrNull() ?: "Bluetooth ${i + 1}"
                        option(label, Icons.Rounded.Bluetooth, route == CallAudioState.ROUTE_BLUETOOTH && device == active) {
                            CallManager.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH, device)
                        }
                    }
                } else {
                    @SuppressLint("MissingPermission")
                    val label = devices.firstOrNull()?.let { d -> runCatching { d.name }.getOrNull() } ?: "Bluetooth"
                    option(label, Icons.Rounded.Bluetooth, route == CallAudioState.ROUTE_BLUETOOTH) {
                        CallManager.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH, devices.firstOrNull())
                    }
                }
            }
        }
    }
}
