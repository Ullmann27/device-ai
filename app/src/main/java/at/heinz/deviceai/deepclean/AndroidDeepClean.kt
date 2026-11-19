package at.heinz.deviceai.deepclean

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import at.heinz.deviceai.AgentActivity
import at.heinz.deviceai.OwnerDeviceLibrary
import at.heinz.deviceai.OwnerLaneOutcome
import at.heinz.deviceai.OwnerLibraryAccess
import at.heinz.deviceai.OwnerMissionLane
import at.heinz.deviceai.briefing.ContinuumBriefingHost
import at.heinz.deviceai.briefing.ContinuumSystemFacts
import java.io.File

/** The lane's records in app-private, non-backed-up storage; each write replaces its file atomically. */
internal class AndroidDeepCleanStore(context: Context) : DeepCleanStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "deepclean")

    override fun read(name: String): String? = runCatching { File(directory, "$name.json").takeIf { it.isFile }?.readText() }.getOrNull()

    @Synchronized override fun write(name: String, text: String?) {
        val target = File(directory, "$name.json")
        if (text == null) { target.delete(); return }
        check(directory.isDirectory || directory.mkdirs()) { "Speicherort der Bereinigung nicht verfügbar" }
        val temporary = File.createTempFile("deepclean-", ".tmp", directory)
        try {
            temporary.writeText(text)
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }
}

/**
 * The storage lane on the phone. Chat, owner turn and the single busy lane are the briefing adapter's ([chat]);
 * this class adds only the device boundaries: the app's own folders and – with Android's „Zugriff auf alle Dateien“
 * – the shared storage, Android's volume figures (StatFs), running downloads (DownloadManager), and Android's own
 * dialogs for the trash (MediaStore.createTrashRequest) and the cache of all apps (ACTION_CLEAR_APP_CACHE). No
 * shell, no Shizuku, no root, no new permission.
 */
