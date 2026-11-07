package at.heinz.deviceai.learning

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Durable source-round completion state. */
object ContinuumSourceNovelty {
    private val lock=Any()
    private val shaPattern=Regex("[0-9a-fA-F]{64}")
    private fun journal(base:File)=File(base,"learning-core-v2/source-attempts-v1.txt")
    private data class Journal(val completed:Set<String>,val started:Set<String>)

    fun pending(sources:List<MentorSource>,completed:Set<String>):List<MentorSource> =
        sources.filter { shaPattern.matches(it.sha) && it.sha.lowercase() !in completed }
            .distinctBy { it.sha.lowercase() }

    fun completedHashes(base:File):Set<String> = synchronized(lock) {
        readJournal(base).completed
    }

    private fun readJournal(base:File):Journal {
        val file=journal(base)
        if(!file.exists())return Journal(emptySet(),emptySet())
        require(file.length()<=8192) { "Source attempts journal too large" }
        val lines=file.readLines().filter(String::isNotBlank)
        require(lines.all { shaPattern.matches(it) ||
            (it.startsWith("?") && shaPattern.matches(it.substring(1))) }) {
            "Invalid source round marker"
        }
        val completed=lines.filter(shaPattern::matches).map(String::lowercase).toSet()
        val started=lines.filter { it.startsWith("?") }.map { it.substring(1).lowercase() }.toSet()
        require(started.intersect(completed).isEmpty() && completed.size+started.size<=120) {
            "Invalid source round journal"
        }
        return Journal(completed,started)
    }

    /** Incomplete rounds remain eligible. */
    fun knownHashes(base:File):Set<String> = synchronized(lock) {
        val state=readJournal(base)
        val archived=runCatching { ContinuumKnowledgeArchive(base).archivedHashes() }
            .getOrDefault(emptySet())
        val records=runCatching {
            FileLearningExperienceStore(File(base,"learning-core-v2")).all()
        }.getOrDefault(emptyList())
        val excluded=sourceHashes(records.filter { it.status in terminalStatuses })
        (state.completed+archived+sourceHashes(records))-state.started+excluded
    }

    private val terminalStatuses=setOf(LearningStatus.REVOKED,LearningStatus.REJECTED)

    private fun sourceHashes(records:List<LearningExperience>):Set<String> = records.asSequence()
        .filter { it.type==LearningExperienceType.SOURCE_TEXT }
        .mapNotNull { it.sourceRef?.substringAfter("#sha256=","")?.lowercase() }
        .filter(shaPattern::matches).toSet()

    /** Persist before archive or ledger. */
    fun markStarted(base:File,sources:List<MentorSource>) {
        synchronized(lock) {
            if(sources.isEmpty())return@synchronized
            require(sources.all { shaPattern.matches(it.sha) }) { "Invalid source hash" }
            val old=readJournal(base)
            val directory=File(base,"learning-core-v2")
            val excluded=if(directory.isDirectory) sourceHashes(
                FileLearningExperienceStore(directory).all().filter { it.status in terminalStatuses })
                else emptySet()
            val started=(old.started+sources.map { it.sha.lowercase() })-old.completed-excluded
            if(started!=old.started)writeJournal(base,Journal(old.completed,started))
        }
    }

    /** Evaluated rounds only; cancellation waits. */
    fun markCompleted(base:File,sources:List<MentorSource>,cancelled:()->Boolean={false}):Boolean = synchronized(lock) {
        if(cancelled())return@synchronized false
        val completed=sources.map { it.sha.lowercase() }.filter(shaPattern::matches).toSet()
        if(completed.isEmpty())return@synchronized true
        val old=readJournal(base)
        writeJournal(base,Journal(old.completed+completed,old.started-completed),cancelled)
    }

    private fun writeJournal(base:File,state:Journal,cancelled:()->Boolean={false}):Boolean {
        require(state.started.size<=120) { "Source rounds journal full" }
        val completed=state.completed.sorted().takeLast(120-state.started.size)
        val lines=completed+state.started.sorted().map { "?"+it }
        if(cancelled())return false
        val target=journal(base)
        val parent=requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create source journal directory" }
        val tmp=File(target.parentFile,target.name+".tmp")
        return try {
            FileOutputStream(tmp).use {
                it.write(lines.joinToString("\n",postfix="\n").toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            if(cancelled())return false
            try { Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING) }
            catch (_:java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } finally {
            tmp.delete()
        }
    }
}
