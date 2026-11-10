package app.dialtone.phone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.rounded.Settings
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
import app.dialtone.phone.ui.contacts.contactItems
import app.dialtone.phone.ui.home.HomeScreen
import app.dialtone.phone.ui.contacts.ContactsScreen
import app.dialtone.phone.ui.keypad.KeypadTab
import app.dialtone.phone.ui.voicemail.VoicemailActions
import app.dialtone.phone.ui.voicemail.VoicemailScreen
import app.dialtone.phone.voicemail.VoicemailNotifier
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.numberKey
import app.dialtone.phone.data.PhoneNumber

// Matches the Contacts app: outlined icons, filled while the tab is selected.
private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    // The FAB's dial pad glyph; it's the same in every icon style.
    Keypad("Keypad", Icons.Rounded.Dialpad, Icons.Rounded.Dialpad),
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
    val transcription by vm.transcription.collectAsStateWithLifecycle()

    // Tabs are pages: swipe between them, or tap the bottom bar. The pager state is saveable,
    // so the open tab survives rotation. targetPage (not currentPage) so the bar highlights
    // where a swipe or tap is heading, without flickering through the middle tab.
    val pager = rememberPagerState(initialPage = Tab.Home.ordinal) { Tab.entries.size }
    val tab = Tab.entries[pager.targetPage]
    val scope = rememberCoroutineScope()
    var keypadText by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf<String?>(null) } // null = not searching
    var voicemailSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // The contacts list, opened from Home's Contacts button in Home's own space.
    var contactsOpen by rememberSaveable { mutableStateOf(false) }
    val showingContacts = contactsOpen && tab == Tab.Home

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(pendingNumber) {
        pendingNumber?.let {
            keypadText = it
            query = null
            pager.scrollToPage(Tab.Keypad.ordinal)
            onPendingConsumed()
        }
    }
    LaunchedEffect(openVoicemail) {
        if (openVoicemail) {
            pager.scrollToPage(Tab.Voicemail.ordinal)
            query = null
            onOpenVoicemailConsumed()
        }
    }
    // Seeing the inbox counts as seeing the "new voicemail" notification.
    LaunchedEffect(tab) {
        if (tab == Tab.Voicemail) VoicemailNotifier.cancelVisual(context)
    }
    BackHandler(enabled = query != null) { query = null }
    BackHandler(enabled = query == null && showingContacts) { contactsOpen = false }
    // Back from Keypad or Voicemail goes to Home; from Home it leaves the app as usual.
    BackHandler(enabled = query == null && tab != Tab.Home && !showingContacts) {
        scope.launch { pager.animateScrollToPage(Tab.Home.ordinal) }
    }

    val call: (String) -> Unit = { number ->
        Dialer.call(context, number)
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
            onToggleTranscription = vm::setTranscriptionEnabled,
            readLog = vm::voicemailLog,
            clearLog = vm::clearVoicemailLog,
        )
    }
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    // Keypad suggestions: contacts you call most (recent calls weigh more), then favorites.
    val suggested = remember(recents, contactIndex, favorites) {
        val now = System.currentTimeMillis()
        val scores = LinkedHashMap<Long, Pair<Double, PhoneNumber>>()
        val byId = HashMap<Long, Contact>()
        recents.orEmpty().forEach { call ->
            val contact = contactIndex[numberKey(call.number)] ?: return@forEach
            val days = ((now - call.date) / 86_400_000.0).coerceAtLeast(0.0)
            val weight = 1.0 / (1.0 + days / 7.0)
            val pn = contact.numbers.firstOrNull { numberKey(it.number) == numberKey(call.number) }
                ?: PhoneNumber(call.number, "Mobile")
            val old = scores[contact.id]
            scores[contact.id] = ((old?.first ?: 0.0) + weight) to (old?.second ?: pn)
            byId[contact.id] = contact
        }
        val frequent = scores.entries.sortedByDescending { it.value.first }
            .map { byId.getValue(it.key) to it.value.second }
        val extra = favorites.filter { it.id !in scores }.mapNotNull { c -> c.numbers.firstOrNull()?.let { c to it } }
        (frequent + extra).take(12)
    }
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
                    // On the contacts list, a back arrow returns to Home.
                    onBack = if (showingContacts) ({ contactsOpen = false }) else null,
                    // Voicemail settings live in the search bar while on the Voicemail tab.
                    trailing = if (tab == Tab.Voicemail) {
                        {
                            IconButton(onClick = { voicemailSettingsOpen = true }) {
                                Icon(Icons.Rounded.Settings, "Voicemail settings")
                            }
                        }
                    } else {
                        null
                    },
                )
            },
            bottomBar = {
                ContactsMatchBottomBar {
                    Tab.entries.forEach { t ->
                        val selected = tab == t && query == null
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = {
                                query = null
                                if (t == Tab.Home) contactsOpen = false
                                scope.launch { pager.animateScrollToPage(t.ordinal) }
                            },
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
        ) { padding ->
            val q = query
            if (q != null) {
                SearchResults(q, contacts, call, padding)
            } else {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    key = { Tab.entries[it] },
                ) { page ->
                    when (Tab.entries[page]) {
                        Tab.Home -> AnimatedContent(
                            targetState = contactsOpen,
                            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                            label = "home-contacts",
                        ) { open ->
                            if (open) {
                                ContactsScreen(contacts, call, vm::setFavorite, padding)
                            } else {
                                HomeScreen(
                                    favorites = favorites,
                                    recents = recents,
                                    contactIndex = contactIndex,
                                    contacts = contacts,
                                    onCall = call,
                                    onOpenContacts = { contactsOpen = true },
                                    onAddFavorite = vm::addFavorite,
                                    contentPadding = padding,
                                )
                            }
                        }
                        Tab.Keypad -> KeypadTab(
                            active = query == null && pager.currentPage == Tab.Keypad.ordinal,
                            number = keypadText,
                            onNumberChange = { keypadText = it },
                            contacts = contacts,
                            suggested = suggested,
                            lastDialed = lastDialed,
                            onCall = call,
                            contentPadding = padding,
                        )
                        Tab.Voicemail -> VoicemailScreen(
                            voicemails = voicemails,
                            contactIndex = contactIndex,
                            accounts = vvmAccounts,
                            health = vvmHealth,
                            transcription = transcription,
                            actions = voicemailActions,
                            contentPadding = padding,
                            showSettings = voicemailSettingsOpen,
                            onShowSettings = { voicemailSettingsOpen = true },
                            onDismissSettings = { voicemailSettingsOpen = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarPill(
    query: String?,
    onQueryChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    // Opaque strip in the app background colour (status bar included), so lists scrolling
    // under the search bar don't show through around the pill.
    Box(Modifier.fillMaxWidth().background(scheme.background).statusBarsPadding()) {
    Surface(
        onClick = { if (query == null) onActiveChange(true) },
        shape = CircleShape,
        color = scheme.surfaceContainerHighest,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (query == null) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Home") }
                    Spacer(Modifier.width(4.dp))
                } else {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "Search contacts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
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
