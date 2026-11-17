package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar.*
import at.heinz.deviceai.ui.Java2DContinuumPainter
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ContinuumAvatarPreview {
@JvmStatic fun main(args: Array<String>) {
    val root = File(args[0])
    val out = File(args[1]).apply { mkdirs() }
    for ((width, height) in listOf(412 to 307, 410 to 291)) {
        val reducer = AvatarPoseReducer()
        val renderer = ContinuumAvatarRenderer()
        for ((name, seconds) in listOf("idle" to 0f, "blink" to 4.51f, "speech" to 0f)) {
            if (name == "speech") {
                reducer.apply(AvatarEvent.SpeechStarted("fixture", "Hallo", 100))
                repeat(8) { reducer.apply(AvatarEvent.AudioLevel("fixture", .8f, 110)) }
                reducer.apply(AvatarEvent.VisemeCue("fixture", Viseme.O, 1f, 100, 110))
            }
            val image = BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.color = java.awt.Color(0x07101f); g.fillRect(0, 0, image.width, image.height)
            val p = Java2DContinuumPainter(g, File(root, "app/src/main/assets"))
            p.scale(2f, 2f)
            renderer.draw(p, width.toFloat(), height.toFloat(), reducer.pose(110), seconds, true)
            g.dispose()
            ImageIO.write(image, "png", File(out, "head-${width}x$height-$name.png"))
        }
    }
    println("6 native-renderer Java2D fixtures written; synthetic events, not device or measured FPS")
}
}
