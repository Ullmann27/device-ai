package at.heinz.deviceai.ui
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.min
import kotlin.math.max
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
/** Supplementary after source inspection: all metric-card painted line bounds, not part of original action-only score. */
object ZetaCardAudit {
 @JvmStatic fun main(args:Array<String>) {
  var overlaps=0
  for((w,h) in listOf(287f to 611f,713f to 897f,1279f to 799f)) for(font in listOf(1f,1.3f,2f)) {
   val name=if(ContinuumHomeLayout.isWide(w,h)) "wide" else "phone"
   val m=ContinuumHomeLayout::class.java.methods.filter { it.name==name }.maxBy { it.parameterCount }
   val values=if(name=="phone") listOf(w,font) else listOf(w,h,font)
   val l=m.invoke(ContinuumHomeLayout,*values.take(m.parameterCount).toTypedArray()) as ContinuumHomeLayout.Layout
   val elements=ContinuumHomeScreen.elements(l,font)
   for(slot in listOf(Slot.MODEL,Slot.LEARNING,Slot.STORAGE,Slot.DEVELOPMENT,Slot.WORKERS,Slot.SYSTEM_STATUS,Slot.SYSTEM_ACTIVITY)) {
    val b=l[slot] ?: continue;val element=elements[slot]?:continue
    val img=BufferedImage(b.width.toInt()+1,b.height.toInt()+1,BufferedImage.TYPE_INT_ARGB)
    val painter=Java2DContinuumPainter(img.createGraphics(),File("app/src/main/assets"))
    val texts=mutableListOf<Java2DContinuumPainter.TextBox>();painter.textBoxes=texts
    painter.scale(l.scale,l.scale);element.draw(painter,b.width/l.scale,b.height/l.scale,ContinuumDashboardState())
    for(i in texts.indices) for(j in i+1 until texts.size) {
     val a=texts[i];val c=texts[j];val ox=min(a.right,c.right)-max(a.left,c.left);val oy=min(a.bottom,c.bottom)-max(a.top,c.top)
     if(ox>.1 && oy>.1) { overlaps++;println("OVERLAP\t${w}x${h}@$font\t$slot\t${a.text} / ${c.text}\t${ox}x${oy}\t[${a.left},${a.top},${a.right},${a.bottom}] / [${c.left},${c.top},${c.right},${c.bottom}]") }
    }
   }
  }
  println("SUMMARY supplementary metric-text linebox overlaps=$overlaps")
 }
}
