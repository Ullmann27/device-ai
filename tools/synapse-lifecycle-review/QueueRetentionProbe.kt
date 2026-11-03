package at.heinz.deviceai.chief

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Follow-up diagnostic, not part of the eight frozen shutdown acceptance cases. */
object QueueRetentionProbe {
    @JvmStatic fun main(args: Array<String>) {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val exited = CountDownLatch(1)
        val calls = AtomicInteger(); val accepted = AtomicInteger()
        val reports = ConcurrentLinkedQueue<WorkerReport>()
        val pool = ChiefWorkerPool(1, 0) { WorkerJob {
            calls.incrementAndGet(); started.countDown()
            while (release.count != 0L) try { release.await() } catch (_: InterruptedException) { }
            exited.countDown(); "late"
        } }
        try {
            check(pool.submit(WorkerBrief("native", "bounded", emptySet(), 300)) { reports += it }); accepted.incrementAndGet()
            check(started.await(3, TimeUnit.SECONDS)); check(pool.awaitIdle(3_000))
            repeat(24) { i ->
                val admitted = try {
                    pool.submit(WorkerBrief("waiting-$i", "bounded", emptySet(), 10)) { reports += it }
                } catch (_: RejectedExecutionException) { false }
                if (admitted) accepted.incrementAndGet()
                check(pool.awaitIdle(3_000))
            }
            val field = ChiefWorkerPool::class.java.getDeclaredField("workers").apply { isAccessible = true }
            val executor = field.get(pool) as ThreadPoolExecutor
            println("{\"attempted\":25,\"accepted\":${accepted.get()},\"physicalCalls\":${calls.get()},\"retainedWorkerQueue\":${executor.queue.size},\"unfinishedSlots\":${pool.active().size},\"maxConcurrent\":1,\"maxQueued\":0}")
            check(calls.get() == 1)
            check(reports.size == accepted.get() && reports.map { it.id }.toSet().size == reports.size)
            check(reports.none { it.status == WorkerStatus.DONE })
            check(executor.queue.size <= 1) { "Retained worker queue exceeds configured logical capacity after terminal reporting" }
            release.countDown(); check(exited.await(3, TimeUnit.SECONDS))
            check(pool.submit(WorkerBrief("recovered", "bounded", emptySet(), 300)) { reports += it })
            check(pool.awaitIdle(3_000))
            check(calls.get() == 2) { "Cancelled queued work replayed after native call exited" }
            check(reports.last().id == "recovered" && reports.last().status == WorkerStatus.DONE)
        } finally { pool.shutdown(); release.countDown(); check(exited.await(3, TimeUnit.SECONDS)) }
    }
}
