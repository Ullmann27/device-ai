package at.heinz.deviceai.jarvis

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/** JPI1 research/shadow only. It does not route audio, discover devices, or register a ToolGate action. */
enum class JarvisActiveEndpoint { HOME_AMBIENT, VEHICLE_HANDSFREE_AUDIO, FOLD7_MOBILE_DIRECT, PC_WORKSTATION }
enum class HouseholdOccupancyState { OWNER_AT_HOME, OWNER_AWAY_OTHERS_PRESENT, HOUSE_CONFIRMED_EMPTY, OCCUPANCY_UNKNOWN }
enum class DeviceActuationCapability { TELEMETRY_READ_ONLY, SAFE_REMOTE_SHUTOFF_ONLY, FULL_BIDIRECTIONAL_CONTROL }

data class ConnectedApplianceState(
    val deviceId: String,
    val displayNameDe: String,
    val isCurrentlyActive: Boolean,
    val activeZoneDescriptionDe: String?,
    val capability: DeviceActuationCapability,
    val lastTelemetryTimestampMs: Long,
    val highHazardWhenUnattended: Boolean = true
) {
    init {
        require(deviceId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")))
        require(displayNameDe.length in 1..100 && displayNameDe.none(Char::isISOControl))
        require(activeZoneDescriptionDe == null || (activeZoneDescriptionDe.length in 1..100 && activeZoneDescriptionDe.none(Char::isISOControl)))
        require(lastTelemetryTimestampMs >= 0)
    }
}

data class SituationalContextSnapshot(
    val timestampMs: Long,
    val ownerName: String,
    val activeEndpoint: JarvisActiveEndpoint,
    val householdOccupancy: HouseholdOccupancyState,
    val ownerIsDriving: Boolean,
    val appliances: List<ConnectedApplianceState>
) {
    init {
        require(timestampMs >= 0)
        require(ownerName.length in 1..60 && ownerName.none(Char::isISOControl))
        require(appliances.size <= 32 && appliances.map { it.deviceId }.distinct().size == appliances.size)
    }
}

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

data class ApplianceReadBack(
    val deviceId: String,
    val isCurrentlyActive: Boolean,
    val observedAtMs: Long,
    val authenticatedSource: Boolean
)

data class VerifiedActuationOutcome(
    val proposalId: String,
    val deviceId: String,
    val commandSent: Boolean,
    val hardwareReadBackConfirmedOff: Boolean,
    val spokenConfirmationDe: String,
    /** An integrity checksum of this local result, NOT a signature or evidence of physical state. */
    val receiptSha256: String
)

/** Research port: production must provide a separate registered, owner-authorized ToolGate adapter. */
interface HomeApplianceToolBridge {
    fun requestSafeShutoff(deviceId: String): Boolean
    fun readBackCurrentActiveState(deviceId: String): ApplianceReadBack?
}

/**
 * Fail-closed, session-local policy. Default SHADOW forbids hardware calls even if a bridge is provided.
 * Never infer owner authentication from a voice transcript. A future Android adapter must authenticate
 * the device/session and bind tool permission to the exact proposal and appliance.
 */
class ContinuumJarvisPresenceEngine(
    private val consentValidityWindowMs: Long = 180_000L,
    private val maxTelemetryAgeMs: Long = 60_000L,
    private val alertCooldownMs: Long = 300_000L,
    private val researchSimulatedActuationEnabled: Boolean = false
) {
    init {
        require(consentValidityWindowMs in 1_000..300_000)
        require(maxTelemetryAgeMs in 1_000..120_000)
        require(alertCooldownMs in 1_000..3_600_000)
    }

    private val pending = LinkedHashMap<String, ProactiveInitiativePrompt>()
    private val lastAlerts = LinkedHashMap<String, Long>()

    @Synchronized
    fun evaluateSituationForInitiative(snapshot: SituationalContextSnapshot): ProactiveInitiativePrompt? {
        require(snapshot.timestampMs <= Long.MAX_VALUE - consentValidityWindowMs)
        purgeExpired(snapshot.timestampMs)
        if (snapshot.householdOccupancy == HouseholdOccupancyState.OWNER_AT_HOME) {
            pending.clear(); lastAlerts.clear(); return null
        }
        // Another known occupant may actively be using the appliance; no unattended-home alert.
        if (snapshot.householdOccupancy == HouseholdOccupancyState.OWNER_AWAY_OTHERS_PRESENT) return null
        snapshot.appliances.filterNot { it.isCurrentlyActive }.forEach { lastAlerts.remove(it.deviceId) }
        for (device in snapshot.appliances) {
            if (!device.isCurrentlyActive || !device.highHazardWhenUnattended) continue
            // Never advertise stale telemetry as a current stove status.
            if (!telemetryFresh(snapshot.timestampMs, device.lastTelemetryTimestampMs)) continue
            val last = lastAlerts[device.deviceId]
            if (last != null && snapshot.timestampMs >= last && snapshot.timestampMs - last < alertCooldownMs) continue
            val canOff = snapshot.householdOccupancy == HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY &&
                device.capability != DeviceActuationCapability.TELEMETRY_READ_ONLY
            val place = if (snapshot.ownerIsDriving) "Du bist gerade unterwegs" else "Du bist nicht zu Hause"
            val zone = device.activeZoneDescriptionDe ?: device.displayNameDe
            val msg = when {
                snapshot.householdOccupancy == HouseholdOccupancyState.OCCUPANCY_UNKNOWN ->
                    "${snapshot.ownerName}, wichtiger Hinweis: $place. Die Haussteuerung meldet $zone als eingeschaltet. Ob jemand zu Hause ist, ist unklar. Bitte prüfe den Zustand."
                canOff -> "${snapshot.ownerName}, kurze Frage. $place, aber zu Hause ist noch $zone eingeschaltet. Möchtest du, dass ich das Gerät über eine freigegebene Steuerung ausschalte?"
                else -> "${snapshot.ownerName}, wichtiger Hinweis. $place und zu Hause meldet $zone aktiven Betrieb. Dieses Gerät erlaubt keine Fernabschaltung. Bitte prüfe den Zustand."
            }
            if (pending.size >= 32) pending.remove(pending.keys.first())
            val proposal = ProactiveInitiativePrompt(
                proposalId = UUID.randomUUID().toString(),
                targetDeviceId = device.deviceId,
                targetEndpoint = snapshot.activeEndpoint,
                spokenMessageDe = msg,
                requiresExplicitOwnerConsent = canOff,
                canExecuteRemoteShutoff = canOff,
                createdAtMs = snapshot.timestampMs,
                expiresAtMs = snapshot.timestampMs + consentValidityWindowMs
            )
            pending[proposal.proposalId] = proposal
            lastAlerts[device.deviceId] = snapshot.timestampMs
            if (lastAlerts.size > 64) lastAlerts.remove(lastAlerts.keys.first())
            return proposal
        }
        return null
    }

    /**
     * Simulation only: opt-in test flag AND an external authenticated-session assertion are required.
     * Consumes a proposal before I/O so simultaneous replies cannot dispatch the same command twice.
     * No monitor is held across calls to the supplied mock bridge.
     */
    fun handleOwnerVoiceReply(
        proposalId: String,
        ownerUtteranceDe: String,
        nowMs: Long,
        currentSituation: SituationalContextSnapshot,
        ownerSessionAuthenticated: Boolean,
        toolBridge: HomeApplianceToolBridge
    ): VerifiedActuationOutcome {
        val proposal = synchronized(this) {
            purgeExpired(nowMs)
            pending.remove(proposalId)
        } ?: return outcome(proposalId, "UNKNOWN", false, false, "Die Anfrage ist nicht mehr gültig. Ich ändere nichts.")
        fun reject(message: String) = outcome(proposalId, proposal.targetDeviceId, false, false, message)
        if (nowMs < proposal.createdAtMs || nowMs > proposal.expiresAtMs)
            return reject("Die Anfrage ist abgelaufen oder die Zeitangabe ist ungültig. Bitte frage den Status erneut ab.")
        if (!proposal.canExecuteRemoteShutoff)
            return reject("Für dieses Gerät ist keine Fernabschaltung freigegeben. Ich ändere nichts.")
        if (!isExplicitAffirmativeConsent(ownerUtteranceDe))
            return reject("Ich habe keine eindeutige Zustimmung zur Abschaltung erkannt und ändere nichts.")
        if (!ownerSessionAuthenticated)
            return reject("Ich kann die Besitzeridentität für diese Aktion nicht bestätigen. Ich ändere nichts.")
        if (!researchSimulatedActuationEnabled)
            return reject("SHADOW: Ich habe deine Zustimmung erkannt, aber die reale Geräteabschaltung ist noch nicht freigeschaltet.")
        val device = currentSituation.appliances.singleOrNull { it.deviceId == proposal.targetDeviceId }
        if (currentSituation.timestampMs != nowMs ||
            currentSituation.householdOccupancy != HouseholdOccupancyState.HOUSE_CONFIRMED_EMPTY ||
            device?.isCurrentlyActive != true ||
            device.capability == DeviceActuationCapability.TELEMETRY_READ_ONLY ||
            !telemetryFresh(nowMs, device.lastTelemetryTimestampMs))
            return reject("Die aktuelle Haushalts- oder Gerätesituation ist nicht sicher bestätigt. Ich ändere nichts.")
        // NOT wired to Home Assistant, SmartThings, Android Auto or the product ToolGate.
        val dispatched = runCatching { toolBridge.requestSafeShutoff(device.deviceId) }.getOrDefault(false)
        if (!dispatched) return outcome(proposalId, device.deviceId, false, false,
            "Die Steuerung hat den Abschaltbefehl nicht bestätigt. Das Gerät könnte weiterhin an sein.")
        val readBack = runCatching { toolBridge.readBackCurrentActiveState(device.deviceId) }.getOrNull()
        val off = readBack != null && readBack.deviceId == device.deviceId &&
            readBack.authenticatedSource && !readBack.isCurrentlyActive &&
            readBack.observedAtMs > nowMs && nowMs <= Long.MAX_VALUE - maxTelemetryAgeMs &&
            readBack.observedAtMs <= nowMs + maxTelemetryAgeMs
        return if (off) outcome(proposalId, device.deviceId, true, true,
            "Die verbundene Gerätesteuerung meldet jetzt AUS. Bitte beachte, dass dies eine Telemetriemeldung ist, keine unabhängige physische Prüfung.")
        else outcome(proposalId, device.deviceId, true, false,
            "Ich habe einen Abschaltbefehl gesendet, aber keine aktuelle bestätigte AUS-Meldung erhalten. Bitte gehe davon aus, dass das Gerät noch an sein könnte.")
    }

    @Synchronized fun resetSession() { pending.clear(); lastAlerts.clear() }
    @Synchronized fun pendingCount(): Int = pending.size

    internal fun isExplicitAffirmativeConsent(utterance: String): Boolean {
        val normalized = utterance.lowercase(Locale.GERMAN).trim().trimEnd('.', '!', '?')
            .replace(Regex("[,.!?]"), " ").replace(Regex("\\s+"), " ").trim()
        if (normalized.length !in 8..96) return false
        if (Regex("\\b(nein|nicht|kein|keine|warte|stopp|abbrechen|vielleicht|später|aber)\\b").containsMatchIn(normalized)) return false
        return normalized in setOf(
            "ja jarvis mach bitte", "ja mach bitte", "ja bitte abschalten", "ja bitte ausschalten",
            "ja schalte das gerät aus", "ja schalte die herdplatte aus", "ja bitte schalte die herdplatte aus"
        )
    }

    private fun telemetryFresh(now: Long, observed: Long): Boolean =
        observed <= now && now - observed <= maxTelemetryAgeMs

    private fun purgeExpired(now: Long) {
        pending.entries.removeAll { it.value.expiresAtMs < now || it.value.createdAtMs > now }
        if (lastAlerts.size > 64) lastAlerts.entries.removeAll { now < it.value || now - it.value >= alertCooldownMs }
    }

    private fun outcome(proposalId: String, deviceId: String, sent: Boolean, confirmed: Boolean, msg: String): VerifiedActuationOutcome {
        val raw = "$proposalId|$deviceId|$sent|$confirmed|$msg"
        val sha = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return VerifiedActuationOutcome(proposalId, deviceId, sent, confirmed, msg, sha)
    }
}
