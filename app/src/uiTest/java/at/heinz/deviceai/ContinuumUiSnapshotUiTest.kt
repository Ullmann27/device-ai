package at.heinz.deviceai

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import at.heinz.deviceai.ui.ContinuumUiSnapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Real resolver; controlled provider failures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class ContinuumUiSnapshotUiTest {
    private class Output(private val failWrite: Boolean, private val failClose: Boolean) : ByteArrayOutputStream() {
        var closed = false
        override fun write(value: Int) {
            if (failWrite) throw IOException("WRITE_FAILURE")
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (failWrite) throw IOException("WRITE_FAILURE")
            super.write(bytes, offset, length)
        }
        override fun close() {
            closed = true
            if (failClose) throw IOException("CLOSE_FAILURE")
            super.close()
        }
    }

    private class MediaProvider : ContentProvider() {
        var missingOutput: String? = null
        var deniedOutput: String? = null
        var failedWrite: String? = null
        var failedClose: String? = null
        var failedPublish: String? = null
        var failedDelete: String? = null
        var skipMetadata = false
        var fileOpens = 0
        val inserted = linkedMapOf<Uri, ContentValues>()
        val rows = linkedMapOf<Uri, ContentValues>()
        val outputs = linkedMapOf<Uri, Output>()
        val published = mutableListOf<Uri>()
        val deletions = mutableListOf<Uri>()
        fun kind(uri: Uri): String = if (inserted.getValue(uri)
            .getAsString(MediaStore.MediaColumns.MIME_TYPE) == "application/json") "json" else "png"
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = rows[uri]?.getAsString(MediaStore.MediaColumns.MIME_TYPE)
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            val supplied = requireNotNull(values)
            if (skipMetadata && supplied.getAsString(MediaStore.MediaColumns.MIME_TYPE) == "application/json") return null
            val target = ContentUris.withAppendedId(uri, inserted.size + 1L)
            inserted[target] = ContentValues(supplied)
            rows[target] = ContentValues(supplied)
            val kind = kind(target)
            if (kind != missingOutput && kind != deniedOutput) {
                val stream = Output(kind == failedWrite, kind == failedClose)
                outputs[target] = stream
                shadowOf(requireNotNull(context).contentResolver).registerOutputStream(target, stream)
            }
            return target
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
            fileOpens++
            if (kind(uri) == deniedOutput) throw SecurityException("OUTPUT_DENIED")
            return null
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (kind(uri) == failedPublish) return 0
            assertTrue("Closed output before publication", outputs.getValue(uri).closed)
            val row = rows[uri] ?: return 0
            row.putAll(requireNotNull(values))
            published += uri
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deletions += uri
            if (kind(uri) == failedDelete) throw SecurityException("DELETE_DENIED")
            return if (rows.remove(uri) != null) 1 else 0
        }
    }

    private fun withProvider(block: (Activity, MediaProvider, Bitmap) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val provider = MediaProvider()
        provider.attachInfo(activity, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try { block(activity, provider, bitmap) }
        finally { bitmap.recycle(); controller.close() }
    }

    private fun metrics() = JSONObject().put("screenWidthDp", 400).put("screenHeightDp", 800)

    @Test fun successfulSavePublishesClosedPngAndJson() = withProvider { activity, provider, bitmap ->
        val saved = requireNotNull(ContinuumUiSnapshot.save(activity, bitmap, metrics()))
        assertEquals(setOf(saved.image, saved.metrics), provider.rows.keys)
        assertEquals(provider.rows.keys, provider.published.toSet())
        assertTrue(provider.inserted.values.all { it.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 1 })
        assertTrue(provider.rows.values.all { it.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0 })
        assertTrue(provider.outputs.values.all { it.closed })
        assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10),
            provider.outputs.getValue(saved.image).toByteArray().copyOfRange(0, 8))
        val data = JSONObject(provider.outputs.getValue(requireNotNull(saved.metrics)).toString("UTF-8"))
        assertEquals(400, data.getInt("screenWidthDp"))
        assertEquals(800, data.getInt("screenHeightDp"))
        assertTrue(provider.deletions.isEmpty())
    }

    @Test fun nullImageStreamRemovesPendingRow() = withProvider { activity, provider, bitmap ->
        provider.missingOutput = "png"
        assertThrows(IOException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.fileOpens > 0)
        assertEquals(1, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun imageAccessDenialLeavesNoPendingRow() = withProvider { activity, provider, bitmap ->
        provider.deniedOutput = "png"
        assertThrows(SecurityException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.fileOpens > 0)
        assertEquals(1, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun failedPngEncodingRemovesPendingRow() = withProvider { activity, provider, bitmap ->
        provider.failedWrite = "png"
        assertThrows(IllegalStateException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.outputs.values.single().closed)
        assertEquals(1, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun imageStreamCloseFailureRemovesPendingRow() = withProvider { activity, provider, bitmap ->
        provider.failedClose = "png"
        assertThrows(IOException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.outputs.values.single().closed)
        assertEquals(1, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun nullMetadataStreamRemovesBothRows() = withProvider { activity, provider, bitmap ->
        provider.missingOutput = "json"
        assertThrows(IOException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.fileOpens > 0)
        assertTrue(provider.outputs.values.single().closed)
        assertEquals(provider.inserted.keys, provider.deletions.toSet())
        assertEquals(2, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun metadataWriteFailureRemovesBothRows() = withProvider { activity, provider, bitmap ->
        provider.failedWrite = "json"
        assertThrows(IOException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertTrue(provider.outputs.values.all { it.closed })
        assertEquals(provider.inserted.keys, provider.deletions.toSet())
        assertEquals(2, provider.deletions.size)
        assertTrue(provider.rows.isEmpty())
        assertTrue(provider.published.isEmpty())
    }

    @Test fun publishFailureRemovesBothRows() {
        for (kind in listOf("png", "json")) withProvider { activity, provider, bitmap ->
            provider.failedPublish = kind
            assertThrows(IllegalStateException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
            assertTrue(provider.outputs.values.all { it.closed })
            assertEquals(provider.inserted.keys, provider.deletions.toSet())
            assertEquals(2, provider.deletions.size)
            assertTrue(provider.rows.isEmpty())
        }
    }

    @Test fun rollbackFailurePreservesCauseAndAttemptsOtherRow() = withProvider { activity, provider, bitmap ->
        provider.failedWrite = "json"
        provider.failedDelete = "json"
        val failure = assertThrows(IOException::class.java) { ContinuumUiSnapshot.save(activity, bitmap, metrics()) }
        assertEquals("WRITE_FAILURE", failure.message)
        assertEquals("DELETE_DENIED", failure.suppressed.single().message)
        assertEquals(provider.inserted.keys, provider.deletions.toSet())
        assertEquals(listOf("json"), provider.rows.keys.map(provider::kind))
        assertTrue(provider.published.isEmpty())
    }

    @Test fun unavailableOptionalMetadataKeepsValidImage() = withProvider { activity, provider, bitmap ->
        provider.skipMetadata = true
        val saved = requireNotNull(ContinuumUiSnapshot.save(activity, bitmap, metrics()))
        assertNull(saved.metrics)
        assertEquals(setOf(saved.image), provider.rows.keys)
        assertEquals(listOf(saved.image), provider.published)
        assertTrue(provider.outputs.values.single().closed)
        assertTrue(provider.deletions.isEmpty())
    }

    @Test @Config(sdk = [28]) fun oldAndroidDoesNotCreateRows() = withProvider { activity, provider, bitmap ->
        assertNull(ContinuumUiSnapshot.save(activity, bitmap, metrics()))
        assertTrue(provider.inserted.isEmpty())
        assertTrue(provider.outputs.isEmpty())
        assertTrue(provider.published.isEmpty())
    }
}
