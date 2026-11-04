package at.heinz.deviceai.jarvis

/** Only an endpoint selector: never owns or copies the existing Continuum dialogue or EWM2 memory. */
data class JarvisSessionRoute(
    val existingSessionId: String,
    val endpoint: JarvisActiveEndpoint,
    val revision: Long
)

/** Real implementations must authenticate both the owner and the requested endpoint. */
fun interface JarvisEndpointHandoffGate {
    fun allows(existingSessionId: String, from: JarvisActiveEndpoint, to: JarvisActiveEndpoint): Boolean
}

/**
 * Keeps the existing session identity across devices. It does not establish transports, trust a Bluetooth
 * device automatically, or expose session data. A rejected handoff leaves the prior route unchanged.
 */
class JarvisSessionHandoff(
    existingSessionId: String,
    initialEndpoint: JarvisActiveEndpoint
) {
    private var route = JarvisSessionRoute(existingSessionId, initialEndpoint, 0L)

    init { require(existingSessionId.isNotBlank() && existingSessionId.length <= 128) }

    @Synchronized fun current(): JarvisSessionRoute = route

    @Synchronized fun request(to: JarvisActiveEndpoint, gate: JarvisEndpointHandoffGate): JarvisSessionRoute? {
        if (to == route.endpoint) return route
        val approved = runCatching { gate.allows(route.existingSessionId, route.endpoint, to) }.getOrDefault(false)
        if (!approved || route.revision == Long.MAX_VALUE) return null
        route = route.copy(endpoint = to, revision = route.revision + 1L)
        return route
    }
}
