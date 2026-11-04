package at.heinz.deviceai.learning

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Durable deduplication of previously examined, consent-gated source revisions. */
object ContinuumSourceNovelty {
    private val lock=Any()
    private val shaPattern=Regex("[0-9a-fA-F]{64}")
    private fun journal(base:File)=File(base,"learning-core-v2/source-attempts-v1.txt")

    fun pending(sources:List<MentorSource>,completed:Set<String>):List<MentorSource> =
        sources.filter { shaPattern.matches(it.sha) && it.sha.lowercase() !in completed }
            .distinctBy { it.sha.lowercase() }

    fun completedHashes(base:File):Set<String> = synchronized(lock) {
        val file=journal(base)
        if(!file.exists())return@synchronized emptySet()
        require(file.length()<=8192) { "Source attempts journal too large" }
        file.readLines().filter(shaPattern::matches).map(String::lowercase).toSet()
    }

    /** Unify attempted, archived and active source hashes across journal compaction. */
    fun knownHashes(base:File):Set<String> = synchronized(lock) {
        val attempts=completedHashes(base)
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
        attempts+archived+ledger
    }

    /** Mark after a completed, evaluated round (accepted or rejected); never after cancellation. */
    fun markCompleted(base:File,sources:List<MentorSource>) = synchronized(lock) {
        val old=completedHashes(base)
        val merged=(old+sources.map { it.sha.lowercase() }.filter(shaPattern::matches))
            .sorted().takeLast(120)
        val target=journal(base)
        target.parentFile?.mkdirs()
        val tmp=File(target.parentFile,target.name+".tmp")
        tmp.writeText(merged.joinToString("\n",postfix="\n"))
        try { Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING) }
        catch (_:java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
