package at.heinz.deviceai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Bounded read-only diagnostics from the app's own Android network APIs.
 * ACCESS_NETWORK_STATE is already granted at install time. This deliberately
 * does not resolve hosts, contact websites or inspect private interface data.
 * Android VALIDATED is only an OS reachability signal, not proof that a
 * particular research/search provider accepts HTTPS requests.
 */
internal object DeviceNetworkProbe {
    fun read(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return describe(false, false, false, emptyList())
        val network = manager.activeNetwork
            ?: return describe(false, false, false, emptyList())
        val caps = manager.getNetworkCapabilities(network)
            ?: return describe(true, false, false, emptyList())
        val transports = buildList {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WLAN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("Mobilfunk")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("Ethernet")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        }
        return describe(
            active = true,
            internetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            transports = transports
        )
    }

    internal fun describe(
        active: Boolean,
        internetCapability: Boolean,
        validated: Boolean,
        transports: List<String>
    ): String = buildString {
        append("Android-Netzwerk: ").append(if (active) "aktiv" else "kein aktives Netzwerk")
        append("\nTransport: ").append(transports.take(4).joinToString(", ").ifBlank { "unbekannt" })
        append("\nInternet-Capability: ").append(if (internetCapability) "vorhanden" else "nicht gemeldet")
        append("\nAndroid-Validierung: ").append(if (validated) "VALIDATED" else "nicht bestätigt")
        append("\nHinweis: Android-Validierung beweist nicht, dass Bing, DuckDuckGo oder andere Webseiten erreichbar sind.")
    }
}
