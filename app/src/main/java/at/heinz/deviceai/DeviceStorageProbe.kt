package at.heinz.deviceai

import java.io.File

/** A dated observation of the app's own volume, using Android/JVM filesystem APIs.
 * No directory scan, user path, shell, network, owner fact or model weight access.
 */
object DeviceStorageProbe {
    fun read(appDirectory: File): String {
        require(appDirectory.isDirectory) { "App storage volume is unavailable." }
        val total = appDirectory.totalSpace
        val available = appDirectory.usableSpace
        check(total > 0 && available in 0..total) { "Filesystem did not provide valid storage statistics." }
        val observedAt = System.currentTimeMillis()
        check(observedAt >= 0)
        return "Speicherbeobachtung v1\nBereich: App-Datenträger\n" +
            "Gesamt: $total Byte\nFür die App nutzbar: $available Byte\nZeitpunkt: $observedAt"
    }
}
