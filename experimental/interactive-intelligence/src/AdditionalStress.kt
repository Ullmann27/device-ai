package at.heinz.deviceai.research.interactive
import at.heinz.deviceai.learning.interactive.InteractiveDialogueContext
import at.heinz.deviceai.learning.context.*
object AdditionalStress {
 @JvmStatic fun main(args:Array<String>){
  val es=listOf(Ctx3Entity("a","Aster",setOf("Aster"),setOf("es","dort"),setOf("s:a")),Ctx3Entity("b","Beryl",setOf("Beryl"),setOf("es","dort"),setOf("s:b")))
  val cases=listOf(
   Triple("trailing-negation","Aster meine ich nicht.","?"),
   Triple("two-part-correction","Nicht Aster, sondern Beryl.","b"),
   Triple("quoted-mention","Du hast Aster gesagt, aber ich meine Beryl.","b"),
   Triple("uncertain-owner","Vielleicht meine ich Beryl, ich bin nicht sicher.","?"),
   Triple("question-is-not-selection","Meinst du Beryl?","?"),
   Triple("no-longer","Aster ist nicht mehr unser Thema.","?"),
   Triple("explicit-switch","Sprich jetzt über Beryl.","b"),
   Triple("model-echo","Erkläre Aster.","a")
  )
  println("case\tarm\texpected\tselected\tcorrect\twrong\tabstain")
  for((id,middle,expected) in cases) {
   val c=InteractiveDialogueContext();c.record("s","Erkläre Aster.","Aster hat wenig Druck.",es,"v1");c.record("s",middle,"Unbestätigte Modellantwort.",es,"v1")
   val r=c.plan("s","Was folgt daraus?",es,"v1");val selected=if(r.clarification)"?" else r.ids.single()
   println("$id\tADAPTIVE\t$expected\t$selected\t${selected==expected}\t${selected!="?"&&selected!=expected}\t${selected=="?"}")
   val b=Ctx3Context();b.decide("Erkläre Aster.",es,"v1");b.decide(middle,es,"v1");val d=b.decide("Was folgt daraus?",es,"v1")
   val picked=if(d is Ctx3Decision.Query)es.filter{it.sourceIds.intersect(d.sourceIds).isNotEmpty()}.map{it.id}.singleOrNull()?:"?" else "?"
   println("$id\tCTX3\t$expected\t$picked\t${picked==expected}\t${picked!="?"&&picked!=expected}\t${picked=="?"}")
  }
 }
}
