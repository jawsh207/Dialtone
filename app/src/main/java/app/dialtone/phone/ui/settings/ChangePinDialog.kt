package app.dialtone.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import app.dialtone.phone.data.VvmAccount
import app.dialtone.phone.ui.voicemail.VoicemailActions
import app.dialtone.phone.voicemail.VoicemailPin
import app.dialtone.phone.vvm.DialtoneVoicemail
import app.dialtone.phone.vvm.PinChanger
import kotlinx.coroutines.launch

/**
 * Change voicemail PIN: the current PIN, the new one twice, then a request to the carrier.
 *
 * The request goes over mobile data, like a sync, and can take a few seconds. When Dialtone itself
 * set a random PIN while activating a new VVM3 mailbox (the engine does this so the carrier's
 * guessable default isn't left in place), nobody else knows it, so it's filled in here and the
 * field is hidden: the user just chooses their own. PINs are never kept in state that survives
 * the dialog, and never logged.
 */
@Composable
fun ChangePinDialog(account: VvmAccount, actions: VoicemailActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<DialtoneVoicemail.PinInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(account.id) {
        info = actions.pinInfo(account)
        loaded = true
    }
    val scrambled = info?.scrambledPin
    val min = info?.minLength ?: 0
    val max = info?.maxLength ?: 0

    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentPin = scrambled ?: current
    val problem = VoicemailPin.problem(currentPin, newPin, confirm, min, max)
    val digitsOnly: (String) -> String = { v -> v.filter { it.isDigit() }.take(if (max > 0) max else 20) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Change voicemail PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (scrambled != null) {
                        "Dialtone set a temporary PIN when it set up your voicemail. Choose your own " +
                            "so you can also dial in to listen."
                    } else {
                        "The PIN you use when you call your voicemail. Changing it needs mobile data."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!loaded) {
                    LoadingIndicator()
                } else {
                    if (scrambled == null) {
                        PinField("Current PIN", current, enabled = !working) { current = digitsOnly(it); error = null }
                    }
                    PinField(
                        "New PIN", newPin, enabled = !working,
                        supporting = VoicemailPin.lengthHint(min, max),
                    ) { newPin = digitsOnly(it); error = null }
                    PinField("Confirm new PIN", confirm, enabled = !working) { confirm = digitsOnly(it); error = null }
                    val shown = error ?: problem.takeIf { newPin.isNotEmpty() && confirm.length >= newPin.length }
                    if (shown != null) {
                        Text(shown, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (working) LoadingIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = loaded && !working && problem == null,
                onClick = {
                    working = true
                    error = null
                    scope.launch {
                        val result = actions.changePin(account, currentPin, newPin)
                        working = false
                        if (result == PinChanger.CHANGE_PIN_SUCCESS) {
                            Toast.makeText(context, VoicemailPin.resultMessage(result, min, max), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            error = VoicemailPin.resultMessage(result, min, max)
                        }
                    }
                },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(label: String, value: String, enabled: Boolean, supporting: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
