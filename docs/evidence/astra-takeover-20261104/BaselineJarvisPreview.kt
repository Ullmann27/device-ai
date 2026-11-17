package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.AvatarPoseReducer
import at.heinz.deviceai.avatar.ContinuumAvatarRenderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Source-derived component preview, not a running Activity or a device capture. */
object CurrentJarvisPreview {
 @JvmStatic fun main(args: Array<String>) {
  val root=File(args[0]); val out=File(args[1]); out.parentFile.mkdirs()
  val width=884f; val height=760f; val density=2f
  val railWidth=JarvisMenuRail.widthDp(width,height)
  val img=BufferedImage((width*density).toInt(),(height*density).toInt(),BufferedImage.TYPE_INT_ARGB)
  val g=img.createGraphics(); val p=Java2DContinuumPainter(g,File(root,"app/src/main/assets")); p.scale(density,density)
  val state=ContinuumDashboardState()
  JarvisShellRenderer.background(p,width,height)
  JarvisMenuRailElement(device=JarvisDeviceStatus.EMPTY).draw(p,railWidth,height,state)
  p.save(); p.translate(railWidth,0f)
  val shell=JarvisShellLayout.create(width-railWidth,height)
  val headBox=shell.grid?.get(JarvisCockpitGrid.Area.HEAD) ?: requireNotNull(shell[JarvisShellLayout.Slot.AVATAR])
  val contents=JarvisShellContent.forState({state},{0L},grid={shell.grid}).toMutableMap()
  val head=ContinuumAvatarRenderer()
  val pose=AvatarPoseReducer().pose(0L)
  val orb=ContinuumHologramRenderer().apply { geometry=ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT; showBrand=false }
  contents[JarvisShellLayout.Slot.AVATAR]=JarvisShellRenderer.SlotContent { painter,_ ->
   painter.save(); painter.translate(headBox.left,headBox.top); painter.clipRect(0f,0f,headBox.width,headBox.height)
   // Same geometry as ContinuumHologramView.onDraw on the current HEAD-only slot.
   head.draw(painter,headBox.width*.66f,headBox.height*.92f,pose,0f,false)
   painter.save(); painter.translate(headBox.width*.57f,headBox.height*.09f)
   val ow=headBox.width*.43f; val oh=headBox.height*.76f
   painter.clipRect(0f,0f,ow,oh); orb.layout(ow,oh)
   orb.draw(painter,ContinuumHologramRenderer.Frame.still(ContinuumHologramRenderer.Mode.READY,0f))
   painter.restore(); painter.restore()
  }
  JarvisShellRenderer.draw(p,shell,contents,animatorScale=0f,
   tiles=JarvisShellContent.tiles(state,0L,subtitles=true),timeMs=0L)
  p.restore(); g.dispose(); ImageIO.write(img,"png",out)
  println("Rendered "+out+"; outer rail="+railWidth+", head="+headBox)
 }
}
