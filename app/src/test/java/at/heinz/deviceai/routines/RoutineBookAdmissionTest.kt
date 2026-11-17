package at.heinz.deviceai.routines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineBookAdmissionTest {
    @Test fun changingTheCallerToolSetCannotExpandAnAdmittedRoutine() {
        val tools = mutableSetOf("get_battery_info")
        val book = RoutineBook()
        assertTrue(book.add(Routine("battery", "Read battery", RoutineTrigger.OnEvent("charging"), tools)))

        tools += "install_apk"
        tools.remove("get_battery_info")

        assertEquals(setOf("get_battery_info"), book.triggered("charging").single().allowedTools)
    }

    @Test fun aReturnedClaimCannotChangeTheStoredToolScope() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("battery", "Read battery", RoutineTrigger.Every(900_000, 0),
            mutableSetOf("get_battery_info"))))
        val claimed = book.due(0).single()

        runCatching { (claimed.allowedTools as MutableSet<String>).add("install_apk") }

        assertEquals(setOf("get_battery_info"), claimed.allowedTools)
        assertTrue(book.release("battery"))
        assertEquals(setOf("get_battery_info"), book.due(1).single().allowedTools)
    }

    @Test fun aLargePeriodCannotOverflowIntoAnImmediatelyDueRoutine() {
        val book = RoutineBook()
        assertTrue(book.add(Routine("long-period", "Read status", RoutineTrigger.Every(Long.MAX_VALUE, 0))))
        assertEquals(listOf("long-period"), book.due(100).map { it.id })

        assertTrue(book.report("long-period", "Read complete", 100) is RoutineOutcome.News)

        assertTrue("overflow must not schedule another immediate execution", book.due(100).isEmpty())
        assertTrue(book.due(Long.MAX_VALUE - 1).isEmpty())
    }
}
