package at.heinz.deviceai.learning.lm

import java.security.MessageDigest

object LmTrainingFixture {
    const val SEED = 20261104L
    const val STEPS = 24
    val tokenizer = LmBpeTokenizer.bytes()
    val config = LmConfig(LmArch.MINGRU, 258, 8, 1, context = 32, mlp = 16)
    private val sequences = listOf(
        intArrayOf(256, 97, 98, 97, 257),
        intArrayOf(256, 99, 99, 100, 99, 100, 257),
        intArrayOf(256, 101, 102, 257),
        intArrayOf(256, 103, 104, 103, 105, 104, 103, 257))
    val dataDescription = "LM1_CONTINUITY_FIXTURE_V1\nseed=$SEED\n" +
        sequences.joinToString("\n", postfix = "\n") { it.joinToString(",") } +
        "batch=[step%4,(step*3+1)%4];next-token;no-shuffle\n"
    val dataSha256 = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256")
        .digest(dataDescription.toByteArray(Charsets.UTF_8)))
    fun batch(step: Int): List<IntArray> {
        require(step in 0 until STEPS)
        return listOf(sequences[step % 4].copyOf(), sequences[(step * 3 + 1) % 4].copyOf())
    }
    fun fresh() = trainer(LmParams.init(config, SEED))
    fun trainer(params: LmParams) = LmTrainer(LmModel(params), .001f, .01f, 3, 32, 1f, 1)
    fun weightHash(params: LmParams): String = LmBpeTokenizer.hex(MessageDigest.getInstance("SHA-256")
        .digest(LmCheckpoint.encode(params, tokenizer, mapOf("fixture" to "continuity-v1"))))
    fun advance(trainer: LmTrainer, until: Int) {
        require(trainer.steps <= until && until <= STEPS)
        while (trainer.steps < until) {
            val step = trainer.steps; val rate = trainer.rate(step)
            val batch = batch(step); val loss = trainer.step(batch)
            check(loss.isFinite())
            println("UPDATE\tstep=${trainer.steps}\ttargets=${batch.sumOf { it.size - 1 }}\trate=$rate\tloss=$loss")
        }
    }
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        fresh().use { trainer ->
            advance(trainer, STEPS)
            println("FINAL\tsteps=${trainer.steps}\tweights=${weightHash(trainer.model.params)}\tdata=$dataSha256")
        }
    }
}
