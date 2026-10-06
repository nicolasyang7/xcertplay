package com.shilapi.xcertplay.navigation

import org.junit.Test
import org.junit.Assert.assertEquals

class AmapIconMappingTest {
    @Test
    fun mapsToAutoNaviStandardIcons() {
        assertEquals(0, AmapBroadcastEmitter.amapIconFor(null))
        assertEquals(2, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.TURN_LEFT))
        assertEquals(3, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.TURN_RIGHT))
        assertEquals(8, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.U_TURN_RIGHT))
        assertEquals(9, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.STRAIGHT))
        assertEquals(11, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.ROUNDABOUT_ENTER))
        assertEquals(15, AmapBroadcastEmitter.amapIconFor(CarPlayManeuverType.ARRIVED_AT_DESTINATION))
    }
}
