package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Code55 field P0: „Suche mir eine PDF“ must open the Android PDF picker, not web research. */
class OwnerDocumentFindIntentTest {
    @Test fun findingAPdfOnTheDeviceOpensThePicker() {
        listOf("Suche mir eine PDF", "Suche mir eine PDF.", "Such mir bitte eine PDF-Datei", "Finde meine PDF",
            "Hol mir die PDF", "Suche mir eine PDF aus meinem Handy", "Bitte suche mir eine PDF",
            "Kannst du mir eine PDF suchen?", "Kannst du mir bitte die PDF heraussuchen?"
        ).forEach { assertEquals(it, OwnerDocumentIntent.Request(wantsDraft = false), OwnerDocumentIntent.parse(it)) }
    }

    @Test fun webTopicsNegationsAndSharingKeepTheirRoute() {
        listOf("Suche mir eine PDF über Quantenphysik im Internet", "Suche im Internet eine PDF",
            "Suche mir keine PDF", "Suche mir eine PDF und sende sie an Anna", "Suche mir eine PDF online",
            "Suche die PDF und lösche sie", "Suche mir Rezepte"
        ).forEach { assertNull(it, OwnerDocumentIntent.parse(it)) }
    }
}
