package com.moblin.android.various.model

import android.location.Location
import com.moblin.android.localized
import com.moblin.android.media.haishinkit.media.video.CaptureDevice
import com.moblin.android.media.haishinkit.media.video.CaptureDevices
import com.moblin.android.media.haishinkit.media.video.VideoEffect
import com.moblin.android.platform.avfoundation.AVCaptureDevice
import com.moblin.android.platform.core.ContinuousClock
import com.moblin.android.platform.coregraphics.CGPoint
import com.moblin.android.platform.coregraphics.CGRect
import com.moblin.android.platform.swiftui.Published
import com.moblin.android.various.settings.*
import com.moblin.android.various.utils.bestFrontCameraId
import com.moblin.android.various.utils.makeUniqueName
import com.moblin.android.various.utils.truncate
import com.moblin.android.various.utils.utcTimeDeltaFromNow
import com.moblin.android.videoeffects.*
import com.moblin.android.videoeffects.alerts.AlertsEffect
import com.moblin.android.videoeffects.alerts.AlertsEffectBackgroundLandmarkRectangle
import com.moblin.android.videoeffects.alerts.alertsEffectBackgroundFaceRectangle
import com.moblin.android.videoeffects.alerts.alertsEffectBackgroundMouthRectangle
import com.moblin.android.videoeffects.alerts.alertsEffectBackgroundRightEyeRectangle
import com.moblin.android.videoeffects.browser.BrowserEffect
import com.moblin.android.videoeffects.browser.WidgetCrop
import com.moblin.android.videoeffects.dewarp360.Dewarp360Effect
import com.moblin.android.videoeffects.scoreboard.ScoreboardEffect
import com.moblin.android.videoeffects.text.TextEffect
import com.moblin.android.videoeffects.text.TextFormatPart
import com.moblin.android.videoeffects.text.isGeographyVariable
import com.moblin.android.videoeffects.text.isGForceVariable
import com.moblin.android.videoeffects.text.isWeatherVariable
import com.moblin.android.videoeffects.text.loadTextFormat
import com.moblin.android.videoeffects.vtuber.VTuberLive2DEffect
import com.moblin.android.videoeffects.vtuber.VTuberVrmEffect
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import com.moblin.android.platform.coregraphics.toCGSize
import com.moblin.android.platform.coregraphics.toComposeSize
import com.moblin.android.platform.coregraphics.toSize
import com.moblin.android.platform.corebluetooth.identifier
import com.moblin.android.remotecontrol.RemoteControlState
import com.moblin.android.various.AudioPlayer
import com.moblin.android.various.utils.add
import com.moblin.android.various.utils.duration
import com.moblin.android.videoeffects.vtuber.VTuberEffect
import com.moblin.android.view.stream.CameraPreviewWidget

class CreateWidgetWizard {
    val name = MutableStateFlow("")
    val type = MutableStateFlow(SettingsWidgetType.text)
    var widget: SettingsWidget = SettingsWidget(name = "")
    var sceneId: UUID? = null

    fun reset(scene: SettingsScene? = null) {
        name.value = ""
        sceneId = scene?.id
        type.value = SettingsWidgetType.text
        widget = SettingsWidget(name = "")
        widget.text.formatString = ""
        if (bestFrontCameraId.isNotEmpty()) {
            widget.vTuber.updateCameraId(settingsCameraId = SettingsCameraId.Front(id = bestFrontCameraId))
            widget.pngTuber.updateCameraId(settingsCameraId = SettingsCameraId.Front(id = bestFrontCameraId))
        }
        val yes = SettingsWidgetWheelOfLuckOption()
        yes.text = localized("Yes")
        val no = SettingsWidgetWheelOfLuckOption()
        no.text = localized("No")
        val later = SettingsWidgetWheelOfLuckOption()
        later.text = localized("Later")
        val spinAgain = SettingsWidgetWheelOfLuckOption()
        spinAgain.text = localized("Spin again")
        widget.wheelOfLuck.options = listOf(yes, no, later, spinAgain)
        widget.wheelOfLuck.updateText()
        widget.wheelOfLuck.updateTotalWeight()
    }
}

class CreateSceneWizard {
    val name = MutableStateFlow("")
    val widgetIds = MutableStateFlow<Set<UUID>>(emptySet())
    var scene: SettingsScene = SettingsScene(name = "")

    fun reset() {
        name.value = ""
        widgetIds.value = emptySet()
        scene = SettingsScene(name = "")
    }
}

val defaultScoreboardSize = 18.52

fun Model.widgetWizardCreate(
    type: SettingsWidgetType,
    name: String,
    widget: SettingsWidget,
    scenes: List<SettingsScene>,
) {
    widget.type = type
    widget.name = if (name.isEmpty()) {
        makeUniqueName(name = SettingsWidget.baseName, existingNames = database.widgets)
    } else {
        name
    }
    database.widgets.add(widget)
    fixAlertMedias()
    resetSelectedScene(changeScene = false, attachCamera = false)
    for (scene in scenes) {
        appendWidgetToScene(scene = scene, widget = widget)
    }
    if (widget.type == SettingsWidgetType.text) {
        textWidgetTextChanged(widget = widget)
    }
}

fun Model.getTextEffects(id: UUID): List<TextEffect> {
    val effects = mutableListOf<TextEffect>()
    textEffects[id]?.let { effects.add(it) }
    for (slideshow in slideshowEffects.values) {
        for (slide in slideshow.slides) {
            if (slide.widgetId == id) {
                (slide.effect as? TextEffect)?.let { effects.add(it) }
            }
        }
    }
    return effects
}

fun Model.getVideoSourceEffect(id: UUID): VideoSourceEffect? {
    return videoSourceEffects[id]
}

fun Model.getVTuberEffect(id: UUID): VTuberEffect? {
    return vTuberEffects[id]
}

fun Model.getPngTuberEffect(id: UUID): PngTuberEffect? {
    return pngTuberEffects[id]
}

fun Model.getSnapshotEffect(id: UUID): SnapshotEffect? {
    return snapshotEffects[id]
}

fun Model.getChatEffect(id: UUID): ChatEffect? {
    return chatEffects[id]
}

fun Model.getChatEmoteComboEffect(id: UUID): ChatEmoteComboEffect? {
    return chatEmoteComboEffects[id]
}

fun Model.getQrCodeEffect(id: UUID): QrCodeEffect? {
    return qrCodeEffects[id]
}

fun Model.getWheelOfLuckEffect(id: UUID): WheelOfLuckEffect? {
    return wheelOfLuckEffects[id]
}

private fun Model.executeWheelOfLuckOptionFunction(widget: SettingsWidget, optionId: UUID?) {
    val option = widget.wheelOfLuck.options.firstOrNull { it.id == optionId } ?: return
    handleControllerFunction(
        buttonId = "wol:${option.id}",
        function = option.function,
        functionData = option.functionData,
        pressed = false,
    )
}

fun Model.updateWheelOfLuckEffect(widget: SettingsWidget) {
    getWheelOfLuckEffect(id = widget.id)?.setSettings(settings = widget.wheelOfLuck)
    if (isMoblinCloudWheelOfLuckWidget(widget = widget)) {
        moblinCloudSendWheelOfLuck()
    }
}

fun Model.getBingoCardEffect(id: UUID): BingoCardEffect? {
    return bingoCardEffects[id]
}

fun Model.getScoreboardEffect(id: UUID): ScoreboardEffect? {
    return scoreboardEffects[id]
}

fun Model.getWidgetShapeEffect(widget: SettingsWidget, effect: SettingsVideoEffect): ShapeEffect? {
    return getWidgetVideoEffect(widget, effect) as? ShapeEffect
}

fun Model.getWidgetAnamorphicLensEffect(
    widget: SettingsWidget,
    effect: SettingsVideoEffect,
): AnamorphicLensEffect? {
    return getWidgetVideoEffect(widget, effect) as? AnamorphicLensEffect
}

fun Model.getWidgetDewarp360Effect(
    widget: SettingsWidget,
    effect: SettingsVideoEffect,
): Dewarp360Effect? {
    return getWidgetVideoEffect(widget, effect) as? Dewarp360Effect
}

fun Model.getWidgetLutEffect(widget: SettingsWidget, effect: SettingsVideoEffect): LutEffect? {
    return getWidgetVideoEffect(widget, effect) as? LutEffect
}

fun Model.getWidgetOpacityEffect(widget: SettingsWidget, effect: SettingsVideoEffect): OpacityEffect? {
    return getWidgetVideoEffect(widget, effect) as? OpacityEffect
}

fun Model.getWidgetMaskEffect(widget: SettingsWidget, effect: SettingsVideoEffect): MaskEffect? {
    return getWidgetVideoEffect(widget, effect) as? MaskEffect
}

fun Model.getWidgetRemoveBackgroundEffect(
    widget: SettingsWidget,
    effect: SettingsVideoEffect,
): RemoveBackgroundEffect? {
    return getWidgetVideoEffect(widget, effect) as? RemoveBackgroundEffect
}

fun Model.shapeEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetShapeEffect(widget, effect)?.setSettings(settings = effect.shape.toSettings())
    updateCameraPreviewWidgets()
}

fun Model.anamorphicLensEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetAnamorphicLensEffect(widget, effect)?.setSettings(settings = effect.anamorphicLens.clone())
}

fun Model.dewarp360EffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetDewarp360Effect(widget, effect)?.setSettings(settings = effect.dewarp360.toSettings())
}

