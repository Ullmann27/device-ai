package at.heinz.deviceai.chief

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Public-contract adversarial checks, frozen before candidate implementation inspection. */
object CoordinatorIndependentChecks {
    private var passed = 0
    private var failed = 0
    private fun test(name: String, block: () -> Unit) {
        try { block(); passed++; println("PASS $name") }
        catch (t: Throwable) { failed++; println("FAIL $name: ${t.javaClass.simpleName}: ${t.message}") }
    }
    @JvmStatic fun main(args: Array<String>) {
        test("caller-owned-profile-sets-cannot-expand-approved-scope") {
            val pool = ChiefWorkerPool(1, 1) { WorkerJob { "host fixture only" } }
            try {
                val capabilities = mutableSetOf("reading")
                val allowed = mutableSetOf("get_battery_info")
                val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
                check(chief.register(ChiefSpecialist("review-bot", capabilities, allowed)))
                capabilities += "writing"; allowed += "delete_file"
                val outcome = chief.delegate(ChiefDelegation("alias-profile", "unapproved write", "writing", setOf("delete_file"))) { }
                check(outcome == ChiefDispatchOutcome.NO_SPECIALIST || outcome == ChiefDispatchOutcome.TOOL_SCOPE_REJECTED) {
                    "unapproved scope accepted as $outcome"
                }
            } finally { pool.shutdown() }
        }
        test("listed-profile-sets-cannot-expand-internal-scope") {
            val pool = ChiefWorkerPool(1, 1) { WorkerJob { "host fixture only" } }
            try {
                val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
                check(chief.register(ChiefSpecialist("review-bot", mutableSetOf("reading"), mutableSetOf("get_battery_info"))))
                val listed = chief.registeredSpecialists().single()
                runCatching { (listed.allowedTools as MutableSet<String>).add("delete_file") }
                val outcome = chief.delegate(ChiefDelegation("alias-listed", "unapproved delete", "reading", setOf("delete_file"))) { }
                check(outcome == ChiefDispatchOutcome.TOOL_SCOPE_REJECTED) { "listed alias broadened authority: $outcome" }
            } finally { pool.shutdown() }
        }
        test("delegation-tool-set-snapshot-survives-queued-caller-mutation") {
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val captured = AtomicReference<Set<String>>()
            val pool = ChiefWorkerPool(1, 2) { brief -> WorkerJob {
                if (brief.id == "blocker") { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
                else captured.set(brief.allowedTools.toSet())
                "host fixture only"
            } }
            try {
                check(pool.submit(WorkerBrief("blocker", "hold deterministic queue", emptySet())) { })
                check(entered.await(2, TimeUnit.SECONDS))
                val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
                check(chief.register(ChiefSpecialist("review-bot", setOf("reading"), setOf("get_battery_info"))))
                val requested = mutableSetOf("get_battery_info")
                check(chief.delegate(ChiefDelegation("alias-request", "read", "reading", requested)) { } == ChiefDispatchOutcome.SUBMITTED)
                requested += "delete_file"
                release.countDown(); check(pool.awaitIdle(3_000))
                check(captured.get() == setOf("get_battery_info")) { "queued brief changed after admission: ${captured.get()}" }
            } finally { release.countDown(); pool.shutdown() }
        }
        test("shutdown-rejection-does-not-leave-phantom-assignment") {
            val pool = ChiefWorkerPool(1, 0) { WorkerJob { "unused" } }
            val chief = ChiefDelegationCoordinator(pool, approveSpecialist = { true })
            check(chief.register(ChiefSpecialist("review-bot", setOf("reading"), emptySet())))
            pool.shutdown()
            runCatching { chief.delegate(ChiefDelegation("after-shutdown", "read", "reading")) { } }
            check(chief.activeAssignments().isEmpty()) { "rejected work remains active: ${chief.activeAssignments()}" }
        }
        println("RESULT passed=$passed failed=$failed")
        check(failed == 0) { "$failed coordinator counterexamples" }
    }
}
