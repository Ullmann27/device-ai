package at.heinz.deviceai

import org.json.JSONObject

/** Shared text-only backend contract. Provider credentials never enter this layer. */
internal object RemoteMentorProtocol {
    private fun requireProvider(provider: String) {
        require(provider == "GROQ" || provider == "OPENROUTER" || provider == "INWORLD") {
            "Unbekannter Mentor-Anbieter."
        }
    }

    fun request(provider: String, question: String): JSONObject {
        requireProvider(provider)
        require(question.length <= 1800 && question.trim().length in 3..1800 &&
            question.none { it.code in 0..8 || it.code in 11..31 }) {
            "Mentorfrage muss 3 bis 1800 Zeichen enthalten und darf keine Steuerzeichen enthalten."
        }
        return JSONObject().apply {
            put("consent", true)
            put("provider", provider)
            put("question", question.trim())
        }
    }

    fun answer(provider: String, json: JSONObject): String {
        requireProvider(provider)
        require(json.opt("ok") == true && json.optString("provider") == provider &&
            json.optString("trust") == "UNVERIFIED_EXTERNAL" &&
            json.opt("memory_written") == false &&
            json.optJSONArray("tool_calls")?.length() == 0) {
            "Ungültige oder nicht isolierte Mentorantwort."
        }
        val message = json.opt("message") as? String
        require(message != null && message.isNotBlank() && message.length <= 4200) {
            "Leere, ungültige oder zu lange Mentorantwort."
        }
        return message.trim()
    }
}
