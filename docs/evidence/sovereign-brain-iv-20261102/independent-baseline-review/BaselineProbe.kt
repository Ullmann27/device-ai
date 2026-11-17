import at.heinz.deviceai.*
import at.heinz.deviceai.cognitive.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

fun delayedStart(cancel:Boolean) {
    val checking = CountDownLatch(1)
    val release = CountDownLatch(1)
    val backend = CountDownLatch(1)
    val clockReads = AtomicInteger()
    val result = AtomicReference<ToolExecutionResult>()
    val gate=ToolGate(ToolPolicy(readOnlyEnabled=true,timeoutMillis=100),ToolRunner {
        backend.countDown(); "unexpected late execution"
    }, clock={
        if(Thread.currentThread().name=="device-ai-tool" && clockReads.incrementAndGet()==2) {
            checking.countDown()
            while(release.count!=0L) { try { release.await(2,TimeUnit.SECONDS) } catch(_:InterruptedException) {} }
        }
        0L
    })
    val caller=Thread { result.set(gate.execute(ToolProposal("get_storage_info",emptyMap()).invocation(if(cancel)"cancel-race" else "timeout-race"))) }
    caller.start()
    check(checking.await(2,TimeUnit.SECONDS))
    if(cancel) gate.cancel()
    caller.join(2000)
    check(!caller.isAlive)
    println("${if(cancel)"CANCEL" else "TIMEOUT"}: returned=${result.get().record.status}, backendBeforeRelease=${backend.count==0L}")
    release.countDown()
    println("${if(cancel)"CANCEL" else "TIMEOUT"}: backendAfterTerminal=${backend.await(1,TimeUnit.SECONDS)}")
}
fun outputOverflow() {
    val plan=ReadOnlyToolPlan(ToolProposal("get_storage_info",emptyMap()))
    val events=java.util.Collections.synchronizedList(mutableListOf<AgentCognitiveEvent>())
    val store=InMemoryExperienceStore()
    val run=runCatching { BoundedAgentOrchestrator(plan,{emptyList()},ToolGate(ToolPolicy(readOnlyEnabled=true),ToolRunner {"x".repeat(8000)}),plan,store,eventObserver={events.add(it)}).run("Prüfe Speicher") }
    println("OUTPUT: error=${run.exceptionOrNull()}, result=${run.getOrNull()}, experiences=${store.all().size}, terminal=${events.last().state}")
}
fun main() { delayedStart(false);delayedStart(true);outputOverflow() }
