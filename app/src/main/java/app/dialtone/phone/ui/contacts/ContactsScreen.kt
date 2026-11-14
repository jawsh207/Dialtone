package app.dialtone.phone.ui.contacts

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Call
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.runtime.collectAsState
import app.dialtone.phone.screening.SendToVoicemail
import app.dialtone.phone.util.numberKey
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.setValue
import app.dialtone.phone.util.hasPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dialtone.phone.data.Contact
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.components.Avatar
import app.dialtone.phone.ui.components.EmptyState
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.components.segmentShape
import app.dialtone.phone.util.formatNumber

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onCall: (String) -> Unit,
    onSetFavorite: (contactId: Long, favorite: Boolean) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val expanded = rememberSaveable { mutableStateOf<Long?>(null) }
    val voicemailNumbers by SendToVoicemail.numbers(context).collectAsState()
    val voicemailKeys = remember(voicemailNumbers) { voicemailNumbers.orEmpty().map(::numberKey).toSet() }
    val setVoicemail: (Contact, Boolean) -> Unit = { contact, send ->
        val numbers = contact.numbers.map { it.number }
        if (send) SendToVoicemail.add(context, numbers) else SendToVoicemail.remove(context, numbers)
    }
    // Starring needs WRITE_CONTACTS (same group as READ_CONTACTS, so usually no prompt).
    var pending by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { (id, fav) -> if (granted) onSetFavorite(id, fav) }
        pending = null
    }
    val setFavorite: (Long, Boolean) -> Unit = { id, fav ->
        if (context.hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            onSetFavorite(id, fav)
        } else {
            pending = id to fav
            writePermission.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }
    // Favorites first, as in the Contacts app; they're also listed under their letter below.
    val favorites = remember(contacts) { contacts.filter { it.starred } }
    val expandedFavorite = rememberSaveable { mutableStateOf<Long?>(null) }
    val grouped = remember(contacts) {
        contacts.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }
            .toSortedMap(compareBy<Char> { if (it == '#') Char.MAX_VALUE else it })
    }
    // Where each letter's header sits in the list, for the A–Z strip.
    val headerIndex = remember(grouped, favorites, contacts.isEmpty()) {
        var index = 1 + (if (contacts.isEmpty()) 1 else 0) + (if (favorites.isNotEmpty()) 1 + favorites.size else 0)
        grouped.entries.associate { (letter, members) -> (letter.toString() to index).also { index += 1 + members.size } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "new") {
            Surface(
                onClick = { Dialer.newContact(context) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PersonAdd, null)
                    Spacer(Modifier.width(16.dp))
                    Text("Create new contact", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (contacts.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.Rounded.Contacts, "No contacts", "Contacts with phone numbers show up here.")
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "h-favorites") {
                Row(
                    Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Favorites", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            contactItems(favorites, expandedFavorite, onCall, setFavorite, keyPrefix = "fav-", voicemailKeys = voicemailKeys, onSetSendToVoicemail = setVoicemail)
        }
        grouped.forEach { (letter, list) ->
            item(key = "h-$letter") { SectionHeader(letter.toString()) }
            contactItems(list, expanded, onCall, setFavorite, voicemailKeys = voicemailKeys, onSetSendToVoicemail = setVoicemail)
        }
    }
    if (grouped.size > 1) {
        LetterScroller(
            letters = grouped.keys.map { it.toString() },
            onLetter = { letter -> headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
        )
    }
    }
}

/**
 * The A–Z strip, as in the Contacts app: tap or drag along it to jump to a letter. While
 * dragging, the letter shows large beside it.
 */
@Composable
private fun LetterScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(0) }
    fun pick(y: Float) {
        if (height == 0) return
        val letter = letters[(y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != active) {
            active = letter
            onLetter(letter)
        }
    }
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        Column(
            // The letters sit in the list's right margin; the touch area reaches a little past it.
            Modifier
                .width(24.dp)
                .onSizeChanged { height = it.height }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y) }
                }
                .pointerInput(letters) {
                    detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); active = null })
                },
            horizontalAlignment = Alignment.End,
        ) {
            letters.forEach { letter ->
                Box(Modifier.width(16.dp).height(LETTER_HEIGHT), contentAlignment = Alignment.Center) {
                    Text(
                        letter,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                        color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        active?.let { letter ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 56.dp).size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private val LETTER_HEIGHT = 18.dp

fun LazyListScope.contactItems(
    list: List<Contact>,
    expanded: MutableState<Long?>,
    onCall: (String) -> Unit,
    /** Adds/removes Favorites from the expanded row; null hides the option (e.g. search). */
    onSetFavorite: ((contactId: Long, favorite: Boolean) -> Unit)? = null,
    /** Distinguishes a contact listed twice (Favorites and its letter section). */
    keyPrefix: String = "",
    /** numberKeys on the "Send to voicemail" list. */
    voicemailKeys: Set<String> = emptySet(),
    /** Sends all the contact's numbers to voicemail (or stops); null hides the option. */
    onSetSendToVoicemail: ((Contact, Boolean) -> Unit)? = null,
) {
    itemsIndexed(list, key = { _, c -> "$keyPrefix${c.id}" }) { i, contact ->
        ContactRow(
            contact = contact,
            expanded = expanded.value == contact.id,
            onToggle = { expanded.value = if (expanded.value == contact.id) null else contact.id },
            onCall = onCall,
            onSetFavorite = onSetFavorite,
            sentToVoicemail = contact.numbers.any { numberKey(it.number) in voicemailKeys },
            onSetSendToVoicemail = onSetSendToVoicemail?.let { f -> { send: Boolean -> f(contact, send) } },
            shape = segmentShape(i, list.size),
            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCall: (String) -> Unit,
    onSetFavorite: ((Long, Boolean) -> Unit)?,
    sentToVoicemail: Boolean,
    onSetSendToVoicemail: ((Boolean) -> Unit)?,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        scheme.surfaceBright, // cards: surfaceBright, as in Contacts
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "contact",
    )
    Surface(onClick = onToggle, shape = shape, color = container, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(contact.name, contact.photoUri, contact.id, 48.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 76.dp, end = 12.dp, bottom = 10.dp)) {
                    contact.numbers.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatNumber(n.number), style = MaterialTheme.typography.bodyMedium)
                                Text(n.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            }
                            // Round tonal buttons, like the call button in the call log: call, then text.
                            FilledTonalIconButton(onClick = { onCall(n.number) }) {
                                Icon(Icons.Outlined.Call, "Call")
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalIconButton(onClick = { Dialer.sms(context, n.number) }) {
                                Icon(Icons.Outlined.ChatBubbleOutline, "Text")
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                    if (onSetFavorite != null) {
                        AssistChip(
                            onClick = { onSetFavorite(contact.id, !contact.starred) },
                            label = { Text(if (contact.starred) "Remove from Favorites" else "Add to Favorites") },
                            leadingIcon = {
                                Icon(
                                    if (contact.starred) Icons.Rounded.StarOutline else Icons.Rounded.Star,
                                    null,
                                    Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                    if (onSetSendToVoicemail != null) {
                        AssistChip(
                            onClick = { onSetSendToVoicemail(!sentToVoicemail) },
                            label = { Text(if (sentToVoicemail) "Stop sending to voicemail" else "Send to voicemail") },
                            leadingIcon = { Icon(Icons.Rounded.Voicemail, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                    }
                }
            }
        }
    }
}
