package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecoveryPolicyTest {
    @Test fun escalatesWarmWarmColdThenGivesUp() {
        val policy = SpeechRecoveryPolicy()
        assertEquals(SpeechRecoveryPolicy.Action.RetryWarm(250L), policy.failure())
        assertEquals(SpeechRecoveryPolicy.Action.RetryWarm(700L), policy.failure())
        assertFalse(policy.coldRestartUsed)
        assertEquals(SpeechRecoveryPolicy.Action.RetryCold(1_200L), policy.failure())
        assertTrue(policy.coldRestartUsed)
        assertEquals(SpeechRecoveryPolicy.Action.GiveUp, policy.failure())
        assertEquals(SpeechRecoveryPolicy.Action.GiveUp, policy.failure())
        assertEquals(3, policy.attemptsUsed)
    }

    @Test fun totalWaitingTimeIsBounded() {
        val policy = SpeechRecoveryPolicy(warmDelaysMs = listOf(3_000L, 3_000L), coldDelayMs = 3_000L)
        assertEquals(SpeechRecoveryPolicy.Action.RetryWarm(3_000L), policy.failure())
        // 3000 + 3000 would exceed MAX_TOTAL_DELAY_MS: give up instead of looping.
        assertEquals(SpeechRecoveryPolicy.Action.GiveUp, policy.failure())
        assertEquals(1, policy.attemptsUsed)
    }

    @Test fun neverMoreThanMaxAttemptsEvenWithManyWarmDelays() {
        val policy = SpeechRecoveryPolicy(warmDelaysMs = listOf(10L, 10L, 10L, 10L, 10L), coldDelayMs = 10L)
        var retries = 0
        while (policy.failure() != SpeechRecoveryPolicy.Action.GiveUp) retries++
        assertEquals(SpeechRecoveryPolicy.MAX_ATTEMPTS, retries)
    }

    @Test fun coldRestartIsUsedAtMostOnce() {
        val policy = SpeechRecoveryPolicy(warmDelaysMs = emptyList(), coldDelayMs = 100L)
        assertEquals(SpeechRecoveryPolicy.Action.RetryCold(100L), policy.failure())
        assertEquals(SpeechRecoveryPolicy.Action.GiveUp, policy.failure())
    }
}
