package at.heinz.deviceai.ui

import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class CommandWorkstationInstrumentsTest {
    @Test fun unknownMeasurementsStayUnknownAndCpuIsAppScoped() {
        val empty = ContinuumDashboardState.SystemStatus()
        assertEquals("—", CommandWorkstationInstruments.cpu(empty).value)
        assertEquals("APP-CPU", CommandWorkstationInstruments.cpu(empty).label)
        assertEquals("—", CommandWorkstationInstruments.memory(empty).value)
        assertEquals("GERÄTE-RAM", CommandWorkstationInstruments.memory(empty).label)
        for (invalid in listOf(-1, 101, Int.MAX_VALUE)) {
            val state = empty.copy(appCpuPercent = invalid, memoryPercent = invalid)
            assertNull(CommandWorkstationInstruments.cpu(state).percent)
            assertNull(CommandWorkstationInstruments.memory(state).percent)
        }
    }

    @Test fun measuredValuesAndAvailableMemoryKeepTheirActualUnits() {
        val state = ContinuumDashboardState.SystemStatus(appCpuPercent = 0,
            memoryPercent = 71, freeRamBytes = 3_758_096_384L)
        assertEquals("0 %", CommandWorkstationInstruments.cpu(state).value)
        assertEquals("71 %", CommandWorkstationInstruments.memory(state).value)
        assertEquals("3,5 GiB frei", CommandWorkstationInstruments.memory(state).note)
        assertEquals("Belegt im Gerät", CommandWorkstationInstruments.memory(state.copy(freeRamBytes = -1)).note)
    }

    @Test fun contentAndTelemetryHaveDistinctReachableRegions() {
        val box = JarvisShellLayout.Box(40f, 50f, 220f, 330f)
        for (bottom in listOf(330f, 650f, 1050f)) {
            val panel = box.copy(bottom = bottom)
            val meter = requireNotNull(CommandWorkstationInstruments.telemetryBox(panel))
            val content = CommandWorkstationInstruments.contentBox(panel)
            assertTrue(panel.contains(meter)); assertTrue(panel.contains(content))
            assertEquals("A longer panel must not move telemetry below the visible start", panel.top, meter.top, 0f)
            assertEquals(116f, meter.height, .001f)
            assertEquals(meter.bottom + 8f, content.top, .001f)
            assertEquals("Project/work content retains the remaining panel", panel.bottom, content.bottom, 0f)
            assertTrue(meter.bottom < content.top)
            assertTrue(meter.height >= 48f && content.height >= 48f)
        }
        val small = box.copy(bottom = 200f)
        assertNull(CommandWorkstationInstruments.telemetryBox(small))
        assertEquals(small, CommandWorkstationInstruments.contentBox(small))
    }

    @Test fun cpuHistoryNeverBridgesMissingOrInvalidSamples() {
        val lines = mutableListOf<List<Float>>()
        val painter = recorder(lines)
        val box = JarvisShellLayout.Box(0f, 0f, 180f, 116f)
        CommandWorkstationInstruments.drawCpu(painter, box,
            ContinuumDashboardState.SystemStatus(activity = listOf(10f, 20f, Float.NaN, 40f, 50f)))
        // Each of the two observed adjacent pairs has one core and one light stroke.
        val dataLines = lines.filter { it[4] == 1.1f }
        assertEquals(2, dataLines.size)
        assertTrue(dataLines[0][2] < dataLines[1][0])
        assertTrue(lines.flatten().all { it.isFinite() })
    }

    @Test fun emptyHistoryDoesNotProduceInventedCurve() {
        val lines = mutableListOf<List<Float>>()
        CommandWorkstationInstruments.drawCpu(recorder(lines), JarvisShellLayout.Box(0f, 0f, 180f, 116f),
            ContinuumDashboardState.SystemStatus())
        assertFalse(lines.any { it[4] == 1.1f })
    }

    @Test fun enlargedTextHasItsOwnMeterSpaceWithoutCrossingContent() {
        val box = JarvisShellLayout.Box(10f, 20f, 280f, 436f)
        val meter = requireNotNull(CommandWorkstationInstruments.telemetryBox(box, 2f))
        val content = CommandWorkstationInstruments.contentBox(box, 2f)
        assertEquals(box.top, meter.top, 0f)
        assertTrue(meter.bottom < content.top)
        assertTrue(content.height >= 48f)
        val lines = mutableListOf<List<Float>>()
        val painter = JarvisTextScalePainter(recorder(lines)).apply { fontScale = 2f }
        CommandWorkstationInstruments.drawCpu(painter, meter,
            ContinuumDashboardState.SystemStatus(appCpuPercent = 30, activity = listOf(0f, 100f, 30f)))
        assertTrue(lines.all { it[1] in meter.top..meter.bottom && it[3] in meter.top..meter.bottom })
        assertTrue(lines.filter { it[4] == 1.1f }.all { it[1].isFinite() && it[3].isFinite() })
    }

    private fun recorder(lines: MutableList<List<Float>>): ContinuumPainter =
        Proxy.newProxyInstance(ContinuumPainter::class.java.classLoader,
            arrayOf(ContinuumPainter::class.java)) { _, method, values ->
            val args = values.orEmpty()
            when (method.name) {
                "drawLine" -> { lines += args.take(5).map { it as Float }; null }
                "measureText" -> (args[0] as String).length * (args[1] as TextStyle).size * .5f
                "capHeight" -> (args[0] as TextStyle).size * .72f
                "drawRaster" -> false
                else -> null
            }
        } as ContinuumPainter
}
