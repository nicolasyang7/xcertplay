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
        // 1 state, 3 currentRoadName, 4 destinationName, 6 timeRemaining (ms),
        // 7 distanceRemaining (m), 10 distanceRemainingToNextManeuver (m)
        val frame = Iap2Messages.buildRaw(0x5201) {
            u8(1, 1)
            string(3, "Tianfu Ave")
            string(4, "Chengdu South Railway Station")
            u64(6, 1_500_000L)
            u32(7, 15400L)
            u32(10, 350L)
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
        // 1 index, 2 description, 3 maneuverType (0 = left turn), 4 afterManeuverRoadName,
        // 5 distanceBetweenManeuver (m)
        val frame = Iap2Messages.buildRaw(0x5202) {
            u16(1, 0)
            string(2, "Turn left onto Yizhou Ave")
            u8(3, 0)
            string(4, "Yizhou Ave")
            u16(5, 200)
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
        assertEquals(200, maneuver!!.distanceMeters)
    }

    @Test
    fun decoderHandlesNavigationEnd() {
        var lastUpdate: CarPlayNavigationUpdate? = null
        val decoder = CarPlayRouteGuidanceDecoder { update ->
            lastUpdate = update
        }

        // Send active guidance first
        val activeFrame = Iap2Messages.buildRaw(0x5201) {
            u8(1, 1) // active
        }
        decoder.onIncomingFrame(activeFrame)
        assertTrue(lastUpdate?.isActive == true)

        // Send 0x5203 StopRouteGuidanceUpdates
        val stopFrame = Iap2Messages.buildRaw(0x5203) {}
        decoder.onIncomingFrame(stopFrame)
        assertFalse(lastUpdate?.isActive == true)
    }
}
