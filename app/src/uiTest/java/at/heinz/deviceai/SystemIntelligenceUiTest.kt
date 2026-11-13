package at.heinz.deviceai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import at.heinz.deviceai.systemintelligence.SystemDiagnosis
import at.heinz.deviceai.systemintelligence.SystemIntelligenceCommands
import at.heinz.deviceai.systemintelligence.ThermalPhaseRecorder
import at.heinz.deviceai.systemintelligence.AndroidSystemDiagnosis
import at.heinz.deviceai.ui.SystemDiagnosisHologramView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowChoreographer
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SystemIntelligenceUiTest {
    @Test fun cloudEnvelopeContainsNoAutomaticDeviceMetadata() {
        val payload = cloudConversationPayload("fixture", "Hallo", emptyList())
        assertEquals(setOf("session_id", "message"), payload.keys().asSequence().toSet())
        assertFalse(payload.has("device"))
        assertFalse(payload.toString().contains("shizuku_ready"))
    }
    @Test fun pluggedButNotChargingRemainsFalse() {
        assertEquals(false, AndroidSystemDiagnosis.chargingStatus(android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING))
        assertEquals(false, AndroidSystemDiagnosis.chargingStatus(android.os.BatteryManager.BATTERY_STATUS_FULL))
        assertEquals(true, AndroidSystemDiagnosis.chargingStatus(android.os.BatteryManager.BATTERY_STATUS_CHARGING))
        assertNull(AndroidSystemDiagnosis.chargingStatus(android.os.BatteryManager.BATTERY_STATUS_UNKNOWN))
    }
    @Test fun nativeDeviceInformationDoesNotRequireShizuku() {
        val app = RuntimeEnvironment.getApplication()
        var bridgeCalls = 0
        val executor = DeviceToolExecutor(app, { false }, { _, _ -> bridgeCalls++ }, null)
        var output: Result<String>? = null
        executor.execute(ToolCallRequest("device", "get_device_info", emptyMap())) { output = it }
        assertTrue(output!!.isSuccess)
        assertTrue(output!!.getOrThrow().contains("SDK: 28"))
        assertEquals(0, bridgeCalls)
    }
    @Test fun diagnosisActuallyReachesNormalAndroidProbe() {
        val app = RuntimeEnvironment.getApplication()
        var bridgeCalls = 0
        val executor = DeviceToolExecutor(app, { false }, { _, _ -> bridgeCalls++ }, null)
        var output: Result<String>? = null
        executor.execute(ToolCallRequest("probe", SystemIntelligenceCommands.DIAGNOSE, emptyMap())) { output = it }
        assertTrue(output!!.isSuccess)
        assertNotNull(executor.lastDiagnosis)
        assertTrue(output!!.getOrThrow().contains("Kein Geschwindigkeitsgewinn belegt"))
        assertEquals(0, bridgeCalls)
    }
    @Test fun mutationsCannotDispatchWithoutBothApprovals() {
        var dispatched = 0
        val gate = ToolGate(ToolPolicy(readOnlyEnabled = true, destructiveEnabled = true), ToolRunner { dispatched++; "unexpected" })
        val call = ToolCallRequest("cleanup", SystemIntelligenceCommands.CLEANUP,
            mapOf("previewId" to "preview", "planDigest" to "digest"))
        val result = gate.execute(ToolInvocation(call.callId, call), userConfirmed = true, destructiveConfirmed = false)
        assertEquals(ToolExecutionStatus.REJECTED, result.record.status)
        assertEquals(0, dispatched)
    }
    @Test @Config(qualifiers = "w900dp-h1120dp-port-mdpi")
    fun existingCinemaPresentsMeasuredAndUnknownStates() {
        render("system-inner", 900, 1120)
    }
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun compactCinemaPresentsMeasuredAndUnknownStates() {
        render("system-cover", 440, 956)
    }
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun naturalRequestReachesDiagnosisContextAndSharedTurn() {
        render("system-dialog", 440, 956, diagnose = true)
    }
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun shortSpokenDiagnosisUsesRealWorkSceneWithoutModelInference() {
        render("system-short", 440, 956, diagnose = true,
            spokenRequest = "Analysiere mein Handy und schlage mir Verbesserungen vor.", fromChat = true)
    }
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun cageCleanupSpeechOnlyPreviewsOwnedCache() {
        render("system-cage", 440, 956, diagnose = true,
            spokenRequest = "bereinige bitte meinen Cage und sage mir wie viel du bereinigt hast", preview = true)
    }
    /** Owner target sentence (Kapitel VI/VII): diagnosis plus own-cache preview, six groups, no change, real work scene. */
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun ownerTargetSentenceDiagnosesAndPreviewsWithoutCleanup() {
        render("system-owner-target", 440, 956, diagnose = true,
            spokenRequest = "Continuum, untersuche bitte mein Handy. Sag mir, was Speicher verbraucht, warum es heiß wird " +
                "und was wir bereinigen könnten. Aber lösch noch nichts.", fromChat = true, diagnosisWithPreview = true)
    }
    @Test @Config(qualifiers = "w440dp-h956dp-port-mdpi")
    fun manualThermalSeriesRequiresOwnerStartAndStopsOnPauseOrCancel() {
        render("system-manual-thermal", 440, 956, thermalSeries = true)
    }

    private fun render(label: String, width: Int, height: Int, diagnose: Boolean = false,
                       spokenRequest: String? = null, fromChat: Boolean = false, preview: Boolean = false,
                       diagnosisWithPreview: Boolean = false, thermalSeries: Boolean = false) {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit().putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("jarvis-shell", true).commit()
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        var pump: CockpitHostFramePump? = null
        try {
            controller.setup().visible()
            shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
            controller.windowFocusChanged(true)
            val activity = controller.get()
            val root = activity.window.decorView
            val framePump = CockpitHostFramePump { root }.also { pump = it }
            fun measure() {
                root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, width, height)
            }
            fun advance(millis: Long) {
                repeat((millis / 16).toInt()) {
                    shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                    measure(); framePump.drawRequestedFrame()
                }
            }
            fun field(name: String) = AgentActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            val executor = field("executor").get(activity) as DeviceToolExecutor
            if (thermalSeries) {
                val open = AgentActivity::class.java.getDeclaredMethod("showSystemIntelligence").apply { isAccessible = true }
                fun recorder() = field("systemThermalRecorder").get(activity) as? ThermalPhaseRecorder
                fun click(label: String) {
                    measure()
                    val action = descendants(root).filterIsInstance<android.widget.TextView>().firstOrNull {
                        it.text.toString() == label && it.isShown
                    }
                    assertNotNull("Existing diagnosis action: $label", action)
                    assertTrue(action!!.performClick())
                    advance(64)
                }
                fun diagnoseOnce() {
                    val previous = executor.lastDiagnosis
                    click("JETZT ANALYSIEREN")
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                    while (field("busy").getBoolean(activity) && System.nanoTime() < deadline) {
                        Thread.sleep(10)
                        shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                        measure(); framePump.drawRequestedFrame()
                    }
                    assertFalse("Owner diagnosis completed", field("busy").getBoolean(activity))
                    assertNotNull(executor.lastDiagnosis)
                    assertNotSame("Fresh probe, no replay", previous, executor.lastDiagnosis)
                    advance(64)
                }
                measure(); open.invoke(activity); advance(128)
                assertNull(recorder())
                diagnoseOnce()
                assertNull("Diagnosis alone never opts in", recorder())
                click("VORHER-MESSUNG MERKEN")
                click("NEUE MESSREIHE STARTEN")
                val first = recorder()!!
                assertEquals(ThermalPhaseRecorder.Phase.DIAGNOSTIC_SNAPSHOT, first.active)
                assertTrue("Previous snapshot was not copied", first.samples().isEmpty())
                diagnoseOnce()
                assertEquals(1, first.samples().size)
                assertTrue((field("systemReport").get(activity) as String).contains("Vorher/Nachher:"))
                assertTrue(descendants(root).any {
                    it.isShown && it.contentDescription?.toString() == "Messpunkte: 1/2000"
                })
                click("MESSREIHE STOPPEN")
                assertNull(first.active)
                diagnoseOnce()
                assertEquals("Explicit stop stays stopped", 1, first.samples().size)
                click("NEUE MESSREIHE STARTEN")
                val second = recorder()!!
                assertNotSame(first, second)
                assertTrue(second.samples().isEmpty())
                controller.pause()
                assertNull(second.active)
                controller.resume()
                open.invoke(activity); advance(128)
                assertSame(second, recorder())
                assertNull("Resume never restarts recording", second.active)
                diagnoseOnce()
                assertTrue(second.samples().isEmpty())
                click("NEUE MESSREIHE STARTEN")
                val third = recorder()!!
                val cancel = AgentActivity::class.java.getDeclaredMethod("cancelSystemDiagnosis").apply { isAccessible = true }
                cancel.invoke(activity)
                assertNull("Existing STOP path stops sampling", third.active)
                assertTrue(third.samples().isEmpty())
                return
            }
            if (diagnose) {
                val request = spokenRequest ?: ("Continuum, schau dir bitte an, warum mein Handy so langsam geworden ist. " +
                    "Prüf zuerst alles, sag mir, was du gefunden hast, und mach danach die Verbesserungen, die ich dir freigebe.")
                val realRuntime = field("localRuntime").get(activity) as at.heinz.deviceai.localmodel.LocalModelRuntime
                val modelCalls = java.util.concurrent.atomic.AtomicInteger()
                val noInference = object : at.heinz.deviceai.localmodel.LocalModelRuntime by realRuntime {
                    override fun stream(prompt: String, callback: at.heinz.deviceai.localmodel.LocalInferenceCallback) {
                        modelCalls.incrementAndGet(); error("Diagnosis must not infer")
                    }
                    override fun streamThinking(prompt: String, callback: at.heinz.deviceai.localmodel.LocalInferenceCallback) {
                        modelCalls.incrementAndGet(); error("Diagnosis must not infer")
                    }
                }
                field("localRuntime").set(activity, noInference)
                val storage = at.heinz.deviceai.systemintelligence.SystemStorageInspector(app)
                val retained = if (preview || diagnosisWithPreview) storage.retainDiagnosis(
                    at.heinz.deviceai.systemintelligence.DiagnosticCacheRecord(1_000, 10_000, 2_000, 73, 0)) else null
                val history = storage.diagnosisHistory()
                if (fromChat) {
                    val chat = JarvisHomeTestRoutes.destination(root, "chat")
                    assertNotNull(chat)
                    JarvisHomeTestRoutes.touch(root, chat)
                }
                val input = field("input").get(activity) as android.widget.EditText
                input.setText(request)
                (field("sendButton").get(activity) as android.widget.Button).performClick()
                var sawWorkScene = false
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while ((field("botLookupPending").getBoolean(activity) || field("busy").getBoolean(activity)) &&
                    System.nanoTime() < deadline) {
                    Thread.sleep(10); shadowOf(Looper.getMainLooper()).idleFor(16, TimeUnit.MILLISECONDS)
                    measure(); framePump.drawRequestedFrame()
                    sawWorkScene = sawWorkScene || descendants(root).any { it.tag == "jarvis-work-window" && it.isShown }
                }
                assertFalse("Address lookup completed", field("botLookupPending").getBoolean(activity))
                assertFalse("Diagnosis completed", field("busy").getBoolean(activity))
                val turn = field("activeChiefTurn").get(activity) as? at.heinz.deviceai.chief.TurnLifecycle
                assertNotNull("Natural request reached the shared turn", turn)
                if (preview) {
                    assertNull(executor.lastDiagnosis)
                    assertNotNull(executor.lastCachePreview)
                    assertTrue(executor.lastCachePreview!!.entries.any { it.id == retained })
                    assertEquals("Preview did not remove a report", history, storage.diagnosisHistory())
                } else {
                    assertNotNull(executor.lastDiagnosis)
                    if (fromChat) assertTrue("Actual diagnosis entered existing work scene", sawWorkScene)
                    if (diagnosisWithPreview) {
                        assertNotNull("Read-only own-cache preview was made", executor.lastCachePreview)
                        assertTrue(executor.lastCachePreview!!.entries.any { it.id == retained })
                        assertEquals("Preview did not remove a report", history, storage.diagnosisHistory())
                        val texts = descendants(root).filterIsInstance<android.widget.TextView>().map { it.text.toString() }
                        listOf("1 · Messwerte", "3 · Hypothesen", "4 · Bereinigungskandidaten", "Bericht $retained",
                            "5 · Erst nach Freigabe", "Keine Änderung durchgeführt", "Fremde App-Caches").forEach { part ->
                            assertTrue(part, texts.any { it.contains(part) })
                        }
                    } else assertNull(executor.lastCachePreview)
                }
                assertNull(executor.lastCleanupResult)
                assertEquals("Direct local route never infers", 0, modelCalls.get())
                assertEquals(at.heinz.deviceai.chief.TurnPhase.ENDED, turn!!.phase)
                val context = field("localConversationContext").get(activity) as at.heinz.deviceai.cognitive.LocalConversationContext
                val prompt = context.render(field("sessionId").get(activity) as String)
                assertTrue(prompt, prompt.contains(if (preview) "preview_own_cache" else "diagnose_device"))
                assertTrue(prompt, prompt.contains(if (preview) "Noch nichts verändert" else "Messzeit"))
                assertTrue(descendants(root).filterIsInstance<android.widget.TextView>().any {
                    it.text.contains(if (preview) "Bereinigungsvorschau" else "Lokale Systemdiagnose") })
                return
            }
            val avatar = field("hologramView").get(activity) as ContinuumHologramView
            avatar.setAnimationsEnabled(true)
            avatar.setForegroundActive(true)
            val open = AgentActivity::class.java.getDeclaredMethod("showSystemIntelligence").apply { isAccessible = true }
            field("systemReport").set(activity, "TESTDATEN · keine Fold7-Messung. Noch keine Diagnose.")
            measure(); advance(800); open.invoke(activity); advance(1200)
            assertTrue(descendants(root).any { it.tag == "system-diagnosis-hologram" && it.isShown })
            capture(root, width, height, "$label-unknown")
            val sample = SystemDiagnosis(1_000, 37, 256L shl 30, 41L shl 30,
                12L shl 30, 3L shl 30, false, 460L shl 20, 73, 37.2, false, 2, 84.2, 250)
            DeviceToolExecutor::class.java.getDeclaredField("lastDiagnosis").apply { isAccessible = true }.set(executor, sample)
            field("systemReport").set(activity, "TESTDATEN · keine Fold7-Messung.\n" + sample.report())
            open.invoke(activity); advance(1200)
            capture(root, width, height, "$label-diagnosis")
            val view = descendants(root).filterIsInstance<SystemDiagnosisHologramView>().first { it.isShown }
            assertTrue(view.contentDescription.contains("37.2"))
            val direct = SystemDiagnosisHologramView(activity, sample)
            direct.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY))
            direct.layout(0, 0, 720, 560)
            capture(direct, 720, 560, "$label-data-surface")
            val gateMethod = AgentActivity::class.java.declaredMethods.single { it.name == "createToolGate" }
            gateMethod.isAccessible = true
            val gate = gateMethod.invoke(activity, "Analysiere", null, { false }, null, null) as ToolGate
            val unauthorized = ToolCallRequest("unbound", SystemIntelligenceCommands.DIAGNOSE, emptyMap())
            val result = gate.execute(ToolInvocation(unauthorized.callId, unauthorized))
            assertEquals(ToolExecutionStatus.FAILED, result.record.status)
            assertSame(sample, executor.lastDiagnosis)
            val confirm = AgentActivity::class.java.getDeclaredMethod("confirmSystemChange",
                ToolCallRequest::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            for (name in listOf(SystemIntelligenceCommands.SAVE, SystemIntelligenceCommands.CLEANUP, SystemIntelligenceCommands.RESTORE)) {
                val pending = ToolCallRequest("stale-$name", name, emptyMap())
                confirm.invoke(activity, pending, "Stale approval fixture", name == SystemIntelligenceCommands.CLEANUP)
                val dialog = field("systemConfirmationDialog").get(activity) as android.app.AlertDialog
                assertTrue(dialog.isShowing)
                controller.pause(); controller.resume()
                assertFalse(dialog.isShowing)
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
                assertNull(field("activeSystemGate").get(activity))
                assertFalse(field("busy").getBoolean(activity))
            }
        } finally { pump?.close(); runCatching { controller.close() } }
    }
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun capture(view: View, width: Int, height: Int, name: String) {
        val directory = System.getProperty("continuum.ui.evidenceDir") ?: return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            File(directory).mkdirs()
            val output = File(directory, "$name.png")
            output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            File(directory, "$name.json").writeText(org.json.JSONObject()
                .put("source_commit", System.getenv("CONTINUUM_CAPTURE_SOURCE_COMMIT"))
                .put("renderer", "Robolectric API28 native Canvas; original Cinema; existing 2D avatar fallback")
                .put("inputs", "explicit synthetic numeric fixture, NOT device data")
                .put("Fold7", "NOT_RUN").put("Filament", "NOT_RUN").toString(2))
        } finally { bitmap.recycle() }
    }
}
