import kotlin.math.ln
import kotlin.math.sqrt

data class MemoryDoc(val id: String, val text: String, val atMillis: Long, val confirmed: Boolean)
interface Retriever { fun index(docs:List<MemoryDoc>); fun snapshot():List<MemoryDoc>; fun indexSize():Int }
object GermanText { fun contentStems(text:String):List<String> = text.split(' ').filter { it.isNotBlank() } }

class Bm25GermanRetriever : Retriever {
    private var docs=emptyList<MemoryDoc>()
    private val postings=HashMap<String,MutableList<IntArray>>()
    private var lengths=IntArray(0); private var avg=1.0
    private var vocab=HashSet<String>()
    private fun terms(text:String,known:Set<String>):List<String> = GermanText.contentStems(text)
    // Production index method is extracted verbatim from the current source file at execution.
    __BM25_INDEX__
    override fun snapshot()=docs
    override fun indexSize()=lengths.size
}

class CharNgramHashRetriever : Retriever {
    private var docs=emptyList<MemoryDoc>()
    private var vectors=emptyList<Pair<IntArray,FloatArray>>()
    private var idf=FloatArray(0)
    private val dim=64
    private fun grams(text:String):Map<Int,Int> = mapOf(1 to text.length)
    private fun vec(g:Map<Int,Int>):Pair<IntArray,FloatArray> = intArrayOf(1) to floatArrayOf((g[1] ?: 0).toFloat())
    // Production index method is extracted verbatim from the current source file at execution.
    __NGRAM_INDEX__
    override fun snapshot()=docs
    override fun indexSize()=vectors.size
}

fun main() {
    var passed=0; var failed=0
    for (r in listOf(Bm25GermanRetriever(),CharNgramHashRetriever())) {
        val cls=r.javaClass.simpleName
        fun check(name:String,condition:Boolean) {if(condition){passed++;println("PASS $cls $name")}else{failed++;println("FAIL $cls $name")}}
        val original=MemoryDoc("original","Bernsteinquarz Gartenlaube",100,true)
        val replacement=MemoryDoc("replacement","Fremdaussage",200,false)
        val input=mutableListOf(original)
        r.index(input)
        check("indexed original present",r.snapshot().singleOrNull()==original)
        input[0]=replacement
        check("caller replacement cannot relabel",r.snapshot().singleOrNull()==original)
        check("posting/vector remains indexed",r.indexSize()==1)
        input.clear()
        check("caller clear cannot erase indexed doc",r.snapshot().singleOrNull()==original)
        input.add(replacement);r.index(input)
        check("explicit reindex replaces document",r.snapshot().singleOrNull()==replacement)
        input.add(original)
        check("caller append cannot alter indexed doc count",r.snapshot().size==1)
    }
    println("SUMMARY total=${passed+failed} pass=$passed fail=$failed")
    if (failed>0) kotlin.system.exitProcess(1)
}
