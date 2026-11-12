package at.heinz.deviceai.cognitive

import java.util.Locale

internal enum class ComputerApp(val discoveryQuery: String, val aliases: List<String>) {
    WHATSAPP("WhatsApp", listOf("whatsapp", "whats app")),
    FACEBOOK("Facebook", listOf("facebook")),
    FILES("Eigene Dateien", listOf("samsung eigene dateien", "eigene dateien", "meine dateien", "dateien")),
    WORD("Word", listOf("microsoft word", "word")),
    EXCEL("Excel", listOf("microsoft excel", "excel")),
    CHROME("Chrome", listOf("google chrome", "chrome")),
    SETTINGS("Einstellungen", listOf("android-einstellungen", "einstellungen")),
    CAMERA("Kamera", listOf("kamera-app", "kamera")),
    GALLERY("Galerie", listOf("samsung galerie", "galerie")),
    CALENDAR("Kalender", listOf("kalender"))
}

internal enum class ComputerGoal { OPEN, SEARCH, OPEN_DOWNLOADS, DRAFT_MESSAGE, SEND_MESSAGE }
internal enum class ComputerPlanStep { OPEN_APP, OBSERVE_APP, OPEN_SEARCH, SET_QUERY, VERIFY_SEARCH,
    OPEN_FOLDER, VERIFY_FOLDER, CHOOSE_CONTACT, OPEN_CONTACT, SET_DRAFT, VERIFY_DRAFT, REQUIRE_SEND_APPROVAL }

internal data class OwnerComputerIntent(
    val app: ComputerApp,
    val goal: ComputerGoal,
    val query: String? = null,
    val draft: String? = null
) {
    val steps: List<ComputerPlanStep> get() = buildList {
        add(ComputerPlanStep.OPEN_APP); add(ComputerPlanStep.OBSERVE_APP)
        if (goal == ComputerGoal.OPEN_DOWNLOADS) {
            add(ComputerPlanStep.OPEN_FOLDER); add(ComputerPlanStep.VERIFY_FOLDER)
        }
        if (goal in setOf(ComputerGoal.SEARCH, ComputerGoal.DRAFT_MESSAGE, ComputerGoal.SEND_MESSAGE)) {
            add(ComputerPlanStep.OPEN_SEARCH); add(ComputerPlanStep.SET_QUERY); add(ComputerPlanStep.VERIFY_SEARCH)
        }
        if (goal in setOf(ComputerGoal.DRAFT_MESSAGE, ComputerGoal.SEND_MESSAGE)) {
            add(ComputerPlanStep.CHOOSE_CONTACT); add(ComputerPlanStep.OPEN_CONTACT)
            add(ComputerPlanStep.SET_DRAFT); add(ComputerPlanStep.VERIFY_DRAFT)
        }
        if (goal == ComputerGoal.SEND_MESSAGE) add(ComputerPlanStep.REQUIRE_SEND_APPROVAL)
    }
}

/** Parsing grants no device permission. */
internal object OwnerComputerIntentParser {
    sealed interface Result {
        data class Ready(val intent: OwnerComputerIntent) : Result
        data class Ask(val question: String) : Result
        data class Unsupported(val reason: String) : Result
        data object NotComputerCommand : Result
    }

