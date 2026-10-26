package com.shilapi.xcertplay

import android.os.Build
import android.util.Log

/**
 * Enforces Wi-Fi P2P channel 149 (5 GHz) on Lynk & Co 09 (Build.MODEL == "G636"),
 * inspired by XiaoBa v3.0's CarPlayChannelRootMain.
 *
 * OneOS / msmnile firmware locks P2P to 2.4 GHz or low channels by default and its
 * system watchdog attempts to reset channels periodically. This guard applies
 * channel 149 via root and keeps a lease heartbeat to prevent reversion.
 */
internal object Lynk09ChannelGuard {
    private const val TAG = "Lynk09-ChannelGuard"
    private const val TARGET_CHANNEL = 149
    private const val LEASE_INTERVAL_MS = 6000L

    @Volatile private var running = false
    @Volatile private var thread: Thread? = null

    fun isLynk09(): Boolean {
        val model = Build.MODEL
        val product = Build.PRODUCT
        return model.equals("G636", ignoreCase = true) ||
            product.contains("G636", ignoreCase = true) ||
            model.contains("Lynk", ignoreCase = true)
    }

    @Synchronized
    fun start() {
        if (!isLynk09() || running) return
        running = true
        thread = Thread({
            Log.i(TAG, "Starting Lynk & Co 09 P2P channel $TARGET_CHANNEL enforcement heartbeat")
            while (running) {
                try {
                    // Try standard root commands to force P2P / AP GO channel to 149
                    val commands = arrayOf(
                        "cmd wifi set-p2p-channel $TARGET_CHANNEL",
                        "wpa_cli -p /data/misc/wifi/sockets -i p2p0 p2p_set channel $TARGET_CHANNEL",
                        "setprop persist.vendor.wifi.p2p.channel $TARGET_CHANNEL",
                    )
                    for (cmd in commands) {
                        try {
                            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                            proc.waitFor()
                        } catch (_: Exception) {
                        }
                    }
                    Thread.sleep(LEASE_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat error: ${e.message}")
                    try { Thread.sleep(10000L) } catch (_: InterruptedException) { break }
                }
            }
            Log.i(TAG, "Lynk & Co 09 P2P channel enforcement stopped")
        }, "lynk09-p2p-guard").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}
