package at.heinz.deviceai.ui

import org.json.JSONArray
import org.json.JSONObject

/**
 * Projects the answer of a real file tool into the one bounded "files" record of the Command03 snapshot.
 *
 * Until now the page could show that a file search ran (activity step) but never what it found: the tool
 * output went only to the model and to native windows that are hidden while the HTML stage is shown.
 * The record carries file names only (never a path), at most [MAX_NAMES] of them, and the number of
 * result lines the tool actually returned. Nothing is synthesized: an output that does not have the
 * tool's exact line format produces no record, and a record is bound to the session that ran the tool.
 */
class Command03FilesProjection {
    private class Record(val session: String, val revision: Long, val kind: String, val label: String,
        val total: Int, val more: Boolean, val names: List<String>)

    private var revision = 0L
    private var record: Record? = null

    /** @return true when the page should receive a new snapshot. */
    @Synchronized fun accept(session: String, tool: String, arguments: Map<String, Any?>, output: String): Boolean {
        if (session.isBlank()) return false
        val next = when (tool) {
            "find_files" -> found(session, arguments, output)
            "list_files" -> listed(session, arguments, output)
            else -> null
        } ?: return false
        record = next
        return true
    }

    /** Null until a file tool answered in [session]. */
    @Synchronized fun snapshot(session: String): JSONObject? {
        val current = record?.takeIf { it.session == session } ?: return null
        val names = JSONArray()
        current.names.forEach(names::put)
        return JSONObject().put("revision", current.revision).put("kind", current.kind)
            .put("label", current.label).put("total", current.total).put("more", current.more).put("names", names)
    }

    /** `find … -type f -iname '*query*' | head -limit`: one absolute path per line, nothing else. */
    private fun found(session: String, arguments: Map<String, Any?>, output: String): Record? {
        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.any { !it.startsWith('/') || it.endsWith('/') }) return null
        val limit = when (val value = arguments["limit"]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: 100
            else -> 100
        }.coerceIn(1, 250)
        return Record(session, ++revision, "SEARCH", bounded(arguments["query"]?.toString().orEmpty(), MAX_LABEL),
            lines.size, lines.size >= limit,
            lines.take(MAX_NAMES).map { bounded(it.substringAfterLast('/'), MAX_NAME) }.filter { it.isNotEmpty() })
    }

    /** `ls -lah -- path` of Android's toybox: an optional "total" line, then one long-format entry per line. */
    private fun listed(session: String, arguments: Map<String, Any?>, output: String): Record? {
        val lines = output.lines().filter { it.isNotBlank() }.let { all ->
            if (all.firstOrNull()?.startsWith("total ") == true) all.drop(1) else all
        }
        val names = lines.map { line ->
            val match = LONG_ENTRY.matchEntire(line) ?: return null
            val name = match.groupValues[2]
            if (match.groupValues[1] == "l") name.substringBefore(" -> ") else name
        }.filter { it != "." && it != ".." }
        val folder = arguments["path"]?.toString().orEmpty().trimEnd('/').substringAfterLast('/')
        return Record(session, ++revision, "FOLDER", bounded(folder, MAX_LABEL), names.size, false,
            names.take(MAX_NAMES).map { bounded(it, MAX_NAME) }.filter { it.isNotEmpty() })
    }

    private fun bounded(text: String, max: Int): String {
        val clean = text.filter { !it.isISOControl() }.trim()
        if (clean.length <= max) return clean
        var end = max - 1
        if (end > 0 && clean[end - 1].isHighSurrogate()) end--
        return clean.substring(0, end) + "…"
    }

    companion object {
        const val MAX_NAMES = 12
        const val MAX_NAME = 96
        const val MAX_LABEL = 80
        private val LONG_ENTRY =
            Regex("""([-dlbcps])[-rwxsStT]{9}\S*\s+\d+\s+\S+\s+\S+\s+\S+\s+\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+(.+)""")
    }
}
