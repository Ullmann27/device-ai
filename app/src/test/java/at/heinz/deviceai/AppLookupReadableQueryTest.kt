package at.heinz.deviceai

import at.heinz.deviceai.cognitive.NativeAppCandidate
import at.heinz.deviceai.cognitive.NativeAppLookup
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppLookupReadableQueryTest {
    private val formatPoints = listOf(
        0x00AD, 0x061C, 0x180E, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F,
        0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2060, 0x2061, 0x2062,
        0x2063, 0x2064, 0x2066, 0x2067, 0x2068, 0x2069, 0xFEFF,
        0xFFF9, 0xFFFA, 0xFFFB, 0x1BCA0, 0x1D173, 0xE0001, 0xE0020, 0xE007F
    )
    private val hiddenQueries = formatPoints.flatMap { point ->
        val hidden = String(Character.toChars(point))
        listOf(hidden, hidden + hidden, "X$hidden", "${hidden}X", "X${hidden}Y")
    } + listOf("\u202EX\u202C", "\u200BX\u2066\u2069")
    private val invalidQueries = listOf("", " ", "\t", "\n", "\u00A0", "\u2007", "\u202F",
        "X\u0000", "X\u001F", "X\u007F", "X\u0085", "X\u009F", "X".repeat(81))
    private val app = NativeAppCandidate("org.example.single", "X")

    private fun call(query: String) = ToolCallRequest("query", "find_app", mapOf("query" to query))
    private fun points(query: String) = query.codePoints().toArray().joinToString { "U+%04X".format(it) }

    @Test fun registryRejectsStandaloneEmbeddedAndSupplementaryFormatCharacters() {
        hiddenQueries.forEach { query ->
            assertNotNull(points(query), ToolRegistry.validate(call(query)))
        }
    }

    @Test fun nativeLookupRejectsTheSameFormatCharactersBeforeSearching() {
        hiddenQueries.forEach { query ->
            assertThrows(points(query), IllegalArgumentException::class.java) {
                NativeAppLookup.search(query, listOf(app))
            }
        }
    }

    @Test fun malformedQueriesNeverReachTheRealGateRunner() {
        val calls = AtomicInteger()
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { request ->
            calls.incrementAndGet()
            NativeAppLookup.search(request.arguments.getValue("query") as String, listOf(app)).toString()
        })
        (invalidQueries + hiddenQueries).forEach { query ->
            val result = gate.execute(ToolInvocation("query", call(query)))
            assertEquals(points(query), ToolExecutionStatus.REJECTED, result.record.status)
            assertEquals(points(query), 0, calls.get())
        }
    }

    @Test fun blankControlsAndLengthLimitsRemainConsistent() {
        invalidQueries.forEach { query ->
            assertNotNull(points(query), ToolRegistry.validate(call(query)))
            assertThrows(points(query), IllegalArgumentException::class.java) {
                NativeAppLookup.search(query, listOf(app))
            }
        }
    }

    @Test fun readableSingleCharacterUnicodeAndEightyCharacterQueriesReachLookupOnly() {
        for (query in listOf("X", "Ö", "日", "العربية", "Cafe\u0301", "X".repeat(80))) {
            val candidate = app.copy(label = query)
            val lookups = AtomicInteger()
            val launches = AtomicInteger()
            val gate = ToolGate(ToolPolicy(readOnlyEnabled = true), ToolRunner { request ->
                if (request.name == "launch_app") {
                    launches.incrementAndGet()
                    "unexpected launch"
                } else {
                    lookups.incrementAndGet()
                    val found = NativeAppLookup.search(request.arguments.getValue("query") as String,
                        listOf(candidate))
                    assertTrue(found.exactLabel)
                    found.candidates.single().packageName
                }
            })
            assertNull(points(query), ToolRegistry.validate(call(query)))
            val result = gate.execute(ToolInvocation("query", call(query)))
            assertEquals(points(query), ToolExecutionStatus.SUCCEEDED, result.record.status)
            assertEquals(candidate.packageName, result.output)
            assertEquals(1, lookups.get())
            val launch = ToolCallRequest("launch", "launch_app", mapOf("packageOrName" to candidate.packageName))
            assertEquals(ToolExecutionStatus.REJECTED,
                gate.execute(ToolInvocation("launch", launch)).record.status)
            assertEquals(0, launches.get())
        }
    }
}
