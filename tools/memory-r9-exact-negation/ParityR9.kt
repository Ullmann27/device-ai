import at.heinz.deviceai.learning.*
import java.util.Locale
fun main() {
    val vocabulary=listOf("Lampe","Licht","Nische","Keim","Tisch","Wasser","Garten","Bernsteinquarz","Sonnenfunken","Fremdaussage","Sonnenhafen","Avatar")
    val docs=(0 until 40).map { i ->
        MemoryDoc("doc-$i",vocabulary[i%12]+" "+vocabulary[(i*5+3)%12]+" "+vocabulary[(i*7+1)%12]+(if(i%4==0)" nicht" else ""),1_000L+i*2_000L,i%3==0)
    }
    val queries=(0 until 48).map { i ->
        (if(i%4==0)"nicht " else "")+vocabulary[i%12]+" "+vocabulary[(i*5+3)%12]
    }
    val models=listOf("bm25" to Bm25GermanRetriever(),"ngram" to CharNgramHashRetriever(dim=1 shl 14))
    for((name,model) in models) {
        model.index(docs)
        println("$name bytes="+model.approxIndexBytes())
        for((i,q) in queries.withIndex()) {
            val hits=model.search(q,100_000L,3)
            val serialized=hits.joinToString("|") { it.doc.id+","+it.confirmed+","+String.format(Locale.US,"%.12f",it.score) }
            println("$name query=$i result=$serialized")
        }
    }
}