fun Model.lutEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    val lut = getLogLutById(id = effect.lut.lut)
    getWidgetLutEffect(widget, effect)?.setLut(lut = lut, imageStorage = imageStorage) { title, subTitle ->
        makeErrorToast(title = title, subTitle = subTitle)
    }
}

fun Model.opacityEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetOpacityEffect(widget, effect)?.setOpacity(opacity = effect.opacity.opacity)
}

fun Model.maskEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetMaskEffect(widget, effect)?.setSettings(settings = effect.mask.toEffectSettings())
}

fun Model.removeBackgroundEffectSettingsChanged(widget: SettingsWidget, effect: SettingsVideoEffect) {
    getWidgetRemoveBackgroundEffect(widget, effect)?.setColorRange(
        from = effect.removeBackground.from,
        to = effect.removeBackground.to,
    )
}

private fun Model.getEffectWithPossibleEffects(id: UUID): VideoEffect? {
    return getVideoSourceEffect(id = id)
        ?: getImageEffect(id = id)
        ?: getBrowserEffect(id = id)
        ?: getMapEffect(id = id)
        ?: getSnapshotEffect(id = id)
        ?: getQrCodeEffect(id = id)
}

private fun Model.getWidgetVideoEffect(widget: SettingsWidget, effect: SettingsVideoEffect): VideoEffect? {
    val effectIndex = widget.effects.filter { it.enabled }.indexOfFirst { it === effect }
    if (effectIndex == -1) {
        return null
    }
    val parentEffect = getEffectWithPossibleEffects(id = widget.id) ?: return null
    if (effectIndex >= parentEffect.effects.size) {
        return null
    }
    return parentEffect.effects[effectIndex]
}

fun Model.isFixedHorizonEnabled(scene: SettingsScene): Boolean {
    return database.fixedHorizon && scene.videoSource.cameraPosition.isBuiltin()
}

fun Model.resetSelectedScene(changeScene: Boolean = true, attachCamera: Boolean = true) {
    if (enabledScenes.isNotEmpty() && changeScene) {
        setSceneId(id = enabledScenes[0].id)
        sceneSelector.sceneIndex.value = 0
    }
    resetVideoEffects(widgets = getLocalAndRemoteWidgets())
    drawOnStreamEffect.updateOverlay(
        videoSize = media.getCanvasSize().toCGSize(),
        size = drawOnStreamSize.toCGSize(),
        lines = drawOnStream.lines.value,
        mirror = streamOverlay.isFrontCameraSelected.value && !database.mirrorFrontCameraOnStream,
    )
    lutEffects.clear()
    for (lut in database.color.allLuts()) {
        val lutEffect = LutEffect()
        lutEffect.setLut(lut = lut.clone(), imageStorage = imageStorage) { title, subTitle ->
            makeErrorToast(title = title, subTitle = subTitle)
        }
        lutEffects[lut.id] = lutEffect
    }
    sceneUpdated(attachCamera = attachCamera)
}

fun Model.getSelectedScene(): SettingsScene? {
    return findEnabledScene(id = sceneSelector.selectedSceneId)
}

fun Model.showSceneSettings(scene: SettingsScene) {
    sceneSettingsPanelScene = scene
    sceneSettingsPanelSceneId.value += 1
    toggleShowingPanel(type = null, panel = ShowingPanel.none)
    toggleShowingPanel(type = null, panel = ShowingPanel.sceneSettings)
}

fun Model.selectSceneByName(name: String) {
    val scene = enabledScenes.firstOrNull { it.name.lowercase() == name.lowercase() }
    if (scene != null) {
        selectScene(id = scene.id)
    }
}

fun Model.selectScene(id: UUID) {
    if (id == sceneSelector.selectedSceneId) {
        return
    }
    val index = findEnabledSceneIndex(id = id) ?: return
    macrosEventOccurred(MacroEvent(event = SettingsMacrosEvent.SWITCH_SCENE, sceneId = id))
    sceneSelector.sceneIndex.value = index
    setSceneId(id = id)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
    switchMicIfNeededAfterSceneSwitch()
}

