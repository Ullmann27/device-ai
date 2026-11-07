package at.heinz.deviceai.learning

import java.security.MessageDigest
import java.util.Base64
private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
private fun encoded(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
fun main() {
 var passed=0
 println("id\tpass\texpectedNonNull\tactualNonNull\tintegrity\tanchorLabels\tstart\tend\telapsedUs\tquote64\terror64")
 for(c in windowCases) {
  val started=System.nanoTime()
  try {
   val q=ContinuumEvidenceWindow.select(c.question,c.source,if(c.wrongHash)"0".repeat(64) else hash(c.source),c.maxChars)
   val integrity=q==null || (q.text==c.source.substring(q.start,q.endExclusive) && q.text.length<=c.maxChars && q.sourceSha256==hash(c.source) && q.quotationSha256==hash(q.text) && q.text==c.source.toByteArray(Charsets.UTF_8).copyOfRange(q.utf8Start,q.utf8EndExclusive).toString(Charsets.UTF_8) && !q.text.first().isLowSurrogate() && !q.text.last().isHighSurrogate())
   val anchors=q==null || c.contains.all { it in q.text }
   val success=(q!=null)==c.nonnull && integrity && anchors
   if(success)passed++
   println(listOf(c.id,success,c.nonnull,q!=null,integrity,anchors,q?.start ?: -1,q?.endExclusive ?: -1,(System.nanoTime()-started)/1000,encoded(q?.text ?: ""),"").joinToString("\t"))
  } catch(e:Throwable) {
   println(listOf(c.id,false,c.nonnull,"ERROR",false,false,-1,-1,(System.nanoTime()-started)/1000,"",encoded(e.toString())).joinToString("\t"))
  }
 }
 System.err.println("FROZEN_WINDOW_CASES=${windowCases.size} passed=$passed failed=${windowCases.size-passed}")
}
