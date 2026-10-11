package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@org.robolectric.annotation.Config(sdk = [28])
@RunWith(RobolectricTestRunner::class)
class CarPlayNavigationTest {
    @Test fun realProfileReachesNaviToolAndAdvancesByRouteIndex() {
        val context = RuntimeEnvironment.getApplication()
        val emitter = AmapBroadcastEmitter(context)
        val decoder = CarPlayRouteGuidanceDecoder { emitter.emitNavigationUpdate(it) }
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) {
            u16(1, 7); u8(3, 1); string(4, "下一道路"); u8(8, 0)
        })
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 8); u8(3, 3) })
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) {
            u8(1, 1); string(3, "当前道路"); u64(6, 1500); u32(7, 15400)
            u32(10, 350); u16List(13, listOf(7))
        })
        val intent = shadowOf(context).broadcastIntents.last()
        assertNull(intent.`package`)
        assertNull(intent.component)
        assertEquals("AUTONAVI_STANDARD_BROADCAST_SEND", intent.action)
        assertEquals(1, intent.getIntExtra("XB_CARLINK", 0))
        assertEquals(10001, intent.getIntExtra("KEY_TYPE", -1))
        assertEquals(2, intent.getIntExtra("NEW_ICON", -1))
        assertEquals("350米", intent.getStringExtra("SEG_REMAIN_DIS_AUTO"))
        assertEquals("下一道路", intent.getStringExtra("NEXT_ROAD_NAME"))
        assertEquals(15400, intent.getIntExtra("ROUTE_REMAIN_DIS", -1))
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u16List(13, listOf(8)); u32(10, 50) })
        assertEquals(9, shadowOf(context).broadcastIntents.last().getIntExtra("NEW_ICON", -1))
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 0) })
        val ended = shadowOf(context).broadcastIntents.last()
        assertEquals(1, ended.getIntExtra("XB_CARLINK", 0))
        assertEquals(10019, ended.getIntExtra("KEY_TYPE", -1))
        assertEquals(9, ended.getIntExtra("EXTRA_STATE", -1))
        val count = shadowOf(context).broadcastIntents.size
        decoder.tick(); decoder.resetNavigation()
        assertEquals(count, shadowOf(context).broadcastIntents.size)
    }

    @Test fun unknownActionsAndUnverifiedStateDoNotEndAnActiveHud() {
        val context = RuntimeEnvironment.getApplication()
        val emitter = AmapBroadcastEmitter(context)
        val decoder = CarPlayRouteGuidanceDecoder { emitter.emitNavigationUpdate(it) }
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 1); u8(3, 2) })
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 1); u16List(13, listOf(1)) })
        for (type in listOf(0, 8, 9, 15, 16, 17, 255)) {
            val count = shadowOf(context).broadcastIntents.size
            decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 1); u8(3, type) })
            decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 2) })
            decoder.tick()
            assertEquals(count, shadowOf(context).broadcastIntents.size)
        }
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 1); u8(3, 3) })
        assertEquals(9, shadowOf(context).broadcastIntents.last().getIntExtra("NEW_ICON", -1))
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 0) })
        assertEquals(10019, shadowOf(context).broadcastIntents.last().getIntExtra("KEY_TYPE", -1))
    }

    @Test fun laneBroadcastUsesReceiverObjectSchemaAndUnknownTotalsStayBlank() {
        val context = RuntimeEnvironment.getApplication()
        val emitter = AmapBroadcastEmitter(context)
        emitter.emitNavigationUpdate(CarPlayNavigationUpdate(
            currentManeuver = CarPlayManeuver(type = CarPlayManeuverType.STRAIGHT),
            distanceToNextManeuverMeters = 120,
        ))
        val guidance = shadowOf(context).broadcastIntents.last()
        assertEquals("", guidance.getStringExtra("ROUTE_REMAIN_DIS_AUTO"))
        assertEquals("", guidance.getStringExtra("ROUTE_REMAIN_TIME_AUTO"))
        emitter.emitLaneGuidance(LaneGuidanceInfo(listOf(LaneDirection(2, true))))
        val lane = shadowOf(context).broadcastIntents.last()
        assertNull(lane.`package`)
        assertNull(lane.component)
        assertEquals(13012, lane.getIntExtra("KEY_TYPE", -1))
        val json = org.json.JSONObject(lane.getStringExtra("EXTRA_DRIVE_WAY")!!)
        assertTrue(json.getBoolean("drive_way_enabled"))
        assertEquals(1, json.getInt("drive_way_size"))
        val first = json.getJSONArray("drive_way_info").getJSONObject(0)
        assertEquals(0, first.getInt("drive_way_number"))
        assertEquals(2, first.getInt("drive_way_lane_Back_icon"))
        assertTrue(first.getBoolean("trafficLaneAdvised"))
    }

    @Test fun allBroadcastKindsDefaultToGlobalDelivery() {
        val context = RuntimeEnvironment.getApplication()
        val received = mutableListOf<android.content.Intent>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                received.add(intent)
            }
        }
        context.registerReceiver(receiver, android.content.IntentFilter(AmapBroadcastEmitter.ACTION_AUTONAVI_SEND))
        try {
            val emitter = AmapBroadcastEmitter(context)
            emitter.emitNavigationUpdate(CarPlayNavigationUpdate(
                currentManeuver = CarPlayManeuver(type = CarPlayManeuverType.TURN_LEFT),
                distanceToNextManeuverMeters = 100,
            ))
            emitter.emitLaneGuidance(LaneGuidanceInfo(listOf(LaneDirection(2, true))))
            emitter.emitTrafficLight(TrafficLightInfo(1, 2, 10))
            emitter.emitSectionSpeed(SectionSpeedInfo(60, 80, 500))
            emitter.emitDayNightMode(true)
            emitter.emitNavigationEnded()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(10001, 13012, 60073, 12110, 10019, 10019),
                received.map { it.getIntExtra("KEY_TYPE", -1) })
            for (intent in received) {
                assertEquals(AmapBroadcastEmitter.ACTION_AUTONAVI_SEND, intent.action)
                assertNull(intent.`package`)
                assertNull(intent.component)
            }
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test fun unknownManeuverDoesNotEmitCruiseAndStaleGuidanceClears() {
        var now = 0L
        val updates = mutableListOf<CarPlayNavigationUpdate>()
        val decoder = CarPlayRouteGuidanceDecoder({ now }) { updates.add(it) }
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 1); u8(3, 255) })
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5201) { u8(1, 1); u16List(13, listOf(1)) })
        assertTrue(updates.isEmpty())
        decoder.onIncomingFrame(Iap2Messages.buildRaw(0x5202) { u16(1, 1); u8(3, 2) })
        assertTrue(updates.last().isActive)
        now = 31_000_000_000
        decoder.tick()
        assertFalse(updates.last().isActive)
    }
}
