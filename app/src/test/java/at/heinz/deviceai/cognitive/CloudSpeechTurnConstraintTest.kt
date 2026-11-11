package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSpeechTurnConstraintTest {
    @Test fun explicitOfflineAnswerAndTransportVetoAlsoBlockCloudSpeech() {
        for (turn in listOf(
            "Antworte offline.",
            "Beantworte diese Frage offline.",
            "Bitte erkläre mir das offline.",
            "Nur lokal antworten.",
            "Bitte ausschließlich gerätelokal.",
            "Sprich bitte nur offline.",
            "Lies diese Antwort offline vor.",
            "Erkläre mir das ohne Internet.",
            "Antworte ohne jeglichen Netzwerkzugriff.",
            "Bitte ohne Cloud.",
            "Keine Internetverbindung.",
            "Nutze keinen Cloudzugriff.",
            "Kein Internetzugriff.",
            "Nutze nicht das Internet.",
            "Verwende dabei nicht die Cloud.",
            "Niemals ins Internet.",
            "Sende die Antwort nicht in die Cloud.",
            "Cloudzugriff bleibt deaktiviert.",
            "Internet ist verboten.",
            "Verbinde dich nicht mit dem Internet.",
            "Suche online, aber antworte ohne Internet."
        )) assertTrue(turn, NetworkTurnConstraint.forbidsExternalSpeech(turn))
    }

    @Test fun researchOnlyRestrictionsAndLocalIntentsDoNotDisableSelectedVoice() {
        for (turn in listOf(
            "Hallo?", "Was kannst du?", "Wie viel Speicher habe ich?",
            "Wie spät ist es?", "Was ist Photosynthese?",
            "Antworte ohne Internetrecherche.",
            "Bitte ohne externe Websuche.",
            "Keine Internetrecherche für diese Frage.",
            "Suche nicht im Internet.",
            "Recherchiere nicht online.",
            "Nicht im Browser recherchieren.",
            "Nicht online suchen.",
            "Internetrecherche ist verboten.",
            "Recherchiere offline mit gespeicherten Quellen.",
            "Suche nur lokal nach Quellen.",
            "Antworte nicht nur offline, sondern auch online.",
            "Beantworte das nicht offline.",
            "Verwende nicht ausschließlich lokale Quellen."
        )) assertFalse(turn, NetworkTurnConstraint.forbidsExternalSpeech(turn))
    }

    @Test fun oversizedTurnCannotAuthorizeCloudSpeech() {
        assertTrue(NetworkTurnConstraint.forbidsExternalSpeech("a".repeat(8193)))
    }

    @Test fun existingResearchVetoRemainsStricterThanSpeech() {
        for (turn in listOf("Hallo?", "Antworte ohne Internetrecherche.", "Suche nur lokal nach Quellen.")) {
            assertTrue(turn, NetworkTurnConstraint.forbidsExternalResearch(turn))
            assertFalse(turn, NetworkTurnConstraint.forbidsExternalSpeech(turn))
        }
    }

    @Test fun trailingNegationAndOfflineCorrectionArePreserved() {
        for (turn in listOf(
            "Nutze das Internet nicht.",
            "Verwende die Cloud nicht.",
            "Benutze bitte das Netzwerk niemals.",
            "Antworte nicht online, sondern offline.",
            "Nicht ohne Internet, aber antworte offline.",
            "Was bedeutet 'ohne Internet'? Antworte offline.",
            "Beachte die Vorgabe: „ohne Cloud“."
        )) assertTrue(turn, NetworkTurnConstraint.forbidsExternalSpeech(turn))
    }

    @Test fun negatedOfflineAndClearlyQuotedMentionsDoNotBecomeVetoes() {
        for (turn in listOf(
            "Bitte nicht ohne Internet arbeiten.",
            "Antworte niemals ohne Cloud.",
            "Nicht  ohne Internet antworten.",
            "Verwende die Cloud nicht nur für diese Frage.",
            "Antworte nicht offline, sondern online.",
            "Was bedeutet „ohne Internet“?",
            "Was heißt 'Antworte offline' auf Englisch?",
            "Erkläre mir den Ausdruck \"ohne Cloud\".",
            "Übersetze „Antworte offline“ ins Englische.",
            "Zitiere den Satz ‚Nutze das Internet nicht‘."
        )) assertFalse(turn, NetworkTurnConstraint.forbidsExternalSpeech(turn))
    }

    @Test fun androidPatternsNeverEmbedUnsupportedUnicodeClassFlag() {
        val patterns = NetworkTurnConstraint::class.java.declaredFields.flatMap { field ->
            field.isAccessible = true
            when (val value = field.get(null)) {
                is Regex -> listOf(value)
                is Iterable<*> -> value.filterIsInstance<Regex>()
                else -> emptyList()
            }
        }
        assertTrue(patterns.isNotEmpty())
        val unsupported = Regex("\\(\\?[A-Za-z-]*U")
        patterns.forEach { assertFalse(it.pattern, unsupported.containsMatchIn(it.pattern)) }
    }
}
