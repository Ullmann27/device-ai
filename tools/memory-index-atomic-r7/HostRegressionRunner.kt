import at.heinz.deviceai.learning.MemoryRetrieverAtomicIndexR7Test
import at.heinz.deviceai.learning.MemoryRetrieverIndexSnapshotR6Test

/** Runs the six original Android-JUnit method bodies against the complete production retriever file. */
fun main() {
    var passed=0;var failed=0
    for (cls in listOf(MemoryRetrieverAtomicIndexR7Test::class.java,MemoryRetrieverIndexSnapshotR6Test::class.java))
        for (method in cls.declaredMethods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }) {
            try { method.invoke(cls.getDeclaredConstructor().newInstance()); println("PASS ${method.name}");passed++ }
            catch(t:Throwable){ println("FAIL ${method.name}: ${t.cause?:t}");failed++ }
        }
    println("HOST_JVM totals=$passed PASS, $failed FAIL")
    if(failed>0 || passed!=6) throw AssertionError("Host regression FAILED")
}
