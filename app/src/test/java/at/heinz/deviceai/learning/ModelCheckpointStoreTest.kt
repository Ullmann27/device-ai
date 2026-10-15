package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelCheckpointStoreTest {
    private fun model(seed:Long)=TrainableSubwordEncoder(seed).save()

    private fun withDir(block:(File)->Any?) {
        val dir=Files.createTempDirectory("continuum-checkpoints").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }

    @Test fun archiveIsContentAddressedVerifiedAndIdempotent() = withDir { dir ->
        val store=ModelCheckpointStore(dir)
        val bytes=model(1)
        val first=store.archive(bytes,"vor test",10)
        assertEquals(ContinuumMemoryDialogue.hash(bytes),first.sha256)
        assertEquals(first,store.archive(bytes,"nochmal",20))
        assertEquals(1,store.list().size)
        assertArrayEquals(bytes,store.read(first.sha256))
        assertArrayEquals(bytes,ModelCheckpointStore(dir).read(first.sha256))
    }

    @Test fun historyIsBoundedAndPrunedFilesAreRemoved() = withDir { dir ->
        val store=ModelCheckpointStore(dir)
        val all=(1L..(ModelCheckpointStore.MAX_CHECKPOINTS+3)).map { seed -> store.archive(model(seed),"s$seed",seed) }
        val kept=store.list()
        assertEquals(ModelCheckpointStore.MAX_CHECKPOINTS,kept.size)
        assertEquals(all.takeLast(ModelCheckpointStore.MAX_CHECKPOINTS).map { it.sha256 },kept.map { it.sha256 })
        assertEquals(ModelCheckpointStore.MAX_CHECKPOINTS,dir.listFiles { f -> f.name.endsWith(".swb1") }!!.size)
        assertThrows(IllegalArgumentException::class.java) { store.read(all.first().sha256) }
    }

    @Test fun tamperedOrUnknownCheckpointsAreRejected() = withDir { dir ->
        val store=ModelCheckpointStore(dir)
        val entry=store.archive(model(5),"vor test",1)
        assertThrows(IllegalArgumentException::class.java) { store.read("0".repeat(64)) }
        assertThrows(IllegalArgumentException::class.java) { store.read("../../etc") }
        val file=File(dir,entry.sha256+".swb1")
        val bytes=file.readBytes();bytes[100]=(bytes[100].toInt() xor 1).toByte();file.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { store.read(entry.sha256) }
    }

    @Test fun damagedIndexFailsClosedInsteadOfForgettingHistory() = withDir { dir ->
        val store=ModelCheckpointStore(dir)
        store.archive(model(7),"vor test",1)
        val index=File(dir,"index-v1.bin")
        val bytes=index.readBytes();bytes[5]=(bytes[5].toInt() xor 1).toByte();index.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { store.list() }
        assertThrows(IllegalArgumentException::class.java) { store.archive(model(8),"x",2) }
    }

    @Test fun invalidModelsAreNeverArchived() = withDir { dir ->
        val store=ModelCheckpointStore(dir)
        assertThrows(IllegalArgumentException::class.java) { store.archive(ByteArray(32960),"x",1) }
        assertTrue(store.list().isEmpty())
    }

    @Test fun everyAcceptedModelChangeIsReversible() = withDir { dir ->
        var clock=1_000L
        val core=ContinuumLearningCore(dir) { clock++ }
        val initial=core.modelSnapshot()
        val report=core.bootstrapOfflineCurriculum()!!
        assertTrue("bundled curriculum should be accepted on a fresh model",report.accepted)
        val trained=core.modelSnapshot()
        assertFalse(initial.contentEquals(trained))
        val history=core.checkpoints()
        assertEquals(ContinuumMemoryDialogue.hash(initial),history.last().sha256)
        assertEquals("vor offline-bootstrap",history.last().reason)

        val rollback=core.rollbackToPrevious()!!
        assertTrue(rollback.accepted)
        assertArrayEquals(initial,core.modelSnapshot())
        // The rollback archived the trained state, so it can itself be undone.
        val undo=core.rollbackTo(ContinuumMemoryDialogue.hash(trained))
        assertTrue(undo.accepted)
        assertArrayEquals(trained,ContinuumLearningCore(dir).modelSnapshot())
        assertEquals(3,core.status().experiences.audits)
        val audits=core.trainingAudits()
        assertEquals(3,audits.size)
        assertTrue(audits.first().startsWith("rollback") && audits.last().startsWith("offline-bootstrap"))
    }

    @Test fun rollbackWithoutHistoryOrToActiveStateChangesNothing() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        assertNull(core.rollbackToPrevious())
        core.bootstrapOfflineCurriculum()
        val active=core.modelSnapshot()
        core.rollbackToPrevious()
        val again=core.rollbackTo(ContinuumMemoryDialogue.hash(core.modelSnapshot()))
        assertFalse(again.accepted)
        assertFalse(active.contentEquals(core.modelSnapshot()))
    }

    @Test fun modelIsNotChangedWhenTheRecoveryPointCannotBeWritten() = withDir { dir ->
        val core=ContinuumLearningCore(dir)
        val base=File(dir,"learning-core-v2").apply { mkdirs() }
        File(base,"checkpoints").writeText("blocks the checkpoint directory")
        val before=core.modelSnapshot()
        assertThrows(IllegalStateException::class.java) { core.bootstrapOfflineCurriculum() }
        assertArrayEquals(before,core.modelSnapshot())
        assertFalse(File(base,"subword-v1.bin").exists())
    }
}
