package at.heinz.deviceai.localmodel

import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** One process-owned import, retained across Activity recreation. No URI, file
 * name, model bytes or free-form exception text is retained in published state. */
data class LocalModelImportOperation(
    val token: Long,
    val revision: Long,
    val running: Boolean,
    val progress: LocalModelImportSnapshot? = null,
    val model: ImportedLocalModel? = null,
    val failureType: String? = null
)

class LocalModelImportCoordinator(
    private val catalog: LocalModelCatalog,
    private val executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "device-ai-model-import").apply { isDaemon = true }
    }
) {
    private val lock = Any()
    private var current: LocalModelImportOperation? = null
    private var nextToken = 0L
    private var claimedLoadToken: Long? = null
    private val observers = linkedSetOf<Observer>()

    fun snapshot(): LocalModelImportOperation? = synchronized(lock) { current }

    /** False means another import is active; the supplied source is never opened. */
    fun start(id: String, version: String, expectedSha256: String,
              openInput: () -> InputStream): Boolean {
        require(isSafeIdentifier(id) && isSafeIdentifier(version))
        require(expectedSha256.matches(Regex("[a-f0-9]{64}")))
        val initial = synchronized(lock) {
            if (current?.running == true) return false
            LocalModelImportOperation(++nextToken, 0L, true).also { current = it }
        }
        publish(initial)
        try {
            executor.execute {
                val result = runCatching {
                    openInput().use { source ->
                        catalog.import(id, version, source, expectedSha256) { progress ->
                            update(initial.token) { previous -> previous.copy(
                                revision = previous.revision + 1, progress = progress
                            ) }
                        }
                    }
                }
                update(initial.token) { previous -> previous.copy(
                    revision = previous.revision + 1,
                    running = false,
                    model = result.getOrNull(),
                    failureType = result.exceptionOrNull()?.let(::failureType)
                ) }
            }
        } catch (failure: Exception) {
            update(initial.token) { previous -> previous.copy(
                revision = previous.revision + 1, running = false,
                failureType = failureType(failure)
            ) }
        }
        return true
    }

    /** A successful import may trigger at most one automatic runtime load even
     * when old/new Activities briefly overlap. Explicit manual load is separate. */
    fun claimResultForLoading(token: Long): Boolean = synchronized(lock) {
        val value = current
        if (value?.token != token || value.running || value.model == null || claimedLoadToken == token) false
        else { claimedLoadToken = token; true }
    }

    /** Replay the latest state; close when the Activity is destroyed. Observer
     * failures never cancel the import, and detached observers release callbacks. */
    fun observe(callback: (LocalModelImportOperation) -> Unit): AutoCloseable {
        val observer = Observer(callback)
        val latest = synchronized(lock) {
            check(observers.size < 8) { "Too many model-import observers" }
            observers += observer
            current
        }
        latest?.let(observer::deliver)
        return AutoCloseable {
            synchronized(lock) { observers -= observer }
            observer.close()
        }
    }

    private fun update(token: Long, transform: (LocalModelImportOperation) -> LocalModelImportOperation) {
        val value = synchronized(lock) {
            val previous = current
            if (previous?.token != token) return
            transform(previous).also { current = it }
        }
        publish(value)
    }

    private fun publish(value: LocalModelImportOperation) {
        val targets = synchronized(lock) { observers.toList() }
        targets.forEach { it.deliver(value) }
    }

    private fun failureType(failure: Throwable): String = when (failure) {
        is LocalModelImportRejectedException -> failure.rejection.name
        is UnsatisfiedLinkError -> "NATIVE_RUNTIME_UNAVAILABLE"
        is java.io.InterruptedIOException, is InterruptedException -> "INTERRUPTED"
        else -> failure.javaClass.simpleName.take(80).ifBlank { "ImportFailure" }
    }

    private class Observer(callback: (LocalModelImportOperation) -> Unit) {
        private var callback: ((LocalModelImportOperation) -> Unit)? = callback
        private var token = -1L
        private var revision = -1L
        private var lastPhase: LocalModelImportPhase? = null
        private var lastBucket = -1L

        @Synchronized fun deliver(value: LocalModelImportOperation) {
            val target = callback ?: return
            if (value.token < token || value.token == token && value.revision <= revision) return
            val bucket = (value.progress?.copiedBytes ?: 0L) / (64L * 1024L * 1024L)
            val notify = value.token != token || !value.running ||
                value.progress?.phase != lastPhase || bucket != lastBucket
            token = value.token
            revision = value.revision
            if (!notify) return
            lastPhase = value.progress?.phase
            lastBucket = bucket
            runCatching { target(value) }
        }

        @Synchronized fun close() { callback = null }
    }
}
