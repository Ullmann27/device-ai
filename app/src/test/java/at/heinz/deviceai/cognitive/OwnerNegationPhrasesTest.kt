package at.heinz.deviceai.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recheck 1c6e50d9: "auf keinen Fall" and a trailing "keinesfalls" negate; no false alarms. */
class OwnerNegationPhrasesTest {
    @Test fun aufKeinenFallAndTrailingKeinesfallsNegate() {
        assertTrue(ExternalContentActionGuard.ownerNegated("lösch bitte auf keinen Fall x", "delete_file"))
        assertTrue(ExternalContentActionGuard.ownerNegated("lösche x.tmp keinesfalls", "delete_file"))
        assertTrue(ExternalContentActionGuard.ownerNegated("verschieb das auf keinen Fall", "move_file"))
        assertTrue(ExternalContentActionGuard.ownerNegated("auf keinen Fall verschieben", "move_file"))
    }

    @Test fun noFalseAlarms() {
        assertFalse(ExternalContentActionGuard.ownerNegated("lösche die Datei, nicht die andere", "delete_file"))
        assertFalse(ExternalContentActionGuard.ownerNegated("nicht vergessen: lösche x.tmp", "delete_file"))
        assertFalse(ExternalContentActionGuard.ownerNegated("lösch bitte auf keinen Fall x", "move_file"))
    }
}
