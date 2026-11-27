package com.moblin.android.view.controlbar.quickbutton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moblin.android.platform.swiftui.Form
import com.moblin.android.platform.swiftui.Label
import com.moblin.android.platform.swiftui.NavigationLink
import com.moblin.android.platform.swiftui.Section
import com.moblin.android.platform.swiftui.formPalette
import com.moblin.android.various.model.Model
import com.moblin.android.various.settings.SettingsAlertsMediaGallery
import com.moblin.android.various.settings.SettingsAlertsMediaGalleryItem
import com.moblin.android.LocalModel
import com.moblin.android.various.model.playSoundBoardSound
import com.moblin.android.view.settings.scenes.widgets.widget.alerts.SoundGalleryView
import com.moblin.android.view.utils.ShortcutSectionView

@Composable
private fun SoundView(model: Model = LocalModel.current, sound: SettingsAlertsMediaGalleryItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(formPalette().cell)
            .border(1.dp, formPalette().separator, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null) {
                model.playSoundBoardSound(soundId = sound.id)
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = sound.name,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (pressed) 0.2f else 1f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
fun QuickButtonSoundBoardView(model: Model = LocalModel.current, gallery: SettingsAlertsMediaGallery) {
    Form(title = "Sound board") {
        Section {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gallery.allSounds().chunked(3).forEach { sounds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        sounds.forEach { sound ->
                            Box(modifier = Modifier.weight(1f)) {
                                key(sound.id) {
                                    SoundView(model = model, sound = sound)
                                }
                            }
                        }
                        repeat(3 - sounds.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        ShortcutSectionView {
            NavigationLink(destination = {
                SoundGalleryView(gallery = gallery, onDeleted = {})
            }) {
                Label("My sounds", systemImage = "speaker.wave.2")
            }
        }
    }
}
