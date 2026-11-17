package at.heinz.deviceai.learning

import java.io.File
import java.security.MessageDigest

/**
 * Research-only link between the committed SWB1 encoder and the CDX1 causal
 * decoder. Neither the personal experience ledger nor production chat is used
 * as training data. An untrained decoder is never represented as chat-ready.
 */
class ContinuumDecoderBridge(private val directory: File) {
    private val checkpoint = File(directory, "context-decoder.cdx")
    private val pairedCheckpoint = File(directory, "context-decoder-paired.cdx")
    private val pairedBinding = File(directory, "context-decoder-paired.swb1-binding")
    private val pairedBundles = ContinuumAtomicPairedBundleStore(directory)

    private data class PairedState(
        val model: TinyContextDecoder,
        val encoder: ByteArray?,
        val status: String,
        val generation: Long,
        val recovered: Boolean
    )

    /** A modern atomic bundle takes precedence over unmodified legacy files. */
    private fun loadPairedState(liveEncoder: ByteArray?): PairedState? {
        val bundled = pairedBundles.loadVerified()
        if (bundled != null) {
            val state = when {
                bundled.recoveredFromBackup -> "RECOVERED_LKG"
                liveEncoder == null -> "PINNED_UNKNOWN"
                bundled.encoder.contentEquals(liveEncoder) -> "PINNED_CURRENT"
                else -> "PINNED_PREVIOUS"
            }
            return PairedState(TinyContextDecoder.restore(bundled.decoder),
                bundled.encoder,state,bundled.generation,bundled.recoveredFromBackup)
        }
        val legacy=loadPairedOrNull() ?: run {
            // An orphaned binding means a previously written checkpoint is missing.
            if (pairedBinding.exists() || java.nio.file.Files.isSymbolicLink(pairedBinding.toPath()))
                throw IllegalStateException("CDX1 legacy binding exists without decoder")
            return null
        }
        val binding=ContinuumPairedEncoderBinding.inspect(
            pairedBinding,legacy.model.save(),liveEncoder)
        val unsafeBinding=binding.state==ContinuumPairedEncoderBinding.State.CORRUPT
        return PairedState(legacy.model,
            if(unsafeBinding)null else binding.frozenEncoder,
            when {
                unsafeBinding -> "CORRUPT"
                legacy.recoveredFromBackup -> "RECOVERED_LKG"
                else -> binding.state.name
            },0L,legacy.recoveredFromBackup)
    }

    /**
     * Read-only evidence about existing CDX1 checkpoint files. Exposes neither
     * private app-directory paths nor any model weights. A missing file,
     * symlink and unreadable existing file must remain distinguishable.
     */
    @Synchronized fun storageEvidence():String {
        fun describe(label:String,file:File):String {
            val state=when {
                java.nio.file.Files.isSymbolicLink(file.toPath()) ->
                    "symbolische Verknüpfung (gesperrt)"
                !file.exists() -> "fehlt"
                !file.isFile -> "kein regulärer Dateityp"
                !file.canRead() -> "vorhanden, nicht lesbar"
                else -> "vorhanden ("+file.length()+" Byte)"
            }
            return label+": "+state
        }
        return listOf(
            describe("Alter CDX1-Checkpoint",pairedCheckpoint),
            describe("Alte CDX1-Sicherung",File(pairedCheckpoint.absolutePath+".lkg")),
            describe("Alte SWB1-Bindung",pairedBinding),
            describe("Neues Paarbundle",File(directory,"context-decoder-paired.bundle")),
            describe("Paarbundle-Sicherung",File(directory,"context-decoder-paired.bundle.lkg"))
        ).joinToString("\n")
    }

    data class Status(
        val available: Boolean,
        val updates: Int,
        val parameters: Int,
        val sha256: String?,
        val usableForChat: Boolean = false,
        val conditioningState: String = "LEGACY_UNBOUND"
    )

    @Synchronized fun status(): Status {
        val model = loadOrNull()
        return if (model == null) Status(false, 0, 7746, null)
        else Status(true, model.updates, model.parameterCount, hash(model.save()))
    }

