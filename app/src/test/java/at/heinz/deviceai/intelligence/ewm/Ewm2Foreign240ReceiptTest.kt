package at.heinz.deviceai.intelligence.ewm

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One-shot read-only foreign M6 evaluation of the genuine EWM2 product supersession graph.
 * Gold labels are never passed to the predictor; no runtime or Android production code changes.
 */
class Ewm2Foreign240ReceiptTest {
    companion object {
        private const val CANDIDATE_SHA = "3ba1ba0bba28e441d2b85ae098371901d3db7407"
        private const val FROZEN_BLOB_SHA = "622370c291ead9ab11ec8917c992037a2fcf9e17"
        private val SOURCE_BLOBS = mapOf(
            "Ewm1EvidenceWorkspace.kt" to "cb19581af34ad177add22a89638988a74a9c7aad",
            "Ewm1Lexicon.kt" to "8c1e6acd4a7ee2cf632b115550eb7c7d5c801df6",
            "Ewm1Workspace.kt" to "b338a755a161a620405bd3581bf45fb74b1c43fc"
        )

        private fun gitBlob(file: File): String {
            val bytes = file.readBytes()
            val header = ("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-1").digest(header + bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }

        private fun projectRoot(): File =
            generateSequence(File(System.getProperty("user.dir")).canonicalFile) { it.parentFile }
                .firstOrNull {
                    File(it, "docs/research/m6-independent/ewm-v2-foreign-240.jsonl").isFile &&
                        File(it, "app/src/main/java/at/heinz/deviceai/intelligence/ewm").isDirectory
                } ?: error("M6_PROJECT_ROOT_NOT_FOUND")

        private data class Prediction(
            val label: String,
            val beforeCurrent: Boolean,
            val afterCurrent: Boolean,
            val unrelatedKept: Boolean,
            val reason: String
        )

        private fun predict(before: String, after: String, unrelated: String): Prediction {
            val memory = Ewm1Memory(listOf("before" to before, "after" to after, "unrelated" to unrelated))
            val link = "before" in memory.supersedes["after"].orEmpty()
            val older = memory.byId.getValue("before")
            val newer = memory.byId.getValue("after")
            val correction = newer.tense == 'p' && older.tense == 'p' && newer.correction
            val label = when {
                !link -> "COEXISTS"
                correction -> "CORRECTS_EVENT"
                else -> "SUPERSEDES_STATE"
            }
            return Prediction(
                label,
                "before" !in memory.supersededBy,
                "after" !in memory.supersededBy,
                "unrelated" !in memory.supersededBy &&
                    "unrelated" !in memory.supersedes.keys &&
                    memory.byId.containsKey("unrelated"),
                "production_ewm_graph_edge=" + link +
                    ";before_tense=" + older.tense +
                    ";after_tense=" + newer.tense +
                    ";explicit_correction=" + newer.correction +
                    ";unrelated_superseded=" + ("unrelated" in memory.supersededBy)
            )
        }
    }

    @Test
    fun exportActualFrozenCandidateReceiptsWithoutGoldLeakage() {
        val root = projectRoot()
        val sourceDir = File(root, "app/src/main/java/at/heinz/deviceai/intelligence/ewm")
        SOURCE_BLOBS.forEach { (name, sha) ->
            val source = File(sourceDir, name)
            assertTrue("M6_CANDIDATE_SOURCE_MISSING:" + name, source.isFile)
            assertEquals("M6_CANDIDATE_SOURCE_CHANGED:" + name, sha, gitBlob(source))
        }

        val frozen = File(root, "docs/research/m6-independent/ewm-v2-foreign-240.jsonl")
        assertEquals("M6_HOLDOUT_MODIFIED", FROZEN_BLOB_SHA, gitBlob(frozen))
        val lines = frozen.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals("M6_EXPECTED_240_CASES", 240, lines.size)
        val seen = HashSet<String>()
        val receipts = ArrayList<String>(lines.size)

        lines.forEachIndexed { index, text ->
            val item = JSONObject(text)
            val id = "M6X-" + (index + 1).toString().padStart(3, '0')
            assertEquals("M6_CASE_ID", id, item.getString("id"))
            assertTrue("M6_DUPLICATE_ID", seen.add(id))
            val result = predict(
                item.getString("before"), item.getString("after"), item.getString("unrelated")
            )
            // Attach gold only after the product graph has produced its independent prediction.
            val receipt = JSONObject()
                .put("id", id)
                .put("candidate_sha", CANDIDATE_SHA)
                .put("expected_label", item.getString("expected"))
                .put("predicted_label", result.label)
                .put("reasons", result.reason)
                .put("before_current", result.beforeCurrent)
                .put("after_current", result.afterCurrent)
                .put("unrelated_fact_kept", result.unrelatedKept)
            receipts += receipt.toString()
        }

        val outputDir = File(root, "app/build/reports/ewm-m6").apply {
            check(isDirectory || mkdirs()) { "M6_RECEIPT_DIRECTORY_UNWRITABLE" }
        }
        val output = File(outputDir, "ewm-v2-foreign-240-receipts.jsonl")
        val pending = Files.createTempFile(outputDir.toPath(), "m6-", ".tmp")
        try {
            Files.writeString(pending, receipts.joinToString("\n", postfix = "\n"))
            Files.move(
                pending, output.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        } finally {
            Files.deleteIfExists(pending)
        }
        assertEquals(240, output.readLines(Charsets.UTF_8).size)
        println("M6_EXTERNAL_RECEIPTS_CREATED:" + output.absolutePath)
        println("M6_CANDIDATE_SHA:" + CANDIDATE_SHA)
        println("M6_HOLDOUT_BLOB:" + FROZEN_BLOB_SHA)
    }
}
