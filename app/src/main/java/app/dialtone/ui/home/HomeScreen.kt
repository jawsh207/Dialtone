package app.dialtone.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.AssistChip
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
import app.dialtone.data.CallType
import app.dialtone.data.Contact
import app.dialtone.data.RecentCall
import app.dialtone.telecom.Dialer
import app.dialtone.ui.components.Avatar
import app.dialtone.ui.components.EmptyState
import app.dialtone.ui.components.SectionHeader
import app.dialtone.ui.components.segmentShape
import app.dialtone.util.DayBucket
import app.dialtone.util.dayBucket
import app.dialtone.util.formatNumber
import app.dialtone.util.formatShortDate
import app.dialtone.util.numberKey

@Composable
fun HomeScreen(
    favorites: List<Contact>,
    recents: List<RecentCall>?,
    contactIndex: Map<String, Contact>,
    onCall: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }

    if (recents == null) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        return
    }
    val grouped = remember(recents) { recents.groupBy { dayBucket(it.date) } }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (favorites.isNotEmpty()) {
            item(key = "fav-header") { SectionHeader("Favorites") }
            item(key = "fav-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(favorites, key = { it.id }) { contact ->
                        FavoriteTile(contact) { contact.numbers.firstOrNull()?.let { onCall(it.number) } }
                    }
                }
            }
        }

        if (recents.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Rounded.History,
                    "No recent calls",
                    "Calls you make and receive will show up here.",
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
                    shape = segmentShape(i, calls.size),
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
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
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
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

    Surface(onClick = onToggle, shape = shape, color = container, modifier = modifier.fillMaxWidth()) {
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
}
