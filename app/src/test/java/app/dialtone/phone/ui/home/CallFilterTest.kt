package app.dialtone.phone.ui.home

import app.dialtone.phone.data.CallType
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.RecentCall
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Home tab's call log filters: All, Missed, Dialed, Contacts, Unknown. */
class CallFilterTest {
    private fun call(id: Long, type: CallType) = RecentCall(id, "555000000$id", null, null, type, 0L, 0L)

    private val missed = call(1, CallType.Missed)
    private val dialed = call(2, CallType.Outgoing)
    private val incoming = call(3, CallType.Incoming)
    private val calls = listOf(missed, dialed, incoming)
    private val saved = setOf(dialed.id)
    private fun contactFor(c: RecentCall): Contact? = if (c.id in saved) CONTACT else null

    private fun shown(f: CallFilter) = calls.filter { f.matches(it, contactFor(it)) }.map { it.id }

    @Test fun all() = assertEquals(listOf(1L, 2L, 3L), shown(CallFilter.All))
    @Test fun missedOnly() = assertEquals(listOf(1L), shown(CallFilter.Missed))
    @Test fun dialedOnly() = assertEquals(listOf(2L), shown(CallFilter.Dialed))
    @Test fun savedContacts() = assertEquals(listOf(2L), shown(CallFilter.Contacts))
    @Test fun unknownNumbers() = assertEquals(listOf(1L, 3L), shown(CallFilter.Unknown))

    private companion object {
        val CONTACT = Contact(id = 9, lookupKey = "sam", name = "Sam", photoUri = null, starred = false, numbers = emptyList())
    }
}
