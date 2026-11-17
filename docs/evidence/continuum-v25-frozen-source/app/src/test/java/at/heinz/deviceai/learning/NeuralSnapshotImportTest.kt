package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.Base64
import java.util.Properties
import java.security.MessageDigest

class NeuralSnapshotImportTest {
    private fun fixture():ByteArray=Base64.getDecoder().decode(
        javaClass.getResourceAsStream("/continuum-neural-swb1.base64")!!.bufferedReader().readText().trim())

    @Test fun javascriptSnapshotHasIdenticalKotlinInferenceAndTokenization() {
        val expected=Properties().apply { this@NeuralSnapshotImportTest.javaClass.getResourceAsStream("/continuum-neural-swb1.properties")!!.use(::load) }
        val bytes=fixture()
        val model=TrainableSubwordEncoder.restore(bytes)
        assertArrayEquals(bytes,model.save())
        assertEquals(expected.getProperty("updates").toInt(),model.updates)
        assertEquals(expected.getProperty("intent").toDouble(),model.intentScore("Wie funktioniert dein Gedächtnis?"),1e-12)
        assertEquals(expected.getProperty("semantic").toDouble(),model.similarity("pflanzen brauchen licht","sonne licht pflanzen"),1e-12)
        assertEquals(expected.getProperty("buckets"),TrainableSubwordEncoder.subwordBuckets("Grüße aus Österreich!").joinToString(","))
        assertEquals(expected.getProperty("sha256"),MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }

    @Test fun importedWeightsPersistWithoutReplacingOwnerFactsOrMappings() {
        val dir=Files.createTempDirectory("neural-import").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            core.recordOwnerFact("Mein privates Projekt heißt Sternenreise")
            core.recordCommandMapping("zeige meine dateien","list_files")
            val report=core.importSnapshot(fixture())
            assertTrue(report.reason,report.accepted)
            assertArrayEquals(fixture(),ContinuumLearningCore(dir).modelSnapshot())
            assertEquals(1,core.status().experiences.userFacts)
            assertEquals(1,core.status().experiences.commandMappings)
            assertFalse(core.importSnapshot(fixture()).accepted)
            val before=core.modelSnapshot()
            val damaged=fixture().also { it[40]=(it[40].toInt() xor 1).toByte() }
            assertThrows(IllegalArgumentException::class.java) { core.importSnapshot(damaged) }
            assertArrayEquals(before,core.modelSnapshot())
            assertFalse(core.importSnapshot(TrainableSubwordEncoder().save()).accepted)
            assertArrayEquals(before,core.modelSnapshot())
        } finally { dir.deleteRecursively() }
    }

    @Test fun localIntentLearningGuardsSemanticTaskSharingTheSameWeights() {
        val dir=Files.createTempDirectory("neural-cross-task").toFile()
        try {
            val core=ContinuumLearningCore(dir)
            assertTrue(core.importSnapshot(fixture()).accepted)
            val before=core.status()
            val report=core.teachIntent("guten morgen lieber freund",0)
            val after=core.status()
            if(report.accepted){
                assertTrue(after.semanticHoldout.correct>=before.semanticHoldout.correct)
                assertTrue(after.semanticHoldout.loss<=before.semanticHoldout.loss+0.015)
            }
        } finally { dir.deleteRecursively() }
    }
}
