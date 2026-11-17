package at.heinz.deviceai

import android.graphics.RectF
import at.heinz.deviceai.avatar3d.AvatarGlbContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Verifies the 3D→fallback→3D aura fit without requiring a GPU. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumAuraLifecycleUiTest {
    private fun get(view: ContinuumHologramView, name: String): Any? =
        ContinuumHologramView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view)

    private fun set(view: ContinuumHologramView, name: String, value: Any?) {
        ContinuumHologramView::class.java.getDeclaredField(name).apply { isAccessible = true }.set(view, value)
    }

    private fun update(view: ContinuumHologramView) {
        ContinuumHologramView::class.java.getDeclaredMethod("updateAuraFraming")
            .apply { isAccessible = true }.invoke(view)
    }

    @Test fun cached3dFitIsRecomputedAfterFallbackAndResume() {
        val view = ContinuumHologramView(RuntimeEnvironment.getApplication())
        val focus = AvatarGlbContract.Focus(0.0, 0.5, 0.0, 0.12, 0.27, 0.0, 0.12, 0.12)
        val metadata = AvatarGlbContract.Metadata(emptyList(), emptyList(), focus)
        set(view, "head3dEnabled", true)
        set(view, "assetMetadata", metadata)
        val bounds = get(view, "headDrawBounds") as RectF
        bounds.set(0f, 0f, 240f, 400f)
        view.setHeadFraming(.70f, .92f)

        set(view, "native3dDrawn", true)
        update(view)
        val scale = get(view, "auraScale") as Float
        val anchor = get(view, "auraAnchorEffective") as Float
        assertNotNull(get(view, "auraFocus"))

        set(view, "native3dDrawn", false)
        update(view)
        assertNull("Fallback must invalidate the previous 3D fit", get(view, "auraFocus"))
        assertEquals(1f, get(view, "auraScale") as Float, 0f)
        assertEquals(.70f, get(view, "auraAnchorEffective") as Float, 0f)

        set(view, "native3dDrawn", true)
        update(view)
        assertEquals("Same focus and bounds must restore the fitted scale", scale,
            get(view, "auraScale") as Float, 0.00001f)
        assertEquals("Same focus and bounds must restore the fitted anchor", anchor,
            get(view, "auraAnchorEffective") as Float, 0.00001f)
        assertNotNull(get(view, "auraFocus"))

        bounds.set(0f, 0f, 192f, 400f)
        update(view)
        assertEquals("Width change must also invalidate the fit", 192f,
            get(view, "auraWidth") as Float, 0f)

        bounds.setEmpty()
        update(view)
        assertNull("Empty stage must invalidate the previous fit", get(view, "auraFocus"))
    }
}
