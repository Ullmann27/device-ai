package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy
import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Action
import at.heinz.deviceai.research.exp20.BoundedCtrnn
import at.heinz.deviceai.research.exp20.LeakyIntegrators
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Post-holdout audit (added after review; the sealed P2 tests and results are unchanged).
 * 1. The earlier invariant only showed that the clarify flag never ADDS a TOOL choice. Here the full feasibility mask is
 *    checked on the same 7,680 signal combinations: TOOL is feasible exactly when toolIntent is set, and with
 *    unresolvedReference=true the real policy never chooses TOOL at all.
 * 2. The recurrence ablation W = 0 of arm C is bit-identical to arm B, so the P2 comparison C vs B is that ablation.
 */
class P2SafetyAuditTest {
    private fun combos(): List<ComputeAllocationPolicy.Signals> {
        val out = ArrayList<ComputeAllocationPolicy.Signals>()
        for (ev in ComputeAllocationPolicy.EvidenceDecision.values()) for (cov in listOf(0.0, 0.6, 1.0)) for (flags in listOf(0, 1))
        for (ref in listOf(false, true)) for (tool in listOf(false, true)) for (personal in listOf(false, true)) for (ready in listOf(false, true))
        for (net in listOf(false, true)) for (low in listOf(false, true)) for (hot in listOf(false, true)) for (calls in listOf(0, 1, 2, 4, 5))
            out += ComputeAllocationPolicy.Signals(ev, cov, flags, ref, false, tool, personal, ready, net, low, hot, calls)
        return out
    }

    @Test fun toolIsFeasibleExactlyWithToolIntentAndNeverChosenWhileAReferenceIsUnresolved() {
        val policy = ComputeAllocationPolicy()
        val all = combos()
        assertEquals(2 * 7_680, all.size)                       // 7,680 per value of unresolvedReference
        val withRef = HashMap<Action, Int>()
        for (s in all) {
            assertEquals("TOOL feasibility for $s", s.toolIntent, policy.feasible(Action.TOOL, s))
            val a = policy.choose(s).action
            if (a == Action.TOOL) assertTrue("TOOL without toolIntent: $s", s.toolIntent)
            if (s.unresolvedReference) {
                assertFalse("TOOL chosen with an unresolved reference: $s", a == Action.TOOL)
                assertFalse("model call with an unresolved reference: $s", a.name.startsWith("MODEL_"))
                withRef.merge(a, 1, Int::plus)
            }
        }
        println("P2SafetyAudit actions with unresolvedReference=true over 7680 combos: $withRef")
        assertEquals(7_680, withRef.values.sum())
    }

    /** The 17 inherited TOOL choices of arm A in holdout S6 all come from this sentence: classifier toolIntent, no presence concept. */
    @Test fun inheritedToolCaseIsTheClassifierToolIntentAndTheClarifyFlagRemovesIt() {
        val text = "Öffne die App und mach das Tor zu."
        assertTrue(RouterBridge.signals(text, false).toolIntent)
        assertEquals(Action.TOOL, RouterBridge.action(text, false))
        assertEquals(Action.CLARIFY, RouterBridge.action(text, true))
    }

    @Test fun ctrnnWithZeroCouplingIsBitIdenticalToTheLeakyBaseline() {
        val taus = P2Config.taus(); val n = taus.size
        val c = BoundedCtrnn(taus, Array(n) { DoubleArray(n) }, DoubleArray(n)); val b = LeakyIntegrators(taus)
        val r = Random(11)
        repeat(20_000) {
            val u = DoubleArray(n) { r.nextDouble(-3.0, 3.0) }; val dt = kotlin.math.exp(r.nextDouble(-8.0, 10.0))
            assertTrue(c.step(u, dt)); assertTrue(b.step(u, dt))
            assertArrayEquals(b.state(), c.state(), 0.0)
        }
    }
}
