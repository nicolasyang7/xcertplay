package com.shilapi.xcertplay

import android.os.Build
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** User-selectable starting settings for the previously surveyed Lynk & Co 09 EX11 head unit. */
internal object LynkCo09Profile {
    const val MANUFACTURER = "Lynk & Co"
    const val MODEL = "09 EX11"
    const val OEM_LABEL = "Lynk & Co"

    const val CH341_VENDOR_ID = 0x1A86
    const val CH341_PRODUCT_ID = 0x5512

    val mfiTarget: MfiTarget = MfiTarget.USB_CH341

    fun hotspotModeFor(apiLevel: Int): WirelessHotspotMode =
        if (apiLevel < Build.VERSION_CODES.Q) {
            WirelessHotspotMode.LOCAL_ONLY_HOTSPOT
        } else {
            WirelessHotspotMode.WIFI_P2P
        }
}
