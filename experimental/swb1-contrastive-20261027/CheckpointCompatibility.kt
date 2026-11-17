package at.heinz.deviceai.research
import at.heinz.deviceai.learning.TrainableSubwordEncoder
import at.heinz.deviceai.learning.Swb2ExperimentalEncoder
import java.io.File

/** Native compatibility check of existing Android codecs, never a model import. */
object CheckpointCompatibility {
 @JvmStatic fun main(args:Array<String>){
  val baseline=File(args[0]).readBytes();val candidate=File(args[1]).readBytes()
  val old=TrainableSubwordEncoder.restore(baseline)
  require(old.updates==13000&&old.save().contentEquals(baseline))
  val sidecar=Swb2ExperimentalEncoder.restore(candidate)
  require(sidecar.save().contentEquals(candidate))
  require(runCatching{TrainableSubwordEncoder.restore(candidate)}.isFailure){"SWB2 must not silently enter original SWB1 loader"}
  val question="Weshalb wird der Behälter geprüft?";val left="Das Ventil ist geöffnet.";val right="Das Ventil ist nicht geöffnet."
  println("baselineIntent="+old.intentScore(question))
  println("baselineSimilarity="+old.similarity(left,right))
  println("sidecarIntent="+sidecar.intentScore(question))
  println("sidecarSimilarity="+sidecar.similarity(left,right))
  println("roundTripBytes="+baseline.size+","+candidate.size)
  println("productiveSwb1RejectsSwb2=true")
 }
}
