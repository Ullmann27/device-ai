package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sqrt

/**
 * Small original distributional representation learned only from the bounded
 * Mentor source notebook. Terms become similar when they occur in similar local
 * contexts, even if the two terms never occur together.
 *
 * This is an experiment in representation learning, not a truth model and not
 * a general language model. It never grants tool authority or edits user memory.
 */
class MentorSemanticModel private constructor(
    private val fingerprint:String,
    private val noteCount:Int,
    private val vectors:Map<String,IntArray>,
    private val sources:Map<String,List<Int>>
) {
    data class SimilarTerm(
        val term:String,
        val similarity:Double,
        val sources:List<Int>
    )

    val vocabularySize:Int get()=vectors.size
    val activeDimensions:Int
        get()=vectors.values.sumOf { vector -> vector.count { it>0 } }

    fun similar(term:String,limit:Int=6):List<SimilarTerm> {
        require(limit in 1..16)
        val key=tokens(term).firstOrNull { vectors.containsKey(it) } ?: return emptyList()
        val query=vectors.getValue(key)
        val queryNorm=norm(query)
        if(queryNorm==0.0)return emptyList()
        return vectors.asSequence()
            .filter { (word,_) -> word!=key }
            .mapNotNull { (word,vector) ->
                val denominator=queryNorm*norm(vector)
                if(denominator==0.0)null else {
                    val score=dot(query,vector).toDouble()/denominator
                    if(score<MIN_SIMILARITY)null
                    else SimilarTerm(word,score,sources[word].orEmpty())
                }
            }
            .sortedWith(compareByDescending<SimilarTerm>{it.similarity}
                .thenByDescending{it.sources.size}.thenBy{it.term})
            .take(limit).toList()
    }

    fun serialize():String=buildString {
        append("MDS1\t").append(noteCount).append('\t').append(fingerprint).append('\n')
        vectors.keys.sorted().forEach { term ->
            append(term).append('\t')
            append(sources[term].orEmpty().joinToString(","))
            append('\t')
            val vector=vectors.getValue(term)
            append(vector.indices.asSequence().filter { vector[it]>0 }
                .joinToString(",") { i -> "${i}:${vector[i]}" })
            append('\n')
        }
    }

    companion object {
        private const val MAX_NOTES=48
        private const val MAX_VOCAB=128
        private const val MAX_TOKENS_PER_NOTE=64
        private const val DIMENSIONS=32
        private const val CONTEXT_RADIUS=3
        private const val MAX_SERIALIZED=90000
        private const val MIN_SIMILARITY=0.20
        private val valid=Regex("[\\p{L}]{4,28}")
        private val stop=setOf(
            "und","der","die","das","den","dem","des","ein","eine","einer","eines",
            "mit","für","von","oder","aus","ist","sind","auf","als","bei","auch",
            "werden","kann","nicht","nach","über","durch","wird","einem","einen",
            "dieser","diese","sowie","dass","sich","wie","was","wenn","mehr"
        )

        private fun tokens(text:String):List<String> =
            text.lowercase(Locale.GERMAN).split(Regex("[^\\p{L}]+"))
                .asSequence().filter { valid.matches(it) && it !in stop }
                .take(MAX_TOKENS_PER_NOTE).toList()

        private fun fingerprint(notes:List<MentorSource>):String {
            val digest=MessageDigest.getInstance("SHA-256")
            notes.forEach { note ->
                listOf(note.subject,note.url,note.sha,note.excerpt).forEach { value ->
                    digest.update(value.toByteArray(Charsets.UTF_8))
                    digest.update(0.toByte())
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun stableBucket(value:String):Int {
            var hash=0x811c9dc5L
            value.toByteArray(Charsets.UTF_8).forEach { b ->
                hash=hash xor (b.toLong() and 0xffL)
                hash=(hash*0x01000193L) and 0xffffffffL
            }
            return (hash%DIMENSIONS).toInt()
        }

        private fun dot(a:IntArray,b:IntArray):Long {
            var total=0L
            for(i in a.indices)total+=a[i].toLong()*b[i].toLong()
            return total
        }

        private fun norm(a:IntArray):Double =
            sqrt(a.fold(0.0) { total,value -> total+value.toDouble()*value.toDouble() })

        fun build(notes:List<MentorSource>):MentorSemanticModel {
            require(notes.size<=MAX_NOTES)
            notes.forEach(MentorSourceNotebook::checkSource)
            val noteTokens=notes.map { tokens(it.excerpt) }
            val frequency=linkedMapOf<String,Int>()
            noteTokens.flatten().forEach { word -> frequency[word]=(frequency[word] ?: 0)+1 }
            val vocabulary=frequency.entries
                .sortedWith(compareByDescending<Map.Entry<String,Int>>{it.value}.thenBy{it.key})
                .take(MAX_VOCAB).map { it.key }.toSet()

            val vectors=linkedMapOf<String,IntArray>()
            val sourceSets=linkedMapOf<String,MutableSet<Int>>()
            vocabulary.sorted().forEach { word ->
                vectors[word]=IntArray(DIMENSIONS)
                sourceSets[word]=linkedSetOf()
            }

            noteTokens.forEachIndexed { sourceIndex,words ->
                words.forEachIndexed { index,word ->
                    val vector=vectors[word] ?: return@forEachIndexed
                    sourceSets.getValue(word).add(sourceIndex)
                    val start=maxOf(0,index-CONTEXT_RADIUS)
                    val end=minOf(words.lastIndex,index+CONTEXT_RADIUS)
                    for(contextIndex in start..end) {
                        if(contextIndex==index)continue
                        val context=words[contextIndex]
                        val distance=kotlin.math.abs(contextIndex-index)
                        val weight=CONTEXT_RADIUS+1-distance
                        val bucket=stableBucket(context)
                        vector[bucket]=(vector[bucket]+weight).coerceAtMost(100000)
                    }
                }
            }
            val frozenSources=sourceSets.mapValues { (_,set)->set.toList().sorted() }
            return MentorSemanticModel(fingerprint(notes),notes.size,vectors,frozenSources)
        }

        fun restore(encoded:String,notes:List<MentorSource>):MentorSemanticModel {
            require(encoded.length<=MAX_SERIALIZED && notes.size<=MAX_NOTES)
            val header=encoded.lineSequence().firstOrNull()?.split('\t') ?: emptyList()
            require(header.size==3 && header[0]=="MDS1" &&
                header[1].toIntOrNull()==notes.size && header[2]==fingerprint(notes))
            val expected=build(notes)
            require(encoded==expected.serialize())
            return expected
        }
    }
}

/** Read-only chat surface for the experimental local distributional representation. */
object MentorSemanticRecall {
    fun topic(message:String):String? {
        val trimmed=message.trim()
        val lower=trimmed.lowercase(Locale.GERMAN)
        val prefixes=listOf("mentor ähnlich:","mentor aehnlich:","mentor semantik:")
        val prefix=prefixes.firstOrNull(lower::startsWith) ?: return null
        return trimmed.drop(prefix.length).trim().takeIf { it.length in 4..100 }
    }

    fun answer(message:String,notes:List<MentorSource>,model:MentorSemanticModel):String? {
        val query=topic(message) ?: return null
        val similar=model.similar(query)
        if(similar.isEmpty())return "Mentor: Für "+query.take(60)+
            " habe ich noch kein belastbares lokales Verteilungsmuster gelernt."
        return buildString {
            append("MENTOR · VERTEILUNGSMUSTER (EXPERIMENT)\n")
            append("Ausgangspunkt: ").append(query.take(60))
            similar.forEach { result ->
                append("\n\n• ").append(result.term)
                append(" · Ähnlichkeit ")
                append(String.format(Locale.ROOT,"%.2f",result.similarity))
                if(result.sources.isNotEmpty()) {
                    val source=notes[result.sources.first()]
                    append("\nBeispielquelle: ").append(source.url.take(220))
                    append("\nSHA-256: ").append(source.sha.take(16))
                }
            }
            append("\n\nDie Ähnlichkeit entsteht aus gemeinsamem Wortkontext in lokal ")
            append("gespeicherten, unbestätigten Quellen. Sie beweist weder Bedeutung noch Wahrheit.")
        }.take(3000)
    }
}
