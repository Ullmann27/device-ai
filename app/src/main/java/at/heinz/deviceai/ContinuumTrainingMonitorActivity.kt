package at.heinz.deviceai

import android.app.Activity
import android.app.job.JobScheduler
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import at.heinz.deviceai.learning.ContinuumAutonomousScheduler
import at.heinz.deviceai.learning.ContinuumPublicStudyScheduler
import at.heinz.deviceai.learning.ContinuumTrainingMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Read-only visualisation backed by actual JobService events, never simulated training. */
class ContinuumTrainingMonitorActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var orb: TrainingOrbView
    private lateinit var headline: TextView
    private lateinit var subline: TextView
    private lateinit var publicCard: TextView
    private lateinit var offlineCard: TextView
    private lateinit var cdxCard: TextView
    private lateinit var evidenceCard: TextView
    private var visible = false

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun stamp(time: Long): String =
        if (time <= 0L) "noch nicht erfasst" else
            SimpleDateFormat("dd.MM. HH:mm:ss", Locale.GERMANY).format(Date(time))

    private fun panel(background: Int, radius: Float = 22f) =
        GradientDrawable().apply {
            cornerRadius = dp(radius.toInt()).toFloat()
            setColor(background)
            setStroke(dp(1), Color.rgb(54, 86, 143))
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun addCard(layout: LinearLayout): TextView =
        label("", 14f, Color.rgb(220, 231, 252)).apply {
            background = panel(Color.rgb(14, 27, 55))
            setPadding(dp(17), dp(15), dp(17), dp(16))
            layout.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 11, 29)
        window.navigationBarColor = Color.rgb(5, 11, 29)
        val page = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(5, 11, 29))
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(23), dp(20), dp(30))
        }
        page.addView(content)
        val back = label("‹  ZURÜCK ZUM LERNZENTRUM", 12f,
            Color.rgb(98, 211, 255), true).apply {
            setPadding(0, dp(5), 0, dp(15))
            setOnClickListener { finish() }
            isClickable = true
            contentDescription = "Zurück zum Lernzentrum"
        }
        content.addView(back)
        content.addView(label("CONTINUUM  /  NEURAL OPERATIONS", 11f,
            Color.rgb(116, 151, 219), true))
        content.addView(label("TRAININGS-MONITOR", 26f,
            Color.rgb(242, 248, 255), true))
        content.addView(label("Echte Hintergrundereignisse · keine simulierte Aktivität", 12f,
            Color.rgb(145, 164, 198)))
        orb = TrainingOrbView(this)
        content.addView(orb, LinearLayout.LayoutParams(-1, dp(222)))
        headline = label("", 22f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        subline = label("", 13f, Color.rgb(172, 193, 230)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }
        content.addView(headline)
        content.addView(subline)
        publicCard = addCard(content)
        offlineCard = addCard(content)
        cdxCard = addCard(content)
        evidenceCard = addCard(content)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            content.addView(label(
                if (granted) "✓  FEHLERBENACHRICHTIGUNGEN AKTIV"
                else "🔔  ECHTE TRAININGSFEHLER AUFS HANDY MELDEN · ERLAUBEN",
                12f, Color.rgb(121, 224, 255), true
            ).apply {
                setPadding(dp(8), dp(16), dp(8), dp(12))
                if (!granted) {
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Benachrichtigungen für tatsächliche Trainingsfehler erlauben"
                    setOnClickListener {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            913)
                    }
                }
            })
        }
        content.addView(label(
            "Eine Statusanimation ist kein Beweis für Modellverbesserung. Entscheidend sind " +
                "akzeptierte Checkpoints und unabhängige Holdout-Prüfungen. Android darf Jobs " +
                "verschieben; Zeitangaben sind Näherungen, keine zugesagten Startzeiten.",
            11f, Color.rgb(150, 170, 205)
        ).apply { setPadding(dp(3), dp(18), dp(3), dp(6)) })
        setContentView(page)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            refresh()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        visible = true
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onPause() {
        visible = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun stateLine(channel: String, name: String): String {
        val s = ContinuumTrainingMonitor.read(this, channel)
        val state = when {
            s.trainingNow -> "TRAINIERT GERADE"
            s.researchingNow -> "ARBEITET GERADE"
            s.stale -> "LAUFSTATUS UNGEKLÄRT (kein aktuelles Signal)"
            else -> "WARTET AUF NÄCHSTEN ZYKLUS / MANUELLEN START"
        }
        return buildString {
            append(name).append("\n").append(state)
            if (s.model.isNotBlank()) append("\nModell: ").append(s.model)
            if (s.subject.isNotBlank()) append("\nThema: ").append(s.subject)
            if (s.active && !s.stale) append("\nAktuelle Phase: ").append(phaseName(s.phase))
            if (s.detail.isNotBlank()) append("\nDetails: ").append(s.detail)
            append("\nLetztes Ergebnis: ").append(outcomeName(s.outcome))
            append("\nLetzter Start: ").append(stamp(s.startedAt))
            append("\nLetzter Abschluss: ").append(stamp(s.endedAt))
        }
    }

    private fun phaseName(phase: String): String = when (phase) {
        "PREPARING" -> "Auftrag vorbereiten"
        "FETCH_SOURCE" -> "Wikipedia-Quelle abrufen"
        "DISCOVER_TOPICS" -> "Neue Themen untersuchen"
        "ARCHIVE_SOURCE" -> "Quelle und Herkunft archivieren"
        "TRAIN_WEIGHTS" -> "Echte Modellgewichte berechnen"
        "AUDIT" -> "Kandidat und Holdouts prüfen"
        "SAVE_RESULT" -> "Prüfentscheidung dauerhaft sichern"
        "DONE" -> "Lauf beendet"
        else -> "Keine laufende Phase bestätigt"
    }

    private fun outcomeName(outcome: String): String = when (outcome) {
        "ACCEPTED" -> "Kandidat übernommen"
        "REJECTED" -> "Kandidat verworfen; alte Gewichte geschützt"
        "NO_CHANGE" -> "Keine neuen Gewichte übernommen"
        "DEFERRED" -> "Auftrag verschoben"
        "INTERRUPTED" -> "Auftrag unterbrochen"
        "FAILED" -> "Auftrag fehlgeschlagen"
        "FINISHED" -> "Auftrag abgeschlossen; kein Lerngewinn behauptet"
        else -> "Noch kein Monitor-Ergebnis (ältere Läufe bleiben im Lernkern)"
    }

    private fun refresh() {
        val research = ContinuumTrainingMonitor.read(this, ContinuumTrainingMonitor.PUBLIC)
        val offline = ContinuumTrainingMonitor.read(this, ContinuumTrainingMonitor.OFFLINE)
        val cdx = ContinuumTrainingMonitor.read(this, ContinuumTrainingMonitor.CDX)
        val training = listOf(research, offline, cdx).firstOrNull { it.trainingNow }
        val work = listOf(research, offline, cdx).firstOrNull { it.researchingNow }
        val running = training ?: work
        val live = running != null
        val last = listOf(research, offline, cdx).maxByOrNull { it.endedAt }
        val justCompleted = !live && last != null && last.endedAt > 0L &&
            System.currentTimeMillis() - last.endedAt in 0L..60_000L
        headline.text = when {
            training != null -> "TRAINIERT GERADE"
            work != null -> "FORSCHT / PRÜFT GERADE"
            justCompleted -> "TRAINING ABGESCHLOSSEN"
            else -> "WARTET AUF NÄCHSTEN ZYKLUS"
        }
        subline.text = when {
            live -> (running!!.subject.ifBlank { "Continuum-Lernkern" }) +
                " · " + phaseName(running.phase)
            justCompleted -> outcomeName(last!!.outcome)
            else -> "Ruhiger Standby · letzter Lernstand bleibt gespeichert"
        }
        orb.mode = when {
            training != null -> 2
            work != null -> 1
            listOf(research, offline, cdx).any { it.stale } -> 3
            justCompleted -> 4
            else -> 0
        }
        val scheduler = ContinuumPublicStudyScheduler(this)
        val jobs = getSystemService(JobScheduler::class.java)
        val registered = runCatching {
            jobs?.getPendingJob(ContinuumPublicStudyScheduler.JOB_ID) != null
        }.getOrDefault(false)
        val offlineRegistered = runCatching {
            jobs?.getPendingJob(ContinuumAutonomousScheduler.JOB_ID) != null
        }.getOrDefault(false)
        val publicNext = if (research.startedAt > 0L)
            stamp(research.startedAt + 15L * 60_000L) + " (ungefähr, Android entscheidet)"
        else "Android entscheidet nach Einplanung"
        publicCard.text = stateLine(ContinuumTrainingMonitor.PUBLIC, "◉  ÖFFENTLICHE FORSCHUNG / SWB1") +
            "\nPlanung: " + (if (scheduler.enabled && registered) "FREIGEGEBEN · REGISTRIERT"
            else if (!scheduler.enabled) "AUS" else "JOB NICHT REGISTRIERT") +
            "\nNächstes Planungsfenster: " + publicNext
        offlineCard.text = stateLine(ContinuumTrainingMonitor.OFFLINE, "◎  OFFLINE-LERNJOB / SWB1") +
            "\nAndroid-Planung: " + (if (offlineRegistered) "REGISTRIERT · 24-Stunden-Zyklus"
            else "NICHT REGISTRIERT") +
            "\n" + at.heinz.deviceai.learning.ContinuumOfflineJobStatus.read(this).display()
        cdxCard.text = stateLine(ContinuumTrainingMonitor.CDX, "◇  CDX1 / PAARARCHIV") +
            "\nCDX1-Großarchiv startet derzeit nur nach deiner Freigabe."
        val p = getSharedPreferences("continuum-public-study-v1", Context.MODE_PRIVATE)
        evidenceCard.text = "LIVE-EVIDENZ / LETZTE ABSCHLÜSSE\n" +
            "Forschungsversuche: " + p.getLong("attempts", 0L) +
            " · neue Quellen: " + p.getLong("sources", 0L) +
            " · akzeptierte SWB1-Quellenrunden: " + p.getLong("accepted", 0L) +
            "\nForschungsstatus: " + p.getString("phase", "NOT_RUN").orEmpty() +
            "\nMonitor aktualisiert jede Sekunde, während diese Ansicht geöffnet ist."
    }
}

