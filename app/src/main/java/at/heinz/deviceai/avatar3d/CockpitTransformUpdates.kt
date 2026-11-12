package at.heinz.deviceai.avatar3d

/** Exact, instance-local upload suppression. */
internal class CockpitTransformUpdates(
    private val upload: (Int, FloatArray) -> Unit,
    enabled: Boolean = true
) {
    private val previous = HashMap<Int, FloatArray>(9)
    var enabled = enabled
        set(value) {
            if (field != value) clear()
            field = value
        }

    fun setTransform(instance: Int, matrix: FloatArray) {
        require(matrix.size >= 16)
        val saved = previous[instance]
        if (enabled && saved != null &&
            (0 until 16).all { saved[it].toRawBits() == matrix[it].toRawBits() }) return
        try {
            upload(instance, matrix)
        } catch (failure: Throwable) {
            previous.remove(instance)
            throw failure
        }
        if (enabled && (0 until 16).all { matrix[it].isFinite() }) {
            val snapshot = saved ?: FloatArray(16)
            matrix.copyInto(snapshot, 0, 0, 16)
            previous[instance] = snapshot
        } else {
            previous.remove(instance)
        }
    }

    fun clear() = previous.clear()
}
