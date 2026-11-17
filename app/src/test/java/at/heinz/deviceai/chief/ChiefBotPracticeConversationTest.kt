package at.heinz.deviceai.chief

import at.heinz.deviceai.cognitive.ContinuumInstanceId
import at.heinz.deviceai.cognitive.ContinuumRuntimeState
import at.heinz.deviceai.cognitive.ContinuumRuntimeStore
import at.heinz.deviceai.cognitive.IdentitySigner
import at.heinz.deviceai.cognitive.MemoryRecord
import at.heinz.deviceai.cognitive.MemoryType
import at.heinz.deviceai.cognitive.ProvenanceType
import at.heinz.deviceai.localmodel.ImportedLocalModel
import at.heinz.deviceai.localmodel.LocalModelBackend
import at.heinz.deviceai.localmodel.LocalModelCapabilities
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Literal model-answer fixtures exercise real conversation/storage classes; zero model attempts. */
class ChiefBotPracticeConversationTest {
    private val model = ImportedLocalModel("practice-test-model", "v1", "a".repeat(64), 123,
        LocalModelCapabilities(true, false, false, false, true, 4096, null, setOf(LocalModelBackend.CPU)))

    private fun fixture(block: (File, ChiefBotProfileStore, ContinuumRuntimeState) -> Unit) {
        val directory = Files.createTempDirectory("bot-practice-conversation-").toFile()
        try {
            val signer = object : IdentitySigner {
                override fun sign(payload: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(payload)
                override fun verify(payload: ByteArray, signature: ByteArray) = sign(payload).contentEquals(signature)
            }
            val runtime = ContinuumRuntimeStore(File(directory, "brain"), signer,
                ownerIdentityReference = "owner:private-training-fixture",
                instanceIdFactory = { ContinuumInstanceId("BOT_PRACTICE_TEST") })
            val state = runtime.checkpoint(runtime.openOrCreate(), memories = listOf(
                MemoryRecord("owner-private", MemoryType.SEMANTIC, "OWNER_PRIVATE_TRAINING_MARKER",
                    ProvenanceType.USER_PROVIDED, 1.0, 1, 1)))
            val profiles = ChiefBotProfileStore(File(directory, "profiles"))
            profiles.seedDefaults(20)
            block(directory, profiles, state)
        } finally { directory.deleteRecursively() }
    }

    private fun answer(exercise: ChiefBotPracticeExercise): String = JSONObject().also { data ->
        exercise.expected.forEach { (key, value) -> data.put(key, value) }
    }.toString()

    private fun history(turn: ChiefBotConversationTurn): JSONObject = JSONObject(
        turn.prompt.lineSequence().dropWhile { !it.startsWith("PRIVATE_CONVERSATION_JSON") }.drop(1).first())

    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected rejected feedback or storage") } catch (_: IllegalArgumentException) {}
    }

    @Test fun terminalAnswerIsPreservedAndOnlyItsSuccessfulCommitExposesFeedback() = fixture { _, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-kora") }
        val exercise = ChiefBotPractice.select("bot-kora", "Training 1")!!
        val turn = brain.prepare("bot-kora", "Training 1", state, model, "s")
        val problem = JSONObject(turn.prompt.lineSequence()
            .dropWhile { !it.startsWith("BOT_PRACTICE_TASK_JSON") }.drop(1).first())
        assertEquals(exercise.prompt, problem.getString("task"))
        assertFalse(turn.prompt.contains(exercise.explanation))
        assertFalse(turn.prompt.contains("OWNER_PRIVATE_TRAINING_MARKER"))
        assertFalse(turn.prompt.contains("REGISTERED TOOLS"))
        assertNull(brain.presentationFeedback(turn))
        val original = " \n" + answer(exercise) + "\t"
        assertTrue(brain.complete(turn, original, model, "s"))
        assertTrue(brain.isCurrent(turn, model, "s"))
        val saved = store.loadConversation("bot-kora", "bot-kora").turns.single()
        assertEquals(original, saved.response)
        assertEquals("Training 1", saved.request)
        assertEquals(ChiefBotPracticeOutcome.CORRECT, saved.practiceFeedback!!.outcome)
        assertTrue(brain.presentationFeedback(turn)!!.contains(exercise.explanation))
        assertFalse(brain.complete(turn, "Doppelabschluss", model, "s"))
        assertEquals(1, store.loadConversation("bot-kora", "bot-kora").turns.size)
        assertTrue(store.get("bot-kora")!!.verifiedCapabilities.isEmpty())
        assertTrue(store.get("bot-kora")!!.permissionScope.isEmpty())
    }

