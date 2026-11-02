package at.heinz.deviceai.learning

import java.util.Locale
import java.util.Random
import kotlin.math.exp
import kotlin.math.tanh

/** Experimental SWB2. Production TrainableSubwordEncoder remains SWB1-only.
 * Both runtimes use the same weighted 4/3/2-gram algorithm and 32960-byte
 * versioned, SHA-256-checked checkpoint. Never silently promote SWB2.
 */
data class Swb2Bucket(val id:Int,val weight:Double)

class Swb2ExperimentalEncoder private constructor(
    val seed:Long, val embeddings:DoubleArray, val head:DoubleArray,
    var updates:Int
) {
    constructor(seed:Long=73):this(seed,
        Random(seed).let { rng->DoubleArray(4096){(rng.nextDouble()*2.0-1.0)*0.08} },
        Random(seed xor 0x51a7L).let { rng->DoubleArray(17){i->if(i==16)0.0 else (rng.nextDouble()*2.0-1.0)*0.08} },0)

    fun features(text:String):List<Swb2Bucket> = weightedBuckets(text)
    fun encode(text:String):DoubleArray = encodeBuckets(features(text))
    fun encodeBuckets(items:List<Swb2Bucket>):DoubleArray {
        val z=DoubleArray(16)
        for(item in items)for(i in 0 until 16)z[i]+=embeddings[item.id*16+i]*item.weight
        return z.map(::tanh).toDoubleArray()
    }
    fun intentScore(text:String):Double {
        val z=encode(text)
        var logit=head[16]
        for(i in 0 until 16)logit+=z[i]*head[i]
        return sigmoid(logit)
    }
    fun similarity(left:String,right:String):Double {
        val a=encode(left);val b=encode(right)
        var dot=0.0
        for(i in 0 until 16)dot+=a[i]*b[i]
        return sigmoid(2.5*dot)
    }
    fun updateIntent(ex:SubwordIntentExample,rate:Double=0.12) {
        require(ex.label in 0..1 && rate.isFinite() && rate>0 && rate<=0.15)
        val ids=features(ex.text);val z=encodeBuckets(ids);val old=head.copyOf()
        var logit=old[16]
        for(i in 0 until 16)logit+=z[i]*old[i]
        val error=sigmoid(logit)-ex.label
        for(i in 0 until 16)head[i]=clip(old[i]-rate*error*z[i])
        head[16]=clip(old[16]-rate*error)
        for(item in ids)for(i in 0 until 16) {
            val idx=item.id*16+i
            embeddings[idx]=clip(embeddings[idx]-rate*error*old[i]*(1-z[i]*z[i])*item.weight)
        }
        updates=(updates+1).coerceAtMost(1_000_000)
    }
    fun updateSemantic(ex:SubwordSemanticExample,rate:Double=0.10) {
        require(rate.isFinite() && rate>0 && rate<=0.12)
        val ai=features(ex.left);val bi=features(ex.right)
        val a=encodeBuckets(ai);val b=encodeBuckets(bi)
        var dot=0.0
        for(i in 0 until 16)dot+=a[i]*b[i]
        val error=sigmoid(2.5*dot)-(if(ex.similar)1 else 0)
        val ga=DoubleArray(16){i->error*2.5*b[i]*(1-a[i]*a[i])}
        val gb=DoubleArray(16){i->error*2.5*a[i]*(1-b[i]*b[i])}
        for(item in ai)for(i in 0 until 16){
            val idx=item.id*16+i
            embeddings[idx]=clip(embeddings[idx]-rate*ga[i]*item.weight)
        }
        for(item in bi)for(i in 0 until 16){
            val idx=item.id*16+i
            embeddings[idx]=clip(embeddings[idx]-rate*gb[i]*item.weight)
        }
        updates=(updates+1).coerceAtMost(1_000_000)
    }
    fun save():ByteArray=LearningSnapshot.encode{out->
        out.writeInt(MAGIC);out.writeLong(seed);out.writeInt(updates)
        out.writeInt(embeddings.size);embeddings.forEach(out::writeDouble)
        out.writeInt(head.size);head.forEach(out::writeDouble)
    }
    companion object {
        private const val MAGIC=0x53574232
        private val STOP=("was wie weißt über und noch dazu darüber das die der ist ich mir mein meine du bitte kannst erzähl erkläre welche dieses thema "+
            "den dem des ein eine einer eines einem einen mit von vom zum zur für auf aus bei sich sie er es wir ihr sind wird werden wurde wurden hat haben "+
            "im in an am zu oder als auch nicht nur man durch unter zwischen").split(' ').toSet()
        private val SPLIT=Regex("[^\\p{L}\\p{N}]+")
        private val SUFFIX=Regex("(en|er|es|em|te|st|un|ch)\\$")
        private fun clip(value:Double)=value.coerceIn(-8.0,8.0)
        private fun sigmoid(x:Double)=if(x>=0)1.0/(1+exp(-x)) else exp(x)/(1+exp(x))
        private fun bucket(gram:String):Int {
            var h=0x811c9dc5L
            for(byte in gram.toByteArray(Charsets.UTF_8)){
                h=h xor (byte.toLong() and 0xffL)
                h=(h*0x01000193L) and 0xffffffffL
            }
            return (h%256).toInt()
        }
        fun weightedBuckets(text:String):List<Swb2Bucket> {
            require(text.length in 1..2048)
            val norm=text.lowercase(Locale.ROOT).replace(SPLIT," ").trim().take(512)
            require(norm.isNotEmpty())
            val weights=LinkedHashMap<Int,Double>()
            for(token in norm.split(' ').filter(String::isNotEmpty)) {
                val factor=if(token.length<3||STOP.contains(token))0.2 else 1.0
                val padded="^$token$"
                for(n in intArrayOf(4,3,2)) {
                    if(padded.length<n)continue
                    for(i in 0..padded.length-n){
                        val gram=padded.substring(i,i+n)
                        val order=when(n){4->1.8;3->1.15;else->0.35}
                        val pos=when{
                            gram.startsWith('^')->1.25
                            SUFFIX.containsMatchIn(gram) && SUFFIX.find(gram)?.range?.last==gram.lastIndex->0.45
                            else->1.0
                        }
                        val id=bucket(gram)
                        val weight=factor*order*pos
                        val old=weights[id]
                        if(old!=null){if(weight>old)weights[id]=weight}
                        else if(weights.size<128)weights[id]=weight
                    }
                }
            }
            require(weights.isNotEmpty())
            val sum=weights.values.sum()
            val inv=1.0/sum
            return weights.entries.map{Swb2Bucket(it.key,it.value*inv)}
        }
        fun restore(bytes:ByteArray):Swb2ExperimentalEncoder=LearningSnapshot.decode(bytes){input->
            require(bytes.size==32960)
            require(input.readInt()==MAGIC)
            val seed=input.readLong()
            val updates=input.readInt().also{require(it in 0..1_000_000)}
            require(input.readInt()==4096)
            val emb=DoubleArray(4096){input.readDouble()}
            require(input.readInt()==17)
            val head=DoubleArray(17){input.readDouble()}
            require((emb.asList()+head.asList()).all{it.isFinite()&&it in -8.0..8.0})
            Swb2ExperimentalEncoder(seed,emb,head,updates)
        }
    }
}
