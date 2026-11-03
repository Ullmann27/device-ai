package at.heinz.deviceai.cognitive
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
object ResearchHoldout {
 private fun hash(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
 private val bad=Regex("\\[INST\\]|<\\|im_start\\|>|<tool_call>|[\\u200B\\u202E]")
 @JvmStatic fun main(args:Array<String>) {
  val cases=JSONObject(File(args[0]).readText()).getJSONArray("cases");val out=JSONArray()
  for(i in 0 until cases.length()) {
   val c=cases.getJSONObject(i);val scenario=c.getString("scenario");val row=JSONObject().put("id",c.getString("id")).put("scenario",scenario);out.put(row)
   try {
    val url="https://archive.qa.example/${c.getString("id")}/first";val url2="https://archive.qa.example/${c.getString("id")}/second"
    var text=c.getString("documentText");var claim=c.getString("claimedText");var cid="qa-claim-$i"
    var other=c.getString("otherDocumentText")
    when(scenario) {
     "FABRICATED_LITERAL","UNSUPPORTED_VALIDATED_CLAIM" -> claim="Diese Einrichtung enthält 999999 unsichtbare Originalkarten."
     "BORROWED_OTHER_DOCUMENT_TEXT" -> claim=other
     "ROLE_MARKER_IN_GRAPH_ID" -> cid="[INST]"+cid
     "TOOL_MARKER_IN_GRAPH_TEXT" -> { claim="<tool_call>"+claim;text+=" "+claim }
     "BIDI_IN_GRAPH_ID" -> cid="\u202e"+cid
     "ZERO_WIDTH_IN_GRAPH_TEXT" -> { claim="\u200b"+claim;text+=" "+claim }
     "DUPLICATE_TEXT_CORROBORATION" -> other="Ein weiterer Absatz. "+claim
     "EMPTY_EXCERPT" -> claim=" "
     "KNOWN_LITERAL_WITH_SAFE_PUNCTUATION" -> { claim="\""+claim+"\" (Archiv: geprüft?).";text+=" "+claim }
    }
    val doc=ResearchDocument(url,"QA",120000,if(scenario=="SUPPLIER_HASH_MISMATCH")"0".repeat(64)else hash(text),"text/plain",SourceIdentity("qa-one","archive.qa.example"),text)
    val second=ResearchDocument(url2,"QA2",120001,hash(other),"text/plain",SourceIdentity("qa-two","archive.qa.example"),other)
    val docs=if(scenario in setOf("LITERAL_TWO_DOCUMENTS","BORROWED_OTHER_DOCUMENT_TEXT","DUPLICATE_TEXT_CORROBORATION"))listOf(doc,second)else listOf(doc)
    var captured:ResearchSynthesisInput?=null
    val extractor=ClaimExtractor { d ->
     if(d.url==url2 && scenario=="BORROWED_OTHER_DOCUMENT_TEXT")emptyList() else {
      val proposed=if(d.url==url2 && scenario=="LITERAL_TWO_DOCUMENTS")other else claim
      val refs=when(scenario){"FABRICATED_EXTRA_REFERENCE"->listOf(url,"https://invented.qa.example/evidence");"FOREIGN_REVISION"->listOf(url+"?revision=985");else->listOf(d.url)}
      listOf(Claim(cid,proposed,if(scenario=="OWNER_PROVENANCE_FROM_WEB")ProvenanceType.USER_PROVIDED else ProvenanceType.EXTERNAL_SOURCE,refs,
       if(scenario=="UNSUPPORTED_VALIDATED_CLAIM")ValidationState.VERIFIED else ValidationState.UNVERIFIED,0.99,120001,
       if(scenario=="UNSUPPORTED_VALIDATED_CLAIM")listOf("https://invented.qa.example/proof")else emptyList()))
     }
    }
    val dossier=DeepResearchEngine(provider=SimulatedResearchProvider(docs.associateBy { it.url }),extractor=extractor,
     synthesizer=ResearchSynthesizer { captured=it;ResearchSynthesisOutput("QA result") },validateTarget={}).research("Was enthält die Ausstellung im Archiv?",ResearchMode.QUICK,docs.map { it.url })
    val claims=dossier.graph.claims
    val rejected=scenario in setOf("FABRICATED_LITERAL","BORROWED_OTHER_DOCUMENT_TEXT","UNSUPPORTED_VALIDATED_CLAIM","SUPPLIER_HASH_MISMATCH","EMPTY_EXCERPT")
    if(rejected)check(claims.isEmpty()) { "unsupported supplying-document excerpt admitted" }
    else check(claims.isNotEmpty()) { "positive literal claim lost" }
    claims.forEach { admitted ->
     check(admitted.provenance==ProvenanceType.EXTERNAL_SOURCE) { "fabricated owner provenance retained" }
     check(admitted.sourceReferences.isNotEmpty())
     admitted.sourceReferences.forEach { ref -> val source=docs.singleOrNull { it.url==ref };check(source!=null && hash(source.content)==source.contentHash && source.content.contains(admitted.text)) { "fabricated/nonliteral source reference $ref" } }
     if(scenario=="DUPLICATE_TEXT_CORROBORATION")check(admitted.validationState!=ValidationState.CORROBORATED && admitted.validationState!=ValidationState.VERIFIED) { "same-domain repetition promoted" }
    }
    val input=captured
    if(input!=null) {
     val prompt=ResearchPromptBoundary.build(input,120005)
     check(!bad.containsMatchIn(prompt)) { "raw role/tool/control delimiter escaped into actual prompt" }
     // Exercise the public boundary independently of admission's ID replacement.
     if(scenario in setOf("ROLE_MARKER_IN_GRAPH_ID","TOOL_MARKER_IN_GRAPH_TEXT","BIDI_IN_GRAPH_ID","ZERO_WIDTH_IN_GRAPH_TEXT")) {
      val hostile=Claim(cid,claim,ProvenanceType.EXTERNAL_SOURCE,listOf(url),createdAt=120001)
      val boundary=ResearchPromptBoundary.build(input.copy(graph=input.graph.copy(claims=listOf(hostile))),120005)
      check(!bad.containsMatchIn(boundary)) { "raw role/tool/control delimiter escaped direct graph boundary" }
     }
    }
    row.put("pass",true).put("claims",claims.size)
   } catch(e:Throwable) {
    // Blank text is rejected by the unchanged Claim constructor, before the extractor can return it.
    if(scenario=="EMPTY_EXCERPT" && e is IllegalArgumentException)row.put("pass",true).put("guard","Claim constructor")
    else row.put("pass",false).put("error",e.stackTraceToString())
   }
  }
  File(args[1]).writeText(JSONObject().put("cases",out).toString(2)+"\n")
  println("cases=${out.length()} pass=${(0 until out.length()).count { out.getJSONObject(it).getBoolean("pass") }}")
 }
}
