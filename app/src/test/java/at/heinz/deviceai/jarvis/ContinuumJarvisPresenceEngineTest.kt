package at.heinz.deviceai.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumJarvisPresenceEngineTest {
    private val time = 1_762_000_000_000L
    private fun cooktop(
        active: Boolean = true,
        capability: DeviceActuationCapability = DeviceActuationCapability.SAFE_REMOTE_SHUTOFF_ONLY,
        observed: Long = time,
        trusted: Boolean = true
    ) = ConnectedApplianceState("cooktop-01", "Kochfeld", active, "eine Herdplatte", capability, observed, telemetryVerified = trusted)

    private fun context(
        occupancy: HouseholdOccupancyState = HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY,
        appliances: List<ConnectedApplianceState> = listOf(cooktop()),
        now: Long = time,
        endpoint: JarvisActiveEndpoint = JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO,
        occupancyVerified: Boolean = true
    ) = SituationalContextSnapshot(now, "Heinz", endpoint, occupancy, true, appliances, occupancyVerified)

    private class FakeBridge(
        var commandAccepted: Boolean = true,
        var readback: ApplianceReadBack? = null
    ) : HomeApplianceToolBridge {
        var sends = 0
        var reads = 0
        override fun requestSafeShutoff(deviceId: String): Boolean {
            sends++
            return commandAccepted
        }
        override fun readBackCurrentActiveState(deviceId: String): ApplianceReadBack? {
            reads++
            return readback
        }
    }

    @Test fun houseEmptyAndDrivingTriggersTargetedSpokenPrompt() {
        val proposal = ContinuumJarvisPresenceEngine().evaluateSituationForInitiative(context())
        assertNotNull(proposal)
        assertEquals(JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO, proposal!!.targetEndpoint)
        assertTrue(proposal.spokenMessageDe.contains("Heinz"))
        assertTrue(proposal.spokenMessageDe.contains("eingeschaltet"))
        assertTrue(proposal.canExecuteRemoteShutoff)
    }

    @Test fun familyHomeDoesNotClaimUnattendedStove() {
        assertNull(ContinuumJarvisPresenceEngine().evaluateSituationForInitiative(
            context(occupancy = HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT)))
    }

    @Test fun unknownOccupancyExplainsUncertaintyAndDisablesShutoff() {
        val p = ContinuumJarvisPresenceEngine().evaluateSituationForInitiative(
            context(occupancy = HouseholdOccupancyState.OCCUPANCY_UNKNOWN))!!
        assertFalse(p.canExecuteRemoteShutoff)
        assertTrue(p.spokenMessageDe.contains("unklar"))
    }

    @Test fun readOnlyDeviceNeverOffersRemoteShutoff() {
        val p = ContinuumJarvisPresenceEngine().evaluateSituationForInitiative(
            context(appliances = listOf(cooktop(capability = DeviceActuationCapability.TELEMETRY_READ_ONLY))))!!
        assertFalse(p.canExecuteRemoteShutoff)
        assertTrue(p.spokenMessageDe.contains("keine Fernabschaltung"))
    }

    @Test fun defaultShadowNeverCallsActuatorEvenWithConsent() {
        val e = ContinuumJarvisPresenceEngine()
        val p = e.evaluateSituationForInitiative(context())!!
        val bridge = FakeBridge()
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja, Jarvis, mach bitte", time + 1000,
            context(now = time + 1000), true, bridge)
        assertFalse(r.commandSent)
        assertFalse(r.hardwareReadBackConfirmedOff)
        assertEquals(0, bridge.sends)
        assertTrue(r.spokenConfirmationDe.contains("SHADOW"))
    }

    @Test fun simulatedExplicitConsentNeedsAuthenticatedOwnerAndFreshReadBack() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context())!!
        val now = time + 1000
        val bridge = FakeBridge(readback = ApplianceReadBack("cooktop-01", false, now + 500, true))
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja, Jarvis, mach bitte.", now,
            context(now = now), true, bridge)
        assertTrue(r.commandSent)
        assertTrue(r.hardwareReadBackConfirmedOff)
        assertEquals(1, bridge.sends)
        assertEquals(1, bridge.reads)
        assertTrue(r.spokenConfirmationDe.contains("meldet jetzt AUS"))
        assertEquals(64, r.receiptSha256.length)
    }

    @Test fun failedReadbackOrOldTelemetryNeverClaimsShutoff() {
        for (readback in listOf(
            ApplianceReadBack("cooktop-01", true, time + 2000, true),
            ApplianceReadBack("cooktop-01", false, time + 1000, true),
            ApplianceReadBack("cooktop-01", false, time + 2000, false),
            ApplianceReadBack("wrong-device", false, time + 2000, true)
        )) {
            val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
            val p = e.evaluateSituationForInitiative(context())!!
            val r = e.handleOwnerVoiceReply(p.proposalId, "Ja mach bitte", time + 1000,
                context(now = time + 1000), true, FakeBridge(readback = readback))
            assertTrue(r.commandSent)
            assertFalse(r.hardwareReadBackConfirmedOff)
            assertFalse(r.spokenConfirmationDe.contains("meldet jetzt AUS"))
        }
    }

    @Test fun noIdentityOrAmbiguousVoiceNeverActuates() {
        for ((utterance, authenticated) in listOf(
            "Ja, mach bitte" to false,
            "Ja, mach nicht" to true,
            "Mach vielleicht" to true,
            "bitte" to true,
            "Ja, aber warte" to true,
            "nein" to true
        )) {
            val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
            val p = e.evaluateSituationForInitiative(context())!!
            val bridge = FakeBridge()
            val r = e.handleOwnerVoiceReply(p.proposalId, utterance, time + 1000,
                context(now = time + 1000), authenticated, bridge)
            assertFalse(r.commandSent)
            assertEquals(0, bridge.sends)
        }
    }

    @Test fun duplicateReplyNeverCallsBridgeTwice() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context())!!
        val bridge = FakeBridge(readback = ApplianceReadBack("cooktop-01", false, time + 2000, true))
        val one = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", time + 1000,
            context(now = time + 1000), true, bridge)
        val two = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", time + 1000,
            context(now = time + 1000), true, bridge)
        assertTrue(one.commandSent)
        assertFalse(two.commandSent)
        assertEquals(1, bridge.sends)
    }

    @Test fun expiredConsentDoesNotExecute() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context())!!
        val bridge = FakeBridge()
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", time + 200_000,
            context(now = time + 200_000), true, bridge)
        assertFalse(r.commandSent)
        assertEquals(0, bridge.sends)
    }

    @Test fun occupantsReturningAfterProposalBlocksExecution() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context())!!
        val bridge = FakeBridge()
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja mach bitte", time + 1000,
            context(occupancy = HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT, now = time + 1000), true, bridge)
        assertFalse(r.commandSent)
        assertEquals(0, bridge.sends)
    }

    @Test fun staleDeviceTelemetryCannotOfferImmediateShutoff() {
        val e = ContinuumJarvisPresenceEngine()
        assertNull(e.evaluateSituationForInitiative(context(
            appliances = listOf(cooktop(observed = time - 180_000)))))
    }

    @Test fun duplicateAlertIsCooldownBoundAndOffResetsIt() {
        val e = ContinuumJarvisPresenceEngine()
        assertNotNull(e.evaluateSituationForInitiative(context()))
        assertNull(e.evaluateSituationForInitiative(context(now = time + 5_000)))
        assertNull(e.evaluateSituationForInitiative(context(appliances = listOf(cooktop(active = false)), now = time + 6_000)))
        assertNotNull(e.evaluateSituationForInitiative(context(now = time + 7_000, appliances = listOf(cooktop(observed = time + 7_000)))))
    }

    @Test fun allFourEndpointsShareSameProposalPolicyButNoAudioRoutingIsClaimed() {
        for (ep in JarvisActiveEndpoint.values()) {
            val p = ContinuumJarvisPresenceEngine().evaluateSituationForInitiative(context(endpoint = ep))!!
            assertEquals(ep, p.targetEndpoint)
        }
    }

    @Test fun unverifiedHouseEmptyQuorumNeverPermitsActuation() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context(occupancyVerified = false))!!
        assertFalse(p.canExecuteRemoteShutoff)
        val bridge = FakeBridge()
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", time + 1000,
            context(now = time + 1000, occupancyVerified = false), true, bridge)
        assertFalse(r.commandSent)
        assertEquals(0, bridge.sends)
    }

    @Test fun unverifiedSensorCannotRaiseAlarm() {
        val e = ContinuumJarvisPresenceEngine()
        assertNull(e.evaluateSituationForInitiative(context(appliances=listOf(cooktop(trusted=false)))))
    }

    @Test fun commandFailureNeverProducesSuccessReceipt() {
        val e = ContinuumJarvisPresenceEngine(researchSimulatedActuationEnabled = true)
        val p = e.evaluateSituationForInitiative(context())!!
        val b = FakeBridge(commandAccepted = false)
        val r = e.handleOwnerVoiceReply(p.proposalId, "Ja mach bitte", time + 1000,
            context(now = time + 1000), true, b)
        assertFalse(r.commandSent)
        assertFalse(r.hardwareReadBackConfirmedOff)
        assertEquals(0, b.reads)
    }

    @Test fun otherFamilyAlreadyHomeIsNotAlertedAndResetClearsPending() {
        val e = ContinuumJarvisPresenceEngine()
        e.evaluateSituationForInitiative(context())!!
        assertEquals(1, e.pendingCount())
        assertNull(e.evaluateSituationForInitiative(context(occupancy = HouseholdOccupancyState.OWNER_AT_HOME)))
        assertEquals(0, e.pendingCount())
    }
}
