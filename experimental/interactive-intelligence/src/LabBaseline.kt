package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.cognitive.LocalConversationContext
import at.heinz.deviceai.learning.context.Ctx3Context
import at.heinz.deviceai.learning.context.Ctx3Decision
import java.io.File

object LabBaseline {
 @JvmStatic fun main(args:Array<String>) {
  println("id\tkind\tarm\tcorrect\tabstain\twrong\tretained\tbytes\tnanos")
  for(c in LabCases.load(File(args.single()))) {
   val ctx=Ctx3Context();val raw=LocalConversationContext()
   for((q,a) in c.history){ctx.decide(q,c.entities,"fixture-v1");raw.record("synthetic",q,a)}
   val start=System.nanoTime();val result=ctx.decide(c.question,c.entities,"fixture-v1");val ns=System.nanoTime()-start
   val ids=if(result is Ctx3Decision.Query) c.entities.filter { it.sourceIds.intersect(result.sourceIds).isNotEmpty() }.map{it.id} else emptyList()
   val abstain=result !is Ctx3Decision.Query || ids.isEmpty()
   val correct=if(c.expected=="?")abstain else ids==listOf(c.expected)
   val text=raw.render("synthetic");val retained=c.required.isEmpty()||text.contains(c.required)
   println(listOf(c.id,c.kind,"CTX3_BASELINE",correct,abstain,!correct&&!abstain,retained,text.toByteArray().size,ns).joinToString("\t"))
  }
 }
}