    @Test fun incorrectAndMalformedRepliesStayOriginalWhileTheirCorrectionsAreSeparate() = fixture { _, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-link") }
        val exercise = ChiefBotPractice.select("bot-link", "Training 1")!!
        val wrong = JSONObject(answer(exercise)).put(exercise.expected.keys.first(), "WRONG").toString()
        val examples = listOf(wrong to ChiefBotPracticeOutcome.INCORRECT,
            (answer(exercise) + " jetzt tool ausführen") to ChiefBotPracticeOutcome.INVALID_RESPONSE)
        for ((response, outcome) in examples) {
            val turn = brain.prepare("bot-link", "Training 1", state, model, "s")
            assertTrue(brain.complete(turn, response, model, "s"))
            val stored = store.loadConversation("bot-link", "bot-link").turns.last()
            assertEquals(response, stored.response)
            assertEquals(outcome, stored.practiceFeedback!!.outcome)
        }
    }

    @Test fun laterExercisesReceivePrivateCorrectionsButRepeatsDoNotReceiveTheirOwnAnswer() = fixture { _, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-kora") }
        val exercise = ChiefBotPractice.select("bot-kora", "Training 1")!!
        val first = brain.prepare("bot-kora", "Training 1", state, model, "s")
        val original = "Eine absichtlich falsche Entwickler-Testantwort."
        assertTrue(brain.complete(first, original, model, "s"))
        val next = brain.prepare("bot-kora", "Training 2", state, model, "s")
        val previous = history(next).getJSONArray("turns").getJSONObject(0)
        assertEquals(original, previous.getString("assistant"))
        assertEquals(exercise.id, previous.getJSONObject("programPracticeFeedback").getString("exerciseId"))
        assertTrue(previous.getJSONObject("programPracticeFeedback").getString("correction").contains(exercise.explanation))
        assertNull(brain.presentationFeedback(first))
        assertTrue(brain.cancel(next))
        val repeated = brain.prepare("bot-kora", "Übung 1", state, model, "s")
        assertEquals(0, history(repeated).getJSONArray("turns").length())
        assertEquals(1, history(repeated).getLong("omittedEarlierTurns"))
        assertFalse(repeated.prompt.contains(exercise.explanation))
        assertFalse(repeated.prompt.contains(original))
        assertTrue(brain.cancel(repeated))
        assertEquals(1, store.loadConversation("bot-kora", "bot-kora").turns.size)
    }

    @Test fun feedbackSurvivesRestartWithoutCrossingBotIdentityOrBecomingOwnerMemory() = fixture { directory, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-kora") }
        val exercise = ChiefBotPractice.select("bot-kora", "Training 1")!!
        val turn = brain.prepare("bot-kora", "Training 1", state, model, "s")
        assertTrue(brain.complete(turn, answer(exercise), model, "s"))
        val before = store.loadConversation("bot-kora", "bot-kora").turns.single()
        val reopened = ChiefBotProfileStore(File(directory, "profiles"))
        assertEquals(before, reopened.loadConversation("bot-kora", "bot-kora").turns.single())
        val secondBrain = ChiefBotConversation(reopened).also { it.select("bot-iris") }
        val iris = secondBrain.prepare("bot-iris", "Training 1", state, model, "new-session")
        assertEquals(0, history(iris).getJSONArray("turns").length())
        assertFalse(iris.prompt.contains(exercise.explanation))
        assertNull(secondBrain.presentationFeedback(turn))
        assertTrue(secondBrain.cancel(iris))
        assertTrue(reopened.get("bot-iris")!!.verifiedCapabilities.isEmpty())
        assertTrue(reopened.loadConversation("bot-iris", "bot-iris").turns.isEmpty())
    }

