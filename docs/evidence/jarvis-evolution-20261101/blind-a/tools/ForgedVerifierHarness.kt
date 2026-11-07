package at.heinz.deviceai.learning.span

data class ForgeCase(val id:String,val category:String,val question:String,val texts:List<String>,val span:String,val expected:Boolean=false,val badId:Boolean=false,val badRevision:Boolean=false,val badOtherHash:Boolean=false,val duplicateOtherIds:Boolean=false,val offsetDelta:Int=0)
fun main() {
 val cases=listOf(
  ForgeCase("f01","positive-control","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich."),"Zürich",true),
  ForgeCase("f02","negation","Wo wohnt Lukas?",listOf("Es ist nicht wahr: Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f03","negated-later-assertion","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich. Lukas wohnt nicht in Zürich."),"Zürich"),
  ForgeCase("f04","conflict-same-source","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich. Lukas wohnt in Bremen."),"Zürich"),
  ForgeCase("f05","conflict-other-source","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich.","Lukas wohnt in Bremen."),"Zürich"),
  ForgeCase("f06","reported-speech","Wo wohnt Lukas?",listOf("Nadine sagt: Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f07","hypothesis","Wo wohnt Lukas?",listOf("Angenommen, Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f08","wrong-grammatical-holder","Wo wohnt Lukas?",listOf("Der Bruder von Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f09","hyphen-subentity","Wo wohnt Lukas?",listOf("Theo-Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f10","question-not-assertion","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich?"),"Zürich"),
  ForgeCase("f11","coordinated-places","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich und Bremen."),"Zürich und Bremen"),
  ForgeCase("f12","partial-span","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich."),"Zür"),
  ForgeCase("f13","relation-drift-standing","Wo wohnt Lukas?",listOf("Lukas steht in Zürich."),"Zürich"),
  ForgeCase("f14","presence-vs-residence-scope","Wo ist Lukas?",listOf("Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f15","invalid-source-id","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich."),"Zürich",badId=true),
  ForgeCase("f16","invalid-revision","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich."),"Zürich",badRevision=true),
  ForgeCase("f17","invalid-unselected-hash","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich.","Philipp wohnt in Nizza."),"Zürich",badOtherHash=true),
  ForgeCase("f18","duplicate-unselected-ids","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich.","Philipp wohnt in Nizza.","Gregor wohnt in Basel."),"Zürich",duplicateOtherIds=true),
  ForgeCase("f19","too-many-sources","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich.","Philipp wohnt in Nizza.","Gregor wohnt in Basel.","Torsten wohnt in London.","Judith wohnt in Oxford."),"Zürich"),
  ForgeCase("f20","oversize-question","Wo wohnt Lukas?"+" ".repeat(180),listOf("Lukas wohnt in Zürich."),"Zürich"),
  ForgeCase("f21","wrong-offset-control","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich."),"Zürich",offsetDelta=1),
  ForgeCase("f22","outside-scope-anaphoric-denial","Wo wohnt Lukas?",listOf("Lukas wohnt in Zürich. Das ist falsch."),"Zürich")
 )
 println("id\tcategory\texpectedVerify\tactualVerify\tcorrect\tproducerFound\tproducerText\treason")
 for(c in cases) {
  val sources=c.texts.mapIndexed { i,text ->
   EvidenceBoundNaturalLocationProbe.Evidence(if(c.badId && i==0)"../unsafe" else if(c.duplicateOtherIds && i>0)"duplicate-other" else "diagnostic-$i",if(c.badRevision && i==0)0 else 1,if(c.badOtherHash && i>0)"0".repeat(64) else EvidenceBoundNaturalLocationProbe.digest(text),text)
  }
  val source=sources[0]
  val start=source.text.indexOf(c.span)+c.offsetDelta
  val forged=EvidenceBoundNaturalLocationProbe.Decision.Found(c.span,source.sourceId,source.revision,source.sha256,start,start+c.span.length)
  val verify=runCatching { EvidenceBoundNaturalLocationVerifier.verify(c.question,sources,forged) }.getOrDefault(false)
  val produced=EvidenceBoundNaturalLocationProbe.answer(c.question,sources)
  val found=produced as? EvidenceBoundNaturalLocationProbe.Decision.Found
  val reason=(produced as? EvidenceBoundNaturalLocationProbe.Decision.Abstained)?.reason ?: ""
  println(listOf(c.id,c.category,c.expected,verify,verify==c.expected,found!=null,found?.text ?: "",reason).joinToString("\t"))
 }
}
