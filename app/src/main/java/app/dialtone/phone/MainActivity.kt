package app.dialtone.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.telecom.TelecomManager
import app.dialtone.phone.telecom.MissedCalls
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dialtone.phone.telecom.Dialer
import app.dialtone.phone.ui.DialerApp
import app.dialtone.phone.ui.onboarding.SetDefaultScreen
import app.dialtone.phone.ui.theme.ContactsMatchTheme
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.voicemail.VoicemailNotifier
import app.dialtone.phone.vvm.DialtoneVoicemail
import app.dialtone.phone.widget.WidgetUi
import app.dialtone.phone.widget.Widgets

/**
 * Dialtone's only full screen: the tabs (Home, Keypad, Voicemail) and Settings, or the "set as
 * default" screen until Dialtone is the phone app.
 *
 * Everything that opens Dialtone arrives here as an intent: the launcher, tel: links and DIAL
 * (Keypad), notifications (call log or Voicemail tab), Android's call settings links (Settings),
 * and the long-press shortcuts and widgets ([WidgetUi.ACTION_OPEN_VOICEMAIL]).
 */
class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var pendingNumber by mutableStateOf<String?>(null)
    private var openVoicemail by mutableStateOf(false)
    private var openCallLog by mutableStateOf(false)
    private var openSettings by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Phone-state access lets the voicemail engine see the SIMs; kick off setup now.
            if (isDefault) DialtoneVoicemail.syncNow(this)
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = Dialer.isDefaultDialer(this)
            if (isDefault) {
                requestRuntimePermissions()
                // Start visual voicemail right away instead of waiting for the next boot.
                DialtoneVoicemail.onBoot(this)
                DialtoneVoicemail.syncNow(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        isDefault = Dialer.isDefaultDialer(this)
        pendingNumber = dialNumberFrom(intent)
        openVoicemail = wantsVoicemail(intent)
        openCallLog = intent.getBooleanExtra(MissedCalls.EXTRA_OPEN_CALL_LOG, false)
        openSettings = wantsSettings(intent)
        if (isDefault) requestRuntimePermissions()

        setContent {
            ContactsMatchTheme {
                if (isDefault) {
                    DialerApp(
                        pendingNumber = pendingNumber,
                        onPendingConsumed = { pendingNumber = null },
                        openVoicemail = openVoicemail,
                        onOpenVoicemailConsumed = { openVoicemail = false },
                        openCallLog = openCallLog,
                        onOpenCallLogConsumed = { openCallLog = false },
                        openSettings = openSettings,
                        onOpenSettingsConsumed = { openSettings = false },
                    )
                } else {
                    SetDefaultScreen(onRequest = ::requestDialerRole)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isDefault = Dialer.isDefaultDialer(this)
        // Catches up widgets and favorite shortcuts (e.g. after permissions were just granted).
        // In the foreground, Android doesn't rate-limit shortcut updates.
        if (isDefault) Widgets.refreshAllAsync(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNumber = dialNumberFrom(intent)
        if (wantsVoicemail(intent)) openVoicemail = true
        if (intent.getBooleanExtra(MissedCalls.EXTRA_OPEN_CALL_LOG, false)) openCallLog = true
        if (wantsSettings(intent)) openSettings = true
    }

    private fun wantsVoicemail(intent: Intent?): Boolean =
        intent?.action == WidgetUi.ACTION_OPEN_VOICEMAIL ||
            intent?.getBooleanExtra(VoicemailNotifier.EXTRA_OPEN_VOICEMAIL, false) == true

    private fun wantsSettings(intent: Intent?): Boolean =
        intent?.action == TelecomManager.ACTION_SHOW_CALL_SETTINGS ||
            intent?.action == TelecomManager.ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS

    /** tel: number for DIAL/VIEW intents, "" for a bare DIAL, null otherwise. */
    private fun dialNumberFrom(intent: Intent?): String? {
        if (intent == null) return null
        val data = intent.data
        return when {
            data?.scheme == "tel" -> data.schemeSpecificPart.orEmpty()
            intent.action == Intent.ACTION_DIAL -> ""
            else -> null
        }
    }

    private fun requestDialerRole() {
        val rm = getSystemService(RoleManager::class.java) ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.SEND_SMS) // visual voicemail setup texts
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot { hasPermission(it) }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }
}
