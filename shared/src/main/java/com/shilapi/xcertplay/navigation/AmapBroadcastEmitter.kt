package com.shilapi.xcertplay.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Emits AutoNavi Standard Broadcast (AUTONAVI_STANDARD_BROADCAST_SEND) for NaviTool to render on HUD.
 */
class AmapBroadcastEmitter(
    private val context: Context,
    private val broadcastAction: String = ACTION_AUTONAVI_SEND,
    // Standard global AMap broadcast; the active dynamic receiver subscribes by action.
    private val targetPackage: String? = null,
    // Modified AMap external bridge requires this marker on the global action.
    private val factoryHudBridge: Boolean = false,
) {
    companion object {
        const val ORIGIN_SOURCE = "xcertplay_carplay"
        private const val TAG = "CarPlay-HUD-TX"
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
    }

    private var initialTotalDistanceMeters: Int = 0
    @Volatile private var navigationSent = false

    /**
     * Emits P0 turn-by-turn guidance and route overview (KEY_TYPE = 10001).
     */
    @Synchronized
    fun emitNavigationUpdate(guidance: CarPlayNavigationUpdate) {
        if (!guidance.isActive) {
            emitNavigationEnded()
            return
        }

        val iconCode = mapManeuverToAmapIcon(guidance.currentManeuver)
        if (iconCode == 0) return
        if (initialTotalDistanceMeters == 0 && (guidance.totalRemainingDistanceMeters ?: 0) > 0) {
            initialTotalDistanceMeters = guidance.totalRemainingDistanceMeters!!
        }

        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            if (factoryHudBridge) putExtra("XB_CARLINK", 1)
            putExtra("KEY_TYPE", KEY_TYPE_GUIDANCE)
            putExtra("EXTRA_STATE", if (guidance.isRerouting) STATE_REROUTING else STATE_NAVIGATING)

            // Turn Icon
            putExtra("ICON", iconCode)
            putExtra("NEW_ICON", iconCode)
            putExtra("ROUNG_ABOUT_NUM", guidance.currentManeuver?.exitNumber?.toIntOrNull() ?: 0)

            // Segment distance (distance to next turn)
            val segDis = guidance.distanceToNextManeuverMeters ?: 0
            putExtra("SEG_REMAIN_DIS", segDis)
            putExtra("SEG_REMAIN_DIS_AUTO", formatDistanceText(segDis))

            // Road names
            putExtra("NEXT_ROAD_NAME", guidance.nextRoadName ?: guidance.currentManeuver?.roadName ?: "")
            putExtra("CUR_ROAD_NAME", guidance.currentRoadName ?: "")

            // Total remaining route distance & time
            val totalDis = guidance.totalRemainingDistanceMeters ?: -1
            val totalTime = guidance.totalRemainingTimeSeconds ?: -1
            putExtra("ROUTE_REMAIN_DIS", totalDis)
            putExtra("ROUTE_REMAIN_DIS_AUTO", if (totalDis >= 0) formatDistanceText(totalDis) else "")
            putExtra("ROUTE_REMAIN_TIME", totalTime)
            putExtra("ROUTE_REMAIN_TIME_AUTO", if (totalTime >= 0) formatTimeText(totalTime) else "")
            putExtra("ROUTE_ALL_DIS", if (initialTotalDistanceMeters > 0) initialTotalDistanceMeters else totalDis)

            // ETA
            if (totalTime > 0) {
                putExtra("ETA_TEXT", calculateEtaText(totalTime))
            }

            // Destination
            guidance.destinationName?.let { putExtra("endPOIName", it) }
        }

        send(intent)
        navigationSent = true

        // Lane Guidance (KEY_TYPE = 13012)
        guidance.laneGuidance?.let { emitLaneGuidance(it) }
    }

    /**
     * Emits lane line recommendations (KEY_TYPE = 13012).
     */
    fun emitLaneGuidance(laneInfo: LaneGuidanceInfo) {
        if (!navigationSent || !laneInfo.verifiedFromIphone || laneInfo.lanes.isEmpty()) return

        val laneJson = buildLaneJson(laneInfo)
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            if (factoryHudBridge) putExtra("XB_CARLINK", 1)
            putExtra("KEY_TYPE", KEY_TYPE_LANE_LINE)
            putExtra("EXTRA_DRIVE_WAY", laneJson)
        }
        send(intent)
    }

    /**
     * Emits traffic light countdown and status (KEY_TYPE = 60073).
     */
    fun emitTrafficLight(info: TrafficLightInfo) {
        if (!navigationSent) return
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_TRAFFIC_LIGHT)
            putExtra("trafficLightStatus", info.status) // 1: Red, 2: Green, 3: Yellow
            putExtra("dir", info.direction)             // 1: Left, 2: Straight, 3: Right, 4: U-turn
            putExtra("redLightCountDownSeconds", info.countdownSeconds)
        }
        send(intent)
    }

    /**
     * Emits section speed camera tracking (KEY_TYPE = 12110).
     */
    fun emitSectionSpeed(info: SectionSpeedInfo) {
        if (!navigationSent) return
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SECTION_SPEED)
            putExtra("AVERAGE_SPEED", info.averageSpeedKmh)
            putExtra("AVG_SPEED", info.averageSpeedKmh)
            putExtra("LIMITED_SPEED", info.limitedSpeedKmh)
            val distText = info.remainingDistanceText ?: formatDistanceText(info.remainingDistanceMeters ?: 0)
            putExtra("END_DISTANCE_TEXT", distText)
        }
        send(intent)
    }

    /**
     * Emits day/night mode switch (KEY_TYPE = 10019).
     */
    fun emitDayNightMode(isNight: Boolean) {
        if (!navigationSent) return
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SYSTEM_STATE)
            putExtra("EXTRA_STATE", if (isNight) STATE_NIGHT_MODE else STATE_DAY_MODE)
        }
        send(intent)
    }

    /**
     * Emits navigation end / clear signal to reset HUD (KEY_TYPE = 10019, EXTRA_STATE = 9).
     */
    @Synchronized
    fun emitNavigationEnded() {
        initialTotalDistanceMeters = 0
        if (!navigationSent) return
        navigationSent = false
        val intent = Intent(broadcastAction).apply {
            targetPackage?.let { setPackage(it) }
            putExtra("KEY_TYPE", KEY_TYPE_SYSTEM_STATE)
            if (factoryHudBridge) putExtra("XB_CARLINK", 1)
            putExtra("EXTRA_STATE", STATE_NAV_ENDED)
        }
        send(intent)
    }

    private fun send(intent: Intent) {
        intent.putExtra("ORIGIN_SOURCE", ORIGIN_SOURCE)
        context.sendBroadcast(intent)
        Log.i(TAG, "[CarPlay-HUD-TX] ${intent.getIntExtra("KEY_TYPE", -1)} sent: " +
            "source=$ORIGIN_SOURCE icon=${intent.getIntExtra("NEW_ICON", -1)} " +
            "remainDist=${intent.getIntExtra("ROUTE_REMAIN_DIS", -1)} " +
            "state=${intent.getIntExtra("EXTRA_STATE", -1)}")
    }

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
        val lanes = laneInfo.lanes.mapIndexed { index, lane ->
            """{"drive_way_number":${index},"drive_way_lane_Back_icon":${lane.directionCode},"trafficLaneAdvised":${lane.isPreferred},"is_recommended":${lane.isPreferred}}"""
        }.joinToString(",")
        return """{"drive_way_enabled":true,"drive_way_size":${laneInfo.lanes.size},"drive_way_info":[$lanes]}"""
    }
}

