package app.dialtone.ui.voicemail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dialtone.data.Contact
import app.dialtone.data.Voicemail
import app.dialtone.data.VvmAccount
import app.dialtone.data.VvmHealth
import app.dialtone.telecom.Dialer
import app.dialtone.ui.components.Avatar
import app.dialtone.ui.components.EmptyState
import app.dialtone.ui.components.segmentShape
import app.dialtone.util.formatDuration
import app.dialtone.util.formatNumber
import app.dialtone.util.formatShortDate
import app.dialtone.util.numberKey
import kotlinx.coroutines.delay

/** Everything the voicemail tab can ask the app to do. */
class VoicemailActions(
    val onCall: (String) -> Unit,
    val onMarkRead: (Voicemail) -> Unit,
    val onDelete: (Voicemail) -> Unit,
    val onRequestAudio: (Voicemail) -> Unit,
    val onToggleVisualVoicemail: (VvmAccount, Boolean) -> Unit,
    val onSyncNow: () -> Unit,
    val readLog: () -> String,
    val clearLog: () -> Unit,
)

@Composable
fun VoicemailScreen(
    voicemails: List<Voicemail>?,
    contactIndex: Map<String, Contact>,
    accounts: List<VvmAccount>,
    health: VvmHealth,
    actions: VoicemailActions,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val player = remember { VoicemailPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.tick()
            delay(80)
        }
    }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (voicemails == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val unread = voicemails.count { !it.isRead }
                Text(
                    when {
                        voicemails.isEmpty() -> "Voicemail"
                        unread > 0 -> "$unread new"
                        else -> "All caught up"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { Dialer.callVoicemail(context) }) { Text("Call voicemail") }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Settings, "Voicemail settings")
                }
            }
        }
        statusBannerFor(accounts, health)?.let { banner ->
            item(key = "status") {
                StatusBanner(
                    banner = banner,
                    onAction = if (banner.retry) actions.onSyncNow else ({ showSettings = true }),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        if (voicemails.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Rounded.Voicemail,
                    "No voicemails",
                    "New messages download here automatically. You can always dial in to listen.",
                    Modifier.padding(top = 24.dp),
                ) {
                    Button(onClick = { Dialer.callVoicemail(context) }) {
                        Icon(Icons.Rounded.Call, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Call voicemail")
                    }
                }
            }
        }
        itemsIndexed(voicemails, key = { _, v -> v.id }) { i, vm ->
            VoicemailRow(
                vm = vm,
                contact = contactIndex[numberKey(vm.number)],
                expanded = expandedId == vm.id,
                onToggle = {
                    expandedId = if (expandedId == vm.id) null else vm.id
                    actions.onMarkRead(vm)
                },
                player = player,
                onCall = actions.onCall,
                onRequestAudio = actions.onRequestAudio,
                onDelete = {
                    if (player.currentId == vm.id) player.release()
                    actions.onDelete(vm)
                },
                shape = segmentShape(i, voicemails.size),
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
    }

    if (showSettings) {
        VoicemailSettingsSheet(
            accounts = accounts,
            health = health,
            actions = actions,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun VoicemailRow(
    vm: Voicemail,
    contact: Contact?,
    expanded: Boolean,
    onToggle: () -> Unit,
    player: VoicemailPlayer,
    onCall: (String) -> Unit,
    onRequestAudio: (Voicemail) -> Unit,
    onDelete: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(expanded, vm.hasContent) {
        if (expanded && !vm.hasContent) onRequestAudio(vm)
    }
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val display = contact?.name ?: if (vm.number.isBlank()) "Unknown" else formatNumber(vm.number)
    val active = player.currentId == vm.id
    val container by animateColorAsState(
        scheme.surfaceBright, // cards: surfaceBright, as in Contacts
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "vm",
    )

    Surface(onClick = onToggle, shape = shape, color = container, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact?.name, contact?.photoUri, numberKey(vm.number).hashCode().toLong(), 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        display,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (vm.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${formatShortDate(context, vm.date)} • ${formatDuration(vm.durationSec)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    if (!expanded && !vm.transcription.isNullOrBlank()) {
                        Text(
                            vm.transcription,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!vm.isRead) {
                    Box(Modifier.size(10.dp).background(scheme.primary, CircleShape))
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    if (!vm.transcription.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerLow) {
                            Text(vm.transcription, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (vm.hasContent) {
                        val fraction = if (active && player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
                        val totalSec = if (active && player.durationMs > 0) player.durationMs / 1000L else vm.durationSec
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayButton(playing = active && player.isPlaying) { player.toggle(vm) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                LinearWavyProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .pointerInput(vm.id) {
                                            detectTapGestures { offset -> player.seek(vm, offset.x / size.width) }
                                        },
                                )
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text(formatDuration(if (active) player.positionMs / 1000L else 0), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                    Spacer(Modifier.weight(1f))
                                    Text(formatDuration(totalSec), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (active) player.error?.let {
                            Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    } else {
                        Text(
                            "Downloading audio…",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.number.isNotBlank()) {
                            AssistChip(
                                onClick = { onCall(vm.number) },
                                label = { Text("Call back") },
                                leadingIcon = { Icon(Icons.Rounded.Call, null, Modifier.size(18.dp)) },
                            )
                            AssistChip(
                                onClick = { Dialer.sms(context, vm.number) },
                                label = { Text("Message") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, null, Modifier.size(18.dp)) },
                            )
                        }
                        AssistChip(
                            onClick = onDelete,
                            label = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }
    }
}

/** Circle when paused, rounded square while playing. */
@Composable
private fun PlayButton(playing: Boolean, onClick: () -> Unit) {
    val corner by animateIntAsState(
        if (playing) 30 else 50, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "play",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = corner),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
        }
    }
}
