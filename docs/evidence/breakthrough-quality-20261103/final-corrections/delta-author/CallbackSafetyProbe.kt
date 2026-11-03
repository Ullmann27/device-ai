import at.heinz.deviceai.chief.ChiefWorkerPool
import at.heinz.deviceai.chief.WorkerBrief
import at.heinz.deviceai.chief.WorkerJob
import at.heinz.deviceai.chief.WorkerReport
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun main() {
    fun brief(id: String) = WorkerBrief(id, "check $id", emptySet())
    val reports = ConcurrentLinkedQueue<WorkerReport>()
    val accepted = AtomicBoolean()
    lateinit var pool: ChiefWorkerPool
    pool = ChiefWorkerPool(1, 1) { WorkerJob { "done" } }
    try {
        check(pool.submit(brief("first")) { report ->
            accepted.set(pool.submit(brief("second")) { reports += it })
            pool.cancel("first") // Reentrant cancellation must not deadlock or emit a second report.
            reports += report
        })
        check(pool.awaitIdle(2_000))
        check(accepted.get())
        check(reports.map { it.id }.sorted() == listOf("first", "second"))
        println("PASS: callback reentrant submit/cancel, two reports, idle")
    } finally { pool.shutdown() }

    val callbackFinished = CountDownLatch(1)
    lateinit var closing: ChiefWorkerPool
    closing = ChiefWorkerPool(1, 0) { WorkerJob { "done" } }
    try {
        check(closing.submit(brief("closing")) {
            closing.shutdown()
            callbackFinished.countDown()
        })
        check(callbackFinished.await(2, TimeUnit.SECONDS))
        check(closing.awaitIdle(2_000))
        check(closing.active().isEmpty())
        println("PASS: callback reentrant shutdown returns and releases slot")
    } finally { closing.shutdown() }
}
