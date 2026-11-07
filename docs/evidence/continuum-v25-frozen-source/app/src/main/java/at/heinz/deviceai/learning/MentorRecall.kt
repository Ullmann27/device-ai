package at.heinz.deviceai.learning

import java.util.Locale

/**
 * Bounded topic retrieval from opt-in source observations.
 * Not a language model. Never promotes a retrieved statement to truth or an action.
 */
object MentorRecall {
    private val stop= setOf("und","der","die","das","den","dem","des","ein","eine",
        "über","aus","mit","was","wie","ist","sind","bitte","wird","im","in","von")
    fun topic(message:String):String? {
        val prefix="mentor wissen:"
        val candidate=message.trim()
        if(!candidate.lowercase(Locale.GERMAN).startsWith(prefix))return null
        return candidate.drop(prefix.length).trim().takeIf { it.length in 3..120 }
    }
    private fun terms(s:String):Set<String> =
        s.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}\\p{N}]+"))
            .asSequence().filter { it.length>=3 && it !in stop }.take(60).toSet()

    fun answer(message:String,notes:List<MentorSource>):String? {
        val sought=topic(message) ?: return null
        val tokens=terms(sought)
        if(tokens.isEmpty())return "Mentor: Bitte nenne ein konkretes Fachthema."
        val matched=notes.asSequence().filter { it.excerpt.length<=400 }
            .map { it to terms(it.subject+" "+it.excerpt).intersect(tokens).size }
            .filter { (_,matches)->matches >= if(tokens.size==1) 1 else 2 }
            .sortedWith(compareByDescending<Pair<MentorSource,Int>> { it.second }.thenBy { it.first.sha })
            .take(3).toList()
        if(matched.isEmpty())return "Mentor: Zu diesem Thema liegen noch keine passenden Quellenbeobachtungen " +
            "in meinem lokalen Studienheft vor. Ich erfinde keine Antwort."
        return buildString {
            append("Mentor: Ich habe ").append(matched.size)
                .append(" passende Quellenstellen gefunden. NICHT UNABHÄNGIG BESTÄTIGT:")
            matched.forEachIndexed { i,(note,_) ->
                append("\n\n").append(i+1).append(". ").append(note.excerpt.take(330))
                append("\nQuelle: ").append(note.url.take(250))
                append("\nSHA-256: ").append(note.sha.take(16))
            }
            append("\n\nDas ist quellengestützte Suche, keine eigene Sprachgenerierung und kein geprüfter Wahrheitsanspruch.")
        }.take(2400)
    }
}
