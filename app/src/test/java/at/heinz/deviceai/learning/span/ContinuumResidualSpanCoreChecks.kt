package at.heinz.deviceai.learning.span

import kotlin.math.abs

/**
 * Executable JVM verification (standard Kotlin/JDK, no JUnit or Android runtime needed).
 * Run: kotlinc [core] [curriculum] [this file] -include-runtime -d csx1-checks.jar
 *      java -cp csx1-checks.jar at.heinz.deviceai.learning.span.ContinuumResidualSpanCoreChecks
 */
object ContinuumResidualSpanCoreChecks {
    @JvmStatic fun main(args: Array<String>) {
        val model = ContinuumResidualSpanCore(77)
        check(model.parameterCount == 4110)
        check(model.parameters().all { it == 0.0 })
        val example = ContinuumResidualSpanCore.Example(
            "Einheit R10 steht in Wien. Ihre Außenhülle ist rot.",
            "In welcher Stadt befindet sich R10?", "Wien"
        )
        check(model.predict(example.evidence, example.question).answer == null)
        val gradient = model.gradient(example)
        check(gradient.values.take(4096).count { abs(it) > 1e-8 } > 5)
        val params = model.parameters()
        val checked = ((0 until 4096).filter { abs(gradient.values[it]) > 1e-7 }.take(24) +
            (4096 until 4110).toList()).distinct()
        var maximumError = 0.0
        for (index in checked) {
            val plus = params.copyOf().also { it[index] += 1e-6 }
            val minus = params.copyOf().also { it[index] -= 1e-6 }
            model.loadParameters(plus)
            val high = model.loss(example)
            model.loadParameters(minus)
            val low = model.loss(example)
            val numerical = (high - low) / 2e-6
            val error = abs(numerical - gradient.values[index])
            maximumError = maxOf(maximumError, error)
            check(error < 1e-6)
        }
        model.loadParameters(params)
        check(model.updates == 0)
        println("PASS residual gradient checks=" + checked.size + " maximumError=" + maximumError)

        fun rejected(block: () -> Unit): Boolean = try {
            block()
            false
        } catch (_: IllegalArgumentException) {
            true
        }

        check(rejected {
            model.train(ContinuumResidualSpanCore.Example(example.evidence, example.question, "Mars"))
        })
        check(rejected {
            model.gradient(ContinuumResidualSpanCore.Example(
                "R11 steht in Graz. R12 steht in Graz.", "Wo steht R11?", "Graz"
            ))
        })
        check(model.updates == 0)
        println("PASS missing/ambiguous gold targets rejected without updates")

        val trio = ContinuumCounterfactualCurriculum.fromReviewedFact(
            ContinuumCounterfactualCurriculum.ReviewedFact(
                "fact-01", "Gerät R80 nutzt Akku ZX-101.", "Welchen Akku nutzt R80?",
                "ZX-101", "NV-994-OMEGA", "Gerät R80 steht in Wien."
            ), model
        )
        check(trio.swapped.answer == "NV-994-OMEGA")
        check(trio.reviewedNegative.answer == null)
        check(rejected {
            ContinuumCounterfactualCurriculum.fromReviewedFact(
                ContinuumCounterfactualCurriculum.ReviewedFact(
                    "bad", "R80 nutzt Akku ZX-101.", "Welchen Akku nutzt R80?",
                    "ZX-101", "ZX-202", "R80 nutzt Akku ZX-101."
                ), model
            )
        })
        println("PASS checked synthetic counterfactual and negative leakage guards")

        val bytes = model.save()
        check(bytes.size == ContinuumResidualSpanCore.SERIALIZED_BYTES)
        check(ContinuumResidualSpanCore.restore(bytes).parameters().contentEquals(model.parameters()))
        check(rejected { ContinuumResidualSpanCore.restore(bytes.copyOf(bytes.size - 1)) })
        check(rejected {
            ContinuumResidualSpanCore.restore(bytes.copyOf().also { it[25] = (it[25].toInt() xor 1).toByte() })
        })
        println("PASS checkpoint roundtrip, exact size and SHA-256 corruption gates")

        val train = listOf(
            ContinuumResidualSpanCore.Example("Einheit R10 steht in Wien.", "Wo steht R10?", "Wien"),
            ContinuumResidualSpanCore.Example("Einheit R10 steht in Wien.", "In welcher Stadt ist R10?", "Wien"),
            ContinuumResidualSpanCore.Example("Einheit R20 steht in Graz.", "Wo befindet sich R20?", "Graz"),
            ContinuumResidualSpanCore.Example("Einheit R20 steht in Graz.", "In welcher Stadt steht R20?", "Graz"),
            ContinuumResidualSpanCore.Example("Roboter R30 nutzt einen Greifer.", "Welches Werkzeug nutzt R30?", "Greifer"),
            ContinuumResidualSpanCore.Example("Roboter R40 verwendet einen Bohrer.", "Welches Werkzeug verwendet R40?", "Bohrer"),
            ContinuumResidualSpanCore.Example("Gerät R50 verwendet Modul AK-101.", "Welches Modul verwendet R50?", "AK-101"),
            ContinuumResidualSpanCore.Example("Gerät R60 verwendet Modul BK-202.", "Welches Modul verwendet R60?", "BK-202"),
            ContinuumResidualSpanCore.Example("Einheit R10 steht in Wien.", "Welche Energiequelle verwendet R10?", null),
            ContinuumResidualSpanCore.Example("Roboter R30 nutzt einen Greifer.", "In welcher Stadt steht R30?", null)
        )
        val checkCases = listOf(
            ContinuumResidualSpanCore.Example("Roboter X77 steht in Salzburg.", "Wo befindet sich X77?", "Salzburg"),
            ContinuumResidualSpanCore.Example("Gerät K99 verwendet Modul XZ-902.", "Welches Modul verwendet K99?", "XZ-902"),
            ContinuumResidualSpanCore.Example("Roboter T82 nutzt eine Zange.", "Welches Werkzeug nutzt T82?", "Zange"),
            ContinuumResidualSpanCore.Example("Roboter X77 steht in Salzburg.", "Welches Werkzeug nutzt X77?", null),
            ContinuumResidualSpanCore.Example("Gerät K99 verwendet Modul XZ-902.", "Wo steht K99?", null)
        )
        val cancelled = ContinuumResidualSpanCore(77)
        val cancellation = cancelled.trainSession(train, epochs = 10, cancelled = { true })
        check(cancellation.interrupted && cancellation.steps == 0 && cancelled.updates == 0)
        val failAtomic = ContinuumResidualSpanCore(77)
        check(rejected {
            failAtomic.trainSession(train + ContinuumResidualSpanCore.Example(
                "R99 steht in Wien.", "Wo steht R99?", "Rom"
            ))
        })
        check(failAtomic.updates == 0)
        println("PASS cancellation and preflight fail-before-first-update")

        val trained = ContinuumResidualSpanCore(77)
        fun score(m: ContinuumResidualSpanCore) =
            checkCases.count { m.predict(it.evidence, it.question).answer == it.answer }
        val before = score(trained)
        val session = trained.trainSession(train, epochs = 30, maxMillis = 15000)
        val after = score(trained)
        check(session.steps > 0 && session.lossBefore.isFinite() && session.lossAfter.isFinite())
        println("MEASURED synthetic DEV accuracy: " + before + "/5 -> " + after + "/5")
        println("MEASURED training: " + session.steps + " steps, loss " +
            session.lossBefore + " -> " + session.lossAfter + ", interrupted=" + session.interrupted)
        checkCases.forEach { sample ->
            val predicted = trained.predict(sample.evidence, sample.question).answer ?: "ABSTAIN"
            println("CASE expected=" + (sample.answer ?: "ABSTAIN") + ", predicted=" + predicted)
        }
        println("PASS executable JVM test finished; public dev cases are not a blind holdout.")
    }
}
