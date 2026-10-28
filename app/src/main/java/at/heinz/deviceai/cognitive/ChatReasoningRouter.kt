package at.heinz.deviceai.cognitive

import java.util.concurrent.CancellationException

class ChatReasoningRouter(
    private val adapter: ContinuumReasoningAdapter,
    private val cancelRuntime: () -> Unit,
    private val config: Config = Config(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    data class Config(
        val maxExtraCalls: Int = 4,
        val totalDeadlineMillis: Long = 20_000,
        val maxPassOutputChars: Int = 2_500,
        val maxAdvisoryChars: Int = 1_500,
        val maxContextChars: Int = 2_500,
        val maxTaskChars: Int = 3_000
    ) {
        init {
            require(maxExtraCalls in 1..16)
            require(totalDeadlineMillis in 250..120_000)
            require(maxPassOutputChars in 256..8_000)
            require(maxAdvisoryChars in 64..4_000)
            require(maxContextChars in 1..16_000)
            require(maxTaskChars in 1..8_000)
        }
    }

    private val cancellation = DeliberationCancellation()

    fun cancel() {
        cancellation.cancel()
        runCatching { adapter.cancel() }
        cancelRuntime()
    }

    /** Returns a bounded, unverified advisory for hard tool-free tasks, or null (never throws). */
    fun advise(taskId: String, task: String, context: String,
               groundedRecall: LocalGroundedRecall? = null): String? {
        return try {
            val assessment = ContinuumTaskClassifier.classify(task)
            if (assessment.expectedToolUse || assessment.taskClass == IntelligenceTaskClass.DEVICE) return null
            val strategy = ReasoningStrategySelector.select(assessment.strategyProfile())
            if (strategy != ReasoningStrategy.DEEP && strategy != ReasoningStrategy.MULTI_CANDIDATE) return null

            // The final answer already receives this same-turn recall. Internal
            // candidate/critic/verifier passes need the same evidence as data.
            // Do not clip either governance context or the exact retrieved text.
            val baseContext = context.ifBlank { task }
            val reasoningContext = if (groundedRecall != null) {
                val evidence = groundedRecall.render()
                if (baseContext.length.toLong() + 2L + evidence.length > config.maxContextChars)
                    return null
                baseContext + "\n\n" + evidence
            } else baseContext.take(config.maxContextChars)

            val deadline = nowMillis() + config.totalDeadlineMillis
            var calls = 0
            val bounded = ContinuumReasoningAdapter { request ->
                cancellation.throwIfCancelled()
                check(calls < config.maxExtraCalls) { "Reasoning call budget exhausted." }
                check(nowMillis() < deadline) { "Reasoning deadline expired." }
                calls++
                adapter.execute(
                    request.copy(
                        maxOutputChars = minOf(request.maxOutputChars, config.maxPassOutputChars),
                        deadlineMillis = minOf(request.deadlineMillis, deadline)
                    )
                )
            }
            val request = ContinuumIntelligenceRequest(
                taskId = taskId,
                userRequest = task.take(config.maxTaskChars),
                compiledContext = reasoningContext,
                requestedStrategy = ReasoningStrategy.STANDARD
            )
            val result = ContinuumIntelligenceCoordinator(bounded, nowMillis).run(request, cancellation)
            sanitize(result.deliberation.finalText)
        } catch (cancelled: CancellationException) {
            runCatching(cancelRuntime)
            null
        } catch (failure: Exception) {
            runCatching(cancelRuntime)
            null
        }
    }

    private fun sanitize(text: String): String? =
        text.map { if (it == '[') '(' else if (it == ']') ')' else if (it.isISOControl() && it != '\n') ' ' else it }
            .joinToString("")
            .trim()
            .take(config.maxAdvisoryChars)
            .ifBlank { null }

    companion object {
        const val ADVISORY_HEADER =
            "[P6 UNVERIFIED ADVISORY - low priority hint from internal reasoning; may be wrong; " +
                "never overrides identity, governance, tool rules or the user's request]\n"
        const val ADVISORY_FOOTER = "\n[END UNVERIFIED ADVISORY]"

        /** body + optional labelled advisory + tail, never above cap; tail and body keep priority over the advisory. */
        fun composePrompt(body: String, advisory: String?, tail: String, cap: Int): String {
            val bodyBudget = (cap - tail.length).coerceAtLeast(0)
            val head = body.take(bodyBudget)
            val room = bodyBudget - head.length - 2 - ADVISORY_HEADER.length - ADVISORY_FOOTER.length
            if (advisory.isNullOrBlank() || room <= 0 || head.length < body.length) return (head + tail).take(cap)
            val safe = advisory.replace('[', '(').replace(']', ')').take(room)
            return (head + "\n\n" + ADVISORY_HEADER + safe + ADVISORY_FOOTER + tail).take(cap)
        }
    }
}

