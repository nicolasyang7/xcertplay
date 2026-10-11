package com.shilapi.xcertplay.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AmapIconMappingTest {
    @Test fun mapsKnownTurnsAndLeavesUnknownBlank() {
        val cases = mapOf(
            CarPlayManeuverType.TURN_LEFT to 2,
            CarPlayManeuverType.TURN_RIGHT to 3,
            CarPlayManeuverType.STRAIGHT to 9,
            CarPlayManeuverType.KEEP_LEFT to 17,
            CarPlayManeuverType.KEEP_RIGHT to 18,
            CarPlayManeuverType.ARRIVED_AT_DESTINATION to 15,
            CarPlayManeuverType.UNKNOWN to 0,
        )
        for ((type, expected) in cases) {
            assertEquals(expected, mapManeuverToAmapIcon(CarPlayManeuver(index = 0, type = type)))
        }
        assertEquals(0, mapManeuverToAmapIcon(null))
    }
}
