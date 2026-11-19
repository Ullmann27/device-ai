package at.heinz.deviceai.cognitive

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * `regression-library.jsonl` (development set) and `regression-library-check.jsonl` (held-out check set) of the
 * document desk: which sentences are an order to open a PDF by name, what the name is, which of the files that exist
 * is meant, how the owner answers the question, the page order with saving, corrections and the stop word – executed
 * against the productive parsers and the matcher. The two sets share no case; the check set was written before the
 * implementation and is not used for tuning (docs/evaluation/continuum-eval-v1/README.md).
 */
class OwnerLibraryRegressionTest {
    private val root: File = listOf("docs/evaluation/continuum-eval-v1", "../docs/evaluation/continuum-eval-v1")
        .map(::File).first { it.isDirectory }

    private fun cases(name: String): List<JSONObject> = File(root, name).readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }.map(::JSONObject)

    private fun strings(json: JSONObject, key: String): List<String> =
        json.optJSONArray(key)?.let { array -> (0 until array.length()).map(array::getString) } ?: emptyList()

    /** `Folder/Name.pdf` or `Name.pdf` (in Download); or `Name.pdf|Folder|2026-11-17` with a modification day. */
    private fun file(spec: String): OwnerLibraryFile {
        val parts = spec.split('|')
        if (parts.size == 3) return OwnerLibraryFile(parts[0], parts[1], "/storage/emulated/0/${parts[1]}/${parts[0]}", 1_000,
            LocalDate.parse(parts[2]).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val folder = spec.substringBeforeLast('/', "Download")
        val name = spec.substringAfterLast('/')
        return OwnerLibraryFile(name, folder, "/storage/emulated/0/$folder/$name", 1_000, 0L)
    }

    private fun page(page: Int): String = if (page == OwnerPageReference.LAST) "LAST" else page.toString()

    /** The lane an utterance takes among the owner lanes this desk sits beside, in the Activity's order. */
    private fun route(input: String): String = when {
        OwnerPageJobCommand.parse(input, "Jarvis") != null -> "DESK_PAGE"
        OwnerLibraryCommand.parseOpen(input, "Jarvis") != null -> "DESK_OPEN"
        EvalProbe.computer(input).startsWith("READY:") -> "COMPUTER"
        OwnerNewsCommand.parse(input, "Jarvis") != null -> "NEWS"
        OwnerExportCommand.parse(input, "Jarvis") != null -> "EXPORT"
        OwnerShareCommand.parse(input, "Jarvis")?.mailNamed == true -> "MAIL"
        OwnerTaskChainRecipes.recognise(input, "Jarvis") != null -> "CHAIN"
        OwnerDocumentIntent.parse(input, "Jarvis") != null -> "PICKER"
        else -> "OTHER"
    }

    private fun failures(name: String): Pair<Int, List<String>> {
        val failures = mutableListOf<String>()
        val all = cases(name)
        all.forEach { case ->
            val id = case.getString("id")
            val expect = case.getJSONObject("expect")
            fun check(ok: Boolean, message: String) { if (!ok) failures += "$id: $message" }
            when (case.getString("runner")) {
                "library_open" -> {
                    val order = OwnerLibraryCommand.parseOpen(case.getString("input"), "Jarvis")
                    val got = order?.let { (if (it.openWhenClear) "OPEN:" else "FIND:") + it.query } ?: "NONE"
                    check(got == expect.getString("open"), "open order $got, expected ${expect.getString("open")}")
                    if (expect.has("folder")) check(order?.folderHint == expect.getString("folder"), "folder ${order?.folderHint}")
                }
                "library_match" -> {
                    val files = strings(case, "files").map(::file)
                    val decision = OwnerLibraryMatcher.decide(case.getString("query"), files, case.optString("folder").ifEmpty { null })
                    val (got, first) = when (decision) {
                        is OwnerLibraryMatcher.Decision.Open -> "OPEN:${decision.hit.file.name}" to decision.hit.file.name
                        is OwnerLibraryMatcher.Decision.Ask -> "ASK:${decision.hits.size}" to decision.hits.first().file.name
                        is OwnerLibraryMatcher.Decision.Near -> "NEAR:" + decision.hits.joinToString("|") { it.file.name } to
                            decision.hits.first().file.name
                        OwnerLibraryMatcher.Decision.None -> "NONE" to null
                    }
                    check(got == expect.getString("decision"), "decision $got, expected ${expect.getString("decision")}")
                    if (expect.has("first")) check(first == expect.getString("first"), "first hit $first")
                }
                "library_choice" -> {
                    val got = when (val answer = OwnerLibraryChoice.parse(case.getString("input"), strings(case, "hits").map(::file), "Jarvis")) {
                        is OwnerLibraryChoice.Answer.Pick -> "PICK:${answer.index + 1}"
                        OwnerLibraryChoice.Answer.Invalid -> "INVALID"
                        OwnerLibraryChoice.Answer.Drop -> "DROP"
                        null -> "NONE"
                    }
                    check(got == expect.getString("choice"), "choice $got, expected ${expect.getString("choice")}")
                }
                "page_job" -> {
                    val parsed = OwnerPageJobCommand.parse(case.getString("input"), "Jarvis")
                    val got = when (parsed) {
                        is OwnerPageJobCommand.Result.Order -> "PAGE:${page(parsed.page)}:${parsed.format?.name ?: "ASK_FORMAT"}"
                        OwnerPageJobCommand.Result.SeveralPages -> "UNSUPPORTED"
                        null -> "NONE"
                    }
                    check(got == expect.getString("page_job"), "page order $got, expected ${expect.getString("page_job")}")
                    if (expect.has("name")) check((parsed as? OwnerPageJobCommand.Result.Order)?.name == expect.getString("name"),
                        "name ${(parsed as? OwnerPageJobCommand.Result.Order)?.name}")
                }
                "desk_correction" -> {
                    val got = when (val correction = OwnerDeskCorrection.parse(case.getString("input"), "Jarvis")) {
                        is OwnerDeskCorrection.Correction.Page -> "PAGE:${page(correction.page)}"
                        is OwnerDeskCorrection.Correction.Format -> "FORMAT:${correction.format.name}"
                        OwnerDeskCorrection.Correction.OtherFile -> "OTHER_FILE"
                        is OwnerDeskCorrection.Correction.Pick -> "PICK:${correction.number}"
                        is OwnerDeskCorrection.Correction.Name -> (if (correction.namedPdf) "NAMED_PDF:" else "NAME:") + correction.fragment
                        null -> "NONE"
                    }
                    check(got == expect.getString("correction"), "correction $got, expected ${expect.getString("correction")}")
                }
                "desk_stop" -> OwnerDeskReply.isStop(case.getString("input"), "Jarvis").let {
                    check(it == expect.getBoolean("stop"), "stop=$it, expected ${expect.getBoolean("stop")}")
                }
                "library_route" -> route(case.getString("input")).let {
                    check(it == expect.getString("route"), "route $it, expected ${expect.getString("route")}")
                }
                else -> failures += "$id: unknown runner ${case.getString("runner")}"
            }
        }
        println("$name: ${all.size - failures.map { it.substringBefore(':') }.distinct().size} / ${all.size} cases hold")
        return all.size to failures
    }

    @Test fun everyDevelopmentCaseHoldsOnTheRealCode() {
        val (total, failures) = failures("regression-library.jsonl")
        assertTrue("development set unexpectedly small: $total", total >= 120)
        if (failures.isNotEmpty()) fail("${failures.size} checks of $total development cases failed:\n" + failures.joinToString("\n"))
    }

    @Test fun everyHeldOutCheckCaseHoldsOnTheRealCode() {
        val (total, failures) = failures("regression-library-check.jsonl")
        assertTrue("check set unexpectedly small: $total", total >= 70)
        if (failures.isNotEmpty()) fail("${failures.size} checks of $total held-out cases failed:\n" + failures.joinToString("\n"))
    }

    /** Separate development and check data: no sentence (and no matching task) of one set appears in the other. */
    @Test fun theDevelopmentAndTheCheckSetShareNoCase() {
        fun keys(name: String) = cases(name).map { case ->
            case.getString("runner") + "|" + case.optString("input").trim().lowercase() + "|" + case.optString("query").trim().lowercase() +
                "|" + strings(case, "files").joinToString(",") + "|" + case.optString("folder")
        }.toSet()
        val shared = keys("regression-library.jsonl") intersect keys("regression-library-check.jsonl")
        assertTrue("shared between development and check set: $shared", shared.isEmpty())
    }
}
