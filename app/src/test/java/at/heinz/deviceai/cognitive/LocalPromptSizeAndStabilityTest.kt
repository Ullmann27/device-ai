package at.heinz.deviceai.cognitive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2 / LiteRT-LM: Promptgröße und stabiles Präfix, auf dem Host messbar.
 * Werkzeugschemata sind in LiteRtLmLocalModelRuntime bereits nativ als OpenApiTool
 * registriert; der Textkatalog im Nutzerprompt ist eine Dublette.
 */
class LocalPromptSizeAndStabilityTest {
    private val now = 100_000L

    private fun compiled(native: Boolean): CompiledAiContext {
        val genome = CognitiveGenome("continuum-context")
        val identity = IdentityState.initial(genome)
        val seed = ContinuumSeed(
            instanceId = ContinuumInstanceId(identity.identityId),
            governanceVersion = GovernanceVersion(1), stateFormatVersion = StateFormatVersion(1),
            memorySchemaVersion = 1, skillSchemaVersion = 1, modelAdapterAbiVersion = ModelAdapterAbiVersion(1),
            ownerIdentityReference = "owner:test", authorizedNodeIds = listOf(ComputeNodeId("fold")),
            currentStateRoot = ContinuumObjectId.of(ContinuumObjectType.STATE_MANIFEST, "root".toByteArray()),
            createdAtMillis = now)
        return AiContextCompiler(AiContextPolicy(maxBytes = 10_500, memoryLimit = 8, researchSourceLimit = 4,
            nativeToolCatalog = native), nowMillis = { now }).compile(
            AiContextInput(seed = seed, genome = genome, identityState = identity,
                userRequest = "Nun hat die rote Kiste 9 Ringe statt 4. Welche Kiste enthält jetzt mehr?"))
    }

    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8).size

    @Test fun nativeToolCatalogShrinksTheCompiledContextButKeepsEveryToolName() {
        val full = compiled(false).text
        val native = compiled(true).text
        println("PROMPT-MESSUNG compiledContext FULL=${bytes(full)} B, NATIVE=${bytes(native)} B, " +
            "Ersparnis=${bytes(full) - bytes(native)} B")
        assertTrue("expected smaller: ${bytes(full)} -> ${bytes(native)}", bytes(native) < bytes(full))
        at.heinz.deviceai.ToolRegistry.all().forEach {
            assertTrue(it.name, native.contains(it.name))
            // The duplicated natural-language schema text is gone (it is registered natively).
            assertFalse(it.name, native.contains(it.description.take(60)))
        }
        // Governance and the owner request stay complete.
        assertTrue(native.contains("operator_authority="))
        assertTrue(native.contains("Nun hat die rote Kiste 9 Ringe statt 4."))
    }

    @Test fun timeAnchorIsInTheRequiredTailNotInTheStaticPrefix() {
        val source = listOf("app/src/main/java/at/heinz/deviceai/AgentActivity.kt",
            "src/main/java/at/heinz/deviceai/AgentActivity.kt").map(::File).first { it.exists() }.readText()
        fun section(start: String, end: String): String {
            val begin = source.indexOf(start)
            assertTrue("missing source marker: $start", begin >= 0)
            val finish = source.indexOf(end, begin + start.length)
            assertTrue("missing source marker: $end", finish >= 0)
            return source.substring(begin + start.length, finish)
        }
        val body = section("fun buildPromptBody(compiledText: String?): String = buildString {", "val requestedPromptCap =")
        assertFalse("time anchor must not be inside promptBody", body.contains("LocalClockIntent.promptAnchor"))
        val tail = section("val promptEnd =", "val continuingAnswer =")
        assertTrue("promptEnd must retain the required tail and trusted clock", Regex(
            """\s*promptTail\s*\+\s*briefInstruction\s*\+\s*"\\n"\s*\+\s*""" +
                """at\.heinz\.deviceai\.cognitive\.LocalClockIntent\.promptAnchor\(""" +
                """\s*java\.time\.ZonedDateTime\.now\(\)\s*\)\s*"""
        ).matches(tail))
        val assemble = section(
            "val prompt = at.heinz.deviceai.cognitive.LocalDecisionPromptFrame.assemble(", ") ?: error(")
        assertTrue("the actual frame must receive the stable prompt body", Regex(
            """(?:^|,)\s*requiredBody\s*=\s*promptBody\s*(?:,|$)"""
        ).containsMatchIn(assemble))
        assertTrue("the actual frame must receive promptEnd as requiredTail", Regex(
            """(?:^|,)\s*requiredTail\s*=\s*promptEnd\s*(?:,|$)"""
        ).containsMatchIn(assemble))
    }
}
