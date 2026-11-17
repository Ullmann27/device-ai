package at.heinz.deviceai

import android.view.View
import android.graphics.Rect
import android.webkit.WebView
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWebView
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Restores native host layout/focus calls; it never supplies Web content, visibility or draw evidence. */
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

    // The same installed 4.14.1 ViewDelegate proxy returns false for requestFocus(int, Rect):
    // invoke -> ShadowWebView.nullish(Method) -> Boolean.valueOf(false). Android 28 WebView
    // delegates this call to its provider. Restore only the underlying View focus transaction;
    // real focusability, touch mode, ancestor policy and hasFocus() checks still apply.
    @Implementation
    protected fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean =
        Shadow.directlyOn(realView, View::class.java, "requestFocus",
            ClassParameter.from(Int::class.javaPrimitiveType, direction),
            ClassParameter.from(Rect::class.java, previouslyFocusedRect))
}
