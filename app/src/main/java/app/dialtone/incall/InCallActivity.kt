package app.dialtone.incall

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dialtone.telecom.CallManager
import app.dialtone.ui.theme.ContactsMatchTheme

class InCallActivity : ComponentActivity() {

    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handle(intent)
        setContent {
            ContactsMatchTheme {
                InCallRoute(onFinished = { finishAndRemoveTask() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallManager.ringingCall()?.let(CallManager::answer)
        }
    }

    /** Screen off when the phone is held to the ear. */
    override fun onStart() {
        super.onStart()
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "dialtone:proximity")
                .apply { acquire(4 * 60 * 60 * 1000L) }
        }
    }

    override fun onStop() {
        proximityLock?.let { if (it.isHeld) it.release() }
        proximityLock = null
        super.onStop()
    }

    companion object {
        const val EXTRA_ANSWER = "app.dialtone.extra.ANSWER"

        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
