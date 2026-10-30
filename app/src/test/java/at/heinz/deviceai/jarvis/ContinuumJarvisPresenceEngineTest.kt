package at.heinz.deviceai.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuumJarvisPresenceEngineTest {
    private val now = 1_762_000_000_000L
    private fun engine() = ContinuumJarvisPresenceEngine(nowMillis = { now })
    private fun appliance(capability: DeviceActuationCapability = DeviceActuationCapability.SAFE_REMOTE_SHUTOFF_ONLY,
                          active: Boolean = true, telemetryAt: Long = now,
                          trusted: Boolean = true): ConnectedApplianceState = ConnectedApplianceState(
        deviceId = "cooktop-kitchen-01", displayNameDe = "Kochfeld Küche", isCurrentlyActive = active,
        activeZoneDescriptionDe = "eine Herdplatte", capability = capability,
        lastTelemetryTimestampMs = telemetryAt, telemetryVerified = trusted)
    private fun snapshot(occupancy: HouseholdOccupancyState = HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY,
                         appliances: List<ConnectedApplianceState> = listOf(appliance()),
                         timestamp: Long = now): SituationalContextSnapshot = SituationalContextSnapshot(
        timestamp, "Heinz", JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO, occupancy,
        ownerIsDriving = true, appliances = appliances, occupancyEvidenceVerified = true)

    private fun gate() = OwnerShutoffApprovalGate { proposal, _, timestamp ->
        OwnerShutoffGrant(proposal.proposalId, proposal.targetDeviceId,
            timestamp + 20_000L, ownerAuthenticated = true, toolGateApproved = true)
    }

    private fun bridge(active: Boolean = false, readingAt: Long = now + 500L,
                       verified: Boolean = true, readingId: String = "cooktop-kitchen-01") =
        object : HomeApplianceToolBridge {
            override fun requestSafeShutoff(deviceId: String, grant: OwnerShutoffGrant) =
                ShutoffDispatch(deviceId, "command-01", now + 100L, true)
            override fun readBackCurrentActiveState(deviceId: String) =
                DeviceReadBack(readingId, active, readingAt, verified)
        }

    @Test fun promptsInVehicleButDoesNotSpeakOrActByItself() {
        val prompt = engine().evaluateSituationForInitiative(snapshot())
        assertNotNull(prompt)
        assertEquals(JarvisActiveEndpoint.VEHICLE_HANDSFREE_AUDIO, prompt!!.targetEndpoint)
        assertTrue(prompt.canExecuteRemoteShutoff)
        assertTrue(prompt.spokenMessageDe.contains("Heinz"))
        assertTrue(prompt.spokenMessageDe.contains("eine Herdplatte"))
    }

    @Test fun explicitAuthenticatedConsentAndFreshReadbackConfirmsOnlyTelemetry() {
        val e = engine()
        val p = e.evaluateSituationForInitiative(snapshot())!!
        val result = e.handleOwnerVoiceReply(p.proposalId, "Ja, Jarvis, mach bitte.", now,
            gate(), bridge())
        assertTrue(result.commandSent)
        assertTrue(result.hardwareReadBackConfirmedOff)
        assertEquals(VerifiedActuationStatus.TELEMETRY_CONFIRMED_OFF, result.status)
        assertTrue(result.spokenConfirmationDe.contains("Haussteuerung meldet"))
        assertEquals(64, result.receiptSha256.length)
    }

    @Test fun sensorStillOnNeverClaimsSuccess() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
        val result = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte.", now, gate(), bridge(active = true))
        assertEquals(VerifiedActuationStatus.COMMAND_SENT_UNVERIFIED, result.status)
        assertTrue(result.commandSent)
        assertFalse(result.hardwareReadBackConfirmedOff)
        assertFalse(result.spokenConfirmationDe.contains("bestätigt, dass die Platte ausgeschaltet"))
    }

    @Test fun houseWithFamilyDoesNotGenerateFalseUnattendedAlarm() {
        assertNull(engine().evaluateSituationForInitiative(snapshot(HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT)))
    }

    @Test fun ownerAtHomeIsNotTreatedAsAway() {
        assertNull(engine().evaluateSituationForInitiative(snapshot(HouseholdOccupancyState.OWNER_AT_HOME)))
    }

    @Test fun unknownOccupancyWarnsWithoutOfferingShutoff() {
        val p = engine().evaluateSituationForInitiative(snapshot(HouseholdOccupancyState.OCCUPANCY_UNKNOWN))!!
        assertFalse(p.canExecuteRemoteShutoff)
        assertTrue(p.spokenMessageDe.contains("nicht bestätigen"))
    }

    @Test fun unverifiedEmptyHouseClaimMayWarnButCannotOfferShutoff() {
        val p = engine().evaluateSituationForInitiative(
            snapshot().copy(occupancyEvidenceVerified = false))!!
        assertFalse(p.canExecuteRemoteShutoff)
        assertTrue(p.spokenMessageDe.contains("nicht bestätigen"))
    }

    @Test fun readOnlyDeviceNeverOffersUnsupportedShutoff() {
        val p = engine().evaluateSituationForInitiative(snapshot(appliances = listOf(
            appliance(DeviceActuationCapability.TELEMETRY_READ_ONLY))))!!
        assertFalse(p.canExecuteRemoteShutoff)
        assertFalse(p.requiresExplicitOwnerConsent)
    }

    @Test fun staleOrUnverifiedTelemetriesCannotTriggerAlert() {
        assertNull(engine().evaluateSituationForInitiative(snapshot(appliances = listOf(
            appliance(telemetryAt = now - 40_000L)))))
        assertNull(engine().evaluateSituationForInitiative(snapshot(appliances = listOf(
            appliance(trusted = false)))))
        assertNull(engine().evaluateSituationForInitiative(snapshot(timestamp = now - 40_000L)))
    }

    @Test fun noRepeatedAlertsForSameOnStateAndOffRearms() {
        val e = engine()
        assertNotNull(e.evaluateSituationForInitiative(snapshot()))
        assertNull(e.evaluateSituationForInitiative(snapshot()))
        assertNull(e.evaluateSituationForInitiative(snapshot(appliances = listOf(appliance(active = false)))))
        assertNotNull(e.evaluateSituationForInitiative(snapshot()))
    }

    @Test fun voiceTranscriptAloneNeverAuthorizesAction() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
        var called = false
        val tool = object : HomeApplianceToolBridge {
            override fun requestSafeShutoff(deviceId: String, grant: OwnerShutoffGrant): ShutoffDispatch {
                called = true
                return ShutoffDispatch(deviceId, "cmd", now, true)
            }
            override fun readBackCurrentActiveState(deviceId: String): DeviceReadBack? = null
        }
        val outcome = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now,
            OwnerShutoffApprovalGate { _, _, _ -> null }, tool)
        assertEquals(VerifiedActuationStatus.REJECTED, outcome.status)
        assertFalse(called)
    }

    @Test fun ambiguousOrNegatedConsentAlwaysFailsClosed() {
        for (phrase in listOf("Ja", "Bitte", "Ja, nicht abschalten", "Ja, mach später", "Vielleicht, ja mach bitte", "Ja, warte, mach bitte")) {
            val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
            val result = e.handleOwnerVoiceReply(p.proposalId, phrase, now, gate(), bridge())
            assertEquals(phrase, VerifiedActuationStatus.REJECTED, result.status)
            assertFalse(result.commandSent)
        }
    }

    @Test fun expiredProposalCannotBeReplayed() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
        assertEquals(VerifiedActuationStatus.REJECTED,
            e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now + 120_000L, gate(), bridge()).status)
        assertEquals(VerifiedActuationStatus.REJECTED,
            e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now, gate(), bridge()).status)
    }

    @Test fun wrongDeviceOrMissingToolGateGrantRejected() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
        val wrong = OwnerShutoffApprovalGate { _, _, _ ->
            OwnerShutoffGrant(p.proposalId, "another-stove", now + 1_000L, true, true)
        }
        assertEquals(VerifiedActuationStatus.REJECTED,
            e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now, wrong, bridge()).status)
        val e2 = engine(); val p2 = e2.evaluateSituationForInitiative(snapshot())!!
        val noTool = OwnerShutoffApprovalGate { _, _, _ ->
            OwnerShutoffGrant(p2.proposalId, p2.targetDeviceId, now + 1_000L, true, false)
        }
        assertEquals(VerifiedActuationStatus.REJECTED,
            e2.handleOwnerVoiceReply(p2.proposalId, "Ja, mach bitte", now, noTool, bridge()).status)
    }

    @Test fun staleMismatchedOrUnverifiedReadbackCannotConfirmOff() {
        for (b in listOf(bridge(readingAt = now - 20_000L),
            bridge(verified = false), bridge(readingId = "other-device"))) {
            val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
            val result = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now, gate(), b)
            assertEquals(VerifiedActuationStatus.COMMAND_SENT_UNVERIFIED, result.status)
            assertFalse(result.hardwareReadBackConfirmedOff)
        }
    }

    @Test fun rejectedDispatchNeverClaimsOff() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot())!!
        val b = object : HomeApplianceToolBridge {
            override fun requestSafeShutoff(deviceId: String, grant: OwnerShutoffGrant) =
                ShutoffDispatch(deviceId, "cmd", now, false)
            override fun readBackCurrentActiveState(deviceId: String): DeviceReadBack? =
                throw AssertionError("must not read back a rejected dispatch")
        }
        val result = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now, gate(), b)
        assertFalse(result.commandSent)
        assertFalse(result.hardwareReadBackConfirmedOff)
        assertEquals(VerifiedActuationStatus.COMMAND_NOT_ACCEPTED, result.status)
    }

    @Test fun unauthenticatedReadOnlyProposalCanNeverDispatch() {
        val e = engine(); val p = e.evaluateSituationForInitiative(snapshot(appliances = listOf(
            appliance(DeviceActuationCapability.TELEMETRY_READ_ONLY))))!!
        val result = e.handleOwnerVoiceReply(p.proposalId, "Ja, mach bitte", now, gate(), bridge())
        assertEquals(VerifiedActuationStatus.REJECTED, result.status)
    }
}
