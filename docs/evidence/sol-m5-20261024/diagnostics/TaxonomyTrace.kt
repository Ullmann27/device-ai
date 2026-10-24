package at.heinz.deviceai.intelligence.maxultra

import at.heinz.deviceai.learning.*
import java.io.File
import java.nio.file.Files
import java.util.Base64

/** Diagnostic traversal of the 27 already-public TEST2 errors, using frozen B2 sources. */
fun main(args: Array<String>) {
    require(args.size == 4) { "usage: wrong-ids.txt swb1.base64 traces.json scratch-directory" }
    val selected = File(args[0]).readLines().filter { it.isNotBlank() }.toSet()
    require(selected.size == 27)
    val corpus = Ctx2Corpus.split(Ctx1Corpus.Split.TEST, 20261104L, 40)
    val corpusHash = Ctx1Corpus.sha256(Ctx1Corpus.canonical(corpus))
    require(corpusHash == "2a7242bf6a93a2366fb36275f431cfeeba8ff7f651c3033e8617ca73c4c9222a")
    val dialogs = corpus.filter { it.id in selected }
    require(dialogs.size == selected.size)
    val bytes = Base64.getDecoder().decode(File(args[1]).readText().trim())
    val directory = Files.createTempDirectory(File(args[3]).toPath(), "dialogue-").toFile()
    val traces = arrayListOf<Any>()
    try {
        for (dialog in dialogs) {
            val experiences = dialog.facts.map {
                LearningExperienceFactory.create(
                    LearningExperienceType.USER_FACT, LearningProvenance.USER_CONFIRMED,
                    LearningTrustClass.OWNER_CONFIRMED, LearningStatus.CONFIRMED, it, now = 1)
            }
            val byId = experiences.associate { it.id to it.content }
            val core = ContinuumMemoryDialogue(directory)
            core.clear()
            val context = Ctx1DialogueContext(dialog.facts, generalized = true)
            val turns = dialog.turns.mapIndexed { index, turn ->
                val decision = context.decide(turn.text)
                var cited = emptyList<String>()
                if (decision is Ctx1DialogueContext.Ask) {
                    val allowed = decision.scope?.let { scope ->
                        experiences.filter { it.content in scope }
                    } ?: experiences
                    cited = core.reply(bytes, allowed, decision.query.take(512), index + 1L)
                        .memoryIds.map { byId.getValue(it) }
                    context.observe(cited)
                }
                linkedMapOf(
                    "turn" to index + 1, "input" to turn.text,
                    "gold" to (turn.gold as? Ctx1Corpus.Memory)?.facts?.toList(),
                    "decision" to decision.toString(),
                    "query" to (decision as? Ctx1DialogueContext.Ask)?.query,
                    "scope" to (decision as? Ctx1DialogueContext.Ask)?.scope?.toList(),
                    "cited" to cited)
            }
            traces += linkedMapOf(
                "id" to dialog.id, "family" to dialog.family, "facts" to dialog.facts,
                "registry" to context.registry.map { entity ->
                    linkedMapOf("surface" to entity.surface,
                        "genders" to entity.genders.map { it.name },
                        "aliases" to entity.aliases.toList(), "known" to entity.known)
                }, "turns" to turns)
        }
    } finally {
        directory.deleteRecursively()
    }
    File(args[2]).writeText(Ctx1Experiment.json(
        linkedMapOf("corpusSha256" to corpusHash, "dialogs" to traces)) + "\n")
    println("Traced ${traces.size} known-open WRONG dialogs with frozen repaired B2; corpus=$corpusHash")
}
