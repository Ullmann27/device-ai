package at.heinz.deviceai.diagnostics

import java.io.File
import java.security.MessageDigest

/**
 * Persistent error and diagnosis log of Continuum.
 *
 * - Each real problem is one [Issue], identified by component + code, so repeated occurrences are counted instead of
 *   duplicated.
 * - An issue stays visible until it is resolved by a later successful check ([resolve]) or explicitly acknowledged by
 *   the owner ([acknowledge]). A new occurrence after an acknowledgement opens it again.
 * - All free text is redacted before it is stored ([Redaction]): no access tokens, keys, passwords, e-mail addresses,
 *   phone numbers or private file paths reach the file or the export.
 * Storage: one escaped tab-separated line per issue (no external dependency), written atomically.
 */
class ContinuumDiagnostics(private val file: File, private val appVersion: String) {
    enum class Severity(val label: String) { INFO("Hinweis"), WARNING("Warnung"), ERROR("Fehler"), CRITICAL("Kritisch") }
    enum class Status(val label: String) { OPEN("offen"), ACKNOWLEDGED("quittiert"), RESOLVED("behoben") }

    data class Issue(
        val fingerprint: String,
        val component: String,
        val code: String,
        val severity: Severity,
        val title: String,
        val detail: String,
        val recommendation: String,
        val firstSeen: Long,
        val lastSeen: Long,
        val count: Int,
        val status: Status,
        val statusAt: Long,
        val statusNote: String,
        val appVersion: String
    )

    data class Report(val issue: Issue, val isNew: Boolean)

    @Synchronized fun report(component: String, code: String, severity: Severity, title: String, detail: String,
                             recommendation: String, now: Long = System.currentTimeMillis()): Report {
        require(component.isNotBlank() && code.matches(Regex("[A-Z0-9_]{2,48}")))
        val issues = load().toMutableList()
        val fp = fingerprint(component, code)
        val index = issues.indexOfFirst { it.fingerprint == fp }
        val clean = { s: String, max: Int -> Redaction.apply(s).take(max) }
        val result: Report
        if (index < 0) {
            val issue = Issue(fp, clean(component, 60), code, severity, clean(title, 140), clean(detail, 600), clean(recommendation, 300),
                now, now, 1, Status.OPEN, now, "", appVersion)
            issues += issue
            result = Report(issue, true)
        } else {
            val old = issues[index]
            val reopened = old.status != Status.OPEN
            val issue = old.copy(severity = maxOf(old.severity, severity), title = clean(title, 140), detail = clean(detail, 600),
                recommendation = clean(recommendation, 300), lastSeen = now, count = old.count + 1,
                status = Status.OPEN, statusAt = if (reopened) now else old.statusAt,
                statusNote = if (reopened) "erneut aufgetreten" else old.statusNote, appVersion = appVersion)
            issues[index] = issue
            result = Report(issue, reopened)
        }
        save(issues.sortedByDescending { it.lastSeen }.take(MAX_ISSUES))
        return result
    }

    /** A later successful check of the same component/code: the issue is closed with the given evidence. */
    @Synchronized fun resolve(component: String, code: String, evidence: String, now: Long = System.currentTimeMillis()): Boolean {
        val issues = load().toMutableList()
        val index = issues.indexOfFirst { it.fingerprint == fingerprint(component, code) && it.status != Status.RESOLVED }
        if (index < 0) return false
        issues[index] = issues[index].copy(status = Status.RESOLVED, statusAt = now, statusNote = Redaction.apply(evidence).take(200))
        save(issues)
        return true
    }

    /** Owner acknowledgement: hides the issue from the open list until it happens again. */
    @Synchronized fun acknowledge(fingerprint: String, now: Long = System.currentTimeMillis()): Boolean {
        val issues = load().toMutableList()
        val index = issues.indexOfFirst { it.fingerprint == fingerprint && it.status == Status.OPEN }
        if (index < 0) return false
        issues[index] = issues[index].copy(status = Status.ACKNOWLEDGED, statusAt = now, statusNote = "vom Besitzer quittiert")
        save(issues)
        return true
    }

    @Synchronized fun all(): List<Issue> = load().sortedByDescending { it.lastSeen }

    /** Open issues, most severe and most recent first. */
    @Synchronized fun open(): List<Issue> = load().filter { it.status == Status.OPEN }
        .sortedWith(compareByDescending<Issue> { it.severity }.thenByDescending { it.lastSeen })

