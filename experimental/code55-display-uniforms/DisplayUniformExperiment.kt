package at.heinz.deviceai.avatar3d

import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import at.heinz.deviceai.AndroidLiveFrame
import at.heinz.deviceai.cognitive.*
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance

private val stableKeys = setOf("frameAvailable", "pointerAvailable", "contentScale")
private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n") + "\""
private fun strings(values: Iterable<String>) = values.joinToString(",", "[", "]", transform = ::quote)

private class Probe {
    var clockOffset = 0L
    var binding = LiveFrameBinding("task-one", 7, "com.example.target", "at.heinz.deviceai",
        LiveFrameOrigin.PROJECTION_WINDOW_CROP, staleAfterMillis = 30_000, minFrameIntervalMillis = 0)
    val feed = LatestLiveFrameHolder<AndroidLiveFrame>(AndroidLiveFrame::close) { System.nanoTime() + clockOffset }
    val engine = Engine()
    val material = MaterialInstance()
    val monitor: CockpitLiveTexture
    var frame = CockpitStageFrame(640f, 480f, 100f, 50f, 600f, 400f)
    val trace = mutableListOf<String>()
    var timestamp = 0L
    var eventSequence = 0L
    var budgetStart = 0
    var budgetEnd: Int? = null
    var ticks = 0
    val submissionMethod get() = monitor.javaClass.methods.firstOrNull { it.name == "frameSubmitted" }

    init {
        HardwareBuffer.reset()
        monitor = if (System.getProperty("uniformControl") == "true") {
            CockpitLiveTexture::class.java.getDeclaredConstructor(Engine::class.java,
                MaterialInstance::class.java, Boolean::class.javaPrimitiveType)
                .newInstance(engine, material, false)
        } else CockpitLiveTexture(engine, material)
        feed.bind(binding)
        monitor.bind(feed)
        budgetStart = material.writes.size
    }

    fun offer(width: Int = 1920, height: Int = 1080,
              viewport: LiveTargetBounds = LiveTargetBounds(0f, 0f, 1f, 1f),
              color: ColorSpace.Named = ColorSpace.Named.SRGB,
              session: Long = binding.captureSession, time: Long = ++timestamp): LiveFrameOffer {
        val buffer = HardwareBuffer.create(width, height)
        val meta = LiveFrameMeta(session, time, width, height, binding.targetPackage, 42,
            ownWindowVisible = false, observationRevision = timestamp, sourceViewport = viewport)
        return feed.offer(meta, AndroidLiveFrame(buffer, ColorSpace.get(color)))
    }

    fun tick(tag: String) {
        monitor.update(frame, System.nanoTime())
        submissionMethod?.invoke(monitor, System.nanoTime())
        ticks++
        record(tag)
    }

    fun record(tag: String) {
        val values = material.values.filterKeys { it != "pointerPhase" }
        val phase = material.values["pointerPhase"]?.toFloat()
        if (material.values["pointerAvailable"] == "true") check(phase != null && phase in 0f..1f)
        trace += "$tag|visible=${monitor.pixelsVisible}|label=${monitor.label}|values=$values|" +
            "imports=${engine.events.filter { it.startsWith("import:") }}|live=${HardwareBuffer.live.size}"
    }

    fun event(x: Float = .5f, y: Float = .5f, session: Long = binding.captureSession,
              frameSequence: Long = feed.status(System.nanoTime()).sequence, ageNanos: Long = 0) {
        monitor.workEvent(CockpitWorkEvent(binding.taskId, session, ++eventSequence,
            System.nanoTime() - ageNanos, 0, CockpitWorkEvent.Stage.DISPATCHED, "click",
            CockpitWorkEvent.Action.CLICK, CockpitWorkEvent.Target(x, y), 1, frameSequence))
    }

    fun finish() {
        budgetEnd = material.writes.size
        monitor.close()
        feed.end()
        engine.releaseAll()
        check(HardwareBuffer.live.isEmpty()) { "RETAINED_BUFFER" }
        check(HardwareBuffer.maxLive <= 4) { "REFERENCE_BOUND" }
        record("closed")
    }

    fun result(name: String): String {
        val writes = material.writes.subList(budgetStart, requireNotNull(budgetEnd)).count { it.first in stableKeys }
        return "{" + "\"case\":" + quote(name) + ",\"ticks\":$ticks,\"stableWrites\":$writes," +
            "\"maxBuffers\":${HardwareBuffer.maxLive},\"trace\":" + strings(trace) +
            ",\"importsAndReleases\":" + strings(engine.events) +
            ",\"bufferEvents\":" + strings(HardwareBuffer.events) + "}"
    }
}

