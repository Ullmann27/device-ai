package at.heinz.deviceai.cognitive

import at.heinz.deviceai.learning.ContinuumMemoryEvent
import at.heinz.deviceai.learning.ContinuumMemoryEventHub
import at.heinz.deviceai.learning.RecallHit
import at.heinz.deviceai.learning.RecallHits
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test

class MemoryRecallObservationTest {
    private fun observed(
        compiler: AiContextCompiler,
        observer: (String, List<RecallHit>, Long) -> Unit
    ): AiContextCompiler {
        val method = compiler.javaClass.methods.singleOrNull { it.name == "withRecallObserver" }
            ?: throw AssertionError("The production context compiler has no recall observation hook")
        return method.invoke(compiler, observer) as AiContextCompiler
    }

    @Suppress("UNCHECKED_CAST")
    private fun publisher(directory: File): (String, List<RecallHit>, Long) -> Unit {
        val type = try { Class.forName("at.heinz.deviceai.cognitive.ContinuumMemoryRecallPublisher") }
        catch (_: ClassNotFoundException) { throw AssertionError("The production recall publisher is absent") }
        return type.getConstructor(File::class.java).newInstance(directory) as (String, List<RecallHit>, Long) -> Unit
    }

    @Test fun observationMatchesOnlyTheMemoriesActuallyIncludedInTheAnswerContext() {
        val observed = mutableListOf<Triple<String, List<RecallHit>, Long>>()
        val compiler = observed(AiContextCompiler(AiContextPolicy(), { 100L })) { query, hits, at ->
            observed += Triple(query, hits, at)
        }
        val permitted = ContextIntegrityFixture.memory("visible", "Maren wohnt in Graz.")
        val hidden = permitted.copy(id = "hidden", privacyClass = PrivacyClass.SENSITIVE)
        val stale = permitted.copy(id = "stale", validationState = ValidationState.STALE)
        val result = compiler.compile(ContextIntegrityFixture.input("Maren", listOf(hidden, stale, permitted)))

        assertEquals(1, observed.size)
        assertEquals(result.includedMemoryIds, observed.single().second.map { it.entryId })
        assertEquals(listOf("visible"), result.includedMemoryIds)
        assertEquals("Maren", observed.single().first)
        assertEquals(100L, observed.single().third)
        assertEquals(listOf(1), observed.single().second.map { it.rank })
    }

    @Test fun anOmittedMemoryGroupProducesAnHonestZeroHitObservation() {
        val seen = mutableListOf<List<RecallHit>>()
        val compiler = observed(AiContextCompiler(AiContextPolicy(maxBytes = 1_100), { 100L })) { _, hits, _ -> seen += hits }
        val memory = ContextIntegrityFixture.memory("oversize", "Maren " + "Kontext ".repeat(700) + "nicht Graz.")
        val result = compiler.compile(ContextIntegrityFixture.input("Maren", listOf(memory)))

        assertTrue(result.truncated)
        assertTrue(result.includedMemoryIds.isEmpty())
        assertEquals(listOf(emptyList<RecallHit>()), seen)
    }

    @Test fun aFailedCompilationNeverPublishesASuccessfulRecall() {
        var events = 0
        val compiler = observed(AiContextCompiler(AiContextPolicy(maxBytes = 1_024), { 100L })) { _, _, _ -> events++ }
        assertThrows(IllegalStateException::class.java) {
            compiler.compile(ContextIntegrityFixture.input("Maren ".repeat(500)))
        }
        assertEquals(0, events)
    }

    @Test fun aBrokenPresentationObserverDoesNotChangeTheAnswerContext() {
        val input = ContextIntegrityFixture.input("Maren", listOf(ContextIntegrityFixture.memory("maren", "Maren wohnt in Graz.")))
        val base = AiContextCompiler(AiContextPolicy(), { 100L })
        val expected = base.compile(input)
        val instrumented = observed(base) { _, _, _ -> throw IllegalStateException("presentation unavailable") }

        assertEquals(expected, instrumented.compile(input))
        assertEquals(expected, base.compile(input))
    }

    @Test fun theActualCompilerPublishesOnlySaltedHashesAndSelectedIdsToTheExistingHub() {
        val directory = Files.createTempDirectory("memory-compiler-events").toFile()
        val hub = ContinuumMemoryEventHub.forApp(directory)
        val seen = CopyOnWriteArrayList<ContinuumMemoryEvent>()
        val subscription = hub.addListener { seen += it }
        try {
            val compiler = observed(AiContextCompiler(AiContextPolicy(), { 100L }), publisher(directory))
            val request = "Maren synthetic-private-question"
            val memory = ContextIntegrityFixture.memory("memory-42", "Maren synthetic-private-content")
            val result = compiler.compile(ContextIntegrityFixture.input(request, listOf(memory)))
            hub.awaitIdle(5_000)

            val event = seen.single() as RecallHits
            assertEquals(result.includedMemoryIds, event.hits.map { it.entryId })
            assertEquals(hub.queryHash(request), event.queryHash)
            assertTrue(event.queryHash.matches(Regex("[a-f0-9]{64}")))
            assertFalse(event.toString().contains("synthetic-private"))
            assertEquals(100L, event.atEpochMs)
        } finally { subscription.close(); directory.deleteRecursively() }
    }

    @Test fun noListenerNeedsNoEventSaltOrBackgroundWorker() {
        val directory = Files.createTempDirectory("memory-no-listener").toFile()
        try {
            val compiler = observed(AiContextCompiler(AiContextPolicy(), { 100L }), publisher(directory))
            compiler.compile(ContextIntegrityFixture.input("Maren", listOf(ContextIntegrityFixture.memory("memory-42", "Maren in Graz"))))
            assertNull(ContinuumMemoryEventHub.forApp(directory).dispatchThreadForTest())
            assertFalse(File(directory, "learning-core-v2/.memory-event-salt").exists())
        } finally { directory.deleteRecursively() }
    }
}
