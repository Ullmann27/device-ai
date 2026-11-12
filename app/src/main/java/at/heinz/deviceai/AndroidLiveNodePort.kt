package at.heinz.deviceai

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import at.heinz.deviceai.cognitive.*
import java.security.MessageDigest

internal class AndroidLiveNodePort(
    private val access: LiveObservationAccess,
    private val taskId: String,
    private val targetPackage: String,
    private val frameSequence: () -> Long,
    private val ensureActive: () -> Unit,
    private val permissionCheck: () -> Unit = { error("ACTION_GRANT_REQUIRED") },
    private val deadlineNanos: Long = System.nanoTime() + 2_500_000_000L
) : NodeActionPort {
    private var before: NodeActionFrame? = null
    private var lastRequest: NodeActionRequest? = null
    private var clickExpectation: ClickRequest? = null
    var inputAttempted: Boolean = false
        private set
    var inputAccepted: Boolean? = null
        private set

    override fun checkActive() {
        ensureActive()
        check(!Thread.currentThread().isInterrupted) { "DEVICE_ACTION_CANCELLED" }
        check(System.nanoTime() < deadlineNanos) { "DEVICE_ACTION_DEADLINE" }
        ScreenObservationBus.liveRevision(access)
    }

    override fun current(): NodeActionFrame = inspect(null, null).first.also { before = it }

    override fun performOnce(request: NodeActionRequest, target: ActionNode): Boolean {
        check(request.taskId == taskId && request.packageName == targetPackage && request.epoch == access.epoch) {
            "ACTION_SESSION_MISMATCH"
        }
        check(request.kind != NodeActionKind.SEND) { "SEND_REQUIRES_REGISTERED_TOOL" }
        lastRequest = request
        return inspect(request, target).second
    }

    override fun awaitPostcondition(): NodeActionFrame? {
        val prior = before ?: error("NO_PRE_ACTION_OBSERVATION")
        var revision = prior.stamp.revision
        var latest: NodeActionFrame? = null
        val observationDeadline = deadlineNanos - 100_000_000L
        while (System.nanoTime() < observationDeadline) {
            if (!ScreenObservationBus.awaitLiveRevision(access, revision, observationDeadline, ::checkActive)) return latest
            checkActive()
            val candidate = try { inspect(null, null).first }
            catch (error: IllegalStateException) {
                if (error.message != "SCREEN_CHANGED_DURING_READ") throw error
                continue
            }
            latest = candidate
            revision = candidate.stamp.revision
            if (candidate.stamp.digest != prior.stamp.digest && expected(candidate)) return candidate
        }
        return latest
    }

    private fun expected(frame: NodeActionFrame): Boolean {
        val request = lastRequest ?: return false
        return when (request.kind) {
            NodeActionKind.SET_TEXT -> frame.nodes.filter {
                it.visible && it.editable && !it.password && request.selector.matches(it)
            }.singleOrNull()?.textDigest == liveSha256(request.text!!)
            NodeActionKind.CLICK -> clickExpectation?.let { click -> frame.nodes.count {
                !it.password && click.matchesExpected(it)
            } == 1 } ?: false
            NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD ->
                frame.nodes.count { it.visible && it.scrollable && request.selector.matches(it) } == 1
            NodeActionKind.SEND -> false
        }
    }

    fun clickPort(request: ClickRequest, onDispatched: (LiveTargetBounds?, Long) -> Unit): ClickObservationPort =
        object : ClickObservationPort {
            override fun checkActive() = this@AndroidLiveNodePort.checkActive()
            override fun current() = this@AndroidLiveNodePort.current().asClickFrame()
            override fun clickOnce(request: ClickRequest): Boolean {
                clickExpectation = request
                val prior = before ?: error("NO_PRE_ACTION_OBSERVATION")
                val target = prior.nodes.single { it.visible && it.label == request.label }
                check(!target.editable && !target.password && target.clickable && target.enabled &&
                    !SendActionGuard.isSendLike(target.label, target.viewId)) { "UNSAFE_CLICK_TARGET" }
                val action = NodeActionRequest(taskId, "click-${request.revision}", targetPackage,
                    request.epoch, request.revision, NodeActionKind.CLICK, NodeSelector(label = request.label))
                val accepted = performOnce(action, target)
                if (accepted) onDispatched(target.bounds, prior.stamp.frameSequence)
                return accepted
            }
            override fun awaitPostcondition(): ClickFrame =
                (this@AndroidLiveNodePort.awaitPostcondition() ?: error("NO_NEW_OBSERVATION")).asClickFrame()
        }

    private fun NodeActionFrame.asClickFrame() = ClickFrame(stamp.packageName, stamp.epoch, stamp.revision,
        stamp.digest, safeWindows, complete, nodes.map {
            // Editable labels contain hints, never field text.
            ClickNode(it.label, it.visible, it.enabled && it.clickable && !it.editable, it.password,
                it.editable, it.heading, it.viewId, it.enabled)
        })

    private fun inspect(request: NodeActionRequest?, target: ActionNode?): Pair<NodeActionFrame, Boolean> {
        checkActive()
        val revision = ScreenObservationBus.liveRevision(access)
        if (request != null) check(revision == request.revision) { "STALE_OR_UNSAFE_SCREEN" }
        return access.service.withLiveWindow(targetPackage, before?.stamp?.windowId, ::checkActive) { root, selection ->
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(root to 0)
            val nodes = ArrayList<ActionNode>()
            val digest = MessageDigest.getInstance("SHA-256")
            var selected: AccessibilityNodeInfo? = null
            var selectedRecord: ActionNode? = null
            var matches = 0
            try {
                while (queue.isNotEmpty()) {
                    val (node, depth) = queue.removeFirst()
                    var retain = false
                    try {
                        checkActive()
                        check(nodes.size < 256 && node.windowId == selection.target.id &&
                            node.packageName?.toString() == targetPackage) { "INCOMPLETE_OR_CHANGED_SCREEN_TREE" }
                        val record = describe(node, selection)
                        nodes += record
                        digest.update((record.toString() + "|$depth\n").toByteArray(Charsets.UTF_8))
                        if (request != null && record.visible && request.selector.matches(record)) {
                            matches++
                            if (selected == null) { selected = node; selectedRecord = record; retain = true }
                        }
                        if (node.isVisibleToUser && !node.isPassword) {
                            val count = node.childCount
                            check(count in 0..256 && (depth < 24 || count == 0)) { "SCREEN_TREE_LIMIT" }
                            for (i in 0 until count) {
                                check(nodes.size + queue.size < 256) { "SCREEN_TREE_LIMIT" }
                                queue.add((node.getChild(i) ?: error("INCOMPLETE_SCREEN_TREE")) to depth + 1)
                            }
                        }
                    } finally { if (node !== root && !retain) @Suppress("DEPRECATION") node.recycle() }
                }
                check(ScreenObservationBus.liveRevision(access) == revision) { "SCREEN_CHANGED_DURING_READ" }
                val stamp = ObservationStamp(taskId, access.epoch, revision, targetPackage, selection.target.id,
                    digest.digest().joinToString("") { "%02x".format(it) }, System.nanoTime(), frameSequence())
                val frame = NodeActionFrame(stamp, true, true, nodes)
                if (request == null) return@withLiveWindow frame to false
                val original = before ?: error("NO_PRE_ACTION_OBSERVATION")
                check(matches == 1 && selectedRecord == target && stamp.digest == original.stamp.digest &&
                    stamp.revision == original.stamp.revision) { "STALE_OR_AMBIGUOUS_TARGET" }
                val node = selected ?: error("TARGET_NOT_FOUND")
                checkActive()
                check(node.refresh()) { "STALE_ACTION_NODE" }
                val fresh = access.service.liveWindowSelection(targetPackage)
                check(fresh.target.id == selection.target.id && fresh.target.bounds == selection.target.bounds &&
                    node.windowId == selection.target.id && node.packageName?.toString() == targetPackage &&
                    describe(node, fresh) == target) { "TARGET_WINDOW_CHANGED" }
                check(ScreenObservationBus.liveRevision(access) == revision) { "SCREEN_CHANGED_BEFORE_ACTION" }
                checkActive()
                permissionCheck()
                inputAttempted = true
                val accepted = when (request.kind) {
                    NodeActionKind.CLICK -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    NodeActionKind.SCROLL_FORWARD -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    NodeActionKind.SCROLL_BACKWARD -> node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    NodeActionKind.SET_TEXT -> node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, request.text)
                    })
                    NodeActionKind.SEND -> error("SEND_REQUIRES_REGISTERED_TOOL")
                }
                inputAccepted = accepted
                frame to accepted
            } finally {
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst().first
                    if (node !== root) @Suppress("DEPRECATION") node.recycle()
                }
                selected?.takeIf { it !== root }?.let { @Suppress("DEPRECATION") it.recycle() }
            }
        }
    }

    private fun describe(node: AccessibilityNodeInfo, selection: LiveWindowSafety.Selection): ActionNode {
        val bounds = Rect().also(node::getBoundsInScreen).liveRect()
        val visible = node.isVisibleToUser && selection.permitsNode(bounds)
        val rawLabel = if (node.isPassword) "" else if (node.isEditable)
            (node.contentDescription ?: node.hintText)?.toString().orEmpty()
        else (node.text?.takeIf { it.isNotBlank() } ?: node.contentDescription)?.toString().orEmpty()
        val label = rawLabel.take(4_000).replace(Regex("\\s+"), " ")
            .filterNot { it.isISOControl() }.trim().take(200)
        val box = selection.target.bounds
        val normalized = if (visible) LiveTargetBounds(
            (bounds.left - box.left).toFloat() / (box.right - box.left),
            (bounds.top - box.top).toFloat() / (box.bottom - box.top),
            (bounds.right - box.left).toFloat() / (box.right - box.left),
            (bounds.bottom - box.top).toFloat() / (box.bottom - box.top)) else null
        return ActionNode(label, node.viewIdResourceName, visible, node.isEnabled, node.isClickable,
            node.isScrollable, node.isEditable, node.isPassword, normalized,
            if (node.isEditable && !node.isPassword) liveSha256(node.text?.toString().orEmpty()) else null,
            android.os.Build.VERSION.SDK_INT >= 28 && node.isHeading)
    }
}
