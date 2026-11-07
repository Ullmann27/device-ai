package at.heinz.deviceai.learning

import java.util.Locale

/** An explicit, bounded conversation for teaching the *existing* binary neural learner.
 * No general-purpose prompt, internet or device instruction is ever executed here.
 */
class NeuralLearningCoach {
    private var awaitingLabelFor: String? = null
    private val queuedExamples = java.util.ArrayDeque<String>()
    private var batchTotal = 0
    private var batchIndex = 0
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
        queuedExamples.clear()
        batchTotal = 0
        batchIndex = 0
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
            queuedExamples.clear()
            batchTotal = 0
            batchIndex = 0
            return Reply("Lern-Gespräch beendet. Bereits bestätigte Gewichte bleiben gespeichert.", closed = true)
        }
        val pending = awaitingLabelFor
        if (pending == null) {
            val examples = extractExamples(phrase)
            if (examples.isEmpty())
                return Reply("Bitte nur einen kurzen, unkritischen Beispielsatz mit 3–320 Zeichen sagen. Keine Passwörter oder Zugangsdaten.")
            awaitingLabelFor = examples.first()
            queuedExamples.clear()
            examples.drop(1).forEach(queuedExamples::addLast)
            batchTotal = examples.size
            batchIndex = 1
            val prefix = if (examples.size > 1)
                "Ich habe ${examples.size} getrennte Beispiele erkannt und gehe sie nacheinander durch. "
                else ""
            val count = if (examples.size > 1) "1/${examples.size} " else ""
            return Reply(prefix + "Beispiel " + count + "gehört: „${examples.first().take(120)}“. " +
                "War das ein **Gruß** oder eine **Frage**? Sag „Gruß“ oder „Frage“, oder „Ende“.")
        }
        val label = when(low) { "gruß", "gruss" -> 0; "frage" -> 1; else -> null }
            ?: return Reply("Bitte antworte mit „Gruß“, „Frage“ oder „Ende“. Noch nichts trainiert.")
        val command=NeuralCommand(NeuralCommandKind.TEACH, pending, label)
        val next=queuedExamples.pollFirst()
        if(next==null) {
            awaitingLabelFor = null
            batchTotal = 0
            batchIndex = 0
            return Reply("Beispiel klassifiziert. Ich bitte nun um deine Trainingsfreigabe. Danach kannst du mir ein weiteres Beispiel sagen.",
                command)
        }
        batchIndex += 1
        awaitingLabelFor = next
        return Reply("Beispiel klassifiziert; die Trainingsfreigabe folgt separat. " +
            "Nächstes Beispiel ${batchIndex}/${batchTotal}: „${next.take(120)}“. " +
            "War das ein **Gruß** oder eine **Frage**?", command)
    }

    private fun extractExamples(text:String):List<String> {
        if(text.length !in 3..1600)return emptyList()
        val quoted = buildList {
            Regex("„([^„“]{3,320})“").findAll(text).forEach { add(it.range.first to it.groupValues[1].trim()) }
            Regex("\"([^\"]{3,320})\"").findAll(text).forEach { add(it.range.first to it.groupValues[1].trim()) }
        }.sortedBy { it.first }.map { it.second }.distinct()
        val candidates = if(quoted.size>=2) quoted else listOf(text.trim())
        return candidates.filter(::validExample).take(12)
    }

    private fun validExample(text:String):Boolean =
        text.length in 3..320 &&
            !text.startsWith("neuron",true) &&
            !text.contains(Regex("""(?i)\b(passwort|password|token|api.?key|secret|iban|pin)\b"""))
}