fun Model.setCurrentSceneVideoSource(cameraId: UUID) {
    val settingsCameraId = cameraIdToSettingsCameraId(cameraId = cameraId) ?: return
    getSelectedScene()?.updateCameraId(settingsCameraId = settingsCameraId)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.toggleWidgetOnOff(id: UUID) {
    val widget = findWidget(id = id) ?: return
    widget.enabled = !widget.enabled
    sceneUpdated()
}

fun Model.sceneUpdatedThrottled() {
    if (sceneUpdateThrottled) {
        throttledSceneUpdatePending = true
    } else {
        throttledSceneUpdate()
    }
}

private fun Model.throttledSceneUpdate() {
    sceneUpdateThrottled = true
    throttledSceneUpdatePending = false
    sceneUpdated()
    CoroutineScope(Dispatchers.Main.immediate).launch {
        delay(33)
        if (throttledSceneUpdatePending) {
            throttledSceneUpdate()
        } else {
            sceneUpdateThrottled = false
        }
    }
}

fun Model.sceneUpdated(attachCamera: Boolean = false, updateRemoteScene: Boolean = true) {
    val scene = getSelectedScene()
    if (scene == null) {
        sceneUpdatedOff()
        startTextVariableManagers()
        updateWidgetOutlines()
        return
    }
    for (browserEffect in browserEffects.values) {
        browserEffect.stop()
    }
    val resolver = makeSceneResolver()
    sceneUpdatedOn(scene = scene, resolver = resolver, attachCamera = attachCamera)
    startTextVariableManagers(resolver = resolver)
    if (updateRemoteScene) {
        remoteSceneSettingsUpdated()
    }
    updateStatusCameraText()
    updateSpeechToText(
        drawnWidgets = resolver.resolve(scene, includeRemote = true).localDrawnWidgets()
            .map { it.widget },
    )
    updateWidgetOutlines(resolver = resolver)
}

fun Model.getSceneName(id: UUID?): String? {
    val id = id ?: return null
    return database.findScene(id = id)?.name
}

fun Model.getWidgetName(id: UUID?): String? {
    val id = id ?: return null
    return database.findWidget(id = id)?.name
}

fun Model.createScene(): SettingsScene {
    val scene = createSceneWizard.scene
    scene.name = if (createSceneWizard.name.value.isEmpty()) {
        makeUniqueName(name = SettingsScene.baseName, existingNames = database.scenes)
    } else {
        createSceneWizard.name.value
    }
    database.scenes.add(scene)
    for (widget in database.widgets) {
        if (createSceneWizard.widgetIds.value.contains(widget.id)) {
            appendWidgetToScene(scene = scene, widget = widget)
        }
    }
    return scene
}

fun Model.deleteScene(scene: SettingsScene) {
    val deletedSelectedScene = getSelectedScene() === scene
    database.scenes.removeAll { it === scene }
    for (widget in database.widgets) {
        if (widget.scene.sceneId == scene.id) {
            widget.scene.sceneId = null
        }
    }
    if (sceneEditor.sceneId == scene.id) {
        sceneEditor.sceneId.value = null
    }
    if (deletedSelectedScene) {
        resetSelectedScene()
    }
    updateWidgetOutlines()
}

fun Model.deleteWidget(widget: SettingsWidget) {
    database.widgets.removeAll { it === widget }
    removeDeadWidgetsFromScenes()
    resetSelectedScene()
}

fun Model.removeDeadWidgetsFromScenes() {
    for (scene in database.scenes) {
        scene.widgets = scene.widgets.filter { findWidget(id = it.widgetId) != null }.toMutableList()
    }
}

fun Model.remoteSceneSettingsUpdated() {
    remoteSceneSettingsUpdateRequested = true
    updateRemoteSceneSettings()
}

fun Model.attachSingleLayout(scene: SettingsScene, resolver: SceneResolver) {
    if (isChatPhone()) {
        return
    }
    streamOverlay.isFrontCameraSelected.value = false
    deactivateAllMediaPlayers()
    when (scene.videoSource.cameraPosition) {
        SettingsSceneCameraPosition.back -> attachCamera(
            scene = scene,
            position = AVCaptureDevice.Position.back.rawValue,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.front -> {
            attachCamera(scene = scene, position = AVCaptureDevice.Position.front.rawValue, resolver = resolver)
            streamOverlay.isFrontCameraSelected.value = true
        }
        SettingsSceneCameraPosition.rtmp -> attachBufferedCamera(
            cameraId = scene.videoSource.rtmpCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.srtla -> attachBufferedCamera(
            cameraId = scene.videoSource.srtlaCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.srtClient -> attachBufferedCamera(
            cameraId = scene.videoSource.srtClientCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.rist -> attachBufferedCamera(
            cameraId = scene.videoSource.ristCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.rtsp -> attachBufferedCamera(
            cameraId = scene.videoSource.rtspCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.whip -> attachBufferedCamera(
            cameraId = scene.videoSource.whipCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.whep -> attachBufferedCamera(
            cameraId = scene.videoSource.whepCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.mediaPlayer -> {
            mediaPlayers[scene.videoSource.mediaPlayerCameraId]?.activate()
            attachBufferedCamera(
                cameraId = scene.videoSource.mediaPlayerCameraId,
                scene = scene,
                resolver = resolver,
            )
        }
        SettingsSceneCameraPosition.external -> attachExternalCamera(scene = scene, resolver = resolver)
        SettingsSceneCameraPosition.screenCapture -> attachBufferedCamera(
            cameraId = screenCaptureCameraId,
            scene = scene,
            resolver = resolver,
        )
        SettingsSceneCameraPosition.backTripleLowEnergy -> attachBackTripleLowEnergyCamera()
        SettingsSceneCameraPosition.backDualLowEnergy -> attachBackDualLowEnergyCamera()
        SettingsSceneCameraPosition.backWideDualLowEnergy -> attachBackWideDualLowEnergyCamera()
        SettingsSceneCameraPosition.none -> attachBufferedCamera(
            cameraId = noneCameraId,
            scene = scene,
            resolver = resolver,
        )
        else -> {}
    }
}

fun Model.findWidget(id: UUID): SettingsWidget? {
    for (widget in getLocalAndRemoteWidgets()) {
        if (widget.id == id) {
            return widget
        }
    }
    return null
}

fun Model.findWidget(name: String): SettingsWidget? {
    for (widget in getLocalAndRemoteWidgets()) {
        if (widget.name == name) {
            return widget
        }
    }
    return null
}

fun Model.getTextWidget(id: UUID?): SettingsWidget? {
    val id = id ?: return null
    val widget = findWidget(id = id) ?: return null
    if (widget.type == SettingsWidgetType.text) {
        return widget
    }
    return null
}

fun Model.findEnabledScene(id: UUID): SettingsScene? {
    return enabledScenes.firstOrNull { it.id == id }
}

fun Model.findEnabledSceneIndex(id: UUID): Int? {
    val index = enabledScenes.indexOfFirst { it.id == id }
    return if (index == -1) null else index
}

fun Model.getFillFrame(scene: SettingsScene): Boolean {
    return scene.fillFrame
}

fun Model.drawnWidgetsInSelectedAndRemoteScene(resolver: SceneResolver? = null): List<SettingsWidget> {
    val resolver = resolver ?: makeSceneResolver()
    var widgets: MutableList<SettingsWidget> = mutableListOf()
    val scene = getSelectedScene()
    if (scene != null) {
        widgets = resolver.resolve(scene, includeRemote = true).localDrawnWidgets()
            .map { it.widget }.toMutableList()
    }
    val remoteSceneId = database.remoteSceneId ?: return widgets
    val remoteScene = database.findScene(id = remoteSceneId) ?: return widgets
    val widgetIds = widgets.map { it.id }.toMutableSet()
    for (widget in resolver.resolve(remoteScene).drawnWidgets().map { it.widget }) {
        if (widgetIds.add(widget.id)) {
            widgets.add(widget)
        }
    }
    return widgets
}

fun Model.sceneWidgetsInSelectedScene(): List<ResolvedSceneWidget> {
    val scene = getSelectedScene() ?: return emptyList()
    return resolveScene(scene, includeDisabledSceneWidgets = true).firstWidgets()
}

fun Model.switchToNextSceneRoundRobin() {
    val currentSceneIndex = findEnabledSceneIndex(id = sceneSelector.selectedSceneId) ?: return
    val nextSceneIndex = (currentSceneIndex + 1) % enabledScenes.size
    if (nextSceneIndex == currentSceneIndex) {
        return
    }
    selectScene(id = enabledScenes[nextSceneIndex].id)
}

fun Model.appendWidgetToScene(scene: SettingsScene, widget: SettingsWidget) {
    if (!addableWidgets(scene = scene).any { it === widget }) {
        return
    }
    scene.widgets.add(SettingsSceneWidget(widget = widget))
    var attachCamera = false
    if (scene.id == getSelectedScene()?.id) {
        attachCamera = isCaptureDeviceWidget(widget = widget)
    }
    sceneUpdated(attachCamera = attachCamera)
}

fun Model.sceneEnabledChanged(scene: SettingsScene) {
    if (getSelectedScene() === scene) {
        resetSelectedScene()
    } else {
        sceneSelector.sceneIndex.value += 0
    }
}

fun Model.sceneSetCamera(scene: SettingsScene, settingsCameraId: SettingsCameraId) {
    scene.updateCameraId(settingsCameraId = settingsCameraId)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.sceneVideoSourceRotationChanged() {
    sceneUpdated(updateRemoteScene = false)
}

fun Model.sceneVideoSourceSettingsChanged() {
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.sceneQuickSwitchGroupChanged(scene: SettingsScene) {
    if (getSelectedScene() === scene) {
        resetSelectedScene(changeScene = false, attachCamera = true)
    }
}

fun Model.sceneMicChanged(scene: SettingsScene) {
    if (getSelectedScene() === scene) {
        switchMicIfNeededAfterSceneSwitch()
    }
}

fun Model.sceneBackgroundColorChanged() {
    sceneSelector.sceneIndex.value += 0
}

fun Model.widgetEnabledChanged(widget: SettingsWidget) {
    sceneUpdated(attachCamera = isCaptureDeviceWidget(widget = widget))
}

fun Model.chatWidgetSettingsChanged(widget: SettingsWidget) {
    getChatEffect(id = widget.id)?.setSettings(settings = widget.chat)
    updateWidgetOutlines()
}

fun Model.bingoCardWidgetSettingsChanged(widget: SettingsWidget) {
    getBingoCardEffect(id = widget.id)?.setSettings(settings = widget.bingoCard)
}

fun Model.vTuberWidgetSettingsChanged(widget: SettingsWidget) {
    val vTuber = widget.vTuber
    getVTuberEffect(id = widget.id)?.setSettings(
        cameraFieldOfView = vTuber.cameraFieldOfView,
        cameraPositionY = vTuber.cameraPositionY,
        mirror = vTuber.mirror,
        sensitivity = vTuber.sensitivity,
        armsAngle = vTuber.armsAngle,
    )
}

fun Model.pngTuberWidgetSettingsChanged(widget: SettingsWidget) {
    getPngTuberEffect(id = widget.id)?.setSettings(
        mirror = widget.pngTuber.mirror,
        sensitivity = widget.pngTuber.sensitivity,
    )
}

fun Model.videoSourceWidgetSetCamera(widget: SettingsWidget, settingsCameraId: SettingsCameraId) {
    widget.videoSource.updateCameraId(settingsCameraId = settingsCameraId)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.vTuberWidgetSetCamera(widget: SettingsWidget, settingsCameraId: SettingsCameraId) {
    widget.vTuber.updateCameraId(settingsCameraId = settingsCameraId)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.pngTuberWidgetSetCamera(widget: SettingsWidget, settingsCameraId: SettingsCameraId) {
    widget.pngTuber.updateCameraId(settingsCameraId = settingsCameraId)
    sceneUpdated(attachCamera = true, updateRemoteScene = false)
}

fun Model.videoSourceWidgetSettingsChanged(widget: SettingsWidget) {
    getVideoSourceEffect(id = widget.id)?.setSettings(settings = widget.videoSource.toEffectSettings())
    updateCameraPreviewWidgets()
}

fun Model.chatEmoteComboWidgetSettingsChanged(widget: SettingsWidget) {
    getChatEmoteComboEffect(id = widget.id)?.setSettings(settings = widget.chatEmoteCombo)
}

fun Model.snapshotWidgetShowtimeChanged(widget: SettingsWidget) {
    getSnapshotEffect(id = widget.id)?.setSettings(showtime = widget.snapshot.showtime)
}

fun Model.textWidgetBackgroundColorChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setBackgroundColor(color = widget.text.backgroundColor)
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetForegroundColorChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setForegroundColor(color = widget.text.foregroundColor)
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetFontSizeChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFontSize(size = widget.text.fontSize.toFloat())
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetFontChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFont(font = widget.text.font)
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetFontDesignChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFontDesign(design = widget.text.fontDesign.toUiKit())
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetFontWeightChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFontWeight(weight = widget.text.fontWeight.toSystem())
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetFontMonospacedDigitsChanged(widget: SettingsWidget) {
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFontMonospacedDigits(enabled = widget.text.fontMonospacedDigits)
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetLayoutChanged(widget: SettingsWidget) {
    val text = widget.text
    for (effect in getTextEffects(id = widget.id)) {
        effect.setLayout(
            alignment = text.horizontalAlignment,
            width = if (text.widthEnabled) text.width else null,
            cornerRadius = text.cornerRadius.toDouble(),
        )
    }
    remoteSceneSettingsUpdated()
}

fun Model.textWidgetTextChanged(widget: SettingsWidget) {
    val parts = loadTextFormat(format = widget.text.formatString)
    for (effect in getTextEffects(id = widget.id)) {
        effect.setFormat(format = widget.text.formatString)
        updateTimers(widget.text, effect, parts)
        updateStopwatches(widget.text, effect, parts)
        updateCheckboxes(widget.text, effect, parts)
        updateRatings(widget.text, effect, parts)
        updateLapTimes(widget.text, effect, parts)
        updateSubtitles(widget.text, effect, parts)
    }
    widget.text.needsWeather = parts.isWeatherVariable()
    widget.text.needsGeography = parts.isGeographyVariable()
    widget.text.needsGForce = parts.isGForceVariable()
    sceneUpdated()
}

fun Model.updateSettingsFromTextWidgets() {
    for (widgetId in textEffects.keys) {
        val widget = findWidget(id = widgetId) ?: continue
        for (stopwatch in widget.text.stopwatches) {
            if (stopwatch.running) {
                stopwatch.totalElapsed += (Instant.now().toEpochMilli() - stopwatch.playPressedTime.toEpochMilli()) / 1000.0
            }
        }
    }
}

fun Model.loadTextWidgetStopwatches() {
    for (widget in database.widgets) {
        if (widget.type != SettingsWidgetType.text) {
            continue
        }
        for (stopwatch in widget.text.stopwatches) {
            if (stopwatch.running) {
                stopwatch.playPressedTime = Instant.now()
            }
        }
    }
}

fun Model.forceUpdateTextEffects() {
    for (effect in textEffects.values) {
        effect.forceOverlayUpdate()
    }
    for (effect in slideshowEffects.values) {
        for (slide in effect.slides) {
            (slide.effect as? TextEffect)?.forceOverlayUpdate()
        }
    }
}

fun Model.updateMapEffects() {
    if (mapEffects.isEmpty()) {
        return
    }
    var location = locationManager.getLatestKnownLocation()
    val latestKnownLocation = location
    if (latestKnownLocation != null && isLocationInPrivacyRegion(location = latestKnownLocation)) {
        location = Location("")
    }
    location?.let { remoteControlAssistantSetRemoteSceneDataLocation(location = it) }
    val remoteLocation = remoteSceneData.location?.toLocation()
    for ((widgetId, mapEffect) in mapEffects) {
        val effectiveLocation = if (isRemoteSceneWidget(id = widgetId)) remoteLocation else location
        if (effectiveLocation != null) {
            mapEffect.updateLocation(location = effectiveLocation)
        }
    }
}

fun Model.isSceneVideoSourceActive(scene: SettingsScene): Boolean {
    return when (scene.videoSource.cameraPosition) {
        SettingsSceneCameraPosition.rtmp -> activeBufferedVideoIds.contains(scene.videoSource.rtmpCameraId)
        SettingsSceneCameraPosition.srtla -> activeBufferedVideoIds.contains(scene.videoSource.srtlaCameraId)
        SettingsSceneCameraPosition.srtClient -> activeBufferedVideoIds.contains(scene.videoSource.srtClientCameraId)
        SettingsSceneCameraPosition.rist -> activeBufferedVideoIds.contains(scene.videoSource.ristCameraId)
        SettingsSceneCameraPosition.rtsp -> activeBufferedVideoIds.contains(scene.videoSource.rtspCameraId)
        SettingsSceneCameraPosition.whip -> activeBufferedVideoIds.contains(scene.videoSource.whipCameraId)
        SettingsSceneCameraPosition.whep -> activeBufferedVideoIds.contains(scene.videoSource.whepCameraId)
        SettingsSceneCameraPosition.external -> isExternalCameraConnected(
            cameraId = scene.videoSource.externalCameraId,
        )
        else -> true
    }
}

fun Model.isCurrentScenesVideoSourceNetwork(cameraId: UUID): Boolean {
    val scene = getSelectedScene() ?: return false
    return scene.videoSource.isNetwork(cameraId = cameraId)
}

fun Model.isSceneVideoSourceActive(sceneId: UUID): Boolean {
    val scene = findEnabledScene(id = sceneId) ?: return false
    return isSceneVideoSourceActive(scene = scene)
}

private fun Model.makeCaptureDevice(device: AVCaptureDevice): CaptureDevice {
    return CaptureDevice(
        device = device,
        id = UUID.nameUUIDFromBytes(device.uniqueID.toByteArray()),
        isVideoMirrored = false,
    )
}

fun Model.getBuiltinCameraDevices(
    scene: SettingsScene,
    sceneDevice: AVCaptureDevice?,
    resolver: SceneResolver,
): CaptureDevices {
    val devices = CaptureDevices(hasSceneDevice = false, devices = mutableListOf())
    if (sceneDevice != null) {
        devices.hasSceneDevice = true
        devices.devices.add(makeCaptureDevice(device = sceneDevice))
    }
    val quickSwitchGroup = scene.quickSwitchGroup
    if (quickSwitchGroup != null) {
        for (otherScene in enabledScenes) {
            if (otherScene.quickSwitchGroup == quickSwitchGroup) {
                getBuiltinCameraDevices(videoSource = otherScene.videoSource, devices = devices.devices)
                getBuiltinCameraDevicesInScene(
                    scene = otherScene,
                    devices = devices.devices,
                    resolver = resolver,
                )
            }
        }
    }
    getBuiltinCameraDevicesInScene(scene = scene, devices = devices.devices, resolver = resolver)
    return devices
}

fun Model.getCameraPreviewDeviceIds(
    scene: SettingsScene,
    sceneDevice: AVCaptureDevice?,
    resolver: SceneResolver,
): List<UUID> {
    val deviceIds = mutableSetOf<UUID>()
    if (sceneDevice != null) {
        deviceIds.add(makeCaptureDevice(device = sceneDevice).id)
    }
    for (otherScene in getQuickSwitchScenes(scene = scene, resolver = resolver)) {
        val devices = mutableListOf<CaptureDevice>()
        getBuiltinCameraDevices(videoSource = otherScene.videoSource, devices = devices)
        deviceIds.addAll(devices.map { it.id })
        deviceIds.addAll(
            getCameraPreviewWidgets(resolvedScene = resolver.resolve(otherScene)).map { it.deviceId },
        )
    }
    return deviceIds.toList()
}

private fun Model.getQuickSwitchScenes(scene: SettingsScene, resolver: SceneResolver): List<SettingsScene> {
    val deviceIds = getSceneBuiltinCameraDeviceIds(scene = scene, resolver = resolver)
    return enabledScenes.filter { otherScene ->
        val quickSwitchGroup = scene.quickSwitchGroup
        if (quickSwitchGroup != null && otherScene.quickSwitchGroup == quickSwitchGroup) {
            return@filter true
        }
        getSceneBuiltinCameraDeviceIds(scene = otherScene, resolver = resolver) == deviceIds
    }
}

private fun Model.getSceneBuiltinCameraDeviceIds(scene: SettingsScene, resolver: SceneResolver): Set<UUID> {
    val devices = mutableListOf<CaptureDevice>()
    getBuiltinCameraDevices(videoSource = scene.videoSource, devices = devices)
    getBuiltinCameraDevicesInScene(scene = scene, devices = devices, resolver = resolver)
    return devices.map { it.id }.toSet()
}

private fun Model.getCameraPreviewWidgets(resolvedScene: ResolvedScene): List<CameraPreviewWidget> {
    val widgets = mutableListOf<CameraPreviewWidget>()
    for (widget in resolvedScene.localDrawnWidgets()) {
        if (widget.widget.type != SettingsWidgetType.videoSource) {
            continue
        }
        val videoSource = widget.widget.videoSource
        val cameraId = videoSource.videoSource.getCaptureDeviceCameraId() ?: continue
        val device = AVCaptureDevice.withUniqueID(cameraId) ?: continue
        val previewWidget = CameraPreviewWidget(
            id = widget.widget.id,
            deviceId = makeCaptureDevice(device = device).id,
            layout = widget.sceneWidget.layout,
            mirror = videoSource.mirror,
            shape = WidgetShape(
                contentRegion = CGRect(origin = CGPoint.zero, size = media.getCanvasSize().toCGSize()),
                rotation = videoSource.rotation,
            ),
        )
        for (effect in widget.widget.effects) {
            if (effect.enabled && effect.type == SettingsVideoEffectType.shape) {
                previewWidget.shape.apply(effect.shape.toSettings())
            }
        }
        widgets.add(previewWidget)
    }
    return widgets
}

fun Model.updateCameraPreviewWidgets() {
    val scene = getSelectedScene() ?: return
    updateCameraPreviewWidgets(resolvedScene = resolveScene(scene))
}

private fun Model.updateCameraPreviewWidgets(resolvedScene: ResolvedScene) {
    cameraPreviewView.setWidgets(
        widgets = getCameraPreviewWidgets(resolvedScene = resolvedScene),
        canvasSize = media.getCanvasSize().toCGSize(),
    )
}

private fun Model.createGlobalVideoEffects() {
    faceEffect = FaceEffect()
    updateFaceFilterSettings()
    movieEffect = MovieEffect()
    grayScaleEffect = GrayScaleEffect()
    sepiaEffect = SepiaEffect()
    tripleEffect = TripleEffect()
    twinEffect = TwinEffect()
    pixellateEffect = PixellateEffect(strength = database.pixellateStrength)
    cameraManEffect = CameraManEffect(
        moveVertically = database.debug.cameraManMoveVertically.value,
        speed = database.debug.cameraManSpeed.value,
        alwaysMove = database.debug.cameraManAlwaysMove.value,
    )
    pollEffect = null
    whirlpoolEffect = WhirlpoolEffect(angle = database.whirlpoolAngle)
    pinchEffect = PinchEffect(scale = database.pinchScale)
    fixedHorizonEffect = FixedHorizonEffect()
    glassesEffect = createGlassesEffect()
    sparkleEffect = createSparkleEffect()
    popeHatEffect = createPopeHatEffect()
    chefHatEffect = createChefHatEffect()
    moustacheEffect = createMoustacheEffect()
    poopEffect = createPoopEffect()
    beautyEffect = BeautyEffect(fps = stream.value.fps.toFloat())
    beautyEffect.setSmoothnessSettings(
        radius = database.beauty.smoothnessRadius,
        strength = database.beauty.smoothnessStrength,
    )
    beautyEffect.setShapeSettings(
        position = database.beauty.shapePosition,
        radius = database.beauty.shapeRadius,
        strength = database.beauty.shapeStrength,
    )
}

private fun Model.createGlassesEffect(): AlertsEffect {
    val settings = SettingsWidgetAlerts()
    settings.disableAll()
    settings.quickButton.enabled = true
    settings.quickButton.positionType = SettingsWidgetAlertPositionType.face
    settings.quickButton.facePosition.x = 0.24
    settings.quickButton.facePosition.y = 0.30
    settings.quickButton.facePosition.width = 0.49
    settings.quickButton.facePosition.height = 0.49
    settings.quickButton.imageId = database.alertsMediaGallery.getGlassesImageId()
    settings.quickButton.imageLoopCount = 3
    return AlertsEffect(
        settings = settings,
        delegate = this,
        mediaStorage = alertMediaStorage,
        bundledImages = database.alertsMediaGallery.bundledImages,
        bundledSounds = database.alertsMediaGallery.bundledSounds,
    )
}

private fun Model.createPopeHatEffect(): AlertsEffect {
    return createFaceReactionEffect(
        imageId = database.alertsMediaGallery.getPopeHatImageId(),
        landmark = alertsEffectBackgroundFaceRectangle,
        centerY = -1.05,
        height = 1.8,
    )
}

private fun Model.createChefHatEffect(): AlertsEffect {
    return createFaceReactionEffect(
        imageId = database.alertsMediaGallery.getChefHatImageId(),
        landmark = alertsEffectBackgroundFaceRectangle,
        centerY = -0.85,
        height = 1.45,
    )
}

private fun Model.createMoustacheEffect(): AlertsEffect {
    return createFaceReactionEffect(
        imageId = database.alertsMediaGallery.getMoustacheImageId(),
        landmark = alertsEffectBackgroundMouthRectangle,
        centerY = 0.05,
        height = 0.3,
    )
}

private fun Model.createPoopEffect(): AlertsEffect {
    return createFaceReactionEffect(
        imageId = database.alertsMediaGallery.getPoopImageId(),
        landmark = alertsEffectBackgroundFaceRectangle,
        centerY = -0.95,
        height = 1.21,
    )
}

private fun Model.createFaceReactionEffect(
    imageId: UUID,
    landmark: AlertsEffectBackgroundLandmarkRectangle,
    centerY: Double,
    height: Double,
): AlertsEffect {
    val settings = SettingsWidgetAlerts()
    settings.disableAll()
    settings.quickButton.enabled = true
    settings.quickButton.positionType = SettingsWidgetAlertPositionType.face
    val size = height * alertsEffectBackgroundFaceRectangle.height()
    settings.quickButton.facePosition.x = (landmark.topLeftX + landmark.bottomRightX) / 2 - size / 2
    settings.quickButton.facePosition.y = landmark.topLeftY + centerY * landmark.height() - size / 2
    settings.quickButton.facePosition.width = size
    settings.quickButton.facePosition.height = size
    settings.quickButton.imageId = imageId
    return AlertsEffect(
        settings = settings,
        delegate = this,
        mediaStorage = alertMediaStorage,
        bundledImages = database.alertsMediaGallery.bundledImages,
        bundledSounds = database.alertsMediaGallery.bundledSounds,
    )
}

private fun Model.createSparkleEffect(): AlertsEffect {
    val settings = SettingsWidgetAlerts()
    settings.disableAll()
    settings.quickButton.enabled = true
    settings.quickButton.positionType = SettingsWidgetAlertPositionType.face
    settings.quickButton.facePosition.x = (alertsEffectBackgroundRightEyeRectangle.topLeftX +
        alertsEffectBackgroundRightEyeRectangle.bottomRightX) / 2 + 0.01
    settings.quickButton.facePosition.y = alertsEffectBackgroundRightEyeRectangle.topLeftY + 0.03
    settings.quickButton.facePosition.width = alertsEffectBackgroundRightEyeRectangle.width() * 0.8
    settings.quickButton.facePosition.height = alertsEffectBackgroundRightEyeRectangle.height() * 0.8
    settings.quickButton.imageId = database.alertsMediaGallery.getWhiteStarImageId()
    return AlertsEffect(
        settings = settings,
        delegate = this,
        mediaStorage = alertMediaStorage,
        bundledImages = database.alertsMediaGallery.bundledImages,
        bundledSounds = database.alertsMediaGallery.bundledSounds,
    )
}

private fun Model.registerGlobalVideoEffects(scene: SettingsScene): List<VideoEffect> {
    val effects = mutableListOf<VideoEffect>()
    sparkleEffect?.let { effects.add(it) }
    glassesEffect?.let { effects.add(it) }
    popeHatEffect?.let { effects.add(it) }
    chefHatEffect?.let { effects.add(it) }
    moustacheEffect?.let { effects.add(it) }
    poopEffect?.let { effects.add(it) }
    val fixedHorizonStatus: String
    if (isFixedHorizonEnabled(scene = scene)) {
        fixedHorizonEffect.start(portrait = stream.value.portrait)
        fixedHorizonStatus = "Enabled"
        effects.add(fixedHorizonEffect)
    } else {
        fixedHorizonStatus = "Disabled"
        fixedHorizonEffect.stop()
    }
    if (fixedHorizonStatus != statusTopRight.fixedHorizonStatus.value) {
        statusTopRight.fixedHorizonStatus.value = fixedHorizonStatus
    }
    if (isFaceEnabled()) {
        effects.add(faceEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.cameraMan)) {
        effects.add(cameraManEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.whirlpool)) {
        effects.add(whirlpoolEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.pinch)) {
        effects.add(pinchEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.movie)) {
        effects.add(movieEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.fourThree)) {
        effects.add(fourThreeEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.grayScale)) {
        effects.add(grayScaleEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.sepia)) {
        effects.add(sepiaEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.triple)) {
        effects.add(tripleEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.twin)) {
        effects.add(twinEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.pixellate)) {
        pixellateEffect.setSettings(strength = database.pixellateStrength)
        effects.add(pixellateEffect)
    }
    if (database.beauty.enabled) {
        effects.add(beautyEffect)
    }
    if (isQuickButtonOn(type = SettingsQuickButtonType.crt)) {
        effects.add(crtEffect)
    }
    return effects
}

private fun Model.registerGlobalVideoEffectsOnTop(): List<VideoEffect> {
    val effects = mutableListOf<VideoEffect>()
    if (isQuickButtonOn(type = SettingsQuickButtonType.poll)) {
        pollEffect?.let { effects.add(it) }
    }
    return effects
}

fun Model.getImageEffect(id: UUID): ImageEffect? {
    return imageEffects[id]
}

private fun Model.getBrowserEffect(id: UUID): BrowserEffect? {
    return browserEffects[id]
}

private fun Model.getMapEffect(id: UUID): MapEffect? {
    return mapEffects[id]
}

fun Model.setBrowserEffectsProxyServer() {
    for (effect in browserEffects.values) {
        effect.setProxyServer(endpoint = getHttpProxyServerEndpoint())
    }
}

private fun Model.resetVideoEffects(widgets: List<SettingsWidget>) {
    createGlobalVideoEffects()
    resetImageEffects(widgets = widgets)
    resetTextVideoEffects(widgets = widgets)
    resetBrowserVideoEffects(widgets = widgets)
    resetMapVideoEffects(widgets = widgets)
    resetQrCodeVideoEffects(widgets = widgets)
    resetVideoSourceVideoEffects(widgets = widgets)
    resetScoreboardVideoEffects(widgets = widgets)
    resetAlertsVideoEffects(widgets = widgets)
    resetVTuberVideoEffects(widgets = widgets)
    resetPngTuberVideoEffects(widgets = widgets)
    resetSnapshotVideoEffects(widgets = widgets)
    resetChatVideoEffects(widgets = widgets)
    resetChatEmoteComboVideoEffects(widgets = widgets)
    resetSlideshowVideoEffects(widgets = widgets)
    resetWheelOfLuckEffects(widgets = widgets)
    resetBingoCardEffects(widgets = widgets)
    resetPomodoroTimerEffects(widgets = widgets)
    browsers.value = browserEffects.map { (widgetId, browser) ->
        val name = getWidgetName(id = widgetId) ?: "Unknown"
        Browser(name = name, browserEffect = browser)
    }.sortedBy { it.name }
}

private fun Model.createImageEffect(widget: SettingsWidget): ImageEffect {
    return ImageEffect(imageStorage = imageStorage, widgetId = widget.id)
}

private fun Model.resetImageEffects(widgets: List<SettingsWidget>) {
    imageEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.image) {
            continue
        }
        val effect = createImageEffect(widget = widget)
        effect.effects = widget.getEffects(model = this).toMutableList()
        imageEffects[widget.id] = effect
    }
}

private fun Model.createTextEffect(widget: SettingsWidget): TextEffect {
    return TextEffect(
        format = widget.text.formatString,
        backgroundColor = widget.text.backgroundColor,
        foregroundColor = widget.text.foregroundColor,
        fontSize = widget.text.fontSize.toFloat(),
        font = widget.text.font,
        fontDesign = widget.text.fontDesign.toUiKit(),
        fontWeight = widget.text.fontWeight.toSystem(),
        fontMonospacedDigits = widget.text.fontMonospacedDigits,
        horizontalAlignment = widget.text.horizontalAlignment,
        width = if (widget.text.widthEnabled) widget.text.width else null,
        cornerRadius = widget.text.cornerRadius.toDouble(),
        delay = widget.text.delay,
        timersEndTime = widget.text.timers.map {
            ContinuousClock.now.advanced(bySeconds = utcTimeDeltaFromNow(to = it.endTime))
        },
        stopwatches = widget.text.stopwatches.map { it.clone() },
        checkboxes = widget.text.checkboxes.map { it.checked },
        ratings = widget.text.ratings.map { it.rating },
        lapTimes = widget.text.lapTimes.map { it.lapTimes },
    )
}

private fun Model.resetTextVideoEffects(widgets: List<SettingsWidget>) {
    textEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.text) {
            continue
        }
        textEffects[widget.id] = createTextEffect(widget = widget)
    }
}

private fun Model.resetBrowserVideoEffects(widgets: List<SettingsWidget>) {
    for (effect in browserEffects.values) {
        effect.stop()
    }
    browserEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.browser) {
            continue
        }
        val request = widget.browser.getRequest() ?: continue
        val effect = BrowserEffect(
            url = URI(request.url.toString()),
            styleSheet = widget.browser.styleSheet,
            widget = widget.browser,
            moblinAccess = widget.browser.moblinAccess,
            proxyServer = getHttpProxyServerEndpoint(),
        )
        effect.effects = widget.getEffects(model = this).toMutableList()
        browserEffects[widget.id] = effect
    }
    moblinCloudMediaShareEffectsReset()
}

private fun Model.resetMapVideoEffects(widgets: List<SettingsWidget>) {
    mapEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.map) {
            continue
        }
        val effect = MapEffect(widget = widget.map)
        effect.effects = widget.getEffects(model = this).toMutableList()
        mapEffects[widget.id] = effect
    }
}

private fun Model.resetQrCodeVideoEffects(widgets: List<SettingsWidget>) {
    qrCodeEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.qrCode) {
            continue
        }
        val effect = QrCodeEffect(widget = widget.qrCode.clone())
        effect.effects = widget.getEffects(model = this).toMutableList()
        qrCodeEffects[widget.id] = effect
    }
}

private fun Model.resetVideoSourceVideoEffects(widgets: List<SettingsWidget>) {
    videoSourceEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.videoSource) {
            continue
        }
        val effect = VideoSourceEffect()
        effect.effects = widget.getEffects(model = this).toMutableList()
        videoSourceEffects[widget.id] = effect
    }
}

private fun Model.resetScoreboardVideoEffects(widgets: List<SettingsWidget>) {
    scoreboardEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.scoreboard) {
            continue
        }
        scoreboardEffects[widget.id] = ScoreboardEffect(canvasSize = media.getCanvasSize().toCGSize().toComposeSize())
    }
}

