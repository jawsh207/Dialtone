package app.dialtone.phone.ui.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalView
import app.dialtone.phone.screening.SendToVoicemail
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.SolidColor
import app.dialtone.phone.util.hasPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dialtone.phone.data.CallType
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.RecentCall
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.components.Avatar
import app.dialtone.phone.ui.components.EmptyState
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.components.ContactPickerSheet
import app.dialtone.phone.ui.components.segmentShape
import app.dialtone.phone.util.DayBucket
import app.dialtone.phone.util.dayBucket
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.formatShortDate
import app.dialtone.phone.util.numberKey

/** Call log filters shown above Favorites. */
private enum class CallFilter(val label: String, val empty: String) {
    All("All", "No recent calls"),
    Missed("Missed", "No missed calls"),
    Dialed("Dialed", "No dialed calls"),
    Contacts("Contacts", "No calls with your contacts"),
    Unknown("Unknown", "No calls from unsaved numbers"),
}

private fun CallFilter.matches(call: RecentCall, contact: Contact?): Boolean = when (this) {
    CallFilter.All -> true
    CallFilter.Missed -> call.type == CallType.Missed
    CallFilter.Dialed -> call.type == CallType.Outgoing
    CallFilter.Contacts -> contact != null
    CallFilter.Unknown -> contact == null
}

