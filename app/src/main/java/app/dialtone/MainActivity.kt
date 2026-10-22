package app.dialtone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dialtone.telecom.Dialer
import app.dialtone.ui.DialerApp
import app.dialtone.ui.onboarding.SetDefaultScreen
import app.dialtone.ui.theme.DialtoneTheme
import app.dialtone.util.hasPermission
import app.dialtone.voicemail.VoicemailNotifier
import app.dialtone.vvm.DialtoneVoicemail

class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var pendingNumber by mutableStateOf<String?>(null)
    private var openVoicemail by mutableStateOf(false)

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
        openVoicemail = intent.getBooleanExtra(VoicemailNotifier.EXTRA_OPEN_VOICEMAIL, false)
        if (isDefault) requestRuntimePermissions()

        setContent {
            DialtoneTheme {
                if (isDefault) {
                    DialerApp(
                        pendingNumber = pendingNumber,
                        onPendingConsumed = { pendingNumber = null },
                        openVoicemail = openVoicemail,
                        onOpenVoicemailConsumed = { openVoicemail = false },
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNumber = dialNumberFrom(intent)
        if (intent.getBooleanExtra(VoicemailNotifier.EXTRA_OPEN_VOICEMAIL, false)) openVoicemail = true
    }

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
