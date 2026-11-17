package at.heinz.deviceai.localmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5 milestone 6: the live runLocal AgentModel attributed every step of a turn to
 * the model captured at turn start ("litertlm:id@version") without the identity
 * re-check that the unused ContinuumLocalAgentModel adapter enforces. If the owner
 * swaps the model while a tool approval dialog is open, later steps ran on another
 * model under the old provenance.
 */
class LocalTurnModelBindingTest {
    private fun model(id: String, version: String = "1", sha: Char = 'a') = ImportedLocalModel(id, version,
        sha.toString().repeat(64), 1_000L,
        LocalModelCapabilities(true, false, false, false, true, 8_192, null, setOf(LocalModelBackend.CPU)))

    @Test fun sameLoadedModelStaysBound() {
        assertTrue(LocalTurnModelBinding.stillBound(model("gemma"), model("gemma")))
    }

    @Test fun unloadedOrSwappedModelBreaksTheTurn() {
        assertFalse(LocalTurnModelBinding.stillBound(model("gemma"), null))
        assertFalse(LocalTurnModelBinding.stillBound(model("gemma"), model("qwen")))
        assertFalse(LocalTurnModelBinding.stillBound(model("gemma"), model("gemma", version = "2")))
        assertFalse("same id/version but different weights", LocalTurnModelBinding.stillBound(model("gemma"), model("gemma", sha = 'b')))
    }
}
