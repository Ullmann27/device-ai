package at.heinz.deviceai

import android.content.Context
import java.util.Locale

data class CommandPlan(
    val title: String,
    val shellCommand: String,
    val needsConfirmation: Boolean = false
)

class CommandInterpreter(private val context: Context) {

    fun interpret(raw: String): CommandPlan? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.GERMAN)

        if (text.startsWith("!")) {
            return CommandPlan(
                title = "Expertenbefehl ausführen",
                shellCommand = text.removePrefix("!").trim(),
                needsConfirmation = true
            )
        }

        return when {
            lower.contains("geräteinfo") || lower.contains("handyinfo") || lower == "info" ->
                CommandPlan(
                    "Geräteinformationen",
                    "echo Hersteller: $(getprop ro.product.manufacturer); " +
                        "echo Modell: $(getprop ro.product.model); " +
                        "echo Android: $(getprop ro.build.version.release); " +
                        "echo SDK: $(getprop ro.build.version.sdk); " +
                        "echo Gerät: $(getprop ro.product.device)"
                )

            lower.contains("speicher") ->
                CommandPlan("Speicherübersicht", "df -h /data /sdcard 2>/dev/null")

            lower.contains("downloads") || lower.contains("download") ->
                CommandPlan("Downloads", "ls -lah /sdcard/Download 2>/dev/null | head -100")

            lower.contains("installierte apps") || lower == "apps" || lower.contains("meine apps") ->
                CommandPlan(
                    "Installierte Benutzer-Apps",
                    "pm list packages -3 | sed 's/^package://' | sort"
                )

            lower.contains("screenshot") || lower.contains("bildschirmfoto") ->
                CommandPlan(
                    "Screenshot",
                    "out=/sdcard/Download/device-ai-$(date +%Y%m%d-%H%M%S).png; screencap -p \"$out\" && echo \"Gespeichert: $out\""
                )

            lower.contains("installiere") && lower.contains("apk") ->
                CommandPlan(
                    "Neueste APK installieren",
                    "apk=$(ls -t /sdcard/Download/*.apk 2>/dev/null | head -n 1); " +
                        "if [ -n \"$apk\" ]; then echo \"Installiere: $apk\"; pm install -r \"$apk\"; " +
                        "else echo \"Keine APK direkt im Download-Ordner gefunden.\"; fi",
                    needsConfirmation = true
                )

            lower.startsWith("neustart ") || (lower.startsWith("starte ") && lower.endsWith(" neu")) -> {
                val name = if (lower.startsWith("neustart ")) {
                    text.substringAfter(" ").trim()
                } else {
                    text.substringAfter(" ").removeSuffix(" neu").trim()
                }
                val pkg = resolvePackage(name) ?: return CommandPlan(
                    "App nicht gefunden",
                    "echo \"Keine installierte App für: ${sanitizeText(name)}\""
                )
                CommandPlan(
                    "App neu starten: $name",
                    "am force-stop ${safePackage(pkg)}; sleep 1; " +
                        "monkey -p ${safePackage(pkg)} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; " +
                        "echo \"Neu gestartet: $pkg\"",
                    needsConfirmation = true
                )
            }

            lower.startsWith("öffne ") || lower.startsWith("starte ") -> {
                val name = text.substringAfter(" ").trim()
                val pkg = resolvePackage(name) ?: return CommandPlan(
                    "App nicht gefunden",
                    "echo \"Keine installierte App für: ${sanitizeText(name)}\""
                )
                CommandPlan(
                    "App starten: $name",
                    "monkey -p ${safePackage(pkg)} -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 && echo \"Gestartet: $pkg\""
                )
            }

            lower.startsWith("beende ") || lower.startsWith("stoppe ") -> {
                val name = text.substringAfter(" ").trim()
                val pkg = resolvePackage(name) ?: return CommandPlan(
                    "App nicht gefunden",
                    "echo \"Keine installierte App für: ${sanitizeText(name)}\""
                )
                CommandPlan(
                    "App beenden: $name",
                    "am force-stop ${safePackage(pkg)} && echo \"Beendet: $pkg\"",
                    needsConfirmation = true
                )
            }

            lower.contains("hilfe") ->
                CommandPlan(
                    "Beispiele",
                    "printf '%s\n' 'Geräteinfo' 'Apps' 'Downloads' 'Speicher' 'Screenshot' " +
                        "'Öffne WhatsApp' 'Beende Lumo' 'Neustart Lumo' 'Installiere neueste APK' '! <Shell-Befehl>'"
                )

            else -> CommandPlan(
                "Nicht verstanden",
                "echo \"Ich habe das noch nicht erkannt. Tippe Hilfe für Beispiele.\""
            )
        }
    }

    private fun resolvePackage(query: String): String? {
        val q = query.trim().lowercase(Locale.GERMAN)
        if (q.matches(Regex("[a-zA-Z0-9_.]+")) && q.contains(".")) return q

        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .mapNotNull { app ->
                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrNull()
                    ?: return@mapNotNull null
                Triple(app.packageName, label, label.lowercase(Locale.GERMAN))
            }
            .sortedBy { it.second.length }
            .firstOrNull { (_, _, labelLower) ->
                labelLower == q || labelLower.contains(q) || q.contains(labelLower)
            }
            ?.first
    }

    private fun safePackage(value: String): String =
        value.takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) }
            ?: error("Ungültiger Paketname")

    private fun sanitizeText(value: String): String =
        value.replace(Regex("[\"'$\\]"), "")
}
