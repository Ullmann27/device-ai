package at.heinz.deviceai.cockpit

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Production [OwnerRecognitionSource] with ONE real signal: the device is unlocked (by its owner's credential).
 * No camera, no face recognition, no JarvisOwnerIntentVerifier. Locked or unknown → UNKNOWN.
 */
class KeyguardOwnerRecognitionSource(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : OwnerRecognitionSource {
    private val app = context.applicationContext
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private var unlockedSince: Long? = null

    override fun current(): OwnerRecognition {
        val locked = runCatching { keyguard?.isDeviceLocked }.getOrNull()
        if (locked == false && unlockedSince == null) unlockedSince = clock()
        if (locked != false) unlockedSince = null
        return map(locked, unlockedSince)
    }

    override fun addListener(l: (OwnerRecognition) -> Unit): AutoCloseable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) unlockedSince = clock()
                l(current())
            }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_OFF) }
        app.registerReceiver(receiver, filter)
        return AutoCloseable { runCatching { app.unregisterReceiver(receiver) } }
    }

    companion object {
        const val METHOD_UNLOCK = "Geräte-Entsperrung"

        /** Keyguard state → card model. Unlocked = RECOGNIZED; locked or unknown = UNKNOWN (never guessed). */
        fun map(deviceLocked: Boolean?, sinceMillis: Long?): OwnerRecognition = when (deviceLocked) {
            false -> OwnerRecognition(OwnerRecognitionState.RECOGNIZED, METHOD_UNLOCK, sinceMillis)
            else -> OwnerRecognition.UNKNOWN
        }
    }
}
