import at.heinz.deviceai.localmodel.*
import com.google.ai.edge.litertlm.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.lang.management.ManagementFactory

fun main() {
 val runtime=readyRuntime()
 val firstDelivery=CountDownLatch(1)
 val cancelNow=CountDownLatch(1)
 NativeProbe.dispatchHook={
   val first=Thread({
     NativeProbe.callback!!.onMessage(Message(Contents.of("first")))
   },"review-native-callback").apply{isDaemon=true}
   first.start()
   check(firstDelivery.await(2,TimeUnit.SECONDS))
   cancelNow.countDown()
   val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
   while(first.state!=Thread.State.BLOCKED && System.nanoTime()<deadline) Thread.yield()
   // Legal reentrant callback while sendMessageAsync has not returned.
   NativeProbe.callback!!.onMessage(Message(Contents.of("second")))
 }
 val dispatch=Thread({
   runtime.respondToTools(listOf(LocalToolResponse("get_device_info","small"))) {event->
     if(event is LocalInferenceEvent.TextDelta && event.text=="first") {
       firstDelivery.countDown()
       check(cancelNow.await(2,TimeUnit.SECONDS))
       runtime.cancel()
     }
   }
 },"review-native-dispatch").apply {isDaemon=true}
 dispatch.start();dispatch.join(1200)
 val bean=ManagementFactory.getThreadMXBean()
 val ids=bean.findDeadlockedThreads()
 println("CALLBACK_SUBMISSION_DEADLOCK=${ids!=null} dispatchAlive=${dispatch.isAlive}")
 if(ids!=null)for(info in bean.getThreadInfo(ids,true,true)) println(info)
}