private fun scenario(name: String, body: Probe.() -> Unit) {
    val probe = Probe()
    try { probe.body() } finally { probe.finish() }
    println(probe.result(name))
}

private fun holdoutScenario(name: String, body: Probe.() -> Unit): Boolean {
    val probe = Probe()
    var failure: String? = null
    try { probe.body() } catch (error: Throwable) { failure = error.message ?: error.javaClass.simpleName }
    finally { probe.finish() }
    println(probe.result(name).dropLast(1) + ",\"passed\":${failure == null},\"failure\":" +
        (failure?.let(::quote) ?: "null") + "}")
    return failure == null
}

private fun partialWriteHoldout() {
    val results = listOf(
        holdoutScenario("partial_frame_write_then_restore") {
            offer(); tick("first")
            offer(); tick("second")
            offer()
            material.failAfterWrite = "frameAvailable"
            val failed = if (submissionMethod == null) runCatching { tick("partial-hide") }
                else runCatching { monitor.javaClass.getMethod("frameFailed").invoke(monitor) }
            check(failed.isFailure) { "HOLDOUT_NOT_TRIGGERED" }
            check(material.values["frameAvailable"] == "false")
            engine.releaseOne()
            tick("restore-live")
            check(monitor.pixelsVisible)
            check(material.values["frameAvailable"] == "true") { "STALE_FRAME_CACHE_AFTER_PARTIAL_WRITE" }
        },
        holdoutScenario("partial_pointer_write_then_clear") {
            offer(); tick("live"); engine.releaseAll()
            event()
            material.failAfterWrite = "pointerAvailable"
            check(runCatching { tick("partial-pointer") }.isFailure) { "HOLDOUT_NOT_TRIGGERED" }
            check(material.values["pointerAvailable"] == "true")
            monitor.workEvent(CockpitWorkEvent(binding.taskId, binding.captureSession, ++eventSequence,
                System.nanoTime(), 0, CockpitWorkEvent.Stage.STOPPED))
            tick("clear-pointer")
            check(material.values["pointerAvailable"] == "false") { "STALE_POINTER_CACHE_AFTER_PARTIAL_WRITE" }
        },
        holdoutScenario("partial_scale_write_then_restore") {
            offer(); tick("original"); engine.releaseAll()
            val originalFrame = frame
            val originalScale = material.values["contentScale"]
            frame = frame.copy(monitorLeft = 200f, monitorRight = 480f)
            material.failAfterWrite = "contentScale"
            check(runCatching { tick("partial-resize") }.isFailure) { "HOLDOUT_NOT_TRIGGERED" }
            check(material.values["contentScale"] != originalScale)
            frame = originalFrame
            tick("restore-scale")
            check(material.values["contentScale"] == originalScale) { "STALE_SCALE_CACHE_AFTER_PARTIAL_WRITE" }
        })
    check(results.all { it }) { "PARTIAL_WRITE_HOLDOUT_FAILED" }
}

