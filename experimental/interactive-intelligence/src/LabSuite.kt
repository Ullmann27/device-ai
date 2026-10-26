package at.heinz.deviceai.research.interactive
import java.io.File
object LabSuite {
 @JvmStatic fun main(args:Array<String>){
  val train=LabCases.load(File(args[0]));val evaluation=LabCases.load(File(args[1]));val out=File(args[2])
  File(out,"models.tsv").writeText(ModelAblations.run(train,evaluation,out).joinToString("\n")+"\n")
  File(out,"mechanisms.tsv").writeText(MechanismExperiments.run(out).joinToString("\n")+"\n")
 }
}
