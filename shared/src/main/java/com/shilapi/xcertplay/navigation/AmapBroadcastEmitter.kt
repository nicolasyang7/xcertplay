package com.shilapi.xcertplay.navigation

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emits AutoNavi Standard Broadcast (AUTONAVI_STANDARD_BROADCAST_SEND) for NaviTool to render on HUD.
 */
class AmapBroadcastEmitter(
    private val context: Context,
    private val broadcastAction: String = ACTION_AUTONAVI_SEND,
    private val targetPackage: String? = null,
) {
    companion object {
        const val ACTION_AUTONAVI_SEND = "AUTONAVI_STANDARD_BROADCAST_SEND"

        // KEY_TYPE definitions
        const val KEY_TYPE_GUIDANCE = 10001
        const val KEY_TYPE_SYSTEM_STATE = 10019
        const val KEY_TYPE_SECTION_SPEED = 12110
        const val KEY_TYPE_LANE_LINE = 13012
        const val KEY_TYPE_TRAFFIC_LIGHT = 60073

        // EXTRA_STATE definitions
        const val STATE_NAVIGATING = 0
        const val STATE_REROUTING = 1
        const val STATE_NAV_ENDED = 9
        const val STATE_DAY_MODE = 37
        const val STATE_NIGHT_MODE = 38

        // AutoNavi standard-broadcast ICON codes (0 = cruise / no maneuver).
        private const val ICON_LEFT = 2
        private const val ICON_RIGHT = 3
        private const val ICON_SLIGHT_LEFT = 4
        private const val ICON_SLIGHT_RIGHT = 5
        private const val ICON_SHARP_LEFT = 6
        private const val ICON_SHARP_RIGHT = 7
        private const val ICON_U_TURN = 8
        private const val ICON_STRAIGHT = 9
        private const val ICON_ENTER_ROUNDABOUT = 11
        private const val ICON_EXIT_ROUNDABOUT = 12
        private const val ICON_ARRIVED = 15

        internal fun amapIconFor(type: CarPlayManeuverType?): Int = when (type) {
            null -> 0
            CarPlayManeuverType.TURN_LEFT -> ICON_LEFT
            CarPlayManeuverType.TURN_RIGHT -> ICON_RIGHT
            CarPlayManeuverType.TURN_SLIGHT_LEFT,
            CarPlayManeuverType.KEEP_LEFT,
            CarPlayManeuverType.FORK_LEFT,
            CarPlayManeuverType.MERGE_LEFT,
            -> ICON_SLIGHT_LEFT
            CarPlayManeuverType.TURN_SLIGHT_RIGHT,
            CarPlayManeuverType.KEEP_RIGHT,
            CarPlayManeuverType.FORK_RIGHT,
            CarPlayManeuverType.MERGE_RIGHT,
            -> ICON_SLIGHT_RIGHT
            CarPlayManeuverType.TURN_SHARP_LEFT -> ICON_SHARP_LEFT
            CarPlayManeuverType.TURN_SHARP_RIGHT -> ICON_SHARP_RIGHT
            CarPlayManeuverType.U_TURN_LEFT,
            CarPlayManeuverType.U_TURN_RIGHT,
            -> ICON_U_TURN
            CarPlayManeuverType.ROUNDABOUT_ENTER -> ICON_ENTER_ROUNDABOUT
            CarPlayManeuverType.ROUNDABOUT_EXIT -> ICON_EXIT_ROUNDABOUT
            CarPlayManeuverType.ARRIVED_AT_DESTINATION -> ICON_ARRIVED
            CarPlayManeuverType.CONTINUE,
            CarPlayManeuverType.STRAIGHT,
            CarPlayManeuverType.ON_RAMP,
            CarPlayManeuverType.OFF_RAMP,
            CarPlayManeuverType.UNKNOWN,
            -> ICON_STRAIGHT
        }
    }

    private var initialTotalDistanceMeters: Int = 0

    /**
     * Emits P0 turn-by-turn guidance and route overview (KEY_TYPE = 10001).
     */
    fun emitNavigationUpdate(guidance: CarPlayNavigationUpdate) {
        if (!guidance.isActive) {
            emitNavigationEnded()
            return
        }

        if (initialTotalDistanceMeters == 0 && (guidance.totalRemainingDistanceMeters ?: 0) > 0) {
            initialTotalDistanceMeters = guidance.totalRemainingDistanceMeters!!
        }

        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_GUIDANCE)
            putExtra("EXTRA_STATE", if (guidance.isRerouting) STATE_REROUTING else STATE_NAVIGATING)

            // Turn Icon
            val iconCode = mapManeuverToAmapIcon(guidance.currentManeuver)
            putExtra("ICON", iconCode)
            putExtra("NEW_ICON", iconCode)

            // Segment distance (distance to next turn)
            val segDis = guidance.distanceToNextManeuverMeters ?: 0
            putExtra("SEG_REMAIN_DIS", segDis)
            putExtra("SEG_REMAIN_DIS_AUTO", formatDistanceText(segDis))

            // Road names
            putExtra("NEXT_ROAD_NAME", guidance.nextRoadName ?: guidance.currentManeuver?.roadName ?: "")
            putExtra("CUR_ROAD_NAME", guidance.currentRoadName ?: "")

            // Total remaining route distance & time
            val totalDis = guidance.totalRemainingDistanceMeters ?: 0
            val totalTime = guidance.totalRemainingTimeSeconds ?: 0
            putExtra("ROUTE_REMAIN_DIS", totalDis)
            putExtra("ROUTE_REMAIN_DIS_AUTO", formatDistanceText(totalDis))
            putExtra("ROUTE_REMAIN_TIME", totalTime)
            putExtra("ROUTE_REMAIN_TIME_AUTO", formatTimeText(totalTime))
            putExtra("ROUTE_ALL_DIS", if (initialTotalDistanceMeters > 0) initialTotalDistanceMeters else totalDis)

            // ETA
            if (totalTime > 0) {
                putExtra("ETA_TEXT", calculateEtaText(totalTime))
            }

            // Destination
            guidance.destinationName?.let { putExtra("endPOIName", it) }
        }

        context.sendBroadcast(intent)

        // Lane Guidance (KEY_TYPE = 13012)
        guidance.laneGuidance?.let { emitLaneGuidance(it) }
    }

    /**
     * Emits lane line recommendations (KEY_TYPE = 13012).
     */
    fun emitLaneGuidance(laneInfo: LaneGuidanceInfo) {
        if (laneInfo.lanes.isEmpty()) return

        val laneJson = buildLaneJson(laneInfo)
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_LANE_LINE)
            putExtra("EXTRA_DRIVE_WAY", laneJson)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Emits traffic light countdown and status (KEY_TYPE = 60073).
     */
    fun emitTrafficLight(info: TrafficLightInfo) {
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_TRAFFIC_LIGHT)
            putExtra("trafficLightStatus", info.status) // 1: Red, 2: Green, 3: Yellow
            putExtra("dir", info.direction)             // 1: Left, 2: Straight, 3: Right, 4: U-turn
            putExtra("redLightCountDownSeconds", info.countdownSeconds)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Emits section speed camera tracking (KEY_TYPE = 12110).
     */
    fun emitSectionSpeed(info: SectionSpeedInfo) {
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SECTION_SPEED)
            putExtra("AVERAGE_SPEED", info.averageSpeedKmh)
            putExtra("AVG_SPEED", info.averageSpeedKmh)
            putExtra("LIMITED_SPEED", info.limitedSpeedKmh)
            val distText = info.remainingDistanceText ?: formatDistanceText(info.remainingDistanceMeters ?: 0)
            putExtra("END_DISTANCE_TEXT", distText)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Emits day/night mode switch (KEY_TYPE = 10019).
     */
    fun emitDayNightMode(isNight: Boolean) {
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SYSTEM_STATE)
            putExtra("EXTRA_STATE", if (isNight) STATE_NIGHT_MODE else STATE_DAY_MODE)
        }
        context.sendBroadcast(intent)
    }

    /**
     * Emits navigation end / clear signal to reset HUD (KEY_TYPE = 10019, EXTRA_STATE = 9).
     */
    fun emitNavigationEnded() {
        initialTotalDistanceMeters = 0
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SYSTEM_STATE)
            putExtra("EXTRA_STATE", STATE_NAV_ENDED)
        }
        context.sendBroadcast(intent)
    }

    private fun mapManeuverToAmapIcon(maneuver: CarPlayManeuver?): Int = amapIconFor(maneuver?.type)

    private fun formatDistanceText(meters: Int): String {
        return when {
            meters < 1000 -> "${meters}米"
            else -> String.format(Locale.US, "%.1f公里", meters / 1000.0)
        }
    }

    private fun formatTimeText(seconds: Int): String {
        val minutes = seconds / 60
        return when {
            minutes < 60 -> "${minutes}分钟"
            else -> {
                val hours = minutes / 60
                val remainMins = minutes % 60
                if (remainMins > 0) "${hours}小时${remainMins}分钟" else "${hours}小时"
            }
        }
    }

    private fun calculateEtaText(secondsRemaining: Int): String {
        val arrivalTimestamp = System.currentTimeMillis() + (secondsRemaining * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(arrivalTimestamp))
    }

    private fun buildLaneJson(laneInfo: LaneGuidanceInfo): String {
        val sb = StringBuilder("[")
        laneInfo.lanes.forEachIndexed { idx, lane ->
            if (idx > 0) sb.append(",")
            sb.append("""{"lane":${idx + 1},"direction":${lane.directionCode},"recommended":${lane.isPreferred},"is_recommended":${lane.isPreferred}}""")
        }
        sb.append("]")
        return sb.toString()
    }
}
