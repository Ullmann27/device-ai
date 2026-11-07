package at.heinz.deviceai.learning
import java.security.MessageDigest
private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
fun main() {
 val labels=(1..24).map { "Ankerwort"+((it-1)/26+97).toChar()+((it-1)%26+97).toChar() }
 val cases=listOf(
  Triple("dense-one","Was ist Quarzregel?","Quarzregel ".repeat(545).take(5995)),
  Triple("dense-24",labels.joinToString(" "),(labels.joinToString(" ")+" ").repeat(30).take(6000)),
  Triple("sparse-tail","Was ist Nachweisanker?","Fremder Vorspann. ".repeat(300)+" Der Nachweisanker steht am Ende."),
  Triple("no-overlap","Was ist Nachweisanker?","Fremder Vorspann. ".repeat(300)))
 for((name,question,source) in cases) {
  val hash=sha(source)
  repeat(4) { ContinuumEvidenceWindow.select(question,source,hash) }
  val times=(1..21).map {
   val started=System.nanoTime()
   val quote=ContinuumEvidenceWindow.select(question,source,hash)
   check(quote==null || quote.text.length<=350)
   (System.nanoTime()-started)/1_000_000.0
  }.sorted()
  println("scenario=$name sourceChars=${source.length} runs=21 warmups=4 p50Ms=${times[times.size/2]} p95Ms=${times[kotlin.math.ceil(times.size*.95).toInt()-1]}")
 }
}
