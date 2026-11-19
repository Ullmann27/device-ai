package at.heinz.deviceai.deepclean

import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.briefing.ContinuumSystemFacts
import java.io.File
import java.nio.file.Files

/** Wraps a real file system and counts every mutating call – the proof that a mode never reached a delete. */
internal class CountingFiles(private val delegate: DeepCleanFiles) : DeepCleanFiles by delegate {
    val deleted = mutableListOf<String>()
    var deleteCalls = 0
    /** Runs after each successful delete (to stop or „crash“ a run mid-way). */
    var afterDelete: (String) -> Unit = {}
    override fun delete(root: DeepCleanRoot, relative: String): Boolean {
        deleteCalls++
        val ok = delegate.delete(root, relative)
        if (ok) { deleted += "$root:$relative"; afterDelete("$root:$relative") }
        return ok
    }
}

/** A temporary phone: the app's cache, files and the shared storage as real directories. */
internal class TempPhone(val base: File = Files.createTempDirectory("deepclean-").toFile()) {
    val cache = File(base, "cache").apply { mkdirs() }
    val files = File(base, "files").apply { mkdirs() }
    val shared = File(base, "sdcard").apply { mkdirs() }
    val trashDir = File(base, "trash").apply { mkdirs() }
    val now = System.currentTimeMillis()
    val old = now - 3L * 24 * 3_600_000L

    fun file(root: File, relative: String, bytes: Int = 1000, content: String? = null, modified: Long = old): File {
        val f = File(root, relative)
        f.parentFile.mkdirs()
        if (content != null) f.writeText(content) else f.writeBytes(ByteArray(bytes) { (it % 251).toByte() })
        f.setLastModified(modified)
        return f
    }

    fun roots(shared: Boolean = true): Map<DeepCleanRoot, File> = buildMap {
        put(DeepCleanRoot.OWN_CACHE, cache)
        put(DeepCleanRoot.OWN_FILES, files)
        if (shared) put(DeepCleanRoot.SHARED, this@TempPhone.shared)
    }

    fun fs(shared: Boolean = true) = NioDeepCleanFiles(roots(shared))
}

/** The lane's host on a [TempPhone]: synchronous jobs, a recording chat and a fake Android trash. */
internal class FakeDeepCleanHost(val phone: TempPhone = TempPhone(), var sharedAccess: DeepCleanAccess.Shared = DeepCleanAccess.Shared.GRANTED) : DeepCleanHost {
    val answers = mutableListOf<String>()
    val shownAnswers = mutableListOf<String>()
    val turns = mutableListOf<String>()
    val outcomes = mutableListOf<OwnerLaneOutcome>()
    val activities = mutableListOf<String>()
    val trashCalls = mutableListOf<List<String>>()
    var trashGranted = true
    var cacheGranted = false
    var cacheCalls = 0
    var clearCacheOffered = false
    var thermal: Int? = 0
    var active: Set<String> = emptySet()
    var facts: ContinuumSystemFacts? = null
    var memory: Long? = null
    var unloadCalls = 0
    var freeBytes = 10_000_000_000L
    val totalBytes = 128_000_000_000L
    var nowValue = phone.now
    val store = MemoryDeepCleanStore()
    private var cached: CountingFiles? = null
    private var cachedShared: Boolean? = null

    val counting: CountingFiles get() = files() as CountingFiles

    override val sessionId: String = "chat-1"
    override var ownerTurn: Long = 0
    override val assistantName: String? = "Jarvis"
    override fun ownerName(): String? = "Heinz"
    override fun nowMillis(): Long = nowValue
    override fun acceptOwnerTurn(request: String) { turns += request; ownerTurn++ }
    override fun answer(request: String?, spoken: String, shown: String) { answers += spoken; shownAnswers += shown }
    override fun activity(line: String) { activities += line }
    override fun idle(): Boolean = true
    override fun later(action: () -> Unit) = action()
    override fun <T> runJob(request: String, label: String, work: () -> T, done: (Result<T>) -> Unit) = done(runCatching(work))
    override fun outcome(outcome: OwnerLaneOutcome) { outcomes += outcome }

    override fun files(): DeepCleanFiles {
        val shared = sharedAccess == DeepCleanAccess.Shared.GRANTED
        if (cached == null || cachedShared != shared) {
            // One counting wrapper for the whole test: delete calls of every instance are counted together.
            val previous = cached
            cached = CountingFiles(phone.fs(shared)).also { next ->
                if (previous != null) { next.deleteCalls = previous.deleteCalls; next.deleted += previous.deleted; next.afterDelete = previous.afterDelete }
            }
            cachedShared = shared
        }
        return cached!!
    }

    override fun access() = DeepCleanAccess(sharedAccess, otherAppStats = false, trash = true, clearCacheDialog = clearCacheOffered)
    override fun volume(): DeepCleanVolume = DeepCleanVolume(freeBytes, totalBytes, nowValue)
    override fun activeFiles(): Set<String> = active
    override fun thermalStatus(): Int? = thermal
    override fun systemFacts(): ContinuumSystemFacts? = facts
    override fun ownMemoryBytes(): Long? = memory
    override fun unloadLocalModel(): String? { unloadCalls++; return null }

    /** Moves the files into a trash folder (like Android renames them) when granted; restores them back. */
    override fun trash(items: List<DeepCleanItem>, restore: Boolean, done: (DeepCleanSystemResult) -> Unit) {
        trashCalls += items.map { (if (restore) "RESTORE:" else "") + it.key }
        if (!trashGranted) { done(DeepCleanSystemResult(false, "im Android-Dialog nicht bestätigt")); return }
        items.forEach { item ->
            val original = File(phone.shared, item.relative)
            val trashed = File(phone.trashDir, item.relative.replace('/', '_'))
            if (restore) { trashed.renameTo(original) } else { item.handle = "content://media/external/file/" + item.id; original.renameTo(trashed) }
        }
        // Freed space as Android would measure it later is not simulated here; the lane must not invent it either.
        done(DeepCleanSystemResult(true))
    }

    override fun clearAllAppCaches(done: (DeepCleanSystemResult) -> Unit) {
        cacheCalls++
        done(if (cacheGranted) DeepCleanSystemResult(true) else DeepCleanSystemResult(false, "abgebrochen oder von Android abgelehnt"))
    }

    override fun store(): DeepCleanStore = store

    fun say(director: DeepCleanDirector, text: String): Boolean = director.handle(text)
}
