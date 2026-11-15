package app.dialtone.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Back-to-back calls from one number and of one type become one row that keeps every call id
 * (Delete removes them all). The same-day rule uses Android's date helper, which only runs on
 * a device, so it isn't covered here.
 */
class CollapseCallsTest {
    private fun call(id: Long, number: String, type: CallType) =
        RecentCall(id, number, null, null, type, date = 1_700_000_000_000L - id * 60_000, durationSec = 0)

    @Test fun groupsConsecutiveCallsAndKeepsIds() {
        val rows = collapseCalls(
            listOf(
                call(1, "5551234567", CallType.Missed),
                call(2, "+1 555 123 4567", CallType.Missed),
                call(3, "5551234567", CallType.Missed),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals(3, rows[0].count)
        assertEquals(listOf(1L, 2L, 3L), rows[0].ids)
    }

    @Test fun differentTypeOrNumberStartsANewRow() {
        val rows = collapseCalls(
            listOf(
                call(1, "5551234567", CallType.Missed),
                call(2, "5551234567", CallType.Incoming),
                call(3, "5559999999", CallType.Incoming),
                call(4, "5551234567", CallType.Missed),
            ),
        )
        assertEquals(listOf(1, 1, 1, 1), rows.map { it.count })
        assertEquals(listOf(listOf(1L), listOf(2L), listOf(3L), listOf(4L)), rows.map { it.ids })
    }
}
