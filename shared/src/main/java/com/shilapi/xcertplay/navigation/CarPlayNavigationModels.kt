package com.shilapi.xcertplay.navigation

/**
 * Normalized navigation models extracted from CarPlay iAP2 Route Guidance protocol.
 */

enum class CarPlayManeuverType(val code: Int) {
    UNKNOWN(0),
    CONTINUE(1),
    STRAIGHT(2),
    TURN_LEFT(3),
    TURN_RIGHT(4),
    TURN_SLIGHT_LEFT(5),
    TURN_SLIGHT_RIGHT(6),
    TURN_SHARP_LEFT(7),
    TURN_SHARP_RIGHT(8),
    U_TURN_LEFT(9),
    U_TURN_RIGHT(10),
    KEEP_LEFT(11),
    KEEP_RIGHT(12),
    ON_RAMP(13),
    OFF_RAMP(14),
    FORK_LEFT(15),
    FORK_RIGHT(16),
    MERGE_LEFT(17),
    MERGE_RIGHT(18),
    ROUNDABOUT_ENTER(19),
    ROUNDABOUT_EXIT(20),
    ARRIVED_AT_DESTINATION(21);

    companion object {
        fun fromCode(code: Int): CarPlayManeuverType =
            entries.find { it.code == code } ?: UNKNOWN
    }
}

data class CarPlayManeuver(
    val index: Int = 0,
    val type: CarPlayManeuverType = CarPlayManeuverType.UNKNOWN,
    val rawType: Int = 0,
    val turnAngleDegrees: Float? = null,
    val instruction: String? = null,
    val roadName: String? = null,
    val distanceMeters: Int? = null,
    val exitNumber: String? = null,
)

data class LaneDirection(
    val directionCode: Int, // 1: straight, 2: left, 3: right, 4: u-turn, etc.
    val isPreferred: Boolean = false,
)

data class LaneGuidanceInfo(
    val lanes: List<LaneDirection> = emptyList(),
    val isHighlighted: Boolean = true,
)

data class CarPlayNavigationUpdate(
    val isActive: Boolean = true,
    val isRerouting: Boolean = false,
    val currentManeuver: CarPlayManeuver? = null,
    val upcomingManeuvers: List<CarPlayManeuver> = emptyList(),
    val distanceToNextManeuverMeters: Int? = null,
    val totalRemainingDistanceMeters: Int? = null,
    val totalRemainingTimeSeconds: Int? = null,
    val currentRoadName: String? = null,
    val nextRoadName: String? = null,
    val destinationName: String? = null,
    val laneGuidance: LaneGuidanceInfo? = null,
    val isNightMode: Boolean = false,
)

data class TrafficLightInfo(
    val status: Int, // 1: Red, 2: Green, 3: Yellow
    val direction: Int, // 1: Left, 2: Straight, 3: Right, 4: U-Turn
    val countdownSeconds: Int,
)

data class SectionSpeedInfo(
    val averageSpeedKmh: Int,
    val limitedSpeedKmh: Int,
    val remainingDistanceMeters: Int? = null,
    val remainingDistanceText: String? = null,
)
