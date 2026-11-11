package thermal.render

import at.heinz.deviceai.ui.*
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

object FlowMathCounter {
    var calls = 0
    @JvmStatic fun sin(value: Double): Double {
        calls++
        return Math.sin(value)
    }
}

private class FlowLoader(private val classes: Path) : ClassLoader(FlowMathCounter::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (!name.startsWith("at.heinz.deviceai.ui.JarvisEnergyFlows")) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            val bytes = Files.readAllBytes(classes.resolve(name.replace('.', '/') + ".class"))
            val reader = ClassReader(bytes)
            val writer = ClassWriter(0)
            reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(access: Int, name: String, descriptor: String,
                                         signature: String?, exceptions: Array<out String>?): MethodVisitor {
                    return object : MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String,
                                                     descriptor: String, isInterface: Boolean) {
                            if (opcode == Opcodes.INVOKESTATIC && owner == "java/lang/Math" &&
                                name == "sin" && descriptor == "(D)D")
                                super.visitMethodInsn(opcode, "thermal/render/FlowMathCounter", name, descriptor, false)
                            else super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        }
                    }
                }
            }, 0)
            val counted = writer.toByteArray()
            return defineClass(name, counted, 0, counted.size).also { if (resolve) resolveClass(it) }
        }
    }
}

private fun encoded(value: Any?): String = when (value) {
    null -> "null"
    is Float -> "f:${value.toRawBits()}"
    is IntArray -> value.joinToString(",", "i[", "]")
    is FloatArray -> value.joinToString(",", "f[", "]") { it.toRawBits().toString() }
    is VectorPath -> value.ops.joinToString(";", "path[", "]") { encoded(it) }
    is VectorPath.Op.Move -> "move:${encoded(value.x)},${encoded(value.y)}"
    is VectorPath.Op.Line -> "line:${encoded(value.x)},${encoded(value.y)}"
    is VectorPath.Op.Cubic -> listOf(value.x1, value.y1, value.x2, value.y2, value.x, value.y)
        .joinToString(",", "cubic[") { encoded(it) }
    is Fill.Linear -> listOf(value.x0, value.y0, value.x1, value.y1, value.colors, value.stops)
        .joinToString(",", "linear[") { encoded(it) }
    is Fill.Radial -> listOf(value.cx, value.cy, value.radius, value.colors, value.stops)
        .joinToString(",", "radial[") { encoded(it) }
    is Fill.Sweep -> listOf(value.cx, value.cy, value.colors, value.stops)
        .joinToString(",", "sweep[") { encoded(it) }
    is Fill.Solid -> "solid:${value.argb}"
    else -> value.toString()
}

fun main(args: Array<String>) {
    require(args.size == 2)
    val target = FlowLoader(Path.of(args[0])).loadClass("at.heinz.deviceai.ui.JarvisEnergyFlows")
    val instance = target.getField("INSTANCE").get(null)
    val draw = target.getMethod("draw", ContinuumPainter::class.java,
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
    val maxCalls = args[1].toInt()
    var cases = 0
    var highestCalls = 0
    val digest = MessageDigest.getInstance("SHA-256")
    val painter = Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
        arrayOf(ContinuumPainter::class.java)) { _, method, values ->
        val line = method.name + ":" + values.orEmpty().joinToString("|") { encoded(it) } + "\n"
        digest.update(line.toByteArray(Charsets.UTF_8))
        when (method.name) {
            "measureText", "capHeight" -> 0f
            "drawRaster" -> false
            else -> null
        }
    } as ContinuumPainter
    for ((w, h) in listOf(600f to 270f, 884f to 760f, 412f to 915f, 884.5f to 760.25f)) {
        for (time in listOf(0f, 3f, 3.016f, 20f, 999.99f, -1f)) {
            for (energy in listOf(0f, .18f, 1f)) {
                FlowMathCounter.calls = 0
                draw.invoke(instance, painter, w, h, time, energy, true)
                highestCalls = maxOf(highestCalls, FlowMathCounter.calls)
                cases++
            }
        }
    }
    println("CASES=$cases")
    println("MAX_SIN_CALLS=$highestCalls")
    println("DRAW_COMMAND_SHA256=" + digest.digest().joinToString("") { "%02x".format(it) })
    check(highestCalls <= maxCalls) { "repeated control-point work: $highestCalls > $maxCalls" }
}
