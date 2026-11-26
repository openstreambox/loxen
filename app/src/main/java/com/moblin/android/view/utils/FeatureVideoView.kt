package com.moblin.android.view.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moblin.android.platform.SystemImage
import com.moblin.android.platform.swiftui.AssetImage
import com.moblin.android.platform.swiftui.Section
import com.moblin.android.platform.uikit.UIColor
import com.moblin.android.platform.uikit.UIApplication
import com.moblin.android.platform.uikit.open
import com.moblin.android.various.FeatureVideo

@Composable
fun FeatureVideoCardView(video: FeatureVideo) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(10.dp))
            .background(UIColor.secondarySystemBackground)
            .clickable(interactionSource = interactionSource, indication = null) {
                UIApplication.shared.open(url = video.url())
            },
    ) {
        AssetImage(
            name = video.thumbnail,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        SystemImage(
            name = "play.circle.fill",
            fontSize = 40.sp,
            tint = Color.White,
        )
    }
}

@Composable
fun FeatureVideosGridView(videos: List<FeatureVideo>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 15.dp
        val minimum = 130.dp
        val columns = (((maxWidth + spacing) / (minimum + spacing)).toInt()).coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            videos.chunked(columns).forEach { rowVideos ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    rowVideos.forEach { video ->
                        Box(modifier = Modifier.weight(1f)) {
                            FeatureVideoCardView(video = video)
                        }
                    }
                    repeat(columns - rowVideos.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureVideosSectionView(videos: List<FeatureVideo>) {
    Section(header = "Help and inspiration") {
        FeatureVideosGridView(videos = videos)
    }
}
