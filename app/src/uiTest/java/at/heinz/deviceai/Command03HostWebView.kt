package at.heinz.deviceai

import android.view.View
import android.webkit.WebView
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Restores host layout only; it neither renders Web content nor supplies visibility/draw evidence. */
@Implements(WebView::class)
open class Command03HostWebView : ShadowWebView() {
    // Robolectric 4.14.1 ShadowWebView.ensureProviderCreated() installs a ViewDelegate proxy
    // (ShadowWebView$1$1.invoke) which returns false for setFrame without updating the View.
    // Android 28 WebView.setFrame delegates there; forward to the real View implementation,
    // as WebView.PrivateAccess.super_setFrame does for an actual WebView provider.
    @Implementation
    protected fun setFrame(left: Int, top: Int, right: Int, bottom: Int): Boolean =
        Shadow.directlyOn(realView, View::class.java, "setFrame",
            ClassParameter.from(Int::class.javaPrimitiveType, left),
            ClassParameter.from(Int::class.javaPrimitiveType, top),
            ClassParameter.from(Int::class.javaPrimitiveType, right),
            ClassParameter.from(Int::class.javaPrimitiveType, bottom))
}
