package at.heinz.deviceai

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

/** Actual PDF; native support required. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfIntakeNativeDecodeProbeUiTest {
    private val uri = Uri.parse("content://at.heinz.test.synthetic.pdf/document/4711")

    @Test fun actualProviderReadRendersAndExtractsSyntheticPdf() {
        val app = RuntimeEnvironment.getApplication()
        val fixture = File(app.cacheDir, "continuum-native-pdf-probe.pdf")
        fixture.writeBytes(syntheticPdf())
        val provider = FixtureProvider(fixture)
        provider.attachInfo(app, ProviderInfo().apply { authority = uri.authority })
        ShadowContentResolver.registerProviderInternal(requireNotNull(uri.authority), provider)
        try {
            val document = try {
                PdfDocumentIntake(app.contentResolver).read(uri, 360)
            } catch (failure: NoSuchMethodError) {
                assertEquals("The actual provider was queried", 1, provider.queries)
                assertEquals("The actual descriptor was opened", 1, provider.opens)
                val missingHostDescriptor = failure.message == "'long java.io.FileDescriptor.getOwnerId\$()'" &&
                    failure.stackTrace.any { it.className == "libcore.io.IoUtils" && it.methodName == "acquireRawFd" } &&
                    failure.stackTrace.any { it.className == "android.graphics.pdf.PdfProcessor" && it.methodName == "create" }
                if (System.getProperty("continuum.pdf.allowMissingHostNative") != "true" || !missingHostDescriptor)
                    throw failure
                failure.printStackTrace()
                throw AssumptionViolatedException("HOST_PDF_DESCRIPTOR_UNSUPPORTED: ${failure.message}", failure)
            }
            try {
                assertEquals(1, provider.queries)
                assertEquals(1, provider.opens)
                assertEquals(fixture.name, document.name)
                assertEquals(fixture.length(), document.sizeBytes)
                assertEquals(1, document.pageCount)
                assertEquals(PdfDocumentIntake.TextState.EXTRACTED, document.textState)
                assertEquals(1, document.textPages)
                assertTrue(document.text.contains("CONTINUUM PDF TEST 4711"))
                val page = requireNotNull(document.firstPage)
                assertEquals(360, page.width)
                val pixels = IntArray(page.width * page.height)
                page.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
                assertTrue("The native page contains rendered ink", pixels.any {
                    Color.alpha(it) > 0 && Color.red(it) < 180 && Color.green(it) < 180 && Color.blue(it) < 180
                })
            } finally {
                document.firstPage?.recycle()
            }
        } finally {
            fixture.delete()
        }
    }

    private class FixtureProvider(private val fixture: File) : ContentProvider() {
        var queries = 0
        var opens = 0

        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/pdf"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries++
            val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            return MatrixCursor(columns).apply {
                addRow(columns.map { column -> when (column) {
                    OpenableColumns.DISPLAY_NAME -> fixture.name
                    OpenableColumns.SIZE -> fixture.length()
                    else -> null
                } }.toTypedArray<Any?>())
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            check(mode == "r")
            opens++
            return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?,
            selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    }

    private fun syntheticPdf(): ByteArray {
        val out = ByteArrayOutputStream()
        fun write(text: String) { out.write(text.toByteArray(Charsets.US_ASCII)) }
        val stream = "BT /F1 18 Tf 72 720 Td (CONTINUUM PDF TEST 4711) Tj ET\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${stream.length} >>\nstream\n${stream}endstream"
        )
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, body ->
            out.size().also { write("${index + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = out.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}
