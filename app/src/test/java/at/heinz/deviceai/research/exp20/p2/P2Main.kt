package at.heinz.deviceai.research.exp20.p2

import at.heinz.deviceai.research.argon4.ComputeAllocationPolicy.Action
import java.io.File
import kotlin.system.exitProcess

/**
 * EXP-20 P2 driver (host laboratory).
 *   generate <dataDir>                                   write DEV (TRAIN/VALIDATION) and SEALED_HOLDOUT TSVs
 *   develop  <dataDir> <outDir>                          fit on TRAIN, tune on VALIDATION, write frozen-candidates.txt
 *   holdout  <dataDir> <outDir> <holdoutSha> <frozenSha> [--replay]   open the sealed holdout exactly once
 * `develop` never reads holdout.tsv. `holdout` refuses to run twice unless --replay, which must reproduce the result.
 */
object P2Main {
    private val readoutArms = listOf(ArmId.A0, ArmId.B, ArmId.C)

    @JvmStatic fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "generate" -> generate(File(args[1]))
            "develop" -> develop(File(args[1]), File(args[2]))
            "holdout" -> holdout(File(args[1]), File(args[2]), args[3], args[4], "--replay" in args)
            else -> { System.err.println("usage: generate|develop|holdout"); exitProcess(2) }
        }
    }

    private fun env() = "jvm=${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} os=${System.getProperty("os.name")} ${System.getProperty("os.arch")} cpus=${Runtime.getRuntime().availableProcessors()}"

    fun generate(dir: File) {
        val dev = P2Generator.generate(P2Generator.devBank(), P2Config.DEV_SEED,
            listOf(Split.TRAIN to P2Config.TRAIN_CLUSTERS_PER_SCENARIO, Split.VALIDATION to P2Config.VALIDATION_CLUSTERS_PER_SCENARIO))
        val hold = P2Generator.generate(P2HoldoutBank.bank, P2HoldoutBank.SEED, listOf(Split.SEALED_HOLDOUT to P2Config.HOLDOUT_CLUSTERS_PER_SCENARIO))
        val files = linkedMapOf(
            "train.tsv" to dev.filter { it.split == Split.TRAIN }, "validation.tsv" to dev.filter { it.split == Split.VALIDATION }, "holdout.tsv" to hold)
        for ((name, ds) in files) P2Tsv.write(ds, File(dir, name))
        File(dir, "split-assignment.tsv").writeText("cluster\tsplit\tbank\n" + (dev + hold).map { "${it.clusterId}\t${it.split}\t${it.clusterId.substringBefore('-')}" }.distinct().joinToString("\n") + "\n")
        for ((name, ds) in files) File(dir, "gold-" + name).writeText("cluster\tvariant\tscenario\tgold\tcause\tneg_control\n" +
            ds.joinToString("\n") { "${it.clusterId}\t${it.variant}\t${it.scenario}\t${it.decision.gold}\t${it.decision.cause}\t${if (it.decision.negControl) 1 else 0}" } + "\n")
        val summary = StringBuilder("EXP-20 P2 dataset summary (counts only)\n")
        for ((name, ds) in files) {
            summary.append("$name: clusters=${ds.map { it.clusterId }.distinct().size} variants=${ds.size} turns=${ds.sumOf { it.turns.size }}\n")
            for (s in Scenario.values()) { val x = ds.filter { it.scenario == s }
                summary.append("  $s: PROCEED=${x.count { it.decision.gold == Gold.PROCEED }} CLARIFY=${x.count { it.decision.gold == Gold.CLARIFY }} negControl=${x.count { it.decision.negControl }}\n") }
            summary.append("  causes: " + Cause.values().joinToString(" ") { c -> "$c=${ds.count { it.decision.cause == c }}" } + "\n")
        }
        File(dir, "dataset-summary.txt").writeText(summary.toString())
        for (f in dir.listFiles()!!.filter { it.isFile }.sortedBy { it.name }) println("${P2Tsv.sha256(f)}  ${f.name}")
    }

    private fun load(dir: File, name: String, split: Split) = P2Tsv.read(File(dir, name)).also { ds -> require(ds.all { it.split == split }) { "$name must contain only $split" } }

    private fun fitReadout(arm: ArmId, train: List<Dialog>, lambda: Double): LogisticReadout {
        val (zs, y) = P2Eval.trainingSet(arm, train)
        return LogisticReadout.fit(zs, y, lambda)
    }

    fun develop(dataDir: File, out: File) {
        out.mkdirs()
        val train = load(dataDir, "train.tsv", Split.TRAIN)
        val validation = load(dataDir, "validation.tsv", Split.VALIDATION)
        val grid = StringBuilder("arm\tlambda\tthreshold\tval_solve_rate\tval_turn_accuracy\treadout_sha256\n")
        val frozen = StringBuilder("# EXP-20 P2 frozen candidates; fitted on TRAIN only, selected on VALIDATION only\n")
        frozen.append("train.tsv\t${P2Tsv.sha256(File(dataDir, "train.tsv"))}\nvalidation.tsv\t${P2Tsv.sha256(File(dataDir, "validation.tsv"))}\n")
        val report = StringBuilder("EXP-20 P2 development report (TRAIN fit, VALIDATION selection) ${env()}\n")
        val chosen = LinkedHashMap<ArmId, FrozenArm>()
        for (arm in readoutArms) {
            val points = ArrayList<P2Eval.GridPoint>()
            for (lambda in P2Config.LAMBDAS) {
                val readout = fitReadout(arm, train, lambda)
                for (thr in P2Config.THRESHOLDS) {
                    val outs = validation.map { FrozenArm(arm, readout, thr).evaluate(it) }
                    val cs = P2Eval.clusters(outs)
                    val p = P2Eval.GridPoint(arm, lambda, thr, cs.count { it.solved }.toDouble() / cs.size, outs.count { it.correct }.toDouble() / outs.size, readout.sha256())
                    points += p
                    grid.append("$arm\t$lambda\t$thr\t${p.solveRate}\t${p.turnAccuracy}\t${p.readoutSha}\n")
                }
            }
            val best = P2Eval.select(points)
            chosen[arm] = FrozenArm(arm, fitReadout(arm, train, best.lambda), best.threshold)
            frozen.append("$arm\t${best.lambda}\t${best.threshold}\t${best.readoutSha}\n")
        }
        frozen.append("A\t-\t-\t-\nA_DSK\t-\t-\t-\n")
        chosen[ArmId.A] = FrozenArm(ArmId.A); chosen[ArmId.A_DSK] = FrozenArm(ArmId.A_DSK)
        for ((arm, fa) in chosen) {
            val outs = validation.map { fa.evaluate(it) }
            val cs = P2Eval.clusters(outs)
            report.append("$arm VALIDATION solved=${cs.count { it.solved }}/${cs.size} turns=${outs.count { it.correct }}/${outs.size} " +
                "noProposal=${outs.count { it.proposal == Proposal.NO_PROPOSAL }} unauthorized=${outs.count { it.unauthorized }} errors=" +
                ErrorType.values().joinToString(",") { e -> "$e:${outs.count { it.error == e }}" } + "\n")
        }
        File(out, "validation-grid.tsv").writeText(grid.toString())
        File(out, "frozen-candidates.txt").writeText(frozen.toString())
        File(out, "development-report.txt").writeText(report.toString())
        print(report)
        println("frozen-candidates.txt sha256=${P2Tsv.sha256(File(out, "frozen-candidates.txt"))}")
    }

    fun holdout(dataDir: File, out: File, holdoutSha: String, frozenSha: String, replay: Boolean) {
        val holdFile = File(dataDir, "holdout.tsv"); val frozenFile = File(out, "frozen-candidates.txt")
        check(P2Tsv.sha256(holdFile) == holdoutSha) { "holdout.tsv hash mismatch" }
        check(P2Tsv.sha256(frozenFile) == frozenSha) { "frozen-candidates.txt hash mismatch" }
        val receipt = File(out, "holdout-opened.receipt")
        if (receipt.exists() && !replay) { System.err.println("REFUSED: holdout already opened (${receipt.readText().trim()}); use --replay to reproduce"); exitProcess(3) }
        val train = load(dataDir, "train.tsv", Split.TRAIN)
        val frozenRows = frozenFile.readLines().filter { !it.startsWith("#") }.map { it.split('\t') }
        check(frozenRows.first { it[0] == "train.tsv" }[1] == P2Tsv.sha256(File(dataDir, "train.tsv"))) { "train.tsv changed since freeze" }
        val arms = LinkedHashMap<ArmId, FrozenArm>()
        arms[ArmId.A] = FrozenArm(ArmId.A); arms[ArmId.A_DSK] = FrozenArm(ArmId.A_DSK)
        for (arm in readoutArms) {
            val row = frozenRows.first { it[0] == arm.name }
            val readout = fitReadout(arm, train, row[1].toDouble())
            check(readout.sha256() == row[3]) { "$arm readout does not reproduce the frozen weights" }
            arms[arm] = FrozenArm(arm, readout, row[2].toDouble())
        }
        val hold = load(dataDir, "holdout.tsv", Split.SEALED_HOLDOUT)       // the one and only read of the sealed set
        val outcomes = arms.mapValues { (_, fa) -> hold.map { fa.evaluate(it) } }
        val dec = StringBuilder("arm\tcluster\tvariant\tscenario\tgold\tcause\tneg_control\tproposal\taction\tbase_action\tcorrect\terror\n")
        for ((arm, os) in outcomes) for (o in os) dec.append("$arm\t${o.dialog.clusterId}\t${o.dialog.variant}\t${o.dialog.scenario}\t${o.dialog.decision.gold}\t" +
            "${o.dialog.decision.cause}\t${if (o.dialog.decision.negControl) 1 else 0}\t${o.proposal}\t${o.action}\t${o.baseAction}\t${if (o.correct) 1 else 0}\t${o.error ?: "-"}\n")
        val decisionsName = if (replay) "holdout-decisions.replay.tsv" else "holdout-decisions.tsv"
        File(out, decisionsName).writeText(dec.toString())
        val summary = summarize(outcomes, hold, holdoutSha, frozenSha)
        File(out, if (replay) "holdout-summary.replay.txt" else "holdout-summary.txt").writeText(summary)
        print(summary)
        val resultSha = P2Tsv.sha256(File(out, decisionsName))
        println("holdout-decisions sha256=$resultSha")
        if (!replay) receipt.writeText("opened_once decisions_sha256=$resultSha holdout_sha256=$holdoutSha frozen_sha256=$frozenSha ${env()}\n")
        else check(receipt.readText().contains("decisions_sha256=$resultSha")) { "REPLAY DIFFERS from the first opening" }
    }

    private fun summarize(outcomes: Map<ArmId, List<Outcome>>, hold: List<Dialog>, holdoutSha: String, frozenSha: String): String {
        val sb = StringBuilder("EXP-20 P2 SEALED_HOLDOUT summary  holdout=$holdoutSha frozen=$frozenSha  ${env()}\n")
        val cs = outcomes.mapValues { P2Eval.clusters(it.value) }
        val n = cs.getValue(ArmId.A).size
        sb.append("clusters=$n variants=${hold.size} (one cluster = one independent dialog stem)\n\n")
        for ((arm, os) in outcomes) {
            val c = cs.getValue(arm)
            sb.append("$arm solved=${c.count { it.solved }}/$n (${"%.4f".format(c.count { it.solved }.toDouble() / n)}) turns=${os.count { it.correct }}/${os.size}" +
                " noProposal=${os.count { it.proposal == Proposal.NO_PROPOSAL }} unauthorized=${os.count { it.unauthorized }}" +
                " inheritedToolOnDrift=${os.count { it.action == Action.TOOL && it.dialog.decision.cause == Cause.PRESENCE_DRIFT }}\n")
            sb.append("   errors " + ErrorType.values().joinToString(" ") { e -> "$e=${os.count { it.error == e }}" } + "\n")
            sb.append("   per-scenario solved " + Scenario.values().joinToString(" ") { s -> "${s.name.substringBefore('_')}=${c.count { it.scenario == s && it.solved }}/${c.count { it.scenario == s }}" } + "\n")
        }
        fun compare(pairs: List<Pair<ArmId, ArmId>>, title: String): Map<Pair<ArmId, ArmId>, P2Stats.Comparison> {
            val raw = pairs.map { (x, y) ->
                val cx = cs.getValue(x); val cy = cs.getValue(y)
                check(cx.map { it.clusterId } == cy.map { it.clusterId })
                val b = cx.indices.count { cx[it].solved && !cy[it].solved }; val c = cx.indices.count { !cx[it].solved && cy[it].solved }
                Triple(x to y, b to c, P2Stats.mcnemarExact(b, c))
            }
            val adj = P2Stats.holm(raw.map { it.third }.toDoubleArray())
            sb.append("\n$title (Holm over ${pairs.size})\n")
            return raw.mapIndexed { i, (pair, bc, p) ->
                val cx = cs.getValue(pair.first); val cy = cs.getValue(pair.second)
                val solve = P2Stats.bootstrapDiff(DoubleArray(n) { if (cx[it].solved) 1.0 else 0.0 }, DoubleArray(n) { if (cy[it].solved) 1.0 else 0.0 })
                val acc = P2Stats.bootstrapDiff(DoubleArray(n) { cx[it].accuracy }, DoubleArray(n) { cy[it].accuracy })
                val cmp = P2Stats.Comparison(pair.first, pair.second, n, cx.count { it.solved }, cy.count { it.solved }, bc.first, bc.second, p, adj[i], solve, acc, P2Stats.verdict(adj[i], solve))
                sb.append("  ${pair.first} vs ${pair.second}: solved ${cmp.solvedX} vs ${cmp.solvedY}, discordant b=${cmp.b} c=${cmp.c}, McNemar p=${"%.3g".format(p)} Holm=${"%.3g".format(adj[i])}, " +
                    "diff=${"%+.4f".format(solve.estimate)} 95%CI[${"%+.4f".format(solve.lo95)},${"%+.4f".format(solve.hi95)}] 90%CI[${"%+.4f".format(solve.lo90)},${"%+.4f".format(solve.hi90)}], " +
                    "turn-acc diff=${"%+.4f".format(acc.estimate)} 95%CI[${"%+.4f".format(acc.lo95)},${"%+.4f".format(acc.hi95)}] -> ${cmp.verdict}\n")
                pair to cmp
            }.toMap()
        }
        val primary = compare(listOf(ArmId.C to ArmId.A, ArmId.C to ArmId.B, ArmId.B to ArmId.A), "PRIMARY")
        val secondary = compare(listOf(ArmId.B to ArmId.A0, ArmId.C to ArmId.A0, ArmId.A_DSK to ArmId.A, ArmId.B to ArmId.A_DSK, ArmId.C to ArmId.A_DSK), "SECONDARY")
        val v = { x: ArmId, y: ArmId -> (primary[x to y] ?: secondary.getValue(x to y)).verdict }
        val disq = setOf(ArmId.B, ArmId.C).filter { a -> outcomes.getValue(a).any { it.unauthorized } }
        val selected = when {
            ArmId.C !in disq && v(ArmId.C, ArmId.A) == P2Stats.Verdict.SUPERIOR && v(ArmId.C, ArmId.B) == P2Stats.Verdict.SUPERIOR -> ArmId.C
            ArmId.B !in disq && v(ArmId.B, ArmId.A) == P2Stats.Verdict.SUPERIOR -> ArmId.B
            else -> ArmId.A
        }
        sb.append("\nREGISTERED QUALITY RULE: disqualified=$disq selected=$selected (cost gate and independence gate are applied separately)\n")
        if (selected != ArmId.A) {
            sb.append("  attribution vs memoryless control A0: ${v(selected, ArmId.A0)}" + (if (v(selected, ArmId.A0) != P2Stats.Verdict.SUPERIOR) " -> GAIN_NOT_ATTRIBUTABLE_TO_TEMPORAL_STATE" else "") + "\n")
            sb.append("  vs existing DialogStateKernel A_DSK: ${v(selected, ArmId.A_DSK)}\n")
        }
        sb.append("  independence gate: SEALED_HOLDOUT is same-author -> at most RESEARCH_ONLY; no shadow hook from this result\n")
        return sb.toString()
    }
}
