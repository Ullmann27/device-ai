package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.learning.interactive.InteractiveDialogueContext
import java.io.File

object LabEvaluation {
 @JvmStatic fun main(args:Array<String>) {
  println("id\tkind\tarm\tcorrect\tabstain\twrong\tretained\tbytes\tnanos")
  for(c in LabCases.load(File(args[0])))for(mode in InteractiveDialogueContext.Mode.entries) {
   val ctx=InteractiveDialogueContext()
   for((q,a) in c.history)ctx.record("synthetic",q,a,c.entities,"fixture-v1")
   val start=System.nanoTime();val r=ctx.plan("synthetic",c.question,c.entities,"fixture-v1",mode);val ns=System.nanoTime()-start
   val correct=if(c.expected=="?")r.clarification else !r.clarification&&r.ids==listOf(c.expected)
   val retained=c.required.isEmpty()||r.context.contains(c.required)
   // RAW only measures original context retention. Its ids are the candidate's
   // side-channel diagnostics, not an invented original-architecture resolver.
   println(listOf(c.id,c.kind,mode.name,if(mode==InteractiveDialogueContext.Mode.RAW)"NA" else correct,r.clarification,!correct&&!r.clarification,retained,r.context.toByteArray().size,ns).joinToString("\t"))
  }
 }
}
