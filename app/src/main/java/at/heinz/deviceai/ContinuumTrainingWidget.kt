package at.heinz.deviceai

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import at.heinz.deviceai.learning.ContinuumAutonomousScheduler
import at.heinz.deviceai.learning.ContinuumPublicStudyScheduler
import at.heinz.deviceai.learning.ContinuumTrainingMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android launcher widget, not a foreground Activity and not a polling service.
 * All indicators originate from actual persisted learning job events.
 * Launcher delivery may be delayed by Android and is never presented as a live stream.
 */
class ContinuumTrainingWidget : AppWidgetProvider() {
    companion object {
        private const val REFRESH = "at.heinz.deviceai.action.REFRESH_CONTINUUM_WIDGET"
        private const val NOTIFY_CHANNEL = "continuum_training_errors_v1"
        private const val ERROR_STORE = "continuum-widget-error-alerts-v1"

        fun requestUpdate(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val component = ComponentName(app, ContinuumTrainingWidget::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return
            app.sendBroadcast(Intent(app, ContinuumTrainingWidget::class.java).apply {
                action = REFRESH
            })
        }

        /** An error notification is sent only once for a recorded FAILED cycle. */
        fun notifyFailure(context: Context, channel: String, startedAt: Long, detail: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            val prefs = context.getSharedPreferences(ERROR_STORE, Context.MODE_PRIVATE)
            val identifier = (channel + ":" + startedAt).take(90)
            if (prefs.getString("last", "") == identifier) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(NOTIFY_CHANNEL,
                "Continuum · Trainingsfehler", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 931,
                Intent(context, ContinuumTrainingMonitorActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = android.app.Notification.Builder(context, NOTIFY_CHANNEL)
                .setSmallIcon(R.drawable.ic_device_ai)
                .setContentTitle("Continuum: echter Trainingsfehler")
                .setContentText((channel.uppercase(Locale.GERMANY) + " · " + detail).take(110))
                .setStyle(android.app.Notification.BigTextStyle().bigText(
                    "Trainingslauf fehlgeschlagen: " + channel + ". " +
                        detail.take(150) + ". Details und sichere Planung im Monitor prüfen."))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(931, notification)
            prefs.edit().putString("last", identifier).apply()
        }

        private fun clock(value: Long): String = if (value <= 0L) "–" else
            SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(value))

        private fun channelLine(snapshot: ContinuumTrainingMonitor.Snapshot, name: String,
                                registered: Boolean): String {
            val value = when {
                snapshot.trainingNow -> "TRAIN"
                snapshot.researchingNow -> "AKTIV"
                snapshot.stale -> "UNKLAR"
                snapshot.outcome == "FAILED" -> "FEHLER"
                !registered -> "AUS"
                else -> "WARTET"
            }
            return name + " " + value
        }

        private fun update(context: Context, widgetIds: IntArray) {
            if (widgetIds.isEmpty()) return
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val public = ContinuumTrainingMonitor.read(app, ContinuumTrainingMonitor.PUBLIC)
            val offline = ContinuumTrainingMonitor.read(app, ContinuumTrainingMonitor.OFFLINE)
            val cdx = ContinuumTrainingMonitor.read(app, ContinuumTrainingMonitor.CDX)
            val snapshots = listOf(public, offline, cdx)
            val jobs = app.getSystemService(JobScheduler::class.java)
            val hasPublic = runCatching {
                ContinuumPublicStudyScheduler(app).enabled &&
                    jobs.getPendingJob(ContinuumPublicStudyScheduler.JOB_ID) != null
            }.getOrDefault(false)
            val hasOffline = runCatching {
                jobs.getPendingJob(ContinuumAutonomousScheduler.JOB_ID) != null
            }.getOrDefault(false)

            val training = snapshots.firstOrNull { it.trainingNow }
            val working = snapshots.firstOrNull { it.researchingNow }
            val last = snapshots.maxByOrNull { it.endedAt }
            val recent = last != null && last.endedAt > 0L &&
                System.currentTimeMillis() - last.endedAt in 0L..90_000L
            val failed = last != null && last.endedAt > 0L &&
                last.outcome == "FAILED" && training == null && working == null
            val selected = training ?: working
            val label = when {
                training != null -> "TRAINIERT GERADE"
                working != null -> "FORSCHT / PRÜFT"
                failed -> "FEHLER ERKANNT"
                snapshots.any { it.stale } -> "STATUS UNGEKLÄRT"
                recent -> "LAUF BEENDET"
                hasPublic || hasOffline -> "WARTET AUF ZYKLUS"
                else -> "KEIN JOB REGISTRIERT"
            }
            val subject = when {
                selected != null -> selected.subject.ifBlank { selected.model }.ifBlank {
                    "Aktueller Lernauftrag"
                }
                failed -> last!!.detail.ifBlank { "Trainingslauf fehlgeschlagen" }
                recent -> when (last!!.outcome) {
                    "ACCEPTED" -> "Gewichte übernommen · Qualitätsgate bestanden"
                    "REJECTED" -> "Gewichte nicht übernommen · Qualitätsgate"
                    "DEFERRED" -> "Lauf verschoben · Bedingungen prüfen"
                    "FINISHED" -> "Lauf abgeschlossen · Qualität separat prüfen"
                    else -> "Letzter Lauf: " + last.outcome.take(30)
                }
                else -> if (hasPublic) "Nächster Forschungszyklus durch Android"
                    else if (hasOffline) "Offline-Zyklus durch Android"
                    else "Lernplanung in der App prüfen"
            }
            val phase = when {
                selected?.trainingNow == true -> "ECHTE GEWICHTSBERECHNUNG · " + selected.model
                selected != null -> selected.phase.replace('_', ' ') + " · " + selected.model
                failed -> "Fehlerkanal: " + last!!.channel
                snapshots.any { it.stale } -> "Kein aktuelles Lebenszeichen – Details prüfen"
                else -> "Kein aktives Trainingssignal"
            }
            val lastFinished = if (last == null || last.endedAt <= 0L) "kein Abschluss erfasst"
                else clock(last.endedAt) + " · " + when (last.outcome) {
                    "ACCEPTED" -> "übernommen"
                    "REJECTED" -> "verworfen"
                    "FAILED" -> "fehlgeschlagen"
                    "DEFERRED" -> "verschoben"
                    "FINISHED" -> "abgeschlossen"
                    else -> last.outcome.take(18)
                }
            val shade = when {
                training != null -> Color.rgb(76, 238, 215)
                working != null -> Color.rgb(80, 185, 253)
                failed -> Color.rgb(255, 117, 133)
                snapshots.any { it.stale } -> Color.rgb(255, 190, 89)
                recent -> Color.rgb(100, 229, 156)
                else -> Color.rgb(167, 165, 255)
            }
            val launch = PendingIntent.getActivity(app, 3101,
                Intent(app, ContinuumTrainingMonitorActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val refresh = PendingIntent.getBroadcast(app, 3102,
                Intent(app, ContinuumTrainingWidget::class.java).apply { action = REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // A one-shot, non-exact expiry prevents long-running stale live labels
            // after process death. Android may delay the refresh under Doze.
            val alarm = app.getSystemService(AlarmManager::class.java)
            val expiry = PendingIntent.getBroadcast(app, 3103,
                Intent(app, ContinuumTrainingWidget::class.java).apply { action = REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm?.cancel(expiry)
            if (selected != null) {
                alarm?.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 50_000L, expiry)
            }
            val lastUpdate = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
                .format(Date(System.currentTimeMillis()))
            for (id in widgetIds) {
                val view = RemoteViews(app.packageName, R.layout.continuum_training_widget)
                view.setTextViewText(R.id.widget_state, label)
                view.setTextColor(R.id.widget_state, shade)
                view.setTextViewText(R.id.widget_subject, subject.take(130))
                view.setTextViewText(R.id.widget_phase, phase.take(100))
                view.setTextViewText(R.id.widget_public,
                    channelLine(public, "WEB", hasPublic))
                view.setTextViewText(R.id.widget_offline,
                    channelLine(offline, "SWB1", hasOffline))
                view.setTextViewText(R.id.widget_cdx,
                    channelLine(cdx, "CDX1", true))
                val stats = app.getSharedPreferences("continuum-public-study-v1",
                    Context.MODE_PRIVATE)
                view.setTextViewText(R.id.widget_counts,
                    "QUELLEN " + stats.getLong("sources", 0L) +
                        "  ·  SWB1-RUNDEN " + stats.getLong("accepted", 0L) +
                        "  ·  VERSUCHE " + stats.getLong("attempts", 0L))
                view.setTextViewText(R.id.widget_footer, "Letzter Lauf: " + lastFinished)
                view.setTextViewText(R.id.widget_updated, lastUpdate)
                view.setOnClickPendingIntent(R.id.training_widget_root, launch)
                view.setOnClickPendingIntent(R.id.widget_refresh, refresh)
                manager.updateAppWidget(id, view)
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        update(context, widgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
            update(context, ids)
        }
    }
}
