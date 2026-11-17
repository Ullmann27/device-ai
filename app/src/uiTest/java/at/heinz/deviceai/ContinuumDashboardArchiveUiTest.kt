package at.heinz.deviceai

import android.content.ContextWrapper
import android.os.Looper
import at.heinz.deviceai.diagnostics.ContinuumDiagnostics
import at.heinz.deviceai.learning.ContinuumKnowledgeArchive
import at.heinz.deviceai.learning.ContinuumLearningCore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumDashboardArchiveUiTest {
    @Test fun actualArchiveCountsReachDashboardAndCacheRefreshWithoutChangingFacts() {
        val dir = Files.createTempDirectory("dashboard-archive").toFile()
        try {
            val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getFilesDir() = dir
            }
            val core = ContinuumLearningCore(dir)
            core.confirmOwnerFact("Mein Prüfprojekt heißt Sternenreise.")
            val facts = core.activeOwnerFacts()
            val weights = core.modelSnapshot()
            val archive = ContinuumKnowledgeArchive(dir)
            fun add(index: Int) = archive.save(ContinuumKnowledgeArchive.Kind.WIKIPEDIA_EXCERPT,
                "Robotik $index", "https://de.wikipedia.org/wiki/Robotik",
                "Wikipedia-Mitwirkende · CC BY-SA 4.0",
                "Robotik verbindet Mechanik und Sensoren. Dieser öffentliche Auszug hat Revision $index.")
            val first = add(1)
            add(2)
            val collector = ContinuumDashboardCollector(context,
                ContinuumDiagnostics(java.io.File(dir, "qa-diagnostics.tsv"), "host-qa"))
            val live = ContinuumDashboardCollector.Live(null, false, false, "OFF",
                false, false, false, false, false, false, false, false, false, emptyList())
            val snapshot = collector.collect(live)
            assertEquals("A research scheduler must not appear as a synchronization service",
                at.heinz.deviceai.ui.ContinuumDashboardState.UNKNOWN, snapshot.storage.syncLabel)
            assertTrue("The separately labelled research state remains available",
                snapshot.storage.researchLabel.contains("Forschung", ignoreCase = true))
            val sync = at.heinz.deviceai.ui.JarvisCockpitPanels.statusSegments(snapshot)
                .single { it.kind == at.heinz.deviceai.ui.JarvisCockpitPanels.Seg.SYNC }
            assertEquals("Continuum-Sync: —", sync.text)
            assertFalse("An unconnected synchronization source has no green success badge", sync.ok)
            assertEquals(2, collector.collect(live).storage.totalSources)
            assertEquals(2, collector.collect(live).storage.activeSources)
            add(3)
            assertEquals("Short refresh uses bounded cache", 2, collector.collect(live).storage.totalSources)
            shadowOf(Looper.getMainLooper()).idleFor(31, TimeUnit.SECONDS)
            assertEquals("Archive additions appear after cache expiry", 3, collector.collect(live).storage.totalSources)
            assertEquals(facts, core.activeOwnerFacts())
            assertArrayEquals(weights, core.modelSnapshot())
            assertTrue(archive.search("Robotik").any { it.source.sha256 == first.sha256 })
        } finally { dir.deleteRecursively() }
    }
}
