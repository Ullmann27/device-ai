package at.heinz.deviceai.intelligence

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import at.heinz.deviceai.ContinuumOwnerUpdateReceiver
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import android.app.PendingIntent

/**
 * One user-initiated, bounded release operation. No background updater, elevated
 * permissions, personal data export or alternate app signing.
 */
internal class ContinuumOwnerUpdater(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor { work ->
        Thread(work, "continuum-owner-update").apply { isDaemon = true }
    }
    private val active = AtomicBoolean(false)

    sealed interface Check {
        data object Current : Check
        data object NoRelease : Check
        data class Available(val release: ContinuumOwnerUpdateRelease) : Check
        data class Rejected(val explanation: String) : Check
    }

    fun check(done: (Result<Check>) -> Unit) {
        submit(done) {
            val version = installedVersionCode()
            val signer = installedSignerSha256()
            val json = readText(ContinuumOwnerUpdateCatalog.MANIFEST_URL,
                ContinuumOwnerUpdateCatalog.MAX_JSON_BYTES)
            val release = ContinuumOwnerUpdateCatalog.parseAvailable(json)
                ?: return@submit Check.NoRelease
            if (release.descriptor.versionCode <= version) return@submit Check.Current
            val policy = ContinuumSignedUpdatePolicy.check(
                context.packageName, version, signer,
                ContinuumOwnerUpdateCatalog.HOST, release.descriptor
            )
            if (policy !is ContinuumUpdatePreflight.ReadyForActualApkVerification)
                return@submit Check.Rejected("Freigabeprüfung verweigert: $policy")
            Check.Available(release)
        }
    }

    fun downloadAndPrepare(
        release: ContinuumOwnerUpdateRelease,
        progress: (Long, Long) -> Unit,
        done: (Result<String>) -> Unit
    ) {
        submit(done) {
            val currentVersion = installedVersionCode()
            val original = installedSignerSha256()
            check(ContinuumSignedUpdatePolicy.check(
                context.packageName, currentVersion, original,
                ContinuumOwnerUpdateCatalog.HOST, release.descriptor
            ) is ContinuumUpdatePreflight.ReadyForActualApkVerification) {
                "Installationsfreigabe nicht mehr gültig"
            }

            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls())
                error("Android muss Installationen aus Continuum erst ausdrücklich erlauben")

            val stage = File(context.cacheDir, "owner-update")
            check(stage.isDirectory || stage.mkdirs()) { "Update-Zwischenspeicher nicht zugänglich" }
            check(!java.nio.file.Files.isSymbolicLink(stage.toPath())) { "Ungültiger Updatepfad" }
            stage.listFiles()?.filter { it.name.startsWith("continuum-") }?.forEach { it.delete() }
            val required = release.descriptor.byteLength + 64L * 1024L * 1024L
            check(StatFs(stage.absolutePath).availableBytes > required) {
                "Nicht ausreichend freier Speicher für die APK"
            }
            val pending = File(stage, "continuum-download.part")
            val verified = File(stage, "continuum-owner.apk")
            try {
                download(release, pending, progress)
                verifyArchive(pending, release)
                check(pending.renameTo(verified)) { "APK konnte nicht sicher bereitgestellt werden" }
                commitToAndroid(verified, release)
                "APK geprüft und an Android übergeben. Die Systeminstallation muss bestätigt werden."
            } finally {
                pending.delete()
            }
        }
    }

    private fun <T> submit(done: (Result<T>) -> Unit, job: () -> T) {
        if (!active.compareAndSet(false, true)) {
            done(Result.failure(IllegalStateException("Eine Updateprüfung läuft bereits")))
            return
        }
        worker.execute {
            val result = runCatching(job)
            active.set(false)
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(result) }
        }
    }

    private fun connect(url: String, expectedPath: String): HttpsURLConnection {
        val endpoint = URL(url)
        check(endpoint.protocol == "https" &&
            endpoint.host.equals(ContinuumOwnerUpdateCatalog.HOST, ignoreCase = true) &&
            endpoint.port in listOf(-1, 443) &&
            endpoint.path == expectedPath && endpoint.query == null && endpoint.ref == null &&
            java.net.URI(url).userInfo == null) { "Unzulässiger Update-Server" }
        return (endpoint.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            useCaches = false
        }
    }

    private fun readText(url: String, maxBytes: Int): String {
        val connection = connect(url, ContinuumOwnerUpdateCatalog.MANIFEST_PATH)
        try {
            check(connection.responseCode == 200) { "Kein freigegebener Update-Katalog (HTTP ${connection.responseCode})" }
            check(connection.contentLengthLong in -1..maxBytes.toLong()) { "Update-Katalog zu groß" }
            val bytes = connection.inputStream.use { source ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val n = source.read(chunk)
                    if (n < 0) break
                    check(out.size() + n <= maxBytes) { "Update-Katalog zu groß" }
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            }
            return bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun download(
        release: ContinuumOwnerUpdateRelease,
        destination: File,
        progress: (Long, Long) -> Unit
    ) {
        val entry = release.descriptor
        val expectedPath = java.net.URI(entry.apkUrl).rawPath
        val connection = connect(entry.apkUrl, expectedPath)
        try {
            check(connection.responseCode == 200) { "APK nicht verfügbar (HTTP ${connection.responseCode})" }
            check(connection.contentLengthLong in listOf(-1L, entry.byteLength)) {
                "APK-Größe weicht von der Freigabe ab"
            }
            val md = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileOutputStream(destination).use { out ->
                connection.inputStream.use { source ->
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) error("Download unterbrochen")
                        val n = source.read(chunk)
                        if (n < 0) break
                        total += n
                        check(total <= entry.byteLength) { "APK übersteigt die freigegebene Größe" }
                        md.update(chunk, 0, n)
                        out.write(chunk, 0, n)
                        if (total == entry.byteLength || total % (1024 * 1024) < n) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                progress(total, entry.byteLength)
                            }
                        }
                    }
                }
                out.fd.sync()
            }
            check(total == entry.byteLength &&
                md.digest().joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) } ==
                entry.apkSha256) { "APK-Prüfsumme oder Größe ungültig" }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyArchive(file: File, release: ContinuumOwnerUpdateRelease) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(
            file.absolutePath,
            if (Build.VERSION.SDK_INT >= 28)
                PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        ) ?: error("Heruntergeladene APK ist kein gültiges Paket")

        check(archive.packageName == ContinuumSignedUpdatePolicy.OWNER_PACKAGE) {
            "Falsche APK-Paketkennung"
        }
        val version = versionCode(archive)
        check(version == release.descriptor.versionCode && version > installedVersionCode()) {
            "APK-Version weicht von freigegebener Aktualisierung ab"
        }
        check(signingFingerprint(archive) == ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256 &&
            installedSignerSha256() == ContinuumSignedUpdatePolicy.ORIGINAL_SIGNER_SHA256) {
            "Originale APK-Signatur nicht bestätigt"
        }
    }

    private fun commitToAndroid(file: File, release: ContinuumOwnerUpdateRelease) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ContinuumSignedUpdatePolicy.OWNER_PACKAGE)
        params.setSize(release.descriptor.byteLength)
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                FileInputStream(file).use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val callback = Intent(context, ContinuumOwnerUpdateReceiver::class.java)
                    .setAction(ContinuumOwnerUpdateReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(ContinuumOwnerUpdateReceiver.EXTRA_VERSION,
                        release.descriptor.versionCode)
                val pending = PendingIntent.getBroadcast(
                    context, 7155, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw error
        }
    }

    fun close() { worker.shutdownNow() }

    fun requiredInstallSetting(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"))

    private fun installedVersionCode(): Int {
        check(context.packageName == ContinuumSignedUpdatePolicy.OWNER_PACKAGE) {
            "Nur die originale Continuum-Haupt-App kann aktualisiert werden"
        }
        return versionCode(installedInfo())
    }

    private fun versionCode(info: PackageInfo): Int =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode

    private fun installedSignerSha256(): String = signingFingerprint(installedInfo())

    private fun installedInfo(): PackageInfo =
        context.packageManager.getPackageInfo(context.packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES)

    private fun signingFingerprint(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val infoSignatures = info.signingInfo ?: error("APK-Signatur nicht auslesbar")
            infoSignatures.apkContentsSigners
        } else info.signatures
        check(signatures != null && signatures.size == 1) { "Unerwartete APK-Signaturanzahl" }
        return MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
    }
}
