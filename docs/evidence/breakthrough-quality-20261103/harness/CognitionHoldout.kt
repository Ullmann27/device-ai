package at.heinz.deviceai.cognitive
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
object CognitionHoldout {
 @JvmStatic fun main(args:Array<String>) {
  val cases=JSONObject(File(args[0]).readText()).getJSONArray("cases")
  val out=JSONArray()
  for(i in 0 until cases.length()) {
   val c=cases.getJSONObject(i); val previous=c.getJSONArray("previousOwnerTurns")
   val turns=(0 until previous.length()).map(previous::getString)
   val before=OwnerStatementTimeline.project(turns,"Bitte antworte knapp.")
   val actual=OwnerStatementTimeline.project(turns,c.getString("currentTurn"))
   val expected=c.getJSONArray("values"); val values=(0 until expected.length()).map { expected.getLong(it) }
   val setup=before?.entries?.map { it.current }==values
   val pass=setup && if(c.getString("expected")=="NO_STALE_PROJECTION") actual==null
    else actual?.entries?.map { it.current }==values && actual.entries.map { it.original }==values
   out.put(JSONObject().put("id",c.getString("id")).put("category",c.getString("category"))
    .put("setupValid",setup).put("pass",pass).put("render",actual?.render() ?: JSONObject.NULL))
  }
  File(args[1]).writeText(JSONObject().put("cases",out).toString(2)+"\n")
  println("cases=${out.length()} pass=${(0 until out.length()).count { out.getJSONObject(it).getBoolean("pass") }}")
 }
}
