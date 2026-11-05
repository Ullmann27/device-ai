import org.junit.Test

fun main() {
    val klasses=listOf(
        "at.heinz.deviceai.learning.MemoryNegationExactnessR9Test",
        "at.heinz.deviceai.learning.MemoryRetrieverPolarityAndLimitR8Test",
        "at.heinz.deviceai.learning.MemoryNegationNearNeighborR9Test",
        "at.heinz.deviceai.learning.MemoryRetrieverAtomicIndexR7Test",
        "at.heinz.deviceai.learning.MemoryRetrieverIndexSnapshotR6Test",
    )
    var good=0; var bad=0
    for(name in klasses) {
        val k=Class.forName(name)
        for(m in k.declaredMethods.filter{it.isAnnotationPresent(Test::class.java)}.sortedBy{it.name}) {
            try {
                m.invoke(k.getDeclaredConstructor().newInstance())
                good++
                println("PASS $name.${m.name}")
            } catch(t:Throwable) {
                bad++
                val root=t.cause?:t
                println("FAIL $name.${m.name}: ${root.javaClass.simpleName}: ${root.message}")
            }
        }
    }
    println("TOTAL=${good+bad} PASS=$good FAIL=$bad")
    if(good+bad!=20 || bad!=0)kotlin.system.exitProcess(1)
}
