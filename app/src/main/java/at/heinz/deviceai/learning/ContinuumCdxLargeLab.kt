package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

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
                     val finalized:Boolean) {
        fun description():String = buildString {
            append("CDX1 · GROSSES BELEG-ANTWORT-ARCHIV\n")
            append("Stufe ").append(stage).append("/").append(total)
            append(" · ").append(completed).append("/").append(scheduled)
                .append(" durchgeführte Gewichtsupdates\n")
            append(if(!finalized) "UNVOLLSTÄNDIG · Stufe wiederholbar" else
                if(accepted) "KANDIDAT ÜBERNOMMEN" else "KANDIDAT VERWORFEN")
            fun loss(value:Double)="%.5f".format(Locale.ROOT,value)
            append("\nTraining: ").append(loss(before)).append(" → ").append(loss(after))
            append("\nUnbekannte Forschungsfälle: ")
                .append(loss(auditBefore)).append(" → ").append(loss(auditAfter))
            append("\nHistorische Prüfbeispiele: ")
                .append(loss(oldAuditBefore)).append(" → ").append(loss(oldAuditAfter))
            append("\nModell-Hash: ").append(hash.take(16))
            if(stage>1)append("\nTRAIN-Rehearsal: 8 ältere Übungsfälle + 8 vollständige Beispielsätze; beide Audits bleiben nur Prüfmaterial.")
            append("\nSWB1 unverändert; CDX1-Checkpoint separat; Chatfreigabe NEIN.")
            append("\nForschungsantworten sind nicht automatisch verifizierte Tatsachen.")
        }
    }

    fun next(cancelled:()->Boolean={false}):Round?=synchronized(LOCK) {
        val curriculum=ContinuumCdxLargeCurriculum
        curriculum.validate()
        val index=readStage()
        if(index==curriculum.STAGES)return@synchronized null
        check(!cancelled()) { "CDX1-Runde vor Beginn abgebrochen" }
        val core=ContinuumLearningCore(filesDir).apply {
            observeTraining(phaseObserver)
        }
        val protected=DecoderEvidenceCorpus(curriculum.historicAudit,DecoderSplit.AUDIT)
        val swbBefore=core.modelHash()
        val report=core.trainExperimentalPairedDecoder(
            curriculum.trainingStageWithReplay(index),curriculum.audit,
            steps=curriculum.TRAIN_STEPS,cancelled=cancelled,
            protectedAudits=listOf(protected))
        check(core.modelHash()==swbBefore) { "SWB1 was changed during CDX1 laboratory" }
        val finished=report.completedSteps==curriculum.TRAIN_STEPS && !cancelled()
        if(finished)writeStage(index+1)
        Round(index+1,curriculum.STAGES,report.accepted,report.completedSteps,
            curriculum.TRAIN_STEPS,report.trainingBefore,report.trainingAfter,
            report.auditBefore,report.auditAfter,
            report.protectedBefore.single(),report.protectedAfter.single(),
            report.activeSha256,finished)
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
