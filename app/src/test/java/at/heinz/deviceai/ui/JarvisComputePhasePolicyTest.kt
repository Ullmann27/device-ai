package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisComputePhasePolicyTest {
    private val high=JarvisRenderQuality.Budget(
        JarvisRenderQuality.Tier.HIGH,16L,70,96,true)
    private val low=JarvisRenderQuality.Budget(
        JarvisRenderQuality.Tier.LOW,80L,12,40,false)

    @Test fun idleAndThinkingOnlyReduceBaseLoad() {
        val idle=JarvisComputePhasePolicy.apply(high,JarvisComputePhasePolicy.Phase.IDLE)
        assertTrue(idle.frameDelayMillis>=high.frameDelayMillis)
        assertTrue(idle.particleCount<=high.particleCount)
        assertTrue(idle.orbitSegments<=high.orbitSegments)

        val thinking=JarvisComputePhasePolicy.apply(high,JarvisComputePhasePolicy.Phase.THINKING)
        assertTrue(thinking.frameDelayMillis>=42L)
        assertTrue(thinking.particleCount<=16)
        assertTrue(thinking.orbitSegments<=48)
        assertTrue(thinking.animate)
    }

    @Test fun lowBudgetRemainsAuthoritativeAndIsNeverUpgraded() {
        JarvisComputePhasePolicy.Phase.entries.forEach { phase ->
            assertEquals(low,JarvisComputePhasePolicy.apply(low,phase))
            assertFalse(JarvisComputePhasePolicy.apply(low,phase).animate)
        }
    }

    @Test fun speakingKeepsHighBaseWhileResearchIsMoreConservative() {
        assertEquals(high,JarvisComputePhasePolicy.apply(high,JarvisComputePhasePolicy.Phase.SPEAKING))
        val research=JarvisComputePhasePolicy.apply(high,JarvisComputePhasePolicy.Phase.RESEARCH)
        assertEquals(50L,research.frameDelayMillis)
        assertTrue(research.particleCount<=12)
        assertTrue(research.orbitSegments<=40)
    }

    @Test fun diagnosticAvatarProofBypassesPhaseShaping() {
        val shaped=JarvisComputePhasePolicy.apply(
            high,JarvisComputePhasePolicy.Phase.THINKING,diagnosticProofActive=true)
        assertEquals(high,shaped)
    }
}
