package at.heinz.deviceai.learning

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.BitSet
import kotlin.math.ln
import kotlin.math.sqrt

/** One retrievable memory. [confirmed] is copied verbatim into every hit; a retriever can never upgrade it. */
data class MemoryDoc(val id:String, val text:String, val atMillis:Long, val confirmed:Boolean)
data class ScoredParts(val hit:RetrievalHit, val cosine:Double, val timeWeight:Double)
data class RetrievalHit(val doc:MemoryDoc, val score:Double) { val confirmed:Boolean get()=doc.confirmed }

/** M2.9: small on-device retrieval interface (no large model). */
interface ContinuumMemoryRetriever {
    fun index(docs:List<MemoryDoc>)
    fun search(query:String, nowMillis:Long, k:Int=3):List<RetrievalHit>
    /** Approximate index size in bytes (postings/vectors + term strings), for the on-device budget. */
    fun approxIndexBytes():Long

    companion object {
        /** Winner of the M2.9 experiment (docs/evidence/m2-retrieval-20261104). */
        fun default(zone:ZoneId=ZoneId.of("Europe/Vienna"), appDirectory:java.io.File?=null):ContinuumMemoryRetriever =
            CharNgramHashRetriever(TemporalCue(zone)).let { if(appDirectory==null) it else EventEmittingRetriever(it,appDirectory) }
    }
}

object GermanText {
    private val STOP=setOf("der","die","das","den","dem","des","ein","eine","einen","einem","einer","und","oder","aber","ich","du","er","sie","es",
        "wir","ihr","mein","meine","meinen","meinem","meiner","mir","mich","dir","dich","ist","bin","sind","war","hat","habe","hast","haben",
        "wo","wann","was","wer","wie","welche","welcher","welches","warum","im","in","am","an","auf","bei","mit","zu","zum","zur","von","vom",
        "fuer","noch","nochmal","schon","auch","so","um","uhr","gestern","heute","vorgestern","letzte","letzten","letzter","woche",
        "monat","vor","seit","hatte","wurde","wird","denn","mal","eigentlich","bitte","weisst","sag","nochmal","dass","als","ob","nach","fuers")
    private val SUFFIXES=listOf("innen","ungen","ung","ern","en","er","es","em","st","e","s","n","t")
    private val NEGATION_STEMS=setOf("nicht","nichts","kein","keine","keinen","nie","niemals","ohne").map(::stem).toSet()
    fun isNegationStem(stem:String)=stem in NEGATION_STEMS
    fun normalize(s:String)=s.lowercase(Locale.GERMAN).replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")
    fun rawTokens(s:String)=normalize(s).split(Regex("[^a-z0-9]+")).filter { it.length>=2 || (it.length==1 && it[0] in '0'..'9') }
    fun stem(t:String):String { if(t.length<=4)return t; for(x in SUFFIXES) if(t.endsWith(x) && t.length-x.length>=4)return t.dropLast(x.length); return t }
    fun contentStems(s:String)=rawTokens(s).filter { it !in STOP }.map(::stem)
    /** Explicit numeric facts require exact evidence; "vor N Tagen" is a time cue, not a fact. */
    fun numericEvidenceTerms(query:String):Set<String> =
        rawTokens(normalize(query).replace(Regex("\\bvor\\s+\\d+\\s+tagen\\b"), " "))
            .filter { token -> token.all { it in '0'..'9' } }.toSet()
    fun editDistance(a:String,b:String,limit:Int):Int {
        if(kotlin.math.abs(a.length-b.length)>limit)return limit+1
        var prev=IntArray(b.length+1){it}; var cur=IntArray(b.length+1)
        for(i in 1..a.length){ cur[0]=i; var best=cur[0]
            for(j in 1..b.length){ cur[j]=minOf(prev[j]+1,cur[j-1]+1,prev[j-1]+if(a[i-1]==b[j-1])0 else 1); best=minOf(best,cur[j]) }
            if(best>limit)return limit+1; val t=prev;prev=cur;cur=t }
        return prev[b.length]
    }
}

