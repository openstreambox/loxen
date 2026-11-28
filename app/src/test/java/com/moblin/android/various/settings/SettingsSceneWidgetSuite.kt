package com.moblin.android.various.settings

import com.moblin.android.platform.codable.codableJson
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSceneWidgetSuite {
    private fun decode(json: String): SettingsSceneWidget =
        codableJson.decodeFromString(SettingsSceneWidget.serializer(), json)

    @Test
    fun oldCenteredWidgetGetsZeroCenterOffset() {
        val sceneWidget = decode("""{"x": 10, "y": 20, "alignment": "Center"}""")
        assertEquals(0.0, sceneWidget.layout.x)
        assertEquals(0.0, sceneWidget.layout.y)
    }

    @Test
    fun oldHorizontallyCenteredWidgetKeepsVerticalOffset() {
        val sceneWidget = decode("""{"x": 10, "y": 20, "alignment": "TopCenter"}""")
        assertEquals(0.0, sceneWidget.layout.x)
        assertEquals(20.0, sceneWidget.layout.y)
    }

    @Test
    fun oldCornerWidgetKeepsOffsets() {
        val sceneWidget = decode("""{"x": 10, "y": 20, "alignment": "BottomRight"}""")
        assertEquals(10.0, sceneWidget.layout.x)
        assertEquals(20.0, sceneWidget.layout.y)
    }

    @Test
    fun centerOffsetSurvivesRoundTrip() {
        val sceneWidget = SettingsSceneWidget(widgetId = UUID.randomUUID())
        sceneWidget.layout = sceneWidget.layout.copy(x = 10.0, y = 20.0, alignment = SettingsAlignment.center)
        val encoded = codableJson.encodeToString(SettingsSceneWidget.serializer(), sceneWidget)
        val decoded = codableJson.decodeFromString(SettingsSceneWidget.serializer(), encoded)
        assertEquals(10.0, decoded.layout.x)
        assertEquals(20.0, decoded.layout.y)
    }

    @Test
    fun oldCenteredReplayGetsZeroCenterOffset() {
        val json = """{"x": 10, "y": 20, "alignment": "Center"}"""
        val replay = codableJson.decodeFromString(SettingsStreamReplay.serializer(), json)
        assertEquals(0.0, replay.layout.x)
        assertEquals(0.0, replay.layout.y)
    }
}
