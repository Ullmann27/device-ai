import org.junit.runner.JUnitCore
import org.junit.runner.Request
import kotlin.system.exitProcess

fun main(selectors: Array<String>) {
    var count = 0
    var failures = 0
    for (selector in selectors) {
        val parts = selector.split('#', limit = 2)
        val type = Class.forName(parts[0])
        val request = if (parts.size == 2) Request.method(type, parts[1]) else Request.aClass(type)
        val result = JUnitCore().run(request)
        count += result.runCount
        failures += result.failureCount
        println("${if (result.wasSuccessful()) "PASS" else "FAIL"} $selector tests=${result.runCount} failures=${result.failureCount}")
        result.failures.forEach { println(it.trace) }
    }
    println("TESTS_RUN=$count FAILURES=$failures")
    if (failures != 0) exitProcess(1)
}
