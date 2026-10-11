package com.shilapi.xcertplay.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NaviToolGuidanceFrameTest {
    @Test
    fun `maps basic turns to amap icons`() {
        assertEquals(NaviToolGuidanceFrame(2, 0, 53), frame(type = 1, distance = 53))
        assertEquals(NaviToolGuidanceFrame(3, 0, 53), frame(type = 2, distance = 53))
        assertEquals(NaviToolGuidanceFrame(9, 0, 53), frame(type = 3, distance = 53))
        assertEquals(NaviToolGuidanceFrame(4, 0, 53), frame(type = 49, distance = 53))
        assertEquals(NaviToolGuidanceFrame(5, 0, 53), frame(type = 14, distance = 53))
    }

    @Test
    fun `numbered roundabout exit carries exit number`() {
        for (side in 0..1) {
            for (type in 28..46) {
                assertEquals(NaviToolGuidanceFrame(12, type - 27, 120), frame(type, 120, side))
            }
            assertEquals(11, frame(type = 6, drivingSide = side).icon)
            assertEquals(12, frame(type = 7, drivingSide = side).icon)
        }
    }

    @Test
    fun `u-turn follows driving side and arrival is destination`() {
        assertEquals(8, frame(type = 4).icon)
        assertEquals(19, frame(type = 4, drivingSide = 1).icon)
        assertEquals(15, frame(type = 12).icon)
    }

    @Test
    fun `negative distance is clamped`() {
        assertEquals(0, frame(type = 1, distance = -5).distanceMeters)
    }

    @Test fun `unknown maneuver is not advertised as straight`() {
        for (type in listOf(0, 8, 9, 15, 16, 17, 54, 255)) assertEquals(0, frame(type).icon)
        for (type in listOf(3, 5, 11, 51)) assertEquals(9, frame(type).icon)
    }

    private fun frame(type: Int, distance: Int = 0, drivingSide: Int = 0) =
        NaviToolGuidanceFrame.from(CarPlayAppleManeuver(distance, type, drivingSide))
}
