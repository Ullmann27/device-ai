package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import at.heinz.deviceai.cognitive.NativeAppCandidate
import at.heinz.deviceai.cognitive.NativeAppLookup
import org.json.JSONArray
import org.json.JSONObject

/** Never launches or manipulates a discovered app. Results are untrusted suggestions. */
internal object AndroidInstalledAppLookup {
    fun search(context: Context, query: String): String {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(launcher, 0)
        val apps = resolved.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                ?: return@mapNotNull null
            NativeAppCandidate(pkg, label)
        }
        val result = NativeAppLookup.search(query, apps)
        val suggestions = JSONArray()
        result.candidates.forEach {
            suggestions.put(JSONObject().put("packageName", it.packageName).put("label", it.label))
        }
        return JSONObject()
            .put("schema", "CONTINUUM_APP_DISCOVERY_V1")
            .put("readOnly", true)
            .put("visibility", "VISIBLE_LAUNCHER_ACTIVITIES_ONLY")
            .put("exactLabelMatch", result.exactLabel)
            .put("uniqueExactMatch", result.exactLabel && result.total == 1)
            .put("totalMatches", result.total)
            .put("candidates", suggestions)
            .put("authority", "SUGGESTIONS_ONLY_LAUNCH_REQUIRES_NEW_OWNER_CONFIRMATION")
            .toString()
    }
}
