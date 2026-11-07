package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class SessionRevisionRoutingTest {
    @Test fun indirectRetractionAndPronounMoveAreTerminalWithoutGuessingNewLocation() {
        for (correction in listOf("Das stimmt nicht.", "Diese Angabe stimmt nicht.",
                "Sie zog nach Wien.", "Er zieht nach Linz.")) {
            val context = LocalConversationContext()
            context.record("s", "Anna ist in Graz. Ben ist in Perg.", "Unbestätigt.")
            context.record("s", correction, "Unbestätigt.")
            assertTrue(correction, CertifiedLocationReflex.routeFromSession("Wo ist Anna?",
                context.sessionEvidence("s")) is CertifiedLocationReflex.Routing.Withheld)
            assertTrue(correction, CertifiedLocationReflex.routeFromSession("Wo ist Ben?",
                context.sessionEvidence("s")) is CertifiedLocationReflex.Routing.Withheld)
        }
    }

    @Test fun compressionKeepsLateNegationInTheModelContext() {
        val context = LocalConversationContext()
        val literal = "Anna ist in Graz. " + "Beilage. ".repeat(40) + "Anna ist nicht mehr in Graz."
        context.record("s", literal, "Unbestätigte Modellantwort.")
        assertTrue(context.render("s").contains("Anna ist nicht mehr in Graz."))
        assertTrue(CertifiedLocationReflex.routeFromSession("Wo ist Anna?",
            context.sessionEvidence("s")) is CertifiedLocationReflex.Routing.Withheld)
    }

    @Test fun omittedOversizedCorrectionCannotLeakAnOldAffirmativePrompt() {
        val context = LocalConversationContext()
        context.record("s", "Anna ist in Graz.", "Graz.")
        context.record("s", "Beilage. ".repeat(1_000) + "Das stimmt nicht.", "Unklar.")
        assertNull(context.sessionEvidence("s"))
        assertFalse(context.render("s").contains("Anna ist in Graz."))
        assertTrue(context.render("s").contains("UNVOLLSTÄNDIG"))
    }

    @Test fun benignDialogueAndSessionResetKeepTheirOriginalBoundaries() {
        val context = LocalConversationContext()
        context.record("s", "Anna ist in Graz. Ben ist in Perg.", "Unbestätigt.")
        context.record("s", "Sie mag Kaffee.", "Unbestätigt.")
        val route = CertifiedLocationReflex.routeFromSession("Wo ist Anna?", context.sessionEvidence("s"))
        assertEquals("Graz", (route as CertifiedLocationReflex.Routing.Proven).proof.support.text)
        assertNull(CertifiedLocationReflex.routeFromSession("Wo ist Anna?", context.sessionEvidence("other")))
        assertFalse(context.render("other").contains("Anna"))
    }
}
