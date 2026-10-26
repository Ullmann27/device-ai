package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.learning.*
import at.heinz.deviceai.learning.interactive.ResearchFeedback
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

/** Actual existing neural code, synthetic examples only. No production checkpoint read/write. */
object ModelAblations {
 data class Pairing(val query:String,val positive:String,val negative:String)
 fun pairs(cases:List<LabCase>)=cases.filter{it.kind=="topic"}.map { c ->
  val name=c.entities[0].label;val other=c.entities[1].label
  Pairing("Erkläre den Druck auf $name.","$name hat wenig Luftdruck.","$other hat wenig Luftdruck.")
 }
 fun run(train:List<LabCase>,evaluation:List<LabCase>,out:File):List<String> {
  val proposed=pairs(train);val tests=pairs(evaluation)
  val feedback=ResearchFeedback(File(out,"feedback"))
  val ids=proposed.mapIndexed { i,p ->
   val c=feedback.propose("Nein, die passende Antwort lautet: ${p.positive}","train-$i")
   if(c.experience.status==LearningStatus.OBSERVED)feedback.review(c.experience.id,c.reviewDigest,ResearchFeedback.Review.CONFIRM,2)
   c.experience.id
  }
  val admitted=feedback.trainingCandidates().map{it.id}.toSet()
  val training=proposed.filterIndexed { i,_->ids[i] in admitted }
  require(training.size==proposed.size)
  File(out,"training-lineage.txt").writeText("syntheticReviewed=true\nactiveFingerprint=${feedback.trainingFingerprint()}\n"+ids.joinToString("\n"))
  require(train.map{it.id}.intersect(evaluation.map{it.id}.toSet()).isEmpty())
  require(train.flatMap{it.entities.map{e->e.label}}.intersect(evaluation.flatMap{it.entities.map{e->e.label}}.toSet()).isEmpty())
  val examples=training.flatMap{listOf(SubwordSemanticExample(it.query,it.positive,true),SubwordSemanticExample(it.query,it.negative,false))}
  val lines=mutableListOf("seed\tarm\tcorrect\ttotal\twrong\tabstain\tnanos\tupdates\tcheckpointSha256\tbytes")
  fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
  val modelDir=File(out,"checkpoints").also{it.mkdirs()}
  for(seed in listOf(73L,101L,211L)) {
   val swb1=TrainableSubwordEncoder(seed);val swb2=Swb2ExperimentalEncoder(seed);val cre=Cre1InferenceEncoder.createResearch(seed)
   fun measure(arm:String,updates:Int,snapshot:()->ByteArray,score:(String,String)->Double) {
    val start=System.nanoTime();var right=0;var abstain=0
    for(t in tests){val a=score(t.query,t.positive);val b=score(t.query,t.negative);require(a.isFinite()&&b.isFinite());if(a>b)right++ else if(a==b)abstain++}
    val ns=System.nanoTime()-start;val bytes=snapshot()
    // Even rejected research checkpoints are available, gzip only changes storage.
    GZIPOutputStream(File(modelDir,"$seed-$arm.bin.gz").outputStream()).use{it.write(bytes)}
    lines+=listOf(seed,arm,right,tests.size,tests.size-right-abstain,abstain,ns,updates,hash(bytes),bytes.size).joinToString("\t")
   }
   measure("SWB1_UNTRAINED",swb1.updates,swb1::save,swb1::similarity)
   measure("SWB2_UNTRAINED",swb2.updates,swb2::save,swb2::similarity)
   measure("CRE1_UNTRAINED",cre.steps,cre::save,cre::score)
   val start=System.nanoTime()
   val step=swb1.trainSemantic(examples,steps=96,learningRate=0.1,maxMillis=4000)
   check(step.steps==96) {"SWB1 budget interruption"}
   repeat(96){swb2.updateSemantic(examples[it%examples.size],rate=0.1)}
   // 96 explicit contrastive updates on precisely the same12 positive/negative pairs.
   repeat(96){val p=training[it%training.size];cre.trainSgdStep(p.query,listOf(p.positive,p.negative),learningRate=0.0001)}
   File(out,"training-$seed.txt").writeText("updates=96 per arm\nnanos=${System.nanoTime()-start}\ntrainOnly=true\n")
   measure("SWB1_TRAINED",swb1.updates,swb1::save,swb1::similarity)
   measure("SWB2_TRAINED",swb2.updates,swb2::save,swb2::similarity)
   measure("CRE1_TRAINED",cre.steps,cre::save,cre::score)
   // Retention probe: train another family, then re-evaluate the original family.
   val before=training.count{swb2.similarity(it.query,it.positive)>swb2.similarity(it.query,it.negative)}
   val other=training.map{SubwordSemanticExample(it.query.replace("Druck","Farbe"),it.negative.replace("Luftdruck","Licht"),true)}
   repeat(96){swb2.updateSemantic(other[it%other.size],rate=0.1)}
   val after=training.count{swb2.similarity(it.query,it.positive)>swb2.similarity(it.query,it.negative)}
   File(out,"retention-$seed.txt").writeText("originalTrainFamilyBefore=$before/${training.size}\noriginalTrainFamilyAfter=$after/${training.size}\nsecondFamilyUpdates=96\nNoReplayOrEwcClaim=true\n")
   measure("SWB2_AFTER_SECOND_FAMILY",swb2.updates,swb2::save,swb2::similarity)
   val decoder=TinyContextDecoder(seed)
   val ds=System.nanoTime();val generations=tests.map{t->decoder.generate(t.query,64,swb1.representation(t.query)).text}
   val dn=System.nanoTime()-ds
   val good=generations.zip(tests).count{(a,t)->a==t.positive}
   val bytes=decoder.save();GZIPOutputStream(File(modelDir,"$seed-CDX1_UNTRAINED.bin.gz").outputStream()).use{it.write(bytes)}
   File(out,"cdx1-$seed.txt").writeText(generations.joinToString("\n") { java.util.Base64.getEncoder().encodeToString(it.toByteArray()) })
   lines+=listOf(seed,"CDX1_UNTRAINED_EXACT_ANSWER",good,tests.size,tests.size-good,0,dn,0,hash(bytes),bytes.size).joinToString("\t")
  }
  return lines
 }
}
