package at.heinz.deviceai.localmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LocalToolTurnGateTest {
    @Test fun oneProposalAcrossChunksOnly() {
        val guard = LocalToolTurnGate()
        assertTrue(guard.accept(0))
        assertTrue(guard.accept(1))
        assertFalse(guard.isAmbiguous())
        assertFalse(guard.accept(1))
        assertTrue(guard.isAmbiguous())
        assertFalse(guard.accept(0))
    }

    @Test fun sameChunkMultipleProposalsFailClosed() {
        val guard = LocalToolTurnGate()
        assertFalse(guard.accept(2))
        assertTrue(guard.isAmbiguous())
        assertFalse(guard.accept(1))
    }

    @Test fun independentTurnsHaveIndependentLimits() {
        val first = LocalToolTurnGate()
        val second = LocalToolTurnGate()
        assertTrue(first.accept(1))
        assertTrue(second.accept(1))
        assertFalse(first.accept(1))
        assertFalse(second.isAmbiguous())
    }

    @Test fun racingChunksCannotBothBeAuthorized() {
        val guard = LocalToolTurnGate()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val completed = CountDownLatch(2)
        val approved = AtomicInteger(0)
        try {
            repeat(2) {
                pool.execute {
                    try {
                        start.await()
                        if (guard.accept(1)) approved.incrementAndGet()
                    } finally {
                        completed.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue("Both callbacks must finish", completed.await(5, TimeUnit.SECONDS))
            assertEquals(1, approved.get())
            assertTrue(guard.isAmbiguous())
        } finally {
            pool.shutdownNow()
        }
    }
}
