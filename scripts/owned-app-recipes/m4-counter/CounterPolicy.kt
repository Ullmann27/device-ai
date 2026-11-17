package at.heinz.continuum.m4counter

internal object CounterPolicy {
    const val MAX_VALUE = 9999
    fun restored(value: Int): Int = value.coerceIn(0, MAX_VALUE)
    fun increment(value: Int): Int = (restored(value) + 1).coerceAtMost(MAX_VALUE)
}
