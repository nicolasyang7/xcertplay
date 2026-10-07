package com.shilapi.xcertplay.iap2.message

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** One entry of the iPhone's maneuver list, reconstructed from RouteGuidanceManeuverUpdate (0x5202). */
data class Iap2Maneuver(
    val index: Int,
    /** Apple RouteGuidanceManeuverType, `null` when the iPhone did not send it. */
    val type: Int? = null,
    val description: String? = null,
    val afterRoadName: String? = null,
    val drivingSide: Int? = null,
)

/**
 * Route guidance reconstructed from 0x5201/0x5202.
 *
 * A `null` field means the iPhone has not reported it for this route. It is never a zero: consumers
 * must omit it rather than render 0 or clear a value another source supplied.
 *
 * [arrivalEpochSeconds] and [timeRemainingSeconds] are seconds: confirmed against an Amap CarPlay
 * trace, where the arrival epoch equalled the frame time plus the reported remaining time.
 */
data class Iap2NavigationState(
    /** Increments whenever the iPhone session changes, so consumers can drop stale callbacks. */
    val sessionId: Long,
    val routeActive: Boolean = false,
    /** Wire RouteGuidanceState: 0 = no route, 2 = arrived; other values are passed through. */
    val guidanceState: Int? = null,
    val maneuverState: Int? = null,
    val currentRoadName: String? = null,
    val destinationName: String? = null,
    /** The guiding app as the phone names it (an Amap trace reported "Amap"). */
    val sourceName: String? = null,
    val arrivalEpochSeconds: Long? = null,
    val timeRemainingSeconds: Long? = null,
    val distanceRemainingMeters: Long? = null,
    val distanceToNextManeuverMeters: Long? = null,
    val maneuverCount: Int? = null,
    /** Maneuver the driver is approaching; `null` until its 0x5202 details have arrived. */
    val nextManeuver: Iap2Maneuver? = null,
    val updatedAtRealtimeMillis: Long = 0,
) {
    /**
     * True once there is something to show. The phone reports state 3 and then partial updates before
     * the route is computed, so [routeActive] alone is too early to drive a display.
     */
    val hasGuidance: Boolean
        get() = routeActive && distanceRemainingMeters != null && nextManeuver?.type != null
}

/**
 * Merges incremental route-guidance updates (the iPhone omits attributes that did not change).
 *
 * Only RouteGuidanceState NoRouteSet (0) or Arrived (2) ends a route. The iPhone also sends an empty
 * maneuver list while rerouting; that keeps the last maneuver instead of blanking it, matching DiPlay.
 */
