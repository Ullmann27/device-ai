package at.heinz.deviceai

import java.util.Locale
import at.heinz.deviceai.learning.KnowledgeSyntax
import at.heinz.deviceai.learning.StructuredKnowledge
import at.heinz.deviceai.learning.OnlineNaiveBayes
import at.heinz.deviceai.learning.NeuralDialogue
import at.heinz.deviceai.cognitive.NetworkTurnConstraint

/** Initial deterministic cognition. Never present these rules as an LLM. */
enum class BootstrapKind { REPLY, REMEMBER, FORGET, FEEDBACK_POSITIVE, FEEDBACK_NEGATIVE, DEVICE_TOOL, TRAIN, REVISE }

data class BootstrapDecision(
    val kind: BootstrapKind,
    val message: String = "",
    val fact: String? = null,
    val tool: String? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val replacementFact: String? = null,
    val allowNeuralRecall: Boolean = false
)

object BootstrapBrain {
    private val unsafe = Regex(
        "(?i)\\b(passwort|password|kennwort|pin|iban|bic|kreditkarte|api[ _-]?key|secret|token|tanf?nummer|zugangscode)\\b"
    )
    // Read-only storage inspection is distinct from a request to SAVE data.
    // Unicode lookarounds behave consistently on JVM and Android, unlike
    // implementation-dependent word boundaries after accented characters.
    private val storageNoun = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:speicher|speicherplatz|speicherstatus|speicherkapazität)(?![\p{L}\p{N}_])"""
    )
    private val storageReadCue = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:prüfe|prüfen|prüf|überprüfe|zeige|zeigen|zeig|anzeigen|kontrolliere|wie viel|wieviel|wie voll|belegt|frei|freien|freier|verfügbar|status|kapazität|auslastung)(?![\p{L}\p{N}_])"""
    )
    private val storageSaveImperative = Regex(
        """(?iu)^\s*(?:bitte\s+)?speicher\s+(?:(?:mir|dir)\s+)?(?:das|dies|diese|dieses|diesen|die|den|dem|meine|unsere|folgende|angaben|daten|zahlen|text|texte|bild|bilder|datei|dateien|notiz|notizen|nicht|nichts)(?![\p{L}\p{N}_])"""
    )
    // Contrasting clauses have independent intent: "nicht Web, sondern prüfe Speicher"
    // may still inspect local storage, while "prüfe nicht den Speicher" never does.
    private val storageNegation = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:nicht|niemals|ohne|unterlasse|weder|kein\p{L}*)(?![\p{L}\p{N}_])"""
    )
    // ", nicht/aber nicht/jedoch kein…" starts an independent contrast clause. A comma
    // before "dass/welche/…" (subordinate clause) is NOT a boundary: the negation of
    // the main clause ("Ich will nicht, dass …") must keep covering it.
    private val storageClauseBoundary = Regex(
        """(?iu)\bsondern\b|[.;!?]|,(?=\s*(?:(?:aber|jedoch|doch)\s+)?(?:nicht|niemals|kein\p{L}*)(?![\p{L}\p{N}_]))"""
    )
    private val batteryNoun = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:akku|batterie|ladezustand|akkustand)(?![\p{L}\p{N}_])"""
    )
    private val batteryReadCue = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:prüfe|prüfen|prüf|überprüfe|zeige|zeigen|zeig|wie viel|wieviel|wie voll|wie ist|wie steht|wie hoch|lade|ladung|ladestand|status|stand|zustand)(?![\p{L}\p{N}_])"""
    )
    private fun isBatteryReadRequest(input: String): Boolean =
        input.trim().split(storageClauseBoundary).any { untrimmed ->
            val text = untrimmed.trim()
            !clauseNegation.containsMatchIn(text) &&
                (text in setOf("akku", "batterie", "ladezustand", "akkustand") ||
                    (batteryNoun.containsMatchIn(text) && batteryReadCue.containsMatchIn(text)))
        }

    // Every read-only device cue is clause-scoped like storage/battery: a negated
    // clause never starts a tool, an independent affirmative clause still may.
    private fun affirmativeClause(input: String, cue: (String) -> Boolean): Boolean =
        input.trim().split(storageClauseBoundary).any { untrimmed ->
            val text = untrimmed.trim()
            text.isNotBlank() && !clauseNegation.containsMatchIn(text) && cue(text)
        }
    private val appsCue = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:installierte[nm]?\s+apps|alle\s+apps|apps\s+(?:sind\s+)?installiert)(?![\p{L}\p{N}_])"""
    )
    private val wlanReadCue = Regex("""(?iu)(?<![\p{L}])(?:verbunden|verbindung|status|zeig\p{L}*|prüf\p{L}*|signal)(?![\p{L}])""")
    // "Prüf nicht X, sondern Y": the read verb of the negated clause applies to the contrast clause.
    private val contrastVerb = Regex("""(?iu)^(\s*(?:bitte\s+)?(?:prüfe?|überprüfe|zeige?|kontrolliere)\b)(.*?\bnicht\b[^.;!?]*?)\bsondern\b""")
    private fun carryContrastVerb(low: String): String =
        contrastVerb.find(low)?.let { m -> low.replaceRange(m.range, m.value + " " + m.groupValues[1].trim()) } ?: low
    private val appMutation = Regex("""(?iu)(?<![\p{L}\p{N}_])(?:schließ\p{L}*|schliess\p{L}*|beend\p{L}*|stopp\p{L}*|aktualisier\p{L}*)""")
    // Clause negation de/en for every read-only device cue. Superset of storageNegation:
    // adds "nie/weder" and English "not/never/no/without/don't/…n't" (straight or typographic
    // apostrophe). Fail-closed: a negated clause never starts a tool; the model answers.
    private val clauseNegation = Regex(
        """(?iu)(?<![\p{L}\p{N}_])(?:nicht|nie|niemals|ohne|unterlasse|weder|kein\p{L}*|not|never|no|without|dont|\p{L}*n['\u2019]t)(?![\p{L}\p{N}_])"""
    )

    private fun isStorageReadRequest(input: String): Boolean {
        val clauses = input.trim().split(storageClauseBoundary)
        val bare = setOf("speicher", "speicherstatus", "speicherplatz", "speicherkapazität")
        return clauses.any { untrimmed ->
            val text = untrimmed.trim()
            when {
                text.isBlank() || storageSaveImperative.containsMatchIn(text) -> false
                clauseNegation.containsMatchIn(text) -> false
                text in bare -> true
                else -> storageNoun.containsMatchIn(text) && storageReadCue.containsMatchIn(text)
            }
        }
    }

    /** These routes read existing owner statements. The Activity resolves them through the checked ledger on IO. */
    fun requiresOwnerFacts(utterance: String): Boolean {
        val low = utterance.trim().lowercase(Locale.ROOT)
        return low.startsWith("korrigiere:") || low.startsWith("widerrufe:") || low.startsWith("prüfe wissen:") ||
            low.contains("was weißt du") || low.contains("was hast du dir gemerkt") || low == "erinnerungen" ||
            ownerRecall.containsMatchIn(low)
    }

    /**
     * #567 (Fold7 Code43 test 04): „Welches Testwort habe ich dir gerade genannt?“ and „Was hast du dir über mein
     * Testwort gemerkt?“ ask for the owner's own earlier statements; they are answered from the checked ledger.
     */
    private val ownerRecall = Regex(
        "(?u)\\b(was|welche[nmrs]?|wie)\\b.*\\b(habe|hab|hatte)\\s+ich\\s+(dir|dich)\\b|" +
            "\\b(was|welche[nmrs]?)\\b.*\\bhast\\s+du\\s+dir\\b.*\\bgemerkt\\b"
    )
    private val recallStopWords = setOf("mein", "meine", "meinen", "meiner", "meinem", "habe", "hast", "dich", "welche",
        "welches", "welcher", "welchen", "gerade", "vorhin", "genannt", "gesagt", "gemerkt", "erzählt", "über", "deinem",
        "lokalen", "gedächtnis", "antworte", "ausschließlich", "starte", "keine", "internetrecherche", "recherche",
        "lautet", "heißt", "ist", "sind", "nicht")
    private fun recallTokens(text: String) = Regex("[\\p{L}\\p{N}]{4,}").findAll(text.lowercase(Locale.ROOT))
        .map { it.value }.filter { it !in recallStopWords }.toSet()

    /**
     * A mutating verb that is itself negated („starte keine Internetrecherche“) is not a device order. Only the
     * explicit kein/keine/nichts forms count, and never in a contrast („…, sondern lösche …“), so a real order stays
     * blocked; „nicht“ is not exempted („Lösche nicht nur den Cache …“).
     */
    private val negatedMutation = Regex(
        "(?iu)\\b(lösch\\w*|entfern\\w*|deinstallier\\w*|installier\\w*|verschieb\\w*|starte\\w*|stoppe?\\w*|überweis\\w*|bezahl\\w*)\\s+(keine|keinen|kein|keinerlei|nichts)\\b"
    )
    private val contrast = Regex("(?iu)\\b(sondern|stattdessen|außer|aber)\\b")

    fun decide(utterance: String, facts: List<String>,
               learnedCommands: Map<String, String> = emptyMap()): BootstrapDecision {
        if (utterance.length > 2_048) return BootstrapDecision(BootstrapKind.REPLY,
            "Eingabe zu lang; bitte höchstens 2048 Zeichen. Es wurde nichts ausgeführt.")
        val text = utterance.trim()
        val low = carryContrastVerb(text.lowercase(Locale.ROOT))
        // Explicit memory deletion stays available, but always requires an Android confirmation.
        if (low == "lösche meine erinnerungen" || low == "lernspeicher löschen")
            return BootstrapDecision(BootstrapKind.FORGET)
        // The deterministic intent recognizer must not mistake a dangerous mixed
        // request or forged role header for permission to run a harmless-looking tool.
        val safetyText = if (contrast.containsMatchIn(text)) text else text.replace(negatedMutation, " ")
        if (Regex("""(?i)^(system|developer|assistant|tool)\s*:""").containsMatchIn(text) ||
            Regex("""(?i)\b(lösch\w*|entfern\w*|deinstallier(?:e|en|st)?\b|installier(?:e|en|st)?\b|verschieb\w*|starte\w*|stoppe?\w*|überweis\w*|bezahl\w*)\b""").containsMatchIn(safetyText)
        ) return BootstrapDecision(
            BootstrapKind.REPLY,
            "Dieser Satz enthält eine nachgeahmte Rolle oder einen verändernden Geräteauftrag. " +
                "Der eigene Startkern führt deshalb keinerlei Gerätewerkzeug aus."
        )
        if (NeuralDialogue.accepts(text)) return BootstrapDecision(BootstrapKind.REPLY,
            "Neuronales Experiment: Öffne das Lernlabor. Neuron Demo · Neuron: Hallo")
        if (low.startsWith("korrigiere:") || low.startsWith("widerrufe:")) {
            val parts = text.substringAfter(':').split("=>").map(String::trim)
            val correcting = low.startsWith("korrigiere:")
            val old = parts.first()
            val replacement = if (correcting) parts.getOrNull(1) else null
            if (parts.size != (if (correcting) 2 else 1) || old !in facts ||
                (correcting && (replacement == null || unsafe.containsMatchIn(replacement) ||
                    KnowledgeSyntax.statement(replacement) == null)))
                return BootstrapDecision(BootstrapKind.REPLY,
                    "Nicht geändert. Format: Korrigiere: <exakter alter Fakt> => Fakt: Name | Beziehung | Wert; oder Widerrufe: <exakter Fakt>.")
            return BootstrapDecision(BootstrapKind.REVISE, fact = old, replacementFact = replacement)
        }
        if (low.startsWith("prüfe wissen:")) {
            val query = KnowledgeSyntax.literal(text.substringAfter(':'))
                ?: return BootstrapDecision(BootstrapKind.REPLY, "Format: Prüfe Wissen: Name | Beziehung | Wert")
            return BootstrapDecision(BootstrapKind.REPLY, runCatching { StructuredKnowledge().query(facts, query).describe() }
                .getOrDefault("UNGEKLÄRT – gespeicherte Fakten überschreiten die zulässigen Grenzen."))
        }
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
            return if (fact.length in 3..320 && !unsafe.containsMatchIn(fact) &&
                (!KnowledgeSyntax.isStructured(fact) || KnowledgeSyntax.statement(fact) != null)) {
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
        if (ownerRecall.containsMatchIn(low) && !low.contains("was hast du dir gemerkt")) {
            val wanted = recallTokens(text)
            // Without a topic word („Was habe ich dir gerade gesagt?“) every confirmed fact is relevant.
            val matching = if (wanted.isEmpty()) facts else facts.filter { fact -> recallTokens(fact).any { it in wanted } }
            return BootstrapDecision(BootstrapKind.REPLY, if (matching.isEmpty())
                "Dazu habe ich keine ausdrücklich gespeicherte Erinnerung gefunden. Ich habe nichts im Internet gesucht. " +
                    "Mit „Merke dir: …“ kannst du es mir sagen."
            else "Dazu habe ich mir gemerkt:\n" + matching.take(8).joinToString("\n") { "• $it" } +
                "\n(Bestätigte Nutzererinnerung · nur lokal, keine Internetrecherche)")
        }
        if (low.contains("was weißt du") || low.contains("was hast du dir gemerkt") ||
            low == "erinnerungen") return BootstrapDecision(BootstrapKind.REPLY,
                if (facts.isEmpty()) "Ich habe noch keine ausdrücklich bestätigten Fakten gespeichert." else
                    "Meine ausdrücklich gespeicherten Fakten:\n" + facts.take(32).joinToString("\n") { "• $it" })
        if (low == "hallo" || low == "hi" || low == "hey" || low == "guten morgen" ||
            low == "guten tag" || low == "servus") return BootstrapDecision(BootstrapKind.REPLY,
                "Hallo! Ich bin Device AI. Mein eigener lokaler Startkern ist aktiv. Was möchtest du mit mir ausprobieren?")
        if (low.contains("wer bist du") || low.contains("wie heißt du")) return BootstrapDecision(
            BootstrapKind.REPLY, "Ich bin Device AI Continuum – ein eigener Android-Startkern mit " +
                "lokaler, ausdrücklich gesteuerter Erinnerung. Ich bin noch kein großes Sprachmodell.")
        // Harmless self-capability questions may tolerate common German/Austrian variants.
        // This never rewrites, confirms or triggers a device or coding instruction.
        if (low.contains("was kannst du") || low.contains("was kanst du") ||
            low.contains("wos kannst du") || low.contains("wos konnst du") ||
            low == "hilfe" || low == "befehle"
        ) return BootstrapDecision(
            BootstrapKind.REPLY,
            "Servus! Ich bin Continuum. Du kannst mit mir auf Deutsch schreiben und " +
                "die Android-Sprachausgabe verwenden. Ich kann ausdrücklich bestätigte Fakten " +
                "lokal speichern, gespeicherte Beziehungen prüfen und bei fehlenden Belegen " +
                "auch sagen, dass ich etwas noch nicht weiß. Mit deiner Freigabe kann ich " +
                "Gerätedaten wie Akku und Speicher nur lesend prüfen. Im Lernzentrum gibt es " +
                "Lernexperimente und verschiedene Gesprächsübungen. Der Entwicklungsbereich " +
                "enthält einen experimentellen CCC1-Reparaturagenten und einen getrennten " +
                "Android-Projektgenerator; ein vollständig autonomer App-Build auf diesem " +
                "Telefon ist noch nicht nachgewiesen. Ein lokal geladenes Sprachmodell " +
                "kann weitere Fragen beantworten, wenn es eingerichtet und bereit ist. " +
                "Natürliches freies Gespräch und selbstständiges Lernen bleiben wichtige " +
                "Forschungsziele. Was möchtest du als Erstes probieren – reden, etwas " +
                "lernen, dein Gerät prüfen oder eine kleine App planen?"
        )
        fun tool(name: String, args: Map<String, Any?> = emptyMap()): BootstrapDecision =
            BootstrapDecision(BootstrapKind.DEVICE_TOOL, tool = name, arguments = args)
        BootstrapTraining.resolve(text, learnedCommands)?.let { name ->
            return BootstrapDecision(BootstrapKind.DEVICE_TOOL, tool = name,
                arguments = if (name == "list_files") mapOf("path" to "/sdcard/Download") else emptyMap())
        }
        if (affirmativeClause(low) { "fehler lumo" in it || ("lumo" in it && "logcat" in it) })
            return tool("read_logcat", mapOf("packageOrName" to "Lumo", "lines" to 180))
        if (affirmativeClause(low) { "lumo" in it && ("prüf" in it || "version" in it || "installiert" in it) })
            return tool("get_app_info", mapOf("packageOrName" to "Lumo"))
        if (affirmativeClause(low) { "downloads" in it || "download-ordner" in it }) return tool("list_files",
            mapOf("path" to "/sdcard/Download"))
        val wantsStorageInspection = isStorageReadRequest(low) ||
            affirmativeClause(low) { "große dateien" in it }
        val wantsBatteryInspection = isBatteryReadRequest(low)
        // A single read-only bootstrap action must not guess between two tools.
        if (wantsStorageInspection && wantsBatteryInspection) return BootstrapDecision(
            BootstrapKind.REPLY,
            "Du fragst nach Akku und Speicher zugleich. Welchen Wert soll ich zuerst prüfen?",
            allowNeuralRecall = true
        )
        if (wantsStorageInspection) return tool("get_storage_info")
        if (wantsBatteryInspection) return tool("get_battery_info")
        if (affirmativeClause(low) { appsCue.containsMatchIn(it) && !appMutation.containsMatchIn(it) })
            return tool("list_installed_apps")
        if (affirmativeClause(low) { "gerät" in it && ("info" in it || "prüf" in it) }) return tool("get_device_info")
        // An explicit offline instruction is neither a web-search request nor
        // a reason to withhold a local model attempt. The ToolGate applies the
        // independent hard veto to any network execution.
        if (NetworkTurnConstraint.forbidsExternalResearch(text))
            return BootstrapDecision(BootstrapKind.REPLY,
                "Ich beantworte diese Anfrage nur aus lokal verfügbaren Informationen. " +
                    "Falls dafür keine ausreichenden Belege vorliegen, sage ich das ausdrücklich.",
                allowNeuralRecall = true)
        if (affirmativeClause(low) { "netzwerk" in it || "internetverbindung" in it ||
                ("wlan" in it && wlanReadCue.containsMatchIn(it)) }) return tool("get_network_summary")
        if ("internet" in low || "recherch" in low) return BootstrapDecision(
            BootstrapKind.REPLY, "Internetrecherche ist nur nach deiner Freigabe aktiv. " +
                "Tippe WEB RECHERCHE und nenne eine direkte HTTPS-Quelle. " +
                "Die Inhalte werden als untrusted data behandelt, nicht als Befehle.")
        if (text.length <= 320 && learnedCommands.isNotEmpty()) {
            val learner = OnlineNaiveBayes()
            learnedCommands.entries.take(24).forEach { (phrase, target) ->
                if (BootstrapTraining.validate(phrase, target) != null)
                    runCatching { learner.learn(phrase, target, approved = true) }
            }
            learner.predict(text)?.let { suggestion ->
                val example = learnedCommands.entries.firstOrNull { it.value == suggestion.label && BootstrapTraining.validate(it.key, it.value) != null }
                if (example != null) return BootstrapDecision(BootstrapKind.REPLY,
                    "Vermutung aus deinen bestätigten Beispielen: ${ToolRegistry.label(suggestion.label)}. " +
                        "Nichts ausgeführt. Falls passend, schreibe den bestätigten Befehl: „${example.key}“.")
            }
        }
        return BootstrapDecision(BootstrapKind.REPLY,
            "Das kann mein eigener Startkern noch nicht verlässlich beantworten. " +
                "Ich erfinde keine Antwort. Probiere „Was kannst du?“, eine Geräteprüfung, " +
                "oder lerne mir etwas ausdrücklich mit „Merke dir: ...“.", allowNeuralRecall = true)
    }
}


