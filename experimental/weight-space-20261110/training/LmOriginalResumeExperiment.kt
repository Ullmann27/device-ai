package at.heinz.deviceai.learning.lm

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

object LmOriginalResumeExperiment {
    private const val TOKENIZER_SHA = "94375c73d1f6e2d04b790738580143fa8c1b9b456d40fa7eb6b4009877bd892f"
    private val texts = listOf("Die Lampe ist blau.", "Das Fenster bleibt offen.")
    private val spec = LmTrainingSpec(.00001f, .01f, 0, 2, 1f, 1)
    private val metadata = mapOf("experiment" to "resume-20261110", "purpose" to "software-diagnostic")
    private fun hash(bytes: ByteArray) = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun read(path: Path): ByteArray {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        require(Files.size(path) in 1..67_108_864)
        return Files.readAllBytes(path)
    }
    private fun batch(tokenizer: LmBpeTokenizer, step: Int): List<IntArray> {
        require(step in 0..1)
        return listOf(intArrayOf(tokenizer.bos) + tokenizer.encode(texts[step]) + tokenizer.eos)
    }
    private fun dataIdentity(tokenizer: LmBpeTokenizer): String {
        val description = "LM1_ORIGINAL_RESUME_V1\n" + texts.indices.joinToString("\n", postfix = "\n") {
            batch(tokenizer, it).single().joinToString(",")
        }
        return hash(description.toByteArray(Charsets.UTF_8))
    }
    private fun export(path: Path, params: LmParams, tokenizer: LmBpeTokenizer) {
        val bytes = LmCheckpoint.encode(params, tokenizer, metadata)
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW)
        println("WEIGHTS\tfile=${path.fileName}\tsha256=${hash(bytes)}\tparameters=${params.count}")
    }
    private fun advance(trainer: LmTrainer, tokenizer: LmBpeTokenizer, arm: String, until: Int) {
        require(trainer.steps <= until && until <= 2)
        while (trainer.steps < until) {
            val step = trainer.steps
            val input = batch(tokenizer, step)
            require(input.all { it.size - 1 <= trainer.model.config.context })
            val loss = trainer.step(input)
            check(loss.isFinite())
            println("UPDATE\tarm=$arm\tstep=${trainer.steps}\ttargets=${input.sumOf { it.size - 1 }}\tloss=$loss")
        }
    }
    private fun describe(trainer: LmTrainer, arm: String) {
        val state = trainer.optimizerState()
        val first = state.first.values.sumOf { a -> a.count { it != 0f }.toLong() }
        val second = state.second.values.sumOf { a -> a.count { it != 0f }.toLong() }
        check(first > 0 && second > 0)
        println("MOMENTS\tarm=$arm\tstep=${state.steps}\tfirstNonzero=$first\tsecondNonzero=$second")
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 7) { "inspect|run checkpoint checkpointSha tokenizer parameterCount sourceSha output" }
        val mode = args[0]
        require(mode == "inspect" || mode == "run")
        val baseline = Path.of(args[1]); val baselineSha = args[2]
        val tokenizerPath = Path.of(args[3]); val expectedCount = args[4].toInt()
        require(expectedCount in setOf(132_160, 16_011_776))
        require(Regex("[0-9a-f]{64}").matches(baselineSha))
        val tokenizerBytes = read(tokenizerPath)
        require(hash(tokenizerBytes) == TOKENIZER_SHA)
        val tokenizer = LmBpeTokenizer.deserialize(tokenizerBytes)
        val dataSha = dataIdentity(tokenizer)
        val identity = LmTrainingIdentity(args[5], dataSha, 20261110L)
        fun fresh(): LmTrainer {
            val bytes = read(baseline)
            require(hash(bytes) == baselineSha)
            val params = LmCheckpoint.decode(bytes, tokenizer).params
            require(params.count == expectedCount)
            require((0..1).all { batch(tokenizer, it).single().size - 1 <= params.config.context })
            return spec.trainer(params)
        }
        println("IDENTITY\tmodel=$baselineSha\ttokenizer=${tokenizer.sha256}\tdata=$dataSha\tparameters=$expectedCount\tbos=${tokenizer.bos}\teos=${tokenizer.eos}")
        if (mode == "inspect") {
            fresh().use { println("INSPECT\tparameters=${it.model.params.count}\tupdates=0\tcontext=${it.model.config.context}") }
            return
        }
        val out = Path.of(args[6])
        Files.createDirectory(out)
        val continuousDir = Files.createDirectory(out.resolve("continuous"))
        val resumedDir = Files.createDirectory(out.resolve("resumed"))
        val continuousHash = fresh().use { trainer ->
            export(out.resolve("initial.lm1"), trainer.model.params, tokenizer)
            advance(trainer, tokenizer, "continuous", 2)
            describe(trainer, "continuous")
            export(continuousDir.resolve("final.lm1"), trainer.model.params, tokenizer)
            LmTrainingState.sha(LmTrainingState.save(continuousDir, trainer, tokenizer, identity))
        }
        val firstState = fresh().use { trainer ->
            advance(trainer, tokenizer, "resumed", 1)
            describe(trainer, "intermediate")
            export(resumedDir.resolve("intermediate.lm1"), trainer.model.params, tokenizer)
            LmTrainingState.save(resumedDir, trainer, tokenizer, identity)
        }
        val resumedHash = LmTrainingState.load(firstState, LmTrainingState.sha(firstState), tokenizer, identity, spec).use { trainer ->
            check(trainer.steps == 1)
            advance(trainer, tokenizer, "resumed", 2)
            describe(trainer, "resumed")
            export(resumedDir.resolve("final.lm1"), trainer.model.params, tokenizer)
            LmTrainingState.sha(LmTrainingState.save(resumedDir, trainer, tokenizer, identity))
        }
        check(continuousHash == resumedHash) { "complete training states differ" }
        check(hash(read(baseline)) == baselineSha && hash(read(tokenizerPath)) == TOKENIZER_SHA)
        println("RESULT\tstatus=PASS\toptimizerUpdates=4\tcommittedStepsPerArm=2\tstateSha256=$continuousHash\tlanguageQuality=NOT_MEASURED")
    }
}
