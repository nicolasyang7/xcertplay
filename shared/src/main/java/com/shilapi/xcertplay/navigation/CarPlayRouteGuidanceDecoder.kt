package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** DiPlay route-guidance profile; lane extensions await verified real samples. */
class CarPlayRouteGuidanceDecoder(
    private val nanoTime: () -> Long = System::nanoTime,
    private val diagnostic: (String) -> Unit = { android.util.Log.i("CarPlay-HUD", "[CarPlay-HUD] $it") },
    private val onNavigationUpdate: (CarPlayNavigationUpdate) -> Unit = {},
) {
    private val route = CarPlayRouteState(nanoTime, diagnostic)
    private var unverifiedLaneLogged = false
    private var emittedActive = false
    private var lastAction: Triple<Int, Int, Int>? = null
    private var lastUnavailable: String? = null

    @Synchronized
    fun onIncomingFrame(frame: Iap2Frame): Boolean {
        if (frame.messageId == 0x5204) {
            if (!unverifiedLaneLogged) {
                diagnostic("Lane frame received bytes=${frame.payload.size}; ignored until verified")
                unverifiedLaneLogged = true
            }
            return true
        }
        if (frame.messageId !in setOf(0x5201, 0x5202)) return false
        val change = route.accept(frame.messageId, frame.payload)
        if (change == CarPlayRouteChange.CLEAR) resetNavigation()
        else if (change == CarPlayRouteChange.GUIDANCE) publishCurrent()
        return true
    }

    /** Called periodically to repeat real guidance and clear expired/empty routes. */
    @Synchronized
    fun tick() = publishCurrent()

    @Synchronized
    fun resetNavigation() {
        diagnostic("Navigation reset; previouslyEmitted=$emittedActive")
        lastAction = null
        lastUnavailable = null
        unverifiedLaneLogged = false
        route.clear()
        if (emittedActive) {
            emittedActive = false
            onNavigationUpdate(CarPlayNavigationUpdate(isActive = false))
        }
    }

    private fun publishCurrent() {
        val apple = route.currentApple()
        if (apple == null) {
            val reason = route.unavailableReason()
            if (reason != lastUnavailable) diagnostic("Navigation unavailable: $reason")
            lastUnavailable = reason
            lastAction = null
            if (emittedActive) {
                emittedActive = false
                onNavigationUpdate(CarPlayNavigationUpdate(isActive = false))
            }
            return
        }
        val frame = NaviToolGuidanceFrame.from(apple)
        // NaviTool treats icon 0 as cruise. Never send an unknown turn as cruise/straight.
        // Skip this update without ending the route or inventing a maneuver.
        // Genuine route end, disconnect and expiry still clear the output.
        lastUnavailable = null
        val action = Triple(route.currentIndex(), apple.type, frame.icon)
        if (action != lastAction) {
            diagnostic("Maneuver advanced: index=${action.first}, rawType=${apple.type} -> " +
                "amapIcon=${frame.icon}, road=${apple.road}, dist=${apple.distanceMeters}m" +
                if (frame.icon == 0) "; unknown action suppressed" else "")
            lastAction = action
        }
        if (frame.icon == 0) return
        emittedActive = true
        val maneuver = CarPlayManeuver(
            index = route.currentIndex(),
            type = when (frame.icon) {
                2 -> CarPlayManeuverType.TURN_LEFT
                3 -> CarPlayManeuverType.TURN_RIGHT
                4 -> CarPlayManeuverType.TURN_SLIGHT_LEFT
                5 -> CarPlayManeuverType.TURN_SLIGHT_RIGHT
                6 -> CarPlayManeuverType.TURN_SHARP_LEFT
                7 -> CarPlayManeuverType.TURN_SHARP_RIGHT
                8 -> CarPlayManeuverType.U_TURN_LEFT
                9 -> CarPlayManeuverType.STRAIGHT
                11 -> CarPlayManeuverType.ROUNDABOUT_ENTER
                12 -> CarPlayManeuverType.ROUNDABOUT_EXIT
                17 -> CarPlayManeuverType.KEEP_LEFT
                18 -> CarPlayManeuverType.KEEP_RIGHT
                15 -> CarPlayManeuverType.ARRIVED_AT_DESTINATION
                19 -> CarPlayManeuverType.U_TURN_RIGHT
                else -> CarPlayManeuverType.UNKNOWN
            },
            rawType = apple.type,
            roadName = apple.road,
            distanceMeters = apple.distanceMeters,
            amapIcon = frame.icon,
            exitNumber = frame.roundaboutExit.takeIf { it > 0 }?.toString(),
        )
        onNavigationUpdate(CarPlayNavigationUpdate(
            isActive = true,
            currentManeuver = maneuver,
            upcomingManeuvers = listOf(maneuver),
            distanceToNextManeuverMeters = frame.distanceMeters,
            totalRemainingDistanceMeters = frame.routeRemainingMeters.takeIf { it >= 0 },
            totalRemainingTimeSeconds = frame.routeRemainingSeconds.takeIf { it >= 0 },
            currentRoadName = route.currentRoadName(),
            nextRoadName = frame.road,
        ))
    }
}
