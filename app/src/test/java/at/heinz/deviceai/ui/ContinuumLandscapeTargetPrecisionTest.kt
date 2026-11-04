package at.heinz.deviceai.ui

import org.junit.Test

class ContinuumLandscapeTargetPrecisionTest {
    @Test fun fractionalTargetsRemainAtLeast48Dp() = ContinuumLandscapeTargetPrecisionChecks.strictRegression()
    @Test fun legacyWindowsRetainStrictGeometry() = ContinuumLandscapeTargetPrecisionChecks.legacyMatrix()
    @Test fun avatarBoundsRemainBitIdentical() = ContinuumLandscapeTargetPrecisionChecks.unchangedHeroBounds()
}
