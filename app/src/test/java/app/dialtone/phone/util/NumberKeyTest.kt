package app.dialtone.phone.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** numberKey is how one caller is recognised across the call log, contacts, voicemail and the Send to voicemail list. */
class NumberKeyTest {
    @Test fun sameNumberInDifferentFormatsMatches() {
        val key = numberKey("5551234567")
        assertEquals(key, numberKey("+1 (555) 123-4567"))
        assertEquals(key, numberKey("1-555-123-4567"))
        assertEquals(key, numberKey("555.123.4567"))
    }

    @Test fun keepsTheLastTenDigits() {
        assertEquals("5551234567", numberKey("+15551234567"))
    }

    @Test fun shortNumbersKeepAllDigits() {
        assertEquals("611", numberKey("611"))
        assertEquals("", numberKey("Unknown"))
    }

    @Test fun differentNumbersDiffer() {
        assertNotEquals(numberKey("555-123-4567"), numberKey("555-123-4568"))
    }
}
