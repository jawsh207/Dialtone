package app.dialtone.phone.ui.keypad

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.PhoneNumber
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.components.Avatar
import app.dialtone.phone.ui.components.DialPad
import app.dialtone.phone.ui.components.EmptyState
import app.dialtone.phone.ui.components.MorphButton
import app.dialtone.phone.ui.components.SectionHeader
import app.dialtone.phone.ui.components.T9
import app.dialtone.phone.ui.components.rememberDtmfTones
import app.dialtone.phone.ui.components.segmentShape
import app.dialtone.phone.ui.theme.CallColors
import app.dialtone.phone.util.formatNumber

/** Contacts whose number contains the digits, or whose name spells them in T9. */
private fun matchContacts(contacts: List<Contact>, query: String): List<Pair<Contact, PhoneNumber>> {
    val digits = query.filter { it.isDigit() }
    if (digits.length < 2) return emptyList()
    return contacts.asSequence().mapNotNull { c ->
        val byNumber = c.numbers.firstOrNull { it.number.filter(Char::isDigit).contains(digits) }
        val byName = c.name.lowercase().split(' ').any { word ->
            word.map { T9[it] ?: ' ' }.joinToString("").startsWith(digits)
        }
        when {
            byNumber != null -> c to byNumber
            byName -> c.numbers.firstOrNull()?.let { c to it }
            else -> null
        }
    }.take(20).toList()
}

/**
 * The Keypad tab: the dial pad slides up from the bottom when the tab opens and stays open,
 * over a scrollable list of suggested contacts (people you call most, then favorites).
 * While typing, the list shows contacts matching the digits instead.
 *
 * @param active the tab is on screen (not mid-swipe elsewhere, not searching).
 */
@Composable
fun KeypadTab(
    active: Boolean,
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    suggested: List<Pair<Contact, PhoneNumber>>,
    lastDialed: String?,
    onCall: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var padHeightPx by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val matches = remember(number, contacts) { matchContacts(contacts, number) }
    val typing = number.filter { it.isDigit() }.length >= 2
    val rows = if (typing) matches else suggested
    val padVisible = active
    val padSpace = if (padVisible) with(density) { padHeightPx.toDp() } else 0.dp

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + padSpace + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { SectionHeader(if (typing) "Contacts" else "Suggested") }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.PersonSearch,
                        if (typing) "No matching contacts" else "No suggestions yet",
                        if (typing) "Press call to dial the number." else "People you call often show up here.",
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            itemsIndexed(rows, key = { _, (c, pn) -> "${c.id}-${pn.number}" }) { i, (contact, pn) ->
                Surface(
                    onClick = { onCall(pn.number) },
                    shape = segmentShape(i, rows.size),
                    color = scheme.surfaceBright,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateItem(),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(contact.name, contact.photoUri, contact.id, 48.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pn.label} • ${formatNumber(pn.number)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = padVisible,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            DialPanel(
                number = number,
                onNumberChange = onNumberChange,
                lastDialed = lastDialed,
                onCall = onCall,
                modifier = Modifier.onSizeChanged { padHeightPx = it.height },
            )
        }
    }
}

@Composable
private fun DialPanel(
    number: String,
    onNumberChange: (String) -> Unit,
    lastDialed: String?,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val tones = rememberDtmfTones()
    val scheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = scheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            BasicText(
                text = formatNumber(number),
                style = MaterialTheme.typography.displaySmall.copy(color = scheme.onSurface, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp).height(48.dp),
            )

            DialPad(
                keyHeight = 60.dp,
                onKey = { c ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    tones.play(c)
                    onNumberChange(number + c)
                },
                onLongKey = { c ->
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    when {
                        c == '0' -> onNumberChange(number + '+')
                        c == '1' && number.isEmpty() -> Dialer.callVoicemail(context)
                        else -> onNumberChange(number + c)
                    }
                },
            )

            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                MorphButton(
                    onClick = {
                        if (number.isBlank()) lastDialed?.let(onNumberChange) else onCall(number)
                    },
                    container = CallColors.container(),
                    content = CallColors.content(),
                    modifier = Modifier.align(Alignment.Center).width(120.dp).height(68.dp),
                ) { Icon(Icons.Rounded.Call, "Call", Modifier.size(30.dp)) }

                if (number.isNotEmpty()) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = null,
                                indication = ripple(),
                                onLongClick = { onNumberChange("") },
                                onClick = { onNumberChange(number.dropLast(1)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
