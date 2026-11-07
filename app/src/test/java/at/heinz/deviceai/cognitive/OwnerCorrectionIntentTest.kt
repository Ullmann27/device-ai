package at.heinz.deviceai.cognitive

import at.heinz.deviceai.cognitive.CockpitWorkJournal.Episode
import at.heinz.deviceai.cognitive.CockpitWorkJournal.Kind
import at.heinz.deviceai.cognitive.CockpitWorkJournal.State
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent.Correction
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent.Target
import at.heinz.deviceai.cognitive.OwnerCorrectionIntent.Which
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class OwnerCorrectionIntentTest {
    private val vienna = TimeZone.getTimeZone("Europe/Vienna")
    private fun at(day: Int, hour: Int) = Calendar.getInstance(vienna).apply { clear(); set(2026, Calendar.NOVEMBER, day, hour, 0, 0) }.timeInMillis

    @Test fun theOwnersCorrectionsAreRecognisedWithTheirReference() {
        assertEquals(Correction(Target.DRAFT, Which.LAST), OwnerCorrectionIntent.parse("Nein, ich meinte den letzten Entwurf"))
        assertEquals(Correction(Target.DOCUMENT, Which.PREVIOUS), OwnerCorrectionIntent.parse("Nicht das – ich meine die vorige PDF"))
        assertEquals(Correction(Target.DRAFT, Which.YESTERDAY), OwnerCorrectionIntent.parse("Falsch, gemeint war der Entwurf von gestern"))
        listOf("Nein danke", "Ich meinte den letzten Entwurf", "Nein, das passt so", "Nein, ich meinte etwas anderes")
            .forEach { assertNull(it, OwnerCorrectionIntent.parse(it)) }
    }

    @Test fun theReferenceIsResolvedInTheRecordAndNeverPointsAtTheCorrectedTask() {
        val now = at(6, 12)
        val yesterday = Episode("y", Kind.DOCUMENT, "a", at(5, 15), at(5, 15), State.SAVED, "Gestern.pdf", draft = "D-gestern", documentUri = "content://y")
        val earlier = Episode("e", Kind.DOCUMENT, "b", at(6, 9), at(6, 9), State.DRAFTED, "Früh.pdf", draft = "D-früh")
        val current = Episode("c", Kind.DOCUMENT, "c", at(6, 11), at(6, 11), State.DRAFTING, "Falsch.pdf", draft = "D-falsch", documentUri = "content://c")
        val all = listOf(yesterday, earlier, current)
        assertEquals("e", OwnerCorrectionIntent.resolve(all, Correction(Target.DRAFT, Which.LAST), "c", now, vienna)?.id)
        assertEquals("y", OwnerCorrectionIntent.resolve(all, Correction(Target.DRAFT, Which.PREVIOUS), "c", now, vienna)?.id)
        assertEquals("y", OwnerCorrectionIntent.resolve(all, Correction(Target.DRAFT, Which.YESTERDAY), "c", now, vienna)?.id)
        assertEquals("only documents Android can reopen", "y",
            OwnerCorrectionIntent.resolve(all, Correction(Target.DOCUMENT, Which.LAST), "c", now, vienna)?.id)
        assertNull(OwnerCorrectionIntent.resolve(listOf(current), Correction(Target.DRAFT, Which.LAST), "c", now, vienna))
    }
}
