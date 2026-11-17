package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.LocalChatPromptBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code56 field P0: prompts must fit the measured native window, not only the 15 500-character frame. */
class LocalNativePromptByteBudgetTest {
    @Test fun fieldSizedPrefaceLeavesFarLessThanTheOldCharacterFrame() {
        // Host measurement: 33 tool declarations ≈ 7 043 JSON bytes ≈ 2 348 tokens at the planner's 3 bytes/token.
        val budget = LocalNativePromptBudget.promptByteBudget(4_096, 2_348, 2_048)!!
        assertEquals((4_096 - 2_348 - 512 - LocalNativePromptBudget.FRAMING_RESERVE) * 3, budget)
        assertTrue("old frame allowed ${LocalChatPromptBudget.FULL_CHARACTERS}", budget < LocalChatPromptBudget.FULL_CHARACTERS / 3)
    }

    @Test fun aPromptAtTheBudgetIsAdmittedByThePlannerWithAUsableAnswer() {
        for (preface in listOf(0, 800, 2_348, 3_000)) {
            val budget = LocalNativePromptBudget.promptByteBudget(4_096, preface, 2_048)!!
            if (budget == 0) continue
            val plan = assertNotNullPlan(budget, preface)
            assertTrue("answer room for preface $preface: ${plan.maxOutputTokens}", plan.maxOutputTokens >= 512)
            assertTrue(plan.plannedInputTokens + plan.maxOutputTokens + LocalNativePromptBudget.FRAMING_RESERVE <= 4_096)
        }
    }

    private fun assertNotNullPlan(bytes: Int, preface: Int): LocalNativePromptBudget.Plan {
        val payload = minOf(bytes, LocalToolContinuation.MAX_INPUT_BYTES)
        val plan = LocalNativePromptBudget.plan(payload, payload, 0, preface, 4_096, 2_048, fresh = true)
        assertNotNull(plan)
        return plan!!
    }

    @Test fun unknownOrFullWindowsAreHonest() {
        assertNull(LocalNativePromptBudget.promptByteBudget(4_096, null, 1_024))
        assertNull(LocalNativePromptBudget.promptByteBudget(0, 100, 1_024))
        assertEquals(0, LocalNativePromptBudget.promptByteBudget(4_096, 3_500, 1_024))
        assertEquals("small model output limit reserves only what it can use",
            (4_096 - 1_000 - 200 - LocalNativePromptBudget.FRAMING_RESERVE) * 3,
            LocalNativePromptBudget.promptByteBudget(4_096, 1_000, 200))
    }

    @Test fun frameUsesTheSmallerBudgetAndKeepsTheOldOneWhenUnknown() {
        assertEquals(15_500, LocalChatPromptBudget.withNativeBudget(15_500, null))
        assertEquals(3_960, LocalChatPromptBudget.withNativeBudget(15_500, 3_960))
        assertEquals(6_000, LocalChatPromptBudget.withNativeBudget(6_000, 9_000))
        assertEquals("a full window still yields a valid frame size that refuses mandatory text", 1,
            LocalChatPromptBudget.withNativeBudget(15_500, 0))
    }
}
