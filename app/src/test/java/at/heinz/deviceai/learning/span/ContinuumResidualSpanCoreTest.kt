package at.heinz.deviceai.learning.span

import org.junit.Test

/** Gradle/JUnit entrypoint reusing the real offline JVM checks without Android or network. */
class ContinuumResidualSpanCoreTest {
    @Test
    fun residualPointerGradientsCheckpointsCounterfactualsAndTrainingExecute() {
        ContinuumResidualSpanCoreChecks.main(emptyArray())
    }
}
