package at.heinz.deviceai.learning

import java.time.ZoneId
import kotlin.random.Random
import kotlin.math.abs

/** Isolated, deterministic nonconcurrent score/ID/index-size parity with the R6 classes. */
fun main() {
    val zone=ZoneId.of("Europe/Vienna")
    val vocab=listOf("gartenhaus","bernstein","quarz","schnelle","morgen","nebel","sonnenfunken","gedaechtnis",
        "fahrzeug","stadt","meldung","bestellung","sicherheit","vorgestern","abend","termin","wechsel","neuland")
    val random=Random(731)
    var cases=0
    repeat(2) { corpusId ->
        val docs=(0 until 40).map { i ->
            MemoryDoc("$corpusId-$i",List(3+random.nextInt(16)){vocab[random.nextInt(vocab.size)]}.joinToString(" "),
                100000000L+i*86400000L,i%2==0)
        }
        val variants=listOf(
            Bm25GermanRetriever(TemporalCue(zone)) to BaselineBm25GermanRetriever(TemporalCue(zone)),
            CharNgramHashRetriever(TemporalCue(zone),1 shl 14) to BaselineCharNgramHashRetriever(TemporalCue(zone),1 shl 14)
        )
        for ((current,old) in variants) {
            current.index(docs);old.index(docs)
            check(current.approxIndexBytes()==old.approxIndexBytes()) { "index size changed" }
            repeat(12) { i ->
                val q=List(1+random.nextInt(4)){vocab[random.nextInt(vocab.size)]}.joinToString(" ")+if(i%4==0)" gestern" else ""
                val a=current.search(q,120000000L+i*86400000L,3)
                val b=old.search(q,120000000L+i*86400000L,3)
                check(a.size==b.size) { "hit count mismatch" }
                for (j in a.indices) {
                    check(a[j].doc==b[j].doc && abs(a[j].score-b[j].score)<1e-7) { "score/identity changed: $q" }
                    cases++
                }
            }
        }
    }
    println("PARITY_PASS comparisons=$cases corpus=2x40 queries=48 scoreTolerance=1e-7 indexByteEqual=true")
}
