package at.heinz.deviceai.ui

import kotlin.math.PI
import kotlin.math.sin

/** Presentation of explicit actual events only. Stores opaque IDs, never content or claimed outcomes. */
class ContinuumTaskEffects {
    enum class Kind { TOOL, WORKER }
    sealed class Event(open val id:String) {
        data class Started(override val id:String,val kind:Kind):Event(id)
        data class Read(override val id:String):Event(id)
        data class Finished(override val id:String):Event(id)
    }
    private data class Active(val kind:Kind,val at:Long,val slot:Int,var read:Boolean=false,var last:Long=at)
    private data class Pulse(val id:String,val type:Int,val kind:Kind,val at:Long,val slot:Int,val duration:Long)
    private var scope:String?=null
    private val active=linkedMapOf<String,Active>()
    private val seen=linkedSetOf<String>()
    private val pulses=ArrayList<Pulse>(12)
    private var lastNow=-1L
    private val cyan=Fill.Solid(0xff54d8ff.toInt())
    private val violet=Fill.Solid(0xffae9aff.toInt())
    private val backing=Fill.Solid(0xff050817.toInt()) // Existing app background palette.
    private val page=VectorPath()

    fun setScope(value:String?) {
        if(value!=null && validId(value) && value==scope)return
        clear()
        scope=value?.takeIf(::validId)
    }
    fun clear() { scope=null;active.clear();seen.clear();pulses.clear();lastNow=-1 }

    /** Both times use the caller's same monotonic clock. No missing-time or future-time default. */
    fun accept(scope:String,event:Event,occurredAtMillis:Long,nowMillis:Long):Boolean {
        if(this.scope!=scope || !validId(event.id) || nowMillis<0 || nowMillis<lastNow ||
            occurredAtMillis<0 || occurredAtMillis>nowMillis)return false
        lastNow=nowMillis
        expire(nowMillis)
        val current=active[event.id]
        if(current!=null && occurredAtMillis<current.last)return false
        val fresh=nowMillis-occurredAtMillis<=750
        when(event) {
            is Event.Started -> {
                if(!fresh || event.id in seen || active.size>=4 || seen.size>=32)return false
                val slot=(0..3).first { n -> active.values.none { it.slot==n } }
                seen.add(event.id);active[event.id]=Active(event.kind,occurredAtMillis,slot)
                pulse(Pulse(event.id,0,event.kind,occurredAtMillis,slot,900))
            }
            is Event.Read -> {
                if(!fresh || current==null || current.kind!=Kind.TOOL || current.read)return false
                current.read=true;current.last=occurredAtMillis
                pulse(Pulse(event.id,1,current.kind,occurredAtMillis,current.slot,1400))
            }
            is Event.Finished -> {
                if(current==null)return false
                active.remove(event.id)
                if(!fresh)return false // Clean the matching reference; do not replay a late departure.
                pulse(Pulse(event.id,2,current.kind,occurredAtMillis,current.slot,650))
            }
        }
        return true
    }
    private fun pulse(p:Pulse) { if(pulses.size==12)pulses.removeAt(0);pulses.add(p) }
    private fun expire(now:Long) { pulses.removeAll { now-it.at>=it.duration } }

    /** Driven by the existing visible View clock. No timers, progress percentages or success marks. */
    fun draw(painter:ContinuumPainter,width:Float,height:Float,nowMillis:Long) {
        if(scope==null || !width.isFinite() || !height.isFinite() || width<=0 || height<=0 || nowMillis<lastNow)return
        lastNow=nowMillis;expire(nowMillis)
        val hubX=width*.5f;val hubY=height*.61f
        // Nominal 24dp icon stays legible in the real 88dp compact chat hero.
        val size=minOf(24f,width*.10f,height*.27f)
        val pad=size*.9f
        for(pulse in pulses) {
            val t=((nowMillis-pulse.at).toFloat()/pulse.duration).coerceIn(0f,1f)
            val fade=sin(PI*t).toFloat().coerceAtLeast(0f)*.88f
            if(fade<=0f)continue
            val ease=t*t*(3-2*t)
            val side=if(pulse.slot%2==0) -1 else 1
            val fill=if(pulse.kind==Kind.WORKER)violet else cyan
            if(pulse.type==2) {
                val x=hubX+side*width*.33f*ease
                val y=hubY-height*.13f*ease
                painter.drawLine(hubX,hubY,x,y,.9f,fill,fade*.35f)
                painter.fillCircle(x,y,size*.17f,fill,fade)
                continue
            }
            val x:Float;val y:Float
            if(pulse.type==1) {
                x=(width*(.92f-.16f*ease)).coerceIn(pad,width-pad)
                y=(height*(.22f+.44f*ease)-sin(PI*t).toFloat()*height*.08f).coerceIn(pad,height-pad)
            } else {
                x=(hubX+side*width*(.43f-.08f*ease)).coerceIn(pad,width-pad)
                y=(height*(.22f+.08f*pulse.slot+.10f*ease)).coerceIn(pad,height-pad)
            }
            painter.drawLine(x,y,hubX,hubY,5f,fill,fade*.16f)
            painter.drawLine(x,y,hubX,hubY,1.1f,fill,fade*.65f)
            painter.fillCircle(hubX,hubY,2.2f,fill,fade*.65f)
            painter.fillCircle(x,y,size*.78f,backing,fade*.95f)
            painter.strokeCircle(x,y,size*.80f,3f,fill,fade*.20f)
            painter.strokeCircle(x,y,size*.73f,1f,fill,fade*.8f)
            if(pulse.type==1) {
                // Generic page; not a PDF, account message, filename, or claim about contents.
                page.reset().moveTo(x-size*.35f,y-size*.5f).lineTo(x+size*.12f,y-size*.5f)
                    .lineTo(x+size*.35f,y-size*.25f).lineTo(x+size*.35f,y+size*.5f)
                    .lineTo(x-size*.35f,y+size*.5f).close()
                painter.strokePath(page,1.3f,fill,fade)
                painter.drawLine(x+size*.12f,y-size*.5f,x+size*.12f,y-size*.25f,1f,fill,fade)
                painter.drawLine(x+size*.12f,y-size*.25f,x+size*.35f,y-size*.25f,1f,fill,fade)
                painter.drawLine(x-size*.19f,y+size*.04f,x+size*.18f,y+size*.04f,1f,fill,fade)
                painter.drawLine(x-size*.19f,y+size*.24f,x+size*.1f,y+size*.24f,1f,fill,fade)
            } else if(pulse.kind==Kind.WORKER) {
                painter.strokeCircle(x,y-size*.25f,size*.20f,1.3f,fill,fade)
                page.reset().moveTo(x-size*.4f,y+size*.42f)
                    .quadTo(x-size*.4f,y+size*.05f,x,y+size*.05f)
                    .quadTo(x+size*.4f,y+size*.05f,x+size*.4f,y+size*.42f).close()
                painter.strokePath(page,1.3f,fill,fade)
            } else {
                painter.strokeRoundRect(x-size*.45f,y-size*.4f,x+size*.45f,y+size*.4f,size*.12f,1.3f,fill,fade)
                painter.drawLine(x-size*.25f,y-size*.13f,x+size*.2f,y-size*.13f,1.2f,fill,fade)
                painter.drawLine(x-size*.25f,y+size*.13f,x+size*.08f,y+size*.13f,1.2f,fill,fade)
            }
        }
    }
    private fun validId(id:String)=id.length in 1..96 && id.all { it.isLetterOrDigit() && it.code<128 || it in "._:-" }
}
