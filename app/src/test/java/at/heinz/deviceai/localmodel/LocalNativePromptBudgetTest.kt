package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

class LocalNativePromptBudgetTest {
    @Test fun textOnlyBudgetReclaimsToolPrefaceWithoutClaimingMoreThan4096NativeTokens() {
        val toolBound = LocalNativePromptBudget.promptByteBudget(4096, 2000, 2048)!!
        val textOnly = LocalNativePromptBudget.toolFreePromptByteBudget(4096, 2048)!!
        assertTrue("Tool-free text input should have significantly more room", textOnly > toolBound * 2)
        assertTrue(textOnly > 0)
        assertTrue(textOnly <= LocalToolContinuation.MAX_INPUT_BYTES)
        assertEquals(null, LocalNativePromptBudget.toolFreePromptByteBudget(0, 2048))
    }

    @Test fun explicitCapacityDoesNotIncreasePhoneContext() {
        assertEquals(4096, LocalNativePromptBudget.contextCapacity(0))
        assertEquals(4096, LocalNativePromptBudget.contextCapacity(32768))
        assertEquals(2048, LocalNativePromptBudget.contextCapacity(2048))
    }

    @Test fun ordinaryMandatoryPromptReachesNativeFullInputValidation() {
        val plan = LocalNativePromptBudget.plan(3300, 9000, 0, 1800, 4096, 2048, true)!!
        assertTrue(plan.nativeFullInputCheckRequired)
        assertEquals(3000, plan.plannedInputTokens)
        assertEquals(840, plan.maxOutputTokens)
    }

    @Test fun prefixMeasurementIsNotAddedAgainToFullTemplateEstimate() {
        val plan = LocalNativePromptBudget.plan(1500, 6000, 0, 1500, 4096, 2048, true)!!
        assertEquals(2000, plan.plannedInputTokens)
        assertEquals(1840, plan.maxOutputTokens)
        assertNull(LocalNativePromptBudget.plan(1500, 6000, 1500, 1500, 4096, 2048, true))
    }

    @Test fun estimateCannotCertifyOrRejectNativeInputFit() {
        val plan = LocalNativePromptBudget.plan(15500, 24000, 0, 1800, 4096, 2048, true)!!
        assertTrue(plan.plannedInputTokens > 4096)
        assertTrue(plan.nativeFullInputCheckRequired)
        assertEquals(128, plan.maxOutputTokens)
    }

    @Test fun actualPrefixAndRequestedOutputStillConstrainThePlan() {
        val plan = LocalNativePromptBudget.plan(100, 200, 0, 3000, 4096, 2048, true)!!
        assertEquals(806, plan.maxOutputTokens)
        assertEquals(64, LocalNativePromptBudget.plan(100, 200, 0, 100, 4096, 64, true)!!.maxOutputTokens)
        assertNull(LocalNativePromptBudget.plan(100, 200, 0, 4096, 4096, 2048, true))
    }

    @Test fun continuationFitsEntireRenderedBytesAndNativeKv() {
        val plan = LocalNativePromptBudget.plan(100, 300, 3000, 1800, 4096, 2048, false)!!
        assertFalse(plan.nativeFullInputCheckRequired)
        assertEquals(300, plan.plannedInputTokens)
        assertEquals(540, plan.maxOutputTokens)
        assertTrue(3000 + 300 + 256 + plan.maxOutputTokens <= 4096)
        assertNull(LocalNativePromptBudget.plan(100, 800, 3000, 1800, 4096, 2048, false))
        assertNull(LocalNativePromptBudget.plan(100, 200, 4000, 1800, 4096, 2048, false))
    }

    @Test fun missingCountsAndInvalidContinuationAreRefused() {
        assertNull(LocalNativePromptBudget.plan(100, null, 0, 100, 4096, 1024, true))
        assertNull(LocalNativePromptBudget.plan(100, 100, null, 100, 4096, 1024, true))
        assertNull(LocalNativePromptBudget.plan(100, 100, 0, null, 4096, 1024, true))
        assertNull(LocalNativePromptBudget.plan(100, 100, 0, 100, 4096, 1024, false))
        assertNull(LocalNativePromptBudget.plan(100, 100, 0, 100, 0, 1024, true))
        assertNull(LocalNativePromptBudget.plan(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, true))
    }

    @Test fun normalizedUnicodeExpansionIsIncluded() {
        val text = "㍍".repeat(100)
        assertTrue(LocalNativePromptBudget.renderedUtf8Estimate(text) > text.toByteArray(Charsets.UTF_8).size)
        assertEquals(100, LocalNativePromptBudget.renderedUtf8Estimate("a".repeat(100)))
    }

    @Test fun nativeFullOrUncountableContextCannotClaimCompletion() {
        assertTrue(LocalNativePromptBudget.completeContext(4095, 4096))
        assertFalse(LocalNativePromptBudget.completeContext(4096, 4096))
        assertFalse(LocalNativePromptBudget.completeContext(5000, 4096))
        assertFalse(LocalNativePromptBudget.completeContext(null, 4096))
        assertTrue(LocalNativePromptBudget.contextFailure("Input token ids are too long. Exceeding the maximum number of tokens allowed: 5000 >= 4096"))
        assertFalse(LocalNativePromptBudget.contextFailure("Native engine unavailable"))
    }
}
