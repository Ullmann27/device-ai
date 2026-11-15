package at.heinz.deviceai.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owner-triggered design acceptance snapshot: the app's real rendering of its window (Android rasteriser, the device's
 * own size, density, font scale and insets) as PNG, plus the metrics that decide the start-screen layout as JSON.
 * Both go to the owner's shared storage (Pictures/Continuum, Download/Continuum) and can be shared for the comparison
 * with the reference images. Nothing is taken automatically; system bar icons are drawn by Android and not included.
 */
object ContinuumUiSnapshot {
    data class Saved(val image: Uri, val metrics: Uri?, val fileName: String)

    /** Window metrics in px and dp, the chosen composition and its scale; no personal data. */
    fun metrics(activity: Activity, wide: Boolean, layoutScale: Float?): JSONObject {
        val c = activity.resources.configuration
        val m = activity.resources.displayMetrics
        val root = activity.window.decorView
        return JSONObject().apply {
            put("schema", "continuum-ui-snapshot")
            put("version", 1)
            put("model", Build.MANUFACTURER + " " + Build.MODEL)
            put("sdk", Build.VERSION.SDK_INT)
            put("screenWidthDp", c.screenWidthDp)
            put("screenHeightDp", c.screenHeightDp)
            put("smallestScreenWidthDp", c.smallestScreenWidthDp)
            put("densityDpi", m.densityDpi)
            put("density", m.density.toDouble())
            put("fontScale", c.fontScale.toDouble())
            put("orientation", if (c.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait")
            put("windowPx", "${root.width}x${root.height}")
            put("wideLayout", wide)
            put("layoutScale", layoutScale?.toDouble() ?: JSONObject.NULL)
            if (Build.VERSION.SDK_INT >= 30) {
                val insets = root.rootWindowInsets
                if (insets != null) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    put("insetsPx", JSONObject().put("left", bars.left).put("top", bars.top).put("right", bars.right).put("bottom", bars.bottom))
                }
            }
        }
    }

    /** The window's content as drawn by its views (software canvas of the same size). */
    fun render(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    /** Writes PNG and JSON through MediaStore (Android 10+; earlier versions would need a storage permission). */
    fun save(activity: Activity, bitmap: Bitmap, metrics: JSONObject): Saved? {
        if (Build.VERSION.SDK_INT < 29) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val name = "continuum-ui-$stamp-${metrics.optInt("screenWidthDp")}x${metrics.optInt("screenHeightDp")}dp"
        val resolver = activity.contentResolver
        val image = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Continuum")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Abnahme-Bild kann nicht angelegt werden.")
        var json: Uri? = null
        try {
            (resolver.openOutputStream(image, "w") ?: throw IOException("Abnahme-Bild ist nicht schreibbar.")).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Abnahme-Bild konnte nicht geschrieben werden." }
            }
            json = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.json")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Continuum")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            json?.let { uri ->
                (resolver.openOutputStream(uri, "w") ?: throw IOException("Bilddaten sind nicht schreibbar.")).use {
                    it.write(metrics.toString(1).toByteArray(Charsets.UTF_8))
                }
                check(resolver.update(uri, published, null, null) == 1) { "Bilddaten wurden nicht veröffentlicht." }
            }
            check(resolver.update(image, published, null, null) == 1) { "Abnahme-Bild wurde nicht veröffentlicht." }
            return Saved(image, json, name)
        } catch (failure: Throwable) {
            listOfNotNull(json, image).forEach { uri ->
                try { resolver.delete(uri, null, null) }
                catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            }
            throw failure
        }
    }

    /** Share sheet with both files; read access is granted only to the app the owner picks. */
    fun shareIntent(saved: Saved): Intent = Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(listOfNotNull(saved.image, saved.metrics)))
        putExtra(Intent.EXTRA_SUBJECT, "Continuum Design-Abnahme ${saved.fileName}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Abnahme-Bild teilen")
}
