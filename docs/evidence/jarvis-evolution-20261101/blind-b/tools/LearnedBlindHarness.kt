package at.heinz.deviceai.learning.span

import java.io.File
import java.security.MessageDigest
import java.util.Base64
private fun encoded(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
fun main(args:Array<String>) {
 val checkpoint=File(args[0]).readBytes()
 check(hash(checkpoint)=="c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92")
 val raw=ContinuumResidualSpanCore.restore(checkpoint)
 check(raw.parameterCount==4110 && raw.updates==3200 && raw.features.naturalEntityBindings)
 val rawBefore=raw.save()
 val verified=VerifiedLocationSpanResearch(checkpoint)
 check(verified.checkpointSha256==hash(checkpoint))
 println("mode\tid\tpart\tkind\texpectedKnown\tcorrect\tfound\tverifier\tintegrity\tlatencyUs\tquestion64\texpected64\tactual64\tsourceId\treason64\tconfidence\tsnapshotSha256\tmodelSha256")
 for(c in blindCases) {
  val sources=c.sources.map { EvidenceBoundNaturalLocationProbe.Evidence(it.id,it.revision,EvidenceBoundNaturalLocationProbe.digest(it.text),it.text) }
  val snapshot=LocationEvidenceSnapshot.freeze(sources)
  for(mode in listOf("TRAINED_RAW","TRAINED_VERIFIED")) {
   val started=System.nanoTime()
   try {
    var confidence:Double?=null
    var reason=""
    val found=if(mode=="TRAINED_RAW") {
     val prediction=raw.predict(sources.joinToString("; "){it.text},c.question)
     confidence=prediction.confidence
     if(prediction.answer==null) {reason="LEARNED_NO_ANSWER";null} else {
      var offset=0
      var mapped:EvidenceBoundNaturalLocationProbe.Decision.Found?=null
      for(s in sources) {
       if(prediction.start>=offset && prediction.end<=offset+s.text.length) {
        mapped=EvidenceBoundNaturalLocationProbe.Decision.Found(prediction.answer,s.sourceId,s.revision,s.sha256,prediction.start-offset,prediction.end-offset)
        break
       }
       offset+=s.text.length+2
      }
      if(mapped==null)reason="CROSS_SOURCE_SPAN"
      mapped
     }
    } else {
     val result=verified.answer(c.question,snapshot)
     confidence=result.confidence
     check(result.modelSha256==hash(checkpoint) && result.snapshotSha256==snapshot.sha256)
     reason=(result.decision as? EvidenceBoundNaturalLocationProbe.Decision.Abstained)?.reason ?: ""
     result.decision as? EvidenceBoundNaturalLocationProbe.Decision.Found
    }
    val independentlyVerified=found?.let { EvidenceBoundNaturalLocationVerifier.verify(c.question,sources,it) } ?: false
    val integrity=found==null || sources.any { s -> s.sourceId==found.sourceId && s.revision==found.revision && s.sha256==found.sha256 && found.start>=0 && found.endExclusive<=s.text.length && found.start<found.endExclusive && s.text.substring(found.start,found.endExclusive)==found.text }
    val correct=if(c.expected==null)found==null else found!=null && found.text==c.expected && found.sourceId==c.expectedSource && integrity && (mode=="TRAINED_RAW" || independentlyVerified)
    println(listOf(mode,c.id,c.part,c.kind,c.expected!=null,correct,found!=null,independentlyVerified,integrity,(System.nanoTime()-started)/1000,encoded(c.question),encoded(c.expected ?: ""),encoded(found?.text ?: ""),found?.sourceId ?: "",encoded(reason),confidence ?: "",snapshot.sha256,hash(checkpoint)).joinToString("\t"))
   } catch(e:Throwable) {
    println(listOf(mode,c.id,c.part,c.kind,c.expected!=null,false,false,false,false,(System.nanoTime()-started)/1000,encoded(c.question),encoded(c.expected ?: ""),"","",encoded(e.toString()),"",snapshot.sha256,hash(checkpoint)).joinToString("\t"))
   }
  }
 }
 check(rawBefore.contentEquals(raw.save())) {"Predictions mutated parameters"}
 check(hash(checkpoint)=="c28c44c21e6b79aea5d13254760a0a782714e662294b366826d9d8c940697e92")
 System.err.println("READ_ONLY_CHECKPOINT_PARAMETERS_UNCHANGED")
}
