package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure admission test; device memory and thermal state must be measured, not invented. */
class LocalModelContextWindowTest {
    private val gib = 1024L * 1024L * 1024L

    private fun measured(total: Long = 12*gib, available: Long = 7*gib,
                         thermal: Int? = 0, low: Boolean = false) =
        LocalModelResourceSnapshot(total, available, low, thermal)

    @Test fun healthyTwelveGigabytePhoneCanUseAdvertisedEightKContext() {
        assertEquals(8_192, LocalModelContextWindow.choose(32_768, measured(), powerSave = false))
        assertEquals(6_144, LocalModelContextWindow.choose(32_768,
            measured(available=4*gib), powerSave=false))
    }

    @Test fun unknownCapacityOrLegacyModelCannotOverclaimContext() {
        assertEquals(4_096, LocalModelContextWindow.choose(0, measured(), powerSave=false))
        assertEquals(4_096, LocalModelContextWindow.choose(4_096, measured(), powerSave=false))
        assertEquals(2_048, LocalModelContextWindow.choose(2_048, measured(), powerSave=false))
    }

    @Test fun thermalPowerOrRamPressureNeverEnablesExtendedContext() {
        for (snap in listOf(
            measured(total=8*gib),
            measured(available=2*gib),
            measured(thermal=2),
            measured(thermal=3),
            measured(thermal=null),
            measured(low=true)
        )) assertEquals(4_096, LocalModelContextWindow.choose(32_768, snap, powerSave=false))
        assertEquals(4_096, LocalModelContextWindow.choose(32_768, measured(), powerSave=true))
        assertEquals(4_096, LocalModelContextWindow.choose(32_768, measured(), powerSave=null))
        assertEquals(4_096, LocalModelContextWindow.choose(32_768, null, powerSave=false))
    }

    @Test fun longerMeasuredWindowActuallyFreesOwnerPromptBytes() {
        val short = LocalNativePromptBudget.promptByteBudget(4096, 1800, 3072)!!
        val long = LocalNativePromptBudget.promptByteBudget(8192, 1800, 3072)!!
        assertTrue(long > short + 8000)
    }
}
