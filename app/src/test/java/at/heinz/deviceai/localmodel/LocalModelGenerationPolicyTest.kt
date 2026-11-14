package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelGenerationPolicyTest {
    @Test fun gemma4E4bOnDeviceCatalogHasExtendedButBoundedBudget() {
        assertEquals(3_072, LocalModelGenerationPolicy.maxOutputTokens("gemma4-e4b-it"))
        assertEquals(3_072, LocalModelGenerationPolicy.maxOutputTokens("gemma-4-E4B-it"))
        assertEquals(3_072, LocalModelGenerationPolicy.maxOutputTokens("gemma-4-E2B-it"))
    }

    @Test fun unknownAndNonGemma4FamiliesKeepConservativeBudget() {
        for (modelId in listOf(null, "", "Qwen3-0.6B", "qwen3-coder", "gemma-3-1b", "other-e4b")) {
            assertEquals(1_024, LocalModelGenerationPolicy.maxOutputTokens(modelId))
        }
    }

    @Test fun machineLocaleDoesNotChangeFamilyDetection() {
        assertEquals(3_072, LocalModelGenerationPolicy.maxOutputTokens("GEMMA-4-E4B-IT"))
    }

    @Test fun gemma4ExtendedContextCanReserveAThreeThousandTokenReply() {
        assertEquals(3_072, LocalModelGenerationPolicy.maxOutputTokens("gemma4-e4b-it"))
    }

}
