package at.heinz.deviceai.learning.span

import java.io.File
import org.json.JSONObject

/** Authored TRAIN/DEV only. Blind fixtures are held by independent reviewer. */
object NaturalSpanExperiment {
    @JvmStatic fun main(args: Array<String>) {
        val names = listOf("Anna", "Ben", "Mira", "Lukas")
        val cities = listOf("Graz", "Linz", "Bad Ischl", "Wien")
        val train = mutableListOf<ContinuumResidualSpanCore.Example>()
        for (i in names.indices) for (j in cities.indices) {
            val a = names[i]; val b = names[(i+1)%4]; val x = cities[j]; val y = cities[(j+1)%4]
            val text = "$a ist in $x. $b ist in $y."
            train += ContinuumResidualSpanCore.Example(text, "Wo ist $a?", x)
            train += ContinuumResidualSpanCore.Example(text, "Wo ist $b?", y)
            train += ContinuumResidualSpanCore.Example(text, "Wo ist Nora?", null)
            train += ContinuumResidualSpanCore.Example("$a ist nicht in $x.", "Wo ist $a?", null)
        }
        val dev = listOf(
            ContinuumResidualSpanCore.Example("Eva ist in Bern. Otto ist in Kiel.", "Wo ist Eva?", "Bern"),
            ContinuumResidualSpanCore.Example("Eva ist in Bern. Otto ist in Kiel.", "Wo ist Otto?", "Kiel"),
            ContinuumResidualSpanCore.Example("Otto ist in Bern. Eva ist in Kiel.", "Wo ist Eva?", "Kiel"),
            ContinuumResidualSpanCore.Example("Eva ist nicht in Bern.", "Wo ist Eva?", null),
            ContinuumResidualSpanCore.Example("Eva ist in Bern.", "Wo ist Otto?", null))
        val out = File(args.single()).apply { mkdirs() }
        for (natural in listOf(false, true)) {
            val model = ContinuumResidualSpanCore(77, ContinuumResidualSpanCore.Features(
                positionFeatures=false, entityProximity=true, excludeCueWords=true, multiTokenSpans=true,
                naturalEntityBindings=natural))
            val before = train.map { model.loss(it) }.average()
            val start = System.nanoTime()
            repeat(3200) { model.train(train[it % train.size], rate=0.025) }
            val millis = (System.nanoTime()-start)/1_000_000.0
            val rows = dev.map { e -> JSONObject().put("evidence",e.evidence).put("question",e.question)
                .put("expected",e.answer ?: JSONObject.NULL).put("prediction",model.predict(e.evidence,e.question).answer ?: JSONObject.NULL) }
            val summary = JSONObject().put("naturalBinding",natural).put("steps",model.updates).put("trainingItems",train.size)
                .put("trainNllBefore",before).put("trainNllAfter",train.map { model.loss(it) }.average())
                .put("elapsedMs",millis).put("dev",rows).put("checkpointBytes",model.save().size)
            File(out, if(natural) "natural-model.bin" else "legacy-model.bin").writeBytes(model.save())
            File(out, if(natural) "natural-dev.json" else "legacy-dev.json").writeText(summary.toString(2))
            println(summary)
        }
    }
}