class Iap2NavigationAccumulator(
    private val realtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var sessionId = 0L
    private var state = Iap2NavigationState(sessionId)
    private val maneuvers = mutableMapOf<Int, Iap2Maneuver>()
    private var activeIndex: Int? = null

    /** Starts a new iPhone session: drops the route and every cached maneuver. */
    @Synchronized
    fun reset(): Iap2NavigationState {
        sessionId += 1
        maneuvers.clear()
        activeIndex = null
        state = Iap2NavigationState(sessionId, updatedAtRealtimeMillis = realtimeMillis())
        return state
    }

    @Synchronized
    fun current(): Iap2NavigationState = state

    /** Returns the new state, or `null` when [frame] is not a route-guidance message. */
    @Synchronized
    fun update(frame: Iap2Frame): Iap2NavigationState? = when (frame.messageId) {
        ROUTE_GUIDANCE_UPDATE -> applyRouteUpdate(frame)
        ROUTE_GUIDANCE_MANEUVER_UPDATE -> applyManeuverUpdate(frame)
        else -> null
    }

    private fun applyRouteUpdate(frame: Iap2Frame): Iap2NavigationState {
        val body = Iap2Messages.reader(frame)
        val guidanceState = body.optionalU8(GUIDANCE_STATE)
        if (guidanceState == NO_ROUTE_SET || guidanceState == ARRIVED) {
            maneuvers.clear()
            activeIndex = null
            state = Iap2NavigationState(
                sessionId = sessionId,
                guidanceState = guidanceState,
                updatedAtRealtimeMillis = realtimeMillis(),
            )
            return state
        }
        body.optionalU16List(CURRENT_MANEUVER_LIST)?.firstOrNull()?.let { activeIndex = it }
        // Before the route is computed the phone sends an arrival equal to "now" with 0 remaining time.
        // Neither is data, and the real arrival is signalled by the Arrived state instead.
        val remaining = body.optionalU64(TIME_REMAINING)
        val placeholderTiming = remaining == 0L
        state = state.copy(
            routeActive = true,
            guidanceState = guidanceState ?: state.guidanceState,
            maneuverState = body.optionalU8(MANEUVER_STATE) ?: state.maneuverState,
            currentRoadName = body.optionalString(CURRENT_ROAD_NAME) ?: state.currentRoadName,
            destinationName = body.optionalString(DESTINATION_NAME) ?: state.destinationName,
            sourceName = body.optionalString(SOURCE_NAME) ?: state.sourceName,
            arrivalEpochSeconds = if (placeholderTiming) {
                state.arrivalEpochSeconds
            } else {
                body.optionalU64(ESTIMATED_ARRIVAL)?.takeIf { it > 0 } ?: state.arrivalEpochSeconds
            },
            timeRemainingSeconds = if (placeholderTiming) state.timeRemainingSeconds else remaining ?: state.timeRemainingSeconds,
            distanceRemainingMeters = body.optionalU32(DISTANCE_REMAINING) ?: state.distanceRemainingMeters,
            distanceToNextManeuverMeters =
                body.optionalU32(DISTANCE_TO_NEXT_MANEUVER) ?: state.distanceToNextManeuverMeters,
            maneuverCount = body.optionalU16(MANEUVER_COUNT) ?: state.maneuverCount,
            nextManeuver = activeIndex?.let { maneuvers[it] },
            updatedAtRealtimeMillis = realtimeMillis(),
        )
        return state
    }

    private fun applyManeuverUpdate(frame: Iap2Frame): Iap2NavigationState {
        val body = Iap2Messages.reader(frame)
        val index = body.optionalU16(MANEUVER_INDEX) ?: return state
        val previous = maneuvers[index]
        maneuvers[index] = Iap2Maneuver(
            index = index,
            type = body.optionalU8(MANEUVER_TYPE) ?: previous?.type,
            description = body.optionalString(MANEUVER_DESCRIPTION) ?: previous?.description,
            afterRoadName = body.optionalString(AFTER_MANEUVER_ROAD_NAME) ?: previous?.afterRoadName,
            drivingSide = lenientByte(body.optionalRaw(DRIVING_SIDE)) ?: previous?.drivingSide,
        )
        if (index == activeIndex) {
            state = state.copy(nextManeuver = maneuvers[index], updatedAtRealtimeMillis = realtimeMillis())
        }
        return state
    }

    /** A single byte, else null: the phone's logs show a zero byte where a one-byte value is absent or 0. */
    private fun lenientByte(raw: ByteArray?): Int? = raw?.takeIf { it.size == 1 }?.let { it[0].toInt() and 0xff }

    companion object {
        const val ROUTE_GUIDANCE_UPDATE = 0x5201
        const val ROUTE_GUIDANCE_MANEUVER_UPDATE = 0x5202

        private const val NO_ROUTE_SET = 0
        private const val ARRIVED = 2

        // RouteGuidanceUpdate parameters. 1, 4, 5, 6, 7, 10, 13, 14 and 19 were seen in an Amap CarPlay
        // trace and 3 is decoded by DiPlay; 2 (ManeuverState) follows the reference body but has not
        // yet appeared in a trace.
        private const val GUIDANCE_STATE = 1
        private const val MANEUVER_STATE = 2
        private const val CURRENT_ROAD_NAME = 3
        private const val DESTINATION_NAME = 4
        private const val ESTIMATED_ARRIVAL = 5
        private const val TIME_REMAINING = 6
        private const val DISTANCE_REMAINING = 7
        private const val DISTANCE_TO_NEXT_MANEUVER = 10
        private const val CURRENT_MANEUVER_LIST = 13
        private const val MANEUVER_COUNT = 14
        private const val SOURCE_NAME = 19

        // RouteGuidanceManeuverUpdate parameters; 1 to 4 were seen in the same Amap trace.
        private const val MANEUVER_INDEX = 1
        private const val MANEUVER_DESCRIPTION = 2
        private const val MANEUVER_TYPE = 3
        private const val AFTER_MANEUVER_ROAD_NAME = 4
        private const val DRIVING_SIDE = 8
    }
}