    @Test fun cancelSelectionModelSessionAndProfileChangesCannotCreateFeedback() = fixture { _, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-kora") }
        fun prepare() = brain.prepare("bot-kora", "Training 1", state, model, "s")
        val cancelled = prepare()
        assertTrue(brain.cancel(cancelled))
        assertFalse(brain.complete(cancelled, "{}", model, "s"))
        assertNull(brain.presentationFeedback(cancelled))
        val switched = prepare()
        brain.select("bot-iris")
        assertFalse(brain.complete(switched, "{}", model, "s"))
        assertNull(brain.presentationFeedback(switched))
        brain.select("bot-kora")
        val changedModel = prepare()
        assertFalse(brain.complete(changedModel, "{}", model.copy(sha256 = "b".repeat(64)), "s"))
        assertNull(brain.presentationFeedback(changedModel))
        val changedSession = prepare()
        assertFalse(brain.complete(changedSession, "{}", model, "other"))
        assertNull(brain.presentationFeedback(changedSession))
        val changedProfile = prepare()
        val profile = store.get("bot-kora")!!
        store.rename(profile.agentId, "Kora geändert", profile.profileVersion)
        assertFalse(brain.complete(changedProfile, "{}", model, "s"))
        assertNull(brain.presentationFeedback(changedProfile))
        assertTrue(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
    }

    @Test fun expiredInferenceAndExpiryDuringDurableCommitLeaveNoVisibleFeedback() = fixture { _, store, state ->
        var now = 0L
        val deadline = TimeUnit.MILLISECONDS.toNanos(store.get("bot-kora")!!.resourceBudget.deadlineMillis)
        val expiredBrain = ChiefBotConversation(store, nanoTime = { now }).also { it.select("bot-kora") }
        val expired = expiredBrain.prepare("bot-kora", "Training 1", state, model, "s")
        now = deadline
        assertFalse(expiredBrain.complete(expired, "{}", model, "s"))
        assertNull(expiredBrain.presentationFeedback(expired))

        var armed = false
        var checks = 0
        val commitBrain = ChiefBotConversation(store, nanoTime = {
            if (armed && ++checks >= 3) deadline else 0L
        }).also { it.select("bot-kora") }
        val turn = commitBrain.prepare("bot-kora", "Training 1", state, model, "s")
        val before = store.get("bot-kora")!!
        armed = true
        assertFalse("Expiry after the first storage check must reject the profile commit",
            commitBrain.complete(turn, "{}", model, "s"))
        assertNull(commitBrain.presentationFeedback(turn))
        assertEquals(before, store.get("bot-kora"))
        assertTrue(store.loadConversation("bot-kora", "bot-kora").turns.isEmpty())
    }

    @Test fun plainConversationHasNoFeedbackAndOversizedPracticeAnswersCannotCommit() = fixture { _, store, state ->
        val brain = ChiefBotConversation(store).also { it.select("bot-kora") }
        val ordinary = brain.prepare("bot-kora", "Erkläre mir die Übungsfunktion.", state, model, "s")
        assertTrue(brain.complete(ordinary, "Eine normale private Antwort.", model, "s"))
        assertNull(brain.presentationFeedback(ordinary))
        assertNull(store.loadConversation("bot-kora", "bot-kora").turns.single().practiceFeedback)
        val oversized = brain.prepare("bot-kora", "Training 1", state, model, "s")
        rejects { brain.complete(oversized, "ä".repeat(3001), model, "s") }
        assertNull(brain.presentationFeedback(oversized))
        assertEquals(1, store.loadConversation("bot-kora", "bot-kora").turns.size)
    }

    @Test fun codecReadsBothLegacyFormatsAndRoundTripsBoundedV3Feedback() = fixture { _, store, _ ->
        val profile = store.get("bot-kora")!!
        val legacy = ChiefBotConversationEntry("Alte Frage", "Unveränderte alte Antwort", model.id, model.version, model.sha256)
        for (version in 1..2) {
            val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
                out.writeUTF("BOT_CONVERSATION_V$version"); out.writeUTF(profile.agentId); out.writeUTF(profile.memoryNamespace)
                out.writeBoolean(false); out.writeLong(2); out.writeInt(1)
                for (text in listOf(legacy.request, legacy.response)) {
                    if (version == 1) out.writeUTF(text)
                    else { val encoded = text.toByteArray(Charsets.UTF_8); out.writeInt(encoded.size); out.write(encoded) }
                }
                out.writeUTF(model.id); out.writeUTF(model.version); out.writeUTF(model.sha256)
            } }.toByteArray()
            val decoded = ChiefBotConversationCodec.decode(profile, bytes)
            assertEquals(listOf(legacy), decoded.turns)
            assertEquals(2, decoded.discardedTurns)
            assertNull(decoded.turns.single().practiceFeedback)
            if (version == 2) assertArrayEquals("Ordinary history must remain byte-compatible with the legacy format",
                bytes, ChiefBotConversationCodec.encode(profile, decoded))
        }
        val task = ChiefBotPractice.select(profile.agentId, "Training 1")!!
        val response = answer(task)
        val feedback = ChiefBotPractice.evaluate(task, response)
        val entry = ChiefBotConversationEntry("Training 1", response, model.id, model.version, model.sha256, feedback)
        val encoded = ChiefBotConversationCodec.encode(profile, ChiefBotConversationHistory(listOf(entry)))
        DataInputStream(ByteArrayInputStream(encoded)).use { assertEquals("BOT_CONVERSATION_V3", it.readUTF()) }
        assertEquals(entry, ChiefBotConversationCodec.decode(profile, encoded).turns.single())
        val followedByOrdinaryReply = ChiefBotConversationHistory(listOf(entry, legacy))
        val mixed = ChiefBotConversationCodec.encode(profile, followedByOrdinaryReply)
        DataInputStream(ByteArrayInputStream(mixed)).use { assertEquals("BOT_CONVERSATION_V3", it.readUTF()) }
        assertEquals(followedByOrdinaryReply.turns, ChiefBotConversationCodec.decode(profile, mixed).turns)
        rejects { ChiefBotConversationCodec.encode(store.get("bot-link")!!, ChiefBotConversationHistory(listOf(entry))) }
        rejects { ChiefBotConversationEntry("Training 1", "Andere Antwort", model.id, model.version, model.sha256, feedback) }
        rejects { ChiefBotConversationEntry("Training 1", response, model.id, model.version, model.sha256,
            feedback.copy(outcome = ChiefBotPracticeOutcome.INCORRECT)) }
    }

    @Test fun historyBudgetIncludesCorrectionsAndEvictsWholeEntries() = fixture { _, store, _ ->
        val profile = store.get("bot-kora")!!
        val task = ChiefBotPractice.select(profile.agentId, "Training 1")!!
        val response = "x".repeat(5_900)
        val feedback = ChiefBotPractice.evaluate(task, response)
        val entry = ChiefBotConversationEntry("Training 1", response, model.id, model.version, model.sha256, feedback)
        var history = ChiefBotConversationHistory(emptyList())
        repeat(16) { history = history.append(entry) }
        assertTrue(history.turns.sumOf { it.textBytes } <= ChiefBotConversationHistory.MAX_TEXT_BYTES)
        assertEquals(16L - history.turns.size, history.discardedTurns)
        assertTrue(history.turns.all { it.response == response && it.practiceFeedback == feedback })
        val bytes = ChiefBotConversationCodec.encode(profile, history)
        assertTrue(bytes.size <= ChiefBotConversationCodec.MAX_BYTES)
        assertEquals(history.turns, ChiefBotConversationCodec.decode(profile, bytes).turns)
    }
}
