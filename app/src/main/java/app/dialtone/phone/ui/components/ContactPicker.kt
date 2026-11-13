package app.dialtone.phone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dialtone.phone.data.Contact

/** Searchable list of contacts in a bottom sheet; tapping one picks it. */
@Composable
fun ContactPickerSheet(
    title: String,
    contacts: List<Contact>,
    emptyText: String,
    onPick: (Contact) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, filter) {
        val q = filter.trim()
        if (q.isEmpty()) contacts else contacts.filter { it.name.contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = scheme.surfaceContainerLow,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        )
        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHighest,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (filter.isEmpty()) Text("Search contacts", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    },
                )
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (contacts.isEmpty()) emptyText else "No matching contacts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            itemsIndexed(shown, key = { _, c -> c.id }) { i, contact ->
                Surface(
                    onClick = { onPick(contact) },
                    shape = segmentShape(i, shown.size),
                    color = scheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact.name, contact.photoUri, contact.id, 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
