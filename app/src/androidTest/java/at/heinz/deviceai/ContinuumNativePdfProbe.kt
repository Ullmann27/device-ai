package at.heinz.deviceai

import android.content.ContentResolver
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle

/** Native decode, no PDF shadows. */
internal object ContinuumNativePdfProbe {
    fun run(resolver: ContentResolver): Bundle = Bundle().apply {
        putInt("sdk", Build.VERSION.SDK_INT)
        putString("evidence_type", "Android native PDF renderer and extraction; synthetic fixture")
        try {
            check(Build.VERSION.SDK_INT >= 35) { "NATIVE_PDF_REQUIRES_API_35" }
            val uri = Uri.parse("content://at.heinz.deviceai.tests.synthetic.pdfprobe/document/4711")
            val before = checkNotNull(resolver.call(uri, "counts", null, null)) { "NO_PROVIDER_COUNTERS" }
            val document = PdfDocumentIntake(resolver).read(uri, 360)
            try {
                val after = checkNotNull(resolver.call(uri, "counts", null, null)) { "NO_PROVIDER_COUNTERS" }
                val queries = after.getInt("queries") - before.getInt("queries")
                val opens = after.getInt("opens") - before.getInt("opens")
                check(queries == 1) { "PROVIDER_QUERY_COUNT" }
                check(opens == 1) { "PROVIDER_OPEN_COUNT" }
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
                putInt("provider_queries", queries)
                putInt("provider_opens", opens)
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
