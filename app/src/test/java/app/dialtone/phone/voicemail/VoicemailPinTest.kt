package app.dialtone.phone.voicemail

import app.dialtone.phone.vvm.PinChanger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The PIN screen must catch mistakes before contacting the carrier, using the carrier's length rules when it gave them. */
class VoicemailPinTest {
    @Test fun acceptsValidPin() {
        assertNull(VoicemailPin.problem("1111", "8642", "8642", 4, 7))
    }

    @Test fun enforcesCarrierLengths() {
        assertEquals("Use at least 4 digits", VoicemailPin.problem("1111", "864", "864", 4, 7))
        assertEquals("Use no more than 7 digits", VoicemailPin.problem("1111", "86420000", "86420000", 4, 7))
    }

    @Test fun noRulesMeansAnyLength() {
        assertNull(VoicemailPin.problem("1111", "86", "86", 0, 0))
        assertNull(VoicemailPin.lengthHint(0, 0))
    }

    @Test fun catchesMismatchSameAndNonDigits() {
        assertEquals("The PINs don't match", VoicemailPin.problem("1111", "8642", "8643", 4, 7))
        assertEquals("The new PIN is the same as the current one", VoicemailPin.problem("8642", "8642", "8642", 4, 7))
        assertEquals("Use numbers only", VoicemailPin.problem("1111", "86a2", "86a2", 4, 7))
        assertEquals("Enter your current PIN", VoicemailPin.problem("", "8642", "8642", 4, 7))
    }

    @Test fun hintsAndResults() {
        assertEquals("4 to 7 digits", VoicemailPin.lengthHint(4, 7))
        assertEquals("6 digits", VoicemailPin.lengthHint(6, 6))
        assertEquals("The current PIN isn't right", VoicemailPin.resultMessage(PinChanger.CHANGE_PIN_MISMATCH, 4, 7))
        assertEquals("Your carrier needs at least 4 digits", VoicemailPin.resultMessage(PinChanger.CHANGE_PIN_TOO_SHORT, 4, 7))
    }
}
