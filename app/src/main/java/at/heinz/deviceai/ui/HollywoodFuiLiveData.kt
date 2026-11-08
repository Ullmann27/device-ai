package at.heinz.deviceai.ui

import at.heinz.deviceai.cockpit.CalcStep
import at.heinz.deviceai.cockpit.ProcessStep
import at.heinz.deviceai.cockpit.StepState

/**
 * Read-only payload for the seven FUI worlds.
 * Every field must originate from an already-emitted production event.
 */
sealed class HollywoodFuiLiveData {
    data class Document(
        val documentName: String,
        val mimeType: String,
        val question: String?,
        /** Null until a real document source supplies it. Never infer from entries. */
        val pageCount: Int? = null,
        val entriesAdded: Int? = null
    ) : HollywoodFuiLiveData()

    data class App(
        val provider: String,
        val accountLabel: String
    ) : HollywoodFuiLiveData()

    data class Timeline(val lines: List<Line>) : HollywoodFuiLiveData() {
        data class Line(val label: String, val state: StepState)

        companion object {
            fun from(steps: List<ProcessStep>): Timeline = Timeline(
                steps.distinctBy { it.key }.takeLast(MAX_LINES).map {
                    Line(clean(it.label, 64), it.state)
                }
            )
        }
    }

    data class Evidence(
        val count: Int,
        val topScore: Float?,
        val sourceLabel: String
    ) : HollywoodFuiLiveData()

    data class Calculation(
        val expression: String,
        val steps: List<CalcStep>,
        val result: Double,
        val unit: String?
    ) : HollywoodFuiLiveData()

    data class Security(
        val sourceLabel: String,
        val measured: Double?,
        val threshold: Double?
    ) : HollywoodFuiLiveData()

    companion object {
        const val MAX_LINES = 8

        fun document(
            name: String,
            mime: String,
            question: String?,
            pageCount: Int? = null,
            entriesAdded: Int? = null
        ) = Document(
            clean(name, 80),
            clean(mime, 64),
            question?.let { clean(it, 120) }?.ifEmpty { null },
            pageCount?.coerceIn(1, 4),
            entriesAdded?.coerceAtLeast(0)
        )

        fun app(provider: String, accountLabel: String) =
            App(clean(provider, 48), clean(accountLabel, 80))

        fun evidence(count: Int, topScore: Float?, sourceLabel: String) =
            Evidence(
                count.coerceAtLeast(0),
                topScore?.takeIf(Float::isFinite)?.coerceIn(0f, 1f),
                clean(sourceLabel, 64)
            )

        fun calculation(
            expression: String,
            steps: List<CalcStep>,
            result: Double,
            unit: String?
        ) = Calculation(
            clean(expression, 96),
            steps.filter { it.value.isFinite() }.take(MAX_LINES).map {
                CalcStep(clean(it.label, 48), it.value)
            },
            result,
            unit?.let { clean(it, 24) }?.ifEmpty { null }
        )

        fun security(sourceLabel: String, measured: Double?, threshold: Double?) =
            Security(
                clean(sourceLabel, 80),
                measured?.takeIf(Double::isFinite),
                threshold?.takeIf(Double::isFinite)
            )

        private fun clean(value: String, max: Int): String =
            value.filterNot(Char::isISOControl).trim().take(max)
    }
}
