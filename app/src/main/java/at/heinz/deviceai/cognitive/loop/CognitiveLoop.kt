package at.heinz.deviceai.cognitive.loop

import at.heinz.deviceai.Risk
import at.heinz.deviceai.ToolRegistry

/** RED stub: contract only, implementation follows. */
class CognitiveLoop(
    private val turnId: String,
    private val ports: LoopPorts,
    private val limits: LoopLimits = LoopLimits(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val listeners: List<CognitiveLoopListener> = emptyList(),
    private val riskOf: (String) -> Risk = ToolRegistry::risk
) {
    fun cancel(): Unit = TODO("CognitiveLoop.cancel")
    fun run(goal: String): LoopOutcome = TODO("CognitiveLoop.run")
}
