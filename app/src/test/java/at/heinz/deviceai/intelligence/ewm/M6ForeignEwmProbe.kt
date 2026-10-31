package at.heinz.deviceai.intelligence.ewm

import java.util.Base64

/** JVM-only observation of the production Ewm1Memory graph.
 * Input is ID, before, after, unrelated (UTF-8 strings base64 encoded, tab separated).
 * Gold labels are deliberately never provided to this process.
 */
object M6ForeignEwmProbe {
    private fun decode(value: String): String =
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty()) { "M6_PROBE_NO_ARGUMENTS_ALLOWED" }
        var count = 0
        System.`in`.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                require(parts.size == 4) { "M6_INVALID_INPUT_FIELDS" }
                val (id, before64, after64, unrelated64) = parts
                require(Regex("M6X-[0-9]{3}").matches(id)) { "M6_INVALID_ID" }
                val before = decode(before64)
                val after = decode(after64)
                val unrelated = decode(unrelated64)
                require(before.isNotBlank() && after.isNotBlank() && unrelated.isNotBlank()) {
                    "M6_EMPTY_EVIDENCE"
                }
                val memory = Ewm1Memory(
                    listOf("before" to before, "unrelated" to unrelated, "after" to after)
                )
                val edgesFromAfter = memory.supersedes["after"].orEmpty().toSortedSet()
                val replacesBefore = "before" in edgesFromAfter
                val beforeCurrent = "before" !in memory.supersededBy
                val afterCurrent = "after" !in memory.supersededBy
                val unrelatedKept = "unrelated" !in memory.supersededBy &&
                    "unrelated" in memory.byId
                val reason = "PRODUCTION_GRAPH:edges_from_after=" +
                    edgesFromAfter.joinToString(",") + ";replaces_before=" + replacesBefore
                println(listOf(id, if (replacesBefore) "1" else "0",
                    beforeCurrent.toString(), afterCurrent.toString(), unrelatedKept.toString(),
                    encode(reason)).joinToString("\t"))
                count++
                require(count <= 240) { "M6_TOO_MANY_INPUTS" }
            }
        }
        require(count == 240) { "M6_EXPECTED_240_INPUTS_GOT_$count" }
    }
}
