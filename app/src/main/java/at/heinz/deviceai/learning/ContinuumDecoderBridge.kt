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

    data class Status(
        val available: Boolean,
        val updates: Int,
        val parameters: Int,
        val sha256: String?,
        val usableForChat: Boolean = false
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
    val activeSha256: String
)
