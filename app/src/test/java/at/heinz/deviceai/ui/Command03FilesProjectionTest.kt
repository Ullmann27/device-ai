package at.heinz.deviceai.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Code64: the HTML stage receives the names a real file tool returned, so the visible work sequence
 * can show the actual search result instead of an invented one.
 */
class Command03FilesProjectionTest {
    private val session = "12345678-1234-1234-1234-123456789abc"

    private fun names(projection: Command03FilesProjection): List<String> =
        projection.snapshot(session)!!.getJSONArray("names").let { list -> List(list.length(), list::getString) }

    @Test fun noToolAnswerMeansNoRecord() {
        val projection = Command03FilesProjection()
        assertNull(projection.snapshot(session))
        assertFalse(projection.accept(session, "get_battery_info", emptyMap(), "/sdcard/Download/a.pdf"))
        assertFalse(projection.accept("", "find_files", mapOf("query" to "a"), "/sdcard/Download/a.pdf"))
        assertNull(projection.snapshot(session))
    }

    @Test fun aFileSearchShowsNamesOnlyAndTheRealCount() {
        val projection = Command03FilesProjection()
        val output = "/sdcard/Download/Argon4.pdf\n/sdcard/Documents/Labor/Argon4 Messprotokoll.pdf\n\n"
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "Argon4"), output))
        val record = projection.snapshot(session)!!
        assertEquals("SEARCH", record.getString("kind"))
        assertEquals("Argon4", record.getString("label"))
        assertEquals(2, record.getInt("total"))
        assertFalse(record.getBoolean("more"))
        assertEquals(listOf("Argon4.pdf", "Argon4 Messprotokoll.pdf"), names(projection))
        assertFalse(record.toString().contains("sdcard"))
    }

    @Test fun anEmptySearchIsARealResultWithZeroFiles() {
        val projection = Command03FilesProjection()
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "fehlt"), ""))
        val record = projection.snapshot(session)!!
        assertEquals(0, record.getInt("total"))
        assertEquals(0, record.getJSONArray("names").length())
    }

    @Test fun moreThanTwelveNamesAreCountedButNotListedAndTheToolLimitIsReported() {
        val projection = Command03FilesProjection()
        val output = (1..40).joinToString("\n") { "/sdcard/Download/Datei $it.txt" }
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "Datei", "limit" to 40), output))
        val record = projection.snapshot(session)!!
        assertEquals(40, record.getInt("total"))
        assertTrue(record.getBoolean("more"))
        assertEquals(Command03FilesProjection.MAX_NAMES, record.getJSONArray("names").length())
        assertEquals("Datei 12.txt", names(projection).last())
    }

    @Test fun textThatIsNotTheToolsLineFormatNeverBecomesAFileList() {
        val projection = Command03FilesProjection()
        assertFalse(projection.accept(session, "find_files", mapOf("query" to "a"), "Shizuku ist nicht verbunden."))
        assertFalse(projection.accept(session, "find_files", mapOf("query" to "a"), "/sdcard/Download/a.pdf\nfind: denied"))
        assertFalse(projection.accept(session, "list_files", mapOf("path" to "/sdcard/Download"), "ls: /sdcard/x: No such file"))
        assertNull(projection.snapshot(session))
    }

    @Test fun aFolderListingUsesTheExactLongFormatAndSkipsDotEntries() {
        val projection = Command03FilesProjection()
        val output = """
            total 5.2M
            drwxrwx--x  4 root sdcard_rw 3.4K 2026-11-01 09:12 .
            drwxrwx--x 21 root sdcard_rw 3.4K 2026-10-30 18:40 ..
            -rw-rw----  1 root sdcard_rw 1.2M 2026-10-01 12:00 Argon4 Messprotokoll.pdf
            drwxrwx--x  2 root sdcard_rw 3.4K 2026-09-14 07:05 Belege
            lrwxrwxrwx  1 root sdcard_rw   12 2026-09-14 07:05 neu -> Belege/neu.pdf
        """.trimIndent()
        assertTrue(projection.accept(session, "list_files", mapOf("path" to "/sdcard/Download/"), output))
        val record = projection.snapshot(session)!!
        assertEquals("FOLDER", record.getString("kind"))
        assertEquals("Download", record.getString("label"))
        assertEquals(3, record.getInt("total"))
        assertEquals(listOf("Argon4 Messprotokoll.pdf", "Belege", "neu"), names(projection))
    }

    @Test fun aRecordBelongsToTheSessionThatRanTheToolAndANewerAnswerReplacesIt() {
        val projection = Command03FilesProjection()
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "a"), "/sdcard/Download/a.pdf"))
        assertNull(projection.snapshot("87654321-4321-4321-4321-cba987654321"))
        val first = projection.snapshot(session)!!.getLong("revision")
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "b"), "/sdcard/Download/b.pdf"))
        val second = projection.snapshot(session)!!
        assertTrue(second.getLong("revision") > first)
        assertEquals(listOf("b.pdf"), names(projection))
    }

    @Test fun namesAndLabelsAreBoundedAndFreeOfControlCharacters() {
        val projection = Command03FilesProjection()
        val long = "x".repeat(300)
        assertTrue(projection.accept(session, "find_files", mapOf("query" to "q".repeat(200)),
            "/sdcard/Download/$long.pdf\n/sdcard/Download/a\u0007b.txt"))
        val record = projection.snapshot(session)!!
        assertEquals(Command03FilesProjection.MAX_LABEL, record.getString("label").length)
        assertEquals(Command03FilesProjection.MAX_NAME, names(projection)[0].length)
        assertTrue(names(projection)[0].endsWith("…"))
        assertEquals("ab.txt", names(projection)[1])
    }

    /** The exact answer shape of `find_app` (AndroidInstalledAppLookup). */
    private fun appAnswer(total: Int, vararg labels: String, schema: String = "CONTINUUM_APP_DISCOVERY_V1"): String {
        val candidates = org.json.JSONArray()
        labels.forEachIndexed { index, label ->
            candidates.put(org.json.JSONObject().put("packageName", "com.example.app$index").put("label", label))
        }
        return org.json.JSONObject().put("schema", schema).put("readOnly", true).put("totalMatches", total)
            .put("candidates", candidates).toString()
    }

    @Test fun anInstalledAppSearchShowsTheLabelsAndroidMatchedAndNeverAPackageName() {
        val projection = Command03FilesProjection()
        assertTrue(projection.accept(session, "find_app", mapOf("query" to "Kamera"), appAnswer(3, "Kamera", "Kamera Assistent")))
        val record = projection.snapshot(session)!!
        assertEquals("APPS", record.getString("kind"))
        assertEquals("Kamera", record.getString("label"))
        assertEquals("The tool's own match count, not the number of shown labels", 3, record.getInt("total"))
        assertFalse(record.getBoolean("more"))
        assertEquals(listOf("Kamera", "Kamera Assistent"), names(projection))
        assertFalse("No package name leaves the app", record.toString().contains("com.example"))
    }

    @Test fun anAppSearchWithoutHitsIsARecordWithoutNamesAndAForeignAnswerIsNone() {
        val projection = Command03FilesProjection()
        assertTrue(projection.accept(session, "find_app", mapOf("query" to "Gibtesnicht"), appAnswer(0)))
        assertEquals(0, projection.snapshot(session)!!.getInt("total"))
        assertEquals(emptyList<String>(), names(projection))
        val before = projection.snapshot(session)!!.getLong("revision")
        // Not the tool's schema, not JSON, a count below the labels: nothing is shown and the last record stays.
        assertFalse(projection.accept(session, "find_app", mapOf("query" to "x"), appAnswer(1, "X", schema = "OTHER")))
        assertFalse(projection.accept(session, "find_app", mapOf("query" to "x"), "Kamera\nUhr"))
        assertFalse(projection.accept(session, "find_app", mapOf("query" to "x"), appAnswer(1, "A", "B")))
        assertFalse("Another app tool is not a hit list", projection.accept(session, "list_installed_apps", emptyMap(), "com.a\ncom.b"))
        assertEquals(before, projection.snapshot(session)!!.getLong("revision"))
    }

    @Test fun appLabelsAreBoundedPlainTextWithoutDuplicates() {
        val projection = Command03FilesProjection()
        val labels = (1..20).map { "App $it" } + listOf("App 1", "a\u0007b", "x/y", "  ")
        assertTrue(projection.accept(session, "find_app", mapOf("query" to "App"), appAnswer(40, *labels.toTypedArray())))
        val shown = names(projection)
        assertEquals(Command03FilesProjection.MAX_NAMES, shown.size)
        assertEquals("App 1", shown[0])
        assertEquals(shown.distinct(), shown)
        assertTrue(projection.accept(session, "find_app", mapOf("query" to "ab"), appAnswer(2, "a\u0007b", "x/y")))
        assertEquals("Control characters are removed and a label that could be a path is not shown", listOf("ab"), names(projection))
    }
}
