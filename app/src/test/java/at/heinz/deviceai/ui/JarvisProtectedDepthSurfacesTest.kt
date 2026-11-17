package at.heinz.deviceai.ui

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class JarvisProtectedDepthSurfacesTest {
    private val sheet = rect(100f, 200f, 500f, 800f)
    private val density = 2.5f

    private fun rect(left: Float, top: Float, right: Float, bottom: Float) = RectF().also {
        it.left = left
        it.top = top
        it.right = right
        it.bottom = bottom
    }

    private fun assertSameRect(expected: RectF, actual: RectF) {
        assertEquals(expected.left, actual.left, 0f)
        assertEquals(expected.top, actual.top, 0f)
        assertEquals(expected.right, actual.right, 0f)
        assertEquals(expected.bottom, actual.bottom, 0f)
    }

    @Test fun enabledRecessIsStrictlyInsideTheGenericSheet() {
        val out = RectF()
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, out))
        assertTrue(out.left > sheet.left && out.top > sheet.top)
        assertTrue(out.right < sheet.right && out.bottom < sheet.bottom)
        assertEquals(JarvisProtectedDepthSurfaces.RECESS_DP * density, out.left - sheet.left, 1e-3f)
        assertFalse("EXP03 plate is not the generic backing rectangle",
            out.left == sheet.left && out.top == sheet.top && out.right == sheet.right && out.bottom == sheet.bottom)
    }

    @Test fun invalidInputLeavesTheCallerRectUntouched() {
        val out = rect(1f, 2f, 3f, 4f)
        assertFalse(JarvisProtectedDepthSurfaces.recess(RectF(), density, out))
        assertFalse(JarvisProtectedDepthSurfaces.recess(sheet, 0f, out))
        assertFalse(JarvisProtectedDepthSurfaces.recess(sheet, Float.NaN, out))
        assertSameRect(rect(1f, 2f, 3f, 4f), out)
    }

    @Test fun recessReusesTheCallerRectWithoutAllocatingANewOne() {
        val reused = RectF()
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, reused))
        val first = rect(reused.left, reused.top, reused.right, reused.bottom)
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, reused))
        assertSameRect(first, reused)
    }

    @Test fun plateAlphaFollowsTheSheetAndRejectsNonFiniteValues() {
        assertEquals(0f, JarvisProtectedDepthSurfaces.plateAlpha(0f), 0f)
        assertTrue(JarvisProtectedDepthSurfaces.plateAlpha(1f) in 0f..1f)
        assertTrue(JarvisProtectedDepthSurfaces.plateAlpha(1f) < 1f)
        assertEquals(0f, JarvisProtectedDepthSurfaces.plateAlpha(Float.NaN), 0f)
    }
}
