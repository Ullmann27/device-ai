package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumMobileBuilderRouteTest {
    @Test fun mainAppOpensOnlyTheKnownPreviewBuilder() {
        val destination = ContinuumMobileBuilderRoute.destination("at.heinz.deviceai")
        assertEquals("at.heinz.deviceai.preview", destination.packageName)
        assertEquals("at.heinz.deviceai.ContinuumMobileBuilderActivity", destination.className)
        assertTrue(destination.externalPackage)
    }

    @Test fun installedPreviewOpensItsOwnExistingBuilder() {
        val destination = ContinuumMobileBuilderRoute.destination("at.heinz.deviceai.preview")
        assertEquals("at.heinz.deviceai.preview", destination.packageName)
        assertFalse(destination.externalPackage)
    }

    @Test fun mentorPreviewOpensItsOwnExistingBuilder() {
        val destination = ContinuumMobileBuilderRoute.destination("at.heinz.deviceai.mentorpaired")
        assertEquals("at.heinz.deviceai.mentorpaired", destination.packageName)
        assertFalse(destination.externalPackage)
    }

    @Test fun similarOrUntrustedApplicationIdsNeverRedirectToOtherPackages() {
        for (untrusted in listOf(
            "at.heinz.deviceai.preview.evil",
            "at.heinz.deviceai.mentorpaired.extra",
            "org.third.party",
            ""
        )) {
            val destination = ContinuumMobileBuilderRoute.destination(untrusted)
            assertEquals(ContinuumMobileBuilderRoute.PREVIEW_PACKAGE, destination.packageName)
            assertEquals(ContinuumMobileBuilderRoute.BUILDER_ACTIVITY, destination.className)
            assertTrue(destination.externalPackage)
        }
    }
}
