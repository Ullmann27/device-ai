package at.heinz.deviceai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ElevenLabsPcmRequestUiTest {
    private class Connection(var status: Int = 200, var type: String = "audio/pcm") :
        HttpsURLConnection(URL(ElevenLabsPcmRequest.ENDPOINT)) {
        val body = ByteArrayOutputStream()
        var disconnected = false
        var reads = 0
        override fun getOutputStream() = body
        override fun getInputStream(): ByteArrayInputStream { reads++; return ByteArrayInputStream(byteArrayOf(0, 0, 1, 0)) }
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
        assertEquals("/v1/text-to-speech/ii0s2u4R3UFnxKL6DOrz/stream", connection.url.path)
        assertEquals("output_format=pcm_24000", connection.url.query)
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("synthetic_test_key", connection.getRequestProperty("xi-api-key"))
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

    @Test fun httpErrorsRedirectsAndNonAudioNeverReachPlayback() {
        for (connection in listOf(Connection(401), Connection(429), Connection(302), Connection(type = "application/json"))) {
            ElevenLabsPcmRequest(connection).use {
                assertTrue(runCatching { it.synthesize("Antwort", "synthetic_test_key") { true } }.isFailure)
            }
            assertEquals(0, connection.reads)
            assertTrue(connection.disconnected)
        }
    }
}
