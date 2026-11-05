import at.heinz.deviceai.learning.*
import java.util.Locale
fun main() {
    val words=listOf("Lampe","Licht","Nische","Keim","Tisch","Wasser","Garten","Bernsteinquarz","Sonnenfunken","Fremdaussage","Sonnenhafen","Avatar")
    val docs=(0 until 40).map { i ->
        MemoryDoc("doc-$i",words[i%12]+" "+words[(i*5+3)%12]+" "+words[(i*7+1)%12]+(if(i%4==0)" nicht" else "")+(if(i%11==0)" ohne" else ""),1_000L+i*2_000L,i%3==0)
    }
    val queries=(0 until 48).map { i ->
        (if(i%4==0)"nicht " else "")+words[i%12]+" "+words[(i*5+3)%12]
    }+listOf("nicht","kein","ohne","nie","niemals","keine","nichts","nicht")
    val models=listOf("bm25" to Bm25GermanRetriever(),"ngram" to CharNgramHashRetriever(dim=1 shl 14))
    for((name,model) in models) {
        model.index(docs)
        for((i,q) in queries.withIndex()) {
            val hits=model.search(q,100_000L,3)
            val serialized=hits.joinToString("|") { it.doc.id+","+it.confirmed+","+String.format(Locale.US,"%.12f",it.score) }
            println("$name query=$i result=$serialized")
        }
    }
}