private fun Model.resetAlertsVideoEffects(widgets: List<SettingsWidget>) {
    alertsEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.alerts) {
            continue
        }
        alertsEffects[widget.id] = AlertsEffect(
            settings = widget.alerts.clone(),
            delegate = this,
            mediaStorage = alertMediaStorage,
            bundledImages = database.alertsMediaGallery.bundledImages,
            bundledSounds = database.alertsMediaGallery.bundledSounds,
        )
    }
}

private fun Model.resetVTuberVideoEffects(widgets: List<SettingsWidget>) {
    vTuberEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.vTuber) {
            continue
        }
        val path = vTuberStorage.makePath(id = widget.vTuber.id)
        when (widget.vTuber.type) {
            SettingsWidgetVTuberType.vrm -> {
                vTuberEffects[widget.id] = VTuberVrmEffect(
                    vrm = path.path,
                    cameraFieldOfView = widget.vTuber.cameraFieldOfView,
                    cameraPositionY = widget.vTuber.cameraPositionY,
                )
            }
            SettingsWidgetVTuberType.live2D -> {
                vTuberEffects[widget.id] = VTuberLive2DEffect(directory = path)
            }
            else -> {}
        }
    }
}

private fun Model.resetPngTuberVideoEffects(widgets: List<SettingsWidget>) {
    pngTuberEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.pngTuber) {
            continue
        }
        pngTuberEffects[widget.id] = PngTuberEffect(
            model = com.moblin.android.various.storages.FileStorage(directory = com.moblin.android.various.storages.pngTuberStorageDirectory).makePath(id = widget.pngTuber.id).path,
            costume = 1,
        )
    }
}

