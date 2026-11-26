package app.dialtone.phone.widget

import android.app.Activity
import android.os.Bundle
import app.dialtone.phone.telecom.Dialer

/**
 * Places a call and closes, with no screen of its own: what a Favorites or Speed dial widget tap
 * and a favorite's long-press app shortcut open.
 *
 * Launchers can only start activities from shortcuts, so a plain "call this number" needs one.
 * It isn't exported: only Dialtone's own widgets and shortcuts (which the launcher starts on
 * Dialtone's behalf) can reach it, so other apps can't use it to place calls. Telecom then shows
 * the in-call screen as for any other call.
 */
class QuickCallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        if (!number.isNullOrBlank()) Dialer.call(this, number)
        // Theme.NoDisplay activities must finish before they would be shown.
        finish()
    }
}