/** Lightweight on-device holographic orb. State is determined by real telemetry. */
private class TrainingOrbView(context: Context) : View(context) {
    var mode: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val started = android.os.SystemClock.uptimeMillis()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * 0.29f
        val t = ((android.os.SystemClock.uptimeMillis() - started) / 1000f)
        val accent = when (mode) {
            2 -> Color.rgb(48, 235, 245)
            1 -> Color.rgb(81, 167, 255)
            3 -> Color.rgb(255, 182, 81)
            4 -> Color.rgb(79, 232, 170)
            else -> Color.rgb(143, 122, 246)
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawCircle(cx, cy, radius * (1.04f + 0.03f * sin(t)), paint)
        paint.color = Color.rgb(12, 30, 62)
        canvas.drawCircle(cx, cy, radius * 0.79f, paint)
        for (layer in 0..2) {
            val r = radius * (0.96f + layer * 0.17f)
            bounds.set(cx-r, cy-r, cx+r, cy+r)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * if (layer == 0) 4f else 1.8f
            paint.color = Color.argb(80 + layer * 34, Color.red(accent),
                Color.green(accent), Color.blue(accent))
            val rotation = if (mode == 2 || mode == 1) t * (20f + layer * 9f)
                else 4f * sin(t * 0.7f + layer)
            canvas.drawArc(bounds, rotation + layer * 65f, 105f + layer * 20f,
                false, paint)
        }
        paint.style = Paint.Style.FILL
        for (i in 0..13) {
            val angle = (i * 0.4488f) + if (mode == 2) t * 0.3f else t * 0.075f
            val orbit = radius * (1.10f + (i % 4) * 0.09f)
            paint.color = Color.argb(90 + i % 4 * 35,
                Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(cx + cos(angle) * orbit, cy + sin(angle) * orbit,
                resources.displayMetrics.density * (1.5f + i % 3), paint)
        }
        paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = resources.displayMetrics.scaledDensity * 16f
        canvas.drawText(if (mode == 2) "LEARNING" else if (mode == 1) "RESEARCH"
            else if (mode == 3) "CHECK" else "STANDBY",
            cx, cy + paint.textSize * 0.34f, paint)
        if (isShown) postInvalidateDelayed(if (mode == 2) 32L else 70L)
    }
}
