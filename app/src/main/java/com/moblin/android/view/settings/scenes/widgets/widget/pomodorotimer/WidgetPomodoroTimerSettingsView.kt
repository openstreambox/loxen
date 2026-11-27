package com.moblin.android.view.settings.scenes.widgets.widget.pomodorotimer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moblin.android.LocalModel
import com.moblin.android.LocalOnNavigate
import com.moblin.android.localized
import com.moblin.android.platform.SystemImage
import com.moblin.android.platform.swiftui.FormSlider
import com.moblin.android.platform.swiftui.Picker
import com.moblin.android.platform.swiftui.Section
import com.moblin.android.platform.swiftui.formPalette
import com.moblin.android.various.model.Model
import com.moblin.android.various.settings.PomodoroBreakIcon
import com.moblin.android.various.settings.PomodoroFocusIcon
import com.moblin.android.various.settings.PomodoroPhase
import com.moblin.android.various.settings.SettingsWidgetPomodoroTimer
import com.moblin.android.view.settings.scenes.widgets.widget.alerts.SoundSelectorNavigationView
import com.moblin.android.view.utils.RgbColorPickerView
import com.moblin.android.view.utils.TextEditNavigationView
import kotlin.math.roundToInt
import com.moblin.android.various.model.updateWidgetOutlines

@Composable
private fun PomodoroIconButton(
    systemImage: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = formPalette()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .alpha(if (pressed) 0.2f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SystemImage(
            name = systemImage,
            fontSize = 28.sp,
            tint = palette.accent,
        )
    }
}

@Composable
fun WidgetPomodoroTimerQuickButtonControlsView(pomodoroTimer: SettingsWidgetPomodoroTimer) {
    val isRunning = pomodoroTimer.isRunning
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PomodoroIconButton(systemImage = "forward.end") {
            pomodoroTimer.advancePhase()
        }
        PomodoroIconButton(systemImage = "arrow.counterclockwise") {
            pomodoroTimer.reset()
        }
        PomodoroIconButton(
            systemImage = if (isRunning) "stop" else "play",
            modifier = Modifier.width(35.dp),
        ) {
            if (isRunning) {
                pomodoroTimer.pause()
            } else {
                pomodoroTimer.start()
            }
        }
    }
}

@Composable
fun WidgetPomodoroTimerSettingsView(
    model: Model = LocalModel.current,
    pomodoroTimer: SettingsWidgetPomodoroTimer,
    onNavigate: (String) -> Unit = LocalOnNavigate.current,
) {
    val focusDurationValues = listOf(1, 2, 3, 5, 10, 15, 20, 25, 30, 45, 60, 90, 120)
    val breakDurationValues = listOf(1, 2, 3, 5, 7, 10, 15, 20, 25, 30)

    Section(header = localized("Controls")) {
        WidgetPomodoroTimerQuickButtonControlsView(pomodoroTimer = pomodoroTimer)
    }
    Section(header = localized("Durations")) {
        Picker(
            title = localized("Focus"),
            selection = pomodoroTimer.focusDuration,
            options = focusDurationValues,
            text = { value -> "$value min" },
            onChange = { value ->
                pomodoroTimer.focusDuration = value
                pomodoroTimer.focusDurationChanged()
            },
        )
        Picker(
            title = localized("Break"),
            selection = pomodoroTimer.breakDuration,
            options = breakDurationValues,
            text = { value -> "$value min" },
            onChange = { value ->
                pomodoroTimer.breakDuration = value
                pomodoroTimer.breakDurationChanged()
            },
        )
    }
    Section(header = localized("Names")) {
        TextEditNavigationView(
            title = localized("Focus"),
            value = pomodoroTimer.focusName,
            onSubmit = { pomodoroTimer.focusName = it },
        )
        TextEditNavigationView(
            title = localized("Break"),
            value = pomodoroTimer.breakName,
            onSubmit = { pomodoroTimer.breakName = it },
        )
    }
    Section(header = localized("Icons")) {
        Picker(
            title = localized("Focus"),
            selection = pomodoroTimer.focusIcon,
            options = PomodoroFocusIcon.entries,
            onChange = { pomodoroTimer.focusIcon = it },
        )
        Picker(
            title = localized("Break"),
            selection = pomodoroTimer.breakIcon,
            options = PomodoroBreakIcon.entries,
            onChange = { pomodoroTimer.breakIcon = it },
        )
    }
    Section {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(localized("Width"))
            FormSlider(
                value = pomodoroTimer.width.toFloat(),
                onValueChange = {
                    val width = ((it / 0.05f).roundToInt() * 0.05f).toDouble()
                    if (pomodoroTimer.width != width) {
                        pomodoroTimer.width = width
                        model.updateWidgetOutlines()
                    }
                },
                valueRange = 1f..5f,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Section(header = localized("Colors")) {
        RgbColorPickerView(
            title = "Background",
            color = pomodoroTimer.backgroundColorColor,
            onColorChanged = { pomodoroTimer.backgroundColorColor = it },
            opacity = true,
        ) {
            pomodoroTimer.backgroundColor = it
        }
        RgbColorPickerView(
            title = "Text",
            color = pomodoroTimer.foregroundColorColor,
            onColorChanged = { pomodoroTimer.foregroundColorColor = it },
        ) {
            pomodoroTimer.foregroundColor = it
        }
        RgbColorPickerView(
            title = "Focus",
            color = pomodoroTimer.focusColorColor,
            onColorChanged = { pomodoroTimer.focusColorColor = it },
        ) {
            pomodoroTimer.focusColor = it
        }
        RgbColorPickerView(
            title = "Break",
            color = pomodoroTimer.breakColorColor,
            onColorChanged = { pomodoroTimer.breakColorColor = it },
        ) {
            pomodoroTimer.breakColor = it
        }
    }
    Section(header = localized("Sounds")) {
        SoundSelectorNavigationView(
            model = model,
            gallery = model.database.alertsMediaGallery,
            title = localized("Focus to break"),
            soundId = pomodoroTimer.focusToBreakSoundId,
            onSoundIdChange = { pomodoroTimer.focusToBreakSoundId = it },
        )
        SoundSelectorNavigationView(
            model = model,
            gallery = model.database.alertsMediaGallery,
            title = localized("Break to focus"),
            soundId = pomodoroTimer.breakToFocusSoundId,
            onSoundIdChange = { pomodoroTimer.breakToFocusSoundId = it },
        )
    }
    Section(header = localized("Chat messages")) {
        TextEditNavigationView(
            title = localized("Focus to break"),
            value = pomodoroTimer.focusToBreakChatMessage,
            onSubmit = { pomodoroTimer.focusToBreakChatMessage = it },
        )
        TextEditNavigationView(
            title = localized("Break to focus"),
            value = pomodoroTimer.breakToFocusChatMessage,
            onSubmit = { pomodoroTimer.breakToFocusChatMessage = it },
        )
    }
}
