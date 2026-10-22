package app.dialtone.phone.ui.keypad

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import app.dialtone.phone.ui.components.MorphButton
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
    }.take(3).toList()
}

@Composable
fun KeypadScreen(
    number: String,
    onNumberChange: (String) -> Unit,
    contacts: List<Contact>,
    lastDialed: String?,
    onCall: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val tones = rememberDtmfTones()
    val matches = remember(number, contacts) { matchContacts(contacts, number) }
    val scheme = MaterialTheme.colorScheme

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close keypad") }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
            ) {
                matches.forEachIndexed { i, (contact, pn) ->
                    Surface(
                        onClick = { onCall(pn.number) },
                        shape = segmentShape(i, matches.size),
                        color = scheme.surfaceBright,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(contact.name, contact.photoUri, contact.id, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(contact.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${pn.label} • ${formatNumber(pn.number)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            BasicText(
                text = formatNumber(number),
                style = MaterialTheme.typography.displayMedium.copy(color = scheme.onSurface, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 44.sp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).height(56.dp),
            )

            DialPad(
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

            Box(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)) {
                MorphButton(
                    onClick = {
                        if (number.isBlank()) lastDialed?.let(onNumberChange) else onCall(number)
                    },
                    container = CallColors.container(),
                    content = CallColors.content(),
                    modifier = Modifier.align(Alignment.Center).width(120.dp).height(76.dp),
                ) { Icon(Icons.Rounded.Call, "Call", Modifier.size(32.dp)) }

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
