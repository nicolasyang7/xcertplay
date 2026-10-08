package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.catalog.Iap2Endpoint
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * Decodes iAP2 Route Guidance CSM frames (0x5201, 0x5202, 0x5204) into normalized [CarPlayNavigationUpdate].
 */
class CarPlayRouteGuidanceDecoder(
    private val onNavigationUpdate: (CarPlayNavigationUpdate) -> Unit = {},
) {
    private var currentGuidance = CarPlayNavigationUpdate(isActive = false)
    private val maneuverMap = mutableMapOf<Int, CarPlayManeuver>()
    private var currentManeuverIds: List<Int> = emptyList()

    /**
     * Inspects an incoming iAP2 frame. If it is a route guidance message, decodes it and dispatches
     * an updated [CarPlayNavigationUpdate].
     *
     * @return true if the frame was a recognized route guidance message.
     */
    fun onIncomingFrame(frame: Iap2Frame): Boolean {
        return when (frame.messageId) {
            0x5201 -> { // RouteGuidanceUpdate
                decodeRouteGuidanceUpdate(frame)
                true
            }
            0x5202 -> { // RouteGuidanceManeuverUpdate
                decodeRouteGuidanceManeuverUpdate(frame)
                true
            }
            0x5204 -> { // LaneGuidanceInfoUpdate
                decodeLaneGuidanceUpdate(frame)
                true
            }
            0x5203 -> { // StopRouteGuidanceUpdates
                resetNavigation()
                true
            }
            else -> false
        }
    }

    fun resetNavigation() {
        maneuverMap.clear()
        currentManeuverIds = emptyList()
        currentGuidance = CarPlayNavigationUpdate(isActive = false)
        onNavigationUpdate(currentGuidance)
    }

    // 0x5201 RouteGuidanceUpdate parameter ids (see docs/iap2-route-guidance-fields.md).
    private fun decodeRouteGuidanceUpdate(frame: Iap2Frame) {
        try {
            val reader = Iap2BodyReader.of(frame)
            val state = reader.optionalU8(P_STATE)
            // 0 = not active, 2 = arrived; anything else keeps guidance running.
            if (state == STATE_NOT_ACTIVE || state == STATE_ARRIVED) {
                resetNavigation()
                return
            }
            val currentRoad = reader.optionalString(P_CURRENT_ROAD)
            val destination = reader.optionalString(P_DESTINATION)
            val remainingMillis = reader.optionalU64(P_TIME_REMAINING)
            val totalRemainDis = reader.optionalU32(P_DISTANCE_REMAINING)?.toInt()
            val distToNextManeuver = reader.optionalU32(P_DISTANCE_TO_NEXT)?.toInt()
            val maneuverList = reader.optionalU16List(P_MANEUVER_LIST)

            if (maneuverList != null) currentManeuverIds = maneuverList

            currentGuidance = currentGuidance.copy(
                isActive = true,
                isRerouting = state == STATE_REROUTING,
                currentRoadName = currentRoad ?: currentGuidance.currentRoadName,
                destinationName = destination ?: currentGuidance.destinationName,
                totalRemainingDistanceMeters = totalRemainDis ?: currentGuidance.totalRemainingDistanceMeters,
                totalRemainingTimeSeconds = remainingMillis?.let { (it / 1000L).toInt() }
                    ?: currentGuidance.totalRemainingTimeSeconds,
                distanceToNextManeuverMeters = distToNextManeuver ?: currentGuidance.distanceToNextManeuverMeters,
            )
            publishWithManeuvers()
        } catch (e: Exception) {
            // Guard against malformed TLV
        }
    }

    // 0x5202 RouteGuidanceManeuverUpdate parameter ids.
    private fun decodeRouteGuidanceManeuverUpdate(frame: Iap2Frame) {
        try {
            val reader = Iap2BodyReader.of(frame)
            val index = reader.optionalU16(P_M_INDEX) ?: return
            val rawType = reader.optionalU8(P_M_TYPE) ?: 0
            maneuverMap[index] = CarPlayManeuver(
                index = index,
                type = mapRawManeuverType(rawType),
                rawType = rawType,
                instruction = reader.optionalString(P_M_DESCRIPTION),
                roadName = reader.optionalString(P_M_AFTER_ROAD),
                distanceMeters = reader.optionalU16(P_M_DISTANCE),
                exitNumber = reader.optionalString(P_M_EXIT_INFO),
            )
            currentGuidance = currentGuidance.copy(isActive = true)
            publishWithManeuvers()
        } catch (e: Exception) {
            // Guard against malformed TLV
        }
    }

    // 0x5204 LaneGuidanceInfoUpdate: the laneInfoList children are not documented, so lane data is
    // not decoded rather than guessed.
    private fun decodeLaneGuidanceUpdate(frame: Iap2Frame) = Unit

    private fun publishWithManeuvers() {
        val ordered = maneuverMap.values.sortedBy { it.index }
        val current = currentManeuverIds.firstNotNullOfOrNull { maneuverMap[it] } ?: ordered.firstOrNull()
        currentGuidance = currentGuidance.copy(
            currentManeuver = current,
            upcomingManeuvers = ordered,
            nextRoadName = current?.roadName ?: currentGuidance.nextRoadName,
        )
        onNavigationUpdate(currentGuidance)
    }

    // iAP2 ManeuverType values; unknown values fall back to UNKNOWN (rawType is kept for logging).
    private fun mapRawManeuverType(raw: Int): CarPlayManeuverType = when (raw) {
        0 -> CarPlayManeuverType.TURN_LEFT
        1 -> CarPlayManeuverType.TURN_RIGHT
        2 -> CarPlayManeuverType.STRAIGHT
        3, 17 -> CarPlayManeuverType.U_TURN_LEFT
        4 -> CarPlayManeuverType.CONTINUE
        5 -> CarPlayManeuverType.ROUNDABOUT_ENTER
        6 -> CarPlayManeuverType.ROUNDABOUT_EXIT
        7 -> CarPlayManeuverType.OFF_RAMP
        8 -> CarPlayManeuverType.ON_RAMP
        9, 11, 18 -> CarPlayManeuverType.ARRIVED_AT_DESTINATION
        12 -> CarPlayManeuverType.KEEP_LEFT
        13 -> CarPlayManeuverType.KEEP_RIGHT
        else -> CarPlayManeuverType.UNKNOWN
    }

    private companion object {
        const val P_STATE = 1
        const val P_CURRENT_ROAD = 3
        const val P_DESTINATION = 4
        const val P_TIME_REMAINING = 6
        const val P_DISTANCE_REMAINING = 7
        const val P_DISTANCE_TO_NEXT = 10
        const val P_MANEUVER_LIST = 13

        const val P_M_INDEX = 1
        const val P_M_DESCRIPTION = 2
        const val P_M_TYPE = 3
        const val P_M_AFTER_ROAD = 4
        const val P_M_DISTANCE = 5
        const val P_M_EXIT_INFO = 13

        const val STATE_NOT_ACTIVE = 0
        const val STATE_ARRIVED = 2
        const val STATE_REROUTING = 5
    }
}
