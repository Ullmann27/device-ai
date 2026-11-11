package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

data class ReviewedWebObservation(val text:String,val url:String,val sourceHash:String)

data class ContinualLearningReport(
    val task:String,
    val accepted:Boolean,
    val steps:Int,
    val replayExamples:Int,
    val trainingBefore:SubwordEvaluation,
    val trainingAfter:SubwordEvaluation,
    val holdoutBefore:SubwordEvaluation,
    val holdoutAfter:SubwordEvaluation,
    val experienceIds:List<String>,
    val reason:String,
    /** False for cancellation/time-budget interruption; never mark source hashes completed. */
    val completedEvaluation:Boolean=true
)

internal class ContinuumSourceEvidenceNotPersisted(
    val modelCommitted:Boolean,
    cause:Exception
):java.io.IOException("Source evaluation evidence not persisted",cause)

data class PublicCurriculumReport(
    val accepted:Boolean,
    val completed:Boolean,
    val intentSteps:Int,
    val semanticSteps:Int,
    val reason:String,
    val intentTrainingBefore:SubwordEvaluation,
    val intentTrainingAfter:SubwordEvaluation,
    val semanticTrainingBefore:SubwordEvaluation,
    val semanticTrainingAfter:SubwordEvaluation,
    val intentAuditBefore:SubwordEvaluation,
    val intentAuditAfter:SubwordEvaluation,
    val semanticAuditBefore:SubwordEvaluation,
    val semanticAuditAfter:SubwordEvaluation,
    val fixedIntentBefore:SubwordEvaluation,
    val fixedIntentAfter:SubwordEvaluation,
    val fixedSemanticBefore:SubwordEvaluation,
    val fixedSemanticAfter:SubwordEvaluation
) {
    fun display():String = buildString {
        fun row(name:String,a:SubwordEvaluation,b:SubwordEvaluation) {
            append(name).append(": ").append(a.correct).append("/").append(a.total)
                .append(" → ").append(b.correct).append("/").append(b.total)
                .append(" · Verlust ")
                .append("%.4f".format(Locale.ROOT,a.loss)).append(" → ")
                .append("%.4f".format(Locale.ROOT,b.loss)).append("\n")
        }
        append("SWB1 · ÖFFENTLICHES OFFLINE-LERNPAKET\n")
        append("Trainingsschritte: ").append(intentSteps).append(" Intent + ")
            .append(semanticSteps).append(" Semantik\n")
        append("Status: ").append(if(!completed)"UNTERBROCHEN" else
            if(accepted)"KANDIDAT ÜBERNOMMEN" else "KANDIDAT VERWORFEN").append("\n\n")
        row("Intent Training",intentTrainingBefore,intentTrainingAfter)
        row("Semantik Training",semanticTrainingBefore,semanticTrainingAfter)
        row("NEUER Intent-Prüfsatz",intentAuditBefore,intentAuditAfter)
        row("NEUER Semantik-Prüfsatz",semanticAuditBefore,semanticAuditAfter)
        row("Bestehender Intent-Holdout",fixedIntentBefore,fixedIntentAfter)
        row("Bestehender Semantik-Holdout",fixedSemanticBefore,fixedSemanticAfter)
        append("\n").append(reason)
        append("\nKeine Besitzerfakten oder Geräteberechtigungen erzeugt. ")
        append("Das ist kein generatives Sprachmodell.")
    }
}

data class ExtendedCurriculumReport(
    val stage:Int,
    val stagesTotal:Int,
    val completed:Boolean,
    val accepted:Boolean,
    val intentUpdates:Int,
    val semanticUpdates:Int,
    val reason:String,
    val priorIntent:SubwordEvaluation,
    val nextIntent:SubwordEvaluation,
    val priorSemantic:SubwordEvaluation,
    val nextSemantic:SubwordEvaluation,
    val diagnostics:String=""
) {
    fun display():String = buildString {
        append("SWB1 · GROSSES OFFLINE-ÜBUNGSARCHIV\n")
        append("Lernstufe: ").append(stage).append("/").append(stagesTotal)
        append(" · ").append(if(!completed)"UNTERBROCHEN" else
            if(accepted)"GEPRÜFTE GEWICHTE ÜBERNOMMEN" else "GEWICHTE VERWORFEN")
        append("\nSchritte: ").append(intentUpdates).append(" Intent + ")
            .append(semanticUpdates).append(" Semantik\n")
        append("Neuer Intent-Test: ").append(priorIntent.correct).append("/")
            .append(priorIntent.total).append(" → ").append(nextIntent.correct)
            .append("/").append(nextIntent.total)
        append("\nNeuer Semantik-Test: ").append(priorSemantic.correct).append("/")
            .append(priorSemantic.total).append(" → ").append(nextSemantic.correct)
            .append("/").append(nextSemantic.total)
        append("\n").append(reason)
        if(diagnostics.isNotBlank())append("\n\nDetaillierte Prüfungen:\n").append(diagnostics)
        append("\nKeine automatischen Besitzerfakten. SWB1-Parameterzahl bleibt 4.113.")
    }
}

enum class OwnerFactChange { ADDED, REACTIVATED, ALREADY_ACTIVE, HELD_UNCONFIRMED }

data class ContinuumLearningStatus(
    val experiences:LearningExperienceStats,
    val modelUpdates:Int,
    val parameters:Int,
    val intentHoldout:SubwordEvaluation,
    val semanticHoldout:SubwordEvaluation,
    val lastAudit:String?
)

/**
 * Persistent local continual-learning coordinator.
 *
 * Every training round starts from the last committed encoder snapshot, replays
 * bounded prior experiences, measures frozen holdouts and commits candidate
 * weights only when regression gates pass. A rejected candidate is discarded.
 */