internal fun mapManeuverToAmapIcon(maneuver: CarPlayManeuver?): Int {
        if (maneuver == null) return 0
        if (maneuver.type in setOf(CarPlayManeuverType.UNKNOWN, CarPlayManeuverType.ON_RAMP,
                CarPlayManeuverType.OFF_RAMP, CarPlayManeuverType.MERGE_LEFT,
                CarPlayManeuverType.MERGE_RIGHT)) return 0
        maneuver.amapIcon?.let {
            return if (it == 11) 12 else if (it in setOf(2, 3, 4, 5, 6, 7, 8, 9, 12, 15, 17, 18, 19)) it else 0
        }
        return when (maneuver.type) {
            CarPlayManeuverType.CONTINUE, CarPlayManeuverType.STRAIGHT -> 9
            CarPlayManeuverType.TURN_LEFT -> 2
            CarPlayManeuverType.TURN_RIGHT -> 3
            CarPlayManeuverType.TURN_SLIGHT_LEFT -> 4
            CarPlayManeuverType.TURN_SLIGHT_RIGHT -> 5
            CarPlayManeuverType.TURN_SHARP_LEFT -> 6
            CarPlayManeuverType.TURN_SHARP_RIGHT -> 7
            CarPlayManeuverType.U_TURN_LEFT -> 8
            CarPlayManeuverType.U_TURN_RIGHT -> 19
            CarPlayManeuverType.KEEP_LEFT, CarPlayManeuverType.FORK_LEFT -> 17
            CarPlayManeuverType.KEEP_RIGHT, CarPlayManeuverType.FORK_RIGHT -> 18
            CarPlayManeuverType.ROUNDABOUT_ENTER -> 12
            CarPlayManeuverType.ROUNDABOUT_EXIT -> 12
            CarPlayManeuverType.ARRIVED_AT_DESTINATION -> 15
            else -> 0 // No invented side for an ambiguous ramp/merge.
        }
    }

