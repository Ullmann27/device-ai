package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Launcher labels of the installed apps for the understanding layer (OwnerUnderstandingLexicon): the same visible
 * launcher activities [AndroidInstalledAppLookup] searches, read once on a background thread and refreshed at most
 * every [MAX_AGE_MILLIS]. Labels only – no package list is kept, nothing is persisted, no permission is added.
 * Until the first read finished the list is empty and the understanding layer works from its fixed names.
 */
internal class InstalledAppLabels(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val appContext = context.applicationContext
    private val loading = AtomicBoolean()
    @Volatile private var cached: List<String> = emptyList()
    @Volatile private var loadedAt = 0L

    fun labels(): List<String> {
        if ((loadedAt == 0L || clock() - loadedAt > MAX_AGE_MILLIS) && loading.compareAndSet(false, true)) {
            Thread({
                try {
                    val pm = appContext.packageManager
                    @Suppress("DEPRECATION")
                    val resolved = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    cached = resolved.asSequence().mapNotNull { info -> runCatching { info.loadLabel(pm).toString() }.getOrNull() }
                        .map { it.filterNot(Char::isISOControl).trim() }.filter { it.length in 2..40 }.distinct().take(MAX_LABELS).toList()
                    loadedAt = clock()
                } catch (_: Exception) {
                    // Unreadable package manager: keep the previous labels and try again on a later turn.
                } finally {
                    loading.set(false)
                }
            }, "continuum-app-labels").apply { isDaemon = true }.start()
        }
        return cached
    }

    companion object {
        const val MAX_LABELS = 400
        const val MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
