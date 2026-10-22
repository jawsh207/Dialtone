package app.dialtone.ui.contacts

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
import app.dialtone.data.Contact
import app.dialtone.telecom.Dialer
import app.dialtone.ui.components.Avatar
import app.dialtone.ui.components.EmptyState
import app.dialtone.ui.components.SectionHeader
import app.dialtone.ui.components.segmentShape
import app.dialtone.util.formatNumber

@Composable
fun ContactsScreen(contacts: List<Contact>, onCall: (String) -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val expanded = rememberSaveable { mutableStateOf<Long?>(null) }
    val grouped = remember(contacts) {
        contacts.groupBy { c -> c.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#' }
            .toSortedMap(compareBy<Char> { if (it == '#') Char.MAX_VALUE else it })
    }
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 104.dp,
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
        grouped.forEach { (letter, list) ->
            item(key = "h-$letter") { SectionHeader(letter.toString()) }
            contactItems(list, expanded, onCall)
        }
    }
}

fun LazyListScope.contactItems(
    list: List<Contact>,
    expanded: MutableState<Long?>,
    onCall: (String) -> Unit,
) {
    itemsIndexed(list, key = { _, c -> c.id }) { i, contact ->
        ContactRow(
            contact = contact,
            expanded = expanded.value == contact.id,
            onToggle = { expanded.value = if (expanded.value == contact.id) null else contact.id },
            onCall = onCall,
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
                Avatar(contact.name, contact.photoUri, contact.id, 44.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (contact.starred) Icon(Icons.Rounded.Star, "Favorite", Modifier.size(18.dp), tint = scheme.primary)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 76.dp, end = 12.dp, bottom = 10.dp)) {
                    contact.numbers.forEach { n ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatNumber(n.number), style = MaterialTheme.typography.bodyMedium)
                                Text(n.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { Dialer.sms(context, n.number) }) {
                                Icon(Icons.AutoMirrored.Rounded.Message, "Message")
                            }
                            FilledTonalIconButton(
                                onClick = { onCall(n.number) },
                                shape = RoundedCornerShape(14.dp),
                            ) { Icon(Icons.Rounded.Call, "Call") }
                        }
                    }
                }
            }
        }
    }
}