@Composable
fun HomeScreen(
    favorites: List<Contact>,
    recents: List<RecentCall>?,
    contactIndex: Map<String, Contact>,
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onAddFavorite: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var pickingFavorite by rememberSaveable { mutableStateOf(false) }
    val voicemailNumbers by SendToVoicemail.numbers(context).collectAsState()
    val voicemailKeys = remember(voicemailNumbers) { voicemailNumbers.orEmpty().map(::numberKey).toSet() }
    // Starring a contact needs WRITE_CONTACTS (same group as READ_CONTACTS, so usually no prompt).
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickingFavorite = true
    }
    val addFavorite = {
        if (context.hasPermission(Manifest.permission.WRITE_CONTACTS)) pickingFavorite = true
        else writePermission.launch(Manifest.permission.WRITE_CONTACTS)
    }
    val prefs = remember { context.getSharedPreferences("home", Context.MODE_PRIVATE) }
    // Remembered across launches: hidden favorites stay hidden.
    var favoritesShown by remember { mutableStateOf(!prefs.getBoolean("favorites_collapsed", false)) }
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(CallFilter.All) }

    if (recents == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }
    val shown = remember(recents, contactIndex, filter) {
        recents.filter { filter.matches(it, contactIndex[numberKey(it.number)]) }
    }
    val grouped = remember(shown) { shown.groupBy { dayBucket(it.date) } }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Filter buttons for the call log below (single choice; All is the plain log).
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            ) {
                items(CallFilter.entries, key = { it.name }) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = {
                            filter = f
                            expandedId = null
                        },
                        label = { Text(f.label) },
                    )
                }
            }
        }

        // "Favorites ⌄" (show/hide) on the left, Contacts on the right.
        item(key = "fav-header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                run {
                    val rotation by animateFloatAsState(if (favoritesShown) 0f else -90f, label = "chevron")
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable {
                                favoritesShown = !favoritesShown
                                prefs.edit().putBoolean("favorites_collapsed", !favoritesShown).apply()
                            }
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Favorites", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (favoritesShown) "Hide favorites" else "Show favorites",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).rotate(rotation),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onOpenContacts,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Rounded.Person, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Contacts")
                }
            }
        }
        if (favoritesShown) {
            item(key = "fav-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(favorites, key = { it.id }) { contact ->
                        FavoriteTile(contact) { contact.numbers.firstOrNull()?.let { onCall(it.number) } }
                    }
                    // Always last, after the favorites (or alone when there are none).
                    item(key = "add") { AddFavoriteTile(onClick = addFavorite) }
                }
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Rounded.History,
                    filter.empty,
                    if (filter == CallFilter.All) "Calls you make and receive will show up here."
                    else "Tap All to see every call.",
                    Modifier.padding(top = 48.dp),
                )
            }
        }

        DayBucket.entries.forEach { bucket ->
            val calls = grouped[bucket] ?: return@forEach
            item(key = "h-${bucket.name}") { SectionHeader(bucket.label) }
            itemsIndexed(calls, key = { _, c -> c.id }) { i, call ->
                RecentRow(
                    call = call,
                    contact = contactIndex[numberKey(call.number)],
                    expanded = expandedId == call.id,
                    onToggle = { expandedId = if (expandedId == call.id) null else call.id },
                    onCall = onCall,
                    sentToVoicemail = numberKey(call.number) in voicemailKeys,
                    onSetSendToVoicemail = { send ->
                        if (send) SendToVoicemail.add(context, call.number) else SendToVoicemail.remove(context, call.number)
                    },
                    shape = segmentShape(i, calls.size),
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
    }
    if (pickingFavorite) {
        ContactPickerSheet(
            title = "Add to Favorites",
            contacts = contacts.filter { !it.starred },
            emptyText = "All your contacts are already favorites.",
            onPick = { contact ->
                onAddFavorite(contact.id)
                pickingFavorite = false
            },
            onDismiss = { pickingFavorite = false },
        )
    }
}

/** Last tile in the Favorites row: opens the contact picker. */
@Composable
private fun AddFavoriteTile(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add favorite", tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Add", style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FavoriteTile(contact: Contact, onClick: () -> Unit) {
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(contact.name, contact.photoUri, contact.id, size = 68.dp, expressive = true)
        Spacer(Modifier.height(8.dp))
        Text(
            contact.name.substringBefore(' '),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun CallType.icon(): ImageVector = when (this) {
    CallType.Incoming -> Icons.AutoMirrored.Rounded.CallReceived
    CallType.Outgoing -> Icons.AutoMirrored.Rounded.CallMade
    CallType.Missed, CallType.Rejected -> Icons.AutoMirrored.Rounded.CallMissed
    CallType.Blocked -> Icons.Rounded.Block
    CallType.Voicemail -> Icons.Rounded.Voicemail
}

@Composable
private fun RecentRow(
    call: RecentCall,
    contact: Contact?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCall: (String) -> Unit,
    /** Number is on the "Send to voicemail" list; long press offers to change that. */
    sentToVoicemail: Boolean,
    onSetSendToVoicemail: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val missed = call.type == CallType.Missed || call.type == CallType.Rejected
    val name = contact?.name ?: call.cachedName?.takeIf { it.isNotBlank() }
    val display = name ?: if (call.number.isBlank()) "Private number" else formatNumber(call.number)
    val label = contact?.numbers?.firstOrNull { numberKey(it.number) == numberKey(call.number) }?.label
    val subtitle = listOfNotNull(label, formatShortDate(context, call.date)).joinToString(" • ")
    val container by animateColorAsState(
        scheme.surfaceBright, // cards: surfaceBright, as in Contacts
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "row",
    )

    Box(modifier.fillMaxWidth()) {
    Surface(
        shape = shape,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = if (call.number.isBlank()) null else ({
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    menu = true
                }),
            ),
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(name, contact?.photoUri ?: call.photoUri, numberKey(call.number).hashCode().toLong(), 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (call.count > 1) "$display (${call.count})" else display,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (missed) scheme.error else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            call.type.icon(), null, Modifier.size(16.dp),
                            tint = if (missed) scheme.error else scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (call.number.isNotBlank()) {
                    FilledTonalIconButton(onClick = { onCall(call.number) }) {
                        Icon(Icons.Rounded.Call, "Call $display")
                    }
                }
            }
            AnimatedVisibility(expanded && call.number.isNotBlank()) {
                Row(
                    Modifier.padding(start = 80.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { Dialer.sms(context, call.number) },
                        label = { Text("Message") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Message, null, Modifier.size(18.dp)) },
                    )
                    if (contact == null) {
                        AssistChip(
                            onClick = { Dialer.addContact(context, call.number) },
                            label = { Text("Add") },
                            leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)) },
                        )
                    }
                    AssistChip(
                        onClick = { Dialer.copy(context, call.number) },
                        label = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text(if (sentToVoicemail) "Stop sending to voicemail" else "Send to voicemail") },
            leadingIcon = { Icon(Icons.Rounded.Voicemail, null) },
            onClick = {
                menu = false
                onSetSendToVoicemail(!sentToVoicemail)
            },
        )
    }
    }
}
