package at.heinz.deviceai.cognitive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Explicitly reviewed source notes. Never passed into tool authority or neural weights. */
class WebLearningJournal(context: Context) {
    private val preferences = context.getSharedPreferences("device_ai_web_learning_v1", Context.MODE_PRIVATE)

    fun notes(): List<WebLearningCandidate> {
        val encoded = preferences.getString("reviewed_notes", "[]") ?: "[]"
        require(encoded.length <= 32_768) { "Quellen-Lernspeicher ist beschädigt oder zu groß" }
        val array = JSONArray(encoded)
        require(array.length() <= 24)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WebLearningCandidate(
                o.getString("id"), o.getString("url"), o.getString("sha"),
                o.getString("text"), o.getInt("sources"), o.getBoolean("conflict")
            ).also {
                require(it.id.matches(Regex("[a-f0-9]{64}")) &&
                    it.sourceHash.matches(Regex("[a-f0-9]{64}")) &&
                    it.sourceUrl.startsWith("https://") && it.sourceUrl.length <= 4096 &&
                    it.text.length in 12..400 && it.sourceCount in 1..24)
            }
        }
    }

    fun approve(note: WebLearningCandidate): Boolean {
        require(note.id.matches(Regex("[a-f0-9]{64}")) &&
            note.sourceHash.matches(Regex("[a-f0-9]{64}")) &&
            note.text.length in 12..400 &&
            note.sourceUrl.startsWith("https://") &&
            note.sourceUrl.length <= 4096 &&
            note.sourceCount in 1..24)
        if (note.contradictory) return false
        val previous = notes()
        if (previous.any { it.id == note.id } || previous.size >= 24) return false
        val array = JSONArray()
        (previous + note).forEach { v ->
            array.put(JSONObject()
                .put("id", v.id).put("url", v.sourceUrl).put("sha", v.sourceHash)
                .put("text", v.text).put("sources", v.sourceCount)
                .put("conflict", v.contradictory))
        }
        return preferences.edit().putString("reviewed_notes", array.toString()).commit()
    }

    fun clear(): Boolean = preferences.edit().remove("reviewed_notes").commit()
}
