package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

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
    val reason:String
)

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
class ContinuumLearningCore(
    baseDirectory:File,
    private val nowMillis:()->Long=System::currentTimeMillis
) {
    private val directory=File(baseDirectory,"learning-core-v2")
    private val store=FileLearningExperienceStore(directory)
    private val modelFile=File(directory,"subword-v1.bin")
    private val modelBackup=File(directory,"subword-v1.lkg")

    fun teachIntent(
        text:String,
        label:Int,
        cancelled:()->Boolean={false}
    ):ContinualLearningReport = synchronized(CORE_LOCK) {
        require(label in 0..1 && text.trim().length in 3..320)
        val experience=LearningExperienceFactory.create(
            type=LearningExperienceType.INTENT_LABEL,
            provenance=LearningProvenance.USER_CONFIRMED,
            trust=LearningTrustClass.OWNER_CONFIRMED,
            status=LearningStatus.CONFIRMED,
            content=text.trim(),
            annotation=label.toString(),
            now=nowMillis()
        )
        store.append(experience)

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
        val train=candidate.trainIntent(
            training,steps=650,maxMillis=2500,cancelled=cancelled
        )
        val after=candidate.evaluateIntent(training)
        val holdoutAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
        val newScore=candidate.intentScore(experience.content)
        val learnedNew=if(label==1)newScore>=0.55 else newScore<=0.45
        val accepted=!cancelled() && !train.stoppedEarly && learnedNew &&
            after.loss+1e-7<before.loss &&
            holdoutAfter.correct>=holdoutBefore.correct &&
            holdoutAfter.loss<=holdoutBefore.loss+0.035
        if(accepted) {
            saveModel(candidate.save())
            store.updateStatus(experience.id,LearningStatus.LEARNED,nowMillis())
        } else {
            store.updateStatus(experience.id,LearningStatus.REPLAY_PENDING,nowMillis())
        }
        val reason=when {
            cancelled() || train.stoppedEarly -> "Abgebrochen; Snapshot unverändert"
            !learnedNew -> "Neue bestätigte Lektion noch nicht ausreichend gelernt"
            holdoutAfter.correct<holdoutBefore.correct -> "Intent-Holdout würde Treffer verlieren"
            holdoutAfter.loss>holdoutBefore.loss+0.035 -> "Intent-Holdout-Verlust überschreitet Regressionsgrenze"
            after.loss>=before.loss-1e-7 -> "Replay-Trainingsverlust nicht verbessert"
            else -> "Replay verbessert und fester Intent-Holdout nicht verschlechtert"
        }
        appendAudit("intent",accepted,train.steps,before,after,holdoutBefore,holdoutAfter,
            listOf(experience.id),reason)
        ContinualLearningReport(
            "intent",accepted,train.steps,replay.size,before,after,
            holdoutBefore,holdoutAfter,listOf(experience.id),reason
        )
    }

    fun learnFromSources(
        sources:List<MentorSource>,
        cancelled:()->Boolean={false}
    ):ContinualLearningReport? = synchronized(CORE_LOCK) {
        if(sources.isEmpty())return@synchronized null
        sources.forEach(MentorSourceNotebook::checkSource)
        val ids=mutableListOf<String>()
        sources.forEach { source ->
            val experience=LearningExperienceFactory.create(
                type=LearningExperienceType.SOURCE_TEXT,
                provenance=LearningProvenance.MENTOR_WIKIPEDIA,
                trust=LearningTrustClass.SOURCE_OBSERVATION,
                status=LearningStatus.OBSERVED,
                content=source.excerpt,
                annotation=source.subject,
                sourceRef=source.url+"#sha256="+source.sha,
                now=nowMillis()
            )
            store.append(experience)
            ids+=experience.id
        }

        val replay=store.replay(LearningExperienceType.SOURCE_TEXT,48)
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
        val train=candidate.trainSemantic(
            training,steps=550,maxMillis=2500,cancelled=cancelled
        )
        val after=candidate.evaluateSemantic(training)
        val holdoutAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val accepted=!cancelled() && !train.stoppedEarly &&
            after.loss+1e-7<before.loss &&
            holdoutAfter.correct>=holdoutBefore.correct &&
            holdoutAfter.loss<=holdoutBefore.loss+0.045
        if(accepted)saveModel(candidate.save())
        ids.forEach { id ->
            store.updateStatus(id,
                if(accepted)LearningStatus.LEARNED else LearningStatus.REPLAY_PENDING,
                nowMillis())
        }
        val reason=when {
            cancelled() || train.stoppedEarly -> "Abgebrochen; Snapshot unverändert"
            holdoutAfter.correct<holdoutBefore.correct -> "Semantik-Holdout würde Treffer verlieren"
            holdoutAfter.loss>holdoutBefore.loss+0.045 -> "Semantik-Holdout-Verlust überschreitet Regressionsgrenze"
            after.loss>=before.loss-1e-7 -> "Quellen-Replay-Verlust nicht verbessert"
            else -> "Quellenkontexte verbessert; semantischer Holdout nicht verschlechtert"
        }
        appendAudit("semantic",accepted,train.steps,before,after,holdoutBefore,holdoutAfter,ids,reason)
        ContinualLearningReport(
            "semantic",accepted,train.steps,replay.size,before,after,
            holdoutBefore,holdoutAfter,ids,reason
        )
    }

    fun status():ContinuumLearningStatus=synchronized(CORE_LOCK) {
        val model=loadModel()
        ContinuumLearningStatus(
            experiences=store.stats(),
            modelUpdates=model.updates,
            parameters=model.parameterCount,
            intentHoldout=model.evaluateIntent(INTENT_HOLDOUT),
            semanticHoldout=model.evaluateSemantic(SEMANTIC_HOLDOUT),
            lastAudit=store.all().lastOrNull { it.type==LearningExperienceType.TRAINING_AUDIT }?.content
        )
    }

    fun intentScore(text:String):Double=synchronized(CORE_LOCK) { loadModel().intentScore(text) }
    fun similarity(left:String,right:String):Double=synchronized(CORE_LOCK) { loadModel().similarity(left,right) }
    fun modelSnapshot():ByteArray=synchronized(CORE_LOCK) { loadModel().save() }

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

    private fun loadModel():TrainableSubwordEncoder {
        if(!modelFile.exists())return TrainableSubwordEncoder()
        return runCatching { TrainableSubwordEncoder.restore(modelFile.readBytes()) }
            .getOrElse {
                if(modelBackup.exists())TrainableSubwordEncoder.restore(modelBackup.readBytes()) else throw it
            }
    }

    private fun saveModel(bytes:ByteArray) {
        TrainableSubwordEncoder.restore(bytes)
        if(!directory.exists() && !directory.mkdirs())error("Cannot create learning-core directory")
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
        private val CORE_LOCK=Any()

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
