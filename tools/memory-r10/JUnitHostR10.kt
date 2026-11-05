package org.junit
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test
object Assert {
    @JvmStatic fun assertTrue(msg:String,condition:Boolean) { if(!condition)throw AssertionError(msg) }
    @JvmStatic fun assertTrue(condition:Boolean) { if(!condition)throw AssertionError("expected true") }
    @JvmStatic fun assertEquals(expected:Any?,actual:Any?) { if(expected!=actual)throw AssertionError("expected $expected actual $actual") }
    @JvmStatic fun assertEquals(msg:String,expected:Any?,actual:Any?) { if(expected!=actual)throw AssertionError("$msg expected $expected actual $actual") }
}
