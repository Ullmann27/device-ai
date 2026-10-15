package at.heinz.deviceai.learning

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class ModelCheckpoint(
    val sha256:String,
    val createdAt:Long,
    val reason:String,
    val updates:Int
)

/**
 * Content-addressed history of SWB1 encoder snapshots.
 *
 * The learning core archives the currently active weights here BEFORE any
 * model change (training acceptance, import, rollback), so every change can be
 * undone. Files are named by their SHA-256 and re-verified on restore; the
 * bounded index is checksummed and replaced atomically.
 */
class ModelCheckpointStore(private val directory:File) {
    private val index=File(directory,"index-v1.bin")

    fun list():List<ModelCheckpoint> = synchronized(LOCK) { load() }

    /** Archive [bytes] (a valid SWB1) unless the newest entry already holds it. */
    fun archive(bytes:ByteArray,reason:String,now:Long):ModelCheckpoint = synchronized(LOCK) {
        require(now>=0)
        val model=TrainableSubwordEncoder.restore(bytes)
        val hash=ContinuumMemoryDialogue.hash(bytes)
        val label=reason.trim().replace(Regex("[\\p{Cntrl}]")," ").take(MAX_REASON)
            .ifEmpty { "unbenannt" }
        val current=load()
        current.lastOrNull()?.takeIf { it.sha256==hash }?.let { return@synchronized it }
        if(!directory.isDirectory && !directory.mkdirs())error("Checkpoint-Verzeichnis nicht anlegbar")
        val file=fileFor(hash)
        if(!file.exists() || !verifies(file,hash))writeAtomically(file,bytes)
        val entry=ModelCheckpoint(hash,now,label,model.updates)
        val next=(current.filter { it.sha256!=hash }+entry).takeLast(MAX_CHECKPOINTS)
        save(next)
        val keep=next.map { it.sha256 }.toSet()
        directory.listFiles { f -> f.name.endsWith(SUFFIX) }?.forEach { f ->
            if(f.name.removeSuffix(SUFFIX) !in keep)f.delete()
        }
        entry
    }

    /** Verified bytes of a stored checkpoint, or an exception if missing/damaged. */
    fun read(sha256:String):ByteArray = synchronized(LOCK) {
        require(sha256.matches(HASH)) { "Ungültiger Checkpoint-Hash" }
        require(load().any { it.sha256==sha256 }) { "Checkpoint unbekannt" }
        val file=fileFor(sha256)
        require(file.exists() && file.length()<=65536) { "Checkpoint-Datei fehlt" }
        val bytes=file.readBytes()
        require(ContinuumMemoryDialogue.hash(bytes)==sha256) { "Checkpoint beschädigt" }
        TrainableSubwordEncoder.restore(bytes)
        bytes
    }

    private fun verifies(file:File,hash:String)=runCatching {
        file.length()<=65536 && ContinuumMemoryDialogue.hash(file.readBytes())==hash
    }.getOrDefault(false)

    private fun fileFor(hash:String)=File(directory,hash+SUFFIX)

    private fun load():List<ModelCheckpoint> {
        if(!index.exists())return emptyList()
        require(index.length() in 36..65536) { "Checkpoint-Index ungültig" }
        return LearningSnapshot.decode(index.readBytes()) { input ->
            require(input.readInt()==MAGIC)
            val count=input.readInt().also { require(it in 0..MAX_CHECKPOINTS) }
            List(count) {
                val hash=input.readUTF().also { require(it.matches(HASH)) }
                val at=input.readLong().also { require(it>=0) }
                val reason=input.readUTF().also { require(it.length in 1..MAX_REASON) }
                val updates=input.readInt().also { require(it in 0..1_000_000) }
                ModelCheckpoint(hash,at,reason,updates)
            }.also { list -> require(list.map { it.sha256 }.distinct().size==list.size) }
        }
    }

    private fun save(items:List<ModelCheckpoint>) {
        writeAtomically(index,LearningSnapshot.encode { out ->
            out.writeInt(MAGIC);out.writeInt(items.size)
            items.forEach { c ->
                out.writeUTF(c.sha256);out.writeLong(c.createdAt);out.writeUTF(c.reason);out.writeInt(c.updates)
            }
        })
    }

    private fun writeAtomically(target:File,bytes:ByteArray) {
        val temp=File.createTempFile("checkpoint-",".tmp",directory)
        try {
            FileOutputStream(temp).use { it.write(bytes);it.fd.sync() }
            try {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            } catch(_:AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temp.delete() }
    }

    companion object {
        const val MAX_CHECKPOINTS=12
        private const val MAX_REASON=120
        private const val MAGIC=0x434b5031 // "CKP1"
        private const val SUFFIX=".swb1"
        private val HASH=Regex("[a-f0-9]{64}")
        private val LOCK=Any()
    }
}
