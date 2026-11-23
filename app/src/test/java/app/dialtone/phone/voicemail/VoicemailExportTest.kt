package app.dialtone.phone.voicemail

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved and shared voicemails need a type and file name that players and file systems accept. */
class VoicemailExportTest {
    private val utc = ZoneId.of("UTC")

    @Test fun cleansMimeTypes() {
        assertEquals("audio/amr", VoicemailExport.cleanMime("Audio/AMR; name=\"msg.amr\""))
        assertEquals("audio/amr", VoicemailExport.cleanMime(null))
        assertEquals("audio/amr", VoicemailExport.cleanMime("garbage"))
        assertEquals("audio/x-wav", VoicemailExport.cleanMime("audio/x-wav"))
    }

    @Test fun picksExtensions() {
        assertEquals("amr", VoicemailExport.extensionFor("audio/amr"))
        assertEquals("wav", VoicemailExport.extensionFor("audio/x-wav"))
        assertEquals("m4a", VoicemailExport.extensionFor("audio/mp4"))
        assertEquals("audio", VoicemailExport.extensionFor("audio/something-new"))
    }

    @Test fun namesFilesSafely() {
        // 2026-11-22 21:36 UTC
        val t = 1_795_383_360_000L
        assertEquals("Voicemail from Alex Doe 2026-11-22 21.36.amr", VoicemailExport.fileName("Alex Doe", t, "amr", utc))
        assertEquals("Voicemail from AC DC 2026-11-22 21.36.amr", VoicemailExport.fileName("A/C: DC?", t, "amr", utc))
        assertEquals("Voicemail from Unknown 2026-11-22 21.36.wav", VoicemailExport.fileName(" */ ", t, "wav", utc))
    }
}
