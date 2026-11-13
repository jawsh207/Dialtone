package app.dialtone.phone.ui.voicemail

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import app.dialtone.phone.data.Contact
import app.dialtone.phone.screening.SendToVoicemail
import app.dialtone.phone.ui.components.ContactPickerSheet
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.numberKey
import androidx.compose.material3.IconButton
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.phone.data.TranscriptionUi
import android.provider.Settings
import android.content.Intent
import app.dialtone.phone.data.VvmAccount
import app.dialtone.phone.data.VvmHealth
import app.dialtone.phone.ui.components.segmentShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StatusBannerModel(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** Button label; null for no button. */
    val actionLabel: String?,
    /** true → action retries sync; false → action opens settings. */
    val retry: Boolean,
    val isError: Boolean,
)

/** A banner only when something needs the user's attention. */
fun statusBannerFor(accounts: List<VvmAccount>, health: VvmHealth): StatusBannerModel? {
    if (accounts.isEmpty()) return null
    val ours = accounts.filter { it.supported && it.enabled && !it.carrierAppInstalled }
    if (accounts.none { it.supported }) {
        return StatusBannerModel(
            Icons.Rounded.Voicemail,
            "Visual voicemail isn't available",
            "Your carrier isn't in Dialtone's list yet. New voicemails still show up as a " +
                "notification, and you can dial in to listen.",
            actionLabel = null, retry = false, isError = false,
        )
    }
    if (ours.isEmpty()) return null
    return when (health) {
        VvmHealth.Configuring -> StatusBannerModel(
            Icons.Rounded.Sync,
            "Setting up visual voicemail",
            "Talking to your carrier. This can take a few minutes the first time.",
            "Check again", retry = true, isError = false,
        )
        VvmHealth.NoConnection -> StatusBannerModel(
            Icons.Rounded.CloudOff,
            "Can't reach your voicemail",
            "Visual voicemail needs mobile data. Check that mobile data is on, then try again.",
            "Try again", retry = true, isError = true,
        )
        VvmHealth.Failed -> StatusBannerModel(
            Icons.Rounded.ErrorOutline,
            "Visual voicemail setup failed",
            "Your carrier didn't accept the setup request. Try again, or open settings to see the log.",
            "Try again", retry = true, isError = true,
        )
        else -> null
    }
}

@Composable
fun StatusBanner(banner: StatusBannerModel, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (banner.isError) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (banner.isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(banner.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(banner.title, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Text(banner.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            val label = banner.actionLabel
            if (label != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAction) { Text(label) }
                }
            } else {
                Spacer(Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun protocolName(protocol: String?): String = when (protocol) {
    "vvm_type_omtp" -> "OMTP"
    "vvm_type_cvvm" -> "CVVM"
    "vvm_type_vvm3" -> "VVM3"
    "vvm_type_advvm" -> "AdvVVM"
    null -> "none"
    else -> protocol.removePrefix("vvm_type_")
}

@Composable
fun VoicemailSettingsSheet(
    accounts: List<VvmAccount>,
    health: VvmHealth,
    transcription: TranscriptionUi,
    contactIndex: Map<String, Contact>,
    actions: VoicemailActions,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    var logVersion by remember { mutableStateOf(0) }
    LaunchedEffect(showLog, logVersion) {
        if (showLog) logText = withContext(Dispatchers.IO) { actions.readLog() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Visual voicemail", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(top = 4.dp))
            Text(
                "Downloads your messages and transcripts from your carrier over mobile data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 16.dp))

            if (accounts.isEmpty()) {
                Text("No SIM found.", style = MaterialTheme.typography.bodyLarge)
            }
            accounts.forEachIndexed { i, account ->
                Surface(
                    shape = segmentShape(i, accounts.size),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(account.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !account.supported -> "Not supported for this carrier"
                                    account.carrierAppInstalled -> "Handled by your carrier's voicemail app"
                                    !account.enabled -> "Off · ${protocolName(account.protocol)}"
                                    account.activated -> "Active · ${protocolName(account.protocol)}"
                                    else -> "Setting up · ${protocolName(account.protocol)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = account.enabled,
                            enabled = account.supported,
                            onCheckedChange = { actions.onToggleVisualVoicemail(account, it) },
                        )
                    }
                }
            }

            if (transcription.supported) {
                Spacer(Modifier.padding(top = 12.dp))
                TranscriptionCard(transcription, actions.onToggleTranscription)
            }

            Spacer(Modifier.padding(top = 12.dp))
            SendToVoicemailCard(contactIndex)

            Spacer(Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        actions.onSyncNow()
                        Toast.makeText(context, "Checking for new voicemail…", Toast.LENGTH_SHORT).show()
                    },
                    enabled = accounts.any { it.supported && it.enabled },
                ) {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync now")
                }
                OutlinedButton(onClick = { showLog = !showLog }) {
                    Text(if (showLog) "Hide diagnostics" else "Diagnostics")
                }
            }

            AnimatedVisibility(showLog) {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        "Status: ${health.name}. The log stays on this phone; passwords are removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceBright,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    ) {
                        Text(
                            logText.ifBlank { "No log entries yet." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("Dialtone voicemail log", logText))
                        }) { Text("Copy") }
                        TextButton(onClick = {
                            actions.clearLog()
                            logVersion++
                        }) { Text("Clear") }
                    }
                }
            }
        }
    }
}

