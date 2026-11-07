package at.heinz.deviceai

import android.content.Context
import java.util.Locale

enum class Risk {
    READ_ONLY,
    CONFIRM,
    DESTRUCTIVE
}

data class CommandStep(
    val label: String,
    val shellCommand: String,
    val risk: Risk = Risk.READ_ONLY
)

data class CommandPlan(
    val title: String,
    val summary: String,
    val steps: List<CommandStep>
) {
    val risk: Risk
        get() = steps.maxByOrNull { it.risk.ordinal }?.risk ?: Risk.READ_ONLY
}

class CommandInterpreter(private val context: Context) {

    fun interpret(raw: String): CommandPlan? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.GERMAN)

        if (text.startsWith("!")) {
            val command = text.removePrefix("!").trim()
            if (command.isBlank()) return null
            return CommandPlan(
                title = "Expertenmodus",
                summary = "Freier Shell-Befehl über Shizuku",
                steps = listOf(
                    CommandStep(
                        label = "Shell-Befehl ausführen",
                        shellCommand = command,
                        risk = Risk.CONFIRM
                    )
                )
            )
        }

        val steps = mutableListOf<CommandStep>()

        if (containsAny(lower, "geräteinfo", "geräte info", "handyinfo", "handy info", "systeminfo", "system info")) {
            steps += CommandStep(
                "Geräteinformationen",
                "echo '=== DEVICE AI ==='; " +
                    "echo Hersteller: $(getprop ro.product.manufacturer); " +
                    "echo Modell: $(getprop ro.product.model); " +
                    "echo Android: $(getprop ro.build.version.release); " +
                    "echo SDK: $(getprop ro.build.version.sdk); " +
                    "echo Build: $(getprop ro.build.display.id); " +
                    "echo ABI: $(getprop ro.product.cpu.abi); " +
                    "echo Gerät: $(getprop ro.product.device)"
            )
        }

        if (containsAny(lower, "akku", "batterie", "battery")) {
            steps += CommandStep(
                "Akku prüfen",
                "dumpsys battery | head -45"
            )
        }

        if (containsAny(lower, "speicher", "freier platz", "freier speicher", "storage")) {
            steps += CommandStep(
                "Speicherübersicht",
                "df -h /data /sdcard 2>/dev/null; " +
                    "echo; echo 'Download:'; du -sh /sdcard/Download 2>/dev/null; " +
                    "echo 'Bilder:'; du -sh /sdcard/DCIM /sdcard/Pictures 2>/dev/null"
            )
        }

        if ((lower.contains("groß") || lower.contains("gross")) && lower.contains("datei")) {
            steps += CommandStep(
                "Große Dateien finden",
                "du -ak /sdcard/Download /sdcard/DCIM /sdcard/Pictures /sdcard/Documents /sdcard/Movies " +
                    "2>/dev/null | sort -nr | head -40"
            )
        }

        if (containsAny(lower, "neueste datei", "letzte datei", "zuletzt heruntergeladen", "letzte downloads")) {
            steps += CommandStep(
                "Neueste Dateien",
                "ls -lt /sdcard/Download 2>/dev/null | head -35"
            )
        }

        if (containsAny(lower, "fotos", "bilder", "galerie") && !lower.contains("screenshot")) {
            steps += CommandStep(
                "Fotos und Bilder",
                "find /sdcard/DCIM /sdcard/Pictures -type f " +
                    "\\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.heic' \\) " +
                    "2>/dev/null | tail -100"
            )
        }

        if (containsAny(lower, "videos", "filme")) {
            steps += CommandStep(
                "Videos",
                "find /sdcard/DCIM /sdcard/Movies -type f " +
                    "\\( -iname '*.mp4' -o -iname '*.mkv' -o -iname '*.webm' -o -iname '*.mov' \\) " +
                    "2>/dev/null | tail -100"
            )
        }

        val wantsDownloads = lower == "downloads" ||
            lower.contains("zeig meine downloads") ||
            lower.contains("zeige meine downloads") ||
            lower.contains("öffne downloads") ||
            lower.contains("download ordner")

        if (wantsDownloads && !(lower.contains("install") && lower.contains("apk"))) {
            steps += CommandStep(
                "Downloads anzeigen",
                "ls -lah /sdcard/Download 2>/dev/null | head -120"
            )
        }

        val wantsApps = lower == "apps" ||
            lower.contains("installierte apps") ||
            lower.contains("meine apps") ||
            lower.contains("welche apps")

        if (wantsApps) {
            steps += CommandStep(
                "Installierte Benutzer-Apps",
                "pm list packages -3 | sed 's/^package://' | sort"
            )
        }

        if (containsAny(lower, "prozesse", "cpu", "auslastung", "performance")) {
            steps += CommandStep(
                "Aktive Prozesse",
                "top -b -n 1 -m 25 2>/dev/null | head -40"
            )
        }

        if (lower.contains("screenshot") || lower.contains("bildschirmfoto")) {
            steps += CommandStep(
                "Screenshot speichern",
                "out=/sdcard/Download/device-ai-$(date +%Y%m%d-%H%M%S).png; " +
                    "screencap -p \"\$out\" && echo \"Gespeichert: \$out\""
            )
        }

        if (lower.contains("bildschirmaufnahme") || lower.contains("screen recording") || lower.contains("screenrecord")) {
            steps += CommandStep(
                "15-Sekunden-Bildschirmaufnahme",
                "out=/sdcard/Download/device-ai-recording-$(date +%Y%m%d-%H%M%S).mp4; " +
                    "screenrecord --time-limit 15 \"\$out\"; echo \"Gespeichert: \$out\"",
                Risk.CONFIRM
            )
        }

        if (lower.contains("install") && lower.contains("apk")) {
            val filter = apkFilter(lower)
            val glob = if (filter == null) "*.apk" else "*${filter}*.apk"
            steps += CommandStep(
                if (filter == null) "Neueste APK installieren" else "Neueste ${filter}-APK installieren",
                "apk=$(ls -t /sdcard/Download/$glob 2>/dev/null | head -n 1); " +
                    "if [ -n \"\$apk\" ]; then " +
                    "echo \"APK: \$apk\"; pm install -r \"\$apk\"; " +
                    "else echo 'Keine passende APK direkt in Downloads gefunden.'; exit 2; fi",
                Risk.CONFIRM
            )
        }

        if (containsAny(lower, "fehler", "logcat", "logs", "absturz", "crash")) {
            val appName = extractAfterAny(text, listOf("von", "für", "bei"))
                ?.takeIf { it.length in 2..80 }
            val resolved = appName?.let { resolvePackage(it) }
            val command = if (resolved != null) {
                val safePkg = safePackage(resolved)
                "logcat -d -t 900 2>/dev/null | grep -i -E 'AndroidRuntime|FATAL EXCEPTION|$safePkg' | tail -160"
            } else {
                "logcat -d -t 900 2>/dev/null | grep -E 'AndroidRuntime|FATAL EXCEPTION| E/' | tail -160"
            }
            steps += CommandStep(
                if (resolved != null) "Fehler für ${appName}" else "Letzte Android-Fehler",
                command
            )
        }

        val restartName = when {
            lower.startsWith("neustart ") -> text.substringAfter(" ").trim()
            lower.contains("starte ") && lower.contains(" neu") -> extractBetweenLast(text, "starte", "neu")
            else -> null
        }?.let(::cleanAppName)

        if (!restartName.isNullOrBlank()) {
            val pkg = resolvePackage(restartName)
            if (pkg != null) {
                val safePkg = safePackage(pkg)
                steps += CommandStep(
                    "App neu starten: $restartName",
                    "am force-stop $safePkg; sleep 1; " +
                        "monkey -p $safePkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                        "echo 'Neu gestartet: $safePkg'",
                    Risk.CONFIRM
                )
            } else {
                steps += notFoundStep(restartName)
            }
        } else {
            val stopName = extractAfterVerb(text, listOf("beende", "stoppe", "schließe", "schliesse"))
                ?.let(::cleanAppName)
            if (!stopName.isNullOrBlank()) {
                val pkg = resolvePackage(stopName)
                if (pkg != null) {
                    steps += CommandStep(
                        "App beenden: $stopName",
                        "am force-stop ${safePackage(pkg)} && echo 'Beendet: ${safePackage(pkg)}'",
                        Risk.CONFIRM
                    )
                } else {
                    steps += notFoundStep(stopName)
                }
            }

            val actualStartName = if (!lower.contains(" neu")) {
                extractAfterVerb(text, listOf("öffne", "oeffne", "starte"))?.let(::cleanAppName)
            } else null

            if (!actualStartName.isNullOrBlank()) {
                val pkg = resolvePackage(actualStartName)
                if (pkg != null) {
                    val safePkg = safePackage(pkg)
                    steps += CommandStep(
                        "App starten: $actualStartName",
                        "monkey -p $safePkg -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 && " +
                            "echo 'Gestartet: $safePkg'"
                    )
                } else {
                    steps += notFoundStep(actualStartName)
                }
            }
        }

        if (lower.contains("deinstalliere ") || lower.contains("deinstallieren ")) {
            val name = extractAfterVerb(text, listOf("deinstalliere", "deinstallieren"))
                ?.let(::cleanAppName)
            if (!name.isNullOrBlank()) {
                val pkg = resolvePackage(name)
                if (pkg != null) {
                    steps += CommandStep(
                        "App deinstallieren: $name",
                        "pm uninstall ${safePackage(pkg)}",
                        Risk.DESTRUCTIVE
                    )
                } else {
                    steps += notFoundStep(name)
                }
            }
        }

        if (lower.contains("app info") || lower.contains("app-info") || lower.contains("version von")) {
            val name = extractAfterAny(text, listOf("von", "zu"))
                ?.let(::cleanAppName)
            if (!name.isNullOrBlank()) {
                val pkg = resolvePackage(name)
                if (pkg != null) {
                    val safePkg = safePackage(pkg)
                    steps += CommandStep(
                        "App-Informationen: $name",
                        "dumpsys package $safePkg 2>/dev/null | " +
                            "grep -E 'versionName=|versionCode=|firstInstallTime|lastUpdateTime|dataDir=|userId=' | head -60"
                    )
                }
            }
        }

        if (steps.isEmpty()) {
            val search = extractFileSearch(text)
            if (!search.isNullOrBlank()) {
                val pattern = shellQuote("*$search*")
                steps += CommandStep(
                    "Dateien suchen: $search",
                    "find /sdcard/Download /sdcard/Documents /sdcard/DCIM /sdcard/Pictures /sdcard/Movies " +
                        "-type f -iname $pattern 2>/dev/null | head -120"
                )
            }
        }

        if (steps.isEmpty() && lower.contains("hilfe")) {
            steps += CommandStep(
                "Beispiele anzeigen",
                "printf '%s\\n' " +
                    "'Öffne WhatsApp' " +
                    "'Starte Lumo neu' " +
                    "'Zeig meine Downloads' " +
                    "'Finde Lumo' " +
                    "'Zeig große Dateien' " +
                    "'Geräteinfo und Akku' " +
                    "'Screenshot' " +
                    "'Zeig Fehler von Lumo' " +
                    "'Installiere neueste Lumo APK und starte Lumo' " +
                    "'! pm list packages -3'"
            )
        }

        if (steps.isEmpty()) {
            steps += CommandStep(
                "Befehl nicht erkannt",
                "echo 'Das habe ich noch nicht sicher verstanden. Beispiele: Hilfe, Öffne WhatsApp, Geräteinfo, Downloads, Screenshot, Zeig Fehler von Lumo.'"
            )
        }

        val uniqueSteps = steps.distinctBy { it.label + "|" + it.shellCommand }
        return CommandPlan(
            title = if (uniqueSteps.size == 1) uniqueSteps.first().label else "${uniqueSteps.size} Aktionen geplant",
            summary = uniqueSteps.joinToString(" → ") { it.label },
            steps = uniqueSteps
        )
    }

    private fun apkFilter(lower: String): String? {
        val known = listOf("lumo", "device-ai", "device ai")
        return known.firstOrNull { lower.contains(it) }
            ?.replace(" ", "-")
            ?.replace(Regex("[^a-z0-9._-]"), "")
    }

    private fun extractFileSearch(text: String): String? {
        val lower = text.lowercase(Locale.GERMAN)
        val verbs = listOf("finde ", "suche ")
        val verb = verbs.firstOrNull { lower.startsWith(it) } ?: return null
        var term = text.substring(verb.length).trim()
        term = term
            .removePrefix("die Datei ")
            .removePrefix("Datei ")
            .removePrefix("nach ")
            .trim()
        return sanitizeSearch(term).takeIf { it.length >= 2 }
    }

    private fun sanitizeSearch(value: String): String =
        value.replace(Regex("[\\r\\n\\t]"), " ")
            .replace(Regex("[;&|<>$]"), "")
            .trim()
            .take(80)

    private fun extractAfterVerb(text: String, verbs: List<String>): String? {
        val lower = text.lowercase(Locale.GERMAN)
        val candidates = verbs.mapNotNull { verb ->
            val token = "$verb "
            val index = lower.lastIndexOf(token)
            if (index < 0) null else index to text.substring(index + token.length)
        }
        val tail = candidates.maxByOrNull { it.first }?.second ?: return null
        return tail.split(Regex("(?i)\\s+und\\s+")).firstOrNull()?.trim()
    }

    private fun extractAfterAny(text: String, words: List<String>): String? {
        val lower = text.lowercase(Locale.GERMAN)
        val candidates = words.mapNotNull { word ->
            val token = " $word "
            val index = lower.lastIndexOf(token)
            if (index < 0) null else index to text.substring(index + token.length)
        }
        return candidates.maxByOrNull { it.first }?.second
            ?.split(Regex("(?i)\\s+und\\s+"))
            ?.firstOrNull()
            ?.trim()
    }

    private fun extractBetweenLast(text: String, startWord: String, endWord: String): String? {
        val lower = text.lowercase(Locale.GERMAN)
        val start = lower.lastIndexOf("$startWord ")
        if (start < 0) return null
        val from = start + startWord.length + 1
        val end = lower.indexOf(" $endWord", from)
        if (end <= from) return null
        return text.substring(from, end).trim()
    }

    private fun cleanAppName(value: String): String =
        value
            .replace(Regex("(?i)^die\\s+app\\s+"), "")
            .replace(Regex("(?i)^app\\s+"), "")
            .replace(Regex("(?i)\\s+app$"), "")
            .replace(Regex("[.!?,;:]+$"), "")
            .trim()
            .take(80)

    private fun resolvePackage(query: String): String? {
        val q = query.trim().lowercase(Locale.GERMAN)
        if (q.matches(Regex("[a-zA-Z0-9_.]+")) && q.contains(".")) return q

        val aliases = mapOf(
            "whatsapp" to "com.whatsapp",
            "chrome" to "com.android.chrome",
            "youtube" to "com.google.android.youtube",
            "shizuku" to "moe.shizuku.privileged.api"
        )
        aliases[q]?.let { return it }

        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .mapNotNull { app ->
                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrNull()
                    ?: return@mapNotNull null
                Triple(app.packageName, label, label.lowercase(Locale.GERMAN))
            }
            .sortedBy { it.second.length }
            .firstOrNull { (pkg, _, labelLower) ->
                labelLower == q ||
                    labelLower.contains(q) ||
                    q.contains(labelLower) ||
                    pkg.lowercase(Locale.GERMAN).contains(q)
            }
            ?.first
    }

    private fun notFoundStep(name: String) =
        CommandStep(
            "App nicht gefunden: $name",
            "echo ${shellQuote("Keine installierte App für: $name")}"
        )

    private fun safePackage(value: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) }
            ?: error("Ungültiger Paketname")

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun containsAny(text: String, vararg terms: String): Boolean =
        terms.any { text.contains(it) }
}
