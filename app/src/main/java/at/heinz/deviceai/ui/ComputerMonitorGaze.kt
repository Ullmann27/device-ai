package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.GazeTarget
import at.heinz.deviceai.avatar3d.CockpitStageFrame
import at.heinz.deviceai.cognitive.CockpitWorkEvent
import at.heinz.deviceai.cognitive.LiveMonitorStatus
import at.heinz.deviceai.cognitive.LiveMonitorState

/** Matches the actual monitor projection. */
internal data class ComputerMonitorGaze(
    val frame: CockpitStageFrame,
    val headX: Float,
    val headY: Float
) {
    fun project(event: CockpitWorkEvent, submitted: LiveMonitorStatus): GazeTarget? {
        val binding = submitted.binding ?: return null
        val meta = submitted.meta ?: return null
        if (!frame.valid() || !headX.isFinite() || !headY.isFinite() ||
            submitted.state != LiveMonitorState.LIVE || binding.taskId != event.taskId ||
            binding.captureSession != event.sessionId || meta.width <= 0 || meta.height <= 0) return null
        val target = event.target
        var x = .5f
        var y = .5f
        if (target != null) {
            val crop = meta.sourceViewport ?: return null
            if (event.frameSequence != submitted.sequence || event.observationRevision != meta.observationRevision ||
                target.x !in crop.left..crop.right || target.y !in crop.top..crop.bottom ||
                crop.right <= crop.left || crop.bottom <= crop.top) return null
            x = (target.x - crop.left) / (crop.right - crop.left)
            y = (target.y - crop.top) / (crop.bottom - crop.top)
        }
        val contentWidth = CockpitStageFrame.CONTENT_RIGHT - CockpitStageFrame.CONTENT_LEFT
        val contentHeight = CockpitStageFrame.CONTENT_BOTTOM - CockpitStageFrame.CONTENT_TOP
        val contentAspect = frame.monitorWidth * contentWidth / (frame.monitorHeight * contentHeight)
        val sourceAspect = meta.width.toFloat() / meta.height
        val u = CockpitStageFrame.CONTENT_LEFT +
            (.5f + (x - .5f) * minOf(1f, sourceAspect / contentAspect)) * contentWidth
        val v = CockpitStageFrame.CONTENT_TOP +
            (.5f + (y - .5f) * minOf(1f, contentAspect / sourceAspect)) * contentHeight
        val point = frame.quad(u, v, u, v)
        return JarvisWindowGaze.fromProjection(point[0], point[1], headX, headY, frame.width, frame.height)
    }
}
