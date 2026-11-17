package at.heinz.deviceai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Tool output cap 3900: keeps the JSON-escaped observation below the 8000-char agent limit. */
class ToolOutputCapTest {
    @Test fun shortOutputIsUnchanged() {
        assertEquals("ok", ToolOutputCap.cap("ok"))
        val exact = "a".repeat(ToolOutputCap.MAX_CHARS)
        assertSame(exact, ToolOutputCap.cap(exact))
    }

    @Test fun longOutputIsCutWithAClearMarkerAndStaysWithinTheCap() {
        val out = ToolOutputCap.cap("x".repeat(20_000))
        assertTrue(out.length <= ToolOutputCap.MAX_CHARS)
        assertTrue(out, out.endsWith(ToolOutputCap.marker(20_000)))
        assertTrue(out.contains("gekürzt"))
    }

    @Test fun neverSplitsASurrogatePair() {
        val emoji = "\uD83D\uDE00" // 😀
        for (pad in 0..3) {
            val out = ToolOutputCap.cap("a".repeat(pad) + emoji.repeat(5_000))
            val body = out.removeSuffix(ToolOutputCap.marker(pad + 10_000))
            assertFalse("dangling high surrogate", body.isNotEmpty() && Character.isHighSurrogate(body.last()))
            assertEquals(out.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8), out)
        }
    }

    @Test fun worstCaseJsonEscapingStaysBelowTheAgentLimit() {
        // Control chars escape to \\u00XX (6 chars each): 3900 raw -> far beyond 8000 escaped.
        val out = ToolOutputCap.cap("\u0001".repeat(50_000))
        val escaped = JSONObject().put("output", out).toString()
        assertTrue("escaped=${escaped.length}", escaped.length < 8_000)
    }
}
