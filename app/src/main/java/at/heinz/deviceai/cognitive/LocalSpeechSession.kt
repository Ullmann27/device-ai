package at.heinz.deviceai.cognitive

/** Main-thread lifecycle shared by the Android recognizer and host behavioral tests.
 * Only a token, callbacks and recognizer handle are retained; no audio or transcript is stored. */
internal class LocalSpeechSession(
    private val platform: Platform,
    private val nowNanos: () -> Long = System::nanoTime
) {
    interface Recognizer {
        fun start()
        fun cancel()
        fun destroy()
    }
    interface Callbacks {
        fun ready()
        fun result(text: String)
        fun failed(failure: LocalSpeechInputPolicy.Failure)
    }
    interface Platform {
        fun available(): Boolean
        fun microphoneGranted(): Boolean
        fun create(callbacks: Callbacks): Recognizer
        fun scheduleTimeout(task: () -> Unit): () -> Unit
        /** End the platform lifetime; pooled native recognizers may be destroyed here. */
        fun shutdown() {}
    }
    private data class Active(
        val token: Long,
        val onState: (Boolean) -> Unit,
        val onText: (String) -> Unit,
        val onError: (String) -> Unit,
        val onFailure: ((LocalSpeechInputPolicy.Failure) -> Unit)?,
        var recognizer: Recognizer? = null,
        var cancelTimeout: (() -> Unit)? = null
    )
    private val turn = EphemeralSpeechTurn(nowNanos)
    private var active: Active? = null
    private var revision = 0L

    fun cancel() {
        revision++
        cancelCurrent()
    }

    /** Full recognition-scope end (hands-free stop / Activity pause). */
    fun shutdown() {
        revision++
        cancelCurrent()
        runCatching { platform.shutdown() }
    }

    private fun cancelCurrent() {
        val old = active
        active = null
        turn.cancel()
        old?.let { release(it, cancel = true) }
    }

    /** [onFailure] (optional) receives the structured reason before the owner-facing [onError] text. */
    fun start(
        onState: (Boolean) -> Unit,
        onText: (String) -> Unit,
        onError: (String) -> Unit,
        onFailure: ((LocalSpeechInputPolicy.Failure) -> Unit)? = null
    ) {
        val requestedRevision = ++revision
        cancelCurrent()
        if (revision != requestedRevision) return
        val availability = runCatching { platform.available() }.getOrDefault(false)
        val permission = runCatching { platform.microphoneGranted() }.getOrDefault(false)
        if (!permission || !availability) {
            onState(false)
            if (revision == requestedRevision) {
                val failure = if (!availability) LocalSpeechInputPolicy.Failure.SERVICE_UNAVAILABLE
                    else LocalSpeechInputPolicy.Failure.PERMISSION
                onFailure?.invoke(failure)
                onError(LocalSpeechInputPolicy.message(failure))
            }
            return
        }
        val current = Active(turn.start(), onState, onText, onError, onFailure)
        active = current
        val callbacks = object : Callbacks {
            override fun ready() { if (isCurrent(current) && turn.isActive(current.token)) current.onState(true) }
            override fun result(text: String) {
                if (!isCurrent(current)) return
                if (!runCatching { platform.microphoneGranted() }.getOrDefault(false)) {
                    fail(current, LocalSpeechInputPolicy.Failure.PERMISSION)
                    return
                }
                val timely = turn.isActive(current.token)
                val accepted = turn.accept(current.token, text)
                complete(current, cancel = false) {
                    if (accepted != null) current.onText(accepted)
                    else {
                        val failure = if (timely) LocalSpeechInputPolicy.Failure.NO_MATCH else LocalSpeechInputPolicy.Failure.TIMEOUT
                        current.onFailure?.invoke(failure)
                        current.onError(LocalSpeechInputPolicy.message(failure))
                    }
                }
            }
            override fun failed(failure: LocalSpeechInputPolicy.Failure) { if (isCurrent(current)) fail(current, failure) }
        }
        try {
            val recognizer = platform.create(callbacks)
            // A platform may synchronously deliver a terminal callback during setup.
            if (!isCurrent(current)) { runCatching { recognizer.destroy() }; return }
            current.recognizer = recognizer
            val cancelTimeout = platform.scheduleTimeout { if (isCurrent(current)) fail(current, LocalSpeechInputPolicy.Failure.TIMEOUT) }
            if (!isCurrent(current)) { cancelTimeout(); return }
            current.cancelTimeout = cancelTimeout
            // Wait for Android readiness.
            if (isCurrent(current)) recognizer.start()
        } catch (_: SecurityException) {
            if (isCurrent(current)) fail(current, LocalSpeechInputPolicy.Failure.PERMISSION)
        } catch (_: RuntimeException) {
            if (isCurrent(current)) fail(current, LocalSpeechInputPolicy.Failure.START_FAILED)
        }
    }

    private fun isCurrent(current: Active) = active === current && turn.isCurrent(current.token)
    private fun fail(current: Active, failure: LocalSpeechInputPolicy.Failure) = complete(current, cancel = true) {
        current.onFailure?.invoke(failure)
        current.onError(LocalSpeechInputPolicy.message(failure))
    }
    private fun complete(current: Active, cancel: Boolean, deliver: () -> Unit) {
        if (active !== current) return
        val completedRevision = ++revision
        active = null
        turn.cancel()
        release(current, cancel)
        // onState(false) is owner code and may synchronously start a replacement turn.
        if (active == null && revision == completedRevision) deliver()
    }
    /** Releases this turn lease. Platform implementations may keep native recognizer state warm. */
    private fun release(current: Active, cancel: Boolean) {
        current.cancelTimeout?.let { runCatching(it) }; current.cancelTimeout = null
        val recognizer = current.recognizer; current.recognizer = null
        if (cancel) runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        current.onState(false)
    }
}
