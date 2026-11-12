package at.heinz.deviceai.localmodel

import org.junit.Assert.*
import org.junit.Test

class LocalNativePromptBudgetTest {
    @Test fun explicitCapacityDoesNotIncreasePhoneContext() {
        assertEquals(4096, LocalNativePromptBudget.contextCapacity(0))
        assertEquals(4096, LocalNativePromptBudget.contextCapacity(32768))
        assertEquals(2048, LocalNativePromptBudget.contextCapacity(2048))
    }

    @Test fun zeroFreshKvDoesNotForgetNativeSystemAndTools() {
        assertFalse(LocalNativePromptBudget.allows(1800, 1900, 0, 1000, 4096, 1024, true))
        assertTrue(LocalNativePromptBudget.allows(500, 600, 0, 1000, 4096, 1024, true))
    }

    @Test fun pristinePrefilledPrefixIsNotChargedTwice() {
        assertTrue(LocalNativePromptBudget.allows(500, 600, 1500, 1500, 4096, 1024, true))
        assertFalse(LocalNativePromptBudget.allows(500, 600, 2500, 1500, 4096, 1024, false))
    }

    @Test fun fullRenderedMessageAndOutputReserveMatter() {
        assertFalse(LocalNativePromptBudget.allows(100, 2500, 500, 500, 4096, 1024, true))
        assertFalse(LocalNativePromptBudget.allows(100, 200, 0, 2000, 4096, 2048, true))
        assertTrue(LocalNativePromptBudget.allows(100, 200, 0, 1500, 4096, 2048, true))
    }

    @Test fun missingCountsAndInvalidContinuationAreRefused() {
        assertFalse(LocalNativePromptBudget.allows(100, null, 100, 100, 4096, 1024, true))
        assertFalse(LocalNativePromptBudget.allows(100, 100, null, 100, 4096, 1024, true))
        assertFalse(LocalNativePromptBudget.allows(100, 100, 100, null, 4096, 1024, true))
        assertFalse(LocalNativePromptBudget.allows(100, 100, 0, 100, 4096, 1024, false))
        assertFalse(LocalNativePromptBudget.allows(100, 100, 0, 100, 0, 1024, true))
    }

    @Test fun normalizedUnicodeExpansionIsIncluded() {
        val text = "㍍".repeat(100)
        assertTrue(LocalNativePromptBudget.renderedUtf8Estimate(text) > text.toByteArray(Charsets.UTF_8).size)
        assertEquals(100, LocalNativePromptBudget.renderedUtf8Estimate("a".repeat(100)))
    }

    @Test fun assemblyBudgetReservesNativePrefixAndOutputWithoutClippingTask() {
        assertEquals(1316, LocalNativePromptBudget.promptBytes(4096, 1500, 1024))
        assertEquals(0, LocalNativePromptBudget.promptBytes(4096, 3000, 2048))
        assertEquals(0, LocalNativePromptBudget.promptBytes(4096, null, 1024))
        assertFalse(LocalNativePromptBudget.allows(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
            Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, true))
    }
}