    /** Pure experiment: candidate promotion requires improvement on distinct audit text. */
    @Synchronized fun trainExperiment(
        train: DecoderCorpus,
        audit: DecoderCorpus,
        encoderSnapshot: ByteArray,
        steps: Int = 600,
        cancelled: () -> Boolean = { false }
    ): DecoderExperimentResult {
        require(train.split == DecoderSplit.TRAIN && audit.split == DecoderSplit.AUDIT)
        require(train.texts.none { it in audit.texts }) { "Training/audit overlap" }
        val encoder = TrainableSubwordEncoder.restore(encoderSnapshot)
        val condition = encoder.representation("research context")
        val baseline = loadOrNull() ?: TinyContextDecoder()
        val candidate = TinyContextDecoder.restore(baseline.save())
        val auditBefore = baseline.evaluate(audit, condition)
        val report = candidate.train(train, steps = steps, conditioning = condition,
            maxMillis = 5000, cancelled = cancelled)
        val auditAfter = candidate.evaluate(audit, condition)
        val accepted = !report.interrupted && report.lossAfter < report.lossBefore &&
            auditAfter <= auditBefore && !cancelled()
        if (accepted) candidate.saveTo(checkpoint)
        val active = if (accepted) candidate else baseline
        return DecoderExperimentResult(accepted, report.steps, report.lossBefore,
            report.lossAfter, auditBefore, auditAfter, hash(active.save()))
    }

    /** Dedicated evidence-paired model, never mixed with historical decoder
     * checkpoints. Train/audit partitions must not reuse the same question+
     * evidence input, even when the answer text differs.
     */
    @Synchronized fun pairedStatus(encoderSnapshot:ByteArray?=null): Status =
        runCatching {
            val state=loadPairedState(encoderSnapshot)
            if(state==null) Status(false,0,7746,null,conditioningState="NO_CHECKPOINT")
            else Status(true,state.model.updates,state.model.parameterCount,
                hash(state.model.save()),conditioningState=state.status)
        }.getOrElse {
            Status(false,0,7746,null,conditioningState="CORRUPT")
        }

    @Synchronized fun trainPairedExperiment(
        train:DecoderEvidenceCorpus,
        audit:DecoderEvidenceCorpus,
        encoderSnapshot:ByteArray,
        steps:Int=600,
        cancelled:()->Boolean={false},
        protectedAudits:List<DecoderEvidenceCorpus> = emptyList()
    ):DecoderExperimentResult {
        require(train.split==DecoderSplit.TRAIN && audit.split==DecoderSplit.AUDIT)
        require(protectedAudits.size<=4 && protectedAudits.all { it.split==DecoderSplit.AUDIT })
        val trainingInputs=train.pairs.map { it.conditionerText() }.toSet()
        require(trainingInputs.size==train.pairs.size) { "Duplicate train input" }
        require(audit.pairs.map { it.conditionerText() }.toSet().size==audit.pairs.size) {
            "Duplicate audit input"
        }
        require(audit.pairs.none { it.conditionerText() in trainingInputs }) {
            "Train/audit evidence-question overlap"
        }
        val heldoutInputs=audit.pairs.map { it.conditionerText() }.toSet()
        val protectedInputs=protectedAudits.flatMap { it.pairs }
            .map { it.conditionerText() }
        require(protectedInputs.size==protectedInputs.toSet().size &&
            protectedInputs.none { it in trainingInputs || it in heldoutInputs }) {
            "Protected holdout must not overlap with training or main audit"
        }
        val state=loadPairedState(encoderSnapshot)
        require(state?.status!="CORRUPT" && state?.recovered!=true) {
            "CDX1 checkpoint invalid or in read-only LKG recovery; repair required"
        }
        val baseline=state?.model ?: TinyContextDecoder()
        // Always condition with the snapshot used by the active paired model.
        val pairedEncoderSnapshot=state?.encoder ?: encoderSnapshot
        val encoder=TrainableSubwordEncoder.restore(pairedEncoderSnapshot)
        val conditionFor:(DecoderEvidencePair)->DoubleArray = {
            encoder.representation(it.conditionerText())
        }
        val candidate=TinyContextDecoder.restore(baseline.save())
        val auditBefore=baseline.evaluateEvidencePairs(audit,conditionFor)
        val protectedBefore=protectedAudits.map { baseline.evaluateEvidencePairs(it,conditionFor) }
        val result=candidate.trainEvidencePairs(train,steps=steps,
            conditionerFor=conditionFor,cancelled=cancelled,maxMillis=5000)
        val auditAfter=candidate.evaluateEvidencePairs(audit,conditionFor)
        val protectedAfter=protectedAudits.map { candidate.evaluateEvidencePairs(it,conditionFor) }
        val protectedSafe=protectedAfter.zip(protectedBefore).all { (now,old) ->
            now.isFinite() && now<=old
        }
        val accepted=result.steps>0 && !result.interrupted && !cancelled() &&
            result.lossAfter<result.lossBefore && auditAfter<=auditBefore &&
            protectedSafe
        if(accepted) {
            // A single verified bundle replaces the previous two-file commit.
            // Legacy decoder and SWB1 binding remain untouched.
            pairedBundles.commit(candidate.save(),pairedEncoderSnapshot,
                expectedGeneration=state?.generation ?: 0L)
        }
        val active=if(accepted)candidate else baseline
        return DecoderExperimentResult(accepted,result.steps,result.lossBefore,
            result.lossAfter,auditBefore,auditAfter,hash(active.save()),
            protectedBefore,protectedAfter)
    }

