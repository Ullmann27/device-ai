package at.heinz.deviceai.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import at.heinz.deviceai.ContinuumHologramView
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZetaNativeHoldouts {
    @Test fun frozenWidthFontCasesKeepRealStateAndActionCallbacks() {
        val root=File(System.getProperty("zeta.output"));root.mkdirs()
        for((w,h,font) in listOf(Triple(287,611,2f),Triple(713,897,1f),Triple(521,333,1f))) {
            RuntimeEnvironment.setQualifiers("w${w}dp-h${h}dp-mdpi")
            val app=RuntimeEnvironment.getApplication()
            val config=Configuration(app.resources.configuration).apply { fontScale=font }
            val ctx=app.createConfigurationContext(config)
            val calls=mutableListOf<Slot>()
            val hero=ContinuumHologramView(ctx);hero.setAnimationsEnabled(false)
            val home=ContinuumHomeView(ctx,hero) { slot,_,_-> calls+=slot }
            val wide=ContinuumHomeLayout.isWide(w.toFloat(),h.toFloat())
            val rail=if(wide && !ContinuumHomeLayout.isPortraitFold(w.toFloat(),h.toFloat())) ContinuumHomeLayout.railWidthDp(w.toFloat()) else 0f
            home.configure(w.toFloat(),wide,railOffset=rail,windowHeightDp=h.toFloat())
            home.state=ContinuumDashboardState(model=ContinuumDashboardState.ModelStatus(name="ZETA-NATIVE-MODEL",stateLabel="ZETA-OFFLINE",online=false),core=ContinuumDashboardState.CoreStatus(thinking="ZETA-NATIVE-THINK"))
            val available=(w-rail).toInt()
            home.measure(View.MeasureSpec.makeMeasureSpec(available,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
            home.layout(0,0,available,home.measuredHeight)
            val elements=(0 until home.childCount).map { home.getChildAt(it) }.filterIsInstance<ContinuumHomeView.ElementView>()
            for(slot in Slot.values().filter { it.name.startsWith("LABEL_") }) {
                val child=elements.single { it.slot==slot }
                assertTrue(child.isClickable);assertTrue(child.isFocusable)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES,child.importantForAccessibility)
                assertTrue(child.performClick());assertEquals(slot,calls.last())
                println("NATIVE $w x $h font=$font $slot bounds=${child.left},${child.top},${child.right},${child.bottom} spoken=${child.contentDescription}")
            }
            assertTrue(elements.single { it.slot==Slot.MODEL }.contentDescription.contains("ZETA-NATIVE-MODEL"))
            assertTrue(elements.single { it.slot==Slot.LABEL_THINKING }.contentDescription.contains("ZETA-NATIVE-THINK"))
            home.state=ContinuumDashboardState()
            assertTrue(elements.single { it.slot==Slot.MODEL }.contentDescription.contains("Offline"))
            val bitmap=Bitmap.createBitmap(available,home.measuredHeight,Bitmap.Config.ARGB_8888)
            home.draw(Canvas(bitmap))
            File(root,"${w}x${h}-font$font.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
}
