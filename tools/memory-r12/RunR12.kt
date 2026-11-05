import at.heinz.deviceai.learning.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException

fun main() {
    var pass=0; var fail=0
    for(clazz in listOf(MemoryNumericEvidenceR12Test::class.java, MemoryOneDigitEvidenceR11Test::class.java, MemoryRepeatedNegationR11Test::class.java)) {
        val instance=clazz.getDeclaredConstructor().newInstance()
        for(method in clazz.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }) {
            try {
                method.invoke(instance)
                println("PASS ${clazz.simpleName}.${method.name}")
                pass++
            } catch(e:InvocationTargetException) {
                println("FAIL ${clazz.simpleName}.${method.name}: ${e.cause}")
                fail++
            }
        }
    }
    println("TOTAL=${pass+fail} PASS=$pass FAIL=$fail")
    if(fail>0) kotlin.system.exitProcess(1)
}
