package at.heinz.deviceai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Preference migration, without rendering/capture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContinuumAvatarAppearanceUpgradeUiTest {
    private lateinit var activity: Activity
    private val preferences get() = activity.getSharedPreferences("continuum-avatar-appearance", Context.MODE_PRIVATE)

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).get()
        preferences.edit().clear().commit()
        shadowOf(activity.application).denyPermissions(Manifest.permission.CAMERA)
    }

    @After fun cleanup() { preferences.edit().clear().commit() }

    private fun saved(head: Boolean? = null, head3d: Boolean? = null) {
        preferences.edit().apply {
            if (head != null) putBoolean("native-head", head)
            if (head3d != null) putBoolean("native-head-3d", head3d)
        }.commit()
    }

    private fun expect(head: Boolean, head3d: Boolean) {
        val view = ContinuumHologramView(activity)
        assertEquals(head, view.isNativeHeadEnabled())
        assertEquals(head3d, view.isNative3dEnabled())
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.CAMERA))
        assertFalse(view.isAttachedToWindow)
    }

    @Test fun freshInstallUsesRequested3dHeadWithoutCameraPermission() = expect(true, true)

    @Test fun legacyEnabledHeadUses3dAfterUpgrade() {
        saved(head = true)
        expect(true, true)
    }

    @Test fun legacyExplicitOrbRemainsOrb() {
        saved(head = false)
        expect(false, false)
    }

    @Test fun explicit2dChoiceSurvivesUpgrade() {
        saved(head = true, head3d = false)
        expect(true, false)
    }

    @Test fun explicitOrbWith3dKeyRemainsOrb() {
        saved(head = false, head3d = false)
        expect(false, false)
    }

    @Test fun explicit3dOffWithoutLegacyKeyStaysOff() {
        saved(head3d = false)
        expect(false, false)
    }

    @Test fun explicit3dChoiceRemains3d() {
        saved(head3d = true)
        expect(true, true)
    }

    @Test fun changingUpgradedHeadTo2dSurvivesRecreation() {
        saved(head = true)
        val view = ContinuumHologramView(activity)
        assertTrue(view.isNative3dEnabled())
        view.setNativeHeadEnabled(true)
        expect(true, false)
    }
}
