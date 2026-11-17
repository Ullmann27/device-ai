package at.heinz.deviceai.avatar3d

import org.junit.Test

class AvatarCycleBoundaryTest {
    @Test fun positiveCycleBoundaries() = AvatarCycleBoundaryCases.positiveCycleBoundaries()
    @Test fun negativeZero() = AvatarCycleBoundaryCases.negativeZero()
    @Test fun negativeCycleBoundaries() = AvatarCycleBoundaryCases.negativeCycleBoundaries()
    @Test fun nearZeroValuesRemainFiniteAndBounded() = AvatarCycleBoundaryCases.nearZeroValuesRemainFiniteAndBounded()
    @Test fun boundariesAfterEarlierFramesRemainDeterministic() = AvatarCycleBoundaryCases.boundariesAfterEarlierFramesRemainDeterministic()
}
