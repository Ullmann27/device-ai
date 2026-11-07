package at.heinz.deviceai.cognitive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEMONSTRATION WITH A SCRIPTED FAKE MODEL — NOT a benchmark and NOT evidence of real model quality or latency.
 * It only shows that a well-formed fake chain yields a reviewable proposal whose execution status is NOT EXECUTED.
 */
class DevelopmentFakeModelDemonstrationTest {
    @Test
    fun scriptedFakeModelProducesReviewableProposal() {
        val src = SourceSnapshot.of("app/Counter.kt", "fun next(n: Int) = n\n")
        val task = DevelopmentTask("demo-1", "Counter.next should increment", listOf(src))
        val replies = ArrayDeque(listOf(
            """{"summary":"Increment n","edits":[{"path":"app/Counter.kt","expectedBaseSha256":"${src.sha256}","replacement":"fun next(n: Int) = n + 1\n","rationale":"bug"}]}""",
            """{"verdict":"ACCEPT","findings":[{"severity":"INFO","message":"Overflow at Int.MAX_VALUE not handled"}]}""",
            """{"tests":[{"name":"nextIncrements","description":"next(1)==2"}]}""",
            """{"decision":"RECOMMEND","rationale":"Minimal change"}"""
        ))
        val outcome = DevelopmentCoordinator({ ReasoningPassResult("scripted-fake@0", replies.removeFirst(), 0) }, {}).run(task)
        assertEquals(DevelopmentJobState.COMPLETED, outcome.state)
        val text = outcome.proposal!!.render()
        println(text)
        assertTrue(text.contains("+fun next(n: Int) = n + 1"))
        assertTrue(text.contains("Generated tests executed"))
    }
}
