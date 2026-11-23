package app.dialtone.phone.ui.voicemail

import app.dialtone.phone.data.VvmAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Voicemail tab's mailbox line shows only what carriers reported, per SIM when there are several. */
class MailboxSpaceTest {
    private fun sim(label: String, used: Int?, total: Int?, enabled: Boolean = true) =
        VvmAccount(label, label, true, "vvm_type_vvm3", enabled, true, false, used, total)

    @Test fun nothingWhenUnreported() {
        assertNull(mailboxSpace(listOf(sim("SIM 1", null, null))))
        assertNull(mailboxSpace(listOf(sim("SIM 1", 4, 20, enabled = false))))
    }

    @Test fun oneSim() {
        assertEquals("4 of 20 in mailbox", mailboxSpace(listOf(sim("SIM 1", 4, 20), sim("SIM 2", null, null))))
    }

    @Test fun severalSims() {
        assertEquals("Spectrum 4/20 · Work 1/30", mailboxSpace(listOf(sim("Spectrum", 4, 20), sim("Work", 1, 30))))
    }
}
