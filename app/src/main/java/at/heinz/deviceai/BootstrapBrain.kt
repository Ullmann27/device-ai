package at.heinz.deviceai

import java.util.Locale

/** Initial deterministic cognition. Never present these rules as an LLM. */
enum class BootstrapKind { REPLY, REMEMBER, FORGET, FEEDBACK_POSITIVE, FEEDBACK_NEGATIVE, DEVICE_TOOL, TRAIN }

data class BootstrapDecision(
    val kind: BootstrapKind,
    val message: String = "",
    val fact: String? = null,
    val tool: String? = null,
    val arguments: Map<String, Any?> = emptyMap()
)

object BootstrapBrain {
    private val unsafe = Regex(
        "(?i)\\b(passwort|password|kennwort|pin|iban|bic|kreditkarte|api[ _-]?key|secret|token|tanf?nummer|zugangscode)\\b"
    )

    fun decide(utterance: String, facts: List<String>,
               learnedCommands: Map<String, String> = emptyMap()): BootstrapDecision {
        val text = utterance.trim().take(2_048)
        val low = text.lowercase(Locale.ROOT)
        // Explicit memory deletion stays available, but always requires an Android confirmation.
        if (low == "lösche meine erinnerungen" || low == "lernspeicher löschen")
            return BootstrapDecision(BootstrapKind.FORGET)
        // The deterministic intent recognizer must not mistake a dangerous mixed
        // request or forged role header for permission to run a harmless-looking tool.
        if (Regex("""(?i)^(system|developer|assistant|tool)\s*:""").containsMatchIn(text) ||
            Regex("""(?i)\b(lösch\w*|entfern\w*|deinstallier(?:e|en|st)\b|installier(?:e|en|st)\b|verschieb\w*|starte\w*|stoppe?\w*|überweis\w*|bezahl\w*)\b""").containsMatchIn(text)
        ) return BootstrapDecision(
            BootstrapKind.REPLY,
            "Dieser Satz enthält eine nachgeahmte Rolle oder einen verändernden Geräteauftrag. " +
                "Der eigene Startkern führt deshalb keinerlei Gerätewerkzeug aus."
        )
        if (low.startsWith("trainiere:")) {
            val candidate = BootstrapTraining.parse(text)
            return if (candidate == null) BootstrapDecision(BootstrapKind.REPLY,
                "Format: Trainiere: <dein Satz> => Akku | Speicher | Downloads | Apps | Gerät | Netzwerk.")
            else BootstrapDecision(BootstrapKind.TRAIN,
                message = "Ich lerne den Befehl erst nach deiner Bestätigung.",
                fact = candidate.first, tool = candidate.second)
        }
        if (low.startsWith("merke dir:") || low.startsWith("merk dir:")) {
            val fact = text.substringAfter(':').trim()
            return if (fact.length in 3..320 && !unsafe.containsMatchIn(fact)) {
                BootstrapDecision(BootstrapKind.REMEMBER, fact = fact)
            } else BootstrapDecision(BootstrapKind.REPLY,
                "Das kann ich nicht speichern: Bitte 3–320 Zeichen, ohne Passwörter, Zugangsdaten oder Bankdaten.")
        }
        if (low == "vergiss alles" || low == "lösche meine erinnerungen" ||
            low == "lernspeicher löschen") return BootstrapDecision(BootstrapKind.FORGET)
        if (low == "das war hilfreich" || low == "gut gemacht") return BootstrapDecision(
            BootstrapKind.FEEDBACK_POSITIVE
        )
        if (low == "das war nicht hilfreich" || low == "das ist falsch") return BootstrapDecision(
            BootstrapKind.FEEDBACK_NEGATIVE
        )
        if (low.contains("was weißt du") || low.contains("was hast du dir gemerkt") ||
            low == "erinnerungen") return BootstrapDecision(BootstrapKind.REPLY,
                if (facts.isEmpty()) "Ich habe noch keine ausdrücklich bestätigten Fakten gespeichert." else
                    "Meine ausdrücklich gespeicherten Fakten:\\n" + facts.take(32).joinToString("\\n") { "• $it" })
        if (low == "hallo" || low == "hi" || low == "hey" || low == "guten morgen" ||
            low == "guten tag" || low == "servus") return BootstrapDecision(BootstrapKind.REPLY,
                "Hallo! Ich bin Device AI. Mein eigener lokaler Startkern ist aktiv. Was möchtest du mit mir ausprobieren?")
        if (low.contains("wer bist du") || low.contains("wie heißt du")) return BootstrapDecision(
            BootstrapKind.REPLY, "Ich bin Device AI Continuum – ein eigener Android-Startkern mit " +
                "lokaler, ausdrücklich gesteuerter Erinnerung. Ich bin noch kein großes Sprachmodell.")
        if (low.contains("was kannst du") || low == "hilfe" || low == "befehle") return BootstrapDecision(
            BootstrapKind.REPLY, "Ich kann dich begrüßen, per Android-TTS sprechen, Sprachtexte entgegennehmen, " +
                "Fakten auf Wunsch speichern, Feedback zählen und mit Shizuku lesend Akku, Speicher, " +
                "Downloads, Apps, Lumo-Fehler und Geräteinformationen prüfen. " +
                "Für Webrecherche tippe auf WEB RECHERCHE und gib direkte HTTPS-Quellen an. " +
                "Schreiben: „Merke dir: ...“, Abrufen: „Was weißt du?“, Löschen: „Vergiss alles“. " +
                "Du kannst mir neue Nur-Lese-Befehle beibringen mit „Trainiere: <Satz> => Akku“. " +
                "Freies Sprachdenken und autonomes Lernen sind noch Forschungsziele.")
        fun tool(name: String, args: Map<String, Any?> = emptyMap()): BootstrapDecision =
            BootstrapDecision(BootstrapKind.DEVICE_TOOL, tool = name, arguments = args)
        BootstrapTraining.resolve(text, learnedCommands)?.let { name ->
            return BootstrapDecision(BootstrapKind.DEVICE_TOOL, tool = name,
                arguments = if (name == "list_files") mapOf("path" to "/sdcard/Download") else emptyMap())
        }
        if ("fehler lumo" in low || ("lumo" in low && "logcat" in low))
            return tool("read_logcat", mapOf("packageOrName" to "Lumo", "lines" to 180))
        if ("lumo" in low && ("prüf" in low || "version" in low || "installiert" in low))
            return tool("get_app_info", mapOf("packageOrName" to "Lumo"))
        if ("downloads" in low || "download-ordner" in low) return tool("list_files",
            mapOf("path" to "/sdcard/Download"))
        if ("akku" in low || "batterie" in low || "ladezustand" in low)
            return tool("get_battery_info")
        if ("speicher" in low || "große dateien" in low) return tool("get_storage_info")
        if ("installierte apps" in low || "alle apps" in low) return tool("list_installed_apps")
        if ("gerät" in low && ("info" in low || "prüf" in low)) return tool("get_device_info")
        if ("netzwerk" in low || "internetverbindung" in low) return tool("get_network_summary")
        if ("internet" in low || "recherch" in low) return BootstrapDecision(
            BootstrapKind.REPLY, "Internetrecherche ist nur nach deiner Freigabe aktiv. " +
                "Tippe WEB RECHERCHE und nenne eine direkte HTTPS-Quelle. " +
                "Die Inhalte werden als untrusted data behandelt, nicht als Befehle.")
        return BootstrapDecision(BootstrapKind.REPLY,
            "Das kann mein eigener Startkern noch nicht verlässlich beantworten. " +
                "Ich erfinde keine Antwort. Probiere „Was kannst du?“, eine Geräteprüfung, " +
                "oder lerne mir etwas ausdrücklich mit „Merke dir: ...“.")
    }
}
