package at.heinz.deviceai.systemintelligence

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class StorageScanLane : AutoCloseable {
    private val busy = AtomicBoolean()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "continuum-storage-read").apply { isDaemon = true }
    }

    fun <T> run(timeoutMs: Long, cancelled: () -> Boolean, work: (() -> Boolean) -> T): T {
        require(timeoutMs in 1..12_000)
        check(!cancelled() && !Thread.currentThread().isInterrupted) { "STORAGE_SCAN_CANCELLED" }
        check(busy.compareAndSet(false, true)) { "STORAGE_SCAN_BUSY" }
        val revoked = AtomicBoolean()
        val runningThread = AtomicReference<Thread?>()
        val task = FutureTask {
            runningThread.set(Thread.currentThread())
            try {
                check(!revoked.get() && !cancelled()) { "STORAGE_SCAN_CANCELLED" }
                val result = work { revoked.get() || cancelled() || Thread.currentThread().isInterrupted }
                check(!revoked.get() && !cancelled() && !Thread.currentThread().isInterrupted) { "STORAGE_SCAN_CANCELLED" }
                result
            } finally {
                runningThread.set(null)
                busy.set(false)
                Thread.interrupted()
            }
        }
        try {
            worker.execute(task)
        } catch (failure: RuntimeException) {
            busy.set(false)
            throw failure
        }
        val start = System.nanoTime()
        val budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        try {
            while (true) {
                check(!cancelled() && !Thread.currentThread().isInterrupted) { "STORAGE_SCAN_CANCELLED" }
                val elapsed = System.nanoTime() - start
                check(elapsed in 0 until budgetNanos) { "STORAGE_SCAN_TIMEOUT" }
                try {
                    val result = task.get(minOf(TimeUnit.MILLISECONDS.toNanos(100), budgetNanos - elapsed), TimeUnit.NANOSECONDS)
                    check(!cancelled() && System.nanoTime() - start in 0 until budgetNanos) { "STORAGE_SCAN_CANCELLED_OR_EXPIRED" }
                    return result
                } catch (_: TimeoutException) {
                    continue
                } catch (failure: ExecutionException) {
                    throw IllegalStateException("STORAGE_SCAN_FAILED", failure.cause)
                }
            }
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("STORAGE_SCAN_CANCELLED", failure)
        } finally {
            revoked.set(true)
            runningThread.get()?.interrupt()
        }
    }

    override fun close() { worker.shutdownNow() }
}
