package at.heinz.deviceai.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drive flag read by the background announcer must end by itself; speaking is device-only (NOT_RUN). */
class DriveBackgroundAnnouncerRuleTest {
    private val start = 1_800_000_000_000L

    @Test fun aDriveFlagIsValidOnlyWithinItsBoundedWindow() {
        assertTrue(DriveBackgroundAnnouncer.driveStillValid(start, start))
        assertTrue(DriveBackgroundAnnouncer.driveStillValid(start, start + DriveBackgroundAnnouncer.MAX_DRIVE_MS - 1))
        assertFalse(DriveBackgroundAnnouncer.driveStillValid(start, start + DriveBackgroundAnnouncer.MAX_DRIVE_MS))
    }

    @Test fun aMissingStartOrAClockThatWentBackwardsNeverKeepsAnnouncing() {
        assertFalse(DriveBackgroundAnnouncer.driveStillValid(0L, start))
        assertFalse(DriveBackgroundAnnouncer.driveStillValid(-5L, start))
        assertFalse(DriveBackgroundAnnouncer.driveStillValid(start, start - 1))
    }
}
