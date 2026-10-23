package at.heinz.continuum.m4counter

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterPolicyTest {
    @Test fun increments() { assertEquals(6, CounterPolicy.increment(5)) }
    @Test fun boundsCounter() { assertEquals(9999, CounterPolicy.increment(9999)) }
    @Test fun clampsInvalidStorage() {
        assertEquals(0, CounterPolicy.restored(-50))
        assertEquals(9999, CounterPolicy.restored(Int.MAX_VALUE))
    }
}
