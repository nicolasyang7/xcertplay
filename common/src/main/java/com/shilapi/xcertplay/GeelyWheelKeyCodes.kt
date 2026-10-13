package com.shilapi.xcertplay

/**
 * Key codes dispatched by the Geely / Lynk & Co head unit's input service
 * (`com.geely.inputservice.InputService`) for the steering-wheel controls.
 *
 * Confirmed on the car:
 * - 200085: play/pause key
 * - 200087: next track key
 * - 200088: previous track key
 * - 200231: voice assist (microphone) key; short press is the car's native assistant, long press
 *   fires onLongPressTriggered (which we map to Siri once the car's HiCar app is disabled).
 */
object GeelyWheelKeyCodes {
    const val KEY_PLAY_PAUSE = 200085
    const val KEY_NEXT = 200087
    const val KEY_PREVIOUS = 200088
    const val KEY_VOICE_ASSIST = 200231

    val ALL = intArrayOf(KEY_PLAY_PAUSE, KEY_NEXT, KEY_PREVIOUS, KEY_VOICE_ASSIST)
    val SIRI_ONLY = intArrayOf(KEY_VOICE_ASSIST)

    /**
     * The key codes to subscribe to on the head unit's input service.
     *
     * With CarPlay audio on the car's Bluetooth, the head unit's own AVRCP already drives the
     * iPhone's music, so registering the media keys here too would double-fire play/pause and skip.
     * Only the microphone key is registered then (long press -> Siri); otherwise all of them are.
     */
    fun registeredCodes(bluetoothAudio: Boolean): IntArray =
        if (bluetoothAudio) SIRI_ONLY else ALL
}
