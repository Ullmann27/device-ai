package android.content

/** Host-only resource holder. This is not an Android Context or a rendering/lifecycle simulation. */
class Context {
    val assets = Any()
    val resources = android.content.res.Resources()
}
