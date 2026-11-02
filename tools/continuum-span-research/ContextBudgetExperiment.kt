package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.span.EvidenceBoundNaturalLocationProbe
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** RESEARCH_SHADOW: prompt information-loss audit, not a language-model benchmark. */
object ContextBudgetExperiment {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "FROZEN_CORPUS.json REPORT.json ARM" }
        require(!File(args[1]).exists()) { "Never overwrite an experiment result" }
        val corpus = JSONObject(File(args[0]).readText()).getJSONArray("cases")
        val rows = JSONArray()
        for (i in 0 until corpus.length()) {
            val c = corpus.getJSONObject(i)
            val q = c.getString("question"); val raw = c.getString("content")
            val expected = if (c.isNull("expected")) null else c.getString("expected")
            val input = ContextIntegrityFixture.input(q, listOf(ContextIntegrityFixture.memory("audit-memory", raw)))
            var failure: String? = null
            val compiled = try { AiContextCompiler(AiContextPolicy(maxBytes = c.getInt("maxBytes")), { 100L }).compile(input) }
                catch (e: IllegalStateException) { failure = e.javaClass.simpleName; null }
            // The old prompt has no typed source projection. Inspect exactly the literal memory line it sent.
            val projected = compiled?.text?.lineSequence()?.firstOrNull { it.startsWith("- [audit-memory] ") }
                ?.substringAfter(": ", "")?.takeIf(String::isNotBlank)
            fun answer(text: String?): String? {
                if (text == null) return null
                val source = EvidenceBoundNaturalLocationProbe.Evidence("compiler-view", 1,
                    EvidenceBoundNaturalLocationProbe.digest(text), text)
                return (EvidenceBoundNaturalLocationProbe.answer(q, listOf(source)) as?
                    EvidenceBoundNaturalLocationProbe.Decision.Found)?.text
            }
            val actual = answer(projected); val oracle = answer(raw)
            rows.put(JSONObject().put("id", c.getString("id")).put("seed", c.getInt("seed"))
                .put("kind", c.getString("kind")).put("expected", expected ?: JSONObject.NULL)
                .put("actual", actual ?: JSONObject.NULL).put("oracle", oracle ?: JSONObject.NULL)
                .put("correct", actual == expected).put("oracleCorrect", oracle == expected)
                .put("abstained", actual == null).put("answerable", expected != null)
                .put("declaredIncluded", compiled?.includedMemoryIds?.contains("audit-memory") == true)
                .put("fullLiteralRetained", projected == raw).put("compilerError", failure ?: JSONObject.NULL)
                .put("compiledBytes", compiled?.text?.toByteArray(Charsets.UTF_8)?.size ?: 0))
        }
        val values = (0 until rows.length()).map { rows.getJSONObject(it) }
        val report = JSONObject().put("schema", 1).put("status", "RESEARCH_SHADOW").put("arm", args[2])
            .put("corpusSha256", EvidenceBoundNaturalLocationProbe.digest(File(args[0]).readText()))
            .put("rows", rows).put("summary", JSONObject().put("total", values.size)
                .put("correct", values.count { it.getBoolean("correct") })
                .put("wrongAnswers", values.count { !it.isNull("actual") && !it.getBoolean("correct") })
                .put("answerableAbstentions", values.count { it.getBoolean("answerable") && it.getBoolean("abstained") })
                .put("correctNegativeAbstentions", values.count { !it.getBoolean("answerable") && it.getBoolean("abstained") })
                .put("oracleCorrect", values.count { it.getBoolean("oracleCorrect") })
                .put("emptyControlCorrect", values.count { !it.getBoolean("answerable") }))
        File(args[1]).writeText(report.toString(2) + "\n")
        println(args[2] + ": " + report.getJSONObject("summary"))
    }
}
