package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class OnlineNaiveBayesTest {
    @Test fun learnsApprovedExamplesGeneralizesAndRestoresWithoutTrainingOnTest() {
        val model = OnlineNaiveBayes()
        val training = listOf("energie status" to "energy", "energie stand" to "energy",
            "dateien übersicht" to "files", "dateien liste" to "files")
        assertNull(model.predict("energie übersicht"))
        training.forEach { (text, label) -> model.learn(text, label, approved = true) }
        val before = model.save()
        assertEquals("energy", model.predict("energie übersicht")?.label)
        assertEquals("files", model.predict("dateien stand")?.label)
        assertNull(model.predict("quantenphysik"))
        assertEquals(2, model.evaluate(listOf("energie übersicht" to "energy", "dateien stand" to "files")).correct)
        assertArrayEquals(before, model.save())
        val restored = OnlineNaiveBayes.restore(before)
        assertEquals(model.predict("energie übersicht"), restored.predict("energie übersicht"))
        restored.reset()
        assertNull(restored.predict("energie übersicht"))
        assertThrows(IllegalArgumentException::class.java) { model.learn("energie", "energy", approved = false) }
    }
    @Test fun boundsAndBadSnapshotsAreRejected() {
        val model = OnlineNaiveBayes()
        assertThrows(IllegalArgumentException::class.java) { model.learn("a".repeat(321), "x", true) }
        assertThrows(IllegalArgumentException::class.java) { OnlineNaiveBayes.restore(byteArrayOf(1,2,3)) }
        repeat(256) { model.learn("example", "x", true) }
        assertThrows(IllegalArgumentException::class.java) { model.learn("example", "x", true) }
    }
}
