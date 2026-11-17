package at.heinz.deviceai.intelligence

/** Portable executable assertions:
 * kotlinc ContinuumCognitiveSpine.kt ContinuumCognitiveSpineChecks.kt -include-runtime -d checks.jar
 * java -jar checks.jar
 */
object ContinuumCognitiveSpineChecks {
    @JvmStatic fun main(args: Array<String>) {
        val text = "Der lokale Dienst nutzt Port 8443."
        val source = ContinuumCognitiveSpine.Source("doc-net-1", text, ContinuumCognitiveSpine.digest(text), true)
        val spine = ContinuumCognitiveSpine { _, s ->
            ContinuumCognitiveSpine.SpanProposal(s.id, s.text.indexOf("8443"), s.text.indexOf("8443") + 4)
        }
        val ok = spine.resolve("Welcher Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(source))
        check(ok.status == ContinuumCognitiveSpine.Status.QUOTED_SOURCE)
        check(ok.quotedText == "8443" && !ok.answer.contains("9000"))
        check(ok.answer.contains("nicht unabhängig verifiziert"))

        val forged = source.copy(sha256 = "0".repeat(64))
        check(spine.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(forged)).status ==
            ContinuumCognitiveSpine.Status.ABSTAINED)
        check(spine.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION,
            listOf(source.copy(markedReviewedByOwner = false))).status == ContinuumCognitiveSpine.Status.ABSTAINED)
        check(spine.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(source, source)).status ==
            ContinuumCognitiveSpine.Status.ABSTAINED)
        val invented = ContinuumCognitiveSpine { _, _ -> ContinuumCognitiveSpine.SpanProposal("other-id", 0, 4) }
        check(invented.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(source)).status ==
            ContinuumCognitiveSpine.Status.ABSTAINED)
        val invalid = ContinuumCognitiveSpine { _, _ -> ContinuumCognitiveSpine.SpanProposal(source.id, 0, 9000) }
        check(invalid.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(source)).status ==
            ContinuumCognitiveSpine.Status.ABSTAINED)
        val throwing = ContinuumCognitiveSpine { _, _ -> throw IllegalStateException("candidate failed") }
        check(throwing.resolve("Port?", ContinuumCognitiveSpine.Task.QUESTION, listOf(source)).status ==
            ContinuumCognitiveSpine.Status.ABSTAINED)
        val mem = spine.resolve("Merke dir: Port 8443", ContinuumCognitiveSpine.Task.MEMORY_PROPOSAL)
        check(mem.status == ContinuumCognitiveSpine.Status.OWNER_CONFIRMATION_REQUIRED)
        check(mem.answer.contains("noch nichts gespeichert"))
        val coding = spine.resolve("Prüfe einen Bug", ContinuumCognitiveSpine.Task.CODING_PROPOSAL)
        check(coding.status == ContinuumCognitiveSpine.Status.OWNER_CONFIRMATION_REQUIRED)
        check(coding.answer.contains("Kein Worker gestartet"))
        check(ok.receiptDigest.matches(Regex("[a-f0-9]{64}")))
        println("PASS: isolated cognitive decision assertions")
    }
}
