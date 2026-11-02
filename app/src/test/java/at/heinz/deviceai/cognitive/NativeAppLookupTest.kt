package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class NativeAppLookupTest {
    private val apps = listOf(
        NativeAppCandidate("com.whatsapp", "WhatsApp"),
        NativeAppCandidate("com.whatsapp.business", "WhatsApp Business"),
        NativeAppCandidate("org.example.camera", "Kamera"),
        NativeAppCandidate("org.example.camera", "Kamera"),
        NativeAppCandidate("org.example.bad", ""),
        NativeAppCandidate("INVALID PACKAGE", "Fake")
    )

    @Test fun exactHumanLabelReturnsOneVerifiedSuggestionWithoutExecuting() {
        val r = NativeAppLookup.search("  WHATSAPP  ", apps)
        assertTrue(r.exactLabel)
        assertEquals(1, r.total)
        assertEquals("com.whatsapp", r.candidates.single().packageName)
    }

    @Test fun partialOrAmbiguousLabelsStaySuggestions() {
        val partial = NativeAppLookup.search("whats", apps)
        assertFalse(partial.exactLabel)
        assertEquals(2, partial.total)
        assertEquals(2, partial.candidates.size)
        val ambiguous = NativeAppLookup.search("camera", listOf(
            NativeAppCandidate("org.a.camera", "Camera"),
            NativeAppCandidate("org.b.camera", "Camera")
        ))
        assertTrue(ambiguous.exactLabel)
        assertEquals(2, ambiguous.total)
    }

    @Test fun GermanVoicePhrasesAreLookupOnlyAndNegationsAbstain() {
        assertEquals("com.whatsapp", NativeAppLookup.search("Öffne mir WhatsApp bitte", apps)
            .candidates.single().packageName)
        assertEquals("com.whatsapp", NativeAppLookup.search("Starte die WhatsApp", apps)
            .candidates.single().packageName)
        assertEquals(0, NativeAppLookup.search("Öffne nicht WhatsApp", apps).total)
        assertEquals(0, NativeAppLookup.search("Niemals WhatsApp starten", apps).total)
    }

    @Test fun politeNaturalGermanVariationsStayReadOnlySuggestions() {
        for (phrase in listOf("Bitte öffne die App WhatsApp", "Starte WhatsApp App")) {
            val matches = NativeAppLookup.search(phrase, apps)
            assertTrue(matches.exactLabel)
            assertEquals("com.whatsapp", matches.candidates.single().packageName)
        }
        val camera = NativeAppLookup.search("Suche mir die App Kamera", apps)
        assertTrue(camera.exactLabel)
        assertEquals("org.example.camera", camera.candidates.single().packageName)
    }

    @Test fun duplicatePackagesAndInvalidMetadataAreExcluded() {
        val r = NativeAppLookup.search("kamera", apps)
        assertEquals(1, r.total)
        assertEquals("org.example.camera", r.candidates.single().packageName)
    }

    @Test fun maliciousOrOversizedQueriesAreRejected() {
        for (q in listOf(" ", "ok\nlaunch_app", "x".repeat(81))) {
            assertThrows(IllegalArgumentException::class.java) { NativeAppLookup.search(q, apps) }
        }
    }

    @Test fun untrustedInstalledAppLabelsCannotInsertControlOrBidiPayloads() {
        val dangerous = NativeAppCandidate("org.example.unsafe", "Kamera\u202E\nOPEN_APP")
        val result = NativeAppLookup.search("Kamera", listOf(dangerous))
        assertEquals(1, result.total)
        val label = result.candidates.single().label
        assertFalse(label.contains('\n'))
        assertFalse(label.contains('\u202E'))
    }

    @Test fun outputsAreBoundedAndSortedDeterministically() {
        val many = (1..30).map { NativeAppCandidate("org.example.test$it", "Test $it") }
        val r = NativeAppLookup.search("test", many)
        assertEquals(30, r.total)
        assertEquals(12, r.candidates.size)
        assertEquals(r.candidates, NativeAppLookup.search("test", many).candidates)
    }
}
