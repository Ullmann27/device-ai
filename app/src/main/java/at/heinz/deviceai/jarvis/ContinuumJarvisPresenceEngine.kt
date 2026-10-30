package at.heinz.deviceai.jarvis

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** Research-only decision layer. Audio routing, authentication and hardware tools are NOT implemented here. */
enum class JarvisActiveEndpoint { HOME_AMBIENT, VEHICLE_HANDSFREE_AUDIO, FOLD7_MOBILE_DIRECT, PC_WORKSTATION }

enum class HouseholdOccupancyState {
    OWNER_AT_HOME, OWNER_AWAY_OTHERS_PRESENT, HOUSE_CONFIRMED_EMPTY, OCCUPANCY_UNKNOWN
}

enum class DeviceActuationCapability { TELEMETRY_READ_ONLY, SAFE_REMOTE_SHUTOFF_ONLY, FULL_BIDIRECTIONAL_CONTROL }

data class ConnectedApplianceState(
    val deviceId: String,
    val displayNameDe: String,
    val isCurrentlyActive: Boolean,
    val activeZoneDescriptionDe: String?,
    val capability: DeviceActuationCapability,
    val lastTelemetryTimestampMs: Long,
    val telemetryVerified: Boolean,
    val highHazardWhenUnattended: Boolean = true
)

data class SituationalContextSnapshot(
    val timestampMs: Long,
    val ownerName: String,
    val activeEndpoint: JarvisActiveEndpoint,
    val householdOccupancy: HouseholdOccupancyState,
    val ownerIsDriving: Boolean,
    val appliances: List<ConnectedApplianceState>,
    /** External presence quorum must verify this claim; owner Wi-Fi alone cannot prove an empty house. */
    val occupancyEvidenceVerified: Boolean = false
)

data class ProactiveInitiativePrompt(
    val proposalId: String,
    val targetDeviceId: String,
    val targetEndpoint: JarvisActiveEndpoint,
    val spokenMessageDe: String,
    val requiresExplicitOwnerConsent: Boolean,
    val canExecuteRemoteShutoff: Boolean,
    val createdAtMs: Long,
    val expiresAtMs: Long
)

enum class VerifiedActuationStatus {
    REJECTED, COMMAND_NOT_ACCEPTED, COMMAND_SENT_UNVERIFIED, TELEMETRY_CONFIRMED_OFF
}

data class VerifiedActuationOutcome(
    val proposalId: String,
    val deviceId: String,
    val status: VerifiedActuationStatus,
    val commandSent: Boolean,
    val hardwareReadBackConfirmedOff: Boolean,
    val spokenConfirmationDe: String,
    /** Local audit checksum only; NOT a device signature or trusted execution receipt. */
    val receiptSha256: String
)

/** Only the EXISTING trusted owner-authentication and ToolGate implementation may supply a grant. */
data class OwnerShutoffGrant(
    val proposalId: String,
    val deviceId: String,
    val validUntilMs: Long,
    val ownerAuthenticated: Boolean,
    val toolGateApproved: Boolean,
    /** A fresh, authenticated preflight must confirm no other occupant returned and shutoff remains supported. */
    val confirmedEmptyHouse: Boolean,
    val preflightTelemetryVerified: Boolean,
    val preflightObservedAtMs: Long
)

/** A voice transcript alone does NOT authenticate the speaker and cannot grant tool privileges. */
fun interface OwnerShutoffApprovalGate {
    fun authorize(proposal: ProactiveInitiativePrompt, utterance: String, nowMs: Long): OwnerShutoffGrant?
}

data class ShutoffDispatch(
    val deviceId: String,
    val commandId: String,
    val acceptedAtMs: Long,
    val accepted: Boolean
)

data class DeviceReadBack(
    val deviceId: String,
    val isCurrentlyActive: Boolean,
    val observedAtMs: Long,
    val observedCommandId: String,
    /** True means the adapter checked freshness and provenance; it is not a physical safety certificate. */
    val independentlyVerifiedTelemetry: Boolean
)

/** Implementations must independently enforce ToolGate policy, device scope and supported shutoff commands. */
interface HomeApplianceToolBridge {
    fun requestSafeShutoff(deviceId: String, grant: OwnerShutoffGrant): ShutoffDispatch
    fun readBackCurrentActiveState(deviceId: String): DeviceReadBack?
}

/**
 * JPI1 · fail-closed SHADOW research kernel. It only returns proposals and outcomes.
 * The existing Continuum session remains authoritative. This class neither speaks nor tracks people nor
 * accesses Bluetooth, home networks, the Android microphone or any real appliance.
 * A privileged bridge may be injected in tests, but production actuation is NOT wired to this class.
 */
