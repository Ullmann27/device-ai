package at.heinz.deviceai.deepclean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's eleven sentences of 19.11.2026 and the rules for restrictions – on the productive parser. */
class DeepCleanCommandTest {
    private fun code(text: String) = DeepCleanCommands.parse(text, "Jarvis")?.code ?: "NONE"

    @Test fun theOwnersSentencesGetTheirOwnRights() {
        val expected = linkedMapOf(
            "Continuum, bereinige mein Handy" to "SAFE_CLEAN",
            "Mach mein Handy sauber" to "SAFE_CLEAN",
            "Kannst du mein Handy mal aufräumen?" to "SAFE_CLEAN",
            "Bereinige meinen Speicher" to "SAFE_CLEAN",
            "Schau, was unnötig Speicher verbraucht" to "ANALYSE",
            "Mach eine Tiefenbereinigung" to "DEEP_CLEAN",
            "Entferne unnötige Dateien" to "SAFE_CLEAN",
            "Optimier mein Handy" to "OPTIMISE",
            "Schau, warum mein Speicher so voll ist" to "ANALYSE",
            "Entferne den ganzen unnötigen Müll" to "SAFE_CLEAN",
            "Kannst du mein Handy wieder schneller machen?" to "OPTIMISE")
        for ((sentence, mode) in expected) assertEquals(sentence, mode, code(sentence))
    }

    @Test fun onlyCleaningModesMayRemoveAndOptimiseNever() {
        assertFalse(DeepCleanCommands.parse("Optimier mein Handy")!!.mayRemove)
        assertFalse(DeepCleanCommands.parse("Kannst du mein Handy wieder schneller machen?")!!.mayRemove)
        assertFalse(DeepCleanCommands.parse("Schau, was unnötig Speicher verbraucht")!!.mayRemove)
        assertTrue(DeepCleanCommands.parse("Bereinige mein Handy")!!.mayRemove)
        for (mode in DeepCleanMode.entries) assertEquals(mode.name, mode in setOf(DeepCleanMode.SAFE_CLEAN, DeepCleanMode.DEEP_CLEAN,
            DeepCleanMode.TARGETED), mode.mayRemove)
    }

    @Test fun negationsAndRestrictionsOnlyNarrow() {
        assertEquals("ANALYSE", code("Bereinige mein Handy, aber lösch nichts"))
        assertEquals("ANALYSE", code("Bereinige mein Handy, nur überprüfen"))
        assertEquals("DEEP_ANALYSE", code("Mach eine Tiefenbereinigung, aber nichts löschen"))
        assertEquals("SAFE_CLEAN|KEEP:PHOTOS", code("Bereinige mein Handy, Fotos behalten"))
        assertEquals("SAFE_CLEAN|KEEP:DOWNLOADS", code("Bereinige mein Handy und lass die Downloads in Ruhe"))
        assertEquals("DEEP_CLEAN|KEEP:PHOTOS,VIDEOS", code("Mach eine Tiefenbereinigung, aber behalte die Fotos und Videos"))
        assertEquals("SAFE_CLEAN|KEEP:WHATSAPP", code("Räum mein Handy auf ohne die WhatsApp-Bilder"))
        assertEquals("TARGETED:APKS|NO_DELETE", code("Lösch die alten APK-Dateien, aber nur zeigen"))
        assertEquals("DECLINE", code("Bereinige mein Handy nicht"))
        // A clause that is not understood removes the right to delete instead of being ignored.
        val unsure = DeepCleanCommands.parse("Bereinige mein Handy, aber pass auf die Sachen von Anna auf")!!
        assertFalse(unsure.mayRemove)
        assertTrue(unsure.unsure)
        assertEquals(DeepCleanMode.ANALYSE, unsure.mode)
    }

    @Test fun targetedOrdersNameTheirGroupOrFile() {
        assertEquals("TARGETED:APKS", code("Lösch die alten APK-Dateien"))
        assertEquals("TARGETED:DUPLICATES", code("Entferne die doppelten Dateien"))
        assertEquals("TARGETED:PARTIAL", code("Lösch die abgebrochenen Downloads"))
        assertEquals("TARGETED:NAME:urlaub 2019.mp4", code("Lösch die Datei Urlaub 2019.mp4"))
        assertEquals("REPORT", code("Wie viel Platz habe ich jetzt wieder?"))
        assertEquals("REPORT", code("sag mir danach, wie viel Platz ich wieder habe"))
        assertEquals("RESTORE", code("Stell die gelöschten Dateien wieder her"))
        assertEquals("CLARIFY", code("Lösch alles"))
        assertEquals("CLARIFY", code("Lösch meine Fotos"))
        assertEquals("CLARIFY", code("Räum auf"))
    }

    @Test fun otherThingsAreNotThisLane() {
        for (sentence in listOf("Lösch die Mail", "Räum den Kalender auf", "Mach den Bildschirm sauber", "Bereinige meinen Cache",
                "Systemdiagnose", "Analysiere mein Handy", "Wie bereinige ich mein Handy?", "Er sagte, bereinige mein Handy",
                "„Bereinige mein Handy“", "Lösch den Chat", "Leere den Papierkorb", "Prüf meinen Speicher und bereinige die temporären Dateien",
                "Was bedeutet Speicherbereinigung?", "Räum mein Zimmer auf", "Mach die Küche sauber"))
            assertEquals(sentence, "NONE", code(sentence))
    }

    @Test fun repliesToTheSelection() {
        fun r(text: String) = DeepCleanCommands.reply(text, "Jarvis")
        assertEquals(DeepCleanCommands.Reply.Count(3), r("ja, die drei"))
        assertEquals(DeepCleanCommands.Reply.Pick(listOf(1, 3)), r("die 1 und die 3"))
        assertEquals(DeepCleanCommands.Reply.Pick(listOf(1, 3)), r("die erste und die dritte"))
        assertEquals(DeepCleanCommands.Reply.Pick(listOf(2)), r("Nummer 2"))
        assertEquals(DeepCleanCommands.Reply.All, r("alle"))
        assertEquals(DeepCleanCommands.Reply.None, r("keine"))
        assertEquals(DeepCleanCommands.Reply.Stop, r("Stopp"))
        assertEquals(DeepCleanCommands.Reply.Restore, r("stell wieder her"))
        assertEquals(DeepCleanCommands.Reply.Show(DeepCleanGroup.LARGE), r("zeig mir die großen"))
        assertEquals(DeepCleanCommands.Reply.Keep(setOf(DeepCleanGroup.PHOTOS)), r("Fotos behalten"))
        assertEquals(DeepCleanCommands.Reply.Yes, r("ja"))
        assertEquals(DeepCleanCommands.Reply.No, r("nein"))
        assertEquals(DeepCleanCommands.Reply.No, r("lösch nichts"))
        assertNull(r("Erzähl mir einen Witz"))
    }
}
