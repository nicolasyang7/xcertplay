package com.shilapi.xcertplay.network

import java.io.IOException
import java.util.concurrent.TimeUnit

/** Keeps a pending system request owned until its reservation is actually released. */
internal class HotspotRequestGate {
    private val lock = Object()
    private var owner: Any? = null

    fun acquire(token: Any, deadlineNanos: Long, isCancelled: () -> Boolean) {
        synchronized(lock) {
            while (true) {
                if (isCancelled()) throw IOException("LocalOnlyHotspot startup was cancelled")
                if (owner == null) {
                    owner = token
                    return
                }
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0) {
                    throw IOException("Previous LocalOnlyHotspot request is still being released; " +
                        "force-stop xcertplay and reopen if this persists")
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)))
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted while waiting for the previous hotspot request", interrupted)
                }
            }
        }
    }

    fun release(token: Any) {
        synchronized(lock) {
            if (owner === token) {
                owner = null
                lock.notifyAll()
            }
        }
    }
}