/** Relative German time cues ("gestern", "vorgestern", "heute", "letzte Woche", "letzten Monat", "vor N Tagen"). */
class TemporalCue(private val zone:ZoneId) {
    fun window(query:String,nowMillis:Long):LongRange? {
        val q=GermanText.normalize(query)
        val today=Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // Resolve each date separately: a midnight gap must not shift other day boundaries.
        fun days(from:Long,to:Long)=today.minusDays(from).atStartOfDay(zone).toInstant().toEpochMilli() until
            today.minusDays(to-1).atStartOfDay(zone).toInstant().toEpochMilli()
        Regex("vor (\\d+|zwei|drei|vier|fuenf) tagen").find(q)?.let { m ->
            val n=when(val count=m.groupValues[1]) {
                "zwei" -> 2L
                "drei" -> 3L
                "vier" -> 4L
                "fuenf" -> 5L
                else -> count.toLongOrNull() ?: return null
            }
            // A valid Long can still exceed the calendar or epoch-millisecond range.
            return try { days(n,n) }
            catch (_:DateTimeException) { null }
            catch (_:ArithmeticException) { null }
        }
        return when {
            "vorgestern" in q -> days(2,2)
            "gestern" in q -> days(1,1)
            "heute" in q -> days(0,0)
            Regex("letzte[nr]? woche|vorige[nr]? woche").containsMatchIn(q) -> {
                val thisWeek=today.minusDays(today.dayOfWeek.value.toLong()-1)
                thisWeek.minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli() until
                    thisWeek.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            Regex("letzte[nr]? monat|voriger monat").containsMatchIn(q) -> {
                val thisMonth=today.withDayOfMonth(1)
                thisMonth.minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() until
                    thisMonth.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            // Preserve the existing approximate cue; it is not a named calendar month.
            "vor einem monat" in q -> days(45,20)
            else -> null
        }
    }
    /** Docs inside the cued window keep their score; others are damped. No cue: unchanged. */
    fun weight(window:LongRange?,atMillis:Long)=if(window==null || atMillis in window) 1.0 else 0.35
}

/** Variant A: BM25 over normalized German stems + corpus-driven compound splitting + typo fallback (edit distance). */
class Bm25GermanRetriever(private val temporal:TemporalCue?=null, private val k1:Double=1.2, private val b:Double=0.75):ContinuumMemoryRetriever {
    /** A search must never combine the corpus of one generation with postings from another. */
    private data class IndexState(
        val docs:List<MemoryDoc>, val postings:Map<String,List<IntArray>>,
        val lengths:IntArray, val avg:Double, val vocab:Set<String>, val approxBytes:Long,
    )
    @Volatile private var state=IndexState(emptyList(),emptyMap(),IntArray(0),1.0,emptySet(),0L)

    private fun terms(text:String,known:Set<String>):List<String> {
        val out=ArrayList<String>()
        for(t in GermanText.contentStems(text)) {
            out+=t
            if(t.length>=8) for(i in 4..t.length-4) {
                val l=GermanText.stem(t.substring(0,i)); val r=GermanText.stem(t.substring(i))
                if(r in known && (l in known || l.length>=4)) { out+=r; if(l in known)out+=l; break }
            }
        }
        return out
    }
    override fun index(docs:List<MemoryDoc>) {
        val snapshot=docs.toList()
        val nextPostings=HashMap<String,MutableList<IntArray>>()
        val base=snapshot.flatMap { GermanText.contentStems(it.text) }.toHashSet()
        val nextLengths=IntArray(snapshot.size)
        snapshot.forEachIndexed { i,d -> val ts=terms(d.text,base); nextLengths[i]=ts.size
            ts.groupingBy { it }.eachCount().forEach { (t,c) -> nextPostings.getOrPut(t){ ArrayList() }.add(intArrayOf(i,c)) } }
        val nextAvg=nextLengths.average().takeIf { it>0 } ?: 1.0
        val nextVocab=HashSet(nextPostings.keys)
        val bytes=nextPostings.entries.sumOf { 40L+2*it.key.length+it.value.size*24L }+nextLengths.size*4L
        // The maps and arrays are built locally and are never mutated after publication.
        state=IndexState(snapshot,nextPostings,nextLengths,nextAvg,nextVocab,bytes)
    }
    private fun resolve(t:String,index:IndexState):List<String> {
        if(t in index.vocab)return listOf(t)
        // Negations must not be silently corrected into unrelated positive evidence.
        if(GermanText.isNegationStem(t))return emptyList()
        val limit=if(t.length>=7)2 else 1
        val ranked=index.vocab.asSequence().filter { it.length>=4 && it[0]==t[0] }
            .map { it to GermanText.editDistance(it,t,limit) }.filter { it.second<=limit }
            .sortedWith(compareBy<Pair<String,Int>>{ it.second }.thenByDescending { index.postings[it.first]!!.size }.thenBy { it.first })
            .take(2).toList()
        // Two equally plausible spellings are not evidence for either memory.
        if(ranked.size==2 && ranked[0].second==ranked[1].second &&
            index.postings[ranked[0].first]!!.size==index.postings[ranked[1].first]!!.size) return emptyList()
        return ranked.take(1).map { it.first }
    }
    override fun search(query:String,nowMillis:Long,k:Int):List<RetrievalHit> {
        if(k<=0)return emptyList()
        val index=state
        if(index.docs.isEmpty())return emptyList()
        val requiredNumbers=GermanText.numericEvidenceTerms(query).map { token ->
            (index.postings[token] ?: return emptyList()).mapTo(HashSet<Int>()) { it[0] }
        }
        val scores=DoubleArray(index.docs.size); val n=index.docs.size.toDouble()
        terms(query,index.vocab).flatMap { resolve(it,index) }.distinct().forEach { t -> val p=index.postings[t] ?: return@forEach
            val idf=ln(1+(n-p.size+0.5)/(p.size+0.5))
            p.forEach { (d,tf) -> scores[d]+=idf*tf*(k1+1)/(tf+k1*(1-b+b*index.lengths[d]/index.avg)) } }
        val w=temporal?.window(query,nowMillis)
        return scores.indices.filter { i -> scores[i]>0 && requiredNumbers.all { i in it } }
            .map { RetrievalHit(index.docs[it],scores[it]*(temporal?.weight(w,index.docs[it].atMillis) ?: 1.0)) }
            .sortedByDescending { it.score }.take(k)
    }
    override fun approxIndexBytes()=state.approxBytes
}

/** Variant B: hashed character 3–5-gram TF-IDF vectors (cosine), robust to typos and compounds by construction. */
class CharNgramHashRetriever(private val temporal:TemporalCue?=null, private val dim:Int=1 shl 15):ContinuumMemoryRetriever {
    private data class IndexState(
        val docs:List<MemoryDoc>, val vectors:List<Pair<IntArray,FloatArray>>,
        val idf:FloatArray, val negationDocIds:Map<String,BitSet>,
        val numericDocIds:Map<String,BitSet>, val approxBytes:Long,
    )
    @Volatile private var state=IndexState(emptyList(),emptyList(),FloatArray(0),emptyMap(),emptyMap(),0L)
    private fun grams(text:String):Map<Int,Int> {
        val m=HashMap<Int,Int>()
        for(w in GermanText.contentStems(text)) { val s=" $w "
            for(n in 3..5) for(i in 0..s.length-n) { val h=Math.floorMod(s.substring(i,i+n).hashCode(),dim); m[h]=(m[h]?:0)+1 } }
        return m
    }
    private fun vec(g:Map<Int,Int>,idf:FloatArray):Pair<IntArray,FloatArray> {
        val keys=g.keys.sorted().toIntArray(); val v=FloatArray(keys.size){ sqrt(g[keys[it]]!!.toFloat())*idf[keys[it]] }
        val norm=sqrt(v.sumOf { (it*it).toDouble() }).toFloat().takeIf { it>0 } ?: 1f
        for(i in v.indices) v[i]/=norm
        return keys to v
    }
    override fun index(docs:List<MemoryDoc>) {
        val snapshot=docs.toList()
        val gs=snapshot.map { grams(it.text) }
        val df=IntArray(dim); gs.forEach { it.keys.forEach { k -> df[k]++ } }
        val nextIdf=FloatArray(dim){ ln((1.0+snapshot.size)/(1.0+df[it])).toFloat()+1f }
        val nextVectors=gs.map { vec(it,nextIdf) }
        // Cache exact negation membership with this generation, not by rescanning all document text per query.
        val nextNegationDocIds=HashMap<String,BitSet>()
        val nextNumericDocIds=HashMap<String,BitSet>()
        snapshot.forEachIndexed { i,doc ->
            val stems=GermanText.contentStems(doc.text)
            stems.asSequence().filter { GermanText.isNegationStem(it) }.distinct().forEach { stem ->
                nextNegationDocIds.getOrPut(stem) { BitSet(snapshot.size) }.set(i)
            }
            stems.asSequence().filter { token -> token.all { it in '0'..'9' } }.distinct().forEach { token ->
                nextNumericDocIds.getOrPut(token) { BitSet(snapshot.size) }.set(i)
            }
        }
        val negationBytes=nextNegationDocIds.entries.sumOf { (stem,bits) -> 40L+stem.length*2L+bits.toLongArray().size*8L }
        val numericBytes=nextNumericDocIds.entries.sumOf { (token,bits) -> 40L+token.length*2L+bits.toLongArray().size*8L }
        val bytes=nextVectors.sumOf { it.first.size*8L+32 }+nextIdf.size*4L+negationBytes+numericBytes
        // Publish docs, vectors, IDF and polarity membership together. No post-publication mutation.
        state=IndexState(snapshot,nextVectors,nextIdf,nextNegationDocIds,nextNumericDocIds,bytes)
    }
    override fun search(query:String,nowMillis:Long,k:Int):List<RetrievalHit> = scoredParts(query,nowMillis,k).map { it.hit }

    /** The real score computation with its parts: score = cosine × timeWeight (same code path as [search]). */
    fun scoredParts(query:String,nowMillis:Long,k:Int):List<ScoredParts> {
        if(k<=0)return emptyList()
        val index=state
        if(index.docs.isEmpty())return emptyList()
        val soleNegation=GermanText.contentStems(query).distinct().singleOrNull()?.takeIf { GermanText.isNegationStem(it) }
        val eligible=soleNegation?.let { index.negationDocIds[it] ?: return emptyList() }
        val numericEligible=GermanText.numericEvidenceTerms(query).map { index.numericDocIds[it] ?: return emptyList() }
        val (qk,qv)=vec(grams(query),index.idf); val w=temporal?.window(query,nowMillis)
        return index.vectors.mapIndexedNotNull { i,(dk,dv) ->
            if(eligible!=null && !eligible.get(i)) return@mapIndexedNotNull null
            if(numericEligible.any { !it.get(i) }) return@mapIndexedNotNull null
            var s=0.0; var a=0; var c=0
            while(a<qk.size && c<dk.size){ when { qk[a]==dk[c] -> { s+=qv[a]*dv[c];a++;c++ }; qk[a]<dk[c] -> a++; else -> c++ } }
            val tw=temporal?.weight(w,index.docs[i].atMillis) ?: 1.0
            ScoredParts(RetrievalHit(index.docs[i],s*tw),s,tw) }
            .filter { it.hit.score>0 }
            .sortedByDescending { it.hit.score }.take(k)
    }
    override fun approxIndexBytes()=state.approxBytes
}
