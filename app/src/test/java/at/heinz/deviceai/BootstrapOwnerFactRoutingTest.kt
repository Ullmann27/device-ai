package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapOwnerFactRoutingTest {
    @Test fun everyDeterministicFactReaderRequiresTheCheckedLedger() {
        for (text in listOf("Was weißt du?", "Was hast du dir gemerkt?", " Erinnerungen ",
            "Prüfe Wissen: Beispiel | wohnt in | Teststadt", "Korrigiere: vorher => Fakt: Person | Ort | Ziel",
            "Widerrufe: bisheriger Fakt", "PRÜFE WISSEN: Beispiel | Ort | Ziel")) {
            assertTrue("Checked owner facts required for $text", BootstrapBrain.requiresOwnerFacts(text))
        }
    }

    @Test fun unrelatedCommandsAndNewExplicitFactsDoNotNeedAWorkingHistoricalLedger() {
        for (text in listOf("Hallo", "Prüfe Akku", "Merke dir: Ich mag Tee.", "Vergiss alles", "Trainiere: Status => Akku")) {
            assertFalse("No owner-fact recall in $text", BootstrapBrain.requiresOwnerFacts(text))
        }
    }
}
