package at.heinz.deviceai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun destructiveToolsStayDestructive() {
        assertEquals(Risk.DESTRUCTIVE, ToolRegistry.risk("delete_file"))
        assertEquals(Risk.DESTRUCTIVE, ToolRegistry.risk("uninstall_app"))
    }

    @Test
    fun installsNeedConfirmation() {
        assertEquals(Risk.CONFIRM, ToolRegistry.risk("install_apk"))
        assertEquals(Risk.CONFIRM, ToolRegistry.risk("restart_app"))
    }

    @Test
    fun validationRejectsUnknownTools() {
        val call = ToolCallRequest("1", "run_any_shell", emptyMap())
        assertNotNull(ToolRegistry.validate(call))
    }

    @Test
    fun validationRequiresArguments() {
        val missing = ToolCallRequest("1", "install_apk", emptyMap())
        assertNotNull(ToolRegistry.validate(missing))

        val valid = ToolCallRequest(
            "1",
            "install_apk",
            mapOf("path" to "/sdcard/Download/app.apk")
        )
        assertNull(ToolRegistry.validate(valid))
    }
}