    /** Redacted plain-text export for sharing with a developer; contains no private content. */
    @Synchronized fun export(now: Long = System.currentTimeMillis()): String = buildString {
        appendLine("Continuum Diagnose-Export")
        appendLine("Erstellt: ${iso(now)} · App ${Redaction.apply(appVersion)}")
        appendLine("Persönliche Inhalte, Zugangsdaten und Pfade sind entfernt.")
        val list = all()
        appendLine("Einträge: ${list.size} (offen ${list.count { it.status == Status.OPEN }})")
        for (i in list) {
            appendLine()
            appendLine("[${i.severity.label}] ${i.component} · ${i.code} · ${i.status.label}${if (i.statusNote.isNotEmpty()) " (${i.statusNote})" else ""}")
            appendLine("  ${i.title}")
            appendLine("  zuerst ${iso(i.firstSeen)}, zuletzt ${iso(i.lastSeen)}, ${i.count}×, Version ${i.appVersion}")
            if (i.detail.isNotEmpty()) appendLine("  Details: ${i.detail}")
            appendLine("  Empfehlung: ${i.recommendation}")
        }
    }

    private fun load(): List<Issue> {
        if (!file.isFile) return emptyList()
        // Fail closed on read errors or malformed lines: never overwrite an
        // existing diagnostic archive by mistaking unreadable data for no data.
        return file.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { decode(it) ?: throw IllegalStateException("invalid diagnostics archive record") }
    }

    private fun save(issues: List<Issue>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(issues.joinToString("\n", postfix = "\n") { encode(it) }, Charsets.UTF_8)
        try {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun encode(i: Issue) = listOf(i.fingerprint, i.component, i.code, i.severity.name, i.title, i.detail, i.recommendation,
        i.firstSeen.toString(), i.lastSeen.toString(), i.count.toString(), i.status.name, i.statusAt.toString(), i.statusNote,
        i.appVersion).joinToString("\t") { esc(it) }

    private fun decode(line: String): Issue? {
        if (line.isBlank()) return null
        val f = line.split('\t').map(::unesc)
        if (f.size != 14) return null
        return runCatching {
            Issue(f[0], f[1], f[2], Severity.valueOf(f[3]), f[4], f[5], f[6], f[7].toLong(), f[8].toLong(), f[9].toInt(),
                Status.valueOf(f[10]), f[11].toLong(), f[12], f[13])
        }.getOrNull()
    }

    companion object {
        const val MAX_ISSUES = 200

        fun fingerprint(component: String, code: String): String =
            MessageDigest.getInstance("SHA-256").digest("$component\u0000$code".toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")
        private fun unesc(s: String): String {
            val out = StringBuilder(); var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) { 't' -> out.append('\t'); 'n' -> out.append('\n'); else -> out.append(s[i + 1]) }
                    i += 2
                } else { out.append(c); i++ }
            }
            return out.toString()
        }

        fun iso(t: Long): String = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.GERMANY).format(java.util.Date(t))
    }
}

/** Removes secrets and personal data from diagnostic text. */
object Redaction {
    private val rules = listOf(
        Regex("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?(-----END [A-Z ]*PRIVATE KEY-----|$)") to "[privater schlüssel entfernt]",
        Regex("(?i)\\bauthorization\\s*[:=][^\\n|]*") to "authorization=[entfernt]",
        Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+") to "[token entfernt]",
        Regex("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b") to "[zugangsdaten entfernt]",
        Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}") to "Bearer [entfernt]",
        Regex("(?i)\\b(api[_-]?key|token|secret|password|passwort|kennwort|authorization)\\b\\s*[:=]\\s*\\S+") to "$1=[entfernt]",
        Regex("\\b(gsk|sk|ghp|gho|github_pat|xox[bpas]|AIza|hf|glpat)[_-][A-Za-z0-9_-]{12,}\\b") to "[zugangsdaten entfernt]",
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b") to "[e-mail entfernt]",
        Regex("(https?://[^\\s?#]+)[?#][^\\s]*") to "$1?[parameter entfernt]",
        Regex("/data/(user|data)/\\d+/[A-Za-z0-9._]+") to "<app-daten>",
        Regex("/storage/emulated/\\d+") to "<speicher>",
        Regex("/(home|Users)/[^/\\s|]+") to "<home>",
        Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b") to "[ip entfernt]",
        Regex("\\+?\\d[\\d /-]{7,}\\d") to "[nummer entfernt]",
        Regex("\\b[A-Fa-f0-9]{40,}\\b") to "[hash gekürzt]",
        Regex("\\b[A-Za-z0-9+/]{48,}={0,2}") to "[daten entfernt]",
        // the owner's names (e.g. from a greeting inside an error text); the package name at.heinz.deviceai stays readable
        Regex("(?i)ullmann\\w*") to "[konto entfernt]",
        Regex("(?i)(?<!at\\.)\\bheinz\\b") to "[name entfernt]"
    )

    fun apply(text: String): String = rules.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
