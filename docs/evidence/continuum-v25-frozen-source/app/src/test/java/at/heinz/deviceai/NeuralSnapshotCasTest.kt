package at.heinz.deviceai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuralSnapshotCasTest {
    private val a = byteArrayOf(1, 2, 3)

    @Test fun savesOnlyOverTheStateTheRunStartedFrom() {
        assertTrue("first save: nothing stored, nothing expected", neuralSnapshotUnchanged(null, null))
        assertTrue(neuralSnapshotUnchanged(a, a.copyOf()))
    }

    @Test fun aSnapshotWrittenMeanwhileIsNeverOverwritten() {
        // background practice committed b while the foreground experiment trained from a
        assertFalse(neuralSnapshotUnchanged(byteArrayOf(9, 9), a))
        // a snapshot appeared although the run started without one
        assertFalse(neuralSnapshotUnchanged(a, null))
        // the snapshot the run started from was removed
        assertFalse(neuralSnapshotUnchanged(null, a))
    }
}
