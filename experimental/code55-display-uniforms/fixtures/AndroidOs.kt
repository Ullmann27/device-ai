package android.os

class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(val looper: Looper)
class Parcel {
    var payload: Any? = null
    fun setDataPosition(position: Int) { require(position == 0) }
    fun recycle() { payload = null }
    companion object { fun obtain() = Parcel() }
}
