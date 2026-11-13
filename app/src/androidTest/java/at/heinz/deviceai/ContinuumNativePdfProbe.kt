package at.heinz.deviceai

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

/** Native decode, no PDF shadows. */
internal object ContinuumNativePdfProbe {
    fun run(resolver: ContentResolver): Bundle = Bundle().apply {
        putInt("sdk", Build.VERSION.SDK_INT)
        putString("evidence_type", "Android native PDF renderer and extraction; synthetic fixture")
        try {
            check(Build.VERSION.SDK_INT >= 35) { "NATIVE_PDF_REQUIRES_API_35" }
            val uri = Uri.parse("content://at.heinz.deviceai.tests.synthetic.pdfprobe/document/4711")
            val document = PdfDocumentIntake(resolver).read(uri, 360)
            try {
                check(document.name == ContinuumNativePdfProbeProvider.NAME) { "PROVIDER_NAME_MISMATCH" }
                check(document.sizeBytes == ContinuumNativePdfProbeProvider.bytes().size.toLong()) { "PROVIDER_SIZE_MISMATCH" }
                check(document.pageCount == 1 && document.textPages == 1) { "PAGE_COUNT_MISMATCH" }
                check(document.textState == PdfDocumentIntake.TextState.EXTRACTED) { "TEXT_NOT_EXTRACTED" }
                check(document.text.contains("CONTINUUM PDF TEST 4711")) { "PDF_TEXT_MISMATCH" }
                check(document.pageTexts.single().contains("CONTINUUM PDF TEST 4711")) { "PAGE_TEXT_MISMATCH" }
                val bitmap = checkNotNull(document.firstPage) { "NO_RENDERED_PAGE" }
                check(bitmap.width == 360 && bitmap.height > 0) { "PAGE_SIZE_MISMATCH" }
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val ink = pixels.count { Color.alpha(it) > 0 && Color.red(it) < 180 &&
                    Color.green(it) < 180 && Color.blue(it) < 180 }
                check(ink > 0) { "NO_RENDERED_INK" }
                putInt("rendered_ink_pixels", ink)
                putInt("page_count", document.pageCount)
                putString("status", "ok")
                putString("stream", "Native PDF provider, render and text extraction passed.\n")
            } finally { document.firstPage?.recycle() }
        } catch (failure: Throwable) {
            putString("status", "failed")
            putString("stream", "Native PDF probe failed: ${failure.stackTraceToString()}\n")
        }
    }
}

/** Serves one immutable synthetic fixture. */
class ContinuumNativePdfProbeProvider : ContentProvider() {
    override fun onCreate() = true

    private fun checkUri(uri: Uri) {
        if (uri.path != "/document/4711" || uri.query != null || uri.fragment != null)
            throw FileNotFoundException("UNKNOWN_SYNTHETIC_FIXTURE")
    }

    override fun getType(uri: Uri): String { checkUri(uri); return "application/pdf" }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkUri(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { when (it) {
                OpenableColumns.DISPLAY_NAME -> NAME
                OpenableColumns.SIZE -> bytes().size.toLong()
                else -> null
            } }.toTypedArray<Any?>())
        }
    }

    @Synchronized override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkUri(uri)
        if (mode != "r") throw FileNotFoundException("READ_ONLY_SYNTHETIC_FIXTURE")
        val fixture = File(checkNotNull(context).cacheDir, NAME)
        if (!fixture.exists()) fixture.writeBytes(bytes())
        return ParcelFileDescriptor.open(fixture, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val NAME = "continuum-native-probe.pdf"
        fun bytes(): ByteArray {
            val output = ByteArrayOutputStream()
            fun write(value: String) { output.write(value.toByteArray(Charsets.US_ASCII)) }
            val stream = "BT /F1 18 Tf 72 720 Td (CONTINUUM PDF TEST 4711) Tj ET\n"
            val objects = listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length ${stream.length} >>\nstream\n${stream}endstream"
            )
            write("%PDF-1.4\n")
            val offsets = objects.mapIndexed { index, value ->
                output.size().also { write("${index + 1} 0 obj\n$value\nendobj\n") }
            }
            val xref = output.size()
            write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
            write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
            return output.toByteArray()
        }
    }
}
