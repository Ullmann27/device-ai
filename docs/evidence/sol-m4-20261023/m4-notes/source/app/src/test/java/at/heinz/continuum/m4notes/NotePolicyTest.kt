package at.heinz.continuum.m4notes

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePolicyTest {
    @Test fun trimsWhitespace() { assertEquals("Hallo", NotePolicy.normalized("  Hallo \n")) }
    @Test fun boundsNotes() { assertEquals(4000, NotePolicy.normalized("x".repeat(5000)).length) }
    @Test fun keepsEmptyNotes() { assertEquals("", NotePolicy.normalized("   ")) }
}
