package at.heinz.deviceai.learning

import at.heinz.deviceai.*
import org.junit.Assert.*
import org.junit.Test

class LearningIntegrationTest {
    @Test fun revisionCommandsAreExplicitAndNeverDeviceActions() {
        val old = "Fakt: Nora | ist | Mensch"
        val revised = BootstrapBrain.decide("Korrigiere: $old => Fakt: Nora | ist | Person", listOf(old))
        assertEquals("REVISE", revised.kind.name)
        assertEquals(old, revised.fact)
        assertNull(revised.tool)
        assertEquals("REVISE", BootstrapBrain.decide("Widerrufe: $old", listOf(old)).kind.name)
        assertEquals(BootstrapKind.REPLY, BootstrapBrain.decide("Widerrufe: unbekannt", listOf(old)).kind)
        assertEquals(BootstrapKind.REPLY, BootstrapBrain.decide("Korrigiere: $old => Fakt: Passwort | ist | geheim", listOf(old)).kind)
    }
    @Test fun learnedStatisticalSuggestionsDoNotAuthorizeDevicesOrLearnFromTheirOwnOutput() {
        val examples = mapOf("energie status" to "get_battery_info", "energie stand" to "get_battery_info",
            "dateien übersicht" to "list_files", "dateien liste" to "list_files")
        val reply = BootstrapBrain.decide("energie übersicht", emptyList(), examples)
        assertEquals(BootstrapKind.REPLY, reply.kind)
        assertNull(reply.tool)
        assertTrue(reply.message, reply.message.contains("Vermutung"))
        assertEquals(4, examples.size)
        assertNull(BootstrapTraining.parse("Trainiere: Neuron Demo => Akku"))
        assertNull(BootstrapTraining.parse("Trainiere: Prüfe Wissen: a => Akku"))
    }
}
