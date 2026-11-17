package at.heinz.deviceai.ui

import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

/** Public production event API; synthetic events are never presented as actual worker execution. */
class IndependentTaskEffectsTest {
    private fun fresh()=ContinuumTaskEffects().apply {setScope("scope-A")}
    private fun start(id:String,worker:Boolean=false)=ContinuumTaskEffects.Event.Started(id,
        if(worker)ContinuumTaskEffects.Kind.WORKER else ContinuumTaskEffects.Kind.TOOL)
    private fun accept(e:ContinuumTaskEffects,v:ContinuumTaskEffects.Event,at:Long,now:Long=at,scope:String="scope-A")=e.accept(scope,v,at,now)
    private fun calls(e:ContinuumTaskEffects,now:Long,w:Float=412f,h:Float=307f):List<String> {
        val names=mutableListOf<String>()
        val painter=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(ContinuumPainter::class.java)) {_,m,args ->
            if(m.name.startsWith("draw")||m.name.startsWith("fill")||m.name.startsWith("stroke"))names+=m.name
            args.orEmpty().forEach {arg ->
                if(arg is Float)assertTrue("Non-finite paint argument",arg.isFinite())
                if(arg is VectorPath)for(op in arg.ops)when(op) {
                    is VectorPath.Op.Move -> assertTrue(op.x.isFinite()&&op.y.isFinite())
                    is VectorPath.Op.Line -> assertTrue(op.x.isFinite()&&op.y.isFinite())
                    else -> Unit
                }
            }
            when(m.returnType) {Float::class.javaPrimitiveType->0f;Int::class.javaPrimitiveType->0;else->null}
        } as ContinuumPainter
        e.draw(painter,w,h,now)
        assertFalse("Opaque IDs are not user-facing content",names.contains("drawText"))
        return names
    }
    @Test fun exactFreshnessBoundaryUsesOriginalTimeNotDeliveryTime() {
        val e=fresh();assertTrue(accept(e,start("boundary"),250,1000))
        assertTrue(calls(e,1100).isNotEmpty())
        assertTrue("Arrival must expire900ms after original event, not delivery",calls(e,1150).isEmpty())
        assertFalse(accept(e,start("too-old"),500,1251))
    }
    @Test fun invalidFutureAndForeignEventsCannotConsumeTheRealId() {
        val e=fresh()
        assertFalse(accept(e,start("real"),Long.MAX_VALUE,1000))
        assertFalse(accept(e,start("real"),1000,1000,"scope-other"))
        assertFalse(accept(e,start("real"),-1,1000))
        assertTrue(accept(e,start("real"),1001));assertTrue(calls(e,1250).isNotEmpty())
    }
    @Test fun invalidScopeClearsOutstandingWorkAndCannotReviveOldScope() {
        for(invalid in listOf("","space id","ü","x".repeat(97))) {
            val e=fresh();assertTrue(accept(e,start("visible"),1000))
            e.setScope(invalid)
            assertTrue(calls(e,1200).isEmpty());assertFalse(accept(e,start("later"),1200))
            e.setScope("scope-A");assertTrue(accept(e,start("new-scope-id"),1300))
        }
    }
    @Test fun unknownOrOutOfOrderTerminalCannotFreeAnotherTasksSlot() {
        val e=fresh();repeat(4) {assertTrue(accept(e,start("id-$it"),1000))}
        assertTrue(accept(e,ContinuumTaskEffects.Event.Read("id-0"),1100))
        assertFalse(accept(e,ContinuumTaskEffects.Event.Finished("unknown"),1101))
        assertFalse(accept(e,ContinuumTaskEffects.Event.Finished("id-0"),1050,1102))
        assertFalse(accept(e,start("overflow"),1103))
        assertTrue(accept(e,ContinuumTaskEffects.Event.Finished("id-0"),1104))
        assertTrue(accept(e,start("replacement"),1105))
        assertFalse(accept(e,ContinuumTaskEffects.Event.Finished("id-0"),1106))
    }
    @Test fun workerCannotAcquireReadPageThroughToolIdReplayOrScopeChange() {
        val e=fresh();assertTrue(accept(e,start("worker",true),1000))
        assertFalse(accept(e,ContinuumTaskEffects.Event.Read("worker"),1010))
        assertFalse(accept(e,start("worker"),1020))
        assertTrue(accept(e,start("tool"),1030));assertTrue(accept(e,ContinuumTaskEffects.Event.Read("tool"),1040))
        e.setScope("scope-B")
        assertFalse(accept(e,ContinuumTaskEffects.Event.Read("tool"),1050,scope="scope-B"))
        assertTrue(calls(e,1200).isEmpty())
    }
    @Test fun oldTerminalRetiresOnlyMatchingSlotWithoutInventingFreshDeparture() {
        val e=fresh();repeat(4) {assertTrue(accept(e,start("id-$it"),1000))}
        assertTrue(calls(e,2000).isEmpty())
        assertFalse(accept(e,ContinuumTaskEffects.Event.Finished("id-2"),1100,2001))
        assertTrue(calls(e,2002).isEmpty())
        assertTrue(accept(e,start("new"),2003));assertFalse(accept(e,start("still-full"),2004))
        assertFalse(accept(e,start("id-2"),2005))
    }
    @Test fun finiteExtremeAndInvalidSizesNeverEmitNonFiniteGeometry() {
        for((w,h) in listOf(1f to 1f,.001f to .001f,Float.MAX_VALUE to Float.MAX_VALUE,412f to 307f)) {
            val e=fresh();assertTrue(accept(e,start("tiny"),1000));assertTrue(accept(e,ContinuumTaskEffects.Event.Read("tiny"),1010))
            assertTrue(calls(e,1200,w,h).isNotEmpty())
        }
        for(w in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f)) {
            val e=fresh();assertTrue(accept(e,start("invalid-size"),1000));assertTrue(calls(e,1200,w,307f).isEmpty())
        }
    }
    @Test fun boundedBurstAndMaximumLongClockDoNotCreateUnboundedPaint() {
        val e=fresh()
        repeat(24) {i->val at=1000L+i*5;val id="burst-$i"
            assertTrue(accept(e,start(id),at));assertTrue(accept(e,ContinuumTaskEffects.Event.Read(id),at+1))
            assertTrue(accept(e,ContinuumTaskEffects.Event.Finished(id),at+2))
        }
        assertTrue(calls(e,1400).size in 1..96)
        assertTrue(calls(e,Long.MAX_VALUE-1).isEmpty())
        assertTrue(accept(e,start("last"),Long.MAX_VALUE-1));assertTrue(accept(e,ContinuumTaskEffects.Event.Finished("last"),Long.MAX_VALUE))
        assertTrue(calls(e,Long.MAX_VALUE).size<=96)
    }
}
