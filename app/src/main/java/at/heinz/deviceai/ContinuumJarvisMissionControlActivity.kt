package at.heinz.deviceai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.learning.ContinuumTrainingMonitor
import at.heinz.deviceai.learning.ContinuumPublicStudyScheduler
import at.heinz.deviceai.cognitive.ContinuumAutonomyControl
import at.heinz.deviceai.cognitive.ContinuumAutonomyLevel
import at.heinz.deviceai.cognitive.DeviceTeamleaderStatusStore
import at.heinz.deviceai.cognitive.ContinuumDnaGuardian
import at.heinz.deviceai.cognitive.AndroidKeystoreIdentitySigner
import at.heinz.deviceai.cognitive.DnaIntegrityState

/**
 * Read-only, evidence-backed JARVIS-inspired operations cockpit.
 *
 * The buildings are a light Canvas visualisation, not employees or autonomous
 * virtual processes. Every status comes from an existing Continuum receipt.
 * No microphone, network, shell, arbitrary plugin, or additional AI is started.
 */
class ContinuumJarvisMissionControlActivity : Activity() {
    private lateinit var detail: TextView
    private lateinit var overview: TextView
    private var selection = 0
    private val ink = Color.rgb(233, 235, 241)
    private val muted = Color.rgb(167, 176, 191)
    private val background = Color.rgb(11, 14, 23)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            setBackgroundColor(this@ContinuumJarvisMissionControlActivity.background)
        }
        fun label(text: String, size: Float, color: Int): TextView =
            TextView(this).apply {
                this.text = text
                this.textSize = size
                setTextColor(color)
                setPadding(0, 0, 0, dp(12))
            }

        content.addView(label("CONTINUUM · MISSION CONTROL", 21f, ink))
        content.addView(label(
            "JARVIS-inspirierte Übersicht. Die Gebäude sind eine Navigation, " +
                "keine simulierten Mitarbeiter. Status nur aus echten Gerätebelegen.",
            14f, muted
        ))
        overview = label("", 14f, ink)
        content.addView(overview)

        val buildings = MissionBuildingsView(this) { selected ->
            selection = selected
            showDetails()
        }.apply {
            contentDescription = "Drei auswählbare Bereiche: Forschung, Entwicklungsworker und APK-Updates"
            isFocusable = true
        }
        content.addView(buildings, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)
        ))
        detail = label("", 16f, ink)
        content.addView(detail)

        val refresh = Button(this).apply {
            text = "Echte Gerätestände aktualisieren"
            setOnClickListener { showDetails() }
        }
        content.addView(refresh)

        val openUpdater = Button(this).apply {
            text = "Updates prüfen und laden"
            setOnClickListener {
                startActivity(Intent(this@ContinuumJarvisMissionControlActivity,
                    ContinuumOwnerUpdateActivity::class.java))
            }
        }
        content.addView(openUpdater)

        val back = Button(this).apply {
            text = "Zurück zu Continuum"
            setOnClickListener { finish() }
        }
        content.addView(back)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
        showDetails()
    }

    override fun onResume() {
        super.onResume()
        if (::detail.isInitialized) showDetails()
    }

    private fun showDetails() {
        val channels = listOf(
            ContinuumTrainingMonitor.PUBLIC,
            ContinuumTrainingMonitor.OFFLINE,
            ContinuumTrainingMonitor.CDX
        )
        val states = channels.map { ContinuumTrainingMonitor.read(this, it) }
        val fresh = states.count { it.active && !it.stale }
        val ownerLevel = ContinuumAutonomyControl.level(this)
        val guardian = runCatching {
            ContinuumDnaGuardian(java.io.File(filesDir, "continuum")) {
                AndroidKeystoreIdentitySigner()
            }.inspect()
        }.getOrNull()
        val guardianState = when (guardian?.state) {
            DnaIntegrityState.VERIFIED -> "Identität und Archiv geprüft"
            DnaIntegrityState.INVALID -> "IDENTITÄT FEHLERHAFT – keine autonome Freigabe"
            DnaIntegrityState.NOT_INITIALIZED -> "Identitätsbeleg fehlt"
            null -> "Identitätsprüfung fehlgeschlagen"
        }
        val partner = runCatching { DeviceTeamleaderStatusStore(filesDir).read() }.getOrNull()
        val partnerState = if (partner == null) "kein belegter Prüflauf"
            else if (System.currentTimeMillis() - partner.checkedAt > 2L * 60L * 60L * 1000L ||
                partner.checkedAt > System.currentTimeMillis() + 60_000L) "letzter Prüflauf veraltet"
            else "letzter Prüflauf: " + partner.state
        overview.text = "CONTINUUM · HAUPTKERN / CODING-WÄCHTER\n" +
            "Hauptkern: " + guardianState + "\n" +
            "Technischer Partner: " + partnerState + "\n" +
            "Autonomie: " + ownerLevel.name + "\n" +
            "Beobachtete Lernkanäle: $fresh/3 aktiv (45-Sekunden-Lebenszeichen). " +
            "Der Partner ist kein zweites frei laufendes LLM; ohne frische Belege gibt es keinen aktiven Schutz-Nachweis."

        val build = getSharedPreferences(
            ContinuumMobileBuildResultReceiver.PREFERENCES, Context.MODE_PRIVATE
        )
        val install = getSharedPreferences("continuum-owner-install-v1", Context.MODE_PRIVATE)
        val report = when (selection) {
            0 -> {
                val lines = states.map { state ->
                    val stateLabel = when {
                        state.stale -> "UNBESTÄTIGT / VERALTET"
                        state.active -> "AKTIV – ${state.phase}"
                        state.changedAt == 0L -> "KEIN BELEG"
                        else -> "LETZTES ERGEBNIS – ${state.outcome}"
                    }
                    "${state.channel.uppercase()}: $stateLabel"
                }
                "FORSCHUNG / GEDÄCHTNIS\n" + lines.joinToString("\n") +
                    "\n\n" + ContinuumPublicStudyScheduler(this).display() +
                    "\nDie angezeigten Quellen- und Versuchszähler sind Forschungsjob-Protokolle, " +
                    "nicht automatisch der verifizierte Archivbestand. " +
                    "Diese Anzeige bestätigt weder Modellintelligenz noch die Qualität gespeicherter Fakten."
            }
            1 -> {
                val job = build.getString(ContinuumMobileBuildResultReceiver.JOB_ID, null)
                val result = build.getString(ContinuumMobileBuildResultReceiver.LAST_MESSAGE, null)
                val commit = build.getString(ContinuumMobileBuildResultReceiver.LAST_SHA, null)
                "ENTWICKLUNGSWORKER / CCC1\n" +
                    (if (job != null) "Vorgang vorhanden (nicht automatisch als aktiv bestätigt)." else
                        "Kein laufender Vorgang aus dem Callback belegt.") +
                    "\nLetzte Rückmeldung: ${result?.take(160) ?: "KEIN BELEG"}" +
                    "\nLetzter Quellstand: ${commit?.take(40) ?: "NICHT ERFASST"}" +
                    "\nEin echter Coding-Erfolg benötigt unabhängige grüne Tests."
            }
            else -> {
                val update = install.getString("result", null)
                "APK / FREIGABEN\n" +
                    "Letzter vom Android-Installer gespeicherter Status: " +
                    (update?.take(170) ?: "KEIN INSTALLATIONSBELEG") +
                    "\nEin APK-Build und eine erfolgreiche Installation sind unterschiedliche Nachweise." +
                    "\nNächster Schritt: Updates prüfen und laden."
            }
        }
        detail.text = report
    }

    /** Isometric-like three-building navigation rendered once per interaction. */
    private class MissionBuildingsView(
        context: Context, private val choose: (Int) -> Unit
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val hitAreas = Array(3) { RectF() }
        private val labels = arrayOf("FORSCHUNG", "CODING", "RELEASE")
        private val levels = intArrayOf(0, 1, 2)
        private var startX = 0f
        private var moved = false

        private fun polygon(canvas: Canvas, color: Int, vararg xy: Float) {
            val shape = Path().apply {
                moveTo(xy[0], xy[1])
                var i = 2
                while (i < xy.size) {
                    lineTo(xy[i], xy[i + 1])
                    i += 2
                }
                close()
            }
            paint.color = color
            paint.style = Paint.Style.FILL
            canvas.drawPath(shape, paint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(15, 18, 31))
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            paint.color = Color.rgb(44, 54, 75)
            paint.strokeWidth = 1f
            for (line in 1..6) {
                val y = h * (0.65f + line * 0.045f)
                canvas.drawLine(0f, y, w, y, paint)
            }

            val cell = w / 3f
            for (i in 0..2) {
                val x1 = cell * (i + 0.17f)
                val x2 = cell * (i + 0.76f)
                val bottom = h * 0.72f
                val top = bottom - h * (0.28f + 0.09f * levels[i])
                val depth = cell * 0.11f
                val lift = depth * 0.42f
                hitAreas[i].set(x1 - depth, top - lift, x2 + depth, bottom + depth)
                polygon(canvas, Color.rgb(111, 30, 53),
                    x2, top, x2 + depth, top - lift, x2 + depth, bottom - lift, x2, bottom)
                polygon(canvas, Color.rgb(191, 64, 66),
                    x1, top, x2, top, x2 + depth, top - lift, x1 + depth, top - lift)
                paint.color = Color.rgb(154 + i * 12, 38 + i * 10, 68)
                canvas.drawRect(x1, top, x2, bottom, paint)
                paint.color = Color.rgb(250, 175, 100)
                val margin = (x2 - x1) / 6f
                for (row in 1..3) for (col in 1..3) {
                    val left = x1 + margin * col
                    val y = top + (bottom - top) * row / 5f
                    canvas.drawRect(left, y, left + margin * 0.45f,
                        y + margin * 0.55f, paint)
                }
                paint.color = Color.WHITE
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = (cell * 0.115f).coerceIn(12f, 21f)
                canvas.drawText(labels[i], (x1 + x2) * 0.5f, bottom + h * 0.085f, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.x - startX) > 24f) moved = true
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val index = hitAreas.indexOfFirst { it.contains(event.x, event.y) }
                        if (index >= 0) {
                            choose(index)
                            performClick()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
