package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelGenerationPolicyTest {
    @Test fun toolFreeAnswersMayRequestLongerGemmaOutputWithoutChangingDeviceTaskLimit() {
        assertEquals(3_072, LocalModelGenerationPolicy.maxTextOnlyOutputTokens("gemma-4-e4b-it"))
        assertEquals(1_024, LocalModelGenerationPolicy.maxTextOnlyOutputTokens("qwen3-coder"))
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens("gemma-4-e4b-it"))
    }

    @Test fun gemma4E4bOnDeviceCatalogHasExtendedButBoundedBudget() {
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens("gemma4-e4b-it"))
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens("gemma-4-E4B-it"))
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens("gemma-4-E2B-it"))
    }

    @Test fun unknownAndNonGemma4FamiliesKeepConservativeBudget() {
        for (modelId in listOf(null, "", "Qwen3-0.6B", "qwen3-coder", "gemma-3-1b", "other-e4b")) {
            assertEquals(1_024, LocalModelGenerationPolicy.maxOutputTokens(modelId))
        }
    }

    @Test fun machineLocaleDoesNotChangeFamilyDetection() {
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens("GEMMA-4-E4B-IT"))
    }

    @Test fun longerTextBudgetUsesOnlyTheLoadedWindow() {
        val id = "gemma-4-e4b-it"
        assertEquals(1_536, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(id, 2_048))
        assertEquals(3_072, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(id, 4_096))
        assertEquals(4_608, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(id, 6_144))
        assertEquals(6_144, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(id, 8_192))
        for (invalid in listOf(-1, 0, 8_193, Int.MAX_VALUE)) {
            assertEquals(3_072, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(id, invalid))
        }
        assertEquals(1_024, LocalModelGenerationPolicy.maxTextOnlyOutputTokens("qwen3-coder", 8_192))
        assertEquals(1_024, LocalModelGenerationPolicy.maxTextOnlyOutputTokens(null, 8_192))
        assertEquals(2_048, LocalModelGenerationPolicy.maxOutputTokens(id))
    }

    @Test fun onlyMeasuredNativeTokenLimitsAreClassified() {
        assertFalse(LocalModelGenerationPolicy.outputLimitReached(3_071, 3_072, 8_192))
        assertTrue(LocalModelGenerationPolicy.outputLimitReached(3_072, 3_072, 8_192))
        assertTrue(LocalModelGenerationPolicy.outputLimitReached(3_073, 3_072, 8_192))
        for (invalid in listOf(null, -1, 0, 8_193, Int.MAX_VALUE)) {
            assertFalse(LocalModelGenerationPolicy.outputLimitReached(invalid, 3_072, 8_192))
        }
        assertFalse(LocalModelGenerationPolicy.outputLimitReached(3_072, 0, 8_192))
        assertFalse(LocalModelGenerationPolicy.outputLimitReached(3_072, 3_072, 0))
        assertFalse(LocalModelGenerationPolicy.isOutputLimit("native runtime failure"))
    }
}
