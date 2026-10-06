package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.message.Iap2NavigationState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-local publication point for the iPhone's route guidance.
 *
 * Downstream consumers (for example a NaviTool adapter) subscribe here instead of touching iAP2.
 * Every state carries a session id; consumers must ignore states whose id is older than the newest
 * one they have seen.
 */
object CarPlayNavigationBridge {
    fun interface Listener {
        fun onNavigationState(state: Iap2NavigationState)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    @Volatile
    var latest: Iap2NavigationState? = null
        private set

    /** Registers [listener] and immediately replays the latest state, if any. */
    fun addListener(listener: Listener) {
        listeners += listener
        latest?.let(listener::onNavigationState)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    internal fun publish(state: Iap2NavigationState) {
        latest = state
        for (listener in listeners) {
            try {
                listener.onNavigationState(state)
            } catch (_: RuntimeException) {
                // A faulty consumer must not break iAP2 handling.
            }
        }
    }
}
