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
        currentGuidance = CarPlayNavigationUpdate(isActive = false)
        onNavigationUpdate(currentGuidance)
    }

    private fun decodeRouteGuidanceUpdate(frame: Iap2Frame) {
        try {
            val reader = Iap2BodyReader.of(frame)
            val currentRoad = reader.optionalString(0)
            val destination = reader.optionalString(1)
            val totalRemainDis = reader.optionalU32(2)?.toInt()
            val totalRemainTime = reader.optionalU32(3)?.toInt()
            val distToNextManeuver = reader.optionalU32(4)?.toInt()
            val state = reader.optionalU8(5) // 0: inactive, 1: active, 2: rerouting, 3: arrived

            val isActive = state == null || state == 1 || state == 2
            val isRerouting = state == 2

            if (state == 0 || state == 3) {
                // Navigation ended or arrived
                resetNavigation()
                return
            }

            currentGuidance = currentGuidance.copy(
                isActive = isActive,
                isRerouting = isRerouting,
                currentRoadName = currentRoad ?: currentGuidance.currentRoadName,
                destinationName = destination ?: currentGuidance.destinationName,
                totalRemainingDistanceMeters = totalRemainDis ?: currentGuidance.totalRemainingDistanceMeters,
                totalRemainingTimeSeconds = totalRemainTime ?: currentGuidance.totalRemainingTimeSeconds,
                distanceToNextManeuverMeters = distToNextManeuver ?: currentGuidance.distanceToNextManeuverMeters,
            )
            onNavigationUpdate(currentGuidance)
        } catch (e: Exception) {
            // Guard against malformed TLV
        }
    }

    private fun decodeRouteGuidanceManeuverUpdate(frame: Iap2Frame) {
        try {
            val reader = Iap2BodyReader.of(frame)
            val index = reader.optionalU16(0) ?: 0
            val rawType = reader.optionalU16(1) ?: reader.optionalU8(1) ?: 0
            val turnAngle = reader.optionalI16(2)?.toFloat()
            val instruction = reader.optionalString(3)
            val roadName = reader.optionalString(4)
            val distance = reader.optionalU32(5)?.toInt()
            val exitNumber = reader.optionalString(6)

            val maneuverType = mapRawManeuverType(rawType)
            val maneuver = CarPlayManeuver(
                index = index,
                type = maneuverType,
                rawType = rawType,
                turnAngleDegrees = turnAngle,
                instruction = instruction,
                roadName = roadName,
                distanceMeters = distance,
                exitNumber = exitNumber,
            )
            maneuverMap[index] = maneuver

            // The maneuver with smallest index >= 0 is typically the current/next upcoming turn
            val upcoming = maneuverMap.values.sortedBy { it.index }
            val currentManeuver = upcoming.firstOrNull()

            currentGuidance = currentGuidance.copy(
                isActive = true,
                currentManeuver = currentManeuver,
                upcomingManeuvers = upcoming,
                nextRoadName = currentManeuver?.roadName ?: currentGuidance.nextRoadName,
                distanceToNextManeuverMeters = currentManeuver?.distanceMeters ?: currentGuidance.distanceToNextManeuverMeters,
            )
            onNavigationUpdate(currentGuidance)
        } catch (e: Exception) {
            // Guard against malformed TLV
        }
    }

    private fun decodeLaneGuidanceUpdate(frame: Iap2Frame) {
        try {
            val reader = Iap2BodyReader.of(frame)
            // Extract lane list if present
            val lanes = mutableListOf<LaneDirection>()
            if (reader.has(0)) {
                // Group or byte-array representation of lanes
                val laneGroup = reader.optionalGroup(0)
                if (laneGroup != null) {
                    val count = laneGroup.optionalU8(0) ?: 0
                    for (i in 0 until count) {
                        val dir = laneGroup.optionalU8(i + 1) ?: 1
                        lanes.add(LaneDirection(directionCode = dir, isPreferred = true))
                    }
                }
            }
            if (lanes.isNotEmpty()) {
                currentGuidance = currentGuidance.copy(
                    laneGuidance = LaneGuidanceInfo(lanes = lanes, isHighlighted = true)
                )
                onNavigationUpdate(currentGuidance)
            }
        } catch (e: Exception) {
            // Guard against malformed TLV
        }
    }

    private fun mapRawManeuverType(raw: Int): CarPlayManeuverType {
        return when (raw) {
            1 -> CarPlayManeuverType.CONTINUE
            2 -> CarPlayManeuverType.STRAIGHT
            3 -> CarPlayManeuverType.TURN_LEFT
            4 -> CarPlayManeuverType.TURN_RIGHT
            5 -> CarPlayManeuverType.TURN_SLIGHT_LEFT
            6 -> CarPlayManeuverType.TURN_SLIGHT_RIGHT
            7 -> CarPlayManeuverType.TURN_SHARP_LEFT
            8 -> CarPlayManeuverType.TURN_SHARP_RIGHT
            9 -> CarPlayManeuverType.U_TURN_LEFT
            10 -> CarPlayManeuverType.U_TURN_RIGHT
            11 -> CarPlayManeuverType.KEEP_LEFT
            12 -> CarPlayManeuverType.KEEP_RIGHT
            13 -> CarPlayManeuverType.ON_RAMP
            14 -> CarPlayManeuverType.OFF_RAMP
            15 -> CarPlayManeuverType.FORK_LEFT
            16 -> CarPlayManeuverType.FORK_RIGHT
            17 -> CarPlayManeuverType.MERGE_LEFT
            18 -> CarPlayManeuverType.MERGE_RIGHT
            19 -> CarPlayManeuverType.ROUNDABOUT_ENTER
            20 -> CarPlayManeuverType.ROUNDABOUT_EXIT
            21 -> CarPlayManeuverType.ARRIVED_AT_DESTINATION
            else -> CarPlayManeuverType.UNKNOWN
        }
    }
}
