package com.shilapi.xcertplay.iap2

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.catalog.Iap2Endpoints
import com.shilapi.xcertplay.iap2.message.Iap2ControlMessages
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2RouteGuidanceDeclarationTest {
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
}
