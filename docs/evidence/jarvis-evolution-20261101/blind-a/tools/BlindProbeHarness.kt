package at.heinz.deviceai.learning.span
import java.util.Base64
private fun encoded(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
fun main() {
 println("id\tpart\tkind\texpectedKnown\tcorrect\tfound\tverifier\tintegrity\tlatencyUs\tquestion64\texpected64\tactual64\tsourceId\treason64")
 for(c in blindCases) {
  val started=System.nanoTime()
  try {
   val sources=c.sources.map { EvidenceBoundNaturalLocationProbe.Evidence(it.id,it.revision,EvidenceBoundNaturalLocationProbe.digest(it.text),it.text) }
   val decision=EvidenceBoundNaturalLocationProbe.answer(c.question,sources)
   val found=decision as? EvidenceBoundNaturalLocationProbe.Decision.Found
   val verified=found?.let { EvidenceBoundNaturalLocationVerifier.verify(c.question,sources,it) } ?: false
   val integrity=found==null || sources.any { s -> s.sourceId==found.sourceId && s.revision==found.revision && s.sha256==found.sha256 && found.start>=0 && found.endExclusive<=s.text.length && found.start<found.endExclusive && s.text.substring(found.start,found.endExclusive)==found.text }
   val correct=if(c.expected==null)found==null else found!=null && found.text==c.expected && found.sourceId==c.expectedSource && verified && integrity
   val reason=(decision as? EvidenceBoundNaturalLocationProbe.Decision.Abstained)?.reason ?: ""
   println(listOf(c.id,c.part,c.kind,c.expected!=null,correct,found!=null,verified,integrity,(System.nanoTime()-started)/1000,encoded(c.question),encoded(c.expected ?: ""),encoded(found?.text ?: ""),found?.sourceId ?: "",encoded(reason)).joinToString("\t"))
  } catch(e:Throwable) {
   println(listOf(c.id,c.part,c.kind,c.expected!=null,false,false,false,false,(System.nanoTime()-started)/1000,encoded(c.question),encoded(c.expected ?: ""),"","",encoded(e.toString())).joinToString("\t"))
  }
 }
}
