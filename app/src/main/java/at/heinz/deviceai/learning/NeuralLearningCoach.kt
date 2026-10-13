package at.heinz.deviceai.learning

import java.util.Locale

/** An explicit, bounded conversation for teaching the *existing* binary neural learner.
 * No general-purpose prompt, internet or device instruction is ever executed here.
 */
class NeuralLearningCoach {
    private var awaitingLabelFor: String? = null
    private var running = false

    data class Reply(
        val message: String,
        val approvedLesson: NeuralCommand? = null,
        val closed: Boolean = false
    )

    fun isActive() = running

    fun begin(): Reply {
        running = true
        awaitingLabelFor = null
        return Reply("LERN-GESPRÄCH AKTIV. Ich lerne kleine Sprachmuster, noch keine freien Gespräche. " +
            "Sag oder schreibe einen kurzen Beispielsatz (3–320 Zeichen). " +
            "Ich frage danach, ob es ein Gruß oder eine Frage ist. " +
            "Jede Gewichtsänderung braucht danach deine ausdrückliche Bestätigung. " +
            "Mit „Ende“ kannst du jederzeit aufhören.")
    }

    fun accept(text: String): Reply? {
        if (!running) return null
        val phrase = text.trim()
        val low = phrase.lowercase(Locale.ROOT)
        if (low in setOf("ende", "stopp", "lernmodus beenden", "neuron tutor aus")) {
            running = false
            awaitingLabelFor = null
            return Reply("Lern-Gespräch beendet. Bereits bestätigte Gewichte bleiben gespeichert.", closed = true)
        }
        val pending = awaitingLabelFor
        if (pending == null) {
            if (phrase.length !in 3..320 || phrase.startsWith("neuron", true) ||
                phrase.contains(Regex("""(?i)\b(passwort|password|token|api.?key|secret|iban|pin)\b"""))
            ) return Reply("Bitte nur einen kurzen, unkritischen Beispielsatz mit 3–320 Zeichen sagen. Keine Passwörter oder Zugangsdaten.")
            awaitingLabelFor = phrase
            return Reply("Beispiel gehört: „${phrase.take(120)}“. War das ein **Gruß** oder eine **Frage**? " +
                "Sag „Gruß“ oder „Frage“, oder „Ende“.")
        }
        val label = when(low) { "gruß", "gruss" -> 0; "frage" -> 1; else -> null }
            ?: return Reply("Bitte antworte mit „Gruß“, „Frage“ oder „Ende“. Noch nichts trainiert.")
        awaitingLabelFor = null
        return Reply("Beispiel klassifiziert. Ich bitte nun um deine Trainingsfreigabe. Danach kannst du mir ein weiteres Beispiel sagen.",
            NeuralCommand(NeuralCommandKind.TEACH, pending, label))
    }
}
