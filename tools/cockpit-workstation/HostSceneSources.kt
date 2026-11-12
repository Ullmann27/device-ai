package at.heinz.deviceai.ui

import at.heinz.deviceai.avatar3d.AvatarGlbContract
import at.heinz.deviceai.avatar3d.AvatarFraming
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.json.JSONObject

/** Exact original assets, host renderer only. */
object HostSceneSources {
    @JvmStatic fun main(args: Array<String>) {
        val root = File(args[0])
        val out = File(args[1]).also { it.mkdirs() }
        val image = BufferedImage(1024, 1024, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        val painter = Java2DContinuumPainter(graphics, File(root, "app/src/main/assets"))
        val orb = ContinuumHologramRenderer().apply {
            geometry = ContinuumHologramRenderer.Geometry.REFERENCE_COCKPIT
            showBrand = false
        }
        orb.layout(1024f, 1024f)
        orb.draw(painter, ContinuumHologramRenderer.Frame.still(), transparentBackdrop = true)
        graphics.dispose()
        ImageIO.write(image, "png", File(out, "original-orb-source.png"))
        val metadata = AvatarGlbContract.validate(File(root, "app/src/main/assets/avatar3d/continuum-avatar.glb").readBytes())
        val f = metadata.focus
        val json = JSONObject().put("assetSha256", AvatarGlbContract.ASSET_SHA256)
            .put("centerX", f.centerX).put("centerY", f.centerY).put("centerZ", f.centerZ)
            .put("halfWidth", f.halfWidth).put("halfHeight", f.halfHeight)
            .put("reachLeft", f.reachLeft).put("reachRight", f.reachRight)
            .put("azimuthDegrees", f.azimuthDegrees)
        File(out, "original-avatar-focus.json").writeText(json.toString(2) + "\n")
        println("HOST_ORIGINAL_SOURCE_EXPORTED")
    }
}
