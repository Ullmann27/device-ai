package at.heinz.deviceai.cognitive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class LocalLongAnswerFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun longStreamKeepsItsEndingAfterRestart() {
        val ending = "\nWichtige Einschränkung: NICHT AUSFÜHREN. 🙂"
        val answer = "ä".repeat(48_000 - ending.length) + ending
        val capture = LocalGenerationCapture()
        answer.chunked(160).forEachIndexed { index, chunk ->
            capture.append(chunk, index * 10_000L)
            assertFalse(capture.poll(index * 10_000L + 100).terminal)
        }
        val result = capture.complete()
        assertTrue(result.responseComplete)
        assertEquals(answer, result.response)
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        LocalChatTranscriptStore(dir).append(id, false, result.response, 1)
        assertEquals(answer, LocalChatTranscriptStore(dir).load(id).single().text)
    }

    @Test fun fullNativeSizedReplySurvivesPersistence() {
        val answer = "x".repeat(63_998) + "🙂"
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        LocalChatTranscriptStore(dir).append(id, false, answer, 1)
        assertEquals(answer, LocalChatTranscriptStore(dir).load(id).single().text)
    }

    @Test fun nativeTokenLimitCannotBecomeACompleteAnswer() {
        val capture = LocalGenerationCapture()
        capture.append("Der erhaltene Antwortanfang bleibt sichtbar.", 20)
        val result = capture.fail("LOCAL_MODEL_OUTPUT_TOKEN_LIMIT")
        assertEquals(LocalGenerationCompletion.OUTPUT_LIMIT, result.completion)
        assertFalse(result.responseComplete)
        assertEquals(result, capture.complete())
        assertEquals(result, capture.append("Späte Fortsetzung", 21))
        assertEquals("Der erhaltene Antwortanfang bleibt sichtbar.", result.response)
    }

    @Test fun ownerStopStillControlsALongStream() {
        val capture = LocalGenerationCapture()
        val text = "x".repeat(30_000)
        capture.append(text, 10)
        val stopped = capture.cancel("Owner STOP")
        assertEquals(LocalGenerationCompletion.CANCELLED, stopped.completion)
        assertEquals(text, stopped.response)
        assertEquals(stopped, capture.complete())
        assertEquals(stopped, capture.append("Später Text", 11))
    }

    @Test fun oversizedTranscriptDoesNotCutAnEmojiInHalf() {
        val prefix = "x".repeat(68_095)
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        LocalChatTranscriptStore(dir).append(id, false, prefix + "🙂", 1)
        val restored = LocalChatTranscriptStore(dir).load(id).single().text
        assertEquals(prefix, restored)
        assertEquals(restored, restored.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }

    @Test fun fullPartialAnswerKeepsStatusAndEndingAfterRestart() {
        val answer = "x".repeat(63_983) + " NICHT AUSFÜHREN."
        val visible = "Antwort unvollständig: Das Ausgabelimit wurde erreicht.\n\n" + answer
        val dir = tmp.newFolder()
        val id = UUID.randomUUID().toString()
        LocalChatTranscriptStore(dir).append(id, false, visible, 1)
        assertEquals(visible, LocalChatTranscriptStore(dir).load(id).single().text)
    }
}
