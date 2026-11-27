package com.moblin.android.various.settings

import com.moblin.android.platform.codable.codableJson
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsGameControllerSuite {
    @Test
    fun playSoundButtonSurvivesEncodeAndDecode() {
        val soundId = UUID.randomUUID()
        val button = SettingsGameControllerButton()
        button.setFunction(SettingsControllerFunction.PLAY_SOUND)
        button.setFunctionData(button.functionData.value.copy(soundId = soundId))
        val decoded = codableJson.decodeFromString(
            SettingsGameControllerButton.serializer(),
            codableJson.encodeToString(SettingsGameControllerButton.serializer(), button),
        )
        assertEquals(SettingsControllerFunction.PLAY_SOUND, decoded.function.value)
        assertEquals(soundId, decoded.functionData.value.soundId)
    }

    @Test
    fun playSoundButtonDefaultsWhenMissingFromSettings() {
        val button = codableJson.decodeFromString(
            SettingsGameControllerButton.serializer(),
            "{}",
        )
        assertNull(button.functionData.value.soundId)
    }

    @Test
    fun playSoundGimbalButtonsSurviveEncodeAndDecode() {
        val shutterSoundId = UUID.randomUUID()
        val flipSoundId = UUID.randomUUID()
        val gimbal = SettingsGimbal()
        gimbal.functionShutter = SettingsControllerFunction.PLAY_SOUND
        gimbal.functionDataShutter = gimbal.functionDataShutter.copy(soundId = shutterSoundId)
        gimbal.functionFlip = SettingsControllerFunction.PLAY_SOUND
        gimbal.functionDataFlip = gimbal.functionDataFlip.copy(soundId = flipSoundId)
        val decoded = codableJson.decodeFromString(
            SettingsGimbal.serializer(),
            codableJson.encodeToString(SettingsGimbal.serializer(), gimbal),
        )
        assertEquals(shutterSoundId, decoded.functionDataShutter.soundId)
        assertEquals(flipSoundId, decoded.functionDataFlip.soundId)
    }
}
