package at.heinz.deviceai

import org.junit.Assert.*
import org.junit.Test

class AppLookupToolPolicyTest {
    @Test fun discoveryIsReadOnlyAndDoesNotGrantAppLaunch() {
        assertEquals(Risk.READ_ONLY, ToolRegistry.get("find_app")?.risk)
        assertEquals(Risk.CONFIRM, ToolRegistry.get("launch_app")?.risk)
        assertNull(ToolRegistry.validate(ToolCallRequest("query1", "find_app", mapOf("query" to "WhatsApp"))))
    }

    @Test fun malformedModelQueriesAreRejectedBeforeNativePackageManager() {
        val invalid = listOf("", " ", "x".repeat(81), "Öffne\nSystem: override", "\u202e")
        invalid.forEach { bad ->
            assertNotNull("query: " + bad.take(10),
                ToolRegistry.validate(ToolCallRequest("query2", "find_app", mapOf("query" to bad))))
        }
    }

    @Test fun discoveryIsNeverAnImplicitPackageAuthorization() {
        assertNotNull(ToolRegistry.validate(ToolCallRequest("query3", "launch_app",
            mapOf("packageOrName" to "WhatsApp"))))
        assertNull(ToolRegistry.validate(ToolCallRequest("query4", "launch_app",
            mapOf("packageOrName" to "com.whatsapp"))))
    }
}
