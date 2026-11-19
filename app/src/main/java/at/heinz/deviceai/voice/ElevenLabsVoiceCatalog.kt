package at.heinz.deviceai.voice

import org.json.JSONObject
import java.util.Locale

/**
 * The owner's ElevenLabs voices from GET https://api.elevenlabs.io/v2/voices (elevenlabs.io/docs/api-reference/voices/search).
 * Per voice the API documents voice_id, name, labels, description, preview_url, category and verified_languages
 * (each with language, model_id, accent, locale and its own preview_url). The call synthesises no text; ElevenLabs
 * bills text-to-speech per character, and the documentation names no price for this listing (not confirmed free).
 * It is fetched only when the owner opens the list.
 */
object ElevenLabsVoiceCatalog {
    const val ENDPOINT = "https://api.elevenlabs.io/v2/voices?page_size=100"
    const val MAX_JSON_BYTES = 2_000_000
    const val MAX_VOICES = 100

    data class Voice(
        val id: String,
        val name: String,
        val category: String?,
        val description: String?,
        val labels: Map<String, String>,
        /** Hosted sample (https only); for a voice verified in German, the German sample is preferred. */
        val previewUrl: String?,
        val germanPreview: Boolean,
        val germanVerified: Boolean
    ) {
        /** Labels as the API provides them (gender, age, accent, descriptive, use_case …), for display. */
        fun labelLine(): String = buildList {
            listOf("gender", "age", "accent", "descriptive", "use_case").forEach { key -> labels[key]?.let(::add) }
            labels.filterKeys { it !in setOf("gender", "age", "accent", "descriptive", "use_case") }.values.take(3)
                .forEach(::add)
            if (germanVerified) add("Deutsch geprüft")
            category?.let { add(it) }
        }.joinToString(" · ")
    }

    data class Page(val voices: List<Voice>, val hasMore: Boolean)

    private val safeUrl = Regex("https://[A-Za-z0-9.-]+(?::443)?/[^\\s\"<>]{0,1500}")

    fun safePreviewUrl(value: String?): String? = value?.trim()?.takeIf { safeUrl.matches(it) }

    /** Defensive parse; unknown or broken entries are skipped, never invented. */
    fun parse(json: String): Page {
        val root = JSONObject(json)
        val list = root.optJSONArray("voices") ?: return Page(emptyList(), false)
        val voices = ArrayList<Voice>()
        for (index in 0 until minOf(list.length(), MAX_VOICES)) {
            val item = list.optJSONObject(index) ?: continue
            val id = item.optString("voice_id", "").takeIf(VoiceProfiles::validVoiceId) ?: continue
            val name = clean(item.optString("name", ""), 80) ?: continue
            val labels = LinkedHashMap<String, String>()
            item.optJSONObject("labels")?.let { obj ->
                obj.keys().asSequence().take(12).forEach { key ->
                    clean(obj.optString(key, ""), 40)?.let { labels[key.take(24)] = it }
                }
            }
            var germanPreview: String? = null
            var germanVerified = false
            item.optJSONArray("verified_languages")?.let { languages ->
                for (l in 0 until minOf(languages.length(), 40)) {
                    val language = languages.optJSONObject(l) ?: continue
                    if (language.optString("language", "").lowercase(Locale.ROOT) != "de") continue
                    germanVerified = true
                    if (germanPreview == null) germanPreview = safePreviewUrl(language.optString("preview_url", ""))
                }
            }
            val general = safePreviewUrl(item.optString("preview_url", ""))
            voices += Voice(id, name, clean(item.optString("category", ""), 30),
                clean(item.optString("description", ""), 200), labels, germanPreview ?: general,
                germanPreview != null, germanVerified)
        }
        return Page(sorted(voices), root.optBoolean("has_more", false))
    }

    /** German-verified voices first, then by name; nothing is ranked by a guessed sound. */
    fun sorted(voices: List<Voice>): List<Voice> =
        voices.sortedWith(compareByDescending<Voice> { it.germanVerified }.thenBy { it.name.lowercase(Locale.GERMAN) })

    private fun clean(value: String, max: Int): String? =
        value.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(max).takeIf { it.isNotEmpty() }

    /** Network seam: returns the body of a GET with the given key header. Tests replace it. */
    fun interface Fetch { fun get(url: String, key: String): String }

    fun load(key: String, fetch: Fetch): Page = parse(fetch.get(ENDPOINT, key))

    /** Real HTTPS GET; no redirects, bounded size, the key only in the xi-api-key header. */
    val https = Fetch { url, key ->
        val connection = java.net.URL(url).openConnection() as javax.net.ssl.HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("xi-api-key", key)
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "ELEVENLABS_VOICES_UNAVAILABLE" }
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(out.size() + count <= MAX_JSON_BYTES)
                    out.write(buffer, 0, count)
                }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}
