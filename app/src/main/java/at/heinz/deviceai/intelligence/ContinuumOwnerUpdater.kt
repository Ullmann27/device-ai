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

            val renderAttempt = runCatching {
                val json = readTextExact(
                    ContinuumOwnerUpdateCatalog.MANIFEST_URL,
                    ContinuumOwnerUpdateCatalog.HOST,
                    ContinuumOwnerUpdateCatalog.MANIFEST_PATH,
                    ContinuumOwnerUpdateCatalog.MAX_JSON_BYTES
                )
                ContinuumOwnerUpdateCatalog.parseAvailable(json)
            }
            val githubAttempt = runCatching {
                val json = readTextExact(
                    ContinuumGitHubReleaseCatalog.API_URL,
                    ContinuumGitHubReleaseCatalog.API_HOST,
                    ContinuumGitHubReleaseCatalog.API_PATH,
                    ContinuumGitHubReleaseCatalog.MAX_JSON_BYTES,
                    accept = "application/vnd.github+json"
                )
                ContinuumGitHubReleaseCatalog.parseLatest(json)
            }

            val releases = listOfNotNull(renderAttempt.getOrNull(), githubAttempt.getOrNull())
            if (releases.isEmpty()) {
                if (renderAttempt.isFailure && githubAttempt.isFailure) {
                    val detail = listOfNotNull(
                        renderAttempt.exceptionOrNull()?.message,
                        githubAttempt.exceptionOrNull()?.message
                    ).joinToString(" · ").take(320)
                    error("Kein sicherer Updatekanal erreichbar: " + detail)
                }
                return@submit Check.NoRelease
            }

            val candidates = releases.map { release ->
                ContinuumOwnerReleaseSelector.Candidate(
                    release,
                    preflight(version, signer, release)
                )
            }
            ContinuumOwnerReleaseSelector.select(candidates)?.let {
                return@submit Check.Available(it)
            }

            if (candidates.any {
                    (it.preflight as? ContinuumUpdatePreflight.Rejected)?.reason ==
                        ContinuumUpdateRejection.OLD_OR_EQUAL_VERSION
                }) return@submit Check.Current

            val rejection = candidates.mapNotNull {
                it.preflight as? ContinuumUpdatePreflight.Rejected
            }.firstOrNull()
            if (rejection != null)
                Check.Rejected("Freigabeprüfung verweigert: " + rejection.reason)
            else Check.NoRelease
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
            check(preflight(currentVersion, original, release)
                is ContinuumUpdatePreflight.ReadyForActualApkVerification) {
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

    private fun preflight(
        installedVersion: Int,
        installedSigner: String,
        release: ContinuumOwnerUpdateRelease
    ): ContinuumUpdatePreflight = when (release.source) {
        ContinuumOwnerUpdateSource.RENDER -> ContinuumSignedUpdatePolicy.check(
            context.packageName, installedVersion, installedSigner,
            ContinuumOwnerUpdateCatalog.HOST, release.descriptor
        )
        ContinuumOwnerUpdateSource.GITHUB_RELEASE -> ContinuumGitHubSignedUpdatePolicy.check(
            context.packageName, installedVersion, installedSigner, release.descriptor
        )
    }

    private fun openHttps(url: String): HttpsURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Continuum-Android-Updater")
            useCaches = false
        }

    private fun connectExact(url: String, expectedHost: String, expectedPath: String): HttpsURLConnection {
        val uri = java.net.URI(url)
        check(uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(expectedHost, ignoreCase = true) &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawPath == expectedPath) { "Unzulässiger Update-Server" }
        return openHttps(url)
    }

    private fun readTextExact(
        url: String,
        expectedHost: String,
        expectedPath: String,
        maxBytes: Int,
        accept: String? = null
    ): String {
        val connection = connectExact(url, expectedHost, expectedPath)
        accept?.let { connection.setRequestProperty("Accept", it) }
        if (expectedHost.equals(ContinuumGitHubReleaseCatalog.API_HOST, ignoreCase = true))
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            check(connection.responseCode == 200) {
                "Kein freigegebener Update-Katalog (HTTP " + connection.responseCode + ")"
            }
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

    private fun openDownloadConnection(release: ContinuumOwnerUpdateRelease): HttpsURLConnection {
        if (release.source == ContinuumOwnerUpdateSource.RENDER) {
            val path = java.net.URI(release.descriptor.apkUrl).rawPath
            return connectExact(release.descriptor.apkUrl, ContinuumOwnerUpdateCatalog.HOST, path)
        }

        var current = java.net.URI(release.descriptor.apkUrl)
        var redirects = 0
        while (true) {
            val connection = openHttps(current.toString())
            val code = connection.responseCode
            if (code !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            check(redirects < ContinuumGitHubDownloadRedirectPolicy.MAX_REDIRECTS) {
                "Zu viele GitHub-Download-Weiterleitungen"
            }
            current = ContinuumGitHubDownloadRedirectPolicy.resolve(current, location.orEmpty())
                ?: error("GitHub-Download wurde auf einen nicht freigegebenen Host umgeleitet")
            redirects++
        }
    }

    private fun download(
        release: ContinuumOwnerUpdateRelease,
        destination: File,
        progress: (Long, Long) -> Unit
    ) {
        val entry = release.descriptor
        val connection = openDownloadConnection(release)
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
                            // Capture the value before posting; 'total' continues changing
                            // while the main-thread callback waits in its message queue.
                            val receivedBytes = total
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                progress(receivedBytes, entry.byteLength)
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
                // PackageInstaller session IDs distinguish concurrent/previous updates.
                // A fixed request code allows an older callback to inherit a newer
                // expected version via FLAG_UPDATE_CURRENT.
                val pending = PendingIntent.getBroadcast(
                    context, id, callback,
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
