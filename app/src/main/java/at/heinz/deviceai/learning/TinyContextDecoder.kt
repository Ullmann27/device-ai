package at.heinz.deviceai.learning

import java.util.Random
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tanh

enum class DecoderSplit { TRAIN, AUDIT }
/** Caller supplies authored/public experimental text. No Android memory is read. */
data class DecoderCorpus(val texts:List<String>,val split:DecoderSplit) {
    init {
        require(texts.size in 1..64)
        require(texts.all { it.isNotBlank() && it.toByteArray(Charsets.UTF_8).size<=512 })
        require(texts.sumOf { it.toByteArray(Charsets.UTF_8).size }<=8192)
    }
}
data class DecoderTrainingReport(val steps:Int,val interrupted:Boolean,val lossBefore:Double,val lossAfter:Double)
data class DecoderGeneration(val text:String,val bytesGenerated:Int,val ended:Boolean)

/** Canonical, byte-stable prefix used by both teacher forcing and generation.
 * The evidence and the question are input context, NEVER language-model targets.
 */
fun decoderEvidencePrefix(evidence:String,question:String):String {
    fun valid(text:String):Boolean =
        text.isNotBlank() && text.toByteArray(Charsets.UTF_8).size<=160 &&
        text.none { it.code<32 || it.code==127 }
    require(valid(evidence) && valid(question)) { "Evidence/question must be bounded single lines" }
    return "Beleg: $evidence\nFrage: $question\nAntwort: ".also {
        require(it.toByteArray(Charsets.UTF_8).size<=512)
    }
}

/** Each pair must be explicitly authored for research; it is not an owner memory. */
data class DecoderEvidencePair(val evidence:String,val question:String,val answer:String) {
    init {
        val prefix=decoderEvidencePrefix(evidence,question)
        require(answer.isNotBlank() && answer.toByteArray(Charsets.UTF_8).size<=160 &&
            answer.none { it.code<32 || it.code==127 })
        require(prefix.toByteArray(Charsets.UTF_8).size+answer.toByteArray(Charsets.UTF_8).size<=640)
    }
    fun prefix():String=decoderEvidencePrefix(evidence,question)
    fun conditionerText():String=evidence+"\n"+question
}
data class DecoderEvidenceCorpus(val pairs:List<DecoderEvidencePair>,val split:DecoderSplit) {
    init {
        require(pairs.size in 1..64)
        require(pairs.sumOf { it.prefix().toByteArray(Charsets.UTF_8).size+
            it.answer.toByteArray(Charsets.UTF_8).size }<=16384)
    }
}

/** Experimental causal byte-language model: learned positional embeddings,
 * tanh context MLP, 258-way softmax, exact SGD backpropagation. No model files,
 * APIs or pretrained weights. Optional 16-vector conditioning consumes the
 * existing shared subword representation. This class has no productive chat hook.
 */
