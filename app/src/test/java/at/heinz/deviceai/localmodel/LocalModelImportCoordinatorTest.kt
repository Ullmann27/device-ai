package at.heinz.deviceai.localmodel

import at.heinz.deviceai.cognitive.contentHash
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LocalModelImportCoordinatorTest {
    private val bytes = "synthetic model transport fixture".toByteArray()
    private val capabilities = LocalModelCapabilities(true, false, false, false, false,
        1_024, null, setOf(LocalModelBackend.CPU))

    @Test fun importSurvivesOldObserverDetachingAndNewActivityReattachesWithoutDuplicateCopy() {
        val directory = Files.createTempDirectory("import-activity-rotation").toFile()
        val worker = Executors.newSingleThreadExecutor()
        val copying = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val opened = AtomicInteger()
        try {
            val coordinator = LocalModelImportCoordinator(
                LocalModelCatalog(directory, LocalModelInspector { capabilities }), worker)
            val oldCallbacks = AtomicInteger()
            val old = coordinator.observe { oldCallbacks.incrementAndGet() }
            assertTrue(coordinator.start("gemma-fixture", "v1", contentHash(bytes.decodeToString())) {
                opened.incrementAndGet()
                object : ByteArrayInputStream(bytes) {
                    override fun read(target: ByteArray, offset: Int, length: Int): Int {
                        copying.countDown()
                        check(resume.await(5, TimeUnit.SECONDS)) { "Test fixture never resumed" }
                        return super.read(target, offset, length)
                    }
                }
            })
            assertTrue(copying.await(5, TimeUnit.SECONDS))
            old.close()
            val oldCount = oldCallbacks.get()
            assertFalse(coordinator.start("gemma-fixture", "v2", contentHash(bytes.decodeToString())) {
                error("A second import must not open any source")
            })
            val newStates = mutableListOf<LocalModelImportOperation>()
            val current = coordinator.observe {
                synchronized(newStates) { newStates += it }
                if (!it.running) completed.countDown()
            }
            try {
                assertTrue(coordinator.snapshot()!!.running)
                resume.countDown()
                assertTrue(completed.await(5, TimeUnit.SECONDS))
                val terminal = coordinator.snapshot()!!
                assertFalse(terminal.running)
                assertNotNull(terminal.model)
                assertNull(terminal.failureType)
                assertEquals(LocalModelImportPhase.COMMITTED, terminal.progress!!.phase)
                assertEquals(1, opened.get())
                assertEquals(oldCount, oldCallbacks.get())
                assertEquals(terminal.model, LocalModelCatalog(directory,
                    LocalModelInspector { capabilities }).get("gemma-fixture", "v1"))
                val states = synchronized(newStates) { newStates.toList() }
                assertTrue(states.first().running)
                assertFalse(states.last().running)
                assertEquals(states.size, states.map { it.revision }.distinct().size)
                assertTrue(coordinator.claimResultForLoading(terminal.token))
                assertFalse(coordinator.claimResultForLoading(terminal.token))
            } finally { current.close() }
        } finally {
            resume.countDown()
            worker.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test fun sourceFailureIsBoundedAndReplayedWithoutRetainingPrivateUriOrBlockingRetry() {
        val directory = Files.createTempDirectory("import-open-failure").toFile()
        try {
            val coordinator = LocalModelImportCoordinator(
                LocalModelCatalog(directory, LocalModelInspector { capabilities }), Executor { it.run() })
            val badObserver = coordinator.observe { throw IllegalStateException("Observer is destroyed") }
            assertTrue(coordinator.start("fixture", "v1", contentHash(bytes.decodeToString())) {
                throw IOException("content://private-owner-document-and-secret")
            })
            val failure = coordinator.snapshot()!!
            assertFalse(failure.running)
            assertEquals("IOException", failure.failureType)
            assertNull(failure.model)
            assertFalse(failure.toString().contains("private-owner"))
            assertFalse(coordinator.claimResultForLoading(failure.token))
            var replay: LocalModelImportOperation? = null
            coordinator.observe { replay = it }.use { assertEquals(failure, replay) }
            assertTrue(coordinator.start("fixture", "v1", contentHash(bytes.decodeToString())) {
                ByteArrayInputStream(bytes)
            })
            assertNotNull(coordinator.snapshot()!!.model)
            assertTrue(coordinator.snapshot()!!.token > failure.token)
            badObserver.close()
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectedDataAndMissingNativeRuntimeUseStableNonSecretDiagnosticCodes() {
        val directory = Files.createTempDirectory("import-diagnostic-codes").toFile()
        try {
            val coordinator = LocalModelImportCoordinator(
                LocalModelCatalog(directory, LocalModelInspector { capabilities }, maxModelBytes = 64),
                Executor { it.run() })
            assertTrue(coordinator.start("fixture", "hash", "0".repeat(64)) {
                ByteArrayInputStream(bytes)
            })
            assertEquals("SHA256_MISMATCH", coordinator.snapshot()!!.failureType)
            assertTrue(coordinator.start("fixture", "size", "0".repeat(64)) {
                ByteArrayInputStream(ByteArray(65))
            })
            assertEquals("SIZE_LIMIT", coordinator.snapshot()!!.failureType)
            assertTrue(coordinator.start("fixture", "empty", contentHash("")) {
                ByteArrayInputStream(ByteArray(0))
            })
            assertEquals("EMPTY_FILE", coordinator.snapshot()!!.failureType)
            assertTrue(directory.listFiles().orEmpty().none { it.extension == "meta" })

            val unsupported = LocalModelImportCoordinator(LocalModelCatalog(directory,
                LocalModelInspector { capabilities.copy(supportedBackends = setOf(LocalModelBackend.GPU)) }),
                Executor { it.run() })
            assertTrue(unsupported.start("fixture", "cpu", contentHash(bytes.decodeToString())) {
                ByteArrayInputStream(bytes)
            })
            assertEquals("CPU_UNSUPPORTED", unsupported.snapshot()!!.failureType)
            val missingNative = LocalModelImportCoordinator(LocalModelCatalog(directory,
                LocalModelInspector { throw UnsatisfiedLinkError("/private/native-library-path") }),
                Executor { it.run() })
            assertTrue(missingNative.start("fixture", "native", contentHash(bytes.decodeToString())) {
                ByteArrayInputStream(bytes)
            })
            assertEquals("NATIVE_RUNTIME_UNAVAILABLE", missingNative.snapshot()!!.failureType)
            assertFalse(missingNative.snapshot().toString().contains("private/native"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectedExecutorBecomesVisibleTerminalFailureAndDoesNotOpenInput() {
        val directory = Files.createTempDirectory("import-executor-rejection").toFile()
        try {
            val coordinator = LocalModelImportCoordinator(
                LocalModelCatalog(directory, LocalModelInspector { capabilities }),
                Executor { throw java.util.concurrent.RejectedExecutionException("unavailable") })
            assertTrue(coordinator.start("fixture", "v1", contentHash(bytes.decodeToString())) {
                error("Rejected work must not open a source")
            })
            val state = coordinator.snapshot()!!
            assertFalse(state.running)
            assertEquals("RejectedExecutionException", state.failureType)
            assertNull(state.model)
        } finally { directory.deleteRecursively() }
    }
}
