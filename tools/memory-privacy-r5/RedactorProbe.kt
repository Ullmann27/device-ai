fun main() {
    val source = RedactorSource()
    data class Check(val name: String, val input: String, val expectedRedacted: List<String>, val preserved: List<String>)
    val cases = listOf(
        Check("standalone-bearer-lf-preserve-negation", "Documentation mentions Bearer\nNevermore approve the action.", emptyList(), listOf("Nevermore approve the action.")),
        Check("standalone-bearer-crlf-preserve-negation", "Documentation mentions Bearer\r\nNevermore approve the action.", emptyList(), listOf("Nevermore approve the action.")),
        Check("standalone-bearer-tab-removes-token", "Bearer R5_SYNTHETIC_TOKEN", listOf("R5_SYNTHETIC_TOKEN"), listOf("Bearer [REDACTED]")),
        Check("access_token-json", "{\"access_token\":\"R5_ACCESS_TOKEN\",\"keep\":\"Never approve\"}", listOf("R5_ACCESS_TOKEN"), listOf("Never approve")),
        Check("refresh_token-form", "refresh_token=R5_REFRESH_TOKEN\nNever approve", listOf("R5_REFRESH_TOKEN"), listOf("Never approve")),
        Check("client_secret-form", "client_secret=R5_CLIENT_SECRET\nNever approve", listOf("R5_CLIENT_SECRET"), listOf("Never approve")),
        Check("session_token-form", "session_token=R5_SESSION_TOKEN\nNever approve", listOf("R5_SESSION_TOKEN"), listOf("Never approve")),
        Check("legacy-bearer-assignment", "Authorization: Bearer R5_LEGACY_SECRET\nNever approve", listOf("R5_LEGACY_SECRET"), listOf("Never approve")),
        Check("legacy-token", "x-token=R5_LEGACY_TOKEN\nNever approve", listOf("R5_LEGACY_TOKEN"), listOf("Never approve")),
        Check("non-key-control", "access_tokenizer=public\nNever approve", emptyList(), listOf("access_tokenizer=public", "Never approve"))
    )
    var ok = 0
    for (c in cases) {
        val output = source.inspect(c.input)
        val pass = c.expectedRedacted.none(output::contains) && c.preserved.all(output::contains)
        if (pass) ++ok
        println("${if(pass) "PASS" else "FAIL"}\t${c.name}\toutput=${output.replace("\n", "\\n").replace("\r", "\\r")}")
    }
    val wrapped = listOf("Bearer\nR5_WRAPPED_SYNTHETIC_TOKEN", "Bearer\r\nR5_WRAPPED_SYNTHETIC_TOKEN")
    var rejected = 0
    for ((index, value) in wrapped.withIndex()) {
        val failure = runCatching { source.inspect(value) }.exceptionOrNull()
        val pass = failure is IllegalStateException && failure.message == "UNTERMINATED_CREDENTIAL_VALUE"
        if (pass) rejected++
        println("${if (pass) "PASS" else "FAIL"}\twrapper-case-$index\treason=${failure?.message ?: "NO_ERROR"}")
    }
    val total = cases.size + wrapped.size
    val passed = ok + rejected
    println("TOTAL=$total PASS=$passed FAIL=${total - passed}")
    if (passed < total) throw IllegalStateException("REGRESSION_RED_${total-passed}")
}
