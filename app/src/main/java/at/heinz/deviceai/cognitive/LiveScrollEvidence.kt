package at.heinz.deviceai.cognitive

/** Numeric movement, no screen text. */
internal data class LiveScrollMotion(val pixelsX: Long = 0, val pixelsY: Long = 0, val firstIndexDelta: Long = 0) {
    val moved: Boolean get() = pixelsX != 0L || pixelsY != 0L || firstIndexDelta != 0L
}

/** One matching OS scroll event. */
internal data class LiveScrollSample(
    val deltaX: Int? = null, val deltaY: Int? = null,
    val offsetX: Int? = null, val offsetY: Int? = null,
    val firstIndex: Int? = null, val itemCount: Int? = null
) {
    fun movementSince(previous: LiveScrollSample?): LiveScrollMotion? {
        fun delta(value: Int?) = value?.takeUnless {
            it == -1 || it == Int.MIN_VALUE || it == Int.MAX_VALUE
        }?.toLong() ?: 0L
        val direct = LiveScrollMotion(delta(deltaX), delta(deltaY))
        if (direct.moved) return direct
        if (previous == null) return null
        fun difference(before: Int?, after: Int?) =
            if (before != null && after != null && before in 0 until Int.MAX_VALUE &&
                after in 0 until Int.MAX_VALUE) after.toLong() - before else 0L
        val offsets = LiveScrollMotion(difference(previous.offsetX, offsetX), difference(previous.offsetY, offsetY))
        if (offsets.moved) return offsets
        val index = if (itemCount != null && itemCount > 0 && itemCount == previous.itemCount &&
            firstIndex != null && previous.firstIndex != null && firstIndex in 0 until itemCount &&
            previous.firstIndex in 0 until itemCount) firstIndex.toLong() - previous.firstIndex else 0L
        return LiveScrollMotion(firstIndexDelta = index).takeIf { it.moved }
    }
}

/** Bound before the native dispatch. */
internal data class LiveScrollDispatch(
    val source: ObservationStamp,
    val stepId: String,
    val fingerprint: String,
    val targetFingerprint: String,
    val dispatchedAtNanos: Long,
    val dispatchedAtUptimeMillis: Long
) {
    companion object {
        fun targetFingerprint(node: ActionNode) = liveSha256("${node.viewId}|${node.label}|${node.bounds}")
    }
}

/** Only the bound native source. */
internal data class LiveScrollEvidence(
    val dispatch: LiveScrollDispatch,
    val eventRevision: Long,
    val receivedAtNanos: Long,
    val eventUptimeMillis: Long,
    val motion: LiveScrollMotion
) {
    fun confirms(request: NodeActionRequest, before: ObservationStamp, target: ActionNode,
                 after: NodeActionFrame): Boolean {
        val source = dispatch.source
        if (request.kind !in setOf(NodeActionKind.SCROLL_FORWARD, NodeActionKind.SCROLL_BACKWARD) ||
            source != before || source.captureSession <= 0 || source.windowId == null ||
            dispatch.stepId != request.stepId || dispatch.fingerprint != request.fingerprint ||
            source.taskId != request.taskId || source.packageName != request.packageName ||
            source.epoch != request.epoch || source.revision != request.revision ||
            dispatch.targetFingerprint != LiveScrollDispatch.targetFingerprint(target) ||
            dispatch.dispatchedAtNanos < source.observedAtNanos || receivedAtNanos < dispatch.dispatchedAtNanos ||
            eventUptimeMillis < dispatch.dispatchedAtUptimeMillis || eventRevision <= source.revision ||
            eventRevision > after.stamp.revision || receivedAtNanos > after.stamp.observedAtNanos || !motion.moved ||
            after.stamp.taskId != source.taskId || after.stamp.epoch != source.epoch ||
            after.stamp.captureSession != source.captureSession || after.stamp.packageName != source.packageName ||
            after.stamp.windowId != source.windowId) return false
        val node = after.nodes.filter { it.visible && request.selector.matches(it) }.singleOrNull() ?: return false
        return node.scrollable && node.enabled && !node.password &&
            LiveScrollDispatch.targetFingerprint(node) == dispatch.targetFingerprint
    }
}
