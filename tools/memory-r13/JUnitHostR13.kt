package org.junit

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test

/** Host assertions; not JUnit4. */
object Assert {
    @JvmStatic fun assertTrue(message: String, condition: Boolean) {
        if (!condition) throw AssertionError(message)
    }
    @JvmStatic fun assertTrue(condition: Boolean) = assertTrue("Expected true", condition)
    @JvmStatic fun assertFalse(message: String, condition: Boolean) = assertTrue(message, !condition)
    @JvmStatic fun assertFalse(condition: Boolean) = assertFalse("Expected false", condition)
    @JvmStatic fun assertEquals(expected: Any?, actual: Any?) = assertEquals("Values differ", expected, actual)
    @JvmStatic fun assertEquals(message: String, expected: Any?, actual: Any?) {
        if (expected != actual) throw AssertionError("$message: expected <$expected>, actual <$actual>")
    }
    @JvmStatic fun assertEquals(expected: Double, actual: Double, delta: Double) {
        require(delta >= 0.0)
        if (java.lang.Double.compare(expected, actual) != 0 && !(kotlin.math.abs(expected - actual) <= delta)) {
            throw AssertionError("Expected <$expected>, actual <$actual>, delta <$delta>")
        }
    }
    @JvmStatic fun assertNull(message: String, value: Any?) = assertTrue(message, value == null)
    @JvmStatic fun assertNull(value: Any?) = assertNull("Expected null: $value", value)
    @JvmStatic fun assertNotNull(message: String, value: Any?) = assertTrue(message, value != null)
    @JvmStatic fun assertNotNull(value: Any?) = assertNotNull("Expected non-null", value)
}
