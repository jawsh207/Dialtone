package app.dialtone.phone.data

import app.dialtone.phone.util.numberKey

/**
 * How much each person is called, for ordering things by "who you call most": the Keypad tab's
 * Suggested list and the favorites offered as long-press app shortcuts.
 *
 * Every call counts, but recent ones count more: a call today weighs 1, a week ago ½, a month ago
 * about ¼. So someone you called a lot last year drops below someone you call every few days.
 */
object FavoriteRanking {

    private const val DAY_MS = 86_400_000.0

    /** Weight of one call made at [date], seen at [now]. */
    fun callWeight(now: Long, date: Long): Double {
        val days = ((now - date) / DAY_MS).coerceAtLeast(0.0)
        return 1.0 / (1.0 + days / 7.0)
    }

    /**
     * [favorites] ordered by how much they're called (any of their numbers). Favorites with the
     * same score, including those never called, keep their original (alphabetical) order.
     */
    fun rank(favorites: List<Contact>, calls: List<RecentCall>, now: Long): List<Contact> {
        val owner = HashMap<String, Long>()
        favorites.forEach { c ->
            c.numbers.forEach { n -> numberKey(n.number).takeIf { it.isNotEmpty() }?.let { owner.putIfAbsent(it, c.id) } }
        }
        val score = HashMap<Long, Double>()
        calls.forEach { call ->
            val id = owner[numberKey(call.number)] ?: return@forEach
            // A collapsed row stands for [count] back-to-back calls.
            score[id] = (score[id] ?: 0.0) + call.count * callWeight(now, call.date)
        }
        // sortedByDescending is stable, so ties stay in their given order.
        return favorites.sortedByDescending { score[it.id] ?: 0.0 }
    }
}
