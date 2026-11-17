import at.heinz.deviceai.learning.*
import java.nio.file.Files

// Only synthetic data in a fresh disposable directory; never an owner application directory.
val dir = Files.createTempDirectory("continuum-auto-dialogue-probe-").toFile()
try {
    val core = ContinuumLearningCore(dir)
    val swbBefore = core.modelHash()
    val factsBefore = core.activeOwnerFacts()
    val started = System.nanoTime()
    val round = requireNotNull(ContinuumAutoDialogueLab(dir).next())
    check(core.modelHash() == swbBefore && core.activeOwnerFacts() == factsBefore)
    check(ContinuumAutoDialogueLab(dir).attemptedStages() == 1)
    check(round.steps in 0..32)
    if (round.accepted) check(!round.stoppedEarly &&
        round.trainAfter < round.trainBefore && round.auditAfter < round.auditBefore)
    println("SOURCE=074fa80729a088644769df300c23b2e093e56553; SYNTHETIC_HOST_PROBE_NOT_DEVICE")
    println(round)
    println("elapsedMillis=" + (System.nanoTime() - started) / 1000000)
    println("productiveSwbUnchanged=true; ownerFactsUnchanged=true; attemptedStages=1; promotion=false")
} finally { check(dir.deleteRecursively()) }
