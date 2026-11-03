package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the real runtime's admission/cleanup without initializing native weights. */
class LocalModelReloadLifecycleTest {
    @Before fun requireCompatibleHostJvm() {
        // The published LiteRT 0.18 host classes are Java 21 bytecode. Android
        // builds still use JDK17/D8; this host-only native-type test needs JDK21.
        val hostJava = System.getProperty("java.specification.version")?.toIntOrNull() ?: 0
        assumeTrue("LiteRT 0.18 lifecycle host tests require JDK21", hostJava >= 21)
    }

    @Test fun failedRuntimeWithRetainedModelRefusesReloadUntilExplicitUnload() {
        val directory = Files.createTempDirectory("local-reload-test").toFile()
        try {
            val runtime = LiteRtLmLocalModelRuntime(
                LocalModelCatalog(directory.resolve("models"), LocalModelInspector { error("No native inspection") }),
                directory.resolve("cache")
            )
            val retained = ImportedLocalModel(
                "test-model", "1", "0".repeat(64), 1,
                LocalModelCapabilities(true, false, false, false, true, 16_384, null,
                    setOf(LocalModelBackend.CPU))
            )
            // A failed conversation release leaves its loaded identity/native engine
            // retained. Seed that snapshot directly; no JNI or inference is claimed.
            field(runtime, "loadedModel", retained)
            field(runtime, "state", LocalModelState.FAILED)
            var reloadFailure: Throwable? = null
            val refused = CountDownLatch(1)
            runtime.load(retained.id, retained.version) {
                reloadFailure = it.exceptionOrNull()
                refused.countDown()
            }
            assertTrue(refused.await(2, TimeUnit.SECONDS))
            assertNotNull(reloadFailure)
            assertEquals("LOCAL_MODEL_UNLOAD_REQUIRED", reloadFailure!!.message)
            assertEquals(LocalModelState.FAILED, runtime.state)
            assertSame(retained, runtime.loadedModel)

            val unloaded = CountDownLatch(1)
            runtime.unload {
                assertTrue(it.isSuccess)
                unloaded.countDown()
            }
            assertTrue(unloaded.await(2, TimeUnit.SECONDS))
            assertEquals(LocalModelState.EMPTY, runtime.state)

            // After successful explicit unload, a normal load attempt is allowed.
            // There is deliberately no imported file in this host-only fixture.
            val retried = CountDownLatch(1)
            runtime.load(retained.id, retained.version) {
                reloadFailure = it.exceptionOrNull()
                retried.countDown()
            }
            assertTrue(retried.await(2, TimeUnit.SECONDS))
            assertEquals("Imported model is missing or failed its integrity check.", reloadFailure!!.message)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun failureBeforeAnyModelWasLoadedCanRetryWithoutUnload() {
        val directory = Files.createTempDirectory("local-empty-retry-test").toFile()
        try {
            val runtime = LiteRtLmLocalModelRuntime(
                LocalModelCatalog(directory.resolve("models"), LocalModelInspector { error("No native inspection") }),
                directory.resolve("cache")
            )
            field(runtime, "state", LocalModelState.FAILED)
            var failure: Throwable? = null
            val retried = CountDownLatch(1)
            runtime.load("test-model", "1") {
                failure = it.exceptionOrNull()
                retried.countDown()
            }
            assertTrue(retried.await(2, TimeUnit.SECONDS))
            assertEquals("Imported model is missing or failed its integrity check.", failure!!.message)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun field(runtime: LiteRtLmLocalModelRuntime, name: String, value: Any) {
        LiteRtLmLocalModelRuntime::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(runtime, value)
        }
    }
}
