package com.shilapi.xcertplay.iap2

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.catalog.Iap2Endpoints
import com.shilapi.xcertplay.iap2.message.Iap2ControlMessages
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.iap2.message.Iap2NavigationAccumulator
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2NavigationTest {
    private fun routeUpdate(block: com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder.() -> Unit): Iap2Frame =
        Iap2Messages.buildRaw(Iap2NavigationAccumulator.ROUTE_GUIDANCE_UPDATE, block)

    private fun maneuverUpdate(block: com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder.() -> Unit): Iap2Frame =
        Iap2Messages.buildRaw(Iap2NavigationAccumulator.ROUTE_GUIDANCE_MANEUVER_UPDATE, block)

    @Test
    fun subscriptionNamesTheDeclaredComponent() {
        val start = Iap2ControlMessages.subscriptions()
            .single { it.messageId == Iap2Endpoints.START_ROUTE_GUIDANCE_UPDATES.id }
        val body = Iap2BodyReader.of(start)

        assertEquals(Iap2ControlMessages.ROUTE_GUIDANCE_COMPONENT_ID, body.u16(0))
        assertTrue(body.has(1))
        assertTrue(body.has(2))
    }

    @Test
    fun identificationDeclaresRouteGuidanceComponentWithRoadNameLimits() {
        val config = Iap2IdentificationConfig(
            name = "wired",
            modelIdentifier = "wired",
            manufacturer = "test",
            serialNumber = "1",
            firmwareVersion = "1.0",
            hardwareVersion = "1.0",
            carPlayUsbInterfaceNumber = 4,
        )
        val group = Iap2BodyReader.of(Iap2IdentificationClient.identificationInformation(config)).group(30)

        assertEquals(Iap2ControlMessages.ROUTE_GUIDANCE_COMPONENT_ID, group.u16(0))
        assertEquals("RouteGuidance", group.string(1))
        assertTrue(group.u16(2) > 0)
        assertTrue(group.u16(4) > 0)
    }

    @Test
    fun maneuverDetailsArriveSeparatelyAndJoinTheActiveManeuver() {
        val accumulator = Iap2NavigationAccumulator { 10L }
        accumulator.update(
            routeUpdate {
                u8(1, 1)
                string(3, "Main St")
                u32(7, 12_000)
                u32(10, 300)
                u16List(13, listOf(4))
            },
        )
        assertNull(accumulator.current().nextManeuver)

        val state = accumulator.update(
            maneuverUpdate {
                u16(1, 4)
                u8(3, 6)
                string(4, "Oak Ave")
                u8(8, 1)
            },
        )!!

        assertTrue(state.routeActive)
        assertEquals("Main St", state.currentRoadName)
        assertEquals(12_000L, state.distanceRemainingMeters)
        assertEquals(300L, state.distanceToNextManeuverMeters)
        assertEquals(6, state.nextManeuver?.type)
        assertEquals("Oak Ave", state.nextManeuver?.afterRoadName)
        assertEquals(1, state.nextManeuver?.drivingSide)
    }

    @Test
    fun unreportedFieldsStayNullInsteadOfZero() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        val state = accumulator.update(routeUpdate { u8(1, 1) })!!

        assertNull(state.distanceRemainingMeters)
        assertNull(state.distanceToNextManeuverMeters)
        assertNull(state.destinationName)
        assertNull(state.arrivalEpochSeconds)
    }

    @Test
    fun incrementalUpdatesKeepPreviouslyReportedFields() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        accumulator.update(routeUpdate { u8(1, 1); string(3, "Main St"); u32(7, 5_000) })
        val state = accumulator.update(routeUpdate { u32(10, 120) })!!

        assertEquals("Main St", state.currentRoadName)
        assertEquals(5_000L, state.distanceRemainingMeters)
        assertEquals(120L, state.distanceToNextManeuverMeters)
    }

    @Test
    fun emptyManeuverListKeepsTheLastManeuver() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        accumulator.update(routeUpdate { u8(1, 1); u16List(13, listOf(2)) })
        accumulator.update(maneuverUpdate { u16(1, 2); u8(3, 3) })
        val state = accumulator.update(routeUpdate { u16List(13, emptyList()) })!!

        assertTrue(state.routeActive)
        assertEquals(3, state.nextManeuver?.type)
    }

    @Test
    fun noRouteSetAndArrivedEndTheRoute() {
        for (ending in listOf(0, 2)) {
            val accumulator = Iap2NavigationAccumulator { 0L }
            accumulator.update(routeUpdate { u8(1, 1); u32(7, 900); u16List(13, listOf(1)) })
            accumulator.update(maneuverUpdate { u16(1, 1); u8(3, 4) })
            val state = accumulator.update(routeUpdate { u8(1, ending) })!!

            assertFalse(state.routeActive)
            assertEquals(ending, state.guidanceState)
            assertNull(state.distanceRemainingMeters)
            assertNull(state.nextManeuver)
        }
    }

    @Test
    fun resetStartsANewSessionAndDropsCachedManeuvers() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        accumulator.update(routeUpdate { u8(1, 1); u16List(13, listOf(1)) })
        accumulator.update(maneuverUpdate { u16(1, 1); u8(3, 4) })
        val firstSession = accumulator.current().sessionId

        val fresh = accumulator.reset()
        accumulator.update(routeUpdate { u8(1, 1); u16List(13, listOf(1)) })

        assertTrue(fresh.sessionId > firstSession)
        assertFalse(fresh.routeActive)
        assertNull(accumulator.current().nextManeuver)
        assertEquals(fresh.sessionId, accumulator.current().sessionId)
    }

    /** The two frames of an Amap CarPlay navigation trace, with the parameters that were logged. */
    @Test
    fun decodesTheAmapCarPlayTrace() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        accumulator.update(
            maneuverUpdate {
                u16(0, 42)
                u16(1, 0)
                string(2, "进入 无名道路")
                u8(3, 2)
                string(4, "进入 无名道路")
                u32(5, 1500)
                string(6, "1.5")
            },
        )
        val state = accumulator.update(
            routeUpdate {
                u16(0, 42)
                u8(1, 1)
                string(4, "未知位置")
                u64(5, 1_791_011_797L)
                u64(6, 5_520L)
                u32(7, 126_900)
                string(8, "127")
                u32(10, 1_500)
                string(11, "1.5")
                u16(13, 0)
                u16(14, 1)
                string(19, "Amap")
                u8(20, 1)
            },
        )!!

        assertTrue(state.routeActive)
        assertEquals("Amap", state.sourceName)
        assertEquals("未知位置", state.destinationName)
        assertEquals(1_791_011_797L, state.arrivalEpochSeconds)
        assertEquals(5_520L, state.timeRemainingSeconds)
        assertEquals(126_900L, state.distanceRemainingMeters)
        assertEquals(1_500L, state.distanceToNextManeuverMeters)
        assertEquals(1, state.maneuverCount)
        assertEquals(2, state.nextManeuver?.type)
        assertEquals("进入 无名道路", state.nextManeuver?.afterRoadName)
        assertNull(state.currentRoadName)
    }

    /** The burst the phone sent when the route was set, in the order it arrived. */
    @Test
    fun theRealBurstBecomesGuidanceOnlyOnceTheRouteIsComputed() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        val started = accumulator.update(
            routeUpdate { u16(0, 42); u8(1, 3); string(4, "未知位置"); u16(14, 0); u8(15, 1); string(19, "Amap"); u8(20, 1) },
        )!!
        assertFalse(started.hasGuidance)

        // ETA is "now" and the remaining time is 0 until the route exists: not data.
        val placeholder = accumulator.update(
            routeUpdate { u16(0, 42); u8(1, 1); string(4, "未知位置"); u64(5, 1_791_006_277L); u64(6, 0L) },
        )!!
        assertNull(placeholder.arrivalEpochSeconds)
        assertNull(placeholder.timeRemainingSeconds)

        val timed = accumulator.update(
            routeUpdate { u64(5, 1_791_011_797L); u64(6, 5_520L); u32(7, 126_900); string(8, "127") },
        )!!
        assertEquals(5_520L, timed.timeRemainingSeconds)
        assertFalse(timed.hasGuidance)

        // The maneuver details arrive before the update that names the current maneuver; the
        // unit and driving side parameters are a single zero byte.
        accumulator.update(
            maneuverUpdate {
                u16(0, 42); u16(1, 0); string(2, "进入 无名道路"); u8(3, 2); string(4, "进入 无名道路")
                u32(5, 1500); string(6, "1.5"); u8(7, 0); u8(8, 0); u8(9, 0)
            },
        )
        accumulator.update(routeUpdate { u16(14, 1) })
        accumulator.update(routeUpdate { u16(13, 0) })
        val full = accumulator.update(routeUpdate { u32(10, 1_500); string(11, "1.5"); u8(12, 0) })!!

        assertTrue(full.hasGuidance)
        assertEquals(0, full.nextManeuver?.drivingSide)
        assertEquals(2, full.nextManeuver?.type)
    }

    @Test
    fun otherMessagesAreIgnored() {
        val accumulator = Iap2NavigationAccumulator { 0L }
        assertNull(accumulator.update(Iap2Frame(0x5001, byteArrayOf())))
    }
}