class ContinuumJarvisPresenceEngine(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val consentValidityWindowMs: Long = 120_000L,
    private val maximumSensorAgeMs: Long = 30_000L
) {
    private val pendingProposals = LinkedHashMap<String, ProactiveInitiativePrompt>()
    private val suppressedDeviceAlerts = LinkedHashMap<String, Long>()

    init {
        require(consentValidityWindowMs in 1_000L..180_000L)
        require(maximumSensorAgeMs in 1_000L..120_000L)
    }

    /** Only trusted, recent telemetries may produce an alert; unknown occupancy is never described as empty. */
    @Synchronized fun evaluateSituationForInitiative(snapshot: SituationalContextSnapshot): ProactiveInitiativePrompt? {
        val now = nowMillis()
        if (snapshot.timestampMs < 0L || snapshot.timestampMs > now + 2_000L ||
            now - snapshot.timestampMs > maximumSensorAgeMs || snapshot.appliances.size > 32 ||
            snapshot.appliances.map { it.deviceId }.distinct().size != snapshot.appliances.size) return null
        prune(now)
        val occupancy = if (snapshot.occupancyEvidenceVerified) snapshot.householdOccupancy
            else HouseholdOccupancyState.OCCUPANCY_UNKNOWN
        if (occupancy == HouseholdOccupancyState.OWNER_AT_HOME ||
            occupancy == HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT) return null

        for (appliance in snapshot.appliances) {
            if (!validDeviceId(appliance.deviceId)) continue
            if (!appliance.isCurrentlyActive) {
                suppressedDeviceAlerts.remove(appliance.deviceId)
                pendingProposals.entries.removeAll { it.value.targetDeviceId == appliance.deviceId }
                continue
            }
            if (!appliance.highHazardWhenUnattended || !appliance.telemetryVerified ||
                appliance.lastTelemetryTimestampMs < 0L ||
                appliance.lastTelemetryTimestampMs > now + 2_000L ||
                now - appliance.lastTelemetryTimestampMs > maximumSensorAgeMs ||
                (suppressedDeviceAlerts[appliance.deviceId]?.let { now >= it && now - it < 300_000L } == true)) continue

            val empty = occupancy == HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY
            val canShutoff = empty && appliance.capability != DeviceActuationCapability.TELEMETRY_READ_ONLY
            val zone = safeDisplay(appliance.activeZoneDescriptionDe ?: appliance.displayNameDe, "das Kochfeld")
            val name = safeDisplay(snapshot.ownerName, "Hallo")
            val spoken = when {
                !empty -> "$name, ein wichtiger Hinweis: Das Kochfeld meldet noch aktiven Betrieb. " +
                    "Ich kann derzeit nicht bestätigen, ob jemand zu Hause ist. Bitte prüfe den Zustand."
                canShutoff -> "$name, kurze Frage. Du bist gerade unterwegs, und zu Hause ist noch $zone eingeschaltet. " +
                    "Möchtest du, dass ich eine sichere Abschaltung zur Freigabe vorbereite?"
                else -> "$name, wichtiger Hinweis: Du bist unterwegs, aber zu Hause ist noch $zone eingeschaltet. " +
                    "Das Gerät erlaubt keine bestätigte Fernabschaltung. Bitte prüfe den Zustand."
            }
            val proposal = ProactiveInitiativePrompt(
                proposalId = UUID.randomUUID().toString(),
                targetDeviceId = appliance.deviceId,
                targetEndpoint = snapshot.activeEndpoint,
                spokenMessageDe = spoken,
                requiresExplicitOwnerConsent = canShutoff,
                canExecuteRemoteShutoff = canShutoff,
                createdAtMs = now,
                expiresAtMs = now + consentValidityWindowMs
            )
            if (pendingProposals.size >= 32) pendingProposals.remove(pendingProposals.keys.first())
            pendingProposals[proposal.proposalId] = proposal
            suppressedDeviceAlerts[appliance.deviceId] = now
            return proposal
        }
        return null
    }

    /**
     * Two-phase tool request: strict wording + externally authenticated owner + ToolGate grant,
     * then dispatched command and timestamped telemetry read-back from the same device.
     * An initial voice message, a mere successful command dispatch or stale telemetry is NEVER success.
     */
    @Synchronized fun handleOwnerVoiceReply(
        proposalId: String,
        ownerUtteranceDe: String,
        nowMs: Long,
        approvalGate: OwnerShutoffApprovalGate,
        toolBridge: HomeApplianceToolBridge
    ): VerifiedActuationOutcome {
        val p = pendingProposals.remove(proposalId)
            ?: return result(proposalId, "UNKNOWN", VerifiedActuationStatus.REJECTED, false,
                "Diese Sicherheitsanfrage ist nicht mehr aktiv.")
        if (nowMs < p.createdAtMs || nowMs >= p.expiresAtMs || nowMs < 0 ||
            nowMs > nowMillis() + 2_000L || !p.canExecuteRemoteShutoff ||
            !isExplicitAffirmativeConsent(ownerUtteranceDe))
            return result(proposalId, p.targetDeviceId, VerifiedActuationStatus.REJECTED, false,
                "Keine Abschaltung ausgeführt. Bitte prüfe den aktuellen Zustand erneut.")

        // The real implementation MUST connect to verified owner identification and existing ToolGate.
        val grant = runCatching { approvalGate.authorize(p, ownerUtteranceDe, nowMs) }.getOrNull()
        if (grant == null || grant.proposalId != p.proposalId || grant.deviceId != p.targetDeviceId ||
            !grant.ownerAuthenticated || !grant.toolGateApproved || !grant.confirmedEmptyHouse ||
            !grant.preflightTelemetryVerified || grant.preflightObservedAtMs < nowMs - maximumSensorAgeMs ||
            grant.preflightObservedAtMs > nowMillis() + 2_000L || grant.validUntilMs < nowMillis() ||
            grant.validUntilMs > p.expiresAtMs || nowMillis() >= p.expiresAtMs)
            return result(proposalId, p.targetDeviceId, VerifiedActuationStatus.REJECTED, false,
                "Für die Abschaltung liegt keine gültige Besitzer- und Werkzeugfreigabe vor.")

        val dispatch = runCatching { toolBridge.requestSafeShutoff(p.targetDeviceId, grant) }.getOrNull()
        if (dispatch == null || !dispatch.accepted || dispatch.deviceId != p.targetDeviceId ||
            dispatch.commandId.isBlank() || dispatch.acceptedAtMs < nowMs - 2_000L ||
            dispatch.acceptedAtMs > nowMillis() + 2_000L)
            return result(proposalId, p.targetDeviceId, VerifiedActuationStatus.COMMAND_NOT_ACCEPTED, false,
                "Der Abschaltbefehl wurde nicht verlässlich bestätigt. Bitte prüfe den Herd.")

        val reading = runCatching { toolBridge.readBackCurrentActiveState(p.targetDeviceId) }.getOrNull()
        val confirmed = reading != null && reading.deviceId == p.targetDeviceId &&
            reading.independentlyVerifiedTelemetry && !reading.isCurrentlyActive &&
            reading.observedCommandId == dispatch.commandId &&
            reading.observedAtMs >= dispatch.acceptedAtMs &&
            reading.observedAtMs <= nowMillis() + 2_000L &&
            nowMillis() - reading.observedAtMs <= maximumSensorAgeMs
        return if (confirmed) result(proposalId, p.targetDeviceId,
            VerifiedActuationStatus.TELEMETRY_CONFIRMED_OFF, true,
            "Die Haussteuerung meldet jetzt AUS. Die Abschaltung ist durch eine aktuelle Gerätemeldung bestätigt.")
        else result(proposalId, p.targetDeviceId, VerifiedActuationStatus.COMMAND_SENT_UNVERIFIED, true,
            "Achtung: Ich habe den Abschaltbefehl gesendet, aber AUS wurde nicht zuverlässig bestätigt. " +
                "Bitte gehe weiterhin davon aus, dass der Herd eingeschaltet sein könnte.")
    }

    /** A standalone 'bitte', 'ja' or mixed/negated instruction never authorizes a shutoff. */
    internal fun isExplicitAffirmativeConsent(utterance: String): Boolean {
        val normalized = utterance.trim().lowercase(Locale.GERMAN)
        if (normalized.length !in 7..100) return false
        if (Regex("(?iu)\\b(nein|nicht|nie|warte|stopp|stop|später|vielleicht|wenn|abbrechen|lass|kein|keine)\\b")
                .containsMatchIn(normalized)) return false
        if (!Regex("(?iu)^ja[,.! ]+((jarvis|bitte)[,.! ]+){0,2}").containsMatchIn(normalized)) return false
        return Regex("(?iu)\\b(mach( das)? bitte|mach das|schalt(e)? (ihn|sie|es|den herd|die platte)? ?(bitte )?(ab|aus)|abschalten|ausschalten)\\b")
            .containsMatchIn(normalized)
    }

    @Synchronized fun clear() {
        pendingProposals.clear()
        suppressedDeviceAlerts.clear()
    }

    private fun prune(nowMs: Long) {
        pendingProposals.entries.removeAll { nowMs >= it.value.expiresAtMs }
        suppressedDeviceAlerts.entries.removeAll { it.value > nowMs || nowMs - it.value >= 300_000L }
        while (suppressedDeviceAlerts.size > 64) suppressedDeviceAlerts.remove(suppressedDeviceAlerts.keys.first())
    }

    private fun validDeviceId(id: String): Boolean = id.length in 3..96 &&
        Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{2,95}").matches(id)

    private fun safeDisplay(text: String, default: String): String {
        val value = text.trim().take(64)
        return if (value.isNotEmpty() && value.none { it.isISOControl() } &&
            value.all { it.isLetterOrDigit() || it in " .-_äöüÄÖÜß" }) value else default
    }

    private fun result(id: String, device: String, status: VerifiedActuationStatus, sent: Boolean,
                       message: String): VerifiedActuationOutcome {
        val confirmed = status == VerifiedActuationStatus.TELEMETRY_CONFIRMED_OFF
        val payload = listOf(id, device, status.name, sent.toString(), confirmed.toString(), message)
            .joinToString("|")
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return VerifiedActuationOutcome(id, device, status, sent, confirmed, message, hash)
    }
}