class TinyContextDecoder private constructor(
    private val seed:Long,private var embedding:DoubleArray,
    private var hiddenWeights:DoubleArray,private var hiddenBias:DoubleArray,
    private var outputWeights:DoubleArray,private var outputBias:DoubleArray,
    var updates:Int
) {
    constructor(seed:Long=91):this(seed,initial(seed,VOCAB*EMBED,.08),
        initial(seed xor 19,INPUT*HIDDEN,.08),DoubleArray(HIDDEN),
        initial(seed xor 37,HIDDEN*VOCAB,.08),DoubleArray(VOCAB),0)

    val parameterCount:Int get()=embedding.size+hiddenWeights.size+hiddenBias.size+outputWeights.size+outputBias.size
    private data class Forward(val x:DoubleArray,val h:DoubleArray,val p:DoubleArray)
    private data class Sample(val context:IntArray,val target:Int)
    private data class PairedSample(val sample:Sample,val conditioner:DoubleArray)

    private fun condition(values:DoubleArray):DoubleArray {
        require(values.size==CONDITION && values.all { it.isFinite() && it in -1.0..1.0 })
        return values.copyOf()
    }
    private fun context(bytes:ByteArray):IntArray = IntArray(CONTEXT) { i ->
        val index=bytes.size-CONTEXT+i
        if(index<0)BOS else bytes[index].toInt() and 255
    }
    private fun forward(tokens:IntArray,c:DoubleArray):Forward {
        val x=DoubleArray(INPUT)
        for(position in 0 until CONTEXT)for(i in 0 until EMBED)x[position*EMBED+i]=embedding[tokens[position]*EMBED+i]
        c.copyInto(x,CONTEXT*EMBED)
        val h=DoubleArray(HIDDEN) { j ->
            var sum=hiddenBias[j]
            for(i in x.indices)sum+=hiddenWeights[j*INPUT+i]*x[i]
            tanh(sum)
        }
        val logits=DoubleArray(VOCAB) { t ->
            var sum=outputBias[t]
            for(j in 0 until HIDDEN)sum+=outputWeights[j*VOCAB+t]*h[j]
            sum
        }
        // BOS pads context only; it is never a generation target.
        logits[BOS]=Double.NEGATIVE_INFINITY
        val maximum=logits.maxOrNull()!!
        val p=DoubleArray(VOCAB) { exp(logits[it]-maximum) }
        val total=p.sum();for(i in p.indices)p[i]/=total
        return Forward(x,h,p)
    }
    private fun samples(corpus:DecoderCorpus):List<Sample> = corpus.texts.flatMap { text ->
        val bytes=text.toByteArray(Charsets.UTF_8)
        (0..bytes.size).map { n ->
            val tokens=IntArray(CONTEXT) { position ->
                val i=n-CONTEXT+position
                if(i<0)BOS else bytes[i].toInt() and 255
            }
            Sample(tokens,if(n==bytes.size)EOS else bytes[n].toInt() and 255)
        }
    }
    /** Train ONLY the answer bytes, plus EOS. The prefix is already present in
     * the causal context at the first answer byte; every example has its OWN
     * condition derived from evidence+question, not a frozen batch-wide vector.
     */
    private fun pairedBuckets(
        corpus:DecoderEvidenceCorpus,
        conditionerFor:(DecoderEvidencePair)->DoubleArray
    ):List<PairedSample> = corpus.pairs.map { pair ->
        val prefix=pair.prefix().toByteArray(Charsets.UTF_8)
        val full=prefix+pair.answer.toByteArray(Charsets.UTF_8)
        val c=condition(conditionerFor(pair))
        (prefix.size..full.size).map { n ->
            val contextTokens=IntArray(CONTEXT) { position ->
                val i=n-CONTEXT+position
                if(i<0)BOS else full[i].toInt() and 255
            }
            PairedSample(Sample(contextTokens,if(n==full.size)EOS else full[n].toInt() and 255),c)
        }
    }
    private fun pairedSamples(corpus:DecoderEvidenceCorpus,
        conditionerFor:(DecoderEvidencePair)->DoubleArray):List<PairedSample> =
        pairedBuckets(corpus,conditionerFor).flatten()

    private fun pairedLoss(samples:List<PairedSample>):Double =
        samples.sumOf { -ln(forward(it.sample.context,it.conditioner).p[it.sample.target].coerceAtLeast(1e-12)) }/samples.size

    private fun loss(samples:List<Sample>,c:DoubleArray):Double =
        samples.sumOf { -ln(forward(it.context,c).p[it.target].coerceAtLeast(1e-12)) }/samples.size

    @Synchronized fun nextTokenProbabilities(prompt:String,conditioning:DoubleArray=DoubleArray(CONDITION)):DoubleArray {
        require(prompt.toByteArray(Charsets.UTF_8).size<=512)
        return forward(context(prompt.toByteArray(Charsets.UTF_8)),condition(conditioning)).p
    }
    @Synchronized fun evaluate(corpus:DecoderCorpus,conditioning:DoubleArray=DoubleArray(CONDITION)):Double =
        loss(samples(corpus),condition(conditioning))

    @Synchronized fun train(
        corpus:DecoderCorpus,steps:Int=1000,learningRate:Double=.035,
        conditioning:DoubleArray=DoubleArray(CONDITION),maxMillis:Long=3000,
        cancelled:()->Boolean={false},nowNanos:()->Long=System::nanoTime
    ):DecoderTrainingReport {
        require(corpus.split==DecoderSplit.TRAIN) { "Audit corpus cannot be used for gradients" }
        require(steps in 1..20000 && learningRate in .0001.. .08 && maxMillis in 1..5000)
        require(updates<=MAX_UPDATES-steps) { "Decoder update budget exhausted" }
        val examples=samples(corpus);val c=condition(conditioning)
        val before=loss(examples,c);val start=nowNanos();var completed=0
        while(completed<steps && !cancelled() && !Thread.currentThread().isInterrupted && nowNanos()-start<maxMillis*1_000_000L) {
            val sample=examples[updates%examples.size]
            update(sample,c,learningRate)
            updates++;completed++
        }
        return DecoderTrainingReport(completed,completed<steps,before,loss(examples,c))
    }
    /** Separate from legacy unpaired training to preserve checkpoint semantics.
     * Different evidence/question pairs must be supplied by the caller; no
     * private experiences, model responses or audit items are ingested here.
     */
    @Synchronized fun evaluateEvidencePairs(
        corpus:DecoderEvidenceCorpus,
        conditionerFor:(DecoderEvidencePair)->DoubleArray
    ):Double=pairedLoss(pairedSamples(corpus,conditionerFor))

    @Synchronized fun trainEvidencePairs(
        corpus:DecoderEvidenceCorpus,steps:Int=600,learningRate:Double=.035,
        conditionerFor:(DecoderEvidencePair)->DoubleArray,maxMillis:Long=3000,
        cancelled:()->Boolean={false},nowNanos:()->Long=System::nanoTime
    ):DecoderTrainingReport {
        require(corpus.split==DecoderSplit.TRAIN)
        require(steps in 1..20000 && learningRate in .0001.. .08 && maxMillis in 1..5000)
        require(updates<=MAX_UPDATES-steps)
        val buckets=pairedBuckets(corpus,conditionerFor)
        val examples=buckets.flatten()
        val before=pairedLoss(examples)
        val started=nowNanos()
        var completed=0
        while(completed<steps && !cancelled() && !Thread.currentThread().isInterrupted &&
            nowNanos()-started<maxMillis*1_000_000L) {
            val bucket=updates%buckets.size
            val character=(updates/buckets.size)%buckets[bucket].size
            val sample=buckets[bucket][character]
            update(sample.sample,sample.conditioner,learningRate)
            updates++;completed++
        }
        return DecoderTrainingReport(completed,completed<steps,before,pairedLoss(examples))
    }

    private fun update(sample:Sample,c:DoubleArray,rate:Double) {
        val f=forward(sample.context,c)
        val error=f.p.copyOf();error[sample.target]-=1
        // Backpropagate with pre-update weights, including repeated byte buckets.
        val dh=DoubleArray(HIDDEN) { j ->
            var sum=0.0;for(t in 0 until VOCAB)sum+=error[t]*outputWeights[j*VOCAB+t]
            sum*(1-f.h[j]*f.h[j])
        }
        val dx=DoubleArray(CONTEXT*EMBED) { i ->
            var sum=0.0;for(j in 0 until HIDDEN)sum+=dh[j]*hiddenWeights[j*INPUT+i];sum
        }
        for(t in 0 until VOCAB) {
            outputBias[t]=clip(outputBias[t]-rate*error[t])
            for(j in 0 until HIDDEN)outputWeights[j*VOCAB+t]=clip(outputWeights[j*VOCAB+t]-rate*error[t]*f.h[j])
        }
        for(j in 0 until HIDDEN) {
            hiddenBias[j]=clip(hiddenBias[j]-rate*dh[j])
            for(i in 0 until INPUT)hiddenWeights[j*INPUT+i]=clip(hiddenWeights[j*INPUT+i]-rate*dh[j]*f.x[i])
        }
        for(position in 0 until CONTEXT)for(i in 0 until EMBED) {
            val index=sample.context[position]*EMBED+i
            embedding[index]=clip(embedding[index]-rate*dx[position*EMBED+i])
        }
    }

    /** Greedy, reproducible, bounded continuation. Raw bytes are decoded as UTF-8;
     * malformed sequences may appear as replacement characters in an immature model.
     */
    @Synchronized fun generate(prompt:String,maxBytes:Int=64,conditioning:DoubleArray=DoubleArray(CONDITION)):DecoderGeneration {
        val prefix=prompt.toByteArray(Charsets.UTF_8)
        require(prefix.size<=512 && maxBytes in 1..128)
        val c=condition(conditioning);var tokens=context(prefix)
        val output=java.io.ByteArrayOutputStream();var ended=false
        repeat(maxBytes) {
            if(!ended) {
                val p=forward(tokens,c).p
                var token=0;for(i in 1 until VOCAB)if(p[i]>p[token])token=i
                if(token==EOS)ended=true else {
                    output.write(token)
                    tokens=IntArray(CONTEXT) { i -> if(i==CONTEXT-1)token else tokens[i+1] }
                }
            }
        }
        return DecoderGeneration(output.toByteArray().toString(Charsets.UTF_8),output.size(),ended)
    }

    @Synchronized fun save():ByteArray=LearningSnapshot.encode { out ->
        out.writeInt(MAGIC);out.writeLong(seed);out.writeInt(updates)
        listOf(embedding,hiddenWeights,hiddenBias,outputWeights,outputBias).forEach { array ->
            out.writeInt(array.size);array.forEach(out::writeDouble)
        }
    }
    /** Atomic experimental persistence; retain the previous valid checkpoint. */
    @Synchronized fun saveTo(file:java.io.File) {
        val next=save();restore(next)
        val directory=file.absoluteFile.parentFile
        check(directory.exists()||directory.mkdirs())
        fun write(target:java.io.File,bytes:ByteArray) {
            val temp=java.io.File.createTempFile("decoder-",".tmp",directory)
            try {
                java.io.FileOutputStream(temp).use { it.write(bytes);it.fd.sync() }
                try {
                    java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } catch(_:java.nio.file.AtomicMoveNotSupportedException) {
                    java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            } finally { temp.delete() }
        }
        if(file.exists()) {
            require(file.length() in 36..65536)
            val old=file.readBytes();restore(old)
            write(java.io.File(file.absolutePath+".lkg"),old)
        }
        write(file,next)
    }

    companion object {
        const val CONTEXT=8
        const val CONDITION=16
        private const val EMBED=8
        private const val HIDDEN=16
        private const val INPUT=CONTEXT*EMBED+CONDITION
        private const val VOCAB=258
        private const val BOS=256
        private const val EOS=257
        private const val MAGIC=0x43445831
        private const val MAX_UPDATES=10000000
        private fun initial(seed:Long,size:Int,scale:Double):DoubleArray {
            val r=Random(seed);return DoubleArray(size) { (r.nextDouble()*2-1)*scale }
        }
        private fun clip(x:Double)=x.coerceIn(-8.0,8.0)
        fun restoreFrom(file:java.io.File):TinyContextDecoder {
            require(file.length() in 36..65536)
            return restore(file.readBytes())
        }
        fun restore(bytes:ByteArray):TinyContextDecoder=LearningSnapshot.decode(bytes) { input ->
            require(input.readInt()==MAGIC)
            val seed=input.readLong();val updates=input.readInt().also { require(it in 0..MAX_UPDATES) }
            val arrays=listOf(VOCAB*EMBED,INPUT*HIDDEN,HIDDEN,HIDDEN*VOCAB,VOCAB).map { expected ->
                require(input.readInt()==expected)
                DoubleArray(expected) { input.readDouble().also { x -> require(x.isFinite() && x in -8.0..8.0) } }
            }
            TinyContextDecoder(seed,arrays[0],arrays[1],arrays[2],arrays[3],arrays[4],updates)
        }
    }
}
