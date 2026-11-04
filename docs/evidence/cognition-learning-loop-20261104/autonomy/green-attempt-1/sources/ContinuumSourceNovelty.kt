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
        val lines=file.readLines()
        val completed=lines.filter(shaPattern::matches).map(String::lowercase).toSet()
        val started=lines.filter { it.startsWith("?") }.map { it.substring(1).lowercase() }.toSet()
        require(started.all(shaPattern::matches) && started.intersect(completed).isEmpty()) {
            "Invalid source round marker"
        }
        return Journal(completed,started)
    }

    /** Incomplete rounds override acquired evidence. */
    fun knownHashes(base:File):Set<String> = synchronized(lock) {
        val state=readJournal(base)
        val archived=runCatching { ContinuumKnowledgeArchive(base).archivedHashes() }
            .getOrDefault(emptySet())
        val ledger=runCatching {
            FileLearningExperienceStore(File(base,"learning-core-v2")).all()
                .asSequence()
                .filter { it.type==LearningExperienceType.SOURCE_TEXT && it.status!=LearningStatus.REVOKED }
                .mapNotNull { it.sourceRef?.substringAfter("#sha256=","")?.lowercase() }
                .filter(shaPattern::matches)
                .toSet()
        }.getOrDefault(emptySet())
        (state.completed+archived+ledger)-state.started
    }

    /** Persist before archive or ledger. */
    fun markStarted(base:File,sources:List<MentorSource>) = synchronized(lock) {
        require(sources.all { shaPattern.matches(it.sha) }) { "Invalid source hash" }
        val old=readJournal(base)
        val started=(old.started+sources.map { it.sha.lowercase() })-old.completed
        writeJournal(base,Journal(old.completed,started))
    }

    /** Evaluated rounds only; never cancellation. */
    fun markCompleted(base:File,sources:List<MentorSource>) = synchronized(lock) {
        val old=readJournal(base)
        val completed=sources.map { it.sha.lowercase() }.filter(shaPattern::matches).toSet()
        writeJournal(base,Journal(old.completed+completed,old.started-completed))
    }

    private fun writeJournal(base:File,state:Journal) {
        require(state.started.size<=120) { "Source rounds journal full" }
        val completed=state.completed.sorted().takeLast(120-state.started.size)
        val lines=completed+state.started.sorted().map { "?"+it }
        val target=journal(base)
        val parent=requireNotNull(target.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Cannot create source journal directory" }
        val tmp=File(target.parentFile,target.name+".tmp")
        try {
            FileOutputStream(tmp).use {
                it.write(lines.joinToString("\n",postfix="\n").toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            try { Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING) }
            catch (_:java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete()
        }
    }
}