class ContinuumLearningCore private constructor(
    baseDirectory:File,
    /** M2.3: when set, every owner-fact mutation is also appended as a signed ProofCarryingMemory mutation to a
     * sidecar journal (the LEX2 ledger format is unchanged). Null = previous behaviour. */
    private val correctionSigner:at.heinz.deviceai.cognitive.IdentitySigner?,
    private val nowMillis:()->Long,
    automaticRecovery:Boolean
) {
    constructor(baseDirectory:File, correctionSigner:at.heinz.deviceai.cognitive.IdentitySigner?,
                nowMillis:()->Long):this(baseDirectory,correctionSigner,nowMillis,false)
    constructor(baseDirectory:File, nowMillis:()->Long=System::currentTimeMillis):this(baseDirectory,null,nowMillis,false)

    private val directory=File(baseDirectory,"learning-core-v2")
    private val store=FileLearningExperienceStore(directory,automaticRecovery)
    private val modelFile=File(directory,"subword-v1.bin")
    private val modelBackup=File(directory,"subword-v1.lkg")
    private val dialogue=ContinuumMemoryDialogue(directory)
    private val defaultContextConversation=java.util.UUID.randomUUID().toString()
    private val checkpoints=ModelCheckpointStore(File(directory,"checkpoints"))
    // Optional observer reports real gradient-entry and post-gradient audit phases.
    // Observing never changes the model and failures cannot abort its training.
    private var trainingObserver:((String)->Unit)?=null
    fun observeTraining(observer:((String)->Unit)?) { trainingObserver=observer }
    private fun emitTrainingPhase(phase:String) {
        runCatching { trainingObserver?.invoke(phase) }
    }

    /** Offline public curriculum: no owner interaction, no network and no private data.
     * Runs once per persisted model. Holdout gates reject regressions.
     */
    fun bootstrapOfflineCurriculum(cancelled:()->Boolean={false}):ContinualLearningReport? =
        synchronized(CORE_LOCK) {
            // Audit is the idempotency marker, including rejected candidates.
            // Imported weights or owner lessons must never be silently retrained.
            if (store.all().any {
                    it.type==LearningExperienceType.TRAINING_AUDIT &&
                    it.annotation=="offline-bootstrap"
                } || loadModel().updates > 0 ||
                store.all().any {
                    it.type==LearningExperienceType.INTENT_LABEL ||
                    it.type==LearningExperienceType.USER_FACT
                }) return@synchronized null
            val baseline=loadModel()
            val candidate=TrainableSubwordEncoder.restore(baseline.save())
            val before=candidate.evaluateIntent(INTENT_BOOTSTRAP)
            val holdoutBefore=candidate.evaluateIntent(INTENT_HOLDOUT)
            val otherBefore=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            emitTrainingPhase("TRAIN_WEIGHTS")
            val train=candidate.trainIntent(INTENT_BOOTSTRAP,steps=650,maxMillis=2500,cancelled=cancelled)
            emitTrainingPhase("AUDIT")
            val after=candidate.evaluateIntent(INTENT_BOOTSTRAP)
            val holdoutAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
            val otherAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            // The audit is this stage's run-once marker: an interrupted run (job
            // stopped, app closed) writes none, so the stage runs again later.
            if(interrupted(cancelled))return@synchronized interruptedReport("offline-bootstrap",train.steps,
                before,after,holdoutBefore,holdoutAfter)
            val accepted=!cancelled() && !train.stoppedEarly &&
                after.loss+1e-7<before.loss && retains(holdoutBefore,holdoutAfter) &&
                retains(otherBefore,otherAfter)
            if(accepted) saveModel(candidate.save(),"offline-bootstrap")
            val reason=if(accepted) "Öffentliches Offline-Curriculum verbessert; Holdouts geschützt"
                else "Offline-Kandidat verworfen; bisherige Gewichte erhalten"
            appendAudit("offline-bootstrap",accepted,train.steps,before,after,
                holdoutBefore,holdoutAfter,emptyList(),reason)
            ContinualLearningReport("offline-bootstrap",accepted,train.steps,0,before,after,
                holdoutBefore,holdoutAfter,emptyList(),reason)
        }

    /** Bounded autonomous public semantic curriculum, no network or owner labels.
     * Only attempts after the initial bootstrap and at most once per local model.
     */
    fun bootstrapSemanticCurriculum(cancelled:()->Boolean={false}):ContinualLearningReport? =
        synchronized(CORE_LOCK) {
            if (store.all().any {
                    it.type==LearningExperienceType.TRAINING_AUDIT &&
                    it.annotation=="offline-semantic-bootstrap"
                } || store.all().any {
                    it.type==LearningExperienceType.INTENT_LABEL ||
                    it.type==LearningExperienceType.USER_FACT
                }) return@synchronized null
            val baseline=loadModel()
            val candidate=TrainableSubwordEncoder.restore(baseline.save())
            val before=candidate.evaluateSemantic(SEMANTIC_BOOTSTRAP)
            val holdoutBefore=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val intentBefore=candidate.evaluateIntent(INTENT_HOLDOUT)
            emitTrainingPhase("TRAIN_WEIGHTS")
            val train=candidate.trainSemantic(SEMANTIC_BOOTSTRAP,steps=550,
                maxMillis=2500,cancelled=cancelled)
            emitTrainingPhase("AUDIT")
            val after=candidate.evaluateSemantic(SEMANTIC_BOOTSTRAP)
            val holdoutAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val intentAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
            if(interrupted(cancelled))return@synchronized interruptedReport("offline-semantic-bootstrap",train.steps,
                before,after,holdoutBefore,holdoutAfter)
            val accepted=!cancelled() && !train.stoppedEarly &&
                after.loss+1e-7<before.loss && retains(holdoutBefore,holdoutAfter) &&
                retains(intentBefore,intentAfter)
            if(accepted)saveModel(candidate.save(),"offline-semantic-bootstrap")
            val reason=if(accepted) "Öffentliches Semantiktraining verbessert; Holdouts geschützt"
                else "Semantikkandidat verworfen; bisherige Gewichte erhalten"
            appendAudit("offline-semantic-bootstrap",accepted,train.steps,before,after,
                holdoutBefore,holdoutAfter,emptyList(),reason)
            ContinualLearningReport("offline-semantic-bootstrap",accepted,train.steps,0,
                before,after,holdoutBefore,holdoutAfter,emptyList(),reason)
        }

    /**
     * Explicit owner-triggered public synthetic learning lab. No Internet,
     * no imported/guessed model weights, no owner-label fabrication.
     * Acceptance requires independent NEW and existing frozen holdouts,
     * and preserves all actual owner-confirmed intent examples.
     * Any interrupted run is not audited so that it can be retried.
     */
    fun trainPublicCurriculum(cancelled:()->Boolean={false}):PublicCurriculumReport? =
        synchronized(CORE_LOCK) {
            val lab=ContinuumPublicCurriculum
            lab.validate()
            val marker=lab.ID
            if(store.all().any { it.type==LearningExperienceType.TRAINING_AUDIT &&
                    it.annotation==marker }) return@synchronized null
            val current=loadModel()
            val candidate=TrainableSubwordEncoder.restore(current.save())
            val intentTraining=INTENT_BOOTSTRAP+lab.intentTrain
            val semanticTraining=SEMANTIC_BOOTSTRAP+lab.semanticTrain
            val own=ownerIntentReplay()
            val ownBefore=own.takeIf { it.isNotEmpty() }?.let(current::evaluateIntent)
            val trainIntentBefore=current.evaluateIntent(intentTraining)
            val trainSemanticBefore=current.evaluateSemantic(semanticTraining)
            val intentAuditBefore=current.evaluateIntent(lab.intentAudit)
            val semanticAuditBefore=current.evaluateSemantic(lab.semanticAudit)
            val fixedIntentBefore=current.evaluateIntent(INTENT_HOLDOUT)
            val fixedSemanticBefore=current.evaluateSemantic(SEMANTIC_HOLDOUT)
            val intentTrain=candidate.trainIntent(intentTraining,steps=1100,maxMillis=3500,
                cancelled=cancelled)
            val semanticTrain=if(!interrupted(cancelled) && !intentTrain.stoppedEarly)
                candidate.trainSemantic(semanticTraining,steps=900,maxMillis=3500,
                    cancelled=cancelled)
            else null
            val trainIntentAfter=candidate.evaluateIntent(intentTraining)
            val trainSemanticAfter=candidate.evaluateSemantic(semanticTraining)
            val intentAuditAfter=candidate.evaluateIntent(lab.intentAudit)
            val semanticAuditAfter=candidate.evaluateSemantic(lab.semanticAudit)
            val fixedIntentAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
            val fixedSemanticAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val completed=!interrupted(cancelled) && !intentTrain.stoppedEarly &&
                semanticTrain!=null && !semanticTrain.stoppedEarly
            val ownerRetained=ownBefore==null || retains(
                ownBefore,candidate.evaluateIntent(own))
            val trainingImproved=trainIntentAfter.loss+1e-7<trainIntentBefore.loss &&
                trainSemanticAfter.loss+1e-7<trainSemanticBefore.loss
            val auditImproved=intentAuditAfter.loss+semanticAuditAfter.loss+0.001<
                intentAuditBefore.loss+semanticAuditBefore.loss
            val accepted=completed && trainingImproved && auditImproved &&
                retains(intentAuditBefore,intentAuditAfter) &&
                retains(semanticAuditBefore,semanticAuditAfter) &&
                retains(fixedIntentBefore,fixedIntentAfter) &&
                retains(fixedSemanticBefore,fixedSemanticAfter) && ownerRetained
            val reason=when {
                !completed -> "Unterbrochen oder Zeitbudget erreicht; keinerlei Änderungen gespeichert."
                !ownerRetained -> "Private bestätigte Lektionen würden schlechter; Gewichte nicht geändert."
                !retains(fixedIntentBefore,fixedIntentAfter) ->
                    "Bisheriger Intent-Holdout würde schlechter; Gewichte nicht geändert."
                !retains(fixedSemanticBefore,fixedSemanticAfter) ->
                    "Bisheriger Semantik-Holdout würde schlechter; Gewichte nicht geändert."
                !retains(intentAuditBefore,intentAuditAfter) ||
                    !retains(semanticAuditBefore,semanticAuditAfter) ->
                    "Neue unabhängige Prüfsätze würden schlechter; Gewichte nicht geändert."
                !trainingImproved -> "Nicht beide Lernaufgaben verbessern sich; Gewichte nicht geändert."
                !auditImproved -> "Keine messbare Verbesserung neuer Prüfsätze; Gewichte nicht geändert."
                else -> "Öffentliche Lernaufgaben verbessern sich; alle Prüfbarrieren bestanden. " +
                    "Vorheriger Gewichtsstand ist als Checkpoint archiviert."
            }
            if(accepted) saveModel(candidate.save(),marker)
            if(completed) appendAudit(marker,accepted,intentTrain.steps+
                (semanticTrain?.steps ?: 0),trainIntentBefore,trainIntentAfter,
                fixedIntentBefore,fixedIntentAfter,emptyList(),
                reason.take(220)+
                    " · semantic-holdout="+fixedSemanticBefore.correct+"/"+fixedSemanticBefore.total+
                    "→"+fixedSemanticAfter.correct+"/"+fixedSemanticAfter.total)
            PublicCurriculumReport(accepted,completed,intentTrain.steps,
                semanticTrain?.steps ?: 0,reason,trainIntentBefore,trainIntentAfter,
                trainSemanticBefore,trainSemanticAfter,intentAuditBefore,intentAuditAfter,
                semanticAuditBefore,semanticAuditAfter,fixedIntentBefore,fixedIntentAfter,
                fixedSemanticBefore,fixedSemanticAfter)
        }

    /**
     * At most one 96+40-example research stage per invocation, against immutable
     * 64+8 extended holdouts, old 8+6 holdouts and confirmed owner examples.
     * A completed stage is permanently audited (accepted or rejected).
     */
    fun trainExtendedCurriculum(cancelled:()->Boolean={false}):ExtendedCurriculumReport? =
        synchronized(CORE_LOCK) {
            val curriculum=ContinuumExtendedCurriculum
            curriculum.validate()
            val done=store.all().filter {
                it.type==LearningExperienceType.TRAINING_AUDIT &&
                    it.annotation?.startsWith(curriculum.VERSION+"-stage-")==true
            }.mapNotNull { item ->
                item.annotation?.removePrefix(curriculum.VERSION+"-stage-")?.toIntOrNull()
            }.toSet()
            val stage=(0 until curriculum.STAGES).firstOrNull { (it+1) !in done }
                ?: return@synchronized null
            val marker=curriculum.VERSION+"-stage-"+(stage+1)
            val baseline=loadModel()
            val candidate=TrainableSubwordEncoder.restore(baseline.save())
            val intentTraining=(INTENT_BOOTSTRAP+curriculum.intentBatch(stage)).take(128)
            val semanticTraining=(SEMANTIC_BOOTSTRAP+curriculum.semanticBatch(stage)).take(128)
            val trainI0=baseline.evaluateIntent(intentTraining)
            val trainS0=baseline.evaluateSemantic(semanticTraining)
            val auditI0=baseline.evaluateIntent(curriculum.intentAudit)
            val auditS0=baseline.evaluateSemantic(curriculum.semanticAudit)
            val fixedI0=baseline.evaluateIntent(INTENT_HOLDOUT)
            val fixedS0=baseline.evaluateSemantic(SEMANTIC_HOLDOUT)
            val extraI0=baseline.evaluateIntent(ContinuumPublicCurriculum.intentAudit)
            val extraS0=baseline.evaluateSemantic(ContinuumPublicCurriculum.semanticAudit)
            val owner=ownerIntentReplay()
            val owner0=owner.takeIf { it.isNotEmpty() }?.let(baseline::evaluateIntent)
            emitTrainingPhase("TRAIN_WEIGHTS")
            val trainI=candidate.trainIntent(intentTraining,
                steps=750,maxMillis=3000,cancelled=cancelled)
            val trainS=if(!interrupted(cancelled) && !trainI.stoppedEarly)
                candidate.trainSemantic(semanticTraining,
                    steps=500,maxMillis=3000,cancelled=cancelled)
            else null
            emitTrainingPhase("AUDIT")
            val trainI1=candidate.evaluateIntent(intentTraining)
            val trainS1=candidate.evaluateSemantic(semanticTraining)
            val auditI1=candidate.evaluateIntent(curriculum.intentAudit)
            val auditS1=candidate.evaluateSemantic(curriculum.semanticAudit)
            val fixedI1=candidate.evaluateIntent(INTENT_HOLDOUT)
            val fixedS1=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val extraI1=candidate.evaluateIntent(ContinuumPublicCurriculum.intentAudit)
            val extraS1=candidate.evaluateSemantic(ContinuumPublicCurriculum.semanticAudit)
            val completed=!interrupted(cancelled) && !trainI.stoppedEarly &&
                trainS!=null && !trainS.stoppedEarly
            val ownerSafe=owner0==null || retains(owner0,candidate.evaluateIntent(owner))
            val allProtected=retains(auditI0,auditI1) && retains(auditS0,auditS1) &&
                retains(fixedI0,fixedI1) && retains(fixedS0,fixedS1) &&
                retains(extraI0,extraI1) && retains(extraS0,extraS1) && ownerSafe
            val better=trainI1.loss+0.000001<trainI0.loss &&
                trainS1.loss+0.000001<trainS0.loss &&
                auditI1.loss+auditS1.loss+0.0005<auditI0.loss+auditS0.loss
            val checks=listOf(
                "Neuer Intent-Holdout" to retains(auditI0,auditI1),
                "Neuer Semantik-Holdout" to retains(auditS0,auditS1),
                "Ursprünglicher Intent-Holdout" to retains(fixedI0,fixedI1),
                "Ursprünglicher Semantik-Holdout" to retains(fixedS0,fixedS1),
                "Öffentliches Paket: Intent" to retains(extraI0,extraI1),
                "Öffentliches Paket: Semantik" to retains(extraS0,extraS1),
                "Bestätigte Besitzerbeispiele" to ownerSafe
            )
            val blockers=checks.filterNot { it.second }.map { it.first }
            val accepted=completed && allProtected && better
            val reason=when {
                !completed -> "Trainingslauf unterbrochen oder Zeitbudget erreicht; wiederholbar."
                !allProtected -> "Schutzprüfung nicht bestanden: "+blockers.joinToString(", ")+
                    ". Kandidatengewichte verworfen."
                !better -> "Kein unabhängiger Qualitätsgewinn; vorige Gewichte erhalten."
                else -> "Neue Gewichtswerte akzeptiert; der frühere Stand ist als Checkpoint archiviert."
            }
            fun checkLine(name:String,before:SubwordEvaluation,after:SubwordEvaluation):String =
                name+": "+before.correct+"/"+before.total+" → "+
                    after.correct+"/"+after.total+
                    " · Verlust "+"%.4f".format(Locale.ROOT,before.loss)+
                    " → "+"%.4f".format(Locale.ROOT,after.loss)+
                    " · "+(if(retains(before,after))"OK" else "REGRESSION")
            val diagnostics=listOf(
                checkLine("Neuer Intent",auditI0,auditI1),
                checkLine("Neue Semantik",auditS0,auditS1),
                checkLine("Alter Intent",fixedI0,fixedI1),
                checkLine("Alte Semantik",fixedS0,fixedS1),
                checkLine("Vorheriges Paket Intent",extraI0,extraI1),
                checkLine("Vorheriges Paket Semantik",extraS0,extraS1),
                "Besitzer-Lektionen: "+(if(ownerSafe)"GESCHÜTZT" else "RÜCKSCHRITT"),
                "Trainingsverlust Intent: "+
                    "%.4f".format(Locale.ROOT,trainI0.loss)+" → "+
                    "%.4f".format(Locale.ROOT,trainI1.loss),
                "Trainingsverlust Semantik: "+
                    "%.4f".format(Locale.ROOT,trainS0.loss)+" → "+
                    "%.4f".format(Locale.ROOT,trainS1.loss)
            ).joinToString("\n")
            if(accepted)saveModel(candidate.save(),marker)
            if(completed)appendAudit(marker,accepted,
                trainI.steps+(trainS?.steps ?: 0),
                trainI0,trainI1,auditI0,auditI1,emptyList(),reason)
            ExtendedCurriculumReport(stage+1,curriculum.STAGES,completed,accepted,
                trainI.steps,trainS?.steps ?: 0,reason,auditI0,auditI1,auditS0,auditS1,
                diagnostics)
        }

    fun teachIntent(
        text:String,
        label:Int,
        cancelled:()->Boolean={false}
    ):ContinualLearningReport = synchronized(CORE_LOCK) {
        require(label in 0..1 && text.trim().length in 3..320)
        // Reject empty encoder input before it can poison every later replay.
        TrainableSubwordEncoder.subwordBuckets(text.trim())
        val experience=LearningExperienceFactory.create(
            type=LearningExperienceType.INTENT_LABEL,
            provenance=LearningProvenance.USER_CONFIRMED,
            trust=LearningTrustClass.OWNER_CONFIRMED,
            status=LearningStatus.CONFIRMED,
            content=text.trim(),
            annotation=label.toString(),
            now=nowMillis()
        )
        store.confirmIntentLabel(experience)

        val baseline=loadModel()
        val candidate=TrainableSubwordEncoder.restore(baseline.save())
        val replay=store.replay(LearningExperienceType.INTENT_LABEL,64)
            .mapNotNull { item ->
                val itemLabel=item.annotation?.toIntOrNull()
                if(itemLabel in 0..1)SubwordIntentExample(item.content,itemLabel!!) else null
            }
            .distinctBy { it.text.lowercase(Locale.ROOT) to it.label }
        val training=(INTENT_BOOTSTRAP+replay).takeLast(96)
        val before=candidate.evaluateIntent(training)
        val holdoutBefore=candidate.evaluateIntent(INTENT_HOLDOUT)
        val otherBefore=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val train=candidate.trainIntent(
            training,steps=650,maxMillis=2500,cancelled=cancelled
        )
        val after=candidate.evaluateIntent(training)
        val holdoutAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
        val otherAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val otherRetained=retains(otherBefore,otherAfter)
        val newScore=candidate.intentScore(experience.content)
        val learnedNew=if(label==1)newScore>=0.55 else newScore<=0.45
        val accepted=!cancelled() && !train.stoppedEarly && learnedNew && otherRetained &&
            after.loss+1e-7<before.loss &&
            holdoutAfter.correct>=holdoutBefore.correct &&
            holdoutAfter.loss<=holdoutBefore.loss+0.035
        if(accepted) {
            saveModel(candidate.save(),"intent")
            store.updateStatus(experience.id,LearningStatus.LEARNED,nowMillis())
        } else {
            store.updateStatus(experience.id,LearningStatus.REPLAY_PENDING,nowMillis())
        }
        val reason=when {
            cancelled() || train.stoppedEarly -> "Abgebrochen; Snapshot unverändert"
            !learnedNew -> "Neue bestätigte Lektion noch nicht ausreichend gelernt"
            !otherRetained -> "Gemeinsame Gewichte würden semantisches Wissen verschlechtern"
            holdoutAfter.correct<holdoutBefore.correct -> "Intent-Holdout würde Treffer verlieren"
            holdoutAfter.loss>holdoutBefore.loss+0.035 -> "Intent-Holdout-Verlust überschreitet Regressionsgrenze"
            after.loss>=before.loss-1e-7 -> "Replay-Trainingsverlust nicht verbessert"
            else -> "Replay verbessert und fester Intent-Holdout nicht verschlechtert"
        }
        appendAudit("intent",accepted,train.steps,before,after,holdoutBefore,holdoutAfter,
            listOf(experience.id),reason)
        ContinualLearningReport(
            "intent",accepted,train.steps,replay.size,before,after,
            holdoutBefore,holdoutAfter,listOf(experience.id),reason,
            completedEvaluation=!cancelled() && !train.stoppedEarly
        )
    }

    fun learnFromSources(
        sources:List<MentorSource>,
        cancelled:()->Boolean={false}
    ):ContinualLearningReport? = synchronized(CORE_LOCK) {
        if(sources.isEmpty())return@synchronized null
        val batch=java.util.Collections.unmodifiableList(ArrayList(sources))
        val requested=sourceExperiences(batch,nowMillis()) ?: return@synchronized null
        ContinuumSourceNovelty.markStarted(directory.parentFile!!,batch)
        store.appendMissing(requested)
        val ids=requested.map { it.id }
        val requestedIds=ids.toSet()
        // Capacity refusal or revocation must not turn into a claimed source
        // training round backed only by the unrelated public curriculum.
        val active=store.all().filter {
            it.status!=LearningStatus.REVOKED && it.status!=LearningStatus.REJECTED
        }.associateBy { it.id }
        // Require the complete persisted batch.
        if(ids.any { it !in active })return@synchronized null

        val replay=(store.replay(LearningExperienceType.SOURCE_TEXT,48).filter {
            it.id !in requestedIds && it.annotation.orEmpty().length>=3
        }+ids.map(active::getValue)).takeLast(48)
        val positives=replay.mapNotNull { item ->
            val topic=item.annotation?.takeIf { it.length>=3 } ?: return@mapNotNull null
            SubwordSemanticExample(topic,item.content,true)
        }
        val sourcePairs=positives.takeLast(48)
        val training=(SEMANTIC_BOOTSTRAP+sourcePairs).takeLast(96)
        val baseline=loadModel()
        val candidate=TrainableSubwordEncoder.restore(baseline.save())
        val before=candidate.evaluateSemantic(training)
        val holdoutBefore=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val protectedIntent=(INTENT_HOLDOUT+ownerIntentReplay()).take(128)
        val otherBefore=candidate.evaluateIntent(protectedIntent)
        emitTrainingPhase("TRAIN_WEIGHTS")
        val train=candidate.trainSemantic(
            training,steps=550,maxMillis=2500,cancelled=cancelled
        )
        emitTrainingPhase("AUDIT")
        val after=candidate.evaluateSemantic(training)
        val holdoutAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val otherAfter=candidate.evaluateIntent(protectedIntent)
        val otherRetained=retains(otherBefore,otherAfter)
        val accepted=!cancelled() && !train.stoppedEarly && otherRetained &&
            after.loss+1e-7<before.loss &&
            holdoutAfter.correct>=holdoutBefore.correct &&
            holdoutAfter.loss<=holdoutBefore.loss+0.045
        if(accepted)saveModel(candidate.save(),"semantic")
        emitTrainingPhase("SAVE_EVIDENCE")
        try {
        ids.forEach { id ->
            store.updateStatus(id,
                if(accepted)LearningStatus.LEARNED else LearningStatus.REPLAY_PENDING,
                nowMillis())
        }
        val reason=when {
            accepted && cancelled() -> "Modell gespeichert; Abschluss unterbrochen"
            cancelled() || train.stoppedEarly -> "Abgebrochen; Snapshot unverändert"
            !otherRetained -> "Gemeinsame Gewichte würden Intent oder persönliche Lektionen verschlechtern"
            holdoutAfter.correct<holdoutBefore.correct -> "Semantik-Holdout würde Treffer verlieren"
            holdoutAfter.loss>holdoutBefore.loss+0.045 -> "Semantik-Holdout-Verlust überschreitet Regressionsgrenze"
            after.loss>=before.loss-1e-7 -> "Quellen-Replay-Verlust nicht verbessert"
            else -> "Quellenkontexte verbessert; semantischer Holdout nicht verschlechtert"
        }
        appendAudit("semantic",accepted,train.steps,before,after,holdoutBefore,holdoutAfter,ids,reason)
        val report=ContinualLearningReport(
            "semantic",accepted,train.steps,sourcePairs.size,before,after,
            holdoutBefore,holdoutAfter,ids,reason,
            completedEvaluation=!cancelled() && !train.stoppedEarly
        )
        if(report.completedEvaluation &&
            !ContinuumSourceNovelty.markCompleted(directory.parentFile!!,batch,cancelled))
            report.copy(completedEvaluation=false,reason=if(accepted)
                "Modell gespeichert; Abschluss unterbrochen" else
                "Abschluss unterbrochen; Snapshot unverändert") else report
        } catch(error:Exception) {
            if(error is java.util.concurrent.CancellationException)throw error
            throw ContinuumSourceEvidenceNotPersisted(accepted,error)
        }
    }

    /** Already owner-reviewed notes join retrieval, never become truth labels. */
    private fun reviewedExperience(note:ReviewedWebObservation):LearningExperience {
        require(note.text.length in 20..400 && note.url.length in 10..400 && note.sourceHash.matches(Regex("[a-f0-9]{64}")))
        val uri=java.net.URI(note.url)
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null)
        return LearningExperienceFactory.create(
            LearningExperienceType.SOURCE_TEXT,LearningProvenance.REVIEWED_WEB_SOURCE,
            LearningTrustClass.SOURCE_OBSERVATION,LearningStatus.OBSERVED,note.text,
            sourceRef=note.url+"#sha256="+note.sourceHash,now=nowMillis()
        )
    }

    fun recordReviewedWebObservation(content:String,url:String,sourceHash:String):Boolean=synchronized(CORE_LOCK) {
        store.append(reviewedExperience(ReviewedWebObservation(content,url,sourceHash)))
    }

    /** Mirror the current reviewed notebook so removed/disputed notes cannot survive
     * as a second hidden source of answers. Validate before mutating the ledger.
     */
    fun synchronizeReviewedWebObservations(notes:List<ReviewedWebObservation>)=synchronized(CORE_LOCK) {
        require(notes.size<=24)
        val live=notes.map(::reviewedExperience)
        val ids=live.map { it.id }.toSet()
        val old=store.all().filter { it.provenance==LearningProvenance.REVIEWED_WEB_SOURCE }
        if(old.any { it.id !in ids && it.status!=LearningStatus.REVOKED })dialogue.clear()
        old.filter { it.id !in ids }.forEach { store.updateStatus(it.id,LearningStatus.REVOKED,nowMillis()) }
        live.forEach { store.append(it);store.updateStatus(it.id,LearningStatus.OBSERVED,nowMillis()) }
    }

    /**
     * M2 hypothetical gate: only an assertion (or a negation, kept as a remove-only barrier) becomes a
     * CONFIRMED owner fact. A conditional, hedged, planned, questioned or reported statement is kept visibly
     * as an OBSERVED owner statement with an `unbestaetigt:<MODALITY>` annotation, which no answer path reads
     * as a fact (all of them filter on CONFIRMED/LEARNED/REPLAY_PENDING). Same enum names, same file format.
     */
    private fun ownerFact(content:String):LearningExperience {
        val held=OwnerStatementModality.heldAnnotation(content)
        return LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED,
            if(held==null)LearningStatus.CONFIRMED else LearningStatus.OBSERVED,
            content.trim(),
            annotation=held,
            now=nowMillis()
        )
    }

    private fun confirmable(e:LearningExperience)=
        e.annotation?.startsWith(OwnerStatementModality.HELD_PREFIX)!=true &&
            OwnerStatementModality.mayBecomeConfirmedFact(OwnerStatementModality.classify(e.content))

    /** Legacy ledgers (before the gate) may hold a CONFIRMED conditional sentence: demote it to OBSERVED. */
    private fun quarantineUnconfirmableOwnerFacts():Int {
        val legacy=store.all().filter { it.type==LearningExperienceType.USER_FACT &&
            it.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING) &&
            !confirmable(it) }
        if(legacy.isNotEmpty())dialogue.clear()
        legacy.forEach { store.updateStatus(it.id,LearningStatus.OBSERVED,nowMillis()) }
        return legacy.size
    }

    /** Owner statements that are stored but deliberately NOT confirmed facts (hypothetical, uncertain, ...). */
    /** Read-only: every owner statement (confirmed, held, revoked) with its confirmability, oldest first. */
    internal fun ownerStatementHistory():List<Pair<LearningExperience,Boolean>> = synchronized(CORE_LOCK) {
        store.all().filter { it.type==LearningExperienceType.USER_FACT && it.trust==LearningTrustClass.OWNER_CONFIRMED }
            .sortedWith(compareBy<LearningExperience> { it.createdAt }.thenBy { it.id })
            .map { it to (it.status!=LearningStatus.OBSERVED && confirmable(it)) }
    }

    fun unconfirmedOwnerStatements():List<LearningExperience> = synchronized(CORE_LOCK) {
        store.all().filter { it.type==LearningExperienceType.USER_FACT && it.status==LearningStatus.OBSERVED &&
            it.trust==LearningTrustClass.OWNER_CONFIRMED }
    }

    /**
     * Read-only view for the temporal memory specialist: every owner statement of the canonical ledger
     * including revocation tombstones and held statements, so validity and knowledge time stay reconstructable.
     * No copy, no second store; [ContinuumTemporalMemory] decides what may answer.
     */
    fun temporalMemoryView(working:List<WorkingTurn> = emptyList(),
                           zone:java.time.ZoneId = ContinuumTemporalMemory.VIENNA):ContinuumTemporalMemory =
        synchronized(CORE_LOCK) {
            // M2.1: the ledger-only view is cached per directory+zone, keyed by the ledger's SHA-256 content
            // fingerprint read under the store's file lock on EVERY call. A changed ledger (incl. a revocation
            // between answer and re-check) always rebuilds; working memory is merged per call, never cached.
            val key=directory.canonicalPath+"|"+zone.id
            val cached=synchronized(VIEW_CACHE) { VIEW_CACHE[key] }
            val (fingerprint,records)=store.snapshotIfChanged(cached?.first)
            val ledgerView=if(records==null && cached!=null) {
                viewCacheHits.incrementAndGet(); cached.second
            } else {
                viewCacheMisses.incrementAndGet()
                ContinuumTemporalMemory.fromLedger((records ?: store.all()).filter { it.type==LearningExperienceType.USER_FACT &&
                    it.trust==LearningTrustClass.OWNER_CONFIRMED && it.provenance==LearningProvenance.USER_CONFIRMED },
                    emptyList(),zone).also { v -> synchronized(VIEW_CACHE) { VIEW_CACHE[key]=fingerprint to v } }
            }
            ledgerView.withWorking(working)
        }

    /** Read-only compact projection of the canonical temporal ledger for already-selected source ids. */
    internal fun stateTelegramForSources(
        sourceIds: Set<String>,
        nowMillis: Long = nowMillis()
    ): at.heinz.deviceai.learning.context.Ctx3StateTelegram.Result = synchronized(CORE_LOCK) {
        require(sourceIds.size <= 64 && sourceIds.none(String::isBlank))
        at.heinz.deviceai.learning.context.Ctx3StateTelegram.compile(
            temporalMemoryView(), sourceIds, nowMillis
        )
    }

    /** Adds a missing owner fact; a fact the owner revoked stays revoked (see [confirmOwnerFact]). */
    fun recordOwnerFact(content:String):Boolean=synchronized(CORE_LOCK) {
        store.append(ownerFact(content))
    }

    /**
     * Mirrors the Android owner memory into the ledger with one read and at most
     * one write. Like [recordOwnerFact] it never re-activates a revoked fact, so a
     * revocation is not undone by facts that are still listed elsewhere. After
     * recovery, missing historical facts are not imported as new confirmations.
     */
    fun syncOwnerFacts(facts:List<String>):Int=synchronized(CORE_LOCK) { signedOwnerMutation {
        quarantineUnconfirmableOwnerFacts()
        store.appendHistoricalOwnerFacts(facts.map(::ownerFact))
    } }

    /**
     * An explicit owner statement ("Merke dir: ..."): adds the fact, or confirms
     * again a fact the owner revoked earlier (the id is the content hash, so the
     * revoked record would otherwise stay revoked forever).
     */
    fun confirmOwnerFact(content:String):OwnerFactChange=synchronized(CORE_LOCK) { signedOwnerMutation { confirmOwnerFactUnsigned(content) } }

    private fun confirmOwnerFactUnsigned(content:String):OwnerFactChange {
        val item=ownerFact(content)
        if(item.status==LearningStatus.OBSERVED) {
            store.append(item)
            return OwnerFactChange.HELD_UNCONFIRMED
        }
        if(store.append(item))return OwnerFactChange.ADDED
        val existing=store.all().first { it.id==item.id }
        if(existing.id in store.pendingOwnerReconfirmation())return OwnerFactChange.HELD_UNCONFIRMED
        if(existing.status!=LearningStatus.REVOKED && existing.status!=LearningStatus.REJECTED)
            return OwnerFactChange.ALREADY_ACTIVE
        check(store.updateStatus(item.id,LearningStatus.CONFIRMED,nowMillis())) { "Owner fact vanished" }
        return OwnerFactChange.REACTIVATED
    }

    /** Display these concrete statements before collecting an owner decision. No model/tool can supply a yes. */
    fun recoveredOwnerFactsForReview():OwnerRecoveryReview = synchronized(CORE_LOCK) { store.ownerRecoveryReview() }

    /** The proof binds an explicit displayed subset and the current ledger/consent revision. No bulk default. */
    fun reconfirmRecoveredOwnerFacts(review:OwnerRecoveryReview,proof:OwnerReconfirmationProof?):Set<String> =
        synchronized(CORE_LOCK) { signedOwnerMutation {
            val ids=store.reconfirmRecoveredOwnerFacts(review,proof)
            if(ids.isNotEmpty())dialogue.clear()
            ids
        } }

    private val correctionJournal=correctionSigner?.let { SignedOwnerCorrectionJournal(directory,it) }

    /** Runs an owner-fact mutation; with a signer, the resulting ledger diff is signed into the sidecar journal
     * after the ledger write succeeded. A failed journal write leaves an auditable UNSIGNED_LEDGER_CHANGE. */
    private fun <T> signedOwnerMutation(block:()->T):T {
        val journal=correctionJournal ?: return block()
        val before=SignedOwnerCorrectionJournal.ownerFacts(store.all())
        val result=block()
        journal.recordDiff(before,SignedOwnerCorrectionJournal.ownerFacts(store.all()),nowMillis())
        return result
    }

    /** M2.3: verifies the signed correction journal against the current ledger (read-only). */
    fun correctionAudit():CorrectionAudit = synchronized(CORE_LOCK) {
        correctionJournal?.audit(store.all()) ?: CorrectionAudit(CorrectionAuditStatus.NOT_CONFIGURED,0,emptyList())
    }

    /** Confirmed, not revoked owner facts (the memory the chat may cite), oldest first. */
    fun activeOwnerFacts():List<LearningExperience> = synchronized(CORE_LOCK) {
        store.all().filter {
            it.type==LearningExperienceType.USER_FACT && it.trust==LearningTrustClass.OWNER_CONFIRMED &&
                it.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING) &&
                confirmable(it)
        }
    }

    /** One consistent, read-only view for the EWM brain: the active SWB1 bytes (for the unchanged
     * B0 ranking) and exactly the facts of [activeOwnerFacts]. Revoked or rejected facts are never
     * part of it; nothing is written and no source observation becomes an owner fact. */
    fun brainView():Pair<ByteArray,List<LearningExperience>> = synchronized(CORE_LOCK) {
        loadModel().save() to activeOwnerFacts()
    }

    /** One read-only view for the optional EWM answer gate. Keep tombstones in
     * the scope calculation so an older active fact cannot reappear through a
     * different answer path after a newer assertion is revoked.
     */
    internal fun brainAnswerView(question:String,proposedIds:List<String>,conversationId:String):Pair<List<LearningExperience>,Set<String>?> =
        synchronized(CORE_LOCK) {
            val all=store.all()
            val active=all.filter { it.type==LearningExperienceType.USER_FACT &&
                it.provenance==LearningProvenance.USER_CONFIRMED && it.trust==LearningTrustClass.OWNER_CONFIRMED &&
                it.status in setOf(LearningStatus.CONFIRMED,LearningStatus.LEARNED,LearningStatus.REPLAY_PENDING) }
            val proposed=active.filter { it.id in proposedIds }.map {
                ContinuumMemoryHit(it.id,it.content,"Bestätigte Nutzererinnerung",it.sourceRef,0.0,0.0)
            }
            val resolved=runCatching { dialogue.history(conversationId).lastOrNull() }
                .getOrNull()?.takeIf { it.input==question.trim().take(320) }?.resolvedQuery ?: question
            val scope=OwnerAttributeEvidenceScope.select(resolved,all,proposed)
                ?: if(Regex("(?u)^[Uu]nd\\s+\\p{Lu}[\\p{L}'’-]*(?:s|['’])(?:\\s.*?)?\\?$").matches(question.trim()))
                    emptySet() else null
            active to scope
        }

    fun revokeOwnerFact(content:String):Int=synchronized(CORE_LOCK) { signedOwnerMutation {
        dialogue.clear()
        store.updateMatching(
            LearningExperienceType.USER_FACT,content.trim(),
            LearningStatus.REVOKED,nowMillis()
        )
    } }

    fun revokeAllOwnerFacts():Int=synchronized(CORE_LOCK) { signedOwnerMutation {
        dialogue.clear()
        store.updateMatching(
            LearningExperienceType.USER_FACT,null,
            LearningStatus.REVOKED,nowMillis()
        )
    } }

    fun recordCommandMapping(phrase:String,tool:String):Boolean=synchronized(CORE_LOCK) {
        require(phrase.trim().length in 3..320 && tool.matches(Regex("[a-z_]{2,64}")))
        val item=LearningExperienceFactory.create(
            LearningExperienceType.COMMAND_MAPPING,
            LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED,
            phrase.trim(),
            annotation=tool,
            now=nowMillis()
        )
        store.append(item)
    }

    fun revokeAllCommandMappings():Int=synchronized(CORE_LOCK) {
        store.updateMatching(
            LearningExperienceType.COMMAND_MAPPING,null,
            LearningStatus.REVOKED,nowMillis()
        )
    }

    fun status():ContinuumLearningStatus=synchronized(CORE_LOCK) {
        val holdouts=statusHoldouts()
        ContinuumLearningStatus(
            experiences=store.stats(),
            modelUpdates=holdouts.updates,
            parameters=holdouts.parameters,
            intentHoldout=holdouts.intent,
            semanticHoldout=holdouts.semantic,
            lastAudit=store.all().lastOrNull { it.type==LearningExperienceType.TRAINING_AUDIT }?.content
        )
    }

    private fun statusHoldouts():StatusHoldouts {
        val checkpoint=if(modelFile.exists())modelFile.readBytes() else null
        val intents=INTENT_HOLDOUT.toList()
        val semantics=SEMANTIC_HOLDOUT.toList()
        cachedStatusHoldouts?.let { cached ->
            val sameCheckpoint=if(checkpoint==null)cached.checkpoint==null
                else cached.checkpoint?.contentEquals(checkpoint)==true
            if(sameCheckpoint && cached.intentExamples==intents &&
                cached.semanticExamples==semantics)return cached
        }
        val model=loadModel(checkpoint)
        return StatusHoldouts(if(checkpoint==null)null else model.save(),intents,semantics,
            model.updates,model.parameterCount,
            model.evaluateIntent(intents),model.evaluateSemantic(semantics))
            .also { cachedStatusHoldouts=it }
    }

    /** Experimental decoder: uses the exact active encoder snapshot, never private
     * experiences as decoder training data and never replaces production chat.
     */
    /**
     * CRE1 stays a dedicated research namespace, never used for productive
     * owner facts, command mappings or answer selection. Invoke training from
     * a bounded background worker, not directly from an Android UI callback.
     */
    fun cre1ResearchStatus():Cre1ResearchStatus=synchronized(CORE_LOCK) {
        Cre1ResearchLab(File(directory,"cre1-research")).status()
    }

    fun trainExperimentalCre1(
        training:List<Cre1ResearchExample>,
        audit:List<Cre1ResearchExample>,
        steps:Int=32,
        cancelled:()->Boolean={false}
    ):Cre1ResearchTrainingReport=synchronized(CORE_LOCK) {
        Cre1ResearchLab(File(directory,"cre1-research")).train(
            training,audit,steps=steps,cancelled=cancelled
        )
    }

    fun decoderStatus():ContinuumDecoderBridge.Status=synchronized(CORE_LOCK) {
        ContinuumDecoderBridge(directory).status()
    }

    fun trainExperimentalDecoder(
        training:DecoderCorpus,
        audit:DecoderCorpus,
        steps:Int=600,
        cancelled:()->Boolean={false}
    ):DecoderExperimentResult=synchronized(CORE_LOCK) {
        ContinuumDecoderBridge(directory).trainExperiment(
            training,audit,loadModel().save(),steps,cancelled
        )
    }

    fun experimentalDecoderContinuation(prompt:String,maxBytes:Int=48):DecoderGeneration?=
        synchronized(CORE_LOCK) {
            ContinuumDecoderBridge(directory).experimentalContinuation(
                prompt,loadModel().save(),maxBytes
            )
        }

    /** A separate, evidence-paired decoder LAB. Its weights cannot silently
     * replace the SWB1/CRE1 memory gate or the productive chat model.
     */
    fun pairedDecoderStatus():ContinuumDecoderBridge.Status=synchronized(CORE_LOCK) {
        val snapshot=runCatching { modelSnapshotReadOnly() }.getOrElse {
            return@synchronized ContinuumDecoderBridge.Status(
                false,0,7746,null,conditioningState="CORRUPT")
        }
        ContinuumDecoderBridge(directory).pairedStatus(snapshot)
    }

    /** Read-only CDX1 quality audit under the existing shared model lock. */
    fun intelligenceAudit(
        cancelled: () -> Boolean = { false }
    ): ContinuumIntelligenceAudit.Report = synchronized(CORE_LOCK) {
        ContinuumIntelligenceOnDevice.run(this, cancelled)
    }

    /** Inspect only filenames and metadata; never writes, trains or restores. */
    fun pairedDecoderStorageEvidence():String=synchronized(CORE_LOCK) {
        ContinuumDecoderBridge(directory).storageEvidence()
    }

    fun trainExperimentalPairedDecoder(
        training:DecoderEvidenceCorpus,
        audit:DecoderEvidenceCorpus,
        steps:Int=600,
        cancelled:()->Boolean={false},
        protectedAudits:List<DecoderEvidenceCorpus> = emptyList(),
        learningRate:Double=.035
    ):DecoderExperimentResult=synchronized(CORE_LOCK) {
        emitTrainingPhase("TRAIN_WEIGHTS")
        try {
            ContinuumDecoderBridge(directory).trainPairedExperiment(
                training,audit,loadModel().save(),steps,cancelled,protectedAudits,learningRate
            )
        } finally {
            emitTrainingPhase("AUDIT")
        }
    }

    fun experimentalPairedDecoderContinuation(
        evidence:String,question:String,maxBytes:Int=48
    ):DecoderGeneration?=synchronized(CORE_LOCK) {
        val snapshot=runCatching { modelSnapshotReadOnly() }.getOrNull()
            ?: return@synchronized null
        ContinuumDecoderBridge(directory).experimentalPairedContinuation(
            evidence,question,snapshot,maxBytes
        )
    }

    fun intentScore(text:String):Double=synchronized(CORE_LOCK) { loadModel().intentScore(text) }
    fun similarity(left:String,right:String):Double=synchronized(CORE_LOCK) { loadModel().similarity(left,right) }
    fun modelSnapshot():ByteArray=synchronized(CORE_LOCK) { loadModel().save() }
    fun modelHash():String=synchronized(CORE_LOCK) { ContinuumMemoryDialogue.hash(loadModel().save()) }
    fun contextShadowEnabled() = at.heinz.deviceai.learning.context.Ctx3Shadow.enabled(directory)
    fun configureContextShadow(enabled:Boolean) = at.heinz.deviceai.learning.context.Ctx3Shadow.configure(directory,enabled)
    fun contextShadowEvents(conversationId:String=defaultContextConversation) = at.heinz.deviceai.learning.context.Ctx3Shadow.events(directory,conversationId)
    fun recentContextShadowEvents() = at.heinz.deviceai.learning.context.Ctx3Shadow.recentEvents(directory)
    fun resetContextConversation(conversationId:String) = at.heinz.deviceai.learning.context.Ctx3Shadow.reset(directory,conversationId)
    /** Explicit new chat: discard this chat's bounded dialogue context, never owner facts or weights.
     * One bounded profile snapshot is ID-bound; stale chats cannot erase newer context.
     * Validate the shadow identity before discarding any baseline context.
     */
    fun resetDialogueConversation(conversationId:String,stillCurrent:()->Boolean={true}):Boolean = synchronized(CORE_LOCK) {
        // A destroyed Activity may still have queued I/O or be waiting behind training.
        // Check its lifetime under the same lock as replacement conversations.
        if(!stillCurrent())return@synchronized false
        resetContextConversation(conversationId)
        dialogue.clear(conversationId)
    }
    fun replyFromMemory(input:String,conversationId:String?=null):ContinuumDialogueTurn=synchronized(CORE_LOCK) {
        ContinuumMemoryDialogue.requireConversationId(conversationId)
        val bytes=loadModel().save(); val experiences=store.all()
        val answer=dialogue.replyInConversation(bytes,experiences,input,nowMillis(),conversationId)
        runCatching { at.heinz.deviceai.learning.context.Ctx3Shadow.evaluate(directory,bytes,experiences,input,answer.memoryIds,conversationId?:defaultContextConversation) }
        answer
    }
    /** One immutable model/experience view for answer AND CRE1 candidates.
     * Keeps provenance stable throughout the turn even when an owner fact is
     * modified by another background job after this lock is released.
     */
    fun replyWithEvidence(
        input:String,
        rerank:((String,List<ContinuumMemoryHit>)->List<ContinuumMemoryHit>)?=null
    ):Pair<ContinuumDialogueTurn,List<ContinuumMemoryHit>> = replyWithEvidenceScoped(input,null,rerank)

    fun replyWithEvidenceInConversation(
        input:String,conversationId:String,
        trace:at.heinz.deviceai.cognitive.CognitiveTurnTrace?=null,
        rerank:((String,List<ContinuumMemoryHit>)->List<ContinuumMemoryHit>)?=null
    ):Pair<ContinuumDialogueTurn,List<ContinuumMemoryHit>> = replyWithEvidenceScoped(input,conversationId,rerank,trace)

    private fun replyWithEvidenceScoped(
        input:String,conversationId:String?,
        rerank:((String,List<ContinuumMemoryHit>)->List<ContinuumMemoryHit>)?,
        trace:at.heinz.deviceai.cognitive.CognitiveTurnTrace?=null
    ):Pair<ContinuumDialogueTurn,List<ContinuumMemoryHit>> =
        synchronized(CORE_LOCK) {
            ContinuumMemoryDialogue.requireConversationId(conversationId)
            val snapshot=loadModel().save()
            val experiences=store.all()
            trace?.event(at.heinz.deviceai.cognitive.TraceStage.MEMORY_LOOKUP)
            val turn=dialogue.replyInConversation(snapshot,experiences,input,nowMillis(),conversationId,rerank)
            runCatching { at.heinz.deviceai.learning.context.Ctx3Shadow.evaluate(directory,snapshot,experiences,input,turn.memoryIds,conversationId?:defaultContextConversation,trace) }
            val hits=if(turn.grounded) {
                val byId=ContinuumMemoryDialogue.rank(snapshot,turn.resolvedQuery,experiences)
                    .associateBy { it.id }
                turn.memoryIds.mapNotNull(byId::get)
            } else emptyList()
            turn to hits
        }

    fun dialogueHistory():List<ContinuumDialogueTurn> = synchronized(CORE_LOCK) { dialogue.history() }



    /** Explicit local public-package import. Verification precedes all writes and
     * model acceptance still depends on frozen local benchmarks and owner replay.
     */
    fun importPublicPackage(model:ByteArray,manifest:String):ContinualLearningReport {
        ContinuumPublicModelPackage.verify(model,manifest)
        return importSnapshot(model)
    }

    /** Owner-selected SWB1 only. Checksum is integrity, not a publisher signature.
     * Independent local evaluation protects both tasks and private owner lessons.
     * This method never replaces the experience ledger or command permissions.
     */
    fun importSnapshot(bytes:ByteArray):ContinualLearningReport=synchronized(CORE_LOCK) {
        require(bytes.size==32960) { "Ungültige SWB1-Dateigröße" }
        val candidate=TrainableSubwordEncoder.restore(bytes)
        val baseline=loadModel()
        val intentBefore=baseline.evaluateIntent(INTENT_HOLDOUT)
        val intentAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
        val semanticBefore=baseline.evaluateSemantic(SEMANTIC_HOLDOUT)
        val semanticAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val replay=ownerIntentReplay()
        val replayRetained=replay.isEmpty() || retains(baseline.evaluateIntent(replay),candidate.evaluateIntent(replay))
        val gained=intentAfter.loss+semanticAfter.loss<intentBefore.loss+semanticBefore.loss-0.001
        val accepted=retains(intentBefore,intentAfter) && retains(semanticBefore,semanticAfter) && replayRetained && gained
        val reason=when {
            !retains(intentBefore,intentAfter) -> "Import verworfen: lokaler Intent-Holdout verschlechtert"
            !retains(semanticBefore,semanticAfter) -> "Import verworfen: lokaler Semantik-Holdout verschlechtert"
            !replayRetained -> "Import verworfen: persönliche Intent-Lektionen würden verschlechtert"
            !gained -> "Import unverändert: kein zusätzlicher gemessener Lerngewinn"
            else -> "Import übernommen: beide lokalen Aufgaben und persönliche Lektionen geprüft"
        }
        if(accepted)saveModel(candidate.save(),"snapshot-import")
        appendAudit("snapshot-import",accepted,0,intentBefore,intentAfter,semanticBefore,semanticAfter,emptyList(),reason)
        ContinualLearningReport("snapshot-import",accepted,0,replay.size,intentBefore,intentAfter,
            semanticBefore,semanticAfter,emptyList(),reason)
    }

    /** Metadata of every stored experience (type, provenance, creation time) for the start-screen statistics.
     * Read-only and content-free: no text of owner facts or sources leaves the core through this call. */
    fun experienceTimeline():List<Triple<LearningExperienceType,LearningProvenance,Long>> = synchronized(CORE_LOCK) {
        store.all().map { Triple(it.type,it.provenance,it.createdAt) }
    }

    /** Most recent training/import/rollback decisions (newest first) with their measurements. */
    fun trainingAudits(limit:Int=8):List<String> = synchronized(CORE_LOCK) {
        require(limit in 1..64)
        store.all().filter { it.type==LearningExperienceType.TRAINING_AUDIT }
            .sortedByDescending { it.createdAt }.take(limit).map { it.content }
    }

    /** Archived pre-change states, oldest first. The active model is [modelHash]. */
    fun checkpoints():List<ModelCheckpoint> = synchronized(CORE_LOCK) { checkpoints.list() }

    /** Owner-initiated restore of an archived state. Not a learning claim: the
     * restore itself is archived (so it can be undone) and audited with the
     * frozen holdout measurements of both states. No experience is deleted.
     */
    fun rollbackTo(sha256:String):ContinualLearningReport = synchronized(CORE_LOCK) {
        val target=TrainableSubwordEncoder.restore(checkpoints.read(sha256))
        val current=loadModel()
        val intentBefore=current.evaluateIntent(INTENT_HOLDOUT)
        val intentAfter=target.evaluateIntent(INTENT_HOLDOUT)
        val semanticBefore=current.evaluateSemantic(SEMANTIC_HOLDOUT)
        val semanticAfter=target.evaluateSemantic(SEMANTIC_HOLDOUT)
        val changed=!current.save().contentEquals(target.save())
        if(changed)saveModel(target.save(),"rollback")
        val reason=if(changed) "Wiederhergestellt: Checkpoint "+sha256.take(12)+" (vorheriger Stand archiviert)"
            else "Checkpoint "+sha256.take(12)+" ist bereits aktiv"
        appendAudit("rollback",changed,0,intentBefore,intentAfter,semanticBefore,semanticAfter,emptyList(),reason)
        ContinualLearningReport("rollback",changed,0,0,intentBefore,intentAfter,
            semanticBefore,semanticAfter,emptyList(),reason)
    }

    /** Restore the newest archived state that differs from the active weights. */
    fun rollbackToPrevious():ContinualLearningReport? = synchronized(CORE_LOCK) {
        val active=ContinuumMemoryDialogue.hash(loadModel().save())
        val previous=checkpoints.list().lastOrNull { it.sha256!=active } ?: return@synchronized null
        rollbackTo(previous.sha256)
    }

    private fun ownerIntentReplay():List<SubwordIntentExample> =
        store.replay(LearningExperienceType.INTENT_LABEL,96).mapNotNull { item ->
            val label=item.annotation?.toIntOrNull()
            if(label in 0..1)SubwordIntentExample(item.content,label!!) else null
        }

    private fun interrupted(cancelled:()->Boolean)=cancelled() || Thread.currentThread().isInterrupted

    /** Nothing saved and no audit written: the stage is attempted again later. */
    private fun interruptedReport(task:String,steps:Int,before:SubwordEvaluation,after:SubwordEvaluation,
        holdoutBefore:SubwordEvaluation,holdoutAfter:SubwordEvaluation)=
        ContinualLearningReport(task,false,steps,0,before,after,holdoutBefore,holdoutAfter,emptyList(),
            "Unterbrochen; nichts gespeichert, wird später erneut versucht",completedEvaluation=false)

    private fun retains(before:SubwordEvaluation,after:SubwordEvaluation):Boolean =
        after.correct>=before.correct && after.loss<=before.loss+0.015

    private fun appendAudit(
        task:String,
        accepted:Boolean,
        steps:Int,
        before:SubwordEvaluation,
        after:SubwordEvaluation,
        holdoutBefore:SubwordEvaluation,
        holdoutAfter:SubwordEvaluation,
        sourceIds:List<String>,
        reason:String
    ) {
        val content=buildString {
            append(task).append(" · ").append(if(accepted)"accepted" else "rejected")
            append(" · steps=").append(steps)
            append(" · train=").append("%.5f".format(Locale.ROOT,before.loss))
                .append("→").append("%.5f".format(Locale.ROOT,after.loss))
            append(" · holdout=").append(holdoutBefore.correct).append("/").append(holdoutBefore.total)
                .append("→").append(holdoutAfter.correct).append("/").append(holdoutAfter.total)
            append(" · ").append(reason)
        }.take(1000)
        store.append(LearningExperienceFactory.create(
            type=LearningExperienceType.TRAINING_AUDIT,
            provenance=LearningProvenance.LOCAL_TRAINING,
            trust=LearningTrustClass.SYSTEM_MEASUREMENT,
            status=if(accepted)LearningStatus.LEARNED else LearningStatus.REJECTED,
            content=content,
            annotation=task,
            sourceRef=sourceIds.joinToString(",").takeIf { it.isNotEmpty() },
            now=nowMillis()
        ))
    }

    /** Diagnostic reads must not recover weights, rename files or append audits. */
    private fun modelSnapshotReadOnly():ByteArray =
        if(!modelFile.exists()) TrainableSubwordEncoder().save()
        else TrainableSubwordEncoder.restore(modelFile.readBytes()).save()

    /**
     * Active weights. Unreadable bytes (not an I/O error) are replaced by the
     * last-known-good copy once: the damaged file is kept as
     * subword-v1.bin.corrupt-<time>, the copy becomes active again and the
     * recovery is audited, so it is visible instead of silently repeated.
     */
    private fun loadModel(bytes:ByteArray?=if(modelFile.exists())modelFile.readBytes() else null):TrainableSubwordEncoder {
        if(bytes==null)return TrainableSubwordEncoder()
        return try { TrainableSubwordEncoder.restore(bytes) } catch (damaged:IllegalArgumentException) {
            if(!modelBackup.exists())throw damaged
            val backupBytes=modelBackup.readBytes()
            val good=TrainableSubwordEncoder.restore(backupBytes)
            Files.move(modelFile.toPath(),File(directory,"${modelFile.name}.corrupt-${nowMillis()}").toPath())
            writeAtomically(modelFile,backupBytes)
            val intent=good.evaluateIntent(INTENT_HOLDOUT)
            val semantic=good.evaluateSemantic(SEMANTIC_HOLDOUT)
            appendAudit("model-recovery",true,0,intent,intent,semantic,semantic,emptyList(),
                "Aktive Gewichte unlesbar; letzter geprüfter Stand wiederhergestellt, beschädigte Datei gesichert")
            good
        }
    }

    /** Archives the active state first; if that fails the model is not changed. */
    private fun saveModel(bytes:ByteArray,reason:String) {
        TrainableSubwordEncoder.restore(bytes)
        if(!directory.exists() && !directory.mkdirs())error("Cannot create learning-core directory")
        checkpoints.archive(loadModel().save(),"vor "+reason,nowMillis())
        if(modelFile.exists()) {
            runCatching { TrainableSubwordEncoder.restore(modelFile.readBytes()) }
                .onSuccess { writeAtomically(modelBackup,modelFile.readBytes()) }
        }
        writeAtomically(modelFile,bytes)
    }

    private fun writeAtomically(target:File,bytes:ByteArray) {
        val temp=File.createTempFile("subword-", ".tmp",directory)
        try {
            java.io.FileOutputStream(temp).use { out -> out.write(bytes);out.fd.sync() }
            try {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } catch (_:AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    companion object {
        internal fun prepareSourceBatch(sources:List<MentorSource>):List<MentorSource>? {
            val snapshot=java.util.Collections.unmodifiableList(ArrayList(sources))
            return snapshot.takeIf { sourceExperiences(it,0L)!=null }
        }

        private fun sourceExperiences(sources:List<MentorSource>,at:Long):List<LearningExperience>? {
            sources.forEach { source ->
                MentorSourceValidation.checkSource(source)
                require(source.subject.trim().length>=3)
                TrainableSubwordEncoder.subwordBuckets(source.subject)
                TrainableSubwordEncoder.subwordBuckets(source.excerpt)
            }
            val requested=sources.map { source ->
                LearningExperienceFactory.create(
                    type=LearningExperienceType.SOURCE_TEXT,
                    provenance=if(source.url.startsWith("https://de.wikipedia.org/wiki/"))
                        LearningProvenance.MENTOR_WIKIPEDIA else LearningProvenance.PUBLIC_WEB_SOURCE,
                    trust=LearningTrustClass.SOURCE_OBSERVATION,
                    status=LearningStatus.OBSERVED,
                    content=source.excerpt,
                    annotation=source.subject,
                    sourceRef=source.url+"#sha256="+source.sha,
                    now=at
                )
            }.distinctBy { it.id }
            return requested.takeIf { it.size<=17 }
        }

        /** Explicit owner-approved recovery policy; existing constructors retain strict integrity reads. */
        fun withAutomaticRecovery(baseDirectory:File, nowMillis:()->Long=System::currentTimeMillis):ContinuumLearningCore =
            ContinuumLearningCore(baseDirectory,null,nowMillis,true)

        fun withAutomaticRecovery(baseDirectory:File, correctionSigner:at.heinz.deviceai.cognitive.IdentitySigner?,
                                  nowMillis:()->Long=System::currentTimeMillis):ContinuumLearningCore =
            ContinuumLearningCore(baseDirectory,correctionSigner,nowMillis,true)

        /** Ledger-view cache: key = canonical directory + zone, value = (content fingerprint, ledger-only view).
         * Bounded LRU; entries are immutable views, so sharing them across instances is safe. */
        private const val VIEW_CACHE_MAX=8
        private val VIEW_CACHE=object:LinkedHashMap<String,Pair<String,ContinuumTemporalMemory>>(16,0.75f,true) {
            override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,Pair<String,ContinuumTemporalMemory>>?)=
                size>VIEW_CACHE_MAX
        }
        private val viewCacheHits=java.util.concurrent.atomic.AtomicLong()
        private val viewCacheMisses=java.util.concurrent.atomic.AtomicLong()
        /** (hits, misses) of the ledger-view cache, process-wide. */
        internal fun ledgerViewCacheStats():Pair<Long,Long> = viewCacheHits.get() to viewCacheMisses.get()

        private val CORE_LOCK=Any()

        private data class StatusHoldouts(
            val checkpoint:ByteArray?,
            val intentExamples:List<SubwordIntentExample>,
            val semanticExamples:List<SubwordSemanticExample>,
            val updates:Int,
            val parameters:Int,
            val intent:SubwordEvaluation,
            val semantic:SubwordEvaluation
        )
        // One checkpoint; CORE_LOCK guards access.
        private var cachedStatusHoldouts:StatusHoldouts?=null

        val INTENT_BOOTSTRAP=listOf(
            SubwordIntentExample("hallo",0),SubwordIntentExample("servus miteinander",0),
            SubwordIntentExample("guten morgen",0),SubwordIntentExample("schönen abend euch",0),
            SubwordIntentExample("grüß dich",0),SubwordIntentExample("willkommen zurück",0),
            SubwordIntentExample("was bedeutet das",1),SubwordIntentExample("wie funktioniert das",1),
            SubwordIntentExample("kannst du mir helfen",1),SubwordIntentExample("warum ist das so",1),
            SubwordIntentExample("wo liegt wien",1),SubwordIntentExample("wann beginnt der sommer",1)
        )
        val INTENT_HOLDOUT=listOf(
            SubwordIntentExample("moin zusammen",0),SubwordIntentExample("einen schönen tag",0),
            SubwordIntentExample("herzliche grüße",0),SubwordIntentExample("mahlzeit allerseits",0),
            SubwordIntentExample("ist morgen feiertag",1),SubwordIntentExample("dürfen wir starten",1),
            SubwordIntentExample("welche zeit ist es",1),SubwordIntentExample("woher kommt silizium",1)
        )
        val SEMANTIC_BOOTSTRAP=listOf(
            SubwordSemanticExample("pflanze licht energie","pflanzen nutzen sonnenlicht für energie",true),
            SubwordSemanticExample("motor fahrzeug bewegung","antrieb bewegt ein fahrzeug",true),
            SubwordSemanticExample("sprache grammatik","grammatik beschreibt sprachliche strukturen",true),
            SubwordSemanticExample("pflanze licht energie","motor getriebe fahrzeug",false),
            SubwordSemanticExample("astronomie planeten","grammatik satzbau",false),
            SubwordSemanticExample("musik harmonie","plattentektonik erdbeben",false)
        )
        val SEMANTIC_HOLDOUT=listOf(
            SubwordSemanticExample("sonne licht pflanzen","pflanzen brauchen licht",true),
            SubwordSemanticExample("fahrzeug antrieb","motor bewegt fahrzeug",true),
            SubwordSemanticExample("wörter sätze sprache","sprachliche sätze und wörter",true),
            SubwordSemanticExample("sonne pflanzen","getriebe automobil",false),
            SubwordSemanticExample("musik rhythmus","planet umlaufbahn",false),
            SubwordSemanticExample("zelle biologie","roman literatur",false)
        )
    }
}