private fun Model.resetSnapshotVideoEffects(widgets: List<SettingsWidget>) {
    snapshotEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.snapshot) {
            continue
        }
        val effect = SnapshotEffect(showtime = widget.snapshot.showtime)
        effect.effects = widget.getEffects(model = this).toMutableList()
        snapshotEffects[widget.id] = effect
    }
}

private fun Model.resetChatVideoEffects(widgets: List<SettingsWidget>) {
    chatEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.chat) {
            continue
        }
        val effect = ChatEffect(chat = chatWidgetChat)
        effect.setSettings(settings = widget.chat)
        chatEffects[widget.id] = effect
    }
}

private fun Model.resetChatEmoteComboVideoEffects(widgets: List<SettingsWidget>) {
    chatEmoteComboEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.chatEmoteCombo) {
            continue
        }
        val effect = ChatEmoteComboEffect(canvasSize = media.getCanvasSize().toCGSize())
        effect.setSettings(settings = widget.chatEmoteCombo)
        chatEmoteComboEffects[widget.id] = effect
    }
}

private fun Model.resetSlideshowVideoEffects(widgets: List<SettingsWidget>) {
    slideshowEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.slideshow) {
            continue
        }
        val slides = mutableListOf<SlideshowEffectSlide>()
        for (slide in widget.slideshow.slides) {
            val widgetId = slide.widgetId ?: continue
            val slideWidget = findWidget(id = widgetId) ?: continue
            val effect: VideoEffect = when (slideWidget.type) {
                SettingsWidgetType.text -> createTextEffect(widget = slideWidget)
                SettingsWidgetType.image -> createImageEffect(widget = slideWidget)
                else -> continue
            }
            slides.add(
                SlideshowEffectSlide(
                    widgetId = widgetId,
                    effect = effect,
                    time = slide.time.toDouble(),
                ),
            )
        }
        slideshowEffects[widget.id] = SlideshowEffect(slides = slides)
    }
}

