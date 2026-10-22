package app.dialtone.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Voicemail
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dialtone.phone.data.CallType
import app.dialtone.phone.data.Contact
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.components.ContactsMatchBottomBar
import app.dialtone.phone.ui.components.EmptyState
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.contacts.ContactsScreen
import app.dialtone.phone.ui.contacts.contactItems
import app.dialtone.phone.ui.home.HomeScreen
import app.dialtone.phone.ui.keypad.KeypadScreen
import app.dialtone.phone.ui.voicemail.VoicemailActions
import app.dialtone.phone.ui.voicemail.VoicemailScreen
import app.dialtone.phone.voicemail.VoicemailNotifier
import app.dialtone.phone.util.formatNumber

// Matches the Contacts app: outlined icons, filled while the tab is selected.
private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Contacts("Contacts", Icons.Outlined.Person, Icons.Filled.Person),
    Voicemail("Voicemail", Icons.Outlined.Voicemail, Icons.Filled.Voicemail),
}

/**
 * @param pendingNumber a number handed to us by a DIAL intent ("" opens an empty keypad).
 */
@Composable
fun DialerApp(
    pendingNumber: String?,
    onPendingConsumed: () -> Unit,
    openVoicemail: Boolean = false,
    onOpenVoicemailConsumed: () -> Unit = {},
    vm: DialerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val voicemails by vm.voicemails.collectAsStateWithLifecycle()
    val contactIndex by vm.contactIndex.collectAsStateWithLifecycle()
    val vvmAccounts by vm.vvmAccounts.collectAsStateWithLifecycle()
    val vvmHealth by vm.vvmHealth.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    var keypadText by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf<String?>(null) } // null = not searching

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(pendingNumber) {
        pendingNumber?.let {
            keypadText = it
            keypadOpen = true
            onPendingConsumed()
        }
    }
    LaunchedEffect(openVoicemail) {
        if (openVoicemail) {
            tab = Tab.Voicemail
            keypadOpen = false
            query = null
            onOpenVoicemailConsumed()
        }
    }
    // Seeing the inbox counts as seeing the "new voicemail" notification.
    LaunchedEffect(tab) {
        if (tab == Tab.Voicemail) VoicemailNotifier.cancelVisual(context)
    }
    BackHandler(enabled = keypadOpen) { keypadOpen = false }
    BackHandler(enabled = !keypadOpen && query != null) { query = null }

    val call: (String) -> Unit = { number ->
        Dialer.call(context, number)
        keypadOpen = false
        keypadText = ""
    }
    val voicemailActions = remember(vm) {
        VoicemailActions(
            onCall = { number -> Dialer.call(context, number) },
            onMarkRead = vm::markRead,
            onDelete = vm::delete,
            onRequestAudio = vm::requestVoicemailAudio,
            onToggleVisualVoicemail = vm::setVisualVoicemailEnabled,
            onSyncNow = vm::syncVoicemail,
            readLog = vm::voicemailLog,
            clearLog = vm::clearVoicemailLog,
        )
    }
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    val unread = voicemails?.count { !it.isRead } ?: 0
    val lastDialed = recents?.firstOrNull { it.type == CallType.Outgoing }?.number

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                SearchBarPill(
                    query = query,
                    onQueryChange = { query = it },
                    onActiveChange = { active -> query = if (active) "" else null },
                )
            },
            bottomBar = {
                ContactsMatchBottomBar {
                    Tab.entries.forEach { t ->
                        val selected = tab == t && query == null
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { tab = t; query = null },
                            icon = {
                                val icon = if (selected) t.selectedIcon else t.icon
                                if (t == Tab.Voicemail && unread > 0) {
                                    BadgedBox(badge = { Badge { Text("$unread") } }) { Icon(icon, contentDescription = t.label) }
                                } else {
                                    Icon(icon, contentDescription = t.label)
                                }
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            floatingActionButton = {
                // Standard 56dp FAB with default shape and elevation, as in Contacts.
                FloatingActionButton(
                    onClick = { keypadOpen = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Rounded.Dialpad, contentDescription = "Open keypad")
                }
            },
        ) { padding ->
            val q = query
            if (q != null) {
                SearchResults(q, contacts, call, padding)
            } else {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    label = "tabs",
                ) { t ->
                    when (t) {
                        Tab.Home -> HomeScreen(favorites, recents, contactIndex, call, padding)
                        Tab.Contacts -> ContactsScreen(contacts, call, padding)
                        Tab.Voicemail -> VoicemailScreen(
                            voicemails = voicemails,
                            contactIndex = contactIndex,
                            accounts = vvmAccounts,
                            health = vvmHealth,
                            actions = voicemailActions,
                            contentPadding = padding,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = keypadOpen,
            enter = slideInVertically(spatial) { it } + fadeIn(effects),
            exit = slideOutVertically(spatial) { it } + fadeOut(effects),
        ) {
            KeypadScreen(
                number = keypadText,
                onNumberChange = { keypadText = it },
                contacts = contacts,
                lastDialed = lastDialed,
                onCall = call,
                onClose = { keypadOpen = false },
            )
        }
    }
}

@Composable
private fun SearchBarPill(
    query: String?,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    Surface(
        onClick = { if (query == null) onActiveChange(true) },
        shape = CircleShape,
        color = scheme.surfaceContainerHighest,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (query == null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Search contacts", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            } else {
                IconButton(onClick = { onActiveChange(false) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search")
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text("Search contacts or type a number", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
                }
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    padding: PaddingValues,
) {
    val expanded = rememberSaveable { mutableStateOf<Long?>(null) }
    val digits = query.filter { it.isDigit() }
    val results = remember(query, contacts) {
        if (query.isBlank()) emptyList()
        else contacts.filter { c ->
            c.name.contains(query, ignoreCase = true) ||
                (digits.length >= 2 && c.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (digits.length >= 3 && digits.length == query.count { !it.isWhitespace() && it !in "()-+" }) {
            item(key = "call-raw") {
                Surface(
                    onClick = { onCall(query) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Call, null)
                        Spacer(Modifier.width(16.dp))
                        Text("Call ${formatNumber(query)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item(key = "none") {
                EmptyState(Icons.Rounded.Search, "No matches", "No contacts match \"$query\".")
            }
        }
        if (results.isNotEmpty()) {
            item(key = "h") { SectionHeader("Contacts") }
            contactItems(results, expanded, onCall)
        }
    }
}
