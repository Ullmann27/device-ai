package at.heinz.deviceai

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import java.util.Base64
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ElevenLabsPcmRequestUiTest {
    private class Connection(var status: Int = 200, var type: String = "application/json") :
        HttpsURLConnection(URL(ElevenLabsPcmRequest.ENDPOINT)) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        var reads = 0
        var response = """{"audio_base64":"AAABAA=="}"""
        override fun getOutputStream() = body
        override fun getInputStream(): ByteArrayInputStream { reads++; return ByteArrayInputStream(response.toByteArray()) }
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "test-only"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    @Test fun realAdapterSerializesApprovedTextUsesExactVoiceAndNeverFollowsRedirects() {
        val connection = Connection()
        val text = "Eine andere Antwort mit \"Anführungszeichen\" und Umlauten: schön."
        ElevenLabsPcmRequest(connection).use {
            assertEquals(4, it.synthesize(text, "synthetic_test_key", { true }).size)
        }
        assertEquals("api.elevenlabs.io", connection.url.host)
        assertEquals("/v1/text-to-speech/ii0s2u4R3UFnxKL6DOrz/with-timestamps", connection.url.path)
        assertEquals("output_format=pcm_24000", connection.url.query)
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("synthetic_test_key", connection.getRequestProperty("xi-api-key"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        val body = JSONObject(connection.body.toString("UTF-8"))
        assertEquals(text, body.getString("text"))
        assertEquals("eleven_multilingual_v2", body.getString("model_id"))
        assertFalse(body.has("previous_text"))
        assertTrue(connection.disconnected)
    }

    @Test fun denyAndClosedRequestsSendNoText() {
        for (closed in listOf(false, true)) {
            val connection = Connection()
            ElevenLabsPcmRequest(connection).use {
                if (closed) it.close()
                assertTrue(runCatching { it.synthesize("Privat", "synthetic_test_key") { closed } }.isFailure)
            }
            assertEquals(0, connection.body.size())
            assertEquals(0, connection.reads)
        }
    }

    @Test fun httpErrorsRedirectsAndNonJsonNeverReachPlayback() {
        for (connection in listOf(Connection(401), Connection(429), Connection(302), Connection(type = "audio/pcm"))) {
            ElevenLabsPcmRequest(connection).use {
                assertTrue(runCatching { it.synthesize("Antwort", "synthetic_test_key") { true } }.isFailure)
            }
            assertEquals(0, connection.reads)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun exactOriginalAlignmentMapsToPcmFrames() {
        val text = "Hi du"
        val alignment = JSONObject().put("characters", JSONArray(text.map(Char::toString)))
            .put("character_start_times_seconds", JSONArray(text.indices.map { it * .05 }))
            .put("character_end_times_seconds", JSONArray(text.indices.map { (it + 1) * .05 }))
        val connection = Connection().apply {
            response = JSONObject().put("audio_base64", Base64.getEncoder().encodeToString(ByteArray(24_000)))
                .put("alignment", alignment).toString()
        }
        ElevenLabsPcmRequest(connection).use {
            val audio = it.synthesizeWithTiming(text, "synthetic_test_key") { true }
            assertEquals(24_000, audio.pcm.size)
            assertEquals(listOf(MayaSpeechAlignment.Range(0, 2, 0, 2400),
                MayaSpeechAlignment.Range(3, 5, 3600, 6000)), audio.ranges)
        }
    }

    @Test fun malformedAlignmentKeepsValidAudioWithoutInventingOffsets() {
        for (alignment in listOf(JSONObject(), JSONObject().put("characters", JSONArray(listOf("anderer Text"))))) {
            val connection = Connection().apply {
                response = JSONObject().put("audio_base64", "AAABAA==").put("alignment", alignment).toString()
            }
            ElevenLabsPcmRequest(connection).use {
                val audio = it.synthesizeWithTiming("Hallo", "synthetic_test_key") { true }
                assertEquals(4, audio.pcm.size)
                assertTrue(audio.ranges.isEmpty())
            }
        }
    }

    @Test fun invalidAudioOrOversizedResponsesAreRejected() {
        for (response in listOf("{}", """{"audio_base64":"!invalid!"}""", """{"audio_base64":"AA=="}""",
            " ".repeat(ElevenLabsPcmRequest.MAX_JSON_BYTES + 1))) {
            val connection = Connection().apply { this.response = response }
            ElevenLabsPcmRequest(connection).use {
                assertTrue(runCatching { it.synthesizeWithTiming("Hallo", "synthetic_test_key") { true } }.isFailure)
            }
        }
    }
}
