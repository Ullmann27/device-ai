package at.heinz.deviceai.ui
import at.heinz.deviceai.avatar.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
object HeadRef5Preview { @JvmStatic fun main(a: Array<String>) {
    val w = 400; val h = 420
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); val g = img.createGraphics()
    g.color = java.awt.Color(JarvisDesign.Colors.background and 0xffffff); g.fillRect(0, 0, w, h)
    val p = Java2DContinuumPainter(g, File(a[0], "app/src/main/assets"))
    val r = ContinuumAvatarRenderer()
    p.translate(-10f, 40f)
    r.draw(p, 420f, 470f, AvatarPoseReducer().pose(0), a[2].toFloat(), true)
    g.dispose(); ImageIO.write(img, "png", File(a[1]))
}}
