package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.OwnerVerbLexicon.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one verb list of the computer lane, the clause splitter and the plan check (review finding B5). */
class OwnerVerbLexiconTest {
    private fun family(clause: String) = OwnerVerbLexicon.leading(clause)?.family

    @Test fun anImperativeStartsAnOrderInItsEverydayForms() {
        for ((clause, expected) in listOf(
            "schreib ihr Hallo" to Family.WRITE, "Schreibe Anna" to Family.WRITE, "schreibst du ihr dann" to Family.WRITE,
            "schick meinem Vater eine Nachricht" to Family.SEND, "sende das" to Family.SEND, "antworte Anna" to Family.WRITE,
            "fass sie zusammen" to Family.SUMMARISE, "fasse mir das Wichtigste zusammen" to Family.SUMMARISE,
            "analysiere Seite zwei" to Family.SUMMARISE, "erklär es mir" to Family.SUMMARISE,
            "erstell eine Tabelle" to Family.CREATE, "erstelle daraus ein PDF" to Family.CREATE, "mach daraus ein PDF" to Family.MAKE,
            "speicher das Dokument" to Family.SAVE, "speichere es als Word" to Family.SAVE,
            "zeig mir ihr Profil" to Family.SHOW, "öffne sie" to Family.OPEN, "Öffne WhatsApp" to Family.OPEN,
            "such nach Bernd" to Family.SEARCH, "Suche die letzte Mail" to Family.SEARCH, "lies sie mir vor" to Family.READ,
            "sag mir, ob ich etwas vergessen könnte" to Family.TELL, "prüf meinen Speicher" to Family.CHECK,
            "bereinige die sicheren temporären Dateien" to Family.CLEAN, "ruf ihn an" to Family.CALL,
            "leite sie weiter" to Family.SEND, "lösch die Mail" to Family.DELETE, "Schau bitte meine E-Mails durch" to Family.LOOK,
            "druck es aus" to Family.OTHER, "trag einen Termin ein" to Family.OTHER
        )) assertEquals(clause, expected, family(clause))
    }

    @Test fun courtesyAndSequenceWordsInFrontDoNotHideTheVerb() {
        assertEquals(Family.WRITE, family("bitte dann schreib ihr"))
        assertEquals(Family.OPEN, family("danach öffne das PDF Argon4"))
        assertEquals(Family.SEND, family("und schick es per Mail an anna@example.org"))
        assertEquals(Family.SUMMARISE, family("zum Schluss fass Seite 2 zusammen"))
    }

    @Test fun aPoliteRequestCarriesItsVerbAtTheEnd() {
        assertEquals(Family.OPEN, family("Kannst du mir bitte mal das Argon-Dokument aufmachen"))
        assertEquals(Family.WRITE, family("könntest du Anna kurz schreiben"))
        assertEquals("aufmachen", OwnerVerbLexicon.leading("Kannst du mir das PDF aufmachen")?.word)
    }

    @Test fun aParticleDecidesWhatTheVerbMeans() {
        assertEquals(Family.OPEN, family("mach das PDF Argon4 auf"))
        assertEquals(Family.MAKE, family("mach daraus ein PDF"))
        assertEquals(Family.SEARCH, family("schau nach dem PDF Argon4"))
        assertEquals(Family.LOOK, family("schau meine Mails durch"))
    }

    @Test fun statementsQuestionsAndNamesStartNoOrder() {
        for (clause in listOf("ich schreibe gerade", "Was steht in meinen Mails", "Bernd Beispiel", "Rechnung und Mahnung",
            "welche Termine ich diese Woche habe", "die dann per Mail an anna@example.org geht", "Wie öffne ich WhatsApp",
            "er macht das schon", "sie sucht noch", ""))
            assertNull(clause, OwnerVerbLexicon.leading(clause))
    }

    @Test fun aFurtherOrderIsAConnectorOrACommaFollowedByAnImperative() {
        for (text in listOf("Anna und schreib ihr Hallo", "Rechnung, dann leite sie weiter", "Papa, danach ruf ihn an",
            "Anna und dann zeig mir ihr Profil", "Anna, schreib ihr Hallo", "Anna anschließend schick ihr das PDF",
            "Anna. Schreib ihr Hallo", "Rechnung und speicher sie als PDF", "Elena, schreibst du ihr dann bitte",
            "Anna und kannst du ihr dann schreiben"))
            assertTrue(text, OwnerVerbLexicon.hasFurtherOrder(text))
        assertEquals(4, OwnerVerbLexicon.furtherOrderAt("Anna und schreib ihr Hallo"))
    }

    @Test fun namesAndNounsAfterUndStaySearchText() {
        for (text in listOf("Anna und Bernd", "Rechnung und Mahnung", "Max und Moritz", "Haus und Garten", "Angebot und Suche",
            "Ersatz und Teile", "Märchen und Sage", "rechnung und mail von anna", "Kleid und Speicher", "Anna",
            "Wetter Wien", "Hund und Katze, Maus"))
            assertFalse(text, OwnerVerbLexicon.hasFurtherOrder(text))
        // The same words written small and going on like an order are one.
        assertTrue(OwnerVerbLexicon.hasFurtherOrder("Anna und such nach Bernd"))
        assertTrue(OwnerVerbLexicon.hasFurtherOrder("rechnung und mail ihr die datei"))
    }

    @Test fun theVerbKindsOfAWholeSentenceAreFoundInAnyForm() {
        val goal = OwnerVerbLexicon.families("Schau bitte meine E-Mails durch, fasse mir das Wichtigste zusammen, mach daraus ein PDF " +
            "und schick meinem Vater auf WhatsApp, dass ich mich später melde.")
        assertTrue(goal.containsAll(setOf(Family.LOOK, Family.SUMMARISE, Family.MAKE, Family.SEND)))
        val five = OwnerVerbLexicon.families("Such die PDF-Datei Argon4, öffne sie, analysiere Seite zwei und fasse sie zusammen")
        assertEquals(setOf(Family.SEARCH, Family.OPEN, Family.SUMMARISE), five)
        // A noun is not the verb: „den Speicher“, „eine Mail“.
        assertEquals(setOf(Family.CHECK), OwnerVerbLexicon.families("Prüf den Speicher"))
        assertEquals(setOf(Family.OPEN), OwnerVerbLexicon.families("Mach das PDF Argon4 auf"))
        assertTrue(Family.OPEN in OwnerVerbLexicon.families("Kannst du mir bitte mal das Argon-Dokument aufmachen"))
    }
}
