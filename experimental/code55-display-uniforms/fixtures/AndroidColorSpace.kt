package android.graphics

class ColorSpace private constructor(val name: Named) {
    enum class Named { SRGB, DISPLAY_P3 }
    companion object {
        private val spaces = Named.entries.associateWith(::ColorSpace)
        fun get(name: Named) = spaces.getValue(name)
    }
}
