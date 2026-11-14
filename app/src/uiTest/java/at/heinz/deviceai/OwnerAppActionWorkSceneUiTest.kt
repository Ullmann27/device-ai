package at.heinz.deviceai

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.cognitive.LocalDialogueRetention
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real Activity; controlled device boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w884dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class OwnerAppActionWorkSceneUiTest {
    private class Port(val packageName: String) : DeviceAppActionPort {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val calls = AtomicInteger()
        var failure: String? = null
        override fun execute(call: ToolCallRequest, callback: (Result<String>) -> Unit) {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(60, TimeUnit.SECONDS))
            callback(failure?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(
                """{"schema":"CONTINUUM_DEVICE_ACTION_V1","action":"LAUNCH_APP","package":"$packageName","status":"VERIFIED_FOREGROUND","windowRevision":7}"""))
            completed.countDown()
        }
        override fun cancelPending() { release.countDown() }
    }

    private var pump: CockpitHostFramePump? = null
    @Before fun pauseVsync() {
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16))
    }
    private fun get(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(owner, value)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 16L)
            shadowOf(Looper.getMainLooper()).idleFor(step, TimeUnit.MILLISECONDS)
            pump?.drawRequestedFrame()
            left -= step
        }
    }
    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(884, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(760, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 884, 760)
    }
    private fun elapse(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(ms, TimeUnit.MILLISECONDS)
        pump?.drawRequestedFrame()
    }
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < end) { advance(16); Thread.sleep(5) }
        assertTrue("Expected real callback", condition())
    }
    private fun withActivity(appPackage: String = "org.example.signal", label: String = "Signal",
                             body: (AgentActivity, Port, ActivityController<AgentActivity>) -> Unit) {
        val app = RuntimeEnvironment.getApplication()
        LocalDialogueRetention.context.clear()
        app.getSharedPreferences("device_ai_bootstrap_v1", Context.MODE_PRIVATE).edit()
            .putBoolean("voice_enabled", false).commit()
        app.getSharedPreferences("continuum-readiness", Context.MODE_PRIVATE).edit()
            .putBoolean("introduced", true).putBoolean("camera-question-shown-v1", true).commit()
        app.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE).edit()
            .putBoolean("native-head", true).putBoolean("native-head-3d", false).commit()
        app.getSharedPreferences("continuum-cockpit-presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val info = ApplicationInfo().apply { packageName = appPackage; nonLocalizedLabel = label }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = info.packageName; applicationInfo = info
        })
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = info.packageName; name = "Main"; applicationInfo = info }
                nonLocalizedLabel = label
            })
        val controller = Robolectric.buildActivity(AgentActivity::class.java)
        val port = Port(appPackage)
        try {
            controller.setup().visible()
            val a = controller.get()
            val root = a.window.decorView
            pump = CockpitHostFramePump { root }
            measure(root); advance(32)
            controller.windowFocusChanged(true)
            set(a, "executor", DeviceToolExecutor(a, { false }, { _, callback ->
                callback(Result.failure(IllegalStateException("No bridge in this fixture")))
            }, port))
            (get(a, "hologramView") as ContinuumHologramView).setAnimationsEnabled(true)
            measure(root); advance(80); measure(root)
            body(a, port, controller)
        } finally {
            port.release.countDown()
            ShadowAlertDialog.getLatestAlertDialog()?.takeIf { it.isShowing }?.dismiss()
            pump?.close(); pump = null
            runCatching { controller.close() }
            LocalDialogueRetention.context.clear()
        }
    }
    private fun lookupGeneration(a: AgentActivity): Long =
        (get(a, "appLookupGeneration") as Number).toLong()

    private fun pendingAppName(a: AgentActivity): Any? {
        val question = requireNotNull(get(a, "appNameClarification"))
        return question.javaClass.getDeclaredField("pending").apply { isAccessible = true }.get(question)
    }

    private fun submit(a: AgentActivity, request: String, chat: Boolean = true) {
        if (chat) {
            val target = JarvisHomeTestRoutes.destination(a.window.decorView, "chat")
            assertNotNull(target)
            JarvisHomeTestRoutes.touch(a.window.decorView, target)
        }
        (get(a, "input") as EditText).setText(request)
        assertTrue((get(a, "sendButton") as Button).performClick())
    }
    private fun approve(port: Port): AlertDialog {
        await { ShadowAlertDialog.getLatestAlertDialog()?.let {
            it.isShowing && shadowOf(it).message.toString().contains(port.packageName)
        } == true }
        return ShadowAlertDialog.getLatestAlertDialog().also {
            it.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            await { port.entered.count == 0L }
        }
    }
    private fun pose(a: AgentActivity) = (get(get(a, "hologramView")!!, "avatarPose") as AvatarPoseReducer)
        .pose(android.os.SystemClock.elapsedRealtime())

    @Test fun missingComputerSearchTermReachesExistingApprovalOnce() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, _ ->
            val before = lookupGeneration(a)
            val request = "Suche in WhatsApp"
            submit(a, request, chat = false)
            assertEquals(before, lookupGeneration(a))
            assertEquals(0, port.calls.get())
            val context = get(a, "localConversationContext") as at.heinz.deviceai.cognitive.LocalConversationContext
            assertEquals(1, context.priorUserTurns(get(a, "sessionId") as String).count { it == request })
            submit(a, "Elena Müller", chat = false)
            await { lookupGeneration(a) > before && ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            val approval = ShadowAlertDialog.getLatestAlertDialog()
            val message = shadowOf(approval).message.toString()
            assertTrue(message, message.contains("com.whatsapp"))
            assertTrue(message, message.contains("nach „Elena Müller“ suchen"))
            assertEquals("A search term grants no launch", 0, port.calls.get())
            approval.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            await { get(a, "busy") == false }
            val after = lookupGeneration(a)
            submit(a, "Elena Müller", chat = false)
            await { get(a, "busy") == false && get(a, "botLookupPending") == false }
            assertEquals("Consumed reply cannot replay", after, lookupGeneration(a))
            assertEquals(0, port.calls.get())
        }

    @Test fun newComputerCommandReplacesPendingSearchWithoutReinterpretation() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, _ ->
            submit(a, "Suche in WhatsApp", chat = false)
            submit(a, "Öffne WhatsApp", chat = false)
            await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
            val approval = ShadowAlertDialog.getLatestAlertDialog()
            val message = shadowOf(approval).message.toString()
            assertTrue(message, message.contains("App öffnen und das echte App-Fenster anzeigen."))
            assertFalse(message, message.contains("nach „Öffne"))
            assertEquals(0, port.calls.get())
            approval.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            await { get(a, "busy") == false }
        }

    @Test fun stopAndNegationInvalidateComputerSearchQuestion() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, _ ->
            for (stop in listOf(true, false)) {
                submit(a, "Suche in WhatsApp", chat = false)
                if (stop) AgentActivity::class.java.getDeclaredMethod("cancelLocalRequest")
                    .apply { isAccessible = true }.invoke(a)
                else submit(a, "Nicht Elena", chat = false)
                await { get(a, "busy") == false && get(a, "botLookupPending") == false }
                val after = lookupGeneration(a)
                submit(a, "Elena", chat = false)
                await { get(a, "busy") == false && get(a, "botLookupPending") == false }
                assertEquals(after, lookupGeneration(a))
                assertNull(get(a, "activeOwnerAppFlow"))
                assertEquals(0, port.calls.get())
                assertFalse(ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
            }
        }

    @Test
    @Config(sdk = [35], shadows = [OwnerAppPlanningContextUiTest.ProjectionConsentShadow::class])
    fun approvedSplitScreenLaunchStartsComputerWithoutRefocusingCockpit() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, lifecycle ->
            shadowOf(a).setInMultiWindowMode(true)
            submit(a, "Öffne WhatsApp.", chat = false)
            assertEquals(0, port.calls.get())
            approve(port)
            lifecycle.pause()
            assertEquals(false, get(a, "foregroundLearningActive"))
            assertEquals(true, get(a, "computerViewVisible"))
            port.release.countDown()
            await { get(a, "activeOwnerAppFlow") == null }

            assertNull("Visible cockpit needs no resume", get(a, "pendingComputerLaunch"))
            val computer = get(a, "computerController") as? OwnerComputerSessionController
            assertNotNull("Approved launch reaches controller", computer)
            assertTrue(requireNotNull(computer).active)
            assertTrue(computer.awaitingConsent)
            assertEquals("fixture.COMPUTER_CAPTURE_CONSENT",
                shadowOf(a).nextStartedActivityForResult?.intent?.action)
            assertEquals("Only approved launch dispatched", 1, port.calls.get())

            (get(a, "input") as EditText).setText("Stopp")
            assertTrue((get(a, "sendButton") as Button).performClick())
            assertFalse(computer.active)
            assertFalse(computer.awaitingConsent)
        }

    @Test
    @Config(sdk = [35], shadows = [OwnerAppPlanningContextUiTest.ProjectionConsentShadow::class])
    fun approvedLaunchDoesNotStartComputerAfterCockpitStops() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, lifecycle ->
            shadowOf(a).setInMultiWindowMode(true)
            submit(a, "Öffne WhatsApp.", chat = false)
            approve(port)
            lifecycle.pause().stop()
            assertEquals(false, get(a, "computerViewVisible"))
            port.release.countDown()
            await { get(a, "activeOwnerAppFlow") == null }

            assertNotNull("Hidden launch waits for return", get(a, "pendingComputerLaunch"))
            assertNull(get(a, "computerController"))
            assertNull(shadowOf(a).nextStartedActivityForResult)
        }

    @Test
    @Config(sdk = [35], shadows = [OwnerAppPlanningContextUiTest.ProjectionConsentShadow::class])
    fun approvedFullscreenLaunchWaitsForCockpitReturn() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, lifecycle ->
            submit(a, "Öffne WhatsApp.", chat = false)
            approve(port)
            lifecycle.pause()
            assertEquals(false, get(a, "computerViewVisible"))
            port.release.countDown()
            await { get(a, "activeOwnerAppFlow") == null }

            assertNotNull(get(a, "pendingComputerLaunch"))
            assertNull(get(a, "computerController"))
            assertNull(shadowOf(a).nextStartedActivityForResult)
        }

    @Test
    @Config(sdk = [35], shadows = [OwnerAppPlanningContextUiTest.ProjectionConsentShadow::class])
    fun ownerStopDuringSplitScreenLaunchRejectsLateCallback() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, lifecycle ->
            shadowOf(a).setInMultiWindowMode(true)
            submit(a, "Öffne WhatsApp.", chat = false)
            approve(port)
            lifecycle.pause()
            (get(a, "input") as EditText).setText("Stopp")
            assertTrue((get(a, "sendButton") as Button).performClick())
            port.release.countDown()
            await { port.completed.count == 0L }
            advance(32)

            assertNull(get(a, "activeOwnerAppFlow"))
            assertNull(get(a, "pendingComputerLaunch"))
            assertNull(get(a, "computerController"))
            assertNull(shadowOf(a).nextStartedActivityForResult)
            assertEquals(false, get(a, "busy"))
        }

    @Test
    @Config(sdk = [35], shadows = [OwnerAppPlanningContextUiTest.ProjectionConsentShadow::class])
    fun failedSplitScreenLaunchCannotRequestComputerConsent() =
        withActivity("com.whatsapp", "WhatsApp") { a, port, lifecycle ->
            shadowOf(a).setInMultiWindowMode(true)
            port.failure = "ACCESSIBILITY_OBSERVATION_NOT_GRANTED"
            submit(a, "Öffne WhatsApp.", chat = false)
            approve(port)
            lifecycle.pause()
            port.release.countDown()
            await { get(a, "activeOwnerAppFlow") == null }

            assertNull(get(a, "pendingComputerLaunch"))
            assertNull(get(a, "computerController"))
            assertNull(shadowOf(a).nextStartedActivityForResult)
        }

    @Test fun installedVerbFinalNameUsesExistingConsentBeforeLaunch() = withActivity { a, port, _ ->
        submit(a, "Signal öffnen", chat = false)
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertEquals("Recognized name grants no launch", 0, port.calls.get())
        approve(port)
        assertEquals(1, port.calls.get())
        port.release.countDown()
        await { get(a, "busy") == false }
    }

    @Test fun missingAppNameReplyUsesLookupAndStillRequiresConsent() = withActivity { a, port, _ ->
        val before = lookupGeneration(a)
        submit(a, "Öffne eine App", chat = false)
        assertEquals("No lookup without the name", before, lookupGeneration(a))
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("Welche App meinst du genau") })
        submit(a, "Jarvis, Signal", chat = false)
        await { lookupGeneration(a) > before && ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertEquals("A name grants no launch", 0, port.calls.get())
        approve(port)
        assertEquals(1, port.calls.get())
        port.release.countDown()
        await { get(a, "busy") == false }
    }

    @Test fun anUnmatchedExplicitAppQuestionAcceptsOneCorrectedName() = withActivity { a, port, _ ->
        submit(a, "Öffne Nichtinstalliert", chat = false)
        await { get(a, "busy") == false }
        val before = lookupGeneration(a)
        assertEquals(0, port.calls.get())
        submit(a, "Signal", chat = false)
        await { lookupGeneration(a) > before && ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        assertEquals(0, port.calls.get())
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        await { get(a, "busy") == false }
        val after = lookupGeneration(a)
        submit(a, "Signal", chat = false)
        advance(100)
        assertEquals("Consumed answer cannot replay a launch", after, lookupGeneration(a))
        assertEquals(0, port.calls.get())
    }

    @Test fun stopAndIndependentNavigationInvalidateAppNameQuestions() = withActivity { a, port, _ ->
        for (cancel in listOf(true, false)) {
            submit(a, "Öffne eine App", chat = false)
            assertNotNull("App-name question is armed", pendingAppName(a))
            val before = lookupGeneration(a)
            if (cancel) AgentActivity::class.java.getDeclaredMethod("cancelLocalRequest")
                .apply { isAccessible = true }.invoke(a)
            else submit(a, "Gedächtniszentrum", chat = false)
            val afterInvalidation = lookupGeneration(a)
            assertEquals("Only STOP advances the invalidation generation", before + (if (cancel) 1L else 0L),
                afterInvalidation)
            assertNull("STOP/navigation clears the question before any dialog closes", pendingAppName(a))
            if (!cancel) {
                val center = get(a, "centerDialog") as? AlertDialog
                assertNotNull("Independent navigation opens its center", center)
                val dialog = requireNotNull(center)
                assertTrue("Memory center is showing", dialog.isShowing)
                assertSame("Navigation did not open an app dialog", dialog, ShadowAlertDialog.getLatestAlertDialog())
                val views = descendants(requireNotNull(dialog.window).decorView).filterIsInstance<TextView>()
                assertTrue("Expected memory center", views.any { it.text.toString() == "Gedächtniszentrum" })
                val close = views.single { it.text.toString() == "SCHLIESSEN" }
                assertTrue("Owner closes the memory center", close.performClick())
                await { !dialog.isShowing && get(a, "centerDialog") == null }
            }
            submit(a, "Signal", chat = false)
            await { get(a, "busy") == false && get(a, "botLookupPending") == false }
            assertEquals("Name after invalidation starts no lookup", afterInvalidation, lookupGeneration(a))
            assertNull(get(a, "activeOwnerAppFlow"))
            assertEquals(false, get(a, "appLookupActive"))
            assertEquals(0, port.calls.get())
            assertFalse("No dialog after ${if (cancel) "STOP" else "independent navigation"}",
                ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        }
    }

    @Test fun diagnosisKeepsPriorityOverPendingAppName() = withActivity { a, port, _ ->
        submit(a, "Öffne eine App", chat = false)
        val before = lookupGeneration(a)
        submit(a, "Systemdiagnose", chat = false)
        await { get(a, "busy") == false }
        assertNotNull((get(a, "executor") as DeviceToolExecutor).lastDiagnosis)
        assertEquals("Diagnosis is an independent command", before, lookupGeneration(a))
        assertEquals(0, port.calls.get())
        submit(a, "Signal", chat = false)
        advance(100)
        assertEquals("Diagnosis consumed the older question", before, lookupGeneration(a))
        assertEquals(0, port.calls.get())
    }

    @Test fun chatInputOpensRealWorkWindowHoldsGazeAndStopsProgressAtResult() = withActivity { a, port, _ ->
        submit(a, "Jarvis kannst du mir bitte Signal öffnen?")
        assertEquals(0, port.calls.get())
        approve(port)
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        advance(80); measure(a.window.decorView); elapse(3_200)
        val views = descendants(a.window.decorView)
        assertTrue(views.any { it.tag == "jarvis-work-window" && it.isShown })
        assertTrue("Running app keeps progress: ${(get(a, "activityText") as TextView).text}",
            views.filterIsInstance<ProgressBar>().any { it.isShown && it.isIndeterminate })
        assertTrue("Actual tool gaze outlives opening glance", pose(a).reading)
        assertEquals(ContinuumWorkArea.START, get(a, "activeWorkArea"))
        port.release.countDown()
        await { get(a, "busy") == false }
        assertFalse(pose(a).reading)
        assertTrue(descendants(a.window.decorView).filterIsInstance<ProgressBar>()
            .filter { it.tag?.toString()?.startsWith("jarvis-tool-progress:") == true }.none { it.isIndeterminate })
        advance(2_200)
        assertEquals(ContinuumWorkArea.CHAT, get(a, "activeWorkArea"))
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("neuen aktiven Fenster") })
    }

    @Test fun implicitFuzzyNeedReturnsToConversationBeforeAppSelection() = withActivity { a, port, _ ->
        val previousGeneration = lookupGeneration(a)
        submit(a, "Ich brauche Sig", chat = false)
        await {
            lookupGeneration(a) > previousGeneration && get(a, "appLookupActive") == false
        }
        assertFalse("Fuzzy implicit needs must not offer app selection",
            ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true)
        assertEquals(0, port.calls.get())
        assertNull(get(a, "activeOwnerAppFlow"))
        assertNull("Unaccepted matches must not become pronoun context", get(a, "appLookupContext"))
    }

    @Test fun rejectedFuzzyNeedClearsPriorVerifiedPronounCandidate() = withActivity { a, port, _ ->
        val first = lookupGeneration(a)
        submit(a, "Suche Signal", chat = false)
        await {
            lookupGeneration(a) > first && get(a, "appLookupActive") == false &&
                get(a, "appLookupContext") != null
        }
        val second = lookupGeneration(a)
        submit(a, "Ich brauche Sig", chat = false)
        await {
            lookupGeneration(a) > second && get(a, "appLookupActive") == false
        }
        assertNull("Rejected fuzzy need must clear older reference", get(a, "appLookupContext"))
        val third = lookupGeneration(a)
        submit(a, "Öffne sie", chat = false)
        advance(100)
        assertEquals("Unresolved pronoun must not dispatch lookup", third, lookupGeneration(a))
        assertEquals(0, port.calls.get())
    }

    @Test fun ambiguousFuzzySelectionKeepsCancellationReceipt() = withActivity { a, port, _ ->
        val info = ApplicationInfo().apply { packageName = "org.example.signalnotes"; nonLocalizedLabel = "Signal Notes" }
        shadowOf(a.packageManager).installPackage(PackageInfo().apply {
            packageName = info.packageName; applicationInfo = info
        })
        shadowOf(a.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = info.packageName; name = "Main"; applicationInfo = info }
                nonLocalizedLabel = "Signal Notes"
            })
        submit(a, "Öffne Sig", chat = false)
        await { ShadowAlertDialog.getLatestAlertDialog()?.isShowing == true }
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(2, dialog.listView.adapter.count)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        await { get(a, "busy") == false }
        assertEquals(0, port.calls.get())
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("App-Auswahl abgebrochen") })
    }

    @Test fun stopBeforePostedBackendStartCannotReopenScene() = withActivity { a, port, _ ->
        submit(a, "Öffne Signal", chat = false)
        approve(port)
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        (get(a, "sendButton") as Button).performClick()
        elapse(3_000)
        assertFalse(pose(a).reading)
        assertFalse(descendants(a.window.decorView).any { it.tag == "jarvis-work-window" && it.isShown })
        assertNull(get(a, "activeOwnerAppFlow"))
    }

    @Test fun foregroundLaunchKeepsActualResultForReturn() = withActivity { a, port, controller ->
        submit(a, "Mach mir Signal auf", chat = false)
        approve(port)
        assertTrue(port.entered.await(5, TimeUnit.SECONDS))
        advance(80)
        controller.pause()
        port.release.countDown()
        await { get(a, "pendingAppActionReply") != null }
        assertFalse(descendants(a.window.decorView).any { it.tag == "jarvis-work-window" && it.isShown })
        controller.resume()
        advance(80)
        assertNull(get(a, "pendingAppActionReply"))
        assertTrue(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("neuen aktiven Fenster") })
    }

    @Test fun missingAppAndPermissionNeverBecomeSuccessText() = withActivity { a, port, _ ->
        submit(a, "Öffne Nichtinstalliert", chat = false)
        await { get(a, "busy") == false }
        assertEquals(0, port.calls.get())
        port.failure = "ACCESSIBILITY_OBSERVATION_NOT_GRANTED"
        submit(a, "Öffne Signal", chat = false)
        approve(port)
        port.release.countDown()
        await { get(a, "busy") == false }
        assertFalse(descendants(a.window.decorView).filterIsInstance<TextView>()
            .any { it.text.contains("wurde in einem neuen aktiven Fenster beobachtet") })
    }
}
