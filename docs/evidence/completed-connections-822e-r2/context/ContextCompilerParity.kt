package at.heinz.deviceai.cognitive

import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import java.util.Random

/** Host-only comparison of the real compiler with the unchanged class from git. */
object ContextCompilerParity {
    private const val NOW = 100_000L
    private const val WARMUP_PAIRS = 600
    private const val ROUNDS = 12
    private const val BATCH = 160
    @Volatile private var sink: CompiledAiContext? = null

    private data class Case(
        val name: String,
        val input: AiContextInput,
        val policy: AiContextPolicy,
        val now: Long = NOW,
        val clockThrows: Boolean = false
    )

    private data class Outcome(
        val context: CompiledAiContext?,
        val exceptionClass: String?,
        val exceptionMessage: String?,
        val clockCalls: Int
    )

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.singleOrNull()) {
            "parity" -> parity()
            "benchmark" -> benchmark()
            else -> error("Expected parity or benchmark")
        }
    }

    private fun fixture(
        unit: String,
        memoryCopies: Int = 40,
        requestCopies: Int = 32,
        researchCopies: Int = 64,
        sourceCount: Int = 2,
        skills: Int = 8
    ): AiContextInput {
        val memories = List(8) { index ->
            ContextIntegrityFixture.memory(
                "memory-$index",
                "Maren Android local model $index: " + unit.repeat(memoryCopies) + " nicht Graz."
            ).copy(
                privacyClass = listOf(PrivacyClass.PUBLIC, PrivacyClass.PRIVATE, PrivacyClass.DEVICE_LOCAL_ONLY)[index % 3],
                validationState = ValidationState.VERIFIED,
                validationEvidenceReferences = listOf("synthetic-evidence-$index"),
                sourceReference = "synthetic-source-$index"
            )
        }
        val snippet = "Maren: " + unit.repeat(researchCopies) + " nicht Graz. token=synthetic_secret"
        val original = ContextIntegrityFixture.dossier(snippet)
        val sources = List(sourceCount) { index ->
            original.sources.single().copy(
                canonicalUrl = "https://example.org/evidence/$index",
                title = "Synthetic $index $unit"
            )
        }
        return ContextIntegrityFixture.input(
            "Maren Android local model: " + unit.repeat(requestCopies),
            memories,
            listOf("Maren: " + unit.repeat(24) + " nicht in Graz.", "Widerruf: keine Freigabe."),
            original.copy(
                sources = sources,
                synthesis = ResearchSynthesisOutput("Maren: " + unit.repeat(48) + " credential=synthetic_secret")
            )
        ).copy(
            activeTaskSummary = "Maren: " + unit.repeat(80) + " Nicht senden.",
            availableSkillNames = List(skills) { "skill-$it-" + unit.repeat(12) },
            model = ImportedLocalModel(
                "synthetic-model", "v1", "0".repeat(64), 1L,
                LocalModelCapabilities(true, false, false, false, true, 4_096, null,
                    setOf(LocalModelBackend.CPU, LocalModelBackend.GPU))
            )
        )
    }

    private fun run(case: Case, baseline: Boolean): Outcome {
        var calls = 0
        val clock = {
            calls++
            check(!case.clockThrows) { "SYNTHETIC_CLOCK_FAILURE" }
            case.now
        }
        return try {
            val result = if (baseline) BaselineAiContextCompiler(case.policy, clock).compile(case.input)
            else AiContextCompiler(case.policy, clock).compile(case.input)
            Outcome(result, null, null, calls)
        } catch (e: Exception) {
            Outcome(null, e.javaClass.name, e.message, calls)
        }
    }

    private fun assertParity(case: Case): Outcome {
        val old = run(case, true)
        val new = run(case, false)
        check(old == new) { "Full context/exception/clock parity failed: ${case.name}" }
        old.context?.let {
            val bytes = it.text.toByteArray(Charsets.UTF_8)
            check(bytes.contentEquals(new.context!!.text.toByteArray(Charsets.UTF_8))) {
                "UTF-8 parity failed: ${case.name}"
            }
            check(bytes.size <= case.policy.maxBytes) { "Budget exceeded: ${case.name}" }
        }
        return old
    }

    private fun cases(): List<Case> {
        val cases = mutableListOf<Case>()
        val atoms = listOf("a", "ä", "中", "🧠", "e\u0301", "\u0000", "\uD800", "\uDC00",
            "\uD800\uD800", "\uDC00\uD800", "\uD800x\uDC00", "\uDBFF\uDFFF")
        for ((index, atom) in atoms.withIndex()) {
            val input = fixture(atom).copy(
                userRequest = "Maren Android local model: " + atom.repeat(64) + "\uD800",
                activeTaskSummary = "\uDC00Maren: " + atom.repeat(80) + "\uD800"
            )
            for (native in listOf(false, true)) {
                for (budget in listOf(1_024, 1_300, 2_048, 4_096, 16_000, 65_536)) {
                    cases += Case("unicode-$index-native-$native-$budget", input,
                        AiContextPolicy(maxBytes = budget, nativeToolCatalog = native))
                }
            }
            // Real compiled section boundaries, including exactly fitting and +/- one byte.
            val policy = AiContextPolicy(maxBytes = 65_536)
            val full = run(Case("boundary-source-$index", input, policy), true).context!!
            val ends = Regex("\n\n## ").findAll(full.text).map { it.range.first }.toList() + full.text.length
            for ((section, end) in ends.withIndex()) {
                val bytes = full.text.substring(0, end).toByteArray(Charsets.UTF_8).size
                for (budget in listOf(bytes - 1, bytes, bytes + 1).filter { it in 1_024..65_536 }) {
                    cases += Case("boundary-$index-$section-$budget", input, policy.copy(maxBytes = budget))
                }
            }
        }

        val redaction = fixture("ä").copy(
            userRequest = "Maren password=" + "synthetic_value".repeat(600) + "\nNicht senden.",
            activeTaskSummary = "PaSsWd:synthetic_value TOKEN=synthetic_value secret=synthetic_value " +
                "credential=synthetic_value authorization:synthetic_value api_key=synthetic_value " +
                "api-key:synthetic_value\nBearer synthetic_bearer_value\uD800",
            availableSkillNames = listOf("token=synthetic_value", "follow-up", "follow-up")
        )
        for (budget in listOf(1_024, 1_300, 2_048, 16_000, 65_536)) {
            cases += Case("redaction-$budget", redaction, AiContextPolicy(maxBytes = budget))
        }

        val base = fixture("x")
        val special = base.memories.first()
        val records = base.memories + listOf(
            special,
            special.copy(content = "Maren Android local model: same id, different content."),
            special.copy(id = "sensitive", privacyClass = PrivacyClass.SENSITIVE),
            special.copy(id = "contradicted", validationState = ValidationState.CONTRADICTED),
            special.copy(id = "stale", validationState = ValidationState.STALE),
            special.copy(id = "future", createdAt = NOW + 1, updatedAt = NOW + 1)
        )
        val random = Random(0x434F4E54455854L)
        repeat(96) { index ->
            val privacy = if (index % 2 == 0) PrivacyClass.entries.toSet() else setOf(PrivacyClass.PUBLIC)
            val policy = AiContextPolicy(
                maxBytes = 1_024 + random.nextInt(15_000),
                memoryLimit = listOf(0, 1, 8, 32)[index % 4],
                researchSourceLimit = listOf(0, 1, 2, 24)[index % 4],
                nativeToolCatalog = index % 2 == 0,
                allowedPrivacy = privacy,
                includeToolCatalog = index % 3 != 0
            )
            cases += Case("selection-$index", base.copy(memories = records), policy,
                now = listOf(-1L, 0L, NOW, Long.MAX_VALUE)[index % 4])
        }
        val blank = base.copy(activeTaskSummary = " \n\t", memories = emptyList(),
            availableSkillNames = emptyList(), unresolvedContradictions = emptyList(),
            researchDossier = base.researchDossier!!.copy(sources = emptyList(), synthesis = null,
                completedSourceHashes = emptySet()))
        cases += Case("blank-sections", blank, AiContextPolicy(includeToolCatalog = false))
        cases += Case("private-frame", base, AiContextPolicy(memoryLimit = 0, researchSourceLimit = 0,
            includeToolCatalog = false, includeIdentityDetails = false))
        cases += Case("clock-exception", base, AiContextPolicy(), clockThrows = true)
        cases += Case("required-before-clock", base.copy(userRequest = "x".repeat(16_000)),
            AiContextPolicy(maxBytes = 1_024), clockThrows = true)
        cases += Case("required-contradictions", base.copy(unresolvedContradictions =
            List(128) { "Maren: keine Freigabe $it " + "ä".repeat(30) }), AiContextPolicy(maxBytes = 1_024))
        cases += Case("skip-then-include", base.copy(memories = listOf(special.copy(
            content = "Maren Android local model: " + "x".repeat(40_000)))), AiContextPolicy(maxBytes = 4_096))
        return cases + benchmarkCases()
    }

    private fun benchmarkCases(): List<Case> = listOf(
        Case("minimal-control", ContextIntegrityFixture.input("Wo ist Maren?"),
            AiContextPolicy(includeToolCatalog = false)),
        Case("mixed-ascii-16k", fixture("Kontext ", memoryCopies = 70, requestCopies = 256,
            researchCopies = 120, sourceCount = 4, skills = 32), AiContextPolicy(maxBytes = 16_000)),
        Case("mixed-unicode-64k", fixture("Å中🧠", memoryCopies = 350, requestCopies = 320,
            researchCopies = 250, sourceCount = 6, skills = 64), AiContextPolicy(maxBytes = 65_536)),
        Case("omitted-memory-8k", fixture("x", memoryCopies = 3_000, requestCopies = 128,
            researchCopies = 128, sourceCount = 4, skills = 16), AiContextPolicy(maxBytes = 8_192))
    )

    private fun parity() {
        val cases = cases()
        val digest = MessageDigest.getInstance("SHA-256")
        var successes = 0
        var failures = 0
        var truncated = 0
        for (case in cases) {
            val outcome = assertParity(case)
            // UTF-16 code units retain unpaired surrogates in the result digest.
            val record = case.name + "|" + outcome.toString()
            record.forEach { char -> digest.update((char.code ushr 8).toByte()); digest.update(char.code.toByte()) }
            if (outcome.context == null) failures++ else {
                successes++
                if (outcome.context.truncated) truncated++
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        println("""{"status":"PASS","cases":${cases.size},"successfulContexts":$successes,"identicalExceptions":$failures,"truncatedContexts":$truncated,"outcomesUtf16Sha256":"$hash","fullDataClassEquality":true,"utf8ByteEquality":true,"clockCallEquality":true,"seed":"0x434F4E54455854"}""")
    }

    private data class Measurement(val nanosPerOperation: Double, val bytesPerOperation: Double)

    private fun measure(bean: ThreadMXBean, operation: () -> CompiledAiContext): Measurement {
        val thread = Thread.currentThread().id
        val allocatedBefore = bean.getThreadAllocatedBytes(thread)
        val started = System.nanoTime()
        repeat(BATCH) { sink = operation() }
        val nanos = System.nanoTime() - started
        val bytes = bean.getThreadAllocatedBytes(thread) - allocatedBefore
        return Measurement(nanos.toDouble() / BATCH, bytes.toDouble() / BATCH)
    }

    private fun benchmark() {
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
            ?: error("Host allocation counter unavailable")
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        println("""{"warmupPairs":$WARMUP_PAIRS,"rounds":$ROUNDS,"batch":$BATCH,"allocationCounter":"HotSpot ThreadMXBean","workloads":[""")
        for ((index, case) in benchmarkCases().withIndex()) {
            val expected = assertParity(case).context ?: error("Benchmark fixture must compile")
            val baseline = BaselineAiContextCompiler(case.policy, nowMillis = { NOW })
            val candidate = AiContextCompiler(case.policy, nowMillis = { NOW })
            val old = { baseline.compile(case.input) }
            val new = { candidate.compile(case.input) }
            repeat(WARMUP_PAIRS) { iteration ->
                if (iteration % 2 == 0) { sink = old(); sink = new() }
                else { sink = new(); sink = old() }
            }
            val samples = mutableListOf<String>()
            repeat(ROUNDS) { round ->
                val a: Measurement
                val b: Measurement
                if (round % 2 == 0) { a = measure(bean, old); b = measure(bean, new) }
                else { b = measure(bean, new); a = measure(bean, old) }
                check(sink == expected)
                samples += """{"round":$round,"baselineFirst":${round % 2 == 0},"baselineNs":${a.nanosPerOperation},"candidateNs":${b.nanosPerOperation},"baselineAllocatedBytes":${a.bytesPerOperation},"candidateAllocatedBytes":${b.bytesPerOperation}}"""
            }
            check(old() == expected && new() == expected)
            if (index > 0) println(",")
            println("""{"name":"${case.name}","outputBytes":${expected.text.toByteArray(Charsets.UTF_8).size},"memoryCount":${expected.includedMemoryIds.size},"sourceCount":${expected.includedResearchSourceUrls.size},"truncated":${expected.truncated},"samples":[${samples.joinToString(",") }]}""")
        }
        println("]}")
    }
}
