package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.LocalGenerationCapture
import at.heinz.deviceai.cognitive.LocalGenerationCompletion
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class LocalModelResponseBoundaryTest {
    @Test fun overrunKeepsEveryRemainingCharacterBeforeOutputLimit() {
        val capture = LocalGenerationCapture()
        capture.append("x".repeat(63_900), 0)
        val prefix = LocalModelGenerationPolicy.responsePrefix("z".repeat(300), 100)
        val queue = LocalInferenceDelivery()
        queue.enqueue { capture.append(prefix, 1) }
        queue.enqueue { capture.fail(LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT) }
        queue.drain()

        val result = capture.snapshot()
        assertEquals("x".repeat(63_900) + "z".repeat(100), result.response)
        assertEquals(LocalGenerationCompletion.OUTPUT_LIMIT, result.completion)
        assertFalse(result.responseComplete)
        assertEquals(result, capture.complete())
    }

    @Test fun oversizedFirstChunkRetainsTheWholeBoundedPrefix() {
        val text = "x".repeat(64_000) + "discarded"
        val prefix = LocalModelGenerationPolicy.responsePrefix(text, LocalModelGenerationPolicy.MAX_RESPONSE_CHARACTERS)
        assertEquals(text.take(64_000), prefix)
    }

    @Test fun truncationDoesNotSplitAnEmoji() {
        val prefix = LocalModelGenerationPolicy.responsePrefix("x".repeat(99) + "🙂" + "later", 100)
        assertEquals("x".repeat(99), prefix)
        assertEquals(prefix, prefix.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
        assertEquals("", LocalModelGenerationPolicy.responsePrefix("🙂", 1))
    }

    @Test fun completeEmojiAndWhitespaceAtTheBoundarySurvive() {
        val text = "x".repeat(95) + "\n\t 🙂"
        assertEquals(100, text.length)
        assertEquals(text, LocalModelGenerationPolicy.responsePrefix(text, 100))
        assertEquals(text, LocalModelGenerationPolicy.responsePrefix(text + "extra", 100))
        assertEquals("\n ", LocalModelGenerationPolicy.responsePrefix("\n \t", 2))
    }

    @Test fun exhaustedBudgetDoesNotProduceAnEmptyTextEvent() {
        assertEquals("", LocalModelGenerationPolicy.responsePrefix("late", 0))
        assertEquals("", LocalModelGenerationPolicy.responsePrefix("late", -1))
        assertEquals("", LocalModelGenerationPolicy.responsePrefix("", 1))
    }

    @Test fun stopBeforeDrainSuppressesQueuedBoundaryText() {
        val capture = LocalGenerationCapture()
        capture.append("Kept", 0)
        val cancelled = AtomicBoolean(false)
        val queue = LocalInferenceDelivery()
        val prefix = LocalModelGenerationPolicy.responsePrefix("boundary", 4)
        queue.enqueue { if (!cancelled.get()) capture.append(prefix, 1) }
        queue.enqueue {
            if (cancelled.get()) capture.cancel()
            else capture.fail(LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT)
        }
        cancelled.set(true)
        queue.drain()

        assertEquals("Kept", capture.snapshot().response)
        assertEquals(LocalGenerationCompletion.CANCELLED, capture.snapshot().completion)
    }

    @Test fun reentrantStopKeepsAcceptedPrefixAndWinsOverOutputFailure() {
        val capture = LocalGenerationCapture()
        val cancelled = AtomicBoolean(false)
        val queue = LocalInferenceDelivery()
        val prefix = LocalModelGenerationPolicy.responsePrefix("x".repeat(64_001), 64_000)
        queue.enqueue {
            if (!cancelled.get()) {
                capture.append(prefix, 1)
                cancelled.set(true)
                queue.drain()
            }
        }
        queue.enqueue {
            if (cancelled.get()) capture.cancel()
            else capture.fail(LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT)
        }
        queue.drain()

        val result = capture.snapshot()
        assertEquals(prefix, result.response)
        assertEquals(LocalGenerationCompletion.CANCELLED, result.completion)
        assertEquals(result, capture.append("late", 2))
        assertEquals(result, capture.fail(LocalModelGenerationPolicy.OUTPUT_CHARACTER_LIMIT))
    }
}
