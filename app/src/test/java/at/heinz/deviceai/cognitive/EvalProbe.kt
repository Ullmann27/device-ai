package at.heinz.deviceai.cognitive

import java.time.Instant
import java.time.ZoneId

/**
 * Test-only access for the evaluation runner to the package-internal deterministic lanes.
 * It calls the productive objects unchanged and adds no behaviour of its own.
 */
object EvalProbe {
    /** `READY:<APP>:<GOAL>`, `ASK`, `UNSUPPORTED` or `NONE`. */
    fun computer(utterance: String): String = when (val result = OwnerComputerIntentParser.parse(utterance)) {
        is OwnerComputerIntentParser.Result.Ready -> "READY:${result.intent.app.name}:${result.intent.goal.name}"
        is OwnerComputerIntentParser.Result.Ask -> "ASK"
        is OwnerComputerIntentParser.Result.Unsupported -> "UNSUPPORTED"
        OwnerComputerIntentParser.Result.NotComputerCommand -> "NONE"
    }

    fun objectAnswer(ownerTurns: List<String>, question: String): String? =
        OwnerObjectStateLedger().apply { ownerTurns.forEach(::observeOwnerTurn) }.answer(question)

    fun clock(utterance: String, now: Instant, zone: ZoneId): String? = OwnerDeviceClockReply.answer(utterance, now, zone)

    fun register(utterance: String): String = ConversationStyle.detect(utterance).name

    fun unfinished(utterance: String): Boolean = OwnerTurnCompletion.looksUnfinished(utterance)

    /** Drive-mode command of a whole utterance while the mode is off. */
    fun driveCommand(utterance: String): String? = DriveMode().commandFor(utterance)?.name
}
