package at.heinz.deviceai.localmodel

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Ordered reentrant callbacks, drained only outside runtime/native monitors. */
internal class LocalInferenceDelivery {
    private val pending=ConcurrentLinkedQueue<()->Unit>()
    private val draining=AtomicBoolean(false)
    fun enqueue(deliver:()->Unit) { pending.add(deliver) }
    fun drain() {
        if(!draining.compareAndSet(false,true))return
        do {
            try { while(true) (pending.poll() ?: break).invoke() }
            finally { draining.set(false) }
        } while(pending.isNotEmpty() && draining.compareAndSet(false,true))
    }
}
