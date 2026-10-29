package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import at.heinz.deviceai.intelligence.ContinuumOwnerUpdateCatalog
import at.heinz.deviceai.intelligence.ContinuumOwnerUpdateRelease
import at.heinz.deviceai.intelligence.ContinuumOwnerUpdater

/**
 * User-visible update entry point in the ORIGINAL main application only.
 * Never automatically downloads or installs; Android keeps install consent.
 */
class ContinuumOwnerUpdateActivity : Activity() {
    private lateinit var updater: ContinuumOwnerUpdater
    private lateinit var status: TextView
    private lateinit var checkButton: Button
    private lateinit var installButton: Button
    private lateinit var settingsButton: Button
    private lateinit var progress: ProgressBar
    private var selected: ContinuumOwnerUpdateRelease? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        updater = ContinuumOwnerUpdater(applicationContext)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 32)
        }
        val heading = TextView(this).apply {
            text = "CONTINUUM · SICHERE UPDATES"
            textSize = 21f
            gravity = Gravity.CENTER
        }
        val help = TextView(this).apply {
            text = "Nur von dir freigegebene, originalsignierte Continuum-Lokal-Versionen. " +
                "Erinnerungen, Quellarchive und importierte Modelle bleiben im App-Speicher. " +
                "Android kann eine Installationsbestätigung verlangen."
            textSize = 15f
        }
        status = TextView(this).apply {
            text = "Noch nicht geprüft. Deine installierte App bleibt unverändert."
            textSize = 15f
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = 100
        }
        checkButton = Button(this).apply {
            text = "Auf Updates prüfen"
            setOnClickListener { checkForUpdate() }
        }
        installButton = Button(this).apply {
            text = "Herunterladen und aktualisieren"
            isEnabled = false
            setOnClickListener { confirmUpdate() }
        }
        settingsButton = Button(this).apply {
            text = "Android-Installationsfreigabe"
            visibility = View.GONE
            setOnClickListener {
                runCatching { startActivity(updater.requiredInstallSetting()) }
                    .onFailure { setMessage("Android-Einstellung konnte nicht geöffnet werden.") }
            }
        }
        listOf(heading, help, status, progress, checkButton,
            installButton, settingsButton).forEach { child ->
            panel.addView(child, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 })
        }
        val scroll = android.widget.ScrollView(this).apply { addView(panel) }
        setContentView(scroll)
        val last = getSharedPreferences("continuum-owner-install-v1", MODE_PRIVATE)
            .getString("result", null)
        if (last != null) status.append("\nLetzte Android-Meldung: " + last.take(180))
    }

    private fun setMessage(message: String) {
        if (!isFinishing && !isDestroyed) status.text = message
    }

    private fun checkForUpdate() {
        selected = null
        installButton.isEnabled = false
        checkButton.isEnabled = false
        setMessage("Verbinde sicher mit dem freigegebenen Updatekanal …")
        updater.check { result ->
            if (isFinishing || isDestroyed) return@check
            checkButton.isEnabled = true
            result.fold(
                onSuccess = { check ->
                    when (check) {
                        ContinuumOwnerUpdater.Check.Current ->
                            setMessage("Continuum Lokal ist auf dem freigegebenen aktuellen Stand.")
                        is ContinuumOwnerUpdater.Check.Rejected ->
                            setMessage("Update nicht freigegeben: " + check.explanation)
                        is ContinuumOwnerUpdater.Check.Available -> {
                            selected = check.release
                            installButton.isEnabled = true
                            setMessage("Freigegeben: Continuum Lokal " +
                                check.release.versionName + " (VersionCode " +
                                check.release.descriptor.versionCode + ").\n" +
                                check.release.notes + "\n" +
                                "Größe: " + check.release.descriptor.byteLength / (1024 * 1024) +
                                " MiB. Vor der Installation werden APK und Originalsignatur geprüft.")
                        }
                    }
                },
                onFailure = { setMessage("Keine sichere Updateauskunft verfügbar: " +
                    (it.message ?: it.javaClass.simpleName).take(240) +
                    "\nEs wurde nichts installiert oder gelöscht.") }
            )
        }
    }

    private fun confirmUpdate() {
        val release = selected ?: return
        AlertDialog.Builder(this)
            .setTitle("Continuum Lokal aktualisieren?")
            .setMessage("Die APK wird aus dem freigegebenen HTTPS-Kanal geladen, " +
                "vollständig geprüft und dem Android-Installer übergeben. " +
                "Deine Daten werden nicht exportiert oder gelöscht. Fortfahren?")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Herunterladen") { _, _ -> downloadAndInstall(release) }
            .show()
    }

    private fun downloadAndInstall(release: ContinuumOwnerUpdateRelease) {
        installButton.isEnabled = false
        checkButton.isEnabled = false
        settingsButton.visibility = View.GONE
        progress.visibility = View.VISIBLE
        progress.progress = 0
        setMessage("Überprüfter Download wird vorbereitet …")
        updater.downloadAndPrepare(release,
            progress = { downloaded, size ->
                if (!isFinishing && !isDestroyed && size > 0L) {
                    progress.progress = ((downloaded * 100L) / size).toInt().coerceIn(0, 100)
                    setMessage("APK wird geprüft und geladen: " + progress.progress + "%")
                }
            },
            done = { result ->
                if (isFinishing || isDestroyed) return@downloadAndPrepare
                progress.visibility = View.GONE
                checkButton.isEnabled = true
                result.fold(
                    onSuccess = {
                        setMessage(it + "\nBitte im Android-Installationsdialog auf Aktualisieren tippen.")
                    },
                    onFailure = {
                        setMessage("Aktualisierung gestoppt: " +
                            (it.message ?: it.javaClass.simpleName).take(220) +
                            "\nBestehende Version und private Daten bleiben erhalten.")
                        if (Build.VERSION.SDK_INT >= 26 &&
                            !packageManager.canRequestPackageInstalls()) {
                            settingsButton.visibility = View.VISIBLE
                        }
                    }
                )
            }
        )
    }

    override fun onDestroy() {
        updater.close()
        super.onDestroy()
    }
}
