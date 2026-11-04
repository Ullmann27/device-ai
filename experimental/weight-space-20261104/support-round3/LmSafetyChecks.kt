package at.heinz.deviceai.learning.lm

import java.security.MessageDigest
import java.nio.ByteBuffer

object LmSafetyChecks {
    private var passed = 0
    private var failed = 0
    private fun params(arch: LmArch = LmArch.MINGRU) =
        LmParams.init(LmConfig(arch, 258, 8, 1, heads = 2, context = 32, mlp = 16), 20261104L)
    private fun grads(p: LmParams, scale: Float = 1f) = p.zeros().also { values ->
        values.values.forEach { m -> m.data.indices.forEach { i -> m.data[i] = scale * ((i % 7) - 3) / 17f } }
    }
    private fun hash(p: LmParams): String {
        val d = MessageDigest.getInstance("SHA-256")
        val b = ByteBuffer.allocate(4)
        p.mats.values.forEach { m -> m.data.forEach { x -> b.clear(); b.putInt(x.toRawBits()); d.update(b.array()) } }
        return LmBpeTokenizer.hex(d.digest())
    }
    private fun report(name: String, ok: Boolean, detail: String) {
        if (ok) passed++ else failed++
        println("CASE\t$name\t${if (ok) "PASS" else "FAIL"}\t$detail")
    }
    private fun invalid(name: String, lr: Float = .001f, clip: Float = 1f,
                        mutate: (LmParams, LinkedHashMap<String, Mat>) -> Unit = { _, _ -> }) {
        val p = params(); val control = params()
        val opt = LmAdamW(p, .01f); val expected = LmAdamW(control, .01f)
        opt.step(grads(p), .001f, 1f); expected.step(grads(control), .001f, 1f)
        val before = hash(p); val step = opt.steps
        val bad = grads(p); mutate(p, bad)
        val rejected = runCatching { opt.step(bad, lr, clip) }.isFailure
        val unchanged = hash(p) == before && opt.steps == step
        val recovered = runCatching {
            opt.step(grads(p, .3f), .001f, 1f)
            expected.step(grads(control, .3f), .001f, 1f)
            hash(p) == hash(control) && opt.steps == expected.steps
        }.getOrDefault(false)
        report(name, rejected && unchanged && recovered,
            "rejected=$rejected;unchanged=$unchanged;future_state_equal=$recovered")
    }
    private fun constructor(name: String, create: () -> Any) =
        report(name, runCatching(create).isFailure, "constructor_rejection")

    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty())
        invalid("nan_gradient") { _, g -> g.getValue("nf").data[7] = Float.NaN }
        invalid("positive_infinity_gradient") { _, g -> g.getValue("nf").data[7] = Float.POSITIVE_INFINITY }
        invalid("negative_infinity_gradient") { _, g -> g.getValue("tok").data[0] = Float.NEGATIVE_INFINITY }
        invalid("missing_gradient") { _, g -> g.remove("nf") }
        invalid("extra_gradient") { _, g -> g["foreign"] = Mat(1, 1, floatArrayOf(100f)) }
        invalid("wrong_shape_same_size") { _, g -> g["nf"] = Mat(8, 1, g.getValue("nf").data.copyOf()) }
        invalid("short_gradient") { _, g -> g["nf"] = Mat(1, 7) }
        invalid("gradient_aliases_parameter") { p, g -> g["tok"] = p.mats.getValue("tok") }
        invalid("negative_rate", lr = -.001f)
        invalid("nan_rate", lr = Float.NaN)
        invalid("infinite_rate", lr = Float.POSITIVE_INFINITY)
        invalid("zero_clip", clip = 0f)
        invalid("negative_clip", clip = -1f)
        invalid("nan_clip", clip = Float.NaN)
        invalid("infinite_clip", clip = Float.POSITIVE_INFINITY)
        invalid("finite_rate_overflow", lr = Float.MAX_VALUE)
        invalid("finite_second_moment_overflow", clip = Float.MAX_VALUE) { _, g ->
            g.getValue("nf").data[7] = Float.MAX_VALUE
        }
        constructor("negative_decay") { LmAdamW(params(), -.01f) }
        constructor("nan_decay") { LmAdamW(params(), Float.NaN) }
        constructor("negative_beta1") { LmAdamW(params(), .01f, beta1 = -.1f) }
        constructor("unit_beta1") { LmAdamW(params(), .01f, beta1 = 1f) }
        constructor("unit_beta2") { LmAdamW(params(), .01f, beta2 = 1f) }
        constructor("zero_epsilon") { LmAdamW(params(), .01f, eps = 0f) }
        constructor("nan_epsilon") { LmAdamW(params(), .01f, eps = Float.NaN) }
        for (arch in LmArch.entries) for ((name, scale, lr, clip) in listOf(
            arrayOf("normal", 1f, .001f, 1f), arrayOf("tiny", 1e-9f, .001f, 1f),
            arrayOf("clipped", 1e6f, .001f, .5f), arrayOf("zero_grad", 0f, .001f, 1f),
            arrayOf("zero_rate", 1f, 0f, 1f))) {
            val p = params(arch); val opt = LmAdamW(p, .01f)
            repeat(4) { opt.step(grads(p, scale as Float), lr as Float, clip as Float) }
            report("positive_${arch}_$name", p.allFinite() && opt.steps == 4, "weights=${hash(p)}")
        }
        println("SUMMARY\tpassed=$passed\tfailed=$failed")
        if (failed != 0) error("$failed safety failures")
    }
}
