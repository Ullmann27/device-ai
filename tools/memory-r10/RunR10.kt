import org.junit.Test
fun main() {
    val klasses=listOf("at.heinz.deviceai.learning.MemoryRetrieverFuzzyAmbiguityR10Test", "at.heinz.deviceai.learning.MemoryNegationIndexR10Test")
    var pass=0; var fail=0
    for(name in klasses) {
        val klass=Class.forName(name)
        for(m in klass.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }) {
            try { m.invoke(klass.getDeclaredConstructor().newInstance()); pass++; println("PASS $name.${m.name}") }
            catch(t:Throwable) { fail++; val cause=t.cause ?: t; println("FAIL $name.${m.name}: ${cause.javaClass.simpleName}: ${cause.message}") }
        }
    }
    println("TOTAL=${pass+fail} PASS=$pass FAIL=$fail")
    if(pass+fail!=7 || fail>0) kotlin.system.exitProcess(1)
}
