package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerResearchCommand.Outcome
import org.junit.Assert.*
import org.junit.Test

/** Code51 reconciliation: one conversational research entry for the #650/#653 and #656 phrasings. */
class ConversationResearchSingleEntryTest {
    private fun outcome(u: String) = OwnerResearchCommand.parse(u, "Jarvis")

    @Test fun routerOnlyPhrasingsReachTheSingleEntry() {
        for (u in listOf("Suche die neuesten Quellen zu Fusionsenergie", "Jarvis, suche aktuelle Infos zu Wasserstoffzügen",
            "Prüfe im RIS Mietrecht Kündigungsfrist")) {
            val research = outcome(u) as? Outcome.Research
            assertNotNull(u, research)
            assertTrue(u, research!!.topic.isNotBlank() && research.topic.length <= 80)
            assertEquals(u, PublicKnowledgeTopicRouter.explicitTopic(u), research.topic)
        }
    }

    @Test fun patternFamilyKeepsTheCleanerTopic() {
        assertEquals("Wasserstoffzüge",
            (outcome("Jarvis, finde aktuelle Informationen über Wasserstoffzüge") as Outcome.Research).topic)
    }

    @Test fun secondActionIsRefusedOnEveryPhrasing() {
        for (u in listOf("Recherchiere Fusionsenergie und sende es an Anna",
            "Suche die neuesten Quellen zu Fusion und lösche danach den Verlauf",
            "Google Wetter Wien und installiere die App")) {
            val refused = outcome(u) as? Outcome.Refused
            assertNotNull(u, refused)
            assertTrue(u, refused!!.reply.contains("nicht an Suchdienste"))
        }
    }

    @Test fun privateDocumentsAndDialogueNeverBecomeASearch() {
        for (u in listOf("Recherchiere meine Notizen zu Projekt Falke", "Suche die neuesten Quellen zu unserem Gespräch",
            "Recherchiere den Inhalt der Datei Vertrag")) assertTrue(u, outcome(u) is Outcome.Refused)
    }

    @Test fun offlineWishesNavigationAppsAndFilesStayOutside() {
        for (u in listOf("Suche ohne Internet die neuesten Quellen zu Wien", "Öffne das Gedächtniszentrum",
            "Zeig mir die Arbeitsaufträge", "Öffne WhatsApp", "Suche mein PDF", "Schreib eine Nachricht",
            "Wie geht es dir?")) assertNull(u, outcome(u))
    }

    @Test fun navigationAndResearchNeverClaimTheSameSentence() {
        for (u in listOf("Öffne das Gedächtniszentrum", "Zeig mir deine Logik und Planung", "Recherchiere Gemma 4",
            "Suche die neuesten Quellen zu Fusionsenergie", "Zeig mir die Recherche", "Google Logik und Planung"))
            assertFalse(u, OwnerInternalNavigationIntent.resolve(u, "Jarvis") != null && outcome(u) != null)
    }
}
