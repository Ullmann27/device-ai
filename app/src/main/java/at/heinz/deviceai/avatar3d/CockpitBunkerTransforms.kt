package at.heinz.deviceai.avatar3d

/** Environment ignores monitor flight. */
object CockpitBunkerTransforms {
    fun room(frame: CockpitStageFrame, out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        val width = frame.width * frame.worldScale
        val height = frame.height * frame.worldScale
        uniform(out, maxOf(width / 18f, height / 7.8f))
        out[13] = -height * .5f
        out[14] = -1.6f
        return out
    }

    fun desk(frame: CockpitStageFrame, out: FloatArray = FloatArray(16)): FloatArray {
        require(frame.valid())
        val scale = frame.width * frame.worldScale * .86f / 7.75f
        uniform(out, scale)
        out[13] = -frame.height * frame.worldScale * .41f - 1.15f * scale
        out[14] = frame.restDepth * frame.worldScale
        return out
    }

    private fun uniform(out: FloatArray, scale: Float) {
        require(out.size >= 16)
        out.fill(0f, 0, 16)
        out[0] = scale
        out[5] = scale
        out[10] = scale
        out[15] = 1f
    }
}
