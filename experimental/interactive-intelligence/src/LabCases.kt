package at.heinz.deviceai.research.interactive

import at.heinz.deviceai.learning.context.Ctx3Entity
import java.io.File
import java.util.Base64

data class LabCase(val id:String,val kind:String,val entities:List<Ctx3Entity>,val history:List<Pair<String,String>>,val question:String,val expected:String,val required:String)
object LabCases {
 fun load(file:File):List<LabCase> = file.readLines().filter(String::isNotBlank).map { line ->
  val c=line.split('\t');require(c.size==7)
  fun decode(s:String)=String(Base64.getDecoder().decode(s),Charsets.UTF_8)
  val entities=decode(c[2]).split('|').map { item ->
   val bits=item.split(':');Ctx3Entity(bits[0],bits[0],setOf(bits[0]),bits[1].split(',').toSet(),setOf("fixture:"+bits[0]))
  }
  val history=decode(c[3]).split('\n').filter(String::isNotEmpty).map { val p=it.split('\t');require(p.size==2);p[0] to p[1] }
  LabCase(c[0],c[1],entities,history,decode(c[4]),c[5],decode(c[6]))
 }
}
