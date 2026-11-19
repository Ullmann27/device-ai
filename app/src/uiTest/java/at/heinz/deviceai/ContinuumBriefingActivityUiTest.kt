package at.heinz.deviceai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import at.heinz.deviceai.briefing.AndroidContinuumDevice
import at.heinz.deviceai.briefing.ContinuumBriefingBoard
import at.heinz.deviceai.briefing.ContinuumBriefingDirector
import at.heinz.deviceai.briefing.ContinuumBriefingPreferences
import at.heinz.deviceai.briefing.ContinuumBriefingText
import at.heinz.deviceai.briefing.ContinuumCalendarEvent
import at.heinz.deviceai.briefing.ContinuumCalendarRead
import at.heinz.deviceai.briefing.ContinuumCalendarSource
import at.heinz.deviceai.briefing.ContinuumSystemCheck
import at.heinz.deviceai.briefing.ContinuumWeather
import at.heinz.deviceai.cognitive.ResearchHttpEnvironment
import at.heinz.deviceai.sending.FakeContacts
import at.heinz.deviceai.sending.FakeContacts.Companion.phone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.security.cert.Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections
import javax.net.ssl.HttpsURLConnection

/**
 * The briefing lane in the real AgentActivity (Robolectric): the hook in the owner input path, the answers in the
 * chat, the calendar permission flow, the dialer, the web consent with the weather job on the busy lane and the hooks
 * for the greeting chain. Calendar, HTTPS and the address book are stand-ins; the real phone is NOT part of this run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w884dp-h760dp-land-mdpi", shadows = [OwnerWorkToolsDeviceUiTest.ThermalPowerManager::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ContinuumBriefingActivityUiTest {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun internet(mode: String) =
        app.getSharedPreferences("continuum-internet", Context.MODE_PRIVATE).edit().putString("mode", mode).commit()

    @Before fun configure() {
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", false).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences(ContinuumBriefingPreferences.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        internet("OFF")
        ContinuumBriefingBoard.clear()
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun texts(view: View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun chat(activity: AgentActivity) = texts(field(activity, "chatContainer") as View)

    private fun withActivity(check: (AgentActivity) -> Unit) {
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        try {
            controller.setup().visible().windowFocusChanged(true)
            idle()
            // In the app the name is what the owner told it („Nenn mich …“); these tests give the lane a fixed one.
            controller.get().continuumOwnerName = { ContinuumBriefingDirector.DEFAULT_OWNER_NAME }
            check(controller.get())
        } finally {
            ShadowAlertDialog.getLatestAlertDialog()?.dismiss()
            runCatching { controller.pause().stop().destroy() }
            at.heinz.deviceai.cockpit.JarvisAvatarSlot.register(null)
        }
    }

    private fun settle(activity: AgentActivity) {
        repeat(400) {
            if (field(activity, "busy") == false && field(activity, "botLookupPending") == false) return
            Thread.sleep(25)
            idle()
        }
    }

    /** Types into the real input field and presses the real send button; then the busy lane has to finish. */
    private fun say(activity: AgentActivity, text: String) {
        (field(activity, "input") as EditText).setText(text)
        assertTrue((field(activity, "sendButton") as Button).performClick())
        idle()
        settle(activity)
        idle()
    }

    private class Calendar(var allowed: Boolean, val events: List<ContinuumCalendarEvent>) : ContinuumCalendarSource {
        var reads = 0
        override fun granted(): Boolean = allowed
        override fun read(fromMillis: Long, untilMillis: Long): ContinuumCalendarRead {
            reads++
            return ContinuumCalendarRead.Events(events.filter { it.startMillis < untilMillis && it.endMillis >= fromMillis })
        }
    }

    private fun tomorrowAt(hour: Int, minute: Int = 0): Long = LocalDate.now(ZoneId.systemDefault()).plusDays(1)
        .atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun theSystemCheckAnswersInTheChatFromTheRealStateOfTheApp() = withActivity { activity ->
        say(activity, "Systemcheck")
        val lines = chat(activity)
        assertTrue(lines.toString(), lines.contains("Systemcheck"))
        val checks = ContinuumSystemCheck.snapshot()
        assertEquals(listOf("brain", "speech_in", "speech_out", "internet", "files", "accessibility", "bridge", "battery", "thermal",
            "storage", "open"), checks.map { it.key })
        val answer = lines.single { it.contains("Gehirn: ") }
        assertTrue(answer, answer.startsWith(ContinuumSystemCheck.report(checks, ContinuumBriefingDirector.DEFAULT_OWNER_NAME)))
        assertTrue(answer, answer.endsWith(ContinuumSystemCheck.lines(checks)))
        // This host has no loaded model: the report must say so and must not claim that all is well.
        assertEquals(ContinuumSystemCheck.State.FAIL, checks.single { it.key == "brain" }.state)
        assertTrue(answer, answer.contains("kein lokales Modell geladen"))
        assertFalse(answer, answer.contains("Alles läuft"))
        // The owner's switches are named as switched off.
        assertEquals(ContinuumSystemCheck.State.OFF, checks.single { it.key == "internet" }.state)
        assertEquals(ContinuumSystemCheck.State.OFF, checks.single { it.key == "speech_out" }.state)
        assertEquals(false, field(activity, "busy"))
        assertNull("no dialog and no approval for a read-only check", ShadowAlertDialog.getLatestAlertDialog())
        // The same question in other words, and the existing routes around it.
        say(activity, "Wie ist dein Status?")
        assertEquals(2, chat(activity).count { it.contains("Gehirn: ") })
        assertFalse(activity.continuumBriefing.handle("Fass mir die Nachrichten von heute zusammen"))
        assertFalse(activity.continuumBriefing.handle("Systemdiagnose"))
        assertFalse(activity.continuumBriefing.handle("Wie spät ist es?"))
    }

    @Test fun theCalendarIsExplainedInPlainGermanBeforeAndroidAsksAndANoIsSaidPlainly() = withActivity { activity ->
        shadowOf(app).denyPermissions(Manifest.permission.READ_CALENDAR)
        val calendar = Calendar(allowed = false, events = listOf(
            ContinuumCalendarEvent("Zahnarzt", tomorrowAt(9, 30), tomorrowAt(10, 15), false, "Praxis Dr. Beispiel")))
        activity.continuumCalendarOverride = calendar
        say(activity, "Welche Termine habe ich morgen?")
        val explanation = requireNotNull(ShadowAlertDialog.getLatestAlertDialog())
        assertEquals("Kalender lesen", shadowOf(explanation).title.toString())
        assertEquals(ContinuumBriefingDirector.CALENDAR_EXPLANATION, shadowOf(explanation).message.toString())
        assertNull("Android is asked only after the explanation", shadowOf(activity).lastRequestedPermission)
        explanation.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        idle()
        assertTrue(chat(activity).toString(), chat(activity).contains(ContinuumBriefingText.CALENDAR_DENIED))
        assertEquals(0, calendar.reads)

        // Asked again on the next explicit question; this time he goes on and Android grants it.
        say(activity, "Welche Termine habe ich morgen?")
        requireNotNull(ShadowAlertDialog.getLatestAlertDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idle()
        val request = requireNotNull(shadowOf(activity).lastRequestedPermission)
        assertEquals(listOf(Manifest.permission.READ_CALENDAR), request.requestedPermissions.toList())
        assertEquals(AndroidContinuumDevice.CALENDAR_PERMISSION_REQUEST, request.requestCode)
        calendar.allowed = true
        activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_GRANTED))
        idle()
        settle(activity)
        idle()
        assertEquals(1, calendar.reads)
        assertTrue(chat(activity).toString(), chat(activity).contains(
            "Morgen hast du einen Termin: um 9 Uhr 30 Zahnarzt (Praxis Dr. Beispiel)."))
        assertEquals(listOf("09:30–10:15 Zahnarzt"), ContinuumBriefingBoard.cards().single { it.kind.code == "calendar" }.lines)
        assertEquals(false, field(activity, "busy"))
    }

    @Test fun rufAnnaAnOpensTheDialerAndNothingMore() = withActivity { activity ->
        activity.ownerSendContactsOverride = FakeContacts(listOf(
            phone(1, "Thomas Muster", "+43 000 555 0103"), phone(2, "Anna Beispiel", "+43 000 555 0101"),
            phone(3, "Anna Muster", "+43 000 555 0102")))
        say(activity, "Ruf Thomas an")
        val started = requireNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("+430005550103", started.data!!.schemeSpecificPart)
        assertTrue(chat(activity).toString(), chat(activity).contains(
            "Die Wähltastatur ist offen, mit der Nummer von Thomas Muster. Auf Anrufen tippst du selbst – ich wähle nicht."))
        // Two „Anna“: a question, and the answer decides – through the real input path.
        say(activity, "Ruf Anna an")
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(chat(activity).toString(), chat(activity).contains(
            "Zu „Anna“ gibt es mehrere passende Nummern: Anna Beispiel, Anna Muster. Welche meinst du?"))
        say(activity, "die zweite")
        assertEquals("+430005550102", requireNotNull(shadowOf(activity).nextStartedActivity).data!!.schemeSpecificPart)
        // The send lane keeps its own orders.
        assertFalse(activity.continuumBriefing.handle("Schreib Anna auf WhatsApp, ich komme später"))
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    private class Response(url: String, val body: String) : HttpsURLConnection(URL(url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode(): Int = 200
        override fun getContentType() = "application/json; charset=utf-8"
        override fun getContentLengthLong() = -1L
        override fun getHeaderField(name: String): String? = null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getCipherSuite() = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }

    private class OpenMeteo(val bodies: (String) -> String) : ResearchHttpEnvironment {
        val opened: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override fun resolve(host: String): List<InetAddress> = listOf(InetAddress.getByName("8.8.8.8"))
        override fun open(uri: URI, addresses: List<InetAddress>, timeoutMillis: Int): HttpsURLConnection {
            opened += uri.toString()
            return Response(uri.toString(), bodies(uri.host))
        }
    }

    private fun recorded(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/briefing/$name")) { name }.use { String(it.readBytes(), Charsets.UTF_8) }

    @Test fun theBriefingReadsCalendarAndWeatherThroughTheOwnersConsentAndSaysOnlyThat() = withActivity { activity ->
        val service = OpenMeteo { host ->
            recorded(if (host == ContinuumWeather.GEOCODING_HOST) "open-meteo-geocoding-linz.json" else "open-meteo-forecast-linz.json")
        }
        activity.continuumWeatherNetworkOverride = service
        val calendar = Calendar(allowed = true, events = emptyList())
        activity.continuumCalendarOverride = calendar

        // Internet OFF: no request at all, and the briefing says that there is no weather.
        say(activity, "Mein Ort ist Linz")
        assertTrue(chat(activity).toString(), chat(activity).any { it.startsWith("Gut, Linz ist jetzt dein Ort für das Wetter.") })
        say(activity, "Was steht heute an?")
        val offline = chat(activity).last { it.contains("Heute steht nichts im Kalender.") }
        assertTrue(offline, offline.contains("Wetter gibt es keines: Der Internetzugang ist ausgeschaltet."))
        assertTrue(offline, offline.contains("Wetter\n· nicht abgerufen · Internetzugang aus"))
        assertTrue("nothing was sent", service.opened.isEmpty())
        assertEquals(1, calendar.reads)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())

        // Standing consent: the place is looked up once, then the forecast is read – only on the two weather hosts.
        internet("ALWAYS_ON")
        say(activity, "Gib mir mein Briefing")
        val online = chat(activity).last { it.contains("Heute steht nichts im Kalender.") }
        assertTrue(online, online.contains("In Linz sind es gerade 9 Grad, überwiegend klar."))
        assertTrue(online, online.contains("Wetter · Linz, Oberösterreich, Österreich"))
        assertTrue(online, online.contains("· Quelle: Open-Meteo"))
        assertEquals(listOf(ContinuumWeather.GEOCODING_HOST, ContinuumWeather.FORECAST_HOST), service.opened.map { URI(it).host })
        assertTrue(service.opened[0], service.opened[0].contains("name=Linz"))
        assertEquals(false, field(activity, "busy"))
        // The coordinates are kept: the next read needs the forecast host alone.
        say(activity, "Wie ist das Wetter?")
        assertEquals(3, service.opened.size)
        assertEquals(ContinuumWeather.FORECAST_HOST, URI(service.opened[2]).host)
        assertTrue(chat(activity).toString(), chat(activity).any { it.endsWith("Quelle: Open-Meteo · Linz, Oberösterreich, Österreich") })
    }

    @Test fun theGreetingChainGetsItsTwoHooksAndTheOfferIsMadeOncePerDay() = withActivity { activity ->
        activity.continuumCalendarOverride = Calendar(allowed = true, events = emptyList())
        // The lane never offers before the hour the owner allows; from then on once per day.
        val hour = java.time.LocalTime.now(ZoneId.systemDefault()).hour
        ContinuumBriefingPreferences(app).put("offer_hour", "0")
        val before = chat(activity).size
        val report = activity.systemStartReport()
        assertEquals(ContinuumSystemCheck.report(ContinuumSystemCheck.snapshot(), ContinuumBriefingDirector.DEFAULT_OWNER_NAME), report)
        assertTrue(report, report.contains("kein lokales Modell geladen") && !report.contains("Alles läuft"))
        assertEquals(ContinuumBriefingDirector.OFFER_TEXT, activity.briefingOffer())
        assertNull("once per day", activity.briefingOffer())
        assertEquals("the hook only returns the text; saying it is the greeting's part", before, chat(activity).size)
        assertTrue(hour in 0..23)
        // A plain yes of the owner starts the briefing.
        say(activity, "ja")
        assertTrue(chat(activity).toString(), chat(activity).any { it.contains("Heute steht nichts im Kalender.") })
        // runBriefing starts it without any owner turn.
        assertEquals(1, chat(activity).count { it.contains("Heute steht nichts im Kalender.") })
        activity.runBriefing()
        idle()
        settle(activity)
        idle()
        assertEquals("one more briefing", 2, chat(activity).count { it.contains("Heute steht nichts im Kalender.") })
        assertFalse("and no owner turn was invented for it", chat(activity).contains(ContinuumBriefingDirector.BRIEFING_LABEL))
        // A tap on an offer that is not open does nothing.
        assertFalse(activity.chooseContinuumOffer("news"))
        assertNotNull(ContinuumBriefingBoard.cards().singleOrNull { it.kind.code == "calendar" })
    }
}
