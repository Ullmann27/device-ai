package at.heinz.deviceai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Experimental, owner-operated Android-on-device build launcher (EXP-00).
 * Uses the user's installed Termux, never interprets chat text or installs an APK.
 */
class ContinuumMobileBuilderActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun px(dp: Int) = (dp * density + 0.5f).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(24), px(20), px(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun paragraph(message: String, bold: Boolean = false): TextView = TextView(this).apply {
            text = message
            textSize = if (bold) 22f else 16f
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, px(10), 0, px(10))
            column.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        paragraph("Continuum · Android Builder", true)
        paragraph("Entwicklung direkt auf diesem Smartphone. Termux und ein geprüfter Device-AI-Checkout unter ~/device-ai sind erforderlich.")
        status = paragraph("Bereit für den lokalen Toolchain-Test. Auf diesem Gerät ist noch kein APK-Build nachgewiesen.")
        for (operation in ContinuumMobileBuildPlan.Operation.entries) {
            val button = Button(this).apply {
                text = operation.title
                isAllCaps = false
                setOnClickListener {
                    if (operation == ContinuumMobileBuildPlan.Operation.BUILD) {
                        AlertDialog.Builder(this@ContinuumMobileBuilderActivity)
                            .setTitle("Lokalen Build starten?")
                            .setMessage("Termux führt Gradle-Tests und einen APK-Build im vorhandenen ~/device-ai-Checkout aus. Das kann länger dauern und Akku/Speicher beanspruchen. Keine Installation erfolgt.")
                            .setNegativeButton("Abbrechen", null)
                            .setPositiveButton("Bauen") { _, _ -> dispatch(operation) }
                            .show()
                    } else dispatch(operation)
                }
            }
            column.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(56)))
        }
        paragraph("Vorbereitung: Termux, Node.js 20+, Git, JDK 17, Gradle 8.11.1, ARM64-aapt2, Android SDK 36 und apksigner. In ~/.termux/termux.properties: allow-external-apps=true.")
        paragraph("Android-Berechtigung: 'Run commands in Termux'. Diese Buttons verwenden ausschließlich feste, vorgegebene Befehle. Jede tatsächliche Ausführung im Termux-Terminal kontrollieren.")
        val scroll = ScrollView(this)
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun dispatch(operation: ContinuumMobileBuildPlan.Operation) {
        if (checkSelfPermission(ContinuumMobileBuildPlan.TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Bitte Termux-Befehle in Android zulassen und anschließend erneut tippen."
            requestPermissions(arrayOf(ContinuumMobileBuildPlan.TERMUX_PERMISSION), 410)
            return
        }
        try {
            packageManager.getPackageInfo(ContinuumMobileBuildPlan.TERMUX_PACKAGE, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            status.text = "Termux fehlt. Ohne einen lokal installierten Worker ist kein Build möglich."
            return
        }
        val plan = ContinuumMobileBuildPlan.command(operation)
        val intent = Intent().apply {
            setClassName(ContinuumMobileBuildPlan.TERMUX_PACKAGE, ContinuumMobileBuildPlan.TERMUX_SERVICE)
            action = ContinuumMobileBuildPlan.TERMUX_ACTION
            putExtra("com.termux.RUN_COMMAND_PATH", plan.executable)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", plan.args.toTypedArray())
            putExtra("com.termux.RUN_COMMAND_WORKDIR", plan.workDir)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        }
        try {
            status.text = if (startService(intent) != null) {
                "An Termux übermittelt: ${operation.title}. Kein bestätigter Erfolg – die Ausgabe in Termux prüfen."
            } else "Termux hat den Auftrag nicht übernommen."
        } catch (_: SecurityException) {
            status.text = "Termux-Zugriff verweigert. RUN_COMMAND und allow-external-apps prüfen."
        } catch (_: IllegalStateException) {
            status.text = "Android verhindert gerade den Dienststart. Termux öffnen und erneut versuchen."
        } catch (e: RuntimeException) {
            status.text = "Start fehlgeschlagen: ${e.javaClass.simpleName}."
        }
    }
}
