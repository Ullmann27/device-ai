package at.heinz.deviceai.learning

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
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
        "fuer","noch","nochmal","schon","nicht","auch","so","um","uhr","gestern","heute","vorgestern","letzte","letzten","letzter","woche",
        "monat","vor","seit","hatte","wurde","wird","denn","mal","eigentlich","bitte","weisst","sag","nochmal","dass","als","ob","nach","fuers")
    private val SUFFIXES=listOf("innen","ungen","ung","ern","en","er","es","em","st","e","s","n","t")
    fun normalize(s:String)=s.lowercase(Locale.GERMAN).replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")
    fun rawTokens(s:String)=normalize(s).split(Regex("[^a-z0-9]+")).filter { it.length>=2 }
    fun stem(t:String):String { if(t.length<=4)return t; for(x in SUFFIXES) if(t.endsWith(x) && t.length-x.length>=4)return t.dropLast(x.length); return t }
    fun contentStems(s:String)=rawTokens(s).filter { it !in STOP }.map(::stem)
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
    private var docs=emptyList<MemoryDoc>()
    private val postings=HashMap<String,MutableList<IntArray>>() // term -> [doc, tf]
    private var lengths=IntArray(0); private var avg=1.0
    private var vocab=HashSet<String>()

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
        this.docs=docs; postings.clear()
        val base=docs.flatMap { GermanText.contentStems(it.text) }.toHashSet()
        lengths=IntArray(docs.size)
        docs.forEachIndexed { i,d -> val ts=terms(d.text,base); lengths[i]=ts.size
            ts.groupingBy { it }.eachCount().forEach { (t,c) -> postings.getOrPut(t){ ArrayList() }.add(intArrayOf(i,c)) } }
        avg=lengths.average().takeIf { it>0 } ?: 1.0
        vocab=HashSet(postings.keys)
    }
    private fun resolve(t:String):List<String> {
        if(t in vocab)return listOf(t)
        val limit=if(t.length>=7)2 else 1
        return vocab.asSequence().filter { it.length>=4 && it[0]==t[0] }.map { it to GermanText.editDistance(it,t,limit) }
            .filter { it.second<=limit }.sortedWith(compareBy<Pair<String,Int>>{ it.second }.thenByDescending { postings[it.first]!!.size }).take(1).map { it.first }.toList()
    }
    override fun search(query:String,nowMillis:Long,k:Int):List<RetrievalHit> {
        if(docs.isEmpty())return emptyList()
        val scores=DoubleArray(docs.size); val n=docs.size.toDouble()
        terms(query,vocab).flatMap(::resolve).distinct().forEach { t -> val p=postings[t] ?: return@forEach
            val idf=ln(1+(n-p.size+0.5)/(p.size+0.5))
            p.forEach { (d,tf) -> scores[d]+=idf*tf*(k1+1)/(tf+k1*(1-b+b*lengths[d]/avg)) } }
        val w=temporal?.window(query,nowMillis)
        return scores.indices.filter { scores[it]>0 }.map { RetrievalHit(docs[it],scores[it]*(temporal?.weight(w,docs[it].atMillis) ?: 1.0)) }
            .sortedByDescending { it.score }.take(k)
    }
    override fun approxIndexBytes()=postings.entries.sumOf { 40L+2*it.key.length+it.value.size*24L }+lengths.size*4L
}

/** Variant B: hashed character 3–5-gram TF-IDF vectors (cosine), robust to typos and compounds by construction. */
class CharNgramHashRetriever(private val temporal:TemporalCue?=null, private val dim:Int=1 shl 15):ContinuumMemoryRetriever {
    private var docs=emptyList<MemoryDoc>()
    private var vectors=emptyList<Pair<IntArray,FloatArray>>()
    private var idf=FloatArray(0)
    private fun grams(text:String):Map<Int,Int> {
        val m=HashMap<Int,Int>()
        for(w in GermanText.contentStems(text)) { val s=" $w "
            for(n in 3..5) for(i in 0..s.length-n) { val h=Math.floorMod(s.substring(i,i+n).hashCode(),dim); m[h]=(m[h]?:0)+1 } }
        return m
    }
    private fun vec(g:Map<Int,Int>):Pair<IntArray,FloatArray> {
        val keys=g.keys.sorted().toIntArray(); val v=FloatArray(keys.size){ sqrt(g[keys[it]]!!.toFloat())*idf[keys[it]] }
        val norm=sqrt(v.sumOf { (it*it).toDouble() }).toFloat().takeIf { it>0 } ?: 1f
        for(i in v.indices) v[i]/=norm
        return keys to v
    }
    override fun index(docs:List<MemoryDoc>) {
        this.docs=docs; val gs=docs.map { grams(it.text) }
        val df=IntArray(dim); gs.forEach { it.keys.forEach { k -> df[k]++ } }
        idf=FloatArray(dim){ ln((1.0+docs.size)/(1.0+df[it])).toFloat()+1f }
        vectors=gs.map(::vec)
    }
    override fun search(query:String,nowMillis:Long,k:Int):List<RetrievalHit> = scoredParts(query,nowMillis,k).map { it.hit }

    /** The real score computation with its parts: score = cosine × timeWeight (same code path as [search]). */
    fun scoredParts(query:String,nowMillis:Long,k:Int):List<ScoredParts> {
        if(docs.isEmpty())return emptyList()
        val (qk,qv)=vec(grams(query)); val w=temporal?.window(query,nowMillis)
        return vectors.mapIndexed { i,(dk,dv) -> var s=0.0; var a=0; var c=0
            while(a<qk.size && c<dk.size){ when { qk[a]==dk[c] -> { s+=qv[a]*dv[c];a++;c++ }; qk[a]<dk[c] -> a++; else -> c++ } }
            val tw=temporal?.weight(w,docs[i].atMillis) ?: 1.0
            ScoredParts(RetrievalHit(docs[i],s*tw),s,tw) }
            .filter { it.hit.score>0 }.sortedByDescending { it.hit.score }.take(k)
    }
    override fun approxIndexBytes()=vectors.sumOf { it.first.size*8L+32 }+idf.size*4L
}