    private val apps = ComputerApp.entries.flatMap { app -> app.aliases.map { it to app } }
        .sortedByDescending { it.first.length }
    private val OPEN_ONLY_APPS = setOf(ComputerApp.CHROME, ComputerApp.SETTINGS,
        ComputerApp.CAMERA, ComputerApp.GALLERY, ComputerApp.CALENDAR)
    private val otherAction = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:und|dann|danach)\\s+(?:sende?|schicke?|lösche?|loesche?|poste?|teile?|kaufe?|rufe?|öffne?|oeffne?|starte?|suche?)(?![\\p{L}\\p{N}_])")
    private val searchLabel = Regex("(?iu)^(?:und\\s+)?(?:suche|such|suche nach|such nach)\\s+(?:nach\\s+)?(.+)$")

    fun parse(raw: String, contextApp: ComputerApp? = null): Result {
        if (raw.length !in 1..2_000 || raw.any { it.isISOControl() && it != '\n' }) return Result.NotComputerCommand
        val colon = raw.indexOf(':')
        var text = if (colon < 0) raw.trim().replace(Regex("\\s+"), " ") else
            raw.substring(0, colon).trim().replace(Regex("\\s+"), " ") + ":" + raw.substring(colon + 1)
        text = text.replace(Regex("(?iu)^(?:kannst du|könntest du|koenntest du)\\s+(?:bitte\\s+)?"), "")
            .replace(Regex("(?iu)^bitte\\s+"), "").trim()
        if (text.isEmpty()) return Result.NotComputerCommand
        val lower = text.lowercase(Locale.ROOT)
        if (Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:nicht|keinesfalls|niemals)(?![\\p{L}\\p{N}_])").containsMatchIn(text.substringBefore(':'))) {
            return if (apps.any { lower.contains(it.first) }) Result.Unsupported("Der verneinte App-Auftrag wird nicht ausgeführt.")
                else Result.NotComputerCommand
        }
        parseMessage(text, contextApp)?.let { return it }
        text = text.trimEnd('.', '?', '!').trim()
        Regex("(?iu)^(?:öffne|oeffne|starte|open)\\s+(.+)$").matchEntire(text)?.let { match ->
            val rest = match.groupValues[1].replace(Regex("(?iu)^bitte\\s+"), "")
            if (rest.matches(Regex("(?iu)(?:den\\s+)?download[- ]?ordner|downloads"))) {
                return Result.Ready(OwnerComputerIntent(ComputerApp.FILES, ComputerGoal.OPEN_DOWNLOADS))
            }
            val found = appPrefix(rest) ?: return Result.NotComputerCommand
            val suffix = rest.substring(found.first.length).trim()
            if (suffix.isEmpty()) return Result.Ready(OwnerComputerIntent(found.second, ComputerGoal.OPEN))
            if (found.second == ComputerApp.FILES && suffix.matches(Regex("(?iu)und\\s+(?:öffne\\s+)?(?:den\\s+)?downloads?(?:[- ]ordner)?"))) {
                return Result.Ready(OwnerComputerIntent(found.second, ComputerGoal.OPEN_DOWNLOADS))
            }
            val search = searchLabel.matchEntire(suffix)
                ?: return Result.Unsupported("Der zusätzliche App-Schritt wird noch nicht sicher verstanden.")
            return search(found.second, search.groupValues[1])
        }
        Regex("(?iu)^(?:suche|such)\\s+(?:in|auf)\\s+(.+)$").matchEntire(text)?.let { match ->
            val found = appPrefix(match.groupValues[1]) ?: return Result.NotComputerCommand
            val query = match.groupValues[1].substring(found.first.length).trim()
                .replace(Regex("(?iu)^nach\\s+"), "")
            return search(found.second, query)
        }
        apps.firstOrNull { lower == "${it.first} öffnen" || lower == "${it.first} oeffnen" }?.let {
            return Result.Ready(OwnerComputerIntent(it.second, ComputerGoal.OPEN))
        }
        return Result.NotComputerCommand
    }

    private fun search(app: ComputerApp, raw: String): Result {
        if (app in OPEN_ONLY_APPS)
            return Result.Unsupported("In ${app.discoveryQuery} ist nur das bestätigte Öffnen mit Livebild freigegeben.")
        val query = raw.trim().trim('"', '„', '“').trim()
        if (query.isBlank()) return Result.Ask("Wonach soll ich in ${app.discoveryQuery} suchen?")
        if (query.length > 200 || otherAction.containsMatchIn(query)) {
            return Result.Unsupported("Bitte nenne einen eindeutigen Suchauftrag ohne weitere Aktion.")
        }
        return Result.Ready(OwnerComputerIntent(app, ComputerGoal.SEARCH, query))
    }

    private fun appPrefix(text: String): Pair<String, ComputerApp>? = apps.firstOrNull { (alias, _) ->
        text.equals(alias, true) || (text.startsWith(alias, true) && text.getOrNull(alias.length)?.isWhitespace() == true)
    }

    private fun parseMessage(text: String, contextApp: ComputerApp?): Result? {
        if (!Regex("(?iu)^(?:bereite|verfasse|schreibe|schreib|sende|schicke)(?![\\p{L}\\p{N}_])").containsMatchIn(text)) return null
        val colon = text.indexOf(':')
        val command = if (colon < 0) text else text.substring(0, colon)
        val draft = if (colon < 0) null else text.substring(colon + 1).trim()
        val appMatches = apps.filter { (alias, _) -> Regex("(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(alias)}(?![\\p{L}\\p{N}])").containsMatchIn(command) }
        val distinct = appMatches.map { it.second }.distinct()
        val explicitMessage = Regex("(?iu)(?<![\\p{L}\\p{N}_])(?:nachricht|nachrichtenentwurf)(?![\\p{L}\\p{N}_])")
            .containsMatchIn(command)
        if (distinct.isEmpty() && contextApp == null && !explicitMessage) return null
        if (distinct.size > 1) return Result.Ask("In welcher App soll ich den Entwurf vorbereiten?")
        val app = distinct.singleOrNull() ?: contextApp
            ?: return Result.Ask("In welcher App soll ich den Entwurf vorbereiten?")
        if (app != ComputerApp.WHATSAPP) return Result.Unsupported("Nachrichtenentwürfe sind für diese App noch nicht sicher angebunden.")
        var remaining = command
        appMatches.map { it.first }.sortedByDescending { it.length }.forEach { alias ->
            remaining = remaining.replace(Regex("(?iu)\\s+(?:in|auf|über|ueber)\\s+${Regex.escape(alias)}(?![\\p{L}\\p{N}_])"), "")
        }
        remaining = remaining.replace(Regex("(?iu)^(?:bereite|verfasse|schreibe|schreib|sende|schicke)\\s+"), "")
            .replace(Regex("(?iu)^(?:eine\\s+)?(?:whatsapp[- ]?)?nachricht\\s+(?:an\\s+)?"), "")
            .replace(Regex("(?iu)^an\\s+"), "")
            .replace(Regex("(?iu)\\s+vor$"), "").trim()
        if (remaining.isBlank()) return Result.Ask("An wen soll ich den Entwurf vorbereiten?")
        if (remaining.length > 120 || otherAction.containsMatchIn(remaining)) return Result.Unsupported("Der Empfänger ist nicht eindeutig beschrieben.")
        if (draft.isNullOrBlank()) return Result.Ask("Welchen Text soll ich als Entwurf eintragen?")
        if (draft.length > 500 || draft.any { it.isISOControl() }) return Result.Unsupported("Der Text überschreitet die sichere Eingabegrenze von 500 Zeichen.")
        val send = Regex("(?iu)^(?:sende|schicke)(?![\\p{L}\\p{N}_])").containsMatchIn(text)
        return Result.Ready(OwnerComputerIntent(app,
            if (send) ComputerGoal.SEND_MESSAGE else ComputerGoal.DRAFT_MESSAGE, remaining, draft))
    }
}
