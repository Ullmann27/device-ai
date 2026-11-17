package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.CancellationException

/**
 * Explicit owner-triggered, bounded CDX1 curriculum runner.
 *
 * This never reads private conversation, owner memories, web sources or tools.
 * It cannot modify SWB1 and never enables the research decoder in normal chat.
 * Completed stages (accepted OR rejected) are checkpointed to prevent
 * accidental repetition; incomplete runs may be safely retried.
 */
class ContinuumCdxLargeLab(private val filesDir:File) {
    private var phaseObserver:((String)->Unit)?=null
    fun observeTraining(observer:((String)->Unit)?) { phaseObserver=observer }
    private val stateFile=File(filesDir,"learning-core-v2/cdx-pairs-v1-stage.txt")

    data class State(val completed:Int,val total:Int,val decoderUpdates:Int,
                     val decoderHash:String?,val available:Boolean)

    fun status():State=synchronized(LOCK) {
        val decoder=ContinuumLearningCore(filesDir).pairedDecoderStatus()
        State(readStage(),ContinuumCdxLargeCurriculum.STAGES,
            decoder.updates,decoder.sha256,decoder.available)
    }

    data class Round(val stage:Int,val total:Int,val accepted:Boolean,
                     val completed:Int,val scheduled:Int,val before:Double,
                     val after:Double,val auditBefore:Double,
                     val auditAfter:Double,val oldAuditBefore:Double,
                     val oldAuditAfter:Double,val hash:String,
                     val finalized:Boolean,val attempts:Int=1,val generationGate:String="NOT_APPLICABLE") {
        fun description():String = buildString {
            append("CDX1 · GROSSES BELEG-ANTWORT-ARCHIV\n")
            append("Stufe ").append(stage).append("/").append(total)
            append(" · ").append(completed).append("/").append(scheduled)
                .append(" durchgeführte Gewichtsupdates\n")
            append(if(!finalized) "UNVOLLSTÄNDIG · Stufe wiederholbar" else
                if(accepted) "KANDIDAT ÜBERNOMMEN" else "KANDIDAT VERWORFEN")
            append("\nFreie Generierungsprüfung: ").append(generationGate)
            if(generationGate=="INSUFFICIENT_VALIDATION")
                append("\nValidierung benötigt mindestens 20 Belegfragen und 5 Missing-Evidence-Kontrollen. Stufe nicht verbraucht.")
            fun loss(value:Double)=if(value.isFinite())"%.5f".format(Locale.ROOT,value) else "nicht gemessen"
            append("\nTraining: ").append(loss(before)).append(" → ").append(loss(after))
            append("\nUnbekannte Forschungsfälle: ")
                .append(loss(auditBefore)).append(" → ").append(loss(auditAfter))
            append("\nHistorische Prüfbeispiele: ")
                .append(loss(oldAuditBefore)).append(" → ").append(loss(oldAuditAfter))
            append("\nModell-Hash: ").append(hash.take(16))
            if(stage>1)append("\nTRAIN-Rehearsal: 8 ältere Übungsfälle + 8 vollständige Beispielsätze; beide Audits bleiben nur Prüfmaterial.")
            if(attempts==2)append("\nZweiter Versuch mit geringerer Lernrate; auch er muss beide Schutzprüfungen bestehen.")
            append("\nSWB1 unverändert; CDX1-Checkpoint separat; Chatfreigabe NEIN.")
            append("\nForschungsantworten sind nicht automatisch verifizierte Tatsachen.")
        }
    }

    fun next(cancelled:()->Boolean={false}):Round?=synchronized(LOCK) {
        val curriculum=ContinuumCdxLargeCurriculum
        curriculum.validate()
        val index=readStage()
        if(index==curriculum.STAGES)return@synchronized null
        if(cancelled())throw CancellationException("CDX1-Runde vor Beginn abgebrochen")
        val core=ContinuumLearningCore(filesDir).apply {
            observeTraining(phaseObserver)
        }
        if(!DecoderGenerativeGate.hasControls(curriculum.validationAudit)) {
            // A structurally ineligible validation cannot be fixed by retrying
            // gradients. Preserve the stage cursor and all model checkpoints.
            return@synchronized Round(index+1,curriculum.STAGES,false,0,
                curriculum.TRAIN_STEPS,Double.NaN,Double.NaN,Double.NaN,Double.NaN,
                Double.NaN,Double.NaN,core.pairedDecoderStatus().sha256 ?: "0".repeat(64),
                false,0,"INSUFFICIENT_VALIDATION")
        }
        val protected=DecoderEvidenceCorpus(curriculum.historicAudit,DecoderSplit.AUDIT)
        val training=curriculum.trainingStageWithReplay(index)
        val swbBefore=core.modelHash()
        // Each experiment begins from the same persisted checkpoint if rejected;
        // the bridge commits ONLY after both frozen audit sets pass. No rewind.
        val first=core.trainExperimentalPairedDecoder(
            training,curriculum.validationAudit,
            steps=curriculum.TRAIN_STEPS,cancelled=cancelled,
            protectedAudits=listOf(protected))
        val retry=index>0 && !first.accepted &&
            first.generationGate!="INSUFFICIENT_VALIDATION" &&
            first.completedSteps==curriculum.TRAIN_STEPS && !cancelled()
        val second=if(retry)core.trainExperimentalPairedDecoder(
            training,curriculum.validationAudit,
            steps=curriculum.TRAIN_STEPS,cancelled=cancelled,
            protectedAudits=listOf(protected),learningRate=.012
        ) else null
        val report=second ?: first
        check(core.modelHash()==swbBefore) { "SWB1 was changed during CDX1 laboratory" }
        val finished=report.completedSteps==curriculum.TRAIN_STEPS && !cancelled() &&
            report.generationGate!="INSUFFICIENT_VALIDATION" && report.generationGate!="INTERRUPTED"
        if(finished)writeStage(index+1)
        Round(index+1,curriculum.STAGES,report.accepted,
            first.completedSteps+(second?.completedSteps ?: 0),
            curriculum.TRAIN_STEPS*(if(second==null)1 else 2),
            report.trainingBefore,report.trainingAfter,
            report.auditBefore,report.auditAfter,
            report.protectedBefore.single(),report.protectedAfter.single(),
            report.activeSha256,finished,if(second==null)1 else 2,report.generationGate)
    }

    private fun readStage():Int {
        // A dangling symbolic link has exists() == false; check the link first to
        // prevent silently resetting consumed training stages to zero.
        require(!Files.isSymbolicLink(stateFile.toPath())) {
            "CDX1 stage cursor cannot be a symbolic link"
        }
        if(!stateFile.exists())return 0
        require(stateFile.length() in 1..3)
        return stateFile.readText().trim().toIntOrNull()?.also {
            require(it in 0..ContinuumCdxLargeCurriculum.STAGES)
        } ?: error("CDX1 training cursor has invalid data; do not overwrite")
    }

    private fun writeStage(stage:Int) {
        require(stage in 1..ContinuumCdxLargeCurriculum.STAGES)
        val parent=stateFile.absoluteFile.parentFile
        check(parent.exists()||parent.mkdirs())
        val tmp=File.createTempFile("cdx-stage-",".tmp",parent)
        try {
            java.io.FileOutputStream(tmp).use {
                it.write(stage.toString().toByteArray(Charsets.UTF_8)); it.fd.sync()
            }
            try {
                Files.move(tmp.toPath(),stateFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } catch (_:java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(),stateFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { tmp.delete() }
    }

    companion object { private val LOCK=Any() }
}
