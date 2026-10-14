package at.heinz.deviceai.learning

import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

data class SubwordIntentExample(val text:String,val label:Int)
data class SubwordSemanticExample(val left:String,val right:String,val similar:Boolean)
data class SubwordEvaluation(val loss:Double,val correct:Int,val total:Int)
data class SubwordTrainingResult(
    val steps:Int,
    val stoppedEarly:Boolean,
    val before:SubwordEvaluation,
    val after:SubwordEvaluation
)

/**
 * Original local trainable text encoder.
 *
 * Character 2-4 grams are hashed into a bounded table of trainable embeddings.
 * The same embedding table serves both intent classification and text-pair
 * similarity. No vocabulary, model file or pretrained weight is imported.
 */
class TrainableSubwordEncoder private constructor(
    private val seed:Long,
    private var embeddings:DoubleArray,
    private var intentHead:DoubleArray,
    var updates:Int
) {
    constructor(seed:Long=73L):this(
        seed,
        initialEmbeddings(seed),
        initialIntentHead(seed),
        0
    )

    val parameterCount:Int get()=embeddings.size+intentHead.size

    @Synchronized fun intentScore(text:String):Double {
        val z=encode(text)
        var logit=intentHead[DIMENSIONS]
        for(i in 0 until DIMENSIONS)logit+=z[i]*intentHead[i]
        return sigmoid(logit)
    }

    @Synchronized fun similarity(left:String,right:String):Double {
        val a=encode(left);val b=encode(right)
        var dot=0.0
        for(i in 0 until DIMENSIONS)dot+=a[i]*b[i]
        return sigmoid(dot*SIMILARITY_SCALE)
    }

    @Synchronized fun evaluateIntent(examples:List<SubwordIntentExample>):SubwordEvaluation {
        require(examples.size in 1..128)
        var loss=0.0;var correct=0
        examples.forEach { ex ->
            require(ex.label in 0..1)
            val p=intentScore(ex.text).coerceIn(1e-9,1.0-1e-9)
            loss+=-(ex.label*ln(p)+(1-ex.label)*ln(1-p))
            if((if(p>=0.5)1 else 0)==ex.label)correct++
        }
        return SubwordEvaluation(loss/examples.size,correct,examples.size)
    }

    @Synchronized fun evaluateSemantic(examples:List<SubwordSemanticExample>):SubwordEvaluation {
        require(examples.size in 1..128)
        var loss=0.0;var correct=0
        examples.forEach { ex ->
            val target=if(ex.similar)1 else 0
            val p=similarity(ex.left,ex.right).coerceIn(1e-9,1.0-1e-9)
            loss+=-(target*ln(p)+(1-target)*ln(1-p))
            if((p>=0.5)==ex.similar)correct++
        }
        return SubwordEvaluation(loss/examples.size,correct,examples.size)
    }

    @Synchronized fun trainIntent(
        examples:List<SubwordIntentExample>,
        steps:Int=400,
        learningRate:Double=0.055,
        maxMillis:Long=1800,
        cancelled:()->Boolean={false},
        nowNanos:()->Long=System::nanoTime
    ):SubwordTrainingResult {
        require(examples.size in 1..128 && steps in 1..2000)
        require(learningRate in 0.0001..0.15 && maxMillis in 1..4000)
        examples.forEach { require(it.label in 0..1);subwordBuckets(it.text) }
        val before=evaluateIntent(examples)
        val start=nowNanos();var completed=0
        while(completed<steps && !cancelled() && !Thread.currentThread().isInterrupted &&
            nowNanos()-start<maxMillis*1_000_000L) {
            val ex=examples[(updates+completed)%examples.size]
            updateIntent(ex,learningRate)
            completed++
        }
        updates=(updates+completed).coerceAtMost(1_000_000)
        return SubwordTrainingResult(completed,completed<steps,before,evaluateIntent(examples))
    }

    @Synchronized fun trainSemantic(
        examples:List<SubwordSemanticExample>,
        steps:Int=400,
        learningRate:Double=0.035,
        maxMillis:Long=1800,
        cancelled:()->Boolean={false},
        nowNanos:()->Long=System::nanoTime
    ):SubwordTrainingResult {
        require(examples.size in 1..128 && steps in 1..2000)
        require(learningRate in 0.0001..0.12 && maxMillis in 1..4000)
        examples.forEach { subwordBuckets(it.left);subwordBuckets(it.right) }
        val before=evaluateSemantic(examples)
        val start=nowNanos();var completed=0
        while(completed<steps && !cancelled() && !Thread.currentThread().isInterrupted &&
            nowNanos()-start<maxMillis*1_000_000L) {
            val ex=examples[(updates+completed)%examples.size]
            updateSemantic(ex,learningRate)
            completed++
        }
        updates=(updates+completed).coerceAtMost(1_000_000)
        return SubwordTrainingResult(completed,completed<steps,before,evaluateSemantic(examples))
    }

    private fun updateIntent(ex:SubwordIntentExample,rate:Double) {
        val buckets=subwordBuckets(ex.text)
        val z=encodeBuckets(buckets)
        var logit=intentHead[DIMENSIONS]
        for(i in 0 until DIMENSIONS)logit+=z[i]*intentHead[i]
        val error=sigmoid(logit)-ex.label
        val oldHead=intentHead.copyOf()
        for(i in 0 until DIMENSIONS)intentHead[i]=clip(intentHead[i]-rate*error*z[i])
        intentHead[DIMENSIONS]=clip(intentHead[DIMENSIONS]-rate*error)
        val scale=1.0/buckets.size
        buckets.forEach { bucket ->
            val offset=bucket*DIMENSIONS
            for(i in 0 until DIMENSIONS) {
                val grad=error*oldHead[i]*(1-z[i]*z[i])*scale
                embeddings[offset+i]=clip(embeddings[offset+i]-rate*grad)
            }
        }
    }

    private fun updateSemantic(ex:SubwordSemanticExample,rate:Double) {
        val aBuckets=subwordBuckets(ex.left);val bBuckets=subwordBuckets(ex.right)
        val a=encodeBuckets(aBuckets);val b=encodeBuckets(bBuckets)
        var dot=0.0
        for(i in 0 until DIMENSIONS)dot+=a[i]*b[i]
        val target=if(ex.similar)1.0 else 0.0
        val error=sigmoid(dot*SIMILARITY_SCALE)-target
        val aGrad=DoubleArray(DIMENSIONS) { i ->
            error*SIMILARITY_SCALE*b[i]*(1-a[i]*a[i])
        }
        val bGrad=DoubleArray(DIMENSIONS) { i ->
            error*SIMILARITY_SCALE*a[i]*(1-b[i]*b[i])
        }
        val aScale=1.0/aBuckets.size;val bScale=1.0/bBuckets.size
        aBuckets.forEach { bucket ->
            val offset=bucket*DIMENSIONS
            for(i in 0 until DIMENSIONS)
                embeddings[offset+i]=clip(embeddings[offset+i]-rate*aGrad[i]*aScale)
        }
        bBuckets.forEach { bucket ->
            val offset=bucket*DIMENSIONS
            for(i in 0 until DIMENSIONS)
                embeddings[offset+i]=clip(embeddings[offset+i]-rate*bGrad[i]*bScale)
        }
    }

    private fun encode(text:String):DoubleArray=encodeBuckets(subwordBuckets(text))

    private fun encodeBuckets(buckets:IntArray):DoubleArray {
        val z=DoubleArray(DIMENSIONS)
        buckets.forEach { bucket ->
            val offset=bucket*DIMENSIONS
            for(i in 0 until DIMENSIONS)z[i]+=embeddings[offset+i]
        }
        val scale=1.0/buckets.size
        for(i in z.indices)z[i]=tanh(z[i]*scale)
        return z
    }

    @Synchronized fun save():ByteArray=LearningSnapshot.encode { out ->
        out.writeInt(MAGIC);out.writeLong(seed);out.writeInt(updates)
        out.writeInt(embeddings.size);embeddings.forEach(out::writeDouble)
        out.writeInt(intentHead.size);intentHead.forEach(out::writeDouble)
    }

    companion object {
        const val BUCKETS=256
        const val DIMENSIONS=16
        private const val MAGIC=0x53574231
        private const val SIMILARITY_SCALE=2.5

        private fun initialEmbeddings(seed:Long):DoubleArray {
            val random=Random(seed)
            return DoubleArray(BUCKETS*DIMENSIONS) { (random.nextDouble()*2.0-1.0)*0.08 }
        }
        private fun initialIntentHead(seed:Long):DoubleArray {
            val random=Random(seed xor 0x51a7L)
            return DoubleArray(DIMENSIONS+1) { if(it==DIMENSIONS)0.0 else (random.nextDouble()*2.0-1.0)*0.08 }
        }
        private fun clip(value:Double)=value.coerceIn(-8.0,8.0)
        private fun sigmoid(value:Double):Double =
            if(value>=0)1.0/(1.0+exp(-value)) else exp(value)/(1.0+exp(value))

        internal fun subwordBuckets(text:String):IntArray {
            require(text.length in 1..2048)
            val normalized=text.lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().take(512)
            require(normalized.isNotEmpty())
            val padded="^$normalized$"
            val values=linkedSetOf<Int>()
            for(n in 2..4) {
                if(padded.length<n)continue
                for(i in 0..padded.length-n) {
                    values+=stableBucket(padded.substring(i,i+n))
                    if(values.size>=128)break
                }
                if(values.size>=128)break
            }
            if(values.isEmpty())values+=stableBucket(padded)
            return values.toIntArray()
        }

        private fun stableBucket(value:String):Int {
            var hash=0x811c9dc5L
            value.toByteArray(Charsets.UTF_8).forEach { byte ->
                hash=hash xor (byte.toLong() and 0xffL)
                hash=(hash*0x01000193L) and 0xffffffffL
            }
            return (hash%BUCKETS).toInt()
        }

        fun restore(bytes:ByteArray):TrainableSubwordEncoder=LearningSnapshot.decode(bytes) { input ->
            require(input.readInt()==MAGIC)
            val seed=input.readLong()
            val updates=input.readInt().also { require(it in 0..1_000_000) }
            val eSize=input.readInt().also { require(it==BUCKETS*DIMENSIONS) }
            val embeddings=DoubleArray(eSize) { input.readDouble() }
            val hSize=input.readInt().also { require(it==DIMENSIONS+1) }
            val head=DoubleArray(hSize) { input.readDouble() }
            require((embeddings.asList()+head.asList()).all { it.isFinite() && abs(it)<=8.0 })
            TrainableSubwordEncoder(seed,embeddings,head,updates)
        }
    }
}
