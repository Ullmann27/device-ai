import at.heinz.deviceai.learning.*
import at.heinz.deviceai.learning.context.Ctx3Shadow
import java.io.File
import java.util.Base64
/** Two separate JVMs, same directory/identity; metadata opt-in persists, referent state does not. */
fun main(args:Array<String>) {
 require(args.size==2&&args[1] in setOf("prime","resume"))
 val dir=File(args[0]);require(dir.isDirectory)
 val bytes=Base64.getDecoder().decode(object {}.javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())
 val fact=LearningExperienceFactory.create(LearningExperienceType.USER_FACT,LearningProvenance.USER_CONFIRMED,LearningTrustClass.OWNER_CONFIRMED,LearningStatus.CONFIRMED,"Max wohnt in Graz.",now=1)
 if(args[1]=="prime")Ctx3Shadow.configure(dir,true)
 Ctx3Shadow.evaluate(dir,bytes,listOf(fact),if(args[1]=="prime")"Wo wohnt Max?" else "Wo wohnt er?",emptyList(),"same-id-after-restart")
 val decision=Ctx3Shadow.events(dir,"same-id-after-restart").last().decision
 check(decision==if(args[1]=="prime")"QUERY" else "CLARIFY")
 println(args[1]+": "+decision+" JVM="+java.lang.management.ManagementFactory.getRuntimeMXBean().name)
}
