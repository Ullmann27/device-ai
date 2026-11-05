package org.junit

/** Host-only JUnit4 signature shim. Not an Android/JUnit runner. */
@Target(AnnotationTarget.FUNCTION) annotation class Test

object Assert {
    @JvmStatic fun assertTrue(message:String,predicate:Boolean) {if(!predicate) throw AssertionError(message)}
    @JvmStatic fun assertTrue(predicate:Boolean) {if(!predicate) throw AssertionError("expected true")}
    @JvmStatic fun assertFalse(predicate:Boolean) {if(predicate) throw AssertionError("expected false")}
    @JvmStatic fun assertEquals(message:String,expected:Any?,actual:Any?) {
        if(expected!=actual)throw AssertionError("$message; expected=$expected actual=$actual")
    }
    @JvmStatic fun assertEquals(expected:Any?,actual:Any?) {
        if(expected!=actual)throw AssertionError("expected=$expected actual=$actual")
    }
}
