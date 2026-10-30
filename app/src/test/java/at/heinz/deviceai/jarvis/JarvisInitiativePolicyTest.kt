package at.heinz.deviceai.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisInitiativePolicyTest {
    @Test fun verifiedEmptyHouseAndActiveCooktopMayInterruptDrivingOnlyThroughAnAuthorizedEndpoint() {
        val d = JarvisInitiativePolicy.evaluate(true, JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO,
            HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY, true, true, true)
        assertEquals(JarvisInitiativeDeliveryMode.SPEAK_WHEN_ENDPOINT_AUTHORIZED, d.delivery)
        assertEquals("UNATTENDED_ACTIVE_DEVICE", d.reasonCode)
        assertTrue(d.heuristicScore > 0)
    }

    @Test fun uncertainHouseholdPresenceAvoidsUnsolicitedDrivingAudio() {
        val d = JarvisInitiativePolicy.evaluate(true, JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO,
            HouseholdOccupancyState.OCCUPANCY_UNKNOWN, false, true, true)
        assertEquals(JarvisInitiativeDeliveryMode.QUEUE_SILENTLY, d.delivery)
        assertEquals("OCCUPANCY_UNCONFIRMED", d.reasonCode)
    }

    @Test fun confirmedFamilyPresenceSuppressesUnattendedWarning() {
        val d = JarvisInitiativePolicy.evaluate(true, JarvisActiveEndpoint.FOLD7_MOBILE_DIRECT,
            HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT, true, true, true)
        assertEquals(JarvisInitiativeDeliveryMode.SUPPRESS, d.delivery)
    }

    @Test fun inactiveOrUnverifiedSensorSuppressesInitiative() {
        for ((active, verified) in listOf(false to true, true to false, false to false)) {
            assertEquals(JarvisInitiativeDeliveryMode.SUPPRESS, JarvisInitiativePolicy.evaluate(
                false, JarvisActiveEndpoint.PC_WORKSTATION,
                HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY, true,
                active, verified).delivery)
        }
    }

    @Test fun engineHonorsPolicyAndDoesNotAutoSpeakWhenOccupancyIsUncertain() {
        val now = 1_762_000_000_000L
        val engine = ContinuumJarvisPresenceEngine(nowMillis = { now })
        val appliance = ConnectedApplianceState(
            "cooktop-kitchen-01", "Kochfeld", true, "eine Herdplatte",
            DeviceActuationCapability.TELEMETRY_READ_ONLY, now, true)
        val snapshot = SituationalContextSnapshot(now, "Heinz",
            JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO,
            HouseholdOccupancyState.OCCUPANCY_UNKNOWN, true, listOf(appliance), false)
        assertEquals(JarvisInitiativeDeliveryMode.QUEUE_SILENTLY,
            engine.evaluateSituationForInitiative(snapshot)!!.deliveryMode)
    }
}
