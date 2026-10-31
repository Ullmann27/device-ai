package at.heinz.deviceai.cognitive

/**
 * Trusted Android entry points supply the origin. Model output and external
 * notification text must never be allowed to claim OWNER_TEXT / OWNER_VOICE.
 */
enum class JarvisIntentOrigin {
    OWNER_TEXT, OWNER_VOICE, DEVICE_SIGNAL, EXTERNAL_NOTIFICATION
}

enum class JarvisIntentKind { DEVELOPMENT, DIAGNOSTIC }

data class JarvisIntentEvent(
    val sourceEventId: String,
    val origin: JarvisIntentOrigin,
    val kind: JarvisIntentKind,
    val summary: String
)

/**
 * Call only from a trusted application UI boundary. Not an LLM callback.
 * The default denies all owner-originated requests.
 */
fun interface JarvisOwnerIntentVerifier {
    fun authorized(event: JarvisIntentEvent): Boolean
}

sealed class JarvisIntentIntakeResult {
    data class Proposed(val taskId: String, val alreadyProposed: Boolean) :
        JarvisIntentIntakeResult()
    data class Rejected(val code: String) : JarvisIntentIntakeResult()
}

/**
 * Data-only event -> durable PROPOSED backlog task. This class never starts an
 * agent, approves a task, invokes tools, dispatches network traffic or changes
 * any file in a development repository. The existing backlog state machine
 * remains the sole authority for human acceptance and eventual verification.
 *
 * No autonomous path to READY exists. DEVICE_SIGNAL is opt-in, diagnostic-only.
 * EXTERNAL_NOTIFICATION cannot enqueue work in this version.
 */
class JarvisIntentIntake(
    private val backlog: DeviceDevelopmentBacklog,
    private val ownerVerifier: JarvisOwnerIntentVerifier = JarvisOwnerIntentVerifier { false },
    private val passiveDeviceDiagnosticsEnabled: () -> Boolean = { false }
) {
    fun propose(event: JarvisIntentEvent): JarvisIntentIntakeResult {
        if (!EVENT_ID.matches(event.sourceEventId)) {
            return JarvisIntentIntakeResult.Rejected("INVALID_EVENT_ID")
        }
        val title = event.summary.trim()
        if (title.isEmpty() || title.length > DevelopmentBacklog.MAX_TITLE ||
            title.any { it.code < 32 || it.code == 127 } || hasUnpairedSurrogate(title)) {
            return JarvisIntentIntakeResult.Rejected("INVALID_EVENT_SUMMARY")
        }

        val authorized = when (event.origin) {
            JarvisIntentOrigin.OWNER_TEXT, JarvisIntentOrigin.OWNER_VOICE ->
                runCatching { ownerVerifier.authorized(event) }.getOrDefault(false)
            JarvisIntentOrigin.DEVICE_SIGNAL ->
                event.kind == JarvisIntentKind.DIAGNOSTIC &&
                    runCatching { passiveDeviceDiagnosticsEnabled() }.getOrDefault(false)
            JarvisIntentOrigin.EXTERNAL_NOTIFICATION -> false
        }
        if (!authorized) return JarvisIntentIntakeResult.Rejected("INTENT_NOT_AUTHORIZED")

        val idHash = DevelopmentBacklog.sha256Of(
            "jarvis-intent-v1|${event.origin.name}|${event.sourceEventId}"
                .toByteArray(Charsets.UTF_8)
        )
        val taskId = "jarvis-" + idHash.take(32)
        val evidenceHash = DevelopmentBacklog.sha256Of(
            "jarvis-intent-content-v1|${event.origin.name}|${event.kind.name}|$title"
                .toByteArray(Charsets.UTF_8)
        )
        // Only owner-confirmed intents can propose code work. DEVICE_SIGNAL may
        // propose a diagnostic for human review but cannot authorize execution.
        val kind = if (event.kind == JarvisIntentKind.DEVELOPMENT) "IMPLEMENT" else "INVESTIGATE"
        val operation = mapOf(
            "op" to "propose",
            "ifAbsent" to true,
            "reason" to "Jarvis intake: proposal only; separate human acceptance required",
            "task" to mapOf(
                "id" to taskId,
                "title" to title,
                "kind" to kind,
                "source" to "android",
                "priority" to 300L,
                "maxAttempts" to 3L,
                "subject" to mapOf(
                    "intentOrigin" to event.origin.name,
                    "intentKind" to event.kind.name,
                    "eventDigest" to evidenceHash,
                    "reviewOnly" to true
                )
            )
        )

        // Store-layer errors must not turn into an implicit approval or action.
        return try {
            val report = backlog.apply(listOf(operation), stopOnError = false).reports.single()
            if (!report.ok) {
                JarvisIntentIntakeResult.Rejected("BACKLOG_" + (report.code ?: "ERROR"))
            } else {
                // The same sourceEventId with different contents is a conflict, not
                // a successful replay. Test the persisted record after ifAbsent.
                @Suppress("UNCHECKED_CAST")
                val tasks = backlog.read().doc["tasks"] as List<Map<String, Any?>>
                @Suppress("UNCHECKED_CAST")
                val saved = tasks.firstOrNull { it["id"] == taskId }
                    ?.get("subject") as? Map<String, Any?>
                if (saved?.get("eventDigest") != evidenceHash ||
                    saved["intentOrigin"] != event.origin.name ||
                    saved["intentKind"] != event.kind.name ||
                    saved["reviewOnly"] != true) {
                    JarvisIntentIntakeResult.Rejected("EVENT_ID_COLLISION")
                } else {
                    JarvisIntentIntakeResult.Proposed(taskId, alreadyProposed = !report.changed)
                }
            }
        } catch (_: Exception) {
            JarvisIntentIntakeResult.Rejected("BACKLOG_UNAVAILABLE")
        }
    }

    private fun hasUnpairedSurrogate(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length ||
                    !Character.isLowSurrogate(value[index + 1])) return true
                index += 2
            } else {
                if (Character.isLowSurrogate(current)) return true
                index++
            }
        }
        return false
    }

    companion object {
        private val EVENT_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}