private fun Model.resetWheelOfLuckEffects(widgets: List<SettingsWidget>) {
    wheelOfLuckEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.wheelOfLuck) {
            continue
        }
        wheelOfLuckEffects[widget.id] = WheelOfLuckEffect(
            canvasSize = media.getCanvasSize().toCGSize().toComposeSize(),
            onResult = { result ->
                widget.wheelOfLuck.appendResult(result = result)
                executeWheelOfLuckOptionFunction(widget = widget, optionId = result.optionId)
            },
        )
    }
}

private fun Model.resetBingoCardEffects(widgets: List<SettingsWidget>) {
    bingoCardEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.bingoCard) {
            continue
        }
        bingoCardEffects[widget.id] = BingoCardEffect(canvasSize = media.getCanvasSize().toCGSize().toComposeSize())
    }
}

private fun Model.resetPomodoroTimerEffects(widgets: List<SettingsWidget>) {
    pomodoroTimerEffects.clear()
    for (widget in widgets) {
        if (widget.type != SettingsWidgetType.pomodoroTimer) {
            continue
        }
        pomodoroTimerEffects[widget.id] = PomodoroTimerEffect(canvasSize = media.getCanvasSize().toCGSize().toComposeSize())
        val pomodoroTimer = widget.pomodoroTimer
        pomodoroTimer.onPhaseChanged = { newPhase ->
            onPomodoroTimerPhaseChanged(pomodoroTimer, newPhase = newPhase)
        }
    }
}

private fun Model.onPomodoroTimerPhaseChanged(
    settings: SettingsWidgetPomodoroTimer,
    newPhase: PomodoroPhase,
) {
    when (newPhase) {
        PomodoroPhase.focus -> {
            settings.breakToFocusSoundId?.let { playPomodoroSound(soundId = it) }
            if (settings.breakToFocusChatMessage.isNotEmpty()) {
                sendChatMessage(message = settings.breakToFocusChatMessage)
            }
        }
        PomodoroPhase.shortBreak -> {
            settings.focusToBreakSoundId?.let { playPomodoroSound(soundId = it) }
            if (settings.focusToBreakChatMessage.isNotEmpty()) {
                sendChatMessage(message = settings.focusToBreakChatMessage)
            }
        }
        else -> {}
    }
}

private fun Model.playPomodoroSound(soundId: UUID) {
    val url = getAlertSoundUrl(soundId = soundId) ?: return
    pomodoroAudioPlayer = runCatching { AudioPlayer(contentsOf = url) }.getOrNull()
    pomodoroAudioPlayer?.play()
}

private fun Model.isQuickButtonOn(type: SettingsQuickButtonType): Boolean {
    return database.quickButtons.firstOrNull { it.type == type }?.isOn?.value ?: false
}

private fun Model.isFaceEnabled(): Boolean {
    val settings = database.face
    return settings.blurFaces || settings.blurText || settings.blurBackground || settings.showMoblin
}

private fun Model.setSceneId(id: UUID) {
    sceneSelector.selectedSceneId = id
    remoteControlStateChanged(state = RemoteControlState(scene = id))
    if (isWatchLocal()) {
        sendSceneToWatch(id = sceneSelector.selectedSceneId)
    }
    val showMediaPlayerControls =
        findEnabledScene(id = id)?.videoSource?.cameraPosition == SettingsSceneCameraPosition.mediaPlayer
    if (showMediaPlayerControls != streamOverlay.showMediaPlayerControls.value) {
        streamOverlay.showMediaPlayerControls.value = showMediaPlayerControls
    }
}

