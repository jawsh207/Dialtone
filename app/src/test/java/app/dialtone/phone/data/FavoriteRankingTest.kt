package app.dialtone.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** FavoriteRanking picks which favorites become long-press app shortcuts. */
class FavoriteRankingTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun contact(id: Long, name: String, vararg numbers: String) = Contact(
        id = id, lookupKey = "k$id", name = name, photoUri = null, starred = true,
        numbers = numbers.map { PhoneNumber(it, "Mobile") },
    )

    private fun call(number: String, daysAgo: Long, count: Int = 1) = RecentCall(
        id = number.hashCode().toLong() + daysAgo, number = number, cachedName = null, photoUri = null,
        type = CallType.Outgoing, date = now - daysAgo * day, durationSec = 60, count = count,
    )

    private val ann = contact(1, "Ann", "555-000-0001")
    private val bob = contact(2, "Bob", "555-000-0002", "555-000-0022")
    private val cat = contact(3, "Cat", "555-000-0003")

    @Test fun mostCalledComesFirst() {
        val calls = listOf(call("+1 555 000 0003", 1), call("5550000003", 2), call("5550000001", 1))
        assertEquals(listOf(cat, ann, bob), FavoriteRanking.rank(listOf(ann, bob, cat), calls, now))
    }

    @Test fun anyOfAContactsNumbersCounts() {
        val calls = listOf(call("5550000022", 0), call("5550000002", 0), call("5550000001", 0))
        assertEquals(bob, FavoriteRanking.rank(listOf(ann, bob, cat), calls, now).first())
    }

    @Test fun recentCallsOutweighOldOnes() {
        // Two calls to Ann three months ago vs one call to Bob yesterday.
        val calls = listOf(call("5550000001", 90), call("5550000001", 91), call("5550000002", 1))
        assertEquals(bob, FavoriteRanking.rank(listOf(ann, bob), calls, now).first())
    }

    @Test fun collapsedRowsCountEveryCall() {
        val calls = listOf(call("5550000001", 3, count = 3), call("5550000002", 3))
        assertEquals(ann, FavoriteRanking.rank(listOf(bob, ann), calls, now).first())
    }

    @Test fun uncalledFavoritesKeepTheirOrder() {
        assertEquals(listOf(ann, bob, cat), FavoriteRanking.rank(listOf(ann, bob, cat), emptyList(), now))
    }

    @Test fun weightHalvesAfterAWeek() {
        assertEquals(1.0, FavoriteRanking.callWeight(now, now), 1e-9)
        assertEquals(0.5, FavoriteRanking.callWeight(now, now - 7 * day), 1e-9)
        assertTrue(FavoriteRanking.callWeight(now, now + day) <= 1.0) // clock skew never boosts
    }
}
