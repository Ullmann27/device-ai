package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.StrictJson
import java.util.Collections

/** Untrusted proposal, never execution authority. */
@ConsistentCopyVisibility
data class AgentFollowupProposal private constructor(
    val goal: String,
    val capability: String,
    val requestedTools: Set<String>,
    val reason: String
) {
    companion object {
        fun create(goal: String, capability: String, requestedTools: Set<String>, reason: String): AgentFollowupProposal {
            require(validText(goal, 1_800) && goal.toByteArray(Charsets.UTF_8).size <= 4_000)
            require(capability.matches(Regex("[a-z][a-z0-9._-]{0,63}")))
            val tools = Collections.unmodifiableSet(LinkedHashSet(requestedTools))
            require(tools.size <= 16 && tools.all { it.matches(Regex("[a-z][a-z0-9_]{1,63}")) })
            require(validText(reason, 300))
            return AgentFollowupProposal(goal, capability, tools, reason)
        }

        private fun validText(text: String, maximum: Int): Boolean = text.isNotBlank() && text.length <= maximum &&
            Charsets.UTF_8.newEncoder().canEncode(text) && text.none {
                it.code in 0..8 || it.code in 11..12 || it.code in 14..31 || it.code == 127
            }
    }
}

/** Optional model envelope; plain replies remain. */
object AgentFollowupEnvelope {
    const val SCHEMA = "continuum-worker-followup-v1"
    data class Decoded(val answer: String, val proposal: AgentFollowupProposal)

    fun parse(text: String): Decoded? = runCatching {
        require(text.toByteArray(Charsets.UTF_8).size <= 6_000)
        val root = StrictJson.parse(text, maxChars = 8_000) as? Map<*, *> ?: return null
        require(root.keys == setOf("schema", "answer", "next"))
        require(root["schema"] == SCHEMA)
        val answer = root["answer"] as? String ?: return null
        require(answer.isNotBlank() && answer.length <= 4_000)
        val next = root["next"] as? Map<*, *> ?: return null
        require(next.keys == setOf("goal", "capability", "tools", "reason"))
        val tools = next["tools"] as? List<*> ?: return null
        require(tools.all { it is String } && tools.distinct().size == tools.size)
        Decoded(answer, AgentFollowupProposal.create(
            next["goal"] as? String ?: return null,
            next["capability"] as? String ?: return null,
            tools.map { it as String }.toSet(), next["reason"] as? String ?: return null))
    }.getOrNull()
}
