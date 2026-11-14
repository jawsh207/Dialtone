package app.dialtone.phone.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.automirrored.outlined.PhoneForwarded
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.TranscriptionUi
import app.dialtone.phone.data.VvmAccount
import app.dialtone.phone.data.VvmHealth
import app.dialtone.phone.screening.SendToVoicemail
import app.dialtone.phone.telecom.QuickResponses
import app.dialtone.phone.ui.components.ContactPickerSheet
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.components.segmentShape
import app.dialtone.phone.ui.voicemail.VoicemailActions
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings, as a full page laid out like the Contacts, Zino and Twill apps: a back bar, then
 * sections with a primary-coloured header and rows grouped into rounded cards (icon, title,
 * summary, switch or action), and the version at the bottom.
 */
@Composable
fun SettingsScreen(
    accounts: List<VvmAccount>,
    health: VvmHealth,
    transcription: TranscriptionUi,
    contactIndex: Map<String, Contact>,
    actions: VoicemailActions,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val numbers by SendToVoicemail.numbers(context).collectAsState()
    val telecom = remember { context.getSystemService(TelecomManager::class.java) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    var typingNumber by rememberSaveable { mutableStateOf(false) }
    var pickingContact by rememberSaveable { mutableStateOf(false) }
    var editingResponses by rememberSaveable { mutableStateOf(false) }
    var viewingLog by rememberSaveable { mutableStateOf(false) }

    fun open(intent: Intent?) {
        if (intent == null) return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    // A Surface (not just a background) so text and icons get the theme's on-background colour.
    Surface(color = scheme.background, contentColor = scheme.onBackground, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Opaque in the background colour (status bar included), so the list scrolls behind it.
        Row(
            Modifier.fillMaxWidth().background(scheme.background).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ---- Visual voicemail
            item(key = "vvm-header") { SectionHeader("Visual voicemail") }
            val vvmRows = accounts.size + 1
            if (accounts.isEmpty()) {
                item(key = "vvm-none") {
                    SettingsRow(Icons.Outlined.Voicemail, "No SIM found", segmentShape(0, vvmRows), summary = "Visual voicemail needs a SIM from a supported carrier")
                }
            }
            itemsIndexed(accounts, key = { _, a -> "vvm-${a.label}" }) { i, account ->
                SettingsRow(
                    icon = Icons.Outlined.Voicemail,
                    title = account.label,
                    summary = when {
                        !account.supported -> "Not supported for this carrier"
                        account.carrierAppInstalled -> "Handled by your carrier's voicemail app"
                        !account.enabled -> "Off · ${protocolName(account.protocol)}"
                        account.activated -> "On · ${protocolName(account.protocol)}"
                        else -> "Setting up · ${protocolName(account.protocol)}"
                    },
                    shape = segmentShape(i, vvmRows),
                    onClick = if (account.supported) ({ actions.onToggleVisualVoicemail(account, !account.enabled) }) else null,
                    trailing = {
                        Switch(
                            checked = account.enabled,
                            enabled = account.supported,
                            onCheckedChange = { actions.onToggleVisualVoicemail(account, it) },
                        )
                    },
                )
            }
            item(key = "vvm-sync") {
                val canSync = accounts.any { it.supported && it.enabled }
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Check for new voicemail",
                    summary = "Downloads messages and transcripts from your carrier over mobile data",
                    shape = segmentShape(vvmRows - 1, vvmRows),
                    onClick = if (canSync) ({
                        actions.onSyncNow()
                        Toast.makeText(context, "Checking for new voicemail…", Toast.LENGTH_SHORT).show()
                    }) else null,
                )
            }

            // ---- Transcription
            if (transcription.supported) {
                item(key = "tx-header") { SectionHeader("Transcription") }
                item(key = "tx-switch") {
                    val name = transcription.recognizerName
                    SettingsRow(
                        icon = Icons.Outlined.Subtitles,
                        title = "Transcribe voicemail",
                        summary = if (!transcription.available) {
                            "No speech-to-text app is set up. Install one that can transcribe recordings and choose it as your voice input"
                        } else {
                            "Tries to transcribe voicemails without a carrier transcript using your speech-to-text app" +
                                (name?.let { " ($it)" } ?: "") +
                                ". Whether it works depends on that app; some send audio off the phone"
                        },
                        shape = segmentShape(0, 2),
                        onClick = if (transcription.available) ({ actions.onToggleTranscription(!transcription.enabled) }) else null,
                        trailing = {
                            Switch(
                                checked = transcription.enabled,
                                enabled = transcription.available,
                                onCheckedChange = actions.onToggleTranscription,
                            )
                        },
                    )
                }
                item(key = "tx-voice-input") {
                    SettingsRow(
                        icon = Icons.Outlined.Language,
                        title = "Change voice input",
                        summary = "Language & region settings, where the speech-to-text app is chosen",
                        shape = segmentShape(1, 2),
                        onClick = { open(Intent(Settings.ACTION_LOCALE_SETTINGS)) },
                    )
                }
            }

            // ---- Send to voicemail
            item(key = "stv-header") { SectionHeader("Send to voicemail") }
            val list = numbers.orEmpty()
            val stvRows = list.size + 2
            itemsIndexed(list, key = { _, n -> "stv-$n" }) { i, number ->
                val name = contactIndex[numberKey(number)]?.name
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.PhoneForwarded,
                    title = name ?: formatNumber(number),
                    summary = if (name != null) formatNumber(number) else null,
                    shape = segmentShape(i, stvRows),
                    trailing = {
                        IconButton(onClick = { SendToVoicemail.remove(context, number) }) {
                            Icon(Icons.Rounded.Close, "Stop sending ${name ?: number} to voicemail", tint = scheme.onSurfaceVariant)
                        }
                    },
                )
            }
            item(key = "stv-add") {
                SettingsRow(
                    icon = Icons.Rounded.Add,
                    title = "Add number",
                    summary = if (list.isEmpty()) "Calls from numbers on this list don't ring; they go straight to voicemail" else null,
                    shape = segmentShape(stvRows - 2, stvRows),
                    onClick = { typingNumber = true },
                )
            }
            item(key = "stv-contacts") {
                SettingsRow(
                    icon = Icons.Outlined.PersonAdd,
                    title = "Add from contacts",
                    shape = segmentShape(stvRows - 1, stvRows),
                    onClick = { pickingContact = true },
                )
            }

            // ---- Calls
            item(key = "calls-header") { SectionHeader("Calls") }
            item(key = "calls-blocked") {
                SettingsRow(
                    Icons.Outlined.Block, "Blocked numbers", segmentShape(0, 3),
                    summary = "Calls and texts from these numbers are rejected",
                    onClick = { open(runCatching { telecom?.createManageBlockedNumbersIntent() }.getOrNull()) },
                )
            }
            item(key = "calls-accounts") {
                SettingsRow(
                    Icons.Outlined.SimCard, "Calling accounts", segmentShape(1, 3),
                    summary = "SIMs, call forwarding, call waiting and caller ID",
                    onClick = { open(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) },
                )
            }
            item(key = "calls-responses") {
                SettingsRow(
                    Icons.Outlined.ChatBubbleOutline, "Quick responses", segmentShape(2, 3),
                    summary = "Texts you can send when declining a call",
                    onClick = { editingResponses = true },
                )
            }

            // ---- Troubleshooting
            item(key = "log-header") { SectionHeader("Troubleshooting") }
            item(key = "log-view") {
                SettingsRow(
                    Icons.Outlined.Description, "View voicemail log", segmentShape(0, 3),
                    summary = "Status: ${health.name}. Sync, transcription and call screening; stays on this phone",
                    onClick = { viewingLog = true },
                )
            }
            item(key = "log-copy") {
                SettingsRow(
                    Icons.Outlined.ContentCopy, "Copy voicemail log", segmentShape(1, 3),
                    summary = "Passwords are removed",
                    onClick = {
                        val text = actions.readLog()
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Dialtone voicemail log", text))
                        Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            item(key = "log-clear") {
                SettingsRow(
                    Icons.Outlined.DeleteSweep, "Clear voicemail log", segmentShape(2, 3),
                    onClick = {
                        actions.clearLog()
                        Toast.makeText(context, "Log cleared", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            item(key = "version") {
                Text(
                    listOfNotNull("Dialtone", version).joinToString(" "),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
                )
            }
        }
    }

    }
    if (typingNumber) AddNumberDialog(onDismiss = { typingNumber = false })
    if (pickingContact) {
        val keys = numbers.orEmpty().map(::numberKey).toSet()
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
                pickingContact = false
            },
            onDismiss = { pickingContact = false },
        )
    }
    if (editingResponses) QuickResponsesEditor(onDismiss = { editingResponses = false })
    if (viewingLog) LogDialog(health, actions, onDismiss = { viewingLog = false })
}

/** A settings row in a grouped card, as in the Contacts app: icon, title, summary, trailing. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
    val modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = scheme.surfaceBright, modifier = modifier) { content() }
    } else {
        Surface(shape = shape, color = scheme.surfaceBright, modifier = modifier) { content() }
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
private fun AddNumberDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entry by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The four quick responses: tap one to edit it; blank restores its default. */
@Composable
private fun QuickResponsesEditor(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val responses by QuickResponses.responses(context).collectAsState()
    var editIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Quick responses", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Offered by \"Reply with message\" on an incoming call. Tap one to edit it.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            val list = responses.orEmpty()
            list.forEachIndexed { i, text ->
                Surface(
                    onClick = { editIndex = i },
                    shape = segmentShape(i, list.size),
                    color = scheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Edit, "Edit", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
            TextButton(onClick = { QuickResponses.resetAll(context) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Reset to defaults")
            }
        }
    }

    editIndex?.let { i ->
        var entry by rememberSaveable(i) { mutableStateOf(responses.orEmpty().getOrElse(i) { "" }) }
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("Quick response") },
            text = { OutlinedTextField(value = entry, onValueChange = { entry = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    QuickResponses.set(context, i, entry)
                    editIndex = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editIndex = null }) { Text("Cancel") } },
        )
    }
}

/** The voicemail log, read on open, with Copy. */
@Composable
private fun LogDialog(health: VvmHealth, actions: VoicemailActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(version) { text = withContext(Dispatchers.IO) { actions.readLog() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voicemail log") },
        text = {
            Column {
                Text(
                    "Status: ${health.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                ) {
                    Text(
                        text.ifBlank { "No log entries yet." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Dialtone voicemail log", text))
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
