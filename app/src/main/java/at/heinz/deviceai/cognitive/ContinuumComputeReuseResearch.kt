package at.heinz.deviceai.cognitive

import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Read-only experimental interface to the EXISTING AiContextCompiler.
 *
 * A context fingerprint alone is NOT an authority receipt or a reusable LLM
 * response. Only caller-declared PURE deterministic intermediate steps can be
 * reused, and only after an independent verifier accepts the result.
 *
 * No Android Activity, network, remote compute, owner memory database,
 * or live production model path is changed by this class.
 */
internal class ContinuumComputeReuseResearch(
    private val compiler: AiContextCompiler = AiContextCompiler(),
    private val receiptVerifier: (key: PureStageKey, result: ByteArray, evidence: String) -> Boolean =
        { _, _, _ -> false },
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 128,
    private val maxResultBytes: Int = 4096,
    private val ttlMillis: Long = 60_000L
) {
    init {
        require(maxEntries in 1..4096)
        require(maxResultBytes in 1..1_048_576)
        require(ttlMillis in 1L..86_400_000L)
    }

    data class PureStageKey(
        val ownerBinding: String,
        val contextSha256: String,
        val modelRevisionSha256: String,
        val policySha256: String,
        val memoryEpoch: Long,
        val stage: Stage
    ) {
        init {
            require(ownerBinding.matches(Regex("[A-Za-z0-9_.:-]{1,128}")))
            require(listOf(contextSha256, modelRevisionSha256, policySha256).all {
                it.matches(Regex("[a-f0-9]{64}"))
            })
            require(memoryEpoch >= 0)
        }
    }

    enum class Stage { SOURCE_PARSE, TOKENIZE, DETERMINISTIC_EMBEDDING, TEST_DISCOVERY }

    data class Observation(
        val compiled: CompiledAiContext,
        val contextSha256: String,
        val includedMemoryCount: Int,
        val includedResearchSourceCount: Int,
        val cacheable: Boolean
    )

    data class Counters(
        val verifiedWrites: Long,
        val hits: Long,
        val misses: Long,
        val rejects: Long,
        val entries: Int
    )

    private data class Entry(val bytes: ByteArray, val deadlineMillis: Long)
    private val entries = object : LinkedHashMap<PureStageKey, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PureStageKey, Entry>?): Boolean =
            size > maxEntries
    }
    private var verifiedWrites = 0L
    private var hits = 0L
    private var misses = 0L
    private var rejects = 0L

    /** Runs the ACTUAL existing context compiler and stores only a digest. */
    fun compileObserved(input: AiContextInput): Observation {
        val compiled = compiler.compile(input)
        return Observation(
            compiled = compiled,
            contextSha256 = sha256(compiled.text.toByteArray(Charsets.UTF_8)),
            includedMemoryCount = compiled.includedMemoryIds.size,
            includedResearchSourceCount = compiled.includedResearchSourceUrls.size,
            cacheable = !compiled.truncated
        )
    }

    /** Call only on a currently approved pure computation: NEVER on LLM final text. */
    @Synchronized fun read(key: PureStageKey): ByteArray? {
        val now = clockMillis()
        val item = entries[key]
        if (item == null) {
            misses++
            return null
        }
        if (now >= item.deadlineMillis) {
            entries.remove(key)
            misses++
            return null
        }
        hits++
        return item.bytes.copyOf()
    }

    /** Default fail-closed. The verifier must independently prove this output. */
    @Synchronized fun putVerified(key: PureStageKey, result: ByteArray, evidence: String): Boolean {
        if (result.isEmpty() || result.size > maxResultBytes || evidence.isBlank()) {
            rejects++
            return false
        }
        if (!runCatching { receiptVerifier(key, result.copyOf(), evidence) }.getOrDefault(false)) {
            rejects++
            return false
        }
        val now = clockMillis()
        if (now < 0 || now > Long.MAX_VALUE - ttlMillis) {
            rejects++
            return false
        }
        entries[key] = Entry(result.copyOf(), now + ttlMillis)
        verifiedWrites++
        return true
    }

    @Synchronized fun clear() { entries.clear() }
    @Synchronized fun metrics(): Counters =
        Counters(verifiedWrites, hits, misses, rejects, entries.size)

    companion object {
        private fun sha256(input: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(input).joinToString("") {
                "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff)
            }
    }
}
