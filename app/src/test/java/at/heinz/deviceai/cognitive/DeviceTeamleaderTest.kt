package at.heinz.deviceai.cognitive

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class DeviceTeamleaderTest {
    @Test fun firstAndroidCycleUsesExistingBacklogAndPersistsNoPersonalText() {
        val dir = Files.createTempDirectory("teamleader-android").toFile()
        try {
            val supervisor = DeviceTeamleader(dir, now = { 1750000000000L })
            assertNull(supervisor.status())
            val result = supervisor.runOnce()
            assertEquals("READY", result.state)
            assertEquals("OK", result.reason)
            assertEquals(0, result.readyTasks)
            assertEquals(0, result.failedTasks)
            assertEquals(0, result.recoveredClaims)
            assertTrue(result.swb1Updates >= 0)
            assertEquals(0, result.cre1Steps)
            assertEquals(result, DeviceTeamleader(dir).status())
            assertFalse(result.display().contains("DONE"))
            val file = dir.resolve("development/device-teamleader-v1.txt")
            assertTrue(file.isFile)
            assertTrue(file.length() < 512)
            assertEquals(2, file.readLines().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun taskIsVisibleForFuturePcButNeverClaimedByAndroidHousekeeping() {
        val dir = Files.createTempDirectory("teamleader-tasks").toFile()
        try {
            val backlog = DeviceDevelopmentBacklog(dir.resolve("development"),
                clock = { 1750000000000L })
            val proposal = mapOf("id" to "dev-android-worker-ready",
                "title" to "Prüfe den Kotlin-Trainingskern",
                "kind" to "INVESTIGATE", "source" to "android",
                "priority" to 500L, "maxAttempts" to 3L)
            backlog.apply(listOf(
                mapOf("op" to "propose", "task" to proposal),
                mapOf("op" to "accept", "id" to proposal["id"], "reason" to "Lokaler Test")
            ))
            val before = backlog.views().single()
            assertEquals("READY", before.state)
            val result = DeviceTeamleader(dir, now = { 1750000001000L }).runOnce()
            assertEquals("READY", result.state)
            assertEquals(1, result.readyTasks)
            assertEquals("dev-android-worker-ready", result.nextTaskId)
            val after = backlog.views().single()
            assertEquals("READY", after.state)
            assertNull(after.claimOwner)
            assertEquals(0L, after.attempts)
            assertFalse(result.display().contains("Prüfe den Kotlin-Trainingskern"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancelledCycleDoesNotClaimWorkOrReportCompletedTasks() {
        val dir = Files.createTempDirectory("teamleader-cancelled").toFile()
        try {
            val result = DeviceTeamleader(dir).runOnce { true }
            assertEquals("DEFERRED", result.state)
            assertEquals("CANCELLED", result.reason)
            assertEquals(result, DeviceTeamleader(dir).status())
            assertFalse(dir.resolve("development/development-backlog.json").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun backgroundAndManualTeamleaderCannotOverlapOrOverwriteStatus() {
        val dir = Files.createTempDirectory("teamleader-overlap").toFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = AtomicReference<DeviceTeamleaderSnapshot?>()
        val first = Thread {
            finished.set(DeviceTeamleader(dir, now = {
                entered.countDown()
                check(release.await(8, TimeUnit.SECONDS))
                1750000000000L
            }).runOnce())
        }
        try {
            first.start()
            assertTrue("first cycle acquired lock", entered.await(5, TimeUnit.SECONDS))
            val second = DeviceTeamleader(dir, now = { 1750000001000L })
            assertEquals("WORKER_BUSY", second.runOnce().reason)
            assertEquals("WORKER_BUSY", second.defer("BATTERY_LOW").reason)
            assertFalse("busy caller must not overwrite durable status",
                dir.resolve("development/device-teamleader-v1.txt").exists())
            release.countDown()
            first.join(10000)
            assertFalse("background worker completed", first.isAlive)
            assertEquals("READY", finished.get()?.state)
            assertEquals("READY", second.status()?.state)
        } finally {
            release.countDown()
            first.join(10000)
            dir.deleteRecursively()
        }
    }

    @Test fun aCorruptedReportFailsClosedWithoutSilentlyResettingIt() {
        val dir = Files.createTempDirectory("teamleader-corrupt").toFile()
        try {
            val worker = DeviceTeamleader(dir)
            worker.defer("BATTERY_LOW")
            val file = dir.resolve("development/device-teamleader-v1.txt")
            val before = file.readText()
            file.writeText(before.replace("BATTERY_LOW", "THERMAL_LIMIT"))
            assertThrows(IllegalArgumentException::class.java) { worker.status() }
            val damaged = file.readBytes()
            assertEquals("ERROR", worker.runOnce().state)
            assertEquals("CHECK_FAILED", worker.defer("BATTERY_LOW").reason)
            assertArrayEquals("corrupted evidence must survive further runs",
                damaged, file.readBytes())
            assertThrows(IllegalArgumentException::class.java) { worker.status() }
            assertTrue(file.readText().contains("THERMAL_LIMIT"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidDeferredReasonCannotModifyExistingTeamleaderStatus() {
        val dir = Files.createTempDirectory("teamleader-invalid-deferral").toFile()
        try {
            val worker = DeviceTeamleader(dir)
            val saved = worker.defer("BATTERY_LOW")
            val file = dir.resolve("development/device-teamleader-v1.txt")
            val bytes = file.readBytes()
            assertThrows(IllegalArgumentException::class.java) {
                worker.defer("SUCCESS_WITHOUT_TESTS")
            }
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(saved, worker.status())
        } finally { dir.deleteRecursively() }
    }

    @Test fun aCorruptedSharedBacklogIsReportedAsFailedNotReset() {
        val dir = Files.createTempDirectory("teamleader-bad-backlog").toFile()
        try {
            val file = dir.resolve("development/development-backlog.json")
            file.parentFile!!.mkdirs()
            file.writeText("not JSON")
            val snapshot = DeviceTeamleader(dir).runOnce()
            assertEquals("ERROR", snapshot.state)
            assertEquals("CHECK_FAILED", snapshot.reason)
            assertEquals("not JSON", file.readText())
        } finally { dir.deleteRecursively() }
    }
}
