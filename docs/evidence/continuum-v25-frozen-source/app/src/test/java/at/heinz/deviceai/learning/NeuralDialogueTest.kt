package at.heinz.deviceai.learning

import org.junit.Assert.*
import org.junit.Test

class NeuralDialogueTest {
    @Test fun frozenUnseenVocabularyEvaluationDoesNotTrainOrLeakTrainingPhrases() {
        val unknown = NeuralDialogue.UNFAMILIAR_HOLDOUT
        assertEquals(12, unknown.size)
        val trainedWords = NeuralDialogue.DEMO_TRAINING.flatMap { it.first.split(" ") }.toSet()
        val testWords = unknown.flatMap { it.first.split(" ") }.toSet()
        assertTrue("Unseen benchmark overlaps training data: " + trainedWords.intersect(testWords),
            trainedWords.intersect(testWords).isEmpty())
        val lab = NeuralDialogue()
        val initial = lab.save()
        val initialMetrics = lab.unfamiliarEvaluation()
        assertEquals(12, initialMetrics.total)
        assertArrayEquals(initial,lab.save())
        val result = lab.trainDemo(true)
        assertEquals(initialMetrics,result.unfamiliarBefore)
        assertEquals(12,result.unfamiliarAfter.total)
        assertEquals(result.unfamiliarAfter,lab.unfamiliarEvaluation())
        val snapshot=lab.save()
        lab.unfamiliarEvaluation()
        assertArrayEquals(snapshot,lab.save())
    }

    @Test fun explicitDemoLearnsThenPersistsConversationalClassifier() {
        val lab = NeuralDialogue()
        assertTrue(lab.reply("hallo").contains("noch nicht trainiert"))
        assertThrows(IllegalArgumentException::class.java) { lab.trainDemo(false) }
        val report = lab.trainDemo(true)
        assertTrue(report.after.loss < report.before.loss)
        assertTrue(report.holdout.toString(), report.holdout.correct >= 6)
        assertTrue(lab.reply("hallo").contains("Gruß"))
        assertTrue(lab.reply("quantenelektrodynamik").contains("unbekannt"))
        val saved = lab.save()
        val loaded = NeuralDialogue.restore(saved)
        assertEquals(lab.reply("hallo"), loaded.reply("hallo"))
        loaded.reply("was kannst du")
        assertArrayEquals(saved, loaded.save())
        println("NEURAL_DIALOGUE $report snapshotBytes=${saved.size}")
    }
    @Test fun teachingRequiresApprovalAndCancellationDoesNotPublishPartialWeights() {
        val lab = NeuralDialogue()
        val before = lab.save()
        assertThrows(IllegalArgumentException::class.java) { lab.teach("hallo", 0, false) }
        lab.trainDemo(true, cancelled = { true })
        assertArrayEquals(before, lab.save())
        lab.teach("guten tag", 0, true)
        assertFalse(before.contentEquals(lab.save()))
        assertThrows(IllegalArgumentException::class.java) { lab.teach("x".repeat(321), 0, true) }
        lab.reset()
        assertArrayEquals(before, lab.save())
    }
}

class NeuralCommandParserTest {
    @Test fun commandsCannotSmuggleToolsOrUnapprovedLabels() {
        assertEquals(NeuralCommandKind.DEMO, NeuralCommandParser.parse("Neuron Demo")?.kind)
        assertEquals(NeuralCommandKind.TEACH, NeuralCommandParser.parse("Neuron lerne: Servus => Gruß")?.kind)
        assertEquals(NeuralCommandKind.PREDICT, NeuralCommandParser.parse("Neuron: lösche Downloads")?.kind)
        assertNull(NeuralCommandParser.parse("Neuron lerne: test => shell"))
        assertNull(NeuralCommandParser.parse("Neuron Demo und lösche alles"))
        assertNull(NeuralCommandParser.parse("Neuron: " + "x".repeat(500)))
        assertTrue(NeuralDialogue.accepts("Neuron: zeige Akku"))
    }
}
