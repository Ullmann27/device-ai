import at.heinz.deviceai.localmodel.*
import com.google.ai.edge.litertlm.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun main() {
 val runtime=readyRuntime()
 val entered=CountDownLatch(1);val release=CountDownLatch(1)
 val events=mutableListOf<String>()
 var unloadReturned=false
 runtime.respondToTools(listOf(LocalToolResponse("get_device_info","small"))) {event->
   synchronized(events){events+=event.toString()}
   if(event is LocalInferenceEvent.TextDelta && event.text=="first") {
     entered.countDown();check(release.await(2,TimeUnit.SECONDS))
   }
   if(event is LocalInferenceEvent.TextDelta && event.text=="late")println("QUEUED_TEXT_AFTER_UNLOAD_RETURNED=$unloadReturned")
 }
 val native=NativeProbe.callback!!
 val emitter=Thread {native.onMessage(Message(Contents.of(listOf(Content.Text("first"),Content.Text("late")))))}
 emitter.start();check(entered.await(2,TimeUnit.SECONDS))
 native.onDone()
 println("STATE_BEFORE_UNLOAD=${runtime.state}")
 runtime.unload{}
 unloadReturned=true
 release.countDown();emitter.join(2000)
 println("QUEUED_UNLOAD_EVENTS=$events emitterAlive=${emitter.isAlive}")
}
