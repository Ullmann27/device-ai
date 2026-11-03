package at.heinz.deviceai.ui
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import at.heinz.deviceai.ui.ContinuumHomeLayout.Slot
/** Supplementary after the broad card linebox audit: distinguish ink collisions from whitespace in font lineboxes. */
object ZetaInkAudit {
 data class Ink(val text:String,val x:Float,val y:Float,val style:TextStyle,val align:TextAlign)
 @JvmStatic fun main(args:Array<String>) {
  val out=File(args[0]);out.mkdirs();var overlaps=0
  for(font in listOf(1f,1.3f,2f)) for(model in listOf("Continuum-Kern","qwen-3","gypqgypq")) {
   val l=ContinuumHomeLayout.wide(713f,897f,font);val b=l[Slot.MODEL]!!
   val img=BufferedImage(b.width.toInt()+1,b.height.toInt()+1,BufferedImage.TYPE_INT_ARGB)
   val p=Java2DContinuumPainter(img.createGraphics(),File("app/src/main/assets"));val commands=mutableListOf<Ink>()
   val recording=object:ContinuumPainter by p {
    override fun drawText(text:String,x:Float,y:Float,style:TextStyle,fill:Fill,align:TextAlign,alpha:Float) {commands+=Ink(text,x,y,style,align);p.drawText(text,x,y,style,fill,align,alpha)}
   }
   ContinuumHomeScreen.elements(l,font).getValue(Slot.MODEL).draw(recording,b.width,b.height,ContinuumDashboardState(model=ContinuumDashboardState.ModelStatus(name=model)))
   val rasters=commands.map { c -> BufferedImage(img.width,img.height,BufferedImage.TYPE_INT_ARGB).also { bi -> Java2DContinuumPainter(bi.createGraphics(),File("app/src/main/assets")).drawText(c.text,c.x,c.y,c.style,Fill.Solid(-1),c.align,1f) } }
   for(i in rasters.indices) for(j in i+1 until rasters.size) {
    var count=0;for(y in 0 until img.height) for(x in 0 until img.width) if((rasters[i].getRGB(x,y) ushr 24)>32 && (rasters[j].getRGB(x,y) ushr 24)>32) count++
    if(count>0) {overlaps++;println("INK_OVERLAP font=$font model=$model '${commands[i].text}' / '${commands[j].text}' pixels=$count")}
   }
   ImageIO.write(img,"png",File(out,"model-$model-font$font.png"))
  }
  println("SUMMARY supplementary rendered-ink collision pairs=$overlaps")
 }
}
