package app.dialtone.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copies of one voicemail from Dialtone and another phone app must collapse to one; different messages must not. */
class VoicemailDuplicatesTest {
    private val own = "app.dialtone.phone"
    private val other = "com.android.dialer"
    private val t = 1_700_000_000_000L

    private fun row(id: Long, pkg: String, uid: String?, number: String, date: Long, dur: Long = 30) =
        VoicemailDuplicates.Row(id, pkg, uid, number, date, dur)

    @Test fun matchesByServerMessageId() {
        val rows = listOf(row(1, own, "17", "5551234567", t), row(2, other, "17", "+1 555-123-4567", t + 60_000))
        assertEquals(mapOf(2L to 1L), VoicemailDuplicates.match(rows, own))
    }

    @Test fun fallsBackToCallerTimeAndLength() {
        val rows = listOf(row(1, own, null, "5551234567", t, 30), row(2, other, null, "+15551234567", t + 120_000, 31))
        assertEquals(mapOf(2L to 1L), VoicemailDuplicates.match(rows, own))
    }

    @Test fun differentMessagesStaySeparate() {
        val rows = listOf(
            row(1, own, "17", "5551234567", t),
            row(2, other, "18", "5559999999", t),                 // other caller
            row(3, other, null, "5551234567", t + 3_600_000),     // same caller, an hour later
            row(4, other, null, "5551234567", t, 90),             // same time, very different length
        )
        assertTrue(VoicemailDuplicates.match(rows, own).isEmpty())
    }

    @Test fun ownCopiesAreNeverHidden() {
        val rows = listOf(row(1, own, "17", "5551234567", t), row(2, own, "17", "5551234567", t))
        assertTrue(VoicemailDuplicates.match(rows, own).isEmpty())
    }
}
