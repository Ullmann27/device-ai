package at.heinz.deviceai.systemintelligence

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SystemStorageInspector(
    context: Context,
    private val limits: StorageScanLimits = StorageScanLimits()
) {
    private val app = context.applicationContext
    private val cache = OwnDiagnosticCache(app.cacheDir, app.noBackupFilesDir)

    fun retainDiagnosis(record: DiagnosticCacheRecord): String = cache.retain(record)
    fun diagnosisHistory(): List<DiagnosticCacheRecord> = cache.history()
    fun previewOwnCache(cancelled: () -> Boolean = { false }): OwnCachePreview = cache.preview(cancelled)
    fun cleanupOwnCache(previewId: String, planDigest: String, cancelled: () -> Boolean = { false }): OwnCacheCleanupResult =
        cache.cleanup(previewId, planDigest, cancelled)
    fun restoreOwnCache(undoId: String): OwnCacheCleanupResult = cache.restore(undoId)

    fun analyzeGrantedTree(uri: Uri, cancelled: () -> Boolean = { false }): StorageAnalysis {
        require(DocumentsContract.isTreeUri(uri)) { "TREE_URI_REQUIRED" }
        validateLocalGrant(uri)
        LocalSource(uri, emptyMap(), cancelled).use { source ->
            val id = DocumentsContract.getTreeDocumentId(uri)
            val root = source.metadata(id) ?: error("TREE_UNAVAILABLE")
            check(root.directory) { "TREE_DIRECTORY_REQUIRED" }
            return StorageContentAnalyzer(limits, SystemClock::elapsedRealtime).analyze(source, listOf(root), cancelled)
        }
    }

    fun analyzeSelectedDocuments(uris: List<Uri>, cancelled: () -> Boolean = { false }): StorageAnalysis {
        require(uris.isNotEmpty() && uris.size <= limits.maxEntries) { "DOCUMENT_SELECTION_LIMIT" }
        uris.forEach(::validateLocalGrant)
        val selected = uris.distinct().associateBy { it.toString() }
        LocalSource(null, selected, cancelled).use { source ->
            val documents = selected.keys.map { source.metadata(it) ?: error("DOCUMENT_UNAVAILABLE") }
            require(documents.none { it.directory }) { "SELECT_FILES_OR_USE_TREE" }
            return StorageContentAnalyzer(limits, SystemClock::elapsedRealtime).analyze(source, documents, cancelled)
        }
    }

    private fun validateLocalGrant(uri: Uri) {
        require(uri.scheme == "content" && uri.authority in LOCAL_AUTHORITIES) { "LOCAL_SYSTEM_DOCUMENT_PROVIDER_REQUIRED" }
        val provider = app.packageManager.resolveContentProvider(uri.authority!!, 0)
        check(provider != null && provider.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            "LOCAL_SYSTEM_DOCUMENT_PROVIDER_REQUIRED"
        }
        check(app.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            == PackageManager.PERMISSION_GRANTED) { "DOCUMENT_READ_GRANT_REQUIRED" }
    }

    private inner class LocalSource(
        private val tree: Uri?,
        private val selected: Map<String, Uri>,
        private val cancelled: () -> Boolean
    ) : StorageDocumentSource, AutoCloseable {
        private val signal = CancellationSignal()
        private val deadline = SystemClock.elapsedRealtime() + limits.maxDurationMs
        private val active = AtomicReference<ParcelFileDescriptor?>()
        private val guard = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "continuum-storage-deadline").apply { isDaemon = true }
        }
        init {
            guard.scheduleAtFixedRate({
                if (cancelled() || SystemClock.elapsedRealtime() >= deadline) {
                    signal.cancel()
                    runCatching { active.getAndSet(null)?.close() }
                }
            }, 0, 100, TimeUnit.MILLISECONDS)
        }

        private fun checkActive() {
            check(!cancelled() && !Thread.currentThread().isInterrupted && SystemClock.elapsedRealtime() < deadline) {
                "STORAGE_SCAN_CANCELLED_OR_EXPIRED"
            }
            signal.throwIfCanceled()
            if (tree != null) validateLocalGrant(tree)
        }

        private fun uri(id: String): Uri {
            require(id.length in 1..4_096) { "DOCUMENT_ID_INVALID" }
            return if (tree == null) selected[id] ?: error("DOCUMENT_OUTSIDE_SELECTION")
            else DocumentsContract.buildDocumentUriUsingTree(tree, id)
        }

        override fun children(parentId: String, limit: Int): List<StorageDocument> {
            checkActive()
            check(tree != null) { "TREE_REQUIRED" }
            val result = mutableListOf<StorageDocument>()
            val target = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            app.contentResolver.query(target, PROJECTION, null, null, null, signal)?.use { cursor ->
                while (result.size < limit && cursor.moveToNext()) {
                    checkActive()
                    result += document(cursor, null)
                }
            } ?: error("DIRECTORY_UNAVAILABLE")
            return result
        }

        override fun metadata(id: String): StorageDocument? {
            checkActive()
            val target = uri(id)
            validateLocalGrant(target)
            return app.contentResolver.query(target, PROJECTION, null, null, null, signal)?.use { cursor ->
                checkActive()
                if (cursor.moveToFirst()) document(cursor, if (tree == null) id else null) else null
            }
        }

        override fun open(id: String): InputStream {
            checkActive()
            val target = uri(id)
            validateLocalGrant(target)
            val descriptor = app.contentResolver.openFileDescriptor(target, "r", signal)
                ?: error("DOCUMENT_UNAVAILABLE")
            if (descriptor.statSize < 0) {
                descriptor.close()
                error("NON_REGULAR_DOCUMENT_UNSUPPORTED")
            }
            active.set(descriptor)
            if (signal.isCanceled) {
                active.compareAndSet(descriptor, null)
                descriptor.close()
                error("STORAGE_SCAN_CANCELLED_OR_EXPIRED")
            }
            return object : FilterInputStream(ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
                override fun close() {
                    active.compareAndSet(descriptor, null)
                    super.close()
                }
            }
        }

        private fun document(cursor: android.database.Cursor, selectedId: String?): StorageDocument {
            fun text(column: String): String = cursor.getString(cursor.getColumnIndexOrThrow(column)).orEmpty()
            fun number(column: String): Long? {
                val index = cursor.getColumnIndex(column)
                return if (index < 0 || cursor.isNull(index)) null else cursor.getLong(index).takeIf { it >= 0 }
            }
            val id = selectedId ?: text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            require(id.length in 1..4_096) { "DOCUMENT_ID_INVALID" }
            val mime = text(DocumentsContract.Document.COLUMN_MIME_TYPE).take(200)
            val name = text(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                .filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }.take(120)
            val flags = number(DocumentsContract.Document.COLUMN_FLAGS) ?: 0
            return StorageDocument(id, name, mime, number(DocumentsContract.Document.COLUMN_SIZE),
                number(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                directory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                virtual = flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT.toLong() != 0L,
                stableKey = "${tree?.authority ?: selected[selectedId]?.authority}/${text(DocumentsContract.Document.COLUMN_DOCUMENT_ID)}")
        }

        override fun close() {
            guard.shutdownNow()
            signal.cancel()
            runCatching { active.getAndSet(null)?.close() }
        }
    }

    companion object {
        private val LOCAL_AUTHORITIES = setOf("com.android.externalstorage.documents",
            "com.android.providers.downloads.documents", "com.android.providers.media.documents")
        private val PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS)
        fun localTreePickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
        fun localDocumentsPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
}
