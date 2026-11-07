package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Expands the fixed interdisciplinary bootstrap syllabus using repeated,
 * source-bound terms from the existing Mentor notebook.
 *
 * Extracted terms are only search topics for the already constrained German
 * Wikipedia provider. They never become commands, facts, labels or tool input.
 */
object MentorCuriosityPlanner {
    private val valid=Regex("[\\p{L}]{5,28}")
    private val stop=setOf(
        "und","oder","sowie","aber","dass","dieser","diese","dieses","einer","einem",
        "einen","werden","wurde","wird","sind","ist","auch","nicht","durch","über",
        "unter","zwischen","gegen","ohne","nach","beim","deren","dessen","mehr",
        "heute","dabei","damit","kann","können","wurde","wurden","eine","eines",
        "lösche","loesche","öffne","oeffne","starte","installiere","deinstalliere",
        "passwort","token","dateien","kamera","mikrofon","shell","befehl"
    )

    private fun terms(text:String):List<String> =
        text.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}]+"))
            .asSequence().filter { valid.matches(it) && it !in stop }
            .take(80).toList()

    private val fixedTerms: Set<String> by lazy {
        MentorCurriculum.topics.flatMap { topic -> terms(topic.area+" "+topic.query) }.toSet()
    }

    fun select(attempt:Long,notes:List<MentorSource>):MentorCurriculum.Topic {
        require(attempt>=0 && notes.size<=48)
        notes.forEach { MentorSourceNotebook.checkSource(it) }
        val fixed=MentorCurriculum.select(attempt)

        // Preserve broad coverage: every fourth acquisition stays on the authored syllabus.
        if(notes.size<6 || attempt%4L==0L)return fixed

        val sourceCount=linkedMapOf<String,Int>()
        val occurrenceCount=linkedMapOf<String,Int>()
        notes.forEach { note ->
            val words=terms(note.excerpt)
            words.forEach { word -> occurrenceCount[word]=(occurrenceCount[word] ?: 0)+1 }
            words.toSet().forEach { word -> sourceCount[word]=(sourceCount[word] ?: 0)+1 }
        }
        val candidates=occurrenceCount.keys.asSequence()
            .filter { it !in fixedTerms }
            .filter { (sourceCount[it] ?: 0)>=2 || (occurrenceCount[it] ?: 0)>=3 }
            .sortedWith(
                compareByDescending<String>{sourceCount[it] ?: 0}
                    .thenByDescending{occurrenceCount[it] ?: 0}
                    .thenBy{it}
            ).take(64).toList()
        if(candidates.isEmpty())return fixed

        val dynamicOrdinal=(attempt-attempt/4L-1L).coerceAtLeast(0L)
        val index=(dynamicOrdinal%candidates.size).toInt()
        val term=candidates[index]
        val label=term.replaceFirstChar {
            if(it.isLowerCase())it.titlecase(Locale.GERMAN) else it.toString()
        }
        return MentorCurriculum.Topic("Vertiefung · "+label,term)
    }
}
