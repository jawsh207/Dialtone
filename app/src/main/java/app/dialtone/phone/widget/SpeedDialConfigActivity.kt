package app.dialtone.phone.widget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.ContactsRepository
import app.dialtone.phone.data.PhoneNumber
import app.dialtone.phone.ui.components.ContactPickerSheet
import app.dialtone.phone.ui.theme.ContactsMatchTheme
import app.dialtone.phone.util.formatNumber
import app.dialtone.phone.util.hasPermission
import kotlinx.coroutines.launch

/**
 * Picks who a Speed dial widget calls: the launcher opens this when the widget is placed (and to
 * reconfigure it), and so does tapping a widget that was never set up.
 *
 * It's the same contact picker as Home's "+ Add" favorite, shown over the home screen; contacts
 * with several numbers then ask which one. The launcher has to be able to open it, so it is
 * exported, but it only ever changes Dialtone's own Speed dial widgets: any other widget id is
 * refused.
 */
class SpeedDialConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves the widget unplaced (the launcher removes it on RESULT_CANCELED).
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (!isOurWidget(appWidgetId)) {
            finish()
            return
        }

        setContent {
            ContactsMatchTheme {
                val scope = rememberCoroutineScope()
                var contacts by remember { mutableStateOf<List<Contact>?>(null) }
                var granted by remember { mutableStateOf(hasPermission(Manifest.permission.READ_CONTACTS)) }
                var choosingNumber by remember { mutableStateOf<Contact?>(null) }
                val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    granted = it
                    if (!it) contacts = emptyList()
                }
                LaunchedEffect(granted) {
                    if (granted) contacts = ContactsRepository(this@SpeedDialConfigActivity).load()
                    else permission.launch(Manifest.permission.READ_CONTACTS)
                }

                val list = contacts
                val picking = choosingNumber
                if (picking != null) {
                    AlertDialog(
                        onDismissRequest = { choosingNumber = null },
                        title = { Text("Call ${picking.name} on") },
                        text = {
                            Column {
                                picking.numbers.forEach { n ->
                                    TextButton(onClick = { scope.launch { save(picking, n) } }) {
                                        Text("${n.label}  ${formatNumber(n.number)}")
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { choosingNumber = null }) { Text("Back") } },
                    )
                } else if (list != null) {
                    ContactPickerSheet(
                        title = "Speed dial",
                        contacts = list.filter { it.numbers.isNotEmpty() },
                        emptyText = if (granted) "No contacts with phone numbers." else "Dialtone needs access to your contacts.",
                        onPick = { c ->
                            if (c.numbers.size == 1) scope.launch { save(c, c.numbers.first()) } else choosingNumber = c
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private suspend fun save(contact: Contact, number: PhoneNumber) {
        SpeedDialStore.put(
            this, appWidgetId,
            SpeedDialStore.Entry(contact.id, contact.lookupKey, contact.name, number.number),
        )
        // Draw it before handing back to the launcher, so the widget appears already filled in.
        Widgets.refreshAll(this)
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun isOurWidget(id: Int): Boolean {
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: return false
        return info.provider.packageName == packageName &&
            info.provider.className == SpeedDialWidgetReceiver::class.java.name
    }
}
