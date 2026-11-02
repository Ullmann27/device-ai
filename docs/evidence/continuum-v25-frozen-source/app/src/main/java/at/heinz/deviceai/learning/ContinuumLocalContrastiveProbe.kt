package at.heinz.deviceai.learning

/**
 * Android-callable research integration, isolated from the production trainer.
 * Uses only public synthetic examples and a copy of the current SWB1 snapshot.
 * There is deliberately no save/promote method.
 */
data class ContinuumContrastiveProbeResult(
    val steps:Int,
    val completed:Boolean,
    val baselineHash:String,
    val candidateHash:String,
    val trainBefore:ContrastiveRetrieval,
    val trainAfter:ContrastiveRetrieval,
    val holdoutBefore:ContrastiveRetrieval,
    val holdoutAfter:ContrastiveRetrieval
) {
    fun display():String = buildString {
        append("Kontrastiver SWB1-Experimentlauf (öffentliche Testsätze)\n\n")
        append("Lernschritte: ").append(steps)
            .append(if(completed)" · abgeschlossen" else " · unterbrochen")
        append("\nTrainingsabruf MRR: ")
            .append("%.3f".format(java.util.Locale.ROOT,trainBefore.reciprocalRank))
            .append(" → ")
            .append("%.3f".format(java.util.Locale.ROOT,trainAfter.reciprocalRank))
        append("\nUnabhängiger Holdout MRR: ")
            .append("%.3f".format(java.util.Locale.ROOT,holdoutBefore.reciprocalRank))
            .append(" → ")
            .append("%.3f".format(java.util.Locale.ROOT,holdoutAfter.reciprocalRank))
        append("\nAktiver Modell-Hash: ").append(baselineHash.take(16))
        append("\nKandidaten-Hash: ").append(candidateHash.take(16))
        append("\n\nKein Modellimport, keine Gewichtsübernahme und kein Upload.")
        append("\nEin solcher Einzeltest genügt nicht zur Freigabe des automatischen Lernens.")
    }
}
object ContinuumLocalContrastiveProbe {
    private val TRAIN=listOf(
        "pflanze wandelt licht in energie um",
        "motor bewegt ein fahrzeug mit getriebe",
        "musikinstrumente erzeugen melodien und rhythmus",
        "regen entsteht aus wasser in wolken",
        "planeten kreisen um einen stern",
        "brot entsteht beim backen aus mehl"
    )
    private val HOLDOUT=listOf(
        "insekten bestauben blueten im fruehling",
        "ein arzt untersucht medizinische beschwerden",
        "winkel verbinden zwei geometrische geraden",
        "ein leser leiht buecher aus einer bibliothek"
    )
    fun run(snapshot:ByteArray,cancelled:()->Boolean={false},steps:Int=100):ContinuumContrastiveProbeResult {
        require(steps in 1..150)
        val baseline=TrainableSubwordEncoder.restore(snapshot)
        val original=baseline.save()
        val report=ContinuumContrastiveViews.trainCandidate(
            baseline,TRAIN,HOLDOUT,steps=steps,seed=43L,drop=0.65,rate=0.08,
            cancelled=cancelled
        )
        check(baseline.save().contentEquals(original)) { "The baseline model was changed" }
        return ContinuumContrastiveProbeResult(
            report.steps,!report.interrupted,
            ContinuumMemoryDialogue.hash(original),
            ContinuumMemoryDialogue.hash(report.candidate.save()),
            report.trainingBefore,report.trainingAfter,
            report.validationBefore,report.validationAfter
        )
    }
}
