package com.shilapi.xcertplay.navigation

import android.graphics.Bitmap
import android.graphics.Color
import com.shilapi.xcertplay.navigation.vision.TrafficLightVisionExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TrafficLightVisionExtractorTest {

    @Test
    fun returnsNullOnBlankFrame() {
        val extractor = TrafficLightVisionExtractor()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val result = extractor.extractTrafficLight(bitmap)
        assertNull(result)
    }

    @Test
    fun detectsRedTrafficLightWithCountdown() {
        val extractor = TrafficLightVisionExtractor()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)

        // Draw synthetic red traffic light circle around (250, 70)
        for (y in 55..85) {
            for (x in 235..265) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }

        // Draw white countdown digits around (280, 70)
        for (y in 60..80) {
            for (x in 275..290) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        val result = extractor.extractTrafficLight(bitmap)
        assertNotNull(result)
        assertEquals(1, result!!.status) // 1: Red
        assertEquals(2, result.direction)
    }

    @Test
    fun detectsGreenTrafficLight() {
        val extractor = TrafficLightVisionExtractor()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)

        // Draw synthetic green traffic light circle around (250, 70)
        for (y in 55..85) {
            for (x in 235..265) {
                bitmap.setPixel(x, y, Color.GREEN)
            }
        }

        // Draw white countdown digits around (280, 70)
        for (y in 60..80) {
            for (x in 275..290) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        val result = extractor.extractTrafficLight(bitmap)
        assertNotNull(result)
        assertEquals(2, result!!.status) // 2: Green
    }
}
