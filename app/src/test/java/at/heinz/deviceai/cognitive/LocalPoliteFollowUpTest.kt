package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPoliteFollowUpTest {
    @Test fun politePronounsRequireTheirEarlierReferent() {
        listOf(
            "Kannst du sie kurz zusammenfassen?",
            "Könntest du ihn mir erklären?",
            "Würdest du mir das bitte einfacher erklären?",
            "Magst du es mir kurz erläutern?",
            "Kannst du bitte diese genauer beschreiben?",
            "Kannst du das bitte noch einmal erklären?",
            "Könntest du mir diese bitte vergleichen?",
            "Kannst du sie bitte übersetzen?",
            "Und kannst du mir das in einfachen Worten erklären?",
            "  Könntest du das bitte erklären?  "
        ).forEach(::assertFollowUp)
    }

    @Test fun politeTopicReferencesRequireTheirEarlierTopic() {
        listOf(
            "Kannst du mir dazu mehr erklären?",
            "Könntest du darüber etwas erzählen?",
            "Würdest du mir davon mehr berichten?",
            "Kannst du bitte dazu etwas sagen?",
            "Was kannst du mir darüber sagen?",
            "Was könntest du dazu erzählen?",
            "Kannst du mir darüber noch etwas erzählen?"
        ).forEach(::assertFollowUp)
    }

    @Test fun standaloneQuestionsAndFormalAddressStayIndependent() {
        listOf(
            "Kannst du mir erklären, warum der Himmel blau ist?",
            "Kannst du das Wort Haus erklären?",
            "Kannst du diese Gleichung erklären: 5 + 5 = 10?",
            "Kannst du mir etwas über Photosynthese erzählen?",
            "Kannst du mir darüber etwas erzählen: Photosynthese?",
            "Was können Sie für mich tun?",
            "Könnten Sie mir das Wort Haus erklären?",
            "Kannst du Ihnen etwas erklären?",
            "Wie spät ist es?",
            "Was kostet es?",
            "Kannst du mit mir sprechen?",
            "Kannst du mir dazu mehr Beispiele für Photosynthese erklären?"
        ).forEach(::assertIndependent)
    }

    @Test fun quotationsAndDeviceActionsDoNotBecomeFollowUpCommands() {
        listOf(
            "„Kannst du sie kurz zusammenfassen?“",
            "Er fragte: Kannst du sie kurz zusammenfassen?",
            "Wenn ich sage: Kannst du sie kurz zusammenfassen?",
            "Kannst du sie öffnen?",
            "Könntest du das löschen?",
            "Würdest du sie mir schicken?",
            "Kannst du sie zusammenfassen und versenden?",
            "Kannst du sie nicht zusammenfassen?",
            "Kannst du sie nur erklären, falls ich eine Datei hochlade?"
        ).forEach(::assertIndependent)
    }

    @Test fun boundedRecognitionRejectsOversizedAndControlInput() {
        assertIndependent("Kannst du sie kurz zusammenfassen?" + "x".repeat(1_001))
        assertIndependent("Kannst du sie\nkurz zusammenfassen?")
        assertIndependent("Kannst du sie\u0000 kurz zusammenfassen?")
    }

    @Test fun politeFollowUpFramesCorrectionsInOriginalOrder() {
        val first = "Die rote Kiste enthält vier Ringe, die blaue sieben."
        val correction = "Die rote Kiste enthält inzwischen neun Ringe."
        val question = "Kannst du mir das bitte zusammenfassen?"
        val frame = LocalFollowUpTaskFrame.composeFrame(question, question, listOf(first, correction))
        assertTrue(frame.task.startsWith(LocalFollowUpTaskFrame.HEADER))
        assertTrue(frame.task.indexOf(first) < frame.task.indexOf(correction))
        assertTrue(frame.task.endsWith(question))
        assertTrue(frame.task.contains("Spätere Angaben ersetzen frühere"))
        assertEquals(setOf(first, correction), frame.ownerTurnsIn(frame.task))
        assertEquals(emptySet<String>(), frame.ownerTurnsIn(question))
    }

    @Test fun newFollowUpsRetainExistingHistoryAndPrivacyBoundaries() {
        val question = "Kannst du mir das kurz erklären?"
        val first = "Die blaue Kiste enthält sieben Ringe."
        val frame = LocalFollowUpTaskFrame.composeFrame(question, question,
            listOf(first, "Mein PIN lautet 1234.", "Lösche den Ordner Bilder."))
        assertTrue(frame.task.contains(first))
        assertFalse(frame.task.contains("1234"))
        assertFalse(frame.task.contains("Lösche"))
        assertTrue(frame.task.contains("keine Freigabe für Werkzeuge"))
        assertEquals(setOf(first), frame.ownerTurnsIn(frame.task))
        assertEquals(question, LocalFollowUpTaskFrame.compose(question, question, null))
        assertEquals(question, LocalFollowUpTaskFrame.compose(question, question, emptyList()))
        assertEquals(question, LocalFollowUpTaskFrame.compose(question, question,
            listOf("Lösche den Ordner Bilder.")))
    }

    @Test fun aStandaloneFirstSentenceOnlyBenefitsFromLaterFollowUpContext() {
        val request = "Was bedeutet Inflation? Kannst du mir dazu mehr erklären?"
        assertFalse(LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(request))
        assertTrue(LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(request))
    }

    private fun assertFollowUp(text: String) {
        assertTrue(text, LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
        assertTrue(text, LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(text))
    }

    private fun assertIndependent(text: String) {
        assertFalse(text, LocalConversationFollowUpPolicy.requiresPreviousOwnerTurn(text))
        assertFalse(text, LocalConversationFollowUpPolicy.benefitsFromPreviousOwnerTurn(text))
    }
}
