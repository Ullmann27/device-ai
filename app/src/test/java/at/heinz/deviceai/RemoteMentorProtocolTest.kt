package at.heinz.deviceai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteMentorProtocolTest {
    private fun request(provider: String, question: String) = RemoteMentorProtocol.request(provider, question)

    private fun answer(provider: String, json: JSONObject) = RemoteMentorProtocol.answer(provider, json)

    private fun validReply(provider: String = "INWORLD") = JSONObject()
        .put("ok", true).put("provider", provider).put("trust", "UNVERIFIED_EXTERNAL")
        .put("memory_written", false).put("tool_calls", JSONArray())
        .put("message", "Eine externe Antwort.")

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Invalid mentor input must be rejected") }
        catch (_: IllegalArgumentException) { /* Expected protocol rejection. */ }
    }

    @Test fun approvedQuestionIsTheOnlyPayloadBesidesProviderAndConsent() {
        val body = request("INWORLD", "  Erkläre elektrische Motoren.  ")
        assertEquals(setOf("question", "provider", "consent"), body.keys().asSequence().toSet())
        assertEquals("INWORLD", body.getString("provider"))
        assertEquals(true, body.get("consent"))
        assertEquals("Erkläre elektrische Motoren.", body.getString("question"))
    }

    @Test fun existingProvidersRetainTheSameRequestContract() {
        for (provider in listOf("GROQ", "OPENROUTER")) {
            assertEquals(provider, request(provider, "Eine einzelne Frage.").getString("provider"))
            assertEquals("Eine externe Antwort.", answer(provider, validReply(provider)))
        }
    }

    @Test fun unknownProviderNeverGetsARequestOrResponse() {
        rejected { request("UNSUPPORTED", "Eine Frage.") }
        rejected { answer("UNSUPPORTED", validReply("UNSUPPORTED")) }
    }

    @Test fun overlongQuestionsAreRejectedAsAWholeWithoutTruncation() {
        val input = "x".repeat(1790) + " ohne Datenweitergabe"
        rejected { request("INWORLD", input) }
        assertEquals(1800, request("INWORLD", "x".repeat(1800)).getString("question").length)
        rejected { request("INWORLD", " ".repeat(1800) + "Frage") }
    }

    @Test fun shortOrControlCharacterInputIsRejected() {
        for (value in listOf("", "ab", "abc\u0000def", "abc\u000Bdef")) {
            rejected { request("INWORLD", value) }
        }
        assertEquals("Zeile eins\nZeile zwei", request("INWORLD", "Zeile eins\nZeile zwei").getString("question"))
    }

    @Test fun onlyTheMatchingUnverifiedTextResponseIsPresented() {
        val reply = validReply().put("reasoning", "Hidden reasoning").put("model", "inworld/gemma-4-31b-it")
        assertEquals("Eine externe Antwort.", answer("INWORLD", reply))
    }

    @Test fun wrongProviderOrTrustNeverReachesTheUser() {
        rejected { answer("INWORLD", validReply("GROQ")) }
        rejected { answer("INWORLD", validReply().put("trust", "VERIFIED")) }
        rejected { answer("INWORLD", validReply().put("ok", false)) }
    }

    @Test fun toolAndMemoryPrivilegesAreRejectedIncludingCoercedBooleans() {
        rejected { answer("INWORLD", validReply().put("memory_written", true)) }
        rejected { answer("INWORLD", validReply().put("memory_written", "false")) }
        rejected { answer("INWORLD", validReply().put("tool_calls", JSONArray().put(JSONObject().put("name", "delete_file")))) }
        rejected { answer("INWORLD", validReply().apply { remove("tool_calls") }) }
    }

    @Test fun missingEmptyNontextAndOversizedAnswersFailClosed() {
        for (value in listOf<Any>(JSONObject.NULL, "", " ", 17, JSONObject(), "x".repeat(4201))) {
            rejected { answer("INWORLD", validReply().put("message", value)) }
        }
        assertEquals(4200, answer("INWORLD", validReply().put("message", "x".repeat(4200))).length)
    }
}