fun main() {
    if (System.getProperty("uniformHoldout") == "true") { partialWriteHoldout(); return }
    scenario("sixty_ticks_ten_frames") {
        repeat(60) { i ->
            if (i % 6 == 0) check(offer() is LiveFrameOffer.Accepted)
            tick("tick-$i")
            check(monitor.pixelsVisible)
            engine.releaseAll()
        }
        check(engine.events.count { it.startsWith("import:") } == 10)
    }
    scenario("sixty_ticks_one_frame") {
        offer()
        repeat(60) { tick("tick-$it"); check(monitor.pixelsVisible); engine.releaseAll() }
        check(engine.events.count { it.startsWith("import:") } == 1)
    }
    scenario("no_source") {
        repeat(12) { tick("waiting-$it"); check(!monitor.pixelsVisible) }
    }
    scenario("session_switch_and_old_callback") {
        offer(); tick("old-session")
        binding = binding.copy(taskId = "task-two", captureSession = 8)
        feed.bind(binding)
        tick("new-session-empty"); check(!monitor.pixelsVisible)
        offer(); tick("new-session-frame"); check(monitor.pixelsVisible)
        engine.releaseOne()
        tick("old-release-delivered"); check(monitor.pixelsVisible)
        check(offer(session = 7) == LiveFrameOffer.Refused(LiveFrameRefusal.WRONG_SESSION))
        tick("old-publisher-rejected"); check(monitor.pixelsVisible)
    }
    scenario("pause_stop_and_rebind") {
        offer(); tick("live")
        feed.pause(LiveMonitorReason.PERMISSION_REVOKED, binding.captureSession)
        tick("revoked"); check(!monitor.pixelsVisible)
        repeat(3) { tick("paused-$it") }
        feed.end(); tick("stopped"); check(!monitor.pixelsVisible)
        binding = binding.copy(captureSession = 9)
        feed.bind(binding); offer(); tick("restarted"); check(monitor.pixelsVisible)
    }
    scenario("resize_and_content_aspect") {
        offer(); tick("landscape")
        val firstScale = material.values["contentScale"]
        engine.releaseAll()
        offer(width = 1080, height = 1920); tick("portrait")
        check(firstScale != material.values["contentScale"])
        engine.releaseAll()
        frame = frame.copy(monitorLeft = 200f, monitorRight = 480f)
        tick("narrow-monitor")
        val changedScale = material.values["contentScale"]
        repeat(3) { tick("stable-aspect-$it"); check(changedScale == material.values["contentScale"]) }
    }
    scenario("viewport_pointer_and_frame_binding") {
        offer(viewport = LiveTargetBounds(.25f, .25f, .75f, .75f)); tick("crop")
        event(y = .375f); tick("bound-click")
        check(material.values["pointerAvailable"] == "true")
        check(material.values["pointerUv"] == "0.5,0.25")
        engine.releaseAll()
        offer(viewport = LiveTargetBounds(0f, 0f, .5f, 1f)); tick("new-viewport")
        check(material.values["pointerAvailable"] == "false")
        event(frameSequence = 1); tick("old-frame-click")
        check(material.values["pointerAvailable"] == "false")
        event(x = .75f); tick("outside-crop")
        check(material.values["pointerAvailable"] == "false")
        event(session = 8); tick("wrong-session-click")
        check(material.values["pointerAvailable"] == "false")
        event(x = .25f); tick("new-crop-click")
        check(material.values["pointerUv"] == "0.5,0.5")
        Thread.sleep(240)
        tick("expired-click")
        check(material.values["pointerAvailable"] == "false")
    }
    scenario("stale_and_out_of_order") {
        offer(); tick("live")
        check(offer(time = timestamp) == LiveFrameOffer.Refused(LiveFrameRefusal.OUT_OF_ORDER))
        tick("old-frame-refused"); check(monitor.pixelsVisible)
        engine.releaseAll()
        binding = binding.copy(captureSession = 8)
        feed.bind(binding)
        clockOffset = -31_000_000_000L
        check(offer() is LiveFrameOffer.Accepted)
        check(feed.status(System.nanoTime()).state == LiveMonitorState.PAUSED)
        tick("stale-frame"); check(!monitor.pixelsVisible)
    }
    scenario("latest_frame_only") {
        offer(); offer(); offer(); tick("latest-of-three")
        check(engine.events.filter { it.startsWith("import:") }.single().endsWith(":3"))
    }
    scenario("delayed_gpu_releases") {
        offer(); tick("first")
        offer(); tick("second")
        offer(); tick("two-references-block")
        check(monitor.pixelsVisible == (submissionMethod != null))
        check(engine.events.count { it.startsWith("import:") } == 2)
        engine.releaseOne()
        tick("latest-after-release"); check(monitor.pixelsVisible)
        check(engine.events.count { it.startsWith("import:") } == 3)
    }
    scenario("unsupported_color") {
        offer(); tick("srgb"); engine.releaseAll()
        offer(color = ColorSpace.Named.DISPLAY_P3); tick("unsupported")
        check(!monitor.pixelsVisible)
        check(engine.events.count { it.startsWith("import:") } == 1)
    }
    scenario("import_failure_retry") {
        offer(); engine.failNextImport = true
        tick("failed-import"); check(!monitor.pixelsVisible)
        tick("retry-import"); check(monitor.pixelsVisible)
        check(engine.events.count { it.startsWith("import:") } == 1)
    }
    scenario("material_failure_retry") {
        offer(); tick("initial"); engine.releaseAll()
        frame = frame.copy(monitorLeft = 200f, monitorRight = 480f)
        material.failNext = "contentScale"
        check(runCatching { tick("material-fails") }.isFailure)
        tick("material-retry")
        val aspect = frame.monitorWidth * (CockpitStageFrame.CONTENT_RIGHT - CockpitStageFrame.CONTENT_LEFT) /
            (frame.monitorHeight * (CockpitStageFrame.CONTENT_BOTTOM - CockpitStageFrame.CONTENT_TOP))
        check(material.values["contentScale"] == "${minOf(1f, (1920f / 1080) / aspect)},${minOf(1f, aspect / (1920f / 1080))}")
    }
    scenario("feed_detach_and_stream_reset") {
        offer(); tick("live")
        monitor.bind(null); tick("detached"); check(!monitor.pixelsVisible)
        monitor.bind(feed); tick("reattached"); check(monitor.pixelsVisible)
        engine.releaseAll()
        monitor.close()
        val calls = material.writes.size
        tick("closed-update")
        check(material.writes.size == calls)
    }
}
