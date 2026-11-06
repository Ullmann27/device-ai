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

    private fun clockTailWired(activity: String, frame: String): Boolean {
        val bodyMarker = "val promptBody = buildString {"
        val capMarker = "val promptCap ="
        val tailMarker = "val promptEnd ="
        val callMarker = "val prompt = at.heinz.deviceai.cognitive.LocalDecisionPromptFrame.assemble("
        if (!activity.contains(bodyMarker) || !activity.contains(capMarker) ||
            !activity.contains(tailMarker) || !activity.contains(callMarker)) return false
        val body = activity.substringAfter(bodyMarker).substringBefore(capMarker)
        val tail = activity.substringAfter(tailMarker).substringBefore(callMarker)
        val call = activity.substringAfter(callMarker).substringBefore(") ?: error(")
        val forwarded = Regex("""LocalPromptFrame\.assemble\([\s\S]*?\brequiredTail\s*=\s*requiredTail\b""")
            .findAll(frame).count()
        return !body.contains("LocalClockIntent.promptAnchor") &&
            tail.contains("LocalClockIntent.promptAnchor(") &&
            Regex("""\brequiredTail\s*=\s*promptEnd\b""").containsMatchIn(call) &&
            forwarded == 2
    }

    private fun clockSources(): Pair<String, String> {
        fun read(vararg paths: String) = paths.map(::File).first { it.exists() }.readText()
        return read("app/src/main/java/at/heinz/deviceai/AgentActivity.kt",
            "src/main/java/at/heinz/deviceai/AgentActivity.kt") to
            read("app/src/main/java/at/heinz/deviceai/cognitive/LocalDecisionPromptFrame.kt",
                "src/main/java/at/heinz/deviceai/cognitive/LocalDecisionPromptFrame.kt")
    }

    @Test fun timeAnchorIsInTheRequiredTailNotInTheStaticPrefix() {
        val (activity, frame) = clockSources()
        assertTrue("clock must flow through promptEnd and both required tails",
            clockTailWired(activity, frame))
    }

    @Test fun timeAnchorContractRejectsDetachedOrDroppedTail() {
        val (activity, frame) = clockSources()
        assertTrue(clockTailWired(activity, frame))
        assertFalse(clockTailWired(activity.replaceFirst("requiredTail=promptEnd",
            "requiredTail=promptTail"), frame))
        assertFalse(clockTailWired(activity.replaceFirst("LocalClockIntent.promptAnchor(",
            "LocalClockIntent.missingAnchor("), frame))
        assertFalse(clockTailWired(activity,
            frame.replaceFirst("requiredTail = requiredTail", "requiredTail = \"\"")))
    }
}
