package at.heinz.deviceai.ui

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisProtectedDepthSurfacesTest {
    private val sheet = RectF(100f, 200f, 500f, 800f)
    private val density = 2.5f

    @Test fun enabledRecessIsStrictlyInsideTheGenericSheet() {
        val out = RectF()
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, out))
        assertTrue(out.left > sheet.left && out.top > sheet.top)
        assertTrue(out.right < sheet.right && out.bottom < sheet.bottom)
        assertEquals(JarvisProtectedDepthSurfaces.RECESS_DP * density, out.left - sheet.left, 1e-3f)
        assertNotEquals("EXP03 plate is not the generic backing rectangle", sheet, out)
    }

    @Test fun invalidInputLeavesTheCallerRectUntouched() {
        val out = RectF(1f, 2f, 3f, 4f)
        assertFalse(JarvisProtectedDepthSurfaces.recess(RectF(), density, out))
        assertFalse(JarvisProtectedDepthSurfaces.recess(sheet, 0f, out))
        assertFalse(JarvisProtectedDepthSurfaces.recess(sheet, Float.NaN, out))
        assertEquals(RectF(1f, 2f, 3f, 4f), out)
    }

    @Test fun recessReusesTheCallerRectWithoutAllocatingANewOne() {
        val reused = RectF()
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, reused))
        val first = RectF(reused)
        assertTrue(JarvisProtectedDepthSurfaces.recess(sheet, density, reused))
        assertEquals(first, reused)
    }

    @Test fun plateAlphaFollowsTheSheetAndRejectsNonFiniteValues() {
        assertEquals(0f, JarvisProtectedDepthSurfaces.plateAlpha(0f), 0f)
        assertTrue(JarvisProtectedDepthSurfaces.plateAlpha(1f) in 0f..1f)
        assertTrue(JarvisProtectedDepthSurfaces.plateAlpha(1f) < 1f)
        assertEquals(0f, JarvisProtectedDepthSurfaces.plateAlpha(Float.NaN), 0f)
    }
}
