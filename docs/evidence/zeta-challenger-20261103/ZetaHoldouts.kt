package at.heinz.deviceai.ui

import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.InvocationTargetException
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.max
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot

object ZetaHoldouts {
    private val cases = listOf(287f to 611f,320f to 431f,393f to 873f,521f to 333f,599f to 981f,601f to 481f,713f to 897f,911f to 521f,1279f to 799f,713f to 1601f)
    private val actions = Slot.values().filter { it.name.startsWith("LABEL_") }
    private val headers = listOf(Slot.SEARCH,Slot.NOTIFICATIONS,Slot.PROFILE)
    private var failed = 0
    private var checked = 0
    private fun check(ok:Boolean, case:String, criterion:String, detail:String) {
        checked++; if(!ok) failed++
        println("${if(ok) "PASS" else "FAIL"}\t$case\t$criterion\t${detail.replace('\t',' ').replace('\n',' ')}")
    }
    private fun layout(w:Float,h:Float,font:Float):ContinuumHomeLayout.Layout {
        val name = if (ContinuumHomeLayout.isWide(w,h)) "wide" else "phone"
        val method = ContinuumHomeLayout::class.java.methods.filter { it.name == name }.maxBy { it.parameterCount }
        val args = if(name=="phone") listOf(w,font) else listOf(w,h,font)
        return method.invoke(ContinuumHomeLayout,*args.take(method.parameterCount).toTypedArray()) as ContinuumHomeLayout.Layout
    }
    private fun overlap(a:ContinuumHomeLayout.Box,b:ContinuumHomeLayout.Box):Boolean = min(a.right,b.right)-max(a.left,b.left)>.01f && min(a.bottom,b.bottom)-max(a.top,b.top)>.01f
    @JvmStatic fun main(args:Array<String>) {
        val out=File(args[0]);out.mkdirs()
        val changed=ContinuumDashboardState(model=ContinuumDashboardState.ModelStatus(name="ZETA-CHECK-MODEL",stateLabel="ZETA-NOT-READY",online=false),core=ContinuumDashboardState.CoreStatus(connected="ZETA-CONNECT",thinking="ZETA-THINK",learning="ZETA-LEARN",analysing="ZETA-ANALYSE",developing="ZETA-DEVELOP",evolving="ZETA-EVOLVE"))
        val expected=listOf("ZETA-CONNECT","ZETA-LEARN","ZETA-DEVELOP","ZETA-THINK","ZETA-ANALYSE","ZETA-EVOLVE")
        for((w,h) in cases) for(font in listOf(1f,1.3f,1.8f,2f)) {
            val id="${w.toInt()}x${h.toInt()}@${font}"
            val l=layout(w,h,font)
            check(l.contentHeight.isFinite() && l.contentHeight>0f,id,"contentFinite",l.contentHeight.toString())
            for((slot,b) in l.boxes) {
                check(listOf(b.left,b.top,b.right,b.bottom).all { it.isFinite() } && b.width>0 && b.height>0 && b.left>=-.01f && b.right<=w+.01f && b.top>=-.01f && (slot==Slot.NAV || b.bottom<=l.contentHeight+.01f),id,"bounds", "$slot=$b")
            }
            val hero=l.boxes.getValue(Slot.HERO)
            for(slot in headers+actions) {
                val b=l.boxes[slot]
                check(b!=null && b.width>=47.99f && b.height>=47.99f,id,"minimumTouch","$slot=$b")
                if(slot in actions && b!=null) check(b.top>=hero.bottom,id,"actionsBelowHero","$slot top=${b.top}, hero bottom=${hero.bottom}")
            }
            val interactive=l.boxes.filterKeys { it !in listOf(Slot.HERO,Slot.NAV,Slot.RAIL) }.entries.toList()
            for(i in interactive.indices) for(j in i+1 until interactive.size) check(!overlap(interactive[i].value,interactive[j].value),id,"interactiveNonOverlap","${interactive[i].key}/${interactive[j].key}")
            val elements=ContinuumHomeScreen.elements(l,font)
            check(actions.all { it in elements },id,"actionIdentity","${actions.map { elements[it]?.javaClass?.simpleName }}")
            for((i,s) in actions.withIndex()) check(elements.getValue(s).describe(changed).contains(expected[i]),id,"realCoreState","$s ${elements.getValue(s).describe(changed)}")
            val model=elements.getValue(Slot.MODEL)
            check(model.describe(changed).contains("ZETA-CHECK-MODEL") && model.describe(changed).contains("ZETA-NOT-READY"),id,"realModelState",model.describe(changed))
            check(model.describe(ContinuumDashboardState()).contains("Offline") && model.describe(ContinuumDashboardState()).contains("—"),id,"unknownModel",model.describe(ContinuumDashboardState()))
            val topCards=listOf(Slot.MODEL,Slot.LEARNING,Slot.STORAGE).mapNotNull { l.boxes[it] }
            println("INFO\t$id\tcardPositions\t${topCards.joinToString()}")
            for(slot in actions) {
                val b=l.boxes.getValue(slot)
                val img=BufferedImage(max(1,b.width.toInt()+1),max(1,b.height.toInt()+1),BufferedImage.TYPE_INT_ARGB)
                val painter=Java2DContinuumPainter(img.createGraphics(),File("app/src/main/assets"))
                val texts=mutableListOf<Java2DContinuumPainter.TextBox>();painter.textBoxes=texts
                val sizes=mutableListOf<Pair<String,Float>>()
                val recording=object:ContinuumPainter by painter {
                    override fun drawText(text:String,x:Float,y:Float,style:TextStyle,fill:Fill,align:TextAlign,alpha:Float) {
                        sizes+=text to style.size*l.scale
                        painter.drawText(text,x,y,style,fill,align,alpha)
                    }
                }
                painter.scale(l.scale,l.scale)
                elements.getValue(slot).draw(recording,b.width/l.scale,b.height/l.scale,changed)
                for(t in texts) check(t.left>=-.5 && t.right<=b.width+.5 && t.top>=-.5 && t.bottom<=b.height+.5,id,"actionTextBounds","$slot '${t.text}' [${t.left},${t.top},${t.right},${t.bottom}] in ${b.width}x${b.height}")
                for(i in texts.indices) for(j in i+1 until texts.size) {
                    val a=texts[i];val c=texts[j]
                    check(min(a.right,c.right)-max(a.left,c.left)<=.1 || min(a.bottom,c.bottom)-max(a.top,c.top)<=.1,id,"actionTextNonOverlap","$slot '${a.text}'/'${c.text}'")
                }
                if(font==1f) sizes.forEachIndexed { i,p -> check(p.second >= if(i==sizes.lastIndex) 10f else 12f,id,"readabilityFloor","$slot '${p.first}' ${p.second}") }
                if(w==287f && font in listOf(1f,2f) && slot==Slot.LABEL_DEVELOPING) ImageIO.write(img,"png",File(out,"$id-$slot.png"))
            }
        }
        for(w in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f)) {
            try { val l=layout(w,700f,1f); check(l.contentHeight.isFinite() && l.boxes.values.all { listOf(it.left,it.top,it.right,it.bottom).all(Float::isFinite) },"invalidWidth=$w","rejectOrFinite","returned $l") }
            catch(e:InvocationTargetException) { check(e.targetException is IllegalArgumentException,"invalidWidth=$w","rejectOrFinite",e.targetException.toString()) }
        }
        for(h in listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f)) {
            val m=ContinuumHomeLayout::class.java.methods.filter { it.name=="wide" }.maxBy { it.parameterCount }
            try { val l=m.invoke(ContinuumHomeLayout,*listOf(713f,h,1f).take(m.parameterCount).toTypedArray()) as ContinuumHomeLayout.Layout;check(l.contentHeight.isFinite() && l.boxes.values.all { listOf(it.left,it.top,it.right,it.bottom).all(Float::isFinite) },"invalidHeight=$h","rejectOrFinite",l.contentHeight.toString()) }
            catch(e:InvocationTargetException) { check(e.targetException is IllegalArgumentException,"invalidHeight=$h","rejectOrFinite",e.targetException.toString()) }
        }
        val first=layout(393f,873f,1f);layout(713f,897f,1f);layout(521f,333f,1f);check(first==layout(393f,873f,1f),"resizeSequence","deterministicReturn","393→713→521→393")
        println("SUMMARY\tchecked=$checked\tfailed=$failed")
    }
}
