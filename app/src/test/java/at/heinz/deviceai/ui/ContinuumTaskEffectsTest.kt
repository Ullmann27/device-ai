package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Actual pure painter contract; reflection makes missing-baseline APIs assertion failures. */
class ContinuumTaskEffectsTest {
    private fun type():Class<*> = runCatching { Class.forName("at.heinz.deviceai.ui.ContinuumTaskEffects") }.getOrNull()
        .also { assertNotNull("Actual-work effects are missing",it) }!!
    private fun effect()=type().getConstructor().newInstance()
    private fun scope(e:Any,s:String?="turn-1") { type().getMethod("setScope",String::class.java).invoke(e,s) }
    private fun started(id:String="job-1",worker:Boolean=false):Any {
        val k=Class.forName(type().name+"\$Kind").enumConstants.first { (it as Enum<*>).name==if(worker) "WORKER" else "TOOL" }
        return Class.forName(type().name+"\$Event\$Started").constructors.single().newInstance(id,k)
    }
    private fun event(name:String,id:String="job-1")=Class.forName(type().name+"\$Event\$$name").getConstructor(String::class.java).newInstance(id)
    private fun accept(e:Any,event:Any,at:Long=1000,now:Long=at,s:String="turn-1"):Boolean =
        type().methods.single { it.name=="accept" }.invoke(e,s,event,at,now) as Boolean
    private fun draw(e:Any,now:Long):List<String> {
        val commands=mutableListOf<String>()
        val p=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(ContinuumPainter::class.java)) { _,m,args ->
            if(m.name.startsWith("draw")||m.name.startsWith("fill")||m.name.startsWith("stroke")) {
                commands+=m.name
                args?.filterIsInstance<Float>()?.forEach { assertTrue("Non-finite painter coordinate",it.isFinite()) }
            }
            when(m.returnType) { Float::class.javaPrimitiveType -> 1f; else -> null }
        } as ContinuumPainter
        type().getMethod("draw",ContinuumPainter::class.java,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Long::class.javaPrimitiveType)
            .invoke(e,p,412f,307f,now)
        return commands
    }
    @Test fun idleHasNoWorkGlyphAndActualStartDrawsThenExpires() {
        val e=effect();scope(e)
        assertTrue(draw(e,1000).isEmpty())
        assertTrue(accept(e,started(),1100))
        assertTrue(draw(e,1450).isNotEmpty())
        assertTrue("No indefinite work/progress animation",draw(e,2600).isEmpty())
    }
    @Test fun staleFutureAndUnknownEventsCannotInventActivity() {
        val e=effect();scope(e)
        assertFalse(accept(e,started(),1001,1000))
        assertFalse(accept(e,started(),100,1000))
        assertFalse(accept(e,event("Read"),1000))
        assertFalse(accept(e,event("Finished"),1000))
        assertTrue(draw(e,1300).isEmpty())
        assertTrue("A future event must not poison later actual timestamps",accept(e,started(),1400))
    }
    @Test fun scopeEndAndDuplicateStartNeverReplayArrivals() {
        val e=effect();scope(e)
        assertTrue(accept(e,started()))
        assertFalse(accept(e,started(),1001))
        assertTrue(accept(e,event("Finished"),1010))
        assertFalse(accept(e,started(),1020))
        scope(e) // A repeated policy refresh cannot reset replay protection.
        assertFalse(accept(e,started(),1030))
        scope(e,null)
        assertFalse(accept(e,started(),1040))
        assertTrue(draw(e,1200).isEmpty())
        scope(e,"turn-2")
        assertFalse(accept(e,started(),1300,s="turn-1"))
        assertTrue(accept(e,started(),1300,s="turn-2"))
    }
    @Test fun readFlightsRequireMatchingToolAndNeverRenderContentOrCounts() {
        val e=effect();scope(e)
        assertTrue(accept(e,started("worker",true)))
        assertFalse(accept(e,event("Read","worker"),1010))
        assertTrue(accept(e,started("read-call"),1020))
        assertTrue(accept(e,event("Read","read-call"),1030))
        assertFalse(accept(e,event("Read","read-call"),1040))
        val commands=draw(e,1400)
        assertTrue(commands.isNotEmpty())
        assertFalse("Opaque IDs/content are never displayed",commands.contains("drawText"))
    }
    @Test fun boundedCapacityAndStaleFinishReleaseOnlyMatchingSlot() {
        val e=effect();scope(e)
        repeat(4) { assertTrue(accept(e,started("call-$it"))) }
        assertFalse(accept(e,started("overflow"),1001))
        assertFalse(accept(e,event("Finished","call-0"),1100,2000))
        assertTrue(accept(e,started("replacement"),2010))
        assertFalse(accept(e,started("still-full"),2020))
        assertTrue(draw(e,2300).size<=120)
    }
    @Test fun boundedReplayHistoryFailsClosedUntilNewScope() {
        val e=effect();scope(e)
        repeat(32) { index ->
            val at=1000L+index*2000
            assertTrue(accept(e,started("call-$index"),at))
            assertTrue(accept(e,event("Finished","call-$index"),at+10))
        }
        assertFalse(accept(e,started("overflow"),66000))
        assertFalse(accept(e,started("call-0"),66001))
    }
    @Test fun toolGlyphRemainsLegibleAtPhoneFoldAndCompactHeroSizes() {
        for((w,h) in listOf(412f to 307f,410f to 291f,412f to 88f,412f to 160f)) {
            val e=effect();scope(e);assertTrue(accept(e,started()))
            var glyphWidth=0f;var glyphHeight=0f
            val p=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(ContinuumPainter::class.java)) { _,m,args ->
                if(m.name=="strokeRoundRect") {
                    glyphWidth=(args!![2] as Float)-(args[0] as Float)
                    glyphHeight=(args[3] as Float)-(args[1] as Float)
                }
                when(m.returnType) { Float::class.javaPrimitiveType -> 1f; else -> null }
            } as ContinuumPainter
            type().getMethod("draw",ContinuumPainter::class.java,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Long::class.javaPrimitiveType)
                .invoke(e,p,w,h,1300L)
            assertTrue("Legible tool glyph width at ${w}x$h: $glyphWidth",glyphWidth in 20f..26f)
            assertTrue("Legible tool glyph height at ${w}x$h: $glyphHeight",glyphHeight in 18f..26f)
        }
    }
}
