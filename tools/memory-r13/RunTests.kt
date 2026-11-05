package at.heinz.deviceai.learning

import org.junit.Test
import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Specify test classes" }
    var passed = 0
    var failed = 0
    for (name in args) {
        val cls = Class.forName("at.heinz.deviceai.learning.$name")
        val methods = cls.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }
        require(methods.isNotEmpty()) { "No tests in $name" }
        for (method in methods) {
            val instance = cls.getDeclaredConstructor().newInstance()
            try {
                method.invoke(instance)
                passed++
                println("PASS $name.${method.name}")
            } catch (error: InvocationTargetException) {
                failed++
                println("FAIL $name.${method.name}: ${error.targetException}")
                error.targetException.printStackTrace(System.out)
            }
        }
    }
    println("TOTAL=${passed + failed} PASS=$passed FAIL=$failed")
    exitProcess(if (failed == 0) 0 else 1)
}
