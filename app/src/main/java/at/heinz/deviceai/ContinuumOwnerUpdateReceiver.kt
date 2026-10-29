package at.heinz.deviceai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

/**
 * Receives only Android PackageInstaller callbacks sent to a private PendingIntent.
 * Statuses remain truthful: installation success is verified against OS package info.
 */
class ContinuumOwnerUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT ||
            context.packageName != PACKAGE_NAME) return
        val prefs = context.getSharedPreferences("continuum-owner-install-v1", Context.MODE_PRIVATE)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                prefs.edit().putString("result", "ANDROID_CONFIRMATION_REQUIRED").apply()
                val action: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (action != null) {
                    action.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(action) }.onFailure {
                        prefs.edit().putString("result", "ANDROID_DIALOG_FAILED").apply()
                    }
                } else {
                    prefs.edit().putString("result", "ANDROID_DIALOG_MISSING").apply()
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val expected = intent.getIntExtra(EXTRA_VERSION, -1)
                val checked = runCatching {
                    val pm = context.packageManager
                    val info = pm.getPackageInfo(PACKAGE_NAME,
                        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                        else PackageManager.GET_SIGNATURES)
                    val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
                        else info.versionCode.toLong()
                    val signers = if (Build.VERSION.SDK_INT >= 28) {
                        info.signingInfo?.apkContentsSigners
                    } else info.signatures
                    check(signers != null && signers.size == 1)
                    val signerHash = MessageDigest.getInstance("SHA-256")
                        .digest(signers[0].toByteArray())
                        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
                    expected > 0 && version == expected.toLong() &&
                        signerHash == ORIGINAL_SIGNER
                }.getOrDefault(false)
                prefs.edit().putString("result",
                    if (checked) "INSTALLED_AND_VERIFIED" else "OS_SUCCESS_BUT_READBACK_FAILED").apply()
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                prefs.edit().putString("result",
                    "ANDROID_INSTALL_FAILED_${status}:${message.take(120)}").apply()
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "at.heinz.deviceai.continuumlocal.OWNER_INSTALL_RESULT"
        const val EXTRA_VERSION = "expected_version_code"
        private const val PACKAGE_NAME = "at.heinz.deviceai.continuumlocal"
        private const val ORIGINAL_SIGNER =
            "6354e91dc0ca8df9173885f32402fd817f46e798f369206d4fd710ff3d03ee23"
    }
}
