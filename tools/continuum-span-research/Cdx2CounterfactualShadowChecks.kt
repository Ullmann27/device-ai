package at.heinz.deviceai.learning.span

import java.io.File
import org.json.JSONObject

/** Independent controls, never a training input. Exits nonzero on provenance/leak/role regression. */
object Cdx2CounterfactualShadowChecks {
    @JvmStatic fun main(args: Array<String>) {
        val j=JSONObject(File(args[0]).readText()).getJSONArray("weights")
        val w=DoubleArray(j.length()){j.getDouble(it)}
        fun request(text:String,subject:String="Nora")=JSONObject().put("id","control").put("question","Wo ist $subject?")
            .put("sources",org.json.JSONArray().put(JSONObject().put("sourceId","control:1").put("revision",3)
                .put("sha256",EvidenceBoundNaturalLocationProbe.digest(text)).put("text",text)))
        for((text,value) in listOf("Nora ist in Perg. Emma ist in Berg." to "Perg",
                "Nora ist in Berg. Emma ist in Perg." to "Berg")) {
            val p=Cdx2CounterfactualShadow.predict(w,request(text));val raw=p.getJSONObject("RAW")
            check(raw.getString("text")==value)
            check(text.substring(raw.getInt("start"),raw.getInt("endExclusive"))==value)
        }
        val badHash=request("Nora ist in Perg.")
        badHash.getJSONArray("sources").getJSONObject(0).put("sha256","0".repeat(64))
        check(Cdx2CounterfactualShadow.predict(w,badHash).getJSONObject("RAW").getString("decision")=="NOANSWER")
        val leakage=request("Nora ist in Perg.").put("expected",JSONObject())
        check(runCatching{Cdx2CounterfactualShadow.predict(w,leakage)}.exceptionOrNull() is IllegalArgumentException)
        check(runCatching{Cdx2CounterfactualShadow.probabilities(DoubleArray(13){Double.NaN},emptyList())}.isFailure)
        val longPlace="Nora ist in Bad Sankt Veit."
        check(Cdx2CounterfactualShadow.predict(w,request(longPlace)).getJSONObject("RAW").getString("text")=="Bad Sankt Veit")
        println("6 independent shadow controls PASS (roles2, tampered hash, label isolation, nonfinite weights, full span)")
    }
}
