package com.shilapi.xcertplay.network

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class HotspotRequestGateTest {
    private fun deadline() = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)

    @Test fun reconnectWaitsUntilPreviousRequestIsReleased() {
        val gate = HotspotRequestGate()
        val previous = Any()
        val next = Any()
        val entered = CountDownLatch(1)
        val acquired = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        gate.acquire(previous, deadline()) { false }
        val waiter = Thread {
            entered.countDown()
            try {
                gate.acquire(next, deadline()) { false }
                acquired.countDown()
            } catch (failure: Throwable) { error.set(failure) }
        }
        waiter.start()
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS))
            gate.release(previous)
            assertTrue(acquired.await(1, TimeUnit.SECONDS))
        } finally {
            gate.release(previous)
            gate.release(next)
            waiter.join(2500)
        }
        assertNull(error.get())
        assertFalse(waiter.isAlive)
    }

    @Test fun lateCleanupCannotReleaseNewRequest() {
        val gate = HotspotRequestGate()
        val previous = Any()
        val current = Any()
        gate.acquire(previous, deadline()) { false }
        gate.release(previous)
        gate.acquire(current, deadline()) { false }
        gate.release(previous)
        assertThrows(IOException::class.java) {
            gate.acquire(Any(), System.nanoTime()) { false }
        }
        gate.release(current)
        gate.acquire(Any(), deadline()) { false }
    }

    @Test fun cancelledReconnectDoesNotClaimTheRequest() {
        val gate = HotspotRequestGate()
        assertThrows(IOException::class.java) {
            gate.acquire(Any(), deadline()) { true }
        }
        gate.acquire(Any(), deadline()) { false }
    }
}
