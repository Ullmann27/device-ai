package at.heinz.deviceai.learning.lm

import java.nio.file.Path

object LmSegmentWorker {
    @JvmStatic fun main(args: Array<String>) {
        if (args.contentEquals(arrayOf("describe"))) {
            val f = LmTrainingFixture
            f.fresh().use { trainer ->
                println("DESCRIPTION\tdata=${f.dataSha256}\tseed=${f.SEED}\tbos=${f.tokenizer.bos}" +
                    "\teos=${f.tokenizer.eos}\tvocab=${f.tokenizer.vocabSize}\ttokenizer=${f.tokenizer.sha256}" +
                    "\tparameters=${trainer.model.params.count}\ttotal=24\tchunk=8" +
                    "\ttargets=${(0 until 24).joinToString(",") { s -> f.batch(s).sumOf { it.size - 1 }.toString() }}" +
                    "\trates=${(0 until 24).joinToString(",") { trainer.rate(it).toString() }}")
            }
            return
        }
        require(args.size == 7)
        require(Runtime.getRuntime().availableProcessors() == 1)
        require(Runtime.getRuntime().maxMemory() <= 1024L * 1024 * 1024)
        val mode = args[0]
        require(mode in setOf("run", "verify", "hold-before-checkpoint", "hold-after-checkpoint"))
        require(args[2] == LmTrainingFixture.dataSha256)
        val identity = LmTrainingIdentity(args[1], args[2], LmTrainingFixture.SEED)
        val dir = Path.of(args[3]); val input = args[4]
        val start = args[5].toInt(); val end = args[6].toInt()
        val spec = LmTrainingSpec(.001f, .01f, 3, 32, 1f, 1)
        val tok = LmTrainingFixture.tokenizer
        val trainer = if (input == "NEW") {
            require(start == 0 && mode != "verify"); LmTrainingFixture.fresh()
        } else {
            require(Regex("[0-9a-f]{64}").matches(input))
            LmTrainingState.load(dir.resolve("$input.lmtrain"), input, tok, identity, spec)
        }
        trainer.use {
            require(it.model.config == LmTrainingFixture.config && it.steps == start)
            if (mode == "verify") {
                require(end == start)
                println("VERIFIED\tstep=$start\tstate=$input\tweights=${LmTrainingFixture.weightHash(it.model.params)}")
                return
            }
            require(start >= 0 && end > start && end <= 24)
            println("START\tstart=$start\tend=$end\tinput=$input")
            LmTrainingFixture.advance(it, end)
            if (mode == "hold-before-checkpoint") hold("BEFORE_CHECKPOINT")
            val saved = LmTrainingState.save(dir, it, tok, identity)
            val state = saved.fileName.toString().removeSuffix(".lmtrain")
            val weights = LmTrainingFixture.weightHash(it.model.params)
            println("SAVED\tstep=${it.steps}\tstate=$state\tweights=$weights")
            System.out.flush()
            if (mode == "hold-after-checkpoint") hold("AFTER_CHECKPOINT")
            println("DONE\tstart=$start\tend=${it.steps}\tstate=$state\tweights=$weights" +
                "\tdata=${LmTrainingFixture.dataSha256}\tparameters=${it.model.params.count}")
        }
    }
    private fun hold(phase: String) {
        println("HOLD\tphase=$phase"); System.out.flush()
        check(System.`in`.read() == 10) { "fault hook requires explicit release" }
    }
}
