package com.shilapi.xcertplay

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.VehicleSpeedReading
import com.shilapi.xcertplay.transport.VehicleSpeedSource
import com.shilapi.xcertplay.vehicle.VehicleGear
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Wheel speed and gear from the ECARX car SDK (`ISensor`), for the iPhone's dead reckoning. The SDK is
 * in the head unit's boot classpath, so it is reached by reflection; an ordinary app reads these
 * sensors with no extra permission (Geely Monjaro, ECARX KX11, Lynk & Co 09 G636, Android 9/11).
 *
 * The speed is polled rather than subscribed to: a listener only fires on a change, so a standing car
 * would send nothing, and the iPhone should hear 0 m/s at a red light. Its raw value is taken as m/s.
 */
internal object EcarxWheelSpeed : VehicleSpeedSource {
    private const val TAG = "xcertplay-ecarx-speed"
    private const val CAR = "com.ecarx.xui.adaptapi.car.Car"
    private const val ICAR = "com.ecarx.xui.adaptapi.car.ICar"
    private const val SENSOR = "com.ecarx.xui.adaptapi.car.sensor.ISensor"
    private const val SENSOR_TYPE_CAR_SPEED = 1048832
    private const val SENSOR_TYPE_GEAR = 2097664
    private const val GEAR_PARK = 2097712
    private const val GEAR_REVERSE = 2097728
    private const val GEAR_NEUTRAL = 2097680
    private const val GEAR_DRIVE = 2097696
    private val GEAR_FORWARD = 2097665..2097674 // GEAR_FIRST..GEAR_TENTH, manual mode
    private const val MAX_METERS_PER_SECOND = 90.0
    private const val READ_MILLIS = 250L
    private const val GEAR_EVERY_READS = 4

    private var context: Context? = null
    private var sensor: Any? = null
    private var executor: ScheduledExecutorService? = null
    @Volatile private var latestReading: VehicleSpeedReading? = null
    private var gear: VehicleGear = VehicleGear.DRIVE
    private var reads = 0
    private var firstSampleLogged = false
    private var failureLogged = false

    /**
     * No position for the iPhone: when only wheel speed is available,
     * only `$PASCD` is sent beside the iPhone's own GPS.
     */
    object NoPosition : Iap2LocationProvider {
        override fun start(): Boolean = false
        override fun stop() = Unit
        override fun latestNmea(): String? = null
    }

    /** Whether the ECARX car SDK is on this head unit. */
    fun present(): Boolean = runCatching { Class.forName(CAR); Class.forName(SENSOR) }.isSuccess

    fun attach(appContext: Context) = apply { context = appContext.applicationContext }

    @Synchronized
    override fun requestSpeed(enabled: Boolean) {
        if (enabled) {
            if (executor != null) return
            reads = 0
            firstSampleLogged = false
            executor = Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "ecarx-wheel-speed").apply { isDaemon = true }
            }.also { it.scheduleWithFixedDelay(::poll, 0, READ_MILLIS, TimeUnit.MILLISECONDS) }
            Log.i(TAG, "ECARX wheel speed started")
        } else {
            val running = synchronized(this) { executor.also { executor = null } } ?: return
            running.shutdown()
            Log.i(TAG, "ECARX wheel speed stopped")
        }
    }

    override fun latestSpeed(): VehicleSpeedReading? = latestReading

    override fun awaitSpeed(timeoutMillis: Long): VehicleSpeedReading? {
        val current = latestReading
        if (current != null) return current
        if (present()) {
            poll()
            return latestReading ?: VehicleSpeedReading(SystemClock.elapsedRealtime(), 0.0, gear)
        }
        return null
    }

    internal fun gear(event: Int): VehicleGear? = when (event) {
        GEAR_PARK -> VehicleGear.PARK
        GEAR_REVERSE -> VehicleGear.REVERSE
        GEAR_NEUTRAL -> VehicleGear.NEUTRAL
        GEAR_DRIVE, in GEAR_FORWARD -> VehicleGear.DRIVE
        else -> null
    }

    internal fun metersPerSecond(raw: Float): Double? =
        raw.toDouble().takeIf { it.isFinite() && it in 0.0..MAX_METERS_PER_SECOND }

    private fun poll() {
        val s = sensor() ?: return
        if (reads++ % GEAR_EVERY_READS == 0) {
            val event = runCatching { call(s, "getSensorEvent", SENSOR_TYPE_GEAR) as Int }.getOrNull()
            val next = event?.let(::gear)
            if (next != null && next != gear) {
                gear = next
                Log.i(TAG, "gear $next (event $event)")
            }
        }
        val before = SystemClock.elapsedRealtime()
        val raw = runCatching { call(s, "getSensorLatestValue", SENSOR_TYPE_CAR_SPEED) as Float }.getOrNull() ?: return
        val speed = metersPerSecond(raw) ?: return
        val now = (before + SystemClock.elapsedRealtime()) / 2
        latestReading = VehicleSpeedReading(now, speed, gear)
        if (!firstSampleLogged) {
            firstSampleLogged = true
            Log.i(TAG, "first ECARX wheel speed raw $raw = ${"%.1f".format(speed * 3.6)} km/h gear=$gear")
        }
    }

    private fun sensor(): Any? = sensor ?: runCatching {
        val app = context ?: return null
        val car = Class.forName(CAR).getMethod("create", Context::class.java).invoke(null, app)
        val manager = Class.forName(ICAR).getMethod("getSensorManager").invoke(car)!!
        val status = call(manager, "isSensorSupported", SENSOR_TYPE_CAR_SPEED)?.toString()
        check(status != null && status != "notavailable" && status != "error") { "speed sensor $status" }
        Log.i(TAG, "ECARX speed sensor $status")
        manager
    }.onSuccess { sensor = it }.onFailure {
        if (!failureLogged) Log.w(TAG, "ECARX sensors unavailable, retrying", it)
        failureLogged = true
    }.getOrNull()

    private fun call(sensor: Any, method: String, type: Int): Any? =
        Class.forName(SENSOR).getMethod(method, Int::class.javaPrimitiveType).invoke(sensor, type)
}
