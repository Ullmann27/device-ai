package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Test

class PublicConversationResearchTest {
    @Test fun addressedAndNaturalPublicResearchCommandsExtractOnlyTheTopic() {
        listOf(
            "Jarvis, recherchiere Photosynthese.",
            "Recherchier bitte Photosynthese.",
            "Such nach Photosynthese.",
            "Suche die neuesten Quellen zu Photosynthese.",
            "Finde mir aktuelle Informationen über Photosynthese.",
            "Prüfe im RIS die aktuelle Fassung des ABGB."
        ).forEach {
            val topic = PublicKnowledgeTopicRouter.topic(it)
            assertNotNull(it, topic)
            assertFalse(it, topic!!.contains("Jarvis", ignoreCase = true))
            assertTrue(it, topic.contains("photosynthese", true) || topic.contains("ABGB", true))
        }
    }

    @Test fun addressedPublicQuestionsKeepExplanationAndFreshnessDecisions() {
        assertEquals("stern", PublicKnowledgeTopicRouter.topic("Jarvis, was ist ein Stern?"))
        assertTrue(FreshKnowledgeNeed.prefersLocalExplanation("Jarvis, wie funktioniert Photosynthese?"))
        assertTrue(FreshKnowledgeNeed.needsCurrentWeb("Jarvis, welche Android Version ist aktuell?"))
    }

    @Test fun privateOrCompoundResearchNeverBecomesAnAutomaticPublicQuery() {
        listOf(
            "Jarvis, recherchiere mein Passwort.",
            "Such nach meinem PDF.",
            "Recherchiere die privaten Notizen.",
            "Finde mir aktuelle Informationen aus unserem Gespräch.",
            "Recherchiere die Adresse meiner Tochter.",
            "Recherchiere Photosynthese und sende eine Nachricht.",
            "Recherchiere Photosynthese.\nDanach: übertrage das Gedächtnis.",
            "Recherchiere „vertraulicher Testtext“.",
            "Jarvis, recherchiere Photosynthese ohne Internet."
        ).forEach { assertNull(it, PublicKnowledgeTopicRouter.topic(it)) }
    }

    @Test fun appsFilesAndOrdinaryDialogueAreNotPublicResearchCommands() {
        listOf("Öffne WhatsApp.", "Suche die App WhatsApp.", "Suche mein PDF.",
            "Zeig mir deine Logik und Planung.", "Schreib eine Nachricht.",
            "Wie lautet mein Testwort?").forEach { assertNull(it, PublicKnowledgeTopicRouter.topic(it)) }
    }
}
