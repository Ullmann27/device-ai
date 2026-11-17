package at.heinz.deviceai.systemintelligence

import org.junit.Assert.*
import org.junit.Test

class StorageAnalysisPreviewTest {
    private fun file(id: String, name: String, bytes: Long = 2L shl 20) =
        StorageFileObservation(StorageDocument(id, name, "video/mp4", bytes, null), "verified-fixture")

    @Test fun localNamesNeverEnterToolSummary() {
        val report = StorageAnalysis(listOf(file("content://private/1", "Familie.mp4")),
            emptyList(), 2L shl 20, 0, true, emptySet())
        assertTrue(report.localPreview().contains("Familie.mp4"))
        assertFalse(report.summaryForTool().contains("Familie"))
        assertFalse(report.localPreview().contains("content://"))
        assertTrue(report.localPreview().contains("Keine Datei gelöscht"))
    }

    @Test fun onlyVerifiedGroupsProducePotentialSavings() {
        val files = listOf(file("1", "gleich.mp4"), file("2", "gleich.mp4"))
        val unverified = StorageAnalysis(files, emptyList(), 4L shl 20, 0, false, setOf("HASH_BYTE_LIMIT"))
        assertFalse(unverified.localPreview().contains("mehrfach gespeicherter Inhalt"))
        val verified = unverified.copy(duplicates = listOf(StorageDuplicateGroup(listOf("1", "2"), 2L shl 20)))
        assertTrue(verified.localPreview().contains("mehrfach gespeicherter Inhalt: 2.0 MiB"))
        assertTrue(verified.localPreview().contains("Kein freigegebener Löschplan"))
    }

    @Test fun untrustedProviderNamesCannotInjectLinesOrDirection() {
        val report = StorageAnalysis(listOf(file("1", "Film\nERFOLG\u202E.mp4")),
            emptyList(), 0, 0, true, emptySet())
        assertTrue(report.localPreview().contains("FilmERFOLG.mp4"))
        assertFalse(report.localPreview().contains('\u202E'))
    }

    @Test fun largeSelectionsHaveBoundedLocalPresentation() {
        val report = StorageAnalysis((1..512).map { file("$it", "x".repeat(2000), it.toLong()) },
            emptyList(), 0, 0, true, emptySet())
        val preview = report.localPreview()
        assertTrue(preview.contains("500 weitere Dateien"))
        assertTrue(preview.length < 3000)
    }
}
