import at.heinz.deviceai.learning.*
fun main() {
    val docs=(0 until 240).map { i ->
        MemoryDoc("doc-$i", ("Licht Nische Tische Wasser ".repeat(40))+(if(i%24==0) " nicht" else ""), 1_000L+i, i%2==0)
    }
    val r=CharNgramHashRetriever(dim=1 shl 14)
    val buildStart=System.nanoTime()
    r.index(docs)
    val buildMs=(System.nanoTime()-buildStart)/1_000_000.0
    println("INDEX_MS=$buildMs APPROX_BYTES=${r.approxIndexBytes()} DOCS=${docs.size}")
    repeat(20) { r.search("nicht", 4_000L, 3) }
    val nanos=LongArray(5)
    for(i in 0..4) {
        val start=System.nanoTime()
        repeat(75) {
            val hits=r.search("nicht", 4_000L, 3)
            check(hits.size==3 && hits.all { GermanText.contentStems(it.doc.text).contains("nich") })
        }
        nanos[i]=System.nanoTime()-start
        println("ROUND_${i}_75_SEARCH_MS=${nanos[i]/1_000_000.0}")
    }
    println("MEDIAN_75_SEARCH_MS=${nanos.sorted()[2]/1_000_000.0}")
}
