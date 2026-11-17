package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ContinuumLearningStatusCacheReviewTest {
    @Test fun warmCacheCannotHideRecoveryAndItsFreshAudit() = directory { dir ->
        val model=File(dir,"learning-core-v2/subword-v1.bin")
        assertTrue(model.parentFile.mkdirs())
        model.writeBytes(TrainableSubwordEncoder(101L).save())
        val backup=TrainableSubwordEncoder(103L)
        File(model.parentFile,"subword-v1.lkg").writeBytes(backup.save())
        var clock=1_000L
        val core=ContinuumLearningCore(dir) { clock++ }
        val before=core.status()
        val corrupt=model.readBytes().also { it[40]=(it[40].toInt() xor 0x55).toByte() }
        model.writeBytes(corrupt)
        val recovered=core.status()
        assertEquals(backup.evaluateIntent(ContinuumLearningCore.INTENT_HOLDOUT),recovered.intentHoldout)
        assertEquals(backup.evaluateSemantic(ContinuumLearningCore.SEMANTIC_HOLDOUT),recovered.semanticHoldout)
        assertEquals(before.experiences.audits+1,recovered.experiences.audits)
        assertTrue(recovered.lastAudit!!.startsWith("model-recovery · accepted"))
        assertArrayEquals(corrupt,model.parentFile.listFiles()!!.single { it.name.contains(".corrupt-") }.readBytes())
        val later=ContinuumLearningCore(dir).status()
        assertEquals(recovered,later)
        assertSame(recovered.intentHoldout,later.intentHoldout)
        assertArrayEquals(backup.save(),model.readBytes())
    }

    @Test fun equalWeightsAcrossDirectoriesNeverShareOwnerStatistics() = directory { a -> directory { b ->
        val weights=TrainableSubwordEncoder(107L).save()
        for(dir in listOf(a,b)) {
            val file=File(dir,"learning-core-v2/subword-v1.bin")
            assertTrue(file.parentFile.mkdirs())
            file.writeBytes(weights)
        }
        val coreA=ContinuumLearningCore(a)
        val coreB=ContinuumLearningCore(b)
        assertEquals(OwnerFactChange.ADDED,coreA.confirmOwnerFact("Mein synthetisches Testprojekt heißt Quarz."))
        val first=coreA.status()
        val second=coreB.status()
        assertEquals(1,first.experiences.userFacts)
        assertEquals(0,second.experiences.userFacts)
        assertSame(first.intentHoldout,second.intentHoldout)
        assertSame(first.semanticHoldout,second.semanticHoldout)
        assertEquals(OwnerFactChange.ADDED,coreB.confirmOwnerFact("Mein synthetisches Testprojekt heißt Achat."))
        assertEquals(1,coreB.status().experiences.userFacts)
    } }

    @Test fun concurrentColdReadersPublishOnlyOneMeasurement() = directory { dir ->
        val file=File(dir,"learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs())
        file.writeBytes(TrainableSubwordEncoder(131L).save())
        val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(4)
        try {
            val futures=(1..16).map { pool.submit<ContinuumLearningStatus> {
                assertTrue(start.await(5,TimeUnit.SECONDS))
                ContinuumLearningCore(dir).status()
            } }
            start.countDown()
            val first=futures.first().get(5,TimeUnit.SECONDS)
            futures.forEach { future ->
                val next=future.get(5,TimeUnit.SECONDS)
                assertEquals(first,next)
                assertSame(first.intentHoldout,next.intentHoldout)
                assertSame(first.semanticHoldout,next.semanticHoldout)
            }
        } finally { pool.shutdownNow() }
    }

    @Test fun deletedCheckpointDoesNotKeepPreviousScores() = directory { dir ->
        val file=File(dir,"learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs())
        file.writeBytes(TrainableSubwordEncoder(113L).save())
        val core=ContinuumLearningCore(dir)
        val first=core.status()
        assertTrue(file.delete())
        val absent=core.status()
        val defaults=TrainableSubwordEncoder()
        assertEquals(defaults.evaluateIntent(ContinuumLearningCore.INTENT_HOLDOUT),absent.intentHoldout)
        assertEquals(defaults.evaluateSemantic(ContinuumLearningCore.SEMANTIC_HOLDOUT),absent.semanticHoldout)
        assertNotEquals(first.intentHoldout,absent.intentHoldout)
    }

    @Test fun invalidBackupStillFailsAfterWarmValidCache() = directory { dir ->
        val file=File(dir,"learning-core-v2/subword-v1.bin")
        assertTrue(file.parentFile.mkdirs())
        file.writeBytes(TrainableSubwordEncoder(137L).save())
        val core=ContinuumLearningCore(dir)
        core.status()
        file.writeBytes(byteArrayOf(1,2,3))
        File(file.parentFile,"subword-v1.lkg").writeBytes(byteArrayOf(4,5,6))
        assertThrows(IllegalArgumentException::class.java) { core.status() }
        assertArrayEquals(byteArrayOf(1,2,3),file.readBytes())
    }

    private fun directory(action:(File)->Unit) {
        val dir=Files.createTempDirectory("thermal-cache-review").toFile()
        try { action(dir) } finally { dir.deleteRecursively() }
    }
}
