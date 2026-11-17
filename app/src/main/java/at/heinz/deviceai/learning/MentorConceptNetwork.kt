package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.util.Locale

/**
 * Local source-bound associative graph. It learns co-occurring terms from
 * retrieved text, not semantics or truth. No tools, labels or weights are changed.
 */
class MentorConceptNetwork private constructor(
    private val fingerprint:String,
    private val noteCount:Int,
    private val edges:List<Edge>
) {
    data class Edge(val left:String,val right:String,val source:Int)
    data class Relation(val term:String,val sources:List<Int>)
    val edgeCount:Int get()=edges.size
    val vocabularySize:Int get()=edges.flatMap { listOf(it.left,it.right) }.toSet().size
    fun related(term:String,limit:Int=6):List<Relation> {
        require(limit in 1..16)
        val key=terms(term).firstOrNull() ?: return emptyList()
        return edges.mapNotNull { e ->
            when(key) { e.left -> e.right to e.source; e.right -> e.left to e.source; else -> null }
        }.groupBy({it.first},{it.second})
            .map { (word,docs) -> Relation(word,docs.distinct().sorted()) }
            .sortedWith(compareByDescending<Relation>{it.sources.size}.thenBy{it.term})
            .take(limit)
    }
    fun serialize():String=buildString {
        append("MCA1\t").append(noteCount).append('\t').append(fingerprint).append('\n')
        edges.forEach { append(it.left).append('\t').append(it.right)
            .append('\t').append(it.source).append('\n') }
    }
    companion object {
        private const val MAX_NOTES=48
        private const val MAX_EDGES=1440
        private val stop=setOf("und","der","die","das","den","dem","des","ein","eine",
            "mit","für","von","oder","aus","ist","sind","auf","als","bei",
            "auch","werden","kann","nicht","nach","über","durch","wird",
            "einem","einen","einer","dieser","diese","eines")
        private val valid=Regex("[\\p{L}]{4,24}")
        private fun terms(text:String):List<String> =
            text.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}]+"))
                .asSequence().filter { valid.matches(it) && it !in stop }
                .distinct().take(12).toList()
        private fun sourceFingerprint(notes:List<MentorSource>):String {
            val digest=MessageDigest.getInstance("SHA-256")
            notes.forEach { v ->
                listOf(v.subject,v.url,v.sha,v.excerpt).forEach { value ->
                    digest.update(value.toByteArray(Charsets.UTF_8))
                    digest.update(0.toByte())
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun build(notes:List<MentorSource>):MentorConceptNetwork {
            require(notes.size<=MAX_NOTES)
            val edges=linkedSetOf<Edge>()
            notes.forEachIndexed { index,note ->
                MentorSourceNotebook.checkSource(note)
                val words=terms(note.excerpt)
                for(i in words.indices) for(j in i+1..minOf(i+2,words.lastIndex)){
                    val a=minOf(words[i],words[j])
                    val b=maxOf(words[i],words[j])
                    if(a!=b)edges+=Edge(a,b,index)
                }
            }
            require(edges.size<=MAX_EDGES)
            return MentorConceptNetwork(sourceFingerprint(notes),notes.size,edges.toList())
        }
        fun restore(encoded:String,notes:List<MentorSource>):MentorConceptNetwork {
            require(encoded.length<=64000 && notes.size<=MAX_NOTES)
            val lines=encoded.lineSequence().filter(String::isNotEmpty).toList()
            require(lines.isNotEmpty() && lines.size<=MAX_EDGES+1)
            val header=lines.first().split('\t')
            require(header.size==3 && header[0]=="MCA1" &&
                header[1].toIntOrNull()==notes.size && header[2]==sourceFingerprint(notes))
            val loaded=lines.drop(1).map { line ->
                val fields=line.split('\t')
                require(fields.size==3)
                val source=fields[2].toIntOrNull()
                require(source!=null && source in notes.indices)
                require(valid.matches(fields[0]) && valid.matches(fields[1]) &&
                    fields[0]<fields[1])
                Edge(fields[0],fields[1],source)
            }
            val expected=build(notes)
            require(loaded==expected.edges)
            return expected
        }
    }
}

/** Source-linked responses about co-occurrence; no ungrounded inferred facts. */
object MentorAssociativeRecall {
    fun topic(message:String):String? {
        val prefix="mentor verbindungen:"
        val trimmed=message.trim()
        if(!trimmed.lowercase(Locale.GERMAN).startsWith(prefix))return null
        return trimmed.drop(prefix.length).trim().takeIf { it.length in 4..100 }
    }
    fun answer(message:String,notes:List<MentorSource>,model:MentorConceptNetwork):String? {
        val query=topic(message) ?: return null
        val related=model.related(query)
        if(related.isEmpty())return "Mentor: Zu "+query.take(60)+
            " kenne ich noch keine dokumentierten Begriffsverbindungen."
        return buildString {
            append("MENTOR · GELERNTE BEGRIFFSVERBINDUNGEN\n")
            append("Ausgangspunkt: ").append(query.take(60))
            related.forEach { edge ->
                append("\n\n• ").append(edge.term).append(" · ")
                    .append(edge.sources.size).append(" Quellenausschnitt(e)")
                val source=notes[edge.sources.first()]
                append("\nBeleg: ").append(source.url.take(220))
                append("\nSHA-256: ").append(source.sha.take(16))
            }
            append("\n\nUnbestätigte Internetbeobachtungen. Es handelt sich um " +
                "Wortnähe, nicht um bewiesene Zusammenhänge oder Sprachverständnis.")
        }.take(3000)
    }
}
