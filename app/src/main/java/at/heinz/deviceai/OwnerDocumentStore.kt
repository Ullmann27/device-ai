package at.heinz.deviceai

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * The owner's Download folder and the hand-over to his own apps. Android 10+: MediaStore.Downloads (the same way
 * the evaluation report is exported) – no storage permission, the file belongs to the owner and stays after an
 * uninstall. Older Android without a storage permission: the app's own Download directory, and the reply says so.
 * Nothing here sends anything: opening and mailing only start the owner's own app with the content.
 */
internal class OwnerDocumentStore(private val context: Context) {
    /** true when this app already wrote a file of that name into Download (other apps' files are renamed by Android). */
    fun nameTaken(displayName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return File(legacyDirectory(), displayName).exists()
        return runCatching {
            context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), MediaStore.MediaColumns.DISPLAY_NAME + " = ?",
                arrayOf(displayName), null)?.use { cursor ->
                var found = false
                while (cursor.moveToNext()) {
                    val path = cursor.getString(0).orEmpty().trimEnd('/')
                    if (path.equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)) found = true
                }
                found
            } ?: false
        }.getOrDefault(false)
    }

    /** Writes the complete file or nothing; throws with the reason when it cannot be written. */
    fun save(displayName: String, mime: String, bytes: ByteArray): OwnerSavedFile {
        check(bytes.isNotEmpty()) { "Das Dokument ist leer." }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val directory = legacyDirectory().apply { mkdirs() }
            val file = File(directory, displayName)
            check(file.createNewFile()) { "Die Datei existiert bereits." }
            file.writeBytes(bytes)
            return OwnerSavedFile(displayName, "App-Ordner Android/data/${context.packageName}/files/Download", null, mime)
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        })) { "Android hat keinen Eintrag im Download-Ordner angelegt." }
        try {
            checkNotNull(resolver.openOutputStream(uri)) { "Die Datei im Download-Ordner ließ sich nicht öffnen." }
                .use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) {
            // Never leave a half-written file behind.
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        // Android may have changed the name (a file of another app with the same name): report the real one.
        val actual = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: displayName
        return OwnerSavedFile(actual, "Download-Ordner", uri.toString(), mime)
    }

    /**
     * The saved file's bytes, read again from where they were written (at most [maxBytes]); null when the file is not
     * there or cannot be read. The document desk compares them with what it wrote before it reports a file as saved.
     */
    fun readBack(file: OwnerSavedFile, maxBytes: Int = MAX_READ_BACK): ByteArray? = runCatching {
        val stream = file.uri?.let { context.contentResolver.openInputStream(Uri.parse(it)) }
            ?: File(legacyDirectory(), file.displayName).takeIf { it.isFile }?.inputStream()
        stream?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() <= maxBytes) {
                val read = input.read(buffer)
                if (read < 0) return@use out.toByteArray()
                out.write(buffer, 0, read)
            }
            null
        }
    }.getOrNull()

    /** Where the saved file lies, in the words of Android's file manager; null when Android does not say. */
    fun location(file: OwnerSavedFile): String? {
        val uri = file.uri?.let(Uri::parse) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.trim('/')?.takeIf { it.isNotEmpty() }?.let { "Interner Speicher › " + it.replace("/", " › ") }
    }

    /** Opens the saved file in the owner's viewer; null = started, otherwise the reason. */
    fun open(file: OwnerSavedFile): String? {
        val uri = file.uri?.let(Uri::parse) ?: return "Für diese Datei gibt es keinen teilbaren Verweis."
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, file.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(intent, "Ich habe keine App gefunden, die diese Datei öffnen kann.")
    }

    /**
     * Opens the owner's mail app with recipient (when an address was given), subject, text and – when [attachment]
     * has a content URI – the file attached with a one-time read grant. Nothing is sent; null = composer started.
     */
    fun composeMail(address: String?, subject: String, body: String, attachment: OwnerSavedFile?): String? {
        val intent = mailIntent(address, subject, body, attachment)
        return start(intent, "Ich habe kein Mailprogramm gefunden, an das ich die Nachricht übergeben kann.")
    }

    private fun start(intent: Intent, missing: String): String? = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        missing
    } catch (error: Exception) {
        error.message ?: error.javaClass.simpleName
    }

    private fun legacyDirectory(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "Download")

    companion object {
        const val MAX_MAIL_BODY = 8_000
        const val MAX_READ_BACK = 8 * 1024 * 1024

        /** ACTION_SEND limited to mail apps (mailto selector); the attachment travels as EXTRA_STREAM with a read grant. */
        fun mailIntent(address: String?, subject: String, body: String, attachment: OwnerSavedFile?): Intent {
            val stream = attachment?.uri?.let(Uri::parse)
            return Intent(Intent.ACTION_SEND).apply {
                type = if (stream != null) attachment.mime else "text/plain"
                if (!address.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                putExtra(Intent.EXTRA_SUBJECT, subject.take(200))
                putExtra(Intent.EXTRA_TEXT, body.take(MAX_MAIL_BODY))
                if (stream != null) {
                    putExtra(Intent.EXTRA_STREAM, stream)
                    clipData = ClipData.newRawUri(attachment.displayName, stream)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            }
        }
    }
}
