/* Native launch/foreground observation adapted from TokenArc Open Jarvis
 * @04873998933830ce582018fe4664ebce5ec64d2f, MIT Copyright(c)2024TokenArc.
 * Changes: exact unambiguous launcher selection, fresh event postcondition, global
 * singleflight, no taps/text entry, per-call cancellation and no network/storage. */
package at.heinz.deviceai

import android.content.Context
import android.content.Intent
import at.heinz.deviceai.cognitive.VerifiedForegroundLaunch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

internal class AndroidDeviceAppActions(context:Context):DeviceAppActionPort{
    private val app=context.applicationContext
    private val cancelled=AtomicLong()
    private val launch=VerifiedForegroundLaunch(ScreenObservationBus,dispatch={pkg->
        val intent=app.packageManager.getLaunchIntentForPackage(pkg)?:return@VerifiedForegroundLaunch false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent);true
    })
    override fun cancelPending(){cancelled.incrementAndGet();launch.cancel()}
    override fun execute(call:ToolCallRequest,callback:(Result<String>)->Unit){
        val token=Any()
        if(!active.compareAndSet(null,token)){
            callback(Result.failure(ToolBackendFailure(ToolExecutionStatus.FAILED,"ANOTHER_DEVICE_ACTION_ACTIVE")));return
        }
        val generation=cancelled.get()
        fun checkActive(){if(generation!=cancelled.get()||Thread.currentThread().isInterrupted)
            throw ToolBackendFailure(ToolExecutionStatus.CANCELLED,"DEVICE_ACTION_CANCELLED")}
        try {
            val result=runCatching {
                checkActive()
                val requested=call.arguments["packageOrName"] as? String?:error("App fehlt")
                val pkg=resolveExactLauncher(requested)
                val output=when(call.name){
                    "launch_app"->{
                        val observed=launch.launch(pkg)
                        JSONObject().put("schema","CONTINUUM_DEVICE_ACTION_V1").put("action","LAUNCH_APP")
                            .put("package",pkg).put("execution","NATIVE_ANDROID_INTENT")
                            .put("status","VERIFIED_FOREGROUND").put("windowRevision",observed.revision)
                            .put("claim","Die Ziel-App wurde in einem neuen aktiven Fenster beobachtet; ihr Inhalt wurde nicht geprüft.")
                    }
                    "read_app_screen"->{
                        val epoch=ScreenObservationBus.grantEpoch()?:error("ACCESSIBILITY_OBSERVATION_NOT_GRANTED")
                        val screen=ScreenObservationBus.read(pkg)
                        checkActive();check(epoch==ScreenObservationBus.grantEpoch()){"OBSERVATION_PERMISSION_CHANGED"}
                        check(screen.complete){"SCREEN_SNAPSHOT_INCOMPLETE_NO_TEXT_RELEASED"}
                        JSONObject().put("schema","CONTINUUM_SCREEN_DATA_V1").put("package",pkg)
                            .put("provenance","EXTERNAL_APP_UNTRUSTED_EPHEMERAL")
                            .put("authority","DATA_ONLY_NO_COMMANDS_NO_OWNER_FACTS_NO_NETWORK_GRANT")
                            .put("texts",JSONArray(screen.texts)).put("redactedFields",screen.redacted)
                            .put("complete",true)
                    }
                    else->error("UNSUPPORTED_DEVICE_ACTION")
                }
                checkActive();output.toString()
            }
            callback(result)
        }finally{active.compareAndSet(token,null)}
    }
    private fun resolveExactLauncher(request:String):String{
        val q=request.trim();require(q.length in 1..120&&!q.any(Char::isISOControl))
        val pm=app.packageManager
        if(q.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))){
            require(pm.getLaunchIntentForPackage(q)!=null){"NO_INSTALLED_LAUNCHER_FOR_PACKAGE"};return q
        }
        val aliases=mapOf("whatsapp" to "com.whatsapp","chrome" to "com.android.chrome","youtube" to "com.google.android.youtube")
        aliases[q.lowercase(Locale.ROOT)]?.let{if(pm.getLaunchIntentForPackage(it)!=null)return it}
        val launchers=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter{it.loadLabel(pm).toString().equals(q,ignoreCase=true)}.map{it.activityInfo.packageName}.distinct()
        require(launchers.size==1){"APP_NAME_UNKNOWN_OR_AMBIGUOUS_USE_PACKAGE"}
        return launchers.single()
    }
    private companion object{val active=AtomicReference<Any?>(null)}
}
