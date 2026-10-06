package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayNavigationTest {

    @Test
    fun decoderExtractsRouteGuidanceUpdateCorrectly() {
        var lastUpdate: CarPlayNavigationUpdate? = null
        val decoder = CarPlayRouteGuidanceDecoder { update ->
            lastUpdate = update
        }

        // Build 0x5201 RouteGuidanceUpdate frame:
        // Param 0: currentRoadName = "Tianfu Ave"
        // Param 1: destinationName = "Chengdu South Railway Station"
        // Param 2: totalDistanceRemaining = 15400 (meters)
        // Param 3: totalTimeRemaining = 1500 (seconds)
        // Param 4: distanceToNextManeuver = 350 (meters)
        // Param 5: state = 1 (active)
        val frame = Iap2Messages.buildRaw(0x5201) {
            string(0, "Tianfu Ave")
            string(1, "Chengdu South Railway Station")
            u32(2, 15400L)
            u32(3, 1500L)
            u32(4, 350L)
            u8(5, 1)
        }

        val handled = decoder.onIncomingFrame(frame)
        assertTrue(handled)
        assertNotNull(lastUpdate)

        val update = lastUpdate!!
        assertTrue(update.isActive)
        assertFalse(update.isRerouting)
        assertEquals("Tianfu Ave", update.currentRoadName)
        assertEquals("Chengdu South Railway Station", update.destinationName)
        assertEquals(15400, update.totalRemainingDistanceMeters)
        assertEquals(1500, update.totalRemainingTimeSeconds)
        assertEquals(350, update.distanceToNextManeuverMeters)
    }

    @Test
    fun decoderExtractsManeuverUpdateCorrectly() {
        var lastUpdate: CarPlayNavigationUpdate? = null
        val decoder = CarPlayRouteGuidanceDecoder { update ->
            lastUpdate = update
        }

        // Build 0x5202 RouteGuidanceManeuverUpdate frame:
        // Param 0: maneuverIndex = 0
        // Param 1: maneuverType = 3 (TURN_LEFT)
        // Param 2: turnAngle = -90
        // Param 3: instruction = "Turn left onto Yizhou Ave"
        // Param 4: roadName = "Yizhou Ave"
        // Param 5: distance = 200 (meters)
        val frame = Iap2Messages.buildRaw(0x5202) {
            u16(0, 0)
            u16(1, 3)
            i16(2, -90)
            string(3, "Turn left onto Yizhou Ave")
            string(4, "Yizhou Ave")
            u32(5, 200L)
        }

        val handled = decoder.onIncomingFrame(frame)
        assertTrue(handled)
        assertNotNull(lastUpdate)

        val update = lastUpdate!!
        assertTrue(update.isActive)
        val maneuver = update.currentManeuver
        assertNotNull(maneuver)
        assertEquals(CarPlayManeuverType.TURN_LEFT, maneuver!!.type)
        assertEquals("Yizhou Ave", update.nextRoadName)
        assertEquals(200, update.distanceToNextManeuverMeters)
    }

    @Test
    fun decoderHandlesNavigationEnd() {
        var lastUpdate: CarPlayNavigationUpdate? = null
        val decoder = CarPlayRouteGuidanceDecoder { update ->
            lastUpdate = update
        }

        // Send active guidance first
        val activeFrame = Iap2Messages.buildRaw(0x5201) {
            u8(5, 1) // active
        }
        decoder.onIncomingFrame(activeFrame)
        assertTrue(lastUpdate?.isActive == true)

        // Send 0x5203 StopRouteGuidanceUpdates
        val stopFrame = Iap2Messages.buildRaw(0x5203) {}
        decoder.onIncomingFrame(stopFrame)
        assertFalse(lastUpdate?.isActive == true)
    }
}
