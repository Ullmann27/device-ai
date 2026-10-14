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
    private val dialogue=ContinuumMemoryDialogue(directory)

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
            val train=candidate.trainIntent(INTENT_BOOTSTRAP,steps=650,maxMillis=2500,cancelled=cancelled)
            val after=candidate.evaluateIntent(INTENT_BOOTSTRAP)
            val holdoutAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
            val otherAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val accepted=!cancelled() && !train.stoppedEarly &&
                after.loss+1e-7<before.loss && retains(holdoutBefore,holdoutAfter) &&
                retains(otherBefore,otherAfter)
            if(accepted) saveModel(candidate.save())
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
            val train=candidate.trainSemantic(SEMANTIC_BOOTSTRAP,steps=550,
                maxMillis=2500,cancelled=cancelled)
            val after=candidate.evaluateSemantic(SEMANTIC_BOOTSTRAP)
            val holdoutAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
            val intentAfter=candidate.evaluateIntent(INTENT_HOLDOUT)
            val accepted=!cancelled() && !train.stoppedEarly &&
                after.loss+1e-7<before.loss && retains(holdoutBefore,holdoutAfter) &&
                retains(intentBefore,intentAfter)
            if(accepted)saveModel(candidate.save())
            val reason=if(accepted) "Öffentliches Semantiktraining verbessert; Holdouts geschützt"
                else "Semantikkandidat verworfen; bisherige Gewichte erhalten"
            appendAudit("offline-semantic-bootstrap",accepted,train.steps,before,after,
                holdoutBefore,holdoutAfter,emptyList(),reason)
            ContinualLearningReport("offline-semantic-bootstrap",accepted,train.steps,0,
                before,after,holdoutBefore,holdoutAfter,emptyList(),reason)
        }

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
            saveModel(candidate.save())
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
            holdoutBefore,holdoutAfter,listOf(experience.id),reason
        )
    }

    fun learnFromSources(
        sources:List<MentorSource>,
        cancelled:()->Boolean={false}
    ):ContinualLearningReport? = synchronized(CORE_LOCK) {
        if(sources.isEmpty())return@synchronized null
        sources.forEach(MentorSourceValidation::checkSource)
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
        val protectedIntent=(INTENT_HOLDOUT+ownerIntentReplay()).take(128)
        val otherBefore=candidate.evaluateIntent(protectedIntent)
        val train=candidate.trainSemantic(
            training,steps=550,maxMillis=2500,cancelled=cancelled
        )
        val after=candidate.evaluateSemantic(training)
        val holdoutAfter=candidate.evaluateSemantic(SEMANTIC_HOLDOUT)
        val otherAfter=candidate.evaluateIntent(protectedIntent)
        val otherRetained=retains(otherBefore,otherAfter)
        val accepted=!cancelled() && !train.stoppedEarly && otherRetained &&
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
            !otherRetained -> "Gemeinsame Gewichte würden Intent oder persönliche Lektionen verschlechtern"
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

    fun recordOwnerFact(content:String):Boolean=synchronized(CORE_LOCK) {
        val item=LearningExperienceFactory.create(
            LearningExperienceType.USER_FACT,
            LearningProvenance.USER_CONFIRMED,
            LearningTrustClass.OWNER_CONFIRMED,
            LearningStatus.CONFIRMED,
            content.trim(),
            now=nowMillis()
        )
        store.append(item)
    }

    fun revokeOwnerFact(content:String):Int=synchronized(CORE_LOCK) {
        dialogue.clear()
        store.updateMatching(
            LearningExperienceType.USER_FACT,content.trim(),
            LearningStatus.REVOKED,nowMillis()
        )
    }

    fun revokeAllOwnerFacts():Int=synchronized(CORE_LOCK) {
        dialogue.clear()
        store.updateMatching(
            LearningExperienceType.USER_FACT,null,
            LearningStatus.REVOKED,nowMillis()
        )
    }

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

    /** Experimental decoder: uses the exact active encoder snapshot, never private
     * experiences as decoder training data and never replaces production chat.
     */
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

    fun intentScore(text:String):Double=synchronized(CORE_LOCK) { loadModel().intentScore(text) }
    fun similarity(left:String,right:String):Double=synchronized(CORE_LOCK) { loadModel().similarity(left,right) }
    fun modelSnapshot():ByteArray=synchronized(CORE_LOCK) { loadModel().save() }
    fun modelHash():String=synchronized(CORE_LOCK) { ContinuumMemoryDialogue.hash(loadModel().save()) }
    fun replyFromMemory(input:String):ContinuumDialogueTurn=synchronized(CORE_LOCK) {
        dialogue.reply(loadModel().save(),store.all(),input,nowMillis())
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
        if(accepted)saveModel(candidate.save())
        appendAudit("snapshot-import",accepted,0,intentBefore,intentAfter,semanticBefore,semanticAfter,emptyList(),reason)
        ContinualLearningReport("snapshot-import",accepted,0,replay.size,intentBefore,intentAfter,
            semanticBefore,semanticAfter,emptyList(),reason)
    }

    private fun ownerIntentReplay():List<SubwordIntentExample> =
        store.replay(LearningExperienceType.INTENT_LABEL,96).mapNotNull { item ->
            val label=item.annotation?.toIntOrNull()
            if(label in 0..1)SubwordIntentExample(item.content,label!!) else null
        }

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

