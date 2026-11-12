package android.hardware

import android.os.Parcel

class HardwareBuffer private constructor(val width: Int, val height: Int, val image: Int, val handle: Int) {
    var isClosed = false
        private set
    fun close() {
        check(!isClosed) { "DOUBLE_NATIVE_CLOSE" }
        isClosed = true
        events += "close:$image:$handle"
        live.remove(handle)
    }
    fun writeToParcel(parcel: Parcel, flags: Int) {
        check(!isClosed)
        parcel.payload = this
    }
    companion object {
        private var nextImage = 0
        private var nextHandle = 0
        val events = mutableListOf<String>()
        val live = mutableSetOf<Int>()
        var maxLive = 0
        fun create(width: Int, height: Int): HardwareBuffer = make(width, height, ++nextImage)
        private fun make(width: Int, height: Int, image: Int): HardwareBuffer {
            val result = HardwareBuffer(width, height, image, ++nextHandle)
            live += result.handle
            maxLive = maxOf(maxLive, live.size)
            events += "open:${result.image}:${result.handle}"
            return result
        }
        fun reset() { check(live.isEmpty()); nextImage = 0; nextHandle = 0; maxLive = 0; events.clear() }
        val CREATOR = Creator()
    }
    class Creator {
        fun createFromParcel(parcel: Parcel): HardwareBuffer {
            val source = parcel.payload as HardwareBuffer
            check(!source.isClosed)
            return make(source.width, source.height, source.image)
        }
    }
}
