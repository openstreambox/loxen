package com.moblin.android.various

import com.moblin.android.various.utils.Identifiable
import java.net.URI

data class FeatureVideo(
    override val id: String,
    val thumbnail: String,
) : Identifiable<String> {
    fun url(): URI {
        return URI.create("https://www.youtube.com/shorts/$id")
    }

    companion object {
        val soundBoard = FeatureVideo(id = "qJaUdIUuIhM", thumbnail = "FeatureVideoSoundBoard")
        val webcamInObs = FeatureVideo(id = "_4ISwYTKV3s", thumbnail = "FeatureVideoWebcamInObs")
        val chatSnapshotsToDiscord = FeatureVideo(
            id = "MHUT8al6Wg8",
            thumbnail = "FeatureVideoChatSnapshotsToDiscord",
        )
        val twitchRaid = FeatureVideo(id = "r-60-f4prss", thumbnail = "FeatureVideoTwitchRaid")
        val zoomAndSceneControls = FeatureVideo(
            id = "5v07BwaDe0s",
            thumbnail = "FeatureVideoZoomAndSceneControls",
        )
        val all: List<FeatureVideo> = listOf(
            soundBoard,
            webcamInObs,
            chatSnapshotsToDiscord,
            twitchRaid,
            zoomAndSceneControls,
        )
    }
}
