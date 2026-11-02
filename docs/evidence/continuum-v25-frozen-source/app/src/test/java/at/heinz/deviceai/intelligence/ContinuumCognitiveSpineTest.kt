package at.heinz.deviceai.intelligence

import org.junit.Test

/** Android Gradle/JUnit entrypoint for the portable decision tests. */
class ContinuumCognitiveSpineTest {
    @Test fun failClosedEvidenceOwnerAndCodingDecisions() {
        ContinuumCognitiveSpineChecks.main(emptyArray())
    }
}