/** Transcription switch. Dialtone has no speech engine of its own: it uses the system recognizer. */
@Composable
private fun TranscriptionCard(transcription: TranscriptionUi, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val name = transcription.recognizerName
    val description = if (!transcription.available) {
        "No speech-to-text app is set up. Install one that can transcribe recordings and choose " +
            "it as your phone's voice input."
    } else {
        "Tries to turn voicemails without a carrier transcript into text using your phone's " +
            "speech-to-text app" + (name?.let { " ($it)" } ?: "") + ". Whether it works depends " +
            "on that app: not every app can transcribe recordings, and some send audio off the phone."
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Transcribe voicemail", style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = transcription.enabled,
                    enabled = transcription.available,
                    onCheckedChange = onToggle,
                )
            }
            TextButton(
                onClick = {
                    // Language & region, where the speech/voice input choice lives; the main
                    // Settings screen if this build has no language screen.
                    val opened = runCatching {
                        context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                modifier = Modifier.padding(start = 0.dp),
            ) { Text(if (transcription.available) "Change voice input" else "Set up voice input") }
        }
    }
}

/**
 * "Send to voicemail": numbers whose calls skip ringing and go straight to voicemail.
 * Lists them (contact name when known) with remove buttons; add by typing a number or by
 * picking a contact (all their numbers). The call log and Contacts list offer this too.
 */
@Composable
private fun SendToVoicemailCard(contactIndex: Map<String, Contact>) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val numbers by SendToVoicemail.numbers(context).collectAsState()
    var typing by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val list = numbers.orEmpty()

    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceBright, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Send to voicemail", style = MaterialTheme.typography.titleMedium)
            Text(
                "Calls from these numbers don't ring; they go straight to voicemail.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Spacer(Modifier.padding(top = 4.dp))
            list.forEach { number ->
                val name = contactIndex[numberKey(number)]?.name
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(name ?: formatNumber(number), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        if (name != null) {
                            Text(formatNumber(number), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { SendToVoicemail.remove(context, number) }) {
                        Icon(Icons.Rounded.Close, "Stop sending ${name ?: number} to voicemail", tint = scheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { typing = true }) { Text("Add number") }
                TextButton(onClick = { picking = true }) { Text("Add from contacts") }
            }
        }
    }

    if (typing) {
        var entry by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("Send to voicemail") },
            text = {
                OutlinedTextField(
                    value = entry,
                    onValueChange = { v -> entry = v.filter { it.isDigit() || it in "+()- " } },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = numberKey(entry).length >= 3,
                    onClick = {
                        SendToVoicemail.add(context, entry)
                        typing = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }

    if (picking) {
        val keys = list.map(::numberKey).toSet()
        val contacts = remember(contactIndex, keys) {
            contactIndex.values.distinctBy { it.id }
                .filter { c -> c.numbers.any { numberKey(it.number) !in keys } }
                .sortedBy { it.name.lowercase() }
        }
        ContactPickerSheet(
            title = "Send to voicemail",
            contacts = contacts,
            emptyText = "All your contacts are already sent to voicemail.",
            onPick = { contact ->
                SendToVoicemail.add(context, contact.numbers.map { it.number })
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
