import at.heinz.deviceai.learning.MemoryRetrieverAtomicIndexR7Test
import at.heinz.deviceai.learning.MemoryRetrieverIndexSnapshotR6Test
import at.heinz.deviceai.learning.MemoryRetrieverPolarityAndLimitR8Test

/** Host-only reflection driver; Android/JUnit remains a separate gate. */
fun main() {
    var passed = 0
    var failed = 0
    val suites = listOf(
        MemoryRetrieverAtomicIndexR7Test::class.java,
        MemoryRetrieverIndexSnapshotR6Test::class.java,
        MemoryRetrieverPolarityAndLimitR8Test::class.java,
    )
    for (cls in suites)
        for (method in cls.declaredMethods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }) {
            try {
                method.invoke(cls.getDeclaredConstructor().newInstance())
                println("PASS ${cls.simpleName}.${method.name}")
                passed++
            } catch (t: Throwable) {
                println("FAIL ${cls.simpleName}.${method.name}: ${t.cause ?: t}")
                failed++
            }
        }
    println("HOST_JVM totals=$passed PASS, $failed FAIL")
    if (failed != 0 || passed != 11) throw AssertionError("R6/R7/R8 host regression FAILED")
}
