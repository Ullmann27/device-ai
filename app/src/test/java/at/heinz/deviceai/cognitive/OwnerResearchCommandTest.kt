package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerResearchCommand.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner wish (#643): „Jarvis recherchiere …“ starts research from chat/voice – no Web button, no private data. */
class OwnerResearchCommandTest {
    private fun topic(u: String, name: String? = "Jarvis"): String? =
        (OwnerResearchCommand.parse(u, name) as? Outcome.Research)?.topic

    @Test fun naturalResearchCommandsYieldThePublicTopic() {
        val expected = mapOf(
            "Jarvis recherchiere die aktuelle Inflationsrate in Österreich" to "aktuelle Inflationsrate in Österreich",
            "Jarvis, recherchiere bitte zu Gemma 4" to "Gemma 4",
            "Recherchier mal nach dem Wetter in Wien" to "Wetter in Wien",
            "Such nach aktuellen Nachrichten zu Android 17" to "aktuellen Nachrichten zu Android 17",
            "Suche im Internet nach Filament Renderer Android" to "Filament Renderer Android",
            "Schau mal online nach dem Galaxy Z Fold7 Preis" to "Galaxy Z Fold7 Preis",
            "Prüfe die neuesten Quellen zu LiteRT-LM" to "LiteRT-LM",
            "Prüf neueste Quellen über den ÖBB Fahrplan" to "ÖBB Fahrplan",
            "Google bitte Kotlin 2.4 Release Notes" to "Kotlin 2.4 Release Notes",
            "Finde mir aktuelle Quellen zu Fold7 Thermal Throttling" to "Fold7 Thermal Throttling",
            "Mach eine Recherche zu Snapdragon 8 Elite NPU" to "Snapdragon 8 Elite NPU",
            "Was sagen aktuelle Quellen zu Blender 4.3?" to "Blender 4.3",
            "Recherche: Bundeskanzler Österreich" to "Bundeskanzler Österreich",
            "Hey Jarvis, such bitte im Netz nach Samsung One UI 9" to "Samsung One UI 9",
            "Recherchiere online zu Vulkan auf Android" to "Vulkan auf Android",
        )
        for ((u, t) in expected) assertEquals(u, t, topic(u))
    }

    @Test fun nonResearchRequestsAreNotCommands() {
        for (u in listOf(
            "Öffne Signal", "Suche Signal", "Starte die Kamera",           // app lookup
            "Öffne das Gedächtniszentrum", "Zeig mir das Gedächtnis",       // internal navigation
            "Wie spät ist es?", "Wer bist du?", "Wie geht es dir?",
            "Was ist die Hauptstadt von Frankreich?",                       // question, not a command
            "Erkläre mir Logik und Planung",
            "Such dir was aus", "Suche nach", "recherchiere", "Google",
            "Merke dir: recherchiere morgen weiter",
        )) assertNull(u, OwnerResearchCommand.parse(u))
    }

    @Test fun negationsAndOfflineWishesNeverBecomeResearch() {
        for (u in listOf(
            "Recherchiere nicht nach Wien", "Such bitte nicht im Internet nach Wetter",
            "Recherchiere ohne Internet zu Wien", "Prüfe keine Quellen zu Fold7",
            "Antworte offline: recherchiere Wien",
        )) assertNull(u, OwnerResearchCommand.parse(u))
    }

    @Test fun privateDataIsRefusedNotSent() {
        for (u in listOf(
            "Recherchiere meine IBAN", "Such nach meiner Adresse", "Google mein Passwort",
            "Recherchiere was ich dir gestern gesagt habe", "Such im Internet nach dem Token abc123",
            "Recherchiere nach heinz@example.com", "Recherchiere nach https://example.com/privat",
            "Prüfe neueste Quellen zu meinem Konto",
        )) {
            val out = OwnerResearchCommand.parse(u)
            assertTrue(u, out is Outcome.Refused)
            assertTrue(u, (out as Outcome.Refused).reason.contains("persönlich"))
        }
    }

    @Test fun topicIsBoundedAndClean() {
        assertNull(OwnerResearchCommand.parse("Recherchiere " + "a".repeat(600)))
        assertNull(OwnerResearchCommand.parse("Recherchiere \u0007Wien"))
        assertEquals("Wien", topic("Recherchiere nach Wien bitte."))
        assertEquals("Wien", topic("Recherchiere für mich im Internet nach Wien!"))
    }

    @Test fun activityLinesNameTheRouteWithoutClaimingResults() {
        val research = OwnerResearchCommand.parse("Jarvis recherchiere Gemma 4") as Outcome.Research
        assertTrue(research.activityLine(InternetAccessMode.ALWAYS_ON).contains("ALWAYS_ON"))
        assertTrue(research.activityLine(InternetAccessMode.ASK).contains("Freigabe"))
        assertTrue(OwnerResearchCommand.offlineReply().contains("AUS"))
        assertTrue(OwnerResearchCommand.offlineReply().contains("Keine Webanfrage"))
        val refused = OwnerResearchCommand.parse("Recherchiere meine IBAN") as Outcome.Refused
        assertTrue(refused.reply.contains("nicht an Suchdienste"))
    }
}
