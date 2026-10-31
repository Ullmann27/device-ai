package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicKnowledgeTopicRouterTest {
    @Test fun choosesHackingFromAnOpenTechnicalQuestion() {
        assertEquals("hacking", PublicKnowledgeTopicRouter.topic("Wie funktioniert Hacking?"))
    }

    @Test fun supportsArbitraryPublicKnowledgeFields() {
        assertEquals("tiere", PublicKnowledgeTopicRouter.topic("Was sind Tiere?"))
        assertEquals("stern", PublicKnowledgeTopicRouter.topic("Was ist ein Stern?")?.let {
            if (it == "stern") it else "not_exact"
        })
    }

    @Test fun keepsPrivateQuestionsLocal() {
        assertNull(PublicKnowledgeTopicRouter.topic("Was ist mein Passwort?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Wie ist meine Adresse?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Was ist test@example.org?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Wie komme ich zu https://example.org?"))
    }

    @Test fun doesNotSearchOrdinaryConversationOrCommands() {
        assertNull(PublicKnowledgeTopicRouter.topic("Hallo, wie geht es dir?"))
        assertNull(PublicKnowledgeTopicRouter.topic("Installiere eine Anwendung."))
    }
}