    /** Research-only evidence-conditional output; never auto-published as a
     * factual answer. Visible text needs an independent evidence verifier.
     */
    @Synchronized fun experimentalPairedContinuation(
        evidence:String,question:String,encoderSnapshot:ByteArray,maxBytes:Int=48
    ):DecoderGeneration? {
        val prefix=decoderEvidencePrefix(evidence,question)
        val state=runCatching { loadPairedState(encoderSnapshot) }.getOrNull() ?: return null
        if(state.status=="CORRUPT")return null
        val condition=TrainableSubwordEncoder.restore(
            state.encoder ?: encoderSnapshot
        ).representation(evidence+"\n"+question)
        return state.model.generate(prefix,maxBytes,condition)
    }

    private data class LegacyState(
        val model:TinyContextDecoder,
        val recoveredFromBackup:Boolean
    )

    /**
     * Never confuse unreadable existing weights with an untrained decoder.
     * A legacy backup can be inspected, but is read-only until explicit
     * recovery is reviewed. No files are modified in this method.
     */
    private fun loadPairedOrNull():LegacyState? {
        val backup=File(pairedCheckpoint.absolutePath+".lkg")
        if(java.nio.file.Files.isSymbolicLink(pairedCheckpoint.toPath()) ||
            java.nio.file.Files.isSymbolicLink(backup.toPath()))
            throw IllegalStateException("CDX1 legacy checkpoint symlink rejected")
        if(pairedCheckpoint.exists()) {
            if(pairedCheckpoint.isFile) {
                runCatching { TinyContextDecoder.restoreFrom(pairedCheckpoint) }
                    .getOrNull()?.let { return LegacyState(it,false) }
            }
            if(backup.isFile) {
                runCatching { TinyContextDecoder.restoreFrom(backup) }
                    .getOrNull()?.let { return LegacyState(it,true) }
            }
            throw IllegalStateException("Existing CDX1 legacy checkpoint cannot be read")
        }
        if(backup.exists()) {
            if(!backup.isFile)throw IllegalStateException("CDX1 backup is not a regular file")
            return LegacyState(
                runCatching { TinyContextDecoder.restoreFrom(backup) }
                    .getOrElse { throw IllegalStateException("CDX1 backup cannot be read",it) },
                true)
        }
        return null
    }

    /** No tool calls, memory access or app actions; output is untrusted experiment text. */
    @Synchronized fun experimentalContinuation(
        prompt: String, encoderSnapshot: ByteArray, maxBytes: Int = 48
    ): DecoderGeneration? {
        val model = loadOrNull() ?: return null
        val condition = TrainableSubwordEncoder.restore(encoderSnapshot)
            .representation(prompt.take(320))
        return model.generate(prompt, maxBytes, condition)
    }

    private fun loadOrNull(): TinyContextDecoder? {
        if (!checkpoint.exists()) return null
        return runCatching { TinyContextDecoder.restoreFrom(checkpoint) }
            .getOrElse {
                val backup = File(checkpoint.absolutePath + ".lkg")
                if (backup.exists()) runCatching { TinyContextDecoder.restoreFrom(backup) }
                    .getOrNull() else null
            }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

data class DecoderExperimentResult(
    val accepted: Boolean,
    val completedSteps: Int,
    val trainingBefore: Double,
    val trainingAfter: Double,
    val auditBefore: Double,
    val auditAfter: Double,
    val activeSha256: String,
    val protectedBefore: List<Double> = emptyList(),
    val protectedAfter: List<Double> = emptyList()
)