internal class AndroidDeepCleanHost(
    private val activity: AgentActivity,
    private val chat: ContinuumBriefingHost,
    private val stage: () -> Unit,
    private val unloadModel: () -> String?,
    private val tone: () -> Boolean
) : DeepCleanHost {
    lateinit var director: DeepCleanDirector
    private val store by lazy { AndroidDeepCleanStore(activity) }
    private var pendingTrash: ((DeepCleanSystemResult) -> Unit)? = null
    private var pendingCaches: ((DeepCleanSystemResult) -> Unit)? = null

    override val sessionId: String get() = chat.sessionId
    override val ownerTurn: Long get() = chat.ownerTurn
    override val assistantName: String? get() = chat.assistantName
    override fun ownerName(): String? = chat.ownerName()
    override fun personalTone(): Boolean = tone()
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun acceptOwnerTurn(request: String) {
        // The step a mission is handing over right now is already shown as the owner's sentence.
        if (activity.ownerMission.laneTurn(OwnerMissionLane.DEEPCLEAN)) return
        chat.acceptOwnerTurn(request)
    }
    override fun answer(request: String?, spoken: String, shown: String) = chat.answer(request, spoken, shown)
    override fun activity(line: String) = chat.activity(line)
    override fun idle(): Boolean = chat.idle()
    override fun later(action: () -> Unit) = chat.later(action)
    override fun <T> runJob(request: String, label: String, work: () -> T, done: (Result<T>) -> Unit) = chat.runJob(request, label, work, done)
    override fun stageChanged() = stage()
    override fun outcome(outcome: OwnerLaneOutcome) = activity.ownerMission.laneOutcome(OwnerMissionLane.DEEPCLEAN, outcome)

    private fun sharedGranted(): Boolean = OwnerDeviceLibrary(activity).access() == OwnerLibraryAccess.GRANTED

    override fun files(): DeepCleanFiles {
        val roots = LinkedHashMap<DeepCleanRoot, File>()
        roots[DeepCleanRoot.OWN_CACHE] = activity.cacheDir
        activity.externalCacheDir?.let { roots[DeepCleanRoot.OWN_EXTERNAL_CACHE] = it }
        roots[DeepCleanRoot.OWN_FILES] = activity.filesDir
        @Suppress("DEPRECATION")
        if (sharedGranted()) roots[DeepCleanRoot.SHARED] = Environment.getExternalStorageDirectory()
        return NioDeepCleanFiles(roots)
    }

    override fun access(): DeepCleanAccess {
        val shared = when (OwnerDeviceLibrary(activity).access()) {
            OwnerLibraryAccess.GRANTED -> DeepCleanAccess.Shared.GRANTED
            OwnerLibraryAccess.MISSING -> DeepCleanAccess.Shared.MISSING
            OwnerLibraryAccess.UNSUPPORTED -> DeepCleanAccess.Shared.UNSUPPORTED
        }
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        // PACKAGE_USAGE_STATS is not declared: other apps' storage statistics are not available, and this says so.
        return DeepCleanAccess(shared, otherAppStats = false, trash = modern,
            clearCacheDialog = modern && shared == DeepCleanAccess.Shared.GRANTED)
    }

    override fun openFileAccessSettings(): Boolean =
        OwnerDeviceLibrary(activity).accessSettings().any { runCatching { activity.startActivity(it) }.isSuccess }

    override fun openStorageSettings(): Boolean = runCatching {
        activity.startActivity(Intent(StorageManager.ACTION_MANAGE_STORAGE)); true
    }.getOrDefault(false)

    /** The data volume, measured by StatFs right now. */
    override fun volume(): DeepCleanVolume? = runCatching {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        if (total > 0 && free in 0..total) DeepCleanVolume(free, total, System.currentTimeMillis()) else null
    }.getOrNull()

    /** Downloads that DownloadManager reports as pending, running or paused – never touched. */
    override fun activeFiles(): Set<String> = runCatching {
        val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return emptySet()
        @Suppress("DEPRECATION") val root = Environment.getExternalStorageDirectory().canonicalPath + "/"
        val keys = HashSet<String>()
        manager.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or
                DownloadManager.STATUS_PAUSED))?.use { cursor ->
            val column = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            while (cursor.moveToNext() && column >= 0) {
                val path = cursor.getString(column)?.let { Uri.parse(it).path } ?: continue
                val canonical = runCatching { File(path).canonicalPath }.getOrNull() ?: continue
                if (canonical.startsWith(root)) keys += DeepCleanRoot.SHARED.name + ":" + canonical.removePrefix(root)
            }
        }
        keys
    }.getOrDefault(emptySet())

    override fun thermalStatus(): Int? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) null else
        runCatching { (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.currentThermalStatus }.getOrNull()

    override fun systemFacts(): ContinuumSystemFacts? = runCatching { chat.systemFacts() }.getOrNull()
    override fun ownMemoryBytes(): Long? = runCatching { Debug.getPss() * 1024L }.getOrNull()?.takeIf { it > 0 }
    override fun unloadLocalModel(): String? = unloadModel()

    /** The MediaStore item of a shared file, found by its path (readable with all-files access). */
    private fun mediaUri(item: DeepCleanItem): Uri? {
        if (item.root != DeepCleanRoot.SHARED || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        @Suppress("DEPRECATION") val path = File(Environment.getExternalStorageDirectory(), item.relative).absolutePath
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        @Suppress("DEPRECATION") val data = MediaStore.MediaColumns.DATA
        return runCatching {
            activity.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "$data = ?", arrayOf(path), null)?.use { cursor ->
                if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
            }
        }.getOrNull()
    }

    override fun trash(items: List<DeepCleanItem>, restore: Boolean, done: (DeepCleanSystemResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) { done(DeepCleanSystemResult(false, "Den Papierkorb gibt es erst ab Android 11")); return }
        val uris = items.mapNotNull { item ->
            val uri = if (restore) item.handle?.let(Uri::parse) else mediaUri(item)
            if (uri == null) item.note = "nicht im Medienindex von Android – nicht angefasst" else item.handle = uri.toString()
            uri
        }.take(MAX_URIS)
        if (uris.isEmpty()) { done(DeepCleanSystemResult(false, "Android kennt diese Dateien nicht in seinem Medienindex")); return }
        try {
            val request = MediaStore.createTrashRequest(activity.contentResolver, uris, !restore)
            pendingTrash = done
            activity.startIntentSenderForResult(request.intentSender, TRASH_REQUEST, null, 0, 0, 0)
        } catch (error: Exception) {
            pendingTrash = null
            done(DeepCleanSystemResult(false, "Android hat den Dialog nicht geöffnet (${error.javaClass.simpleName})"))
        }
    }

    override fun clearAllAppCaches(done: (DeepCleanSystemResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !sharedGranted()) {
            done(DeepCleanSystemResult(false, "dafür braucht Continuum „Zugriff auf alle Dateien“")); return
        }
        try {
            pendingCaches = done
            activity.startActivityForResult(Intent(StorageManager.ACTION_CLEAR_APP_CACHE), CACHE_REQUEST)
        } catch (error: Exception) {
            pendingCaches = null
            done(DeepCleanSystemResult(false, "Android hat den Dialog nicht geöffnet"))
        }
    }

    /** Activity result of one of the lane's system dialogs; true = it was the lane's. */
    fun activityResult(requestCode: Int, resultCode: Int): Boolean {
        when (requestCode) {
            TRASH_REQUEST -> {
                val done = pendingTrash ?: return true
                pendingTrash = null
                done(if (resultCode == Activity.RESULT_OK) DeepCleanSystemResult(true) else DeepCleanSystemResult(false, "im Android-Dialog nicht bestätigt"))
                return true
            }
            CACHE_REQUEST -> {
                val done = pendingCaches ?: return true
                pendingCaches = null
                done(when (resultCode) {
                    Activity.RESULT_OK -> DeepCleanSystemResult(true)
                    Activity.RESULT_CANCELED -> DeepCleanSystemResult(false, "abgebrochen oder von Android abgelehnt")
                    else -> DeepCleanSystemResult(false, "Android meldete einen Fehler beim Leeren")
                })
                return true
            }
        }
        return false
    }

    override fun openPanel() { DeepCleanPanel.show(activity, director, this) }
    override fun store(): DeepCleanStore = store

    companion object {
        const val TRASH_REQUEST = 2291
        const val CACHE_REQUEST = 2292
        /** Android 16 accepts at most 2000 URIs per request (MediaStore docs, BAKLAVA). */
        const val MAX_URIS = 2000

        fun create(activity: AgentActivity, chat: ContinuumBriefingHost, stage: () -> Unit, unloadModel: () -> String?,
                   tone: () -> Boolean): Pair<DeepCleanDirector, AndroidDeepCleanHost> {
            val host = AndroidDeepCleanHost(activity, chat, stage, unloadModel, tone)
            val director = DeepCleanDirector(host)
            host.director = director
            return director to host
        }
    }
}