private fun Model.sceneUpdatedOff() {
    media.unregisterAllEffects()
    createGlobalVideoEffects()
    for (effect in browserEffects.values) {
        effect.stop()
    }
}

private fun Model.sceneUpdatedOn(scene: SettingsScene, resolver: SceneResolver, attachCamera: Boolean) {
    val resolvedScene = resolver.resolve(scene, includeRemote = true)
    val effects = mutableListOf<VideoEffect>()
    if (database.color.lutEnabled && database.color.space.isAppleLog()) {
        effects.add(lutEffect)
    }
    for (lut in database.color.allLuts()) {
        if (!lut.enabled) {
            continue
        }
        val lutEffect = lutEffects[lut.id] ?: continue
        effects.add(lutEffect)
    }
    effects.addAll(registerGlobalVideoEffects(scene = scene))
    var needsSpeechToText = false
    enabledAlertsEffects.clear()
    enabledSnapshotEffects.clear()
    enabledChatEffects.clear()
    enabledChatEmoteComboEffects.clear()
    if (addSceneEffects(resolvedScene.drawnWidgets(), effects)) {
        needsSpeechToText = true
    }
    if (drawOnStream.lines.value.isNotEmpty()) {
        effects.add(drawOnStreamEffect)
    }
    effects.addAll(registerGlobalVideoEffectsOnTop())
    media.setPendingAfterAttachEffects(
        effects = effects,
        rotation = scene.videoSourceRotation,
        mirror = scene.mirror,
    )
    for (effect in browserEffects.values) {
        if (!effects.contains(effect)) {
            effect.setSceneWidget(sceneWidget = null, crops = emptyList())
        }
    }
    for (effect in mapEffects.values) {
        if (!effects.contains(effect)) {
            effect.setSceneWidget(sceneWidget = null)
        }
    }
    for (effect in chatEffects.values) {
        if (!effects.contains(effect)) {
            effect.stop()
        }
    }
    for ((id, scoreboardEffect) in scoreboardEffects) {
        if (!effects.contains(scoreboardEffect)) {
            if (isWatchLocal()) {
                sendRemoveScoreboardToWatch(id = id)
            }
        }
    }
    media.setSpeechToText(enabled = needsSpeechToText)
    if (attachCamera) {
        attachSingleLayout(scene = scene, resolver = resolver)
    } else {
        media.usePendingAfterAttachEffects()
        updateCameraPreviewWidgets(resolvedScene = resolvedScene)
    }
    if (drawOnStream.lines.value.isNotEmpty()) {
        drawOnStreamEffect.updateOverlay(
            videoSize = media.getCanvasSize().toCGSize(),
            size = drawOnStreamSize.toCGSize(),
            lines = drawOnStream.lines.value,
            mirror = streamOverlay.isFrontCameraSelected.value && !database.mirrorFrontCameraOnStream,
        )
    }
}

private fun Model.addSceneEffects(
    drawnWidgets: List<ResolvedSceneWidget>,
    effects: MutableList<VideoEffect>,
): Boolean {
    var needsSpeechToText = false
    for (drawnWidget in drawnWidgets) {
        val sceneWidget = drawnWidget.sceneWidget
        val widget = drawnWidget.widget
        when (widget.type) {
            SettingsWidgetType.image -> addSceneImageEffects(sceneWidget, widget, effects)
            SettingsWidgetType.text -> {
                if (addSceneTextEffects(sceneWidget, widget, effects)) {
                    needsSpeechToText = true
                }
            }
            SettingsWidgetType.browser -> {
                if (addSceneBrowserEffects(sceneWidget, widget, drawnWidgets, effects)) {
                    needsSpeechToText = true
                }
            }
            SettingsWidgetType.crop -> addSceneCropEffects(widget, drawnWidgets, effects)
            SettingsWidgetType.map -> addSceneMapEffects(sceneWidget, widget, effects)
            SettingsWidgetType.scene -> {}
            SettingsWidgetType.slideshow -> addSceneSlideshowEffects(sceneWidget, widget, effects)
            SettingsWidgetType.qrCode -> addSceneQrCodeEffects(sceneWidget, widget, effects)
            SettingsWidgetType.alerts -> {
                if (addSceneAlertsEffects(sceneWidget, widget, effects)) {
                    needsSpeechToText = true
                }
            }
            SettingsWidgetType.videoSource -> addSceneVideoSourceEffects(sceneWidget, widget, effects)
            SettingsWidgetType.scoreboard -> addSceneScoreboardEffects(sceneWidget, widget, effects)
            SettingsWidgetType.vTuber -> addSceneVTuberEffects(sceneWidget, widget, effects)
            SettingsWidgetType.pngTuber -> addScenePngTuberEffects(sceneWidget, widget, effects)
            SettingsWidgetType.snapshot -> addSceneSnapshotEffects(sceneWidget, widget, effects)
            SettingsWidgetType.chat -> addSceneChatEffects(sceneWidget, widget, effects)
            SettingsWidgetType.chatEmoteCombo -> addSceneChatEmoteComboEffects(sceneWidget, widget, effects)
            SettingsWidgetType.wheelOfLuck -> addSceneWheelOfLuckEffects(sceneWidget, widget, effects)
            SettingsWidgetType.bingoCard -> addSceneBingoCardEffects(sceneWidget, widget, effects)
            SettingsWidgetType.pomodoroTimer -> addScenePomodoroTimerEffects(sceneWidget, widget, effects)
            else -> {}
        }
    }
    return needsSpeechToText
}

private fun Model.addSceneImageEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = imageEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effects.add(effect)
}

private fun Model.addSceneTextEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
): Boolean {
    val effect = textEffects[widget.id] ?: return false
    if (effects.contains(effect)) {
        return false
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effects.add(effect)
    return widget.text.needsSubtitles
}

private fun Model.addSceneBrowserEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    drawnWidgets: List<ResolvedSceneWidget>,
    effects: MutableList<VideoEffect>,
): Boolean {
    val effect = browserEffects[widget.id] ?: return false
    if (effects.contains(effect)) {
        return false
    }
    effect.setSceneWidget(
        sceneWidget = sceneWidget.clone(),
        crops = findWidgetCrops(drawnWidgets, widget.id),
    )
    effects.add(effect)
    return widget.browser.moblinAccess && widget.browser.speechToText
}

private fun Model.addSceneCropEffects(
    widget: SettingsWidget,
    drawnWidgets: List<ResolvedSceneWidget>,
    effects: MutableList<VideoEffect>,
) {
    val sourceWidgetId = widget.crop.sourceWidgetId
    val effect = browserEffects[sourceWidgetId] ?: return
    if (effects.contains(effect)) {
        return
    }
    val sceneWidget = drawnWidgets.firstOrNull { it.widget.id == sourceWidgetId }?.sceneWidget
    effect.setSceneWidget(
        sceneWidget = sceneWidget?.clone(),
        crops = findWidgetCrops(drawnWidgets, sourceWidgetId),
    )
    effects.add(effect)
}

private fun Model.addSceneMapEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = mapEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effects.add(effect)
}

private fun Model.addSceneSlideshowEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = slideshowEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effects.add(effect)
}

private fun Model.addSceneQrCodeEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = qrCodeEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effects.add(effect)
}

private fun Model.addSceneAlertsEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
): Boolean {
    val effect = alertsEffects[widget.id] ?: return false
    if (effects.contains(effect)) {
        return false
    }
    effect.setLayout(sceneWidget.layout)
    enabledAlertsEffects.add(effect)
    effects.add(effect)
    return widget.alerts.needsSubtitles
}

private fun Model.addSceneVideoSourceEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = videoSourceEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    val videoSourceId = getVideoSourceId(cameraId = widget.videoSource.toCameraId())
    if (videoSourceId != null) {
        effect.setVideoSourceId(videoSourceId = videoSourceId)
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(settings = widget.videoSource.toEffectSettings())
    effects.add(effect)
}

private fun Model.addSceneScoreboardEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = getScoreboardEffect(id = widget.id) ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.update(
        scoreboard = widget.scoreboard,
        config = getModularScoreboardConfig(scoreboard = widget.scoreboard),
        players = database.scoreboardPlayers,
    )
    if (isWatchLocal()) {
        when (widget.scoreboard.sport) {
            SettingsWidgetScoreboardSport.padel -> sendUpdatePadelScoreboardToWatch(
                id = widget.id,
                padel = widget.scoreboard.padel,
            )
            SettingsWidgetScoreboardSport.generic -> sendUpdateGenericScoreboardToWatch(
                id = widget.id,
                generic = widget.scoreboard.generic,
            )
            else -> {}
        }
    }
    effects.add(effect)
}

private fun Model.addSceneVTuberEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = vTuberEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    val videoSourceId = getVideoSourceId(cameraId = widget.vTuber.toCameraId())
    if (videoSourceId != null) {
        effect.setVideoSourceId(videoSourceId = videoSourceId)
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(
        cameraFieldOfView = widget.vTuber.cameraFieldOfView,
        cameraPositionY = widget.vTuber.cameraPositionY,
        mirror = widget.vTuber.mirror,
        sensitivity = widget.vTuber.sensitivity,
        armsAngle = widget.vTuber.armsAngle,
    )
    effects.add(effect)
}

private fun Model.addScenePngTuberEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = pngTuberEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    val videoSourceId = getVideoSourceId(cameraId = widget.pngTuber.toCameraId())
    if (videoSourceId != null) {
        effect.setVideoSourceId(videoSourceId = videoSourceId)
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(mirror = widget.pngTuber.mirror, sensitivity = widget.pngTuber.sensitivity)
    effects.add(effect)
}

