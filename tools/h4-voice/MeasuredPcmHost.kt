package at.heinz.deviceai

private var total = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    total++
    try { block(); println("ok $total - $name") }
    catch (e: Throwable) { failed++; println("not ok $total - $name: ${e.message}") }
}
private fun checkNear(actual: Float?, expected: Float?, delta: Float = .0001f) {
    if (expected == null) check(actual == null) { "expected null, got $actual" }
    else check(actual != null && kotlin.math.abs(actual - expected) <= delta) {
        "expected $expected, got $actual"
    }
}
fun main() {
    val signal = TtsHologramSignal()
    val method = signal.javaClass.declaredMethods.firstOrNull {
        it.name.startsWith("measuredLevel") && it.parameterCount == 2
    }?.apply { isAccessible = true }
    fun measured(id: String, time: Long): Float? = method?.invoke(signal, id, time) as Float?

    test("required nullable PCM evidence interface exists") {
        check(method != null) { "measuredLevel missing; silent and unavailable both equal level=0" }
    }
    test("legacy level API cannot distinguish unavailable from silence") {
        checkNear(signal.level(1_000), 0f)
        signal.activate("a")
        signal.begin("a", 8_000, 2, 1)
        checkNear(signal.level(1_000), 0f)
    }
    if (method != null) {
        test("no PCM before synthesis is unavailable") { checkNear(measured("a",1_000), null) }
        signal.append("a", ByteArray(1_600))
        test("PCM before audible anchor is unavailable") { checkNear(measured("a",1_000), null) }
        signal.anchor("a", 0, 1_000)
        test("measured silent PCM is zero rather than missing") { checkNear(measured("a",1_050), 0f) }
        test("incorrect utterance cannot read current signal") { checkNear(measured("old",1_050), null) }
        signal.append("a", ByteArray(1_600) { if(it % 2 == 0) 0 else 64 })
        test("audible nonzero PCM is measured") { checkNear(measured("a",1_150), .5f, .001f) }
        test("finished buffer has measured zero") { checkNear(measured("a",1_200), 0f) }
        signal.activate("b")
        test("old utterance cannot read next utterance") { checkNear(measured("a",1_100), null) }
        signal.begin("b", 8_000, 2, 1)
        signal.append("b", ByteArray(1_600) { if(it%2 == 0) 0 else 64 })
        signal.anchor("b",0,2_000)
        test("new utterance has isolated PCM") { checkNear(measured("b",2_025), .5f, .001f) }
        signal.begin("a",8_000,2,1)
        signal.append("a",ByteArray(1_600))
        signal.anchor("a",0,2_000)
        test("delayed old callbacks cannot corrupt new signal") { checkNear(measured("b",2_025), .5f, .001f) }
        signal.stop()
        test("stopped utterance cannot expose stale PCM") { checkNear(measured("b",2_025), null) }
    }
    println("TOTAL=$total PASS=${total-failed} FAIL=$failed")
    if (failed != 0) kotlin.system.exitProcess(1)
}
