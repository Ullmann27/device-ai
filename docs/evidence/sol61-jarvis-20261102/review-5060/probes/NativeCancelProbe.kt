import at.heinz.deviceai.localmodel.*
import com.google.ai.edge.litertlm.*
import java.nio.file.Files

fun readyRuntime():LiteRtLmLocalModelRuntime {
 val runtime=LiteRtLmLocalModelRuntime(LocalModelCatalog(),Files.createTempDirectory("native-cancel-cache").toFile())
 for((name,value) in listOf("state" to LocalModelState.READY,"conversation" to Conversation(),"loadedModel" to ImportedLocalModel())) {
  runtime.javaClass.getDeclaredField(name).apply {isAccessible=true}.set(runtime,value)
 }
 return runtime
}
fun main() {
 for(unload in listOf(false,true)) {
   val runtime=readyRuntime()
   val events=mutableListOf<String>()
   var cancellationReturned=false
   runtime.respondToTools(listOf(LocalToolResponse("get_device_info","small"))) {event->
     events+=event.toString()
     if(event is LocalInferenceEvent.TextDelta && event.text=="first") {
       if(unload)runtime.unload {} else runtime.cancel()
       cancellationReturned=true
     }
     if(event is LocalInferenceEvent.TextDelta && event.text=="late")
       println("TEXT_AFTER_${if(unload)"UNLOAD" else "CANCEL"}_RETURNED=$cancellationReturned")
   }
   NativeProbe.callback!!.onMessage(Message(Contents.of(listOf(Content.Text("first"),Content.Text("late")))))
   NativeProbe.callback!!.onDone()
   println("EVENTS_${if(unload)"UNLOAD" else "CANCEL"}=$events")
 }
}