private fun Model.addSceneSnapshotEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = snapshotEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    enabledSnapshotEffects.add(effect)
    effects.add(effect)
}

private fun Model.addSceneChatEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = chatEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.start()
    enabledChatEffects.add(effect)
    effects.add(effect)
}

private fun Model.addSceneChatEmoteComboEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = chatEmoteComboEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(settings = widget.chatEmoteCombo)
    enabledChatEmoteComboEffects.add(effect)
    effects.add(effect)
}

private fun Model.addSceneWheelOfLuckEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = wheelOfLuckEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(settings = widget.wheelOfLuck)
    effects.add(effect)
}

private fun Model.addSceneBingoCardEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = bingoCardEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(settings = widget.bingoCard)
    effects.add(effect)
}

private fun Model.addScenePomodoroTimerEffects(
    sceneWidget: SettingsSceneWidget,
    widget: SettingsWidget,
    effects: MutableList<VideoEffect>,
) {
    val effect = pomodoroTimerEffects[widget.id] ?: return
    if (effects.contains(effect)) {
        return
    }
    effect.setSceneWidget(sceneWidget = sceneWidget.clone())
    effect.setSettings(settings = widget.pomodoroTimer)
    effects.add(effect)
}

private fun Model.updateRemoteSceneSettings() {
    if (remoteSceneSettingsUpdating) {
        return
    }
    remoteSceneSettingsUpdating = true
    remoteControlAssistantSetRemoteSceneSettings()
    CoroutineScope(Dispatchers.Main.immediate).launch {
        delay(1000)
        remoteSceneSettingsUpdating = false
        if (remoteSceneSettingsUpdateRequested) {
            remoteSceneSettingsUpdateRequested = false
            updateRemoteSceneSettings()
        }
    }
}

private fun Model.isRemoteSceneWidget(id: UUID): Boolean {
    return remoteSceneWidgets.any { it.id == id }
}

private fun Model.getLocalAndRemoteWidgets(): List<SettingsWidget> {
    return database.widgets + remoteSceneWidgets
}

private fun Model.findWidgetCrops(
    drawnWidgets: List<ResolvedSceneWidget>,
    sourceWidgetId: UUID,
): List<WidgetCrop> {
    return drawnWidgets
        .filter { it.widget.type == SettingsWidgetType.crop && it.widget.crop.sourceWidgetId == sourceWidgetId }
        .map { WidgetCrop(crop = it.widget.crop.clone(), sceneWidget = it.sceneWidget.clone()) }
}

fun Model.isCaptureDeviceWidget(widget: SettingsWidget): Boolean {
    return when (widget.type) {
        SettingsWidgetType.scene -> {
            val sceneId = widget.scene.sceneId
            if (sceneId == null) {
                return false
            }
            val scene = database.findScene(id = sceneId) ?: return false
            resolveScene(scene, includeDisabledSceneWidgets = true).widgets.any {
                it.widget.type != SettingsWidgetType.scene && isCaptureDeviceWidget(widget = it.widget)
            }
        }
        SettingsWidgetType.videoSource -> widget.videoSource.videoSource.isCaptureDevice()
        SettingsWidgetType.vTuber -> widget.vTuber.videoSource.isCaptureDevice()
        SettingsWidgetType.pngTuber -> widget.pngTuber.videoSource.isCaptureDevice()
        else -> false
    }
}

private fun Model.updateTextWidgetsLapTimes(now: Instant) {
    for (widget in database.widgets) {
        if (widget.type != SettingsWidgetType.text) {
            continue
        }
        if (widget.text.lapTimes.isEmpty()) {
            continue
        }
        val nowSeconds = now.toEpochMilli() / 1000.0
        for (lapTimes in widget.text.lapTimes) {
            val lastIndex = lapTimes.lapTimes.size - 1
            val currentLapStartTime = lapTimes.currentLapStartTime ?: continue
            if (lastIndex < 0) {
                continue
            }
            lapTimes.lapTimes = lapTimes.lapTimes.toMutableList().also {
                it[lastIndex] = nowSeconds - currentLapStartTime
            }
        }
        for (effect in getTextEffects(id = widget.id)) {
            effect.setLapTimes(lapTimes = widget.text.lapTimes.map { it.lapTimes })
        }
    }
}

fun Model.updateTextEffects(now: Instant, timestamp: ContinuousClock.Instant) {
    if (textEffects.isEmpty()) {
        return
    }
    updateTextWidgetsLapTimes(now = now)
    val variables = createVariables(now = now, timestamp = TimeSource.Monotonic.markNow())
    remoteControlAssistantSetRemoteSceneDataVariables(variables = variables)
    val remoteVariables = remoteSceneData.textStats?.toVariables()
    for ((widgetId, effect) in textEffects) {
        val effectiveVariables = if (isRemoteSceneWidget(id = widgetId)) remoteVariables else variables
        if (effectiveVariables != null) {
            effect.updateVariables(variables = effectiveVariables)
        }
    }
    for (effect in slideshowEffects.values) {
        for (slide in effect.slides) {
            val textEffect = slide.effect as? TextEffect ?: continue
            val effectiveVariables = if (isRemoteSceneWidget(id = slide.widgetId)) {
                remoteVariables
            } else {
                variables
            }
            if (effectiveVariables != null) {
                textEffect.updateVariables(variables = effectiveVariables)
            }
        }
    }
}

private fun Model.getBuiltinCameraDevicesInScene(
    scene: SettingsScene,
    devices: MutableList<CaptureDevice>,
    resolver: SceneResolver,
) {
    for (drawnWidget in resolver.resolve(scene).drawnWidgets()) {
        val widget = drawnWidget.widget
        when (widget.type) {
            SettingsWidgetType.videoSource -> getBuiltinCameraDevices(
                videoSource = widget.videoSource.videoSource,
                devices = devices,
            )
            SettingsWidgetType.vTuber -> getBuiltinCameraDevices(
                videoSource = widget.vTuber.videoSource,
                devices = devices,
            )
            SettingsWidgetType.pngTuber -> getBuiltinCameraDevices(
                videoSource = widget.pngTuber.videoSource,
                devices = devices,
            )
            else -> {}
        }
    }
}

private fun Model.getBuiltinCameraDevices(
    videoSource: SettingsVideoSource,
    devices: MutableList<CaptureDevice>,
) {
    val cameraId = videoSource.getCaptureDeviceCameraId() ?: return
    val device = AVCaptureDevice.withUniqueID(cameraId) ?: return
    if (devices.none { it.device == device }) {
        devices.add(makeCaptureDevice(device = device))
    }
}

private fun Model.updateTimers(
    text: SettingsWidgetText,
    textEffect: TextEffect,
    parts: List<TextFormatPart>,
) {
    val length = parts.count { it == TextFormatPart.Timer }
    text.timers = text.timers.toMutableList().also { it.truncate(length = length) { SettingsWidgetTextTimer() } }
    textEffect.setTimersEndTime(
        endTimes = text.timers.map {
            ContinuousClock.now.advanced(bySeconds = utcTimeDeltaFromNow(to = it.endTime))
        },
    )
}

private fun Model.updateStopwatches(
    text: SettingsWidgetText,
    textEffect: TextEffect,
    parts: List<TextFormatPart>,
) {
    val length = parts.count { it == TextFormatPart.Stopwatch }
    text.stopwatches = text.stopwatches.toMutableList()
        .also { it.truncate(length = length) { SettingsWidgetTextStopwatch() } }
    textEffect.setStopwatches(stopwatches = text.stopwatches.map { it.clone() })
}

private fun Model.updateCheckboxes(
    text: SettingsWidgetText,
    textEffect: TextEffect,
    parts: List<TextFormatPart>,
) {
    val length = parts.count { it == TextFormatPart.Checkbox }
    text.checkboxes = text.checkboxes.toMutableList()
        .also { it.truncate(length = length) { SettingsWidgetTextCheckbox() } }
    textEffect.setCheckboxes(checkboxes = text.checkboxes.map { it.checked })
}

private fun Model.updateRatings(
    text: SettingsWidgetText,
    textEffect: TextEffect,
    parts: List<TextFormatPart>,
) {
    val length = parts.count { it == TextFormatPart.Rating }
    text.ratings = text.ratings.toMutableList()
        .also { it.truncate(length = length) { SettingsWidgetTextRating() } }
    textEffect.setRatings(ratings = text.ratings.map { it.rating })
}

private fun Model.updateLapTimes(
    text: SettingsWidgetText,
    textEffect: TextEffect,
    parts: List<TextFormatPart>,
) {
    val length = parts.count { it == TextFormatPart.LapTimes }
    text.lapTimes = text.lapTimes.toMutableList()
        .also { it.truncate(length = length) { SettingsWidgetTextLapTimes() } }
    textEffect.setLapTimes(lapTimes = text.lapTimes.map { it.lapTimes })
}

private fun Model.updateSubtitles(
    text: SettingsWidgetText,
    textEffect: TextEffect?,
    parts: List<TextFormatPart>,
) {
    text.subtitles = emptyList()
    for (part in parts) {
        when (part) {
            is TextFormatPart.Subtitles -> {
                val item = SettingsWidgetTextSubtitles()
                item.identifier = UUID.randomUUID().toString()
                text.subtitles = text.subtitles + item
            }
            else -> {}
        }
    }
    text.needsSubtitles = text.subtitles.isNotEmpty()
    reloadSpeechToText()
}
