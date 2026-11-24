package com.moblin.android.view.settings.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.moblin.android.LocalModel
import com.moblin.android.common.various.RgbColor
import com.moblin.android.common.various.cleanUrl
import com.moblin.android.common.various.isValidHttpUrl
import com.moblin.android.localized
import com.moblin.android.platform.swiftui.ForEach
import com.moblin.android.platform.swiftui.Form
import com.moblin.android.platform.swiftui.NavigationLink
import com.moblin.android.platform.swiftui.Picker
import com.moblin.android.platform.swiftui.Section
import com.moblin.android.platform.swiftui.Toggle
import com.moblin.android.platform.swiftui.move
import com.moblin.android.platform.swiftui.remove
import com.moblin.android.various.model.Model
import com.moblin.android.various.network.DefaultTcpPorts
import com.moblin.android.various.settings.SettingsChat
import com.moblin.android.various.settings.SettingsChatBotAlias
import com.moblin.android.various.settings.SettingsChatBotCommandSnapshot
import com.moblin.android.various.settings.SettingsChatBotCommandSnapshotSource
import com.moblin.android.various.settings.SettingsChatBotCommandSnapshotWebRemoteControlMode
import com.moblin.android.various.settings.SettingsChatBotCustomCommand
import com.moblin.android.various.settings.SettingsChatBotPermissionsCommand
import com.moblin.android.various.settings.SettingsOpenAi
import com.moblin.android.view.settings.scenes.widgets.widget.text.TextFormatVariablesView
import com.moblin.android.view.settings.scenes.widgets.widget.text.TextFormatWarningsView
import com.moblin.android.view.settings.scenes.widgets.widget.text.TextWidgetSuggestionsView
import com.moblin.android.view.settings.scenes.widgets.widget.text.TextWidgetTextView
import com.moblin.android.view.settings.streams.stream.GrayTextView
import com.moblin.android.view.utils.ContextMenuDeleteButton
import com.moblin.android.view.utils.CreateButtonView
import com.moblin.android.view.utils.OpenAiSettingsView
import com.moblin.android.view.utils.TextEditNavigationView
import com.moblin.android.view.utils.TextEditView
import com.moblin.android.view.utils.TextItemLocalizedView
import com.moblin.android.view.utils.TradingCardView
import com.moblin.android.view.utils.tradingCardPulseOpacity
import com.moblin.android.various.model.chatBotCustomCommandsTextChanged

@Composable
private fun PermissionsSettingsInnerView(permissions: SettingsChatBotPermissionsCommand) {
    Section(header = "Permissions") {
        Toggle("Moderators", isOn = permissions.moderatorsEnabled) {
            permissions.moderatorsEnabled = it
        }
        Toggle("Subscribers", isOn = permissions.subscribersEnabled) {
            permissions.subscribersEnabled = it
        }
        Picker(
            title = "Minimum subscriber tier",
            selection = permissions.minimumSubscriberTier,
            options = listOf(3, 2, 1),
        ) {
            permissions.minimumSubscriberTier = it
        }
        Toggle("Others", isOn = permissions.othersEnabled) {
            permissions.othersEnabled = it
        }
    }
    Section(footer = "Does not apply to you and your moderators.") {
        Picker(
            title = "Cooldown",
            selection = permissions.cooldown,
            options = listOf<Int?>(null, 1, 2, 3, 5, 10, 15, 30, 60),
            text = { if (it == null) localized("-- None --") else "${it}s" },
        ) {
            permissions.cooldown = it
        }
    }
    Section(
        footer = "Typically sends a chat message if the user is not allowed to execute the command. Some commands responds on success as well.",
    ) {
        Toggle("Send chat responses", isOn = permissions.sendChatMessages) {
            permissions.sendChatMessages = it
        }
    }
}

@Composable
private fun PermissionsSettingsView(
    title: String,
    permissions: SettingsChatBotPermissionsCommand,
) {
    NavigationLink(destination = {
        Form(title = title) {
            PermissionsSettingsInnerView(permissions = permissions)
        }
    }) {
        Text(title)
    }
}

@Composable
private fun FixPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Fix OBS input.") {
        PermissionsSettingsView(title = "!moblin obs fix", permissions = permissions)
    }
}

@Composable
private fun GimbalPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Move to given gimbal preset.") {
        PermissionsSettingsView(
            title = localized("!moblin gimbal preset <name>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun AlertPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Trigger alerts. Configure alert names in alert widgets.") {
        PermissionsSettingsView(
            title = localized("!moblin alert <name>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun FaxPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Fax the streamer images.") {
        PermissionsSettingsView(
            title = localized("!moblin fax <url>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun SnapshotPermissionsSettingsView(
    permissions: SettingsChatBotPermissionsCommand,
    snapshot: SettingsChatBotCommandSnapshot,
) {
    Section(footer = "Take snapshot.") {
        NavigationLink(destination = {
            Form(title = "!moblin snapshot <optional message>") {
                Section(
                    footer = "Take the snapshot on this device, or fetch it from another device's web remote control.",
                ) {
                    Picker(
                        title = "Source",
                        selection = snapshot.source,
                        options = SettingsChatBotCommandSnapshotSource.entries,
                    ) {
                        snapshot.source = it
                    }
                    if (snapshot.source == SettingsChatBotCommandSnapshotSource.webRemoteControl) {
                        Picker(
                            title = "Use remote source",
                            selection = snapshot.webRemoteControlMode,
                            options = SettingsChatBotCommandSnapshotWebRemoteControlMode.entries,
                        ) {
                            snapshot.webRemoteControlMode = it
                        }
                        TextEditNavigationView(
                            title = localized("URL"),
                            value = snapshot.webRemoteControlUrl,
                            onChange = { isValidHttpUrl(it) },
                            onSubmit = { snapshot.webRemoteControlUrl = cleanUrl(value = it) },
                            placeholder = "http://192.168.1.10:${DefaultTcpPorts.remoteControlWeb}",
                        )
                    }
                }
                PermissionsSettingsInnerView(permissions = permissions)
            }
        }) {
            Text("!moblin snapshot <optional message>")
        }
    }
}

@Composable
private fun ReactionPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(localized("Perform reaction."))
            Text("")
            Text(
                localized(
                    "<reaction> is hearts, fireworks, balloons, confetti, lasers, glasses, sparkle, pope, pizzachef or poop.",
                ),
            )
        }
    }) {
        PermissionsSettingsView(
            title = localized("!moblin reaction <reaction>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun FilterPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(localized("Turn a filter on or off."))
            Text("")
            Text(localized("<filter> is movie, grayscale, sepia, triple, pixellate or 4:3."))
            Text("")
            Text(localized("<on/off> is on or off."))
        }
    }) {
        PermissionsSettingsView(
            title = localized("!moblin filter <filter> <on/off>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun ZoomPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Set zoom for the current camera.") {
        PermissionsSettingsView(title = "!moblin zoom <x>", permissions = permissions)
    }
}

@Composable
private fun ScenePermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Switch to given scene.") {
        PermissionsSettingsView(
            title = localized("!moblin scene <name>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun StreamPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin stream start")
            Text(localized("Start the stream."))
            Text("")
            Text("!moblin stream stop")
            Text(localized("Stop the stream."))
            Text("")
            Text("!moblin stream title <title>")
            Text(localized("Set stream title."))
            Text("")
            Text("!moblin stream category <category name>")
            Text(localized("Set stream category."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin stream ...", permissions = permissions)
    }
}

@Composable
private fun WidgetPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin widget <name> enable")
            Text(localized("Enable given widget."))
            Text("")
            Text("!moblin widget <name> disable")
            Text(localized("Disable given widget."))
            Text("")
            Text("!moblin widget <name> wheelofluck options <option 1> <option 2> ...")
            Text(localized("Set options for given wheel of luck."))
            Text("")
            Text("!moblin widget <name> wheelofluck spin")
            Text(localized("Spin given wheel of luck."))
            Text("")
            Text("!moblin widget <name> timer <number> add <seconds>")
            Text(localized("Change timer value."))
        }
    }) {
        PermissionsSettingsView(
            title = localized("!moblin widget ..."),
            permissions = permissions,
        )
    }
}

@Composable
private fun LocationPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin location data reset")
            Text(localized("Resets distances, average speed and slope."))
            Text("")
            Text("!moblin location data split")
            Text(localized("Reset split distance."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin location ...", permissions = permissions)
    }
}

@Composable
private fun MapPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footer = "Zoom out map widget temporarily.") {
        PermissionsSettingsView(title = "!moblin map zoom out", permissions = permissions)
    }
}

@Composable
private fun TtsSayPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin tts on")
            Text(localized("Turn on chat text to speech."))
            Text("")
            Text("!moblin tts off")
            Text(localized("Turn off chat text to speech."))
            Text("")
            Text("!moblin say <message>")
            Text(localized("Say given message."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin tts/say ...", permissions = permissions)
    }
}

@Composable
private fun AiPermissionsSettingsView(
    permissions: SettingsChatBotPermissionsCommand,
    ai: SettingsOpenAi,
) {
    Section(footer = "Ask an AI a question.") {
        NavigationLink(destination = {
            Form(title = "!moblin ai ask <question>") {
                OpenAiSettingsView(ai = ai)
                PermissionsSettingsInnerView(permissions = permissions)
            }
        }) {
            Text("!moblin ai ask <question>")
        }
    }
}

@Composable
private fun TwitchPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin twitch raid <channel>")
            Text(localized("Raid given channel."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin twitch ...", permissions = permissions)
    }
}

@Composable
private fun MuteUnmutePermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin mute")
            Text(localized("Mute audio."))
            Text("")
            Text("!moblin unmute")
            Text(localized("Unmute audio."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin mute/unmute", permissions = permissions)
    }
}

@Composable
private fun TorchPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin torch on")
            Text(localized("Turn the torch on."))
            Text("")
            Text("!moblin torch off")
            Text(localized("Turn the torch off."))
            Text("")
            Text("!moblin torch level <level>")
            Text(localized("Set torch level, from 1 to 100."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin torch ...", permissions = permissions)
    }
}

@Composable
private fun VibratePermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin vibrate on [<seconds>] [<intensity>] [<sharpness>]")
            Text(localized("Vibrate the phone. Until turned off if no seconds is given."))
            Text(localized("Intensity and sharpness from 1 to 100, default 100 and 50."))
            Text("")
            Text("!moblin vibrate off")
            Text(localized("Stop vibrating the phone."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin vibrate ...", permissions = permissions)
    }
}

@Composable
private fun TeslaPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin tesla trunk open")
            Text(localized("Open the trunk."))
            Text("")
            Text("!moblin tesla trunk close")
            Text(localized("Close the trunk."))
            Text("")
            Text("!moblin tesla media next")
            Text(localized("Next track."))
            Text("")
            Text("!moblin tesla media previous")
            Text(localized("Previous track."))
            Text("")
            Text("!moblin tesla media toggle-playback")
            Text(localized("Toggle playback."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin tesla ...", permissions = permissions)
    }
}

@Composable
private fun MacroPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin macro run <name>")
            Text(localized("Run given macro."))
            Text("")
            Text("!moblin macro cancel <name>")
            Text(localized("Cancel given macro."))
        }
    }) {
        PermissionsSettingsView(
            title = localized("!moblin macro <run/cancel> <name>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun SendPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin send <message>")
            Text(localized("Send given message."))
        }
    }) {
        PermissionsSettingsView(
            title = localized("!moblin send <message>"),
            permissions = permissions,
        )
    }
}

@Composable
private fun AppleMusicPermissionsSettingsView(permissions: SettingsChatBotPermissionsCommand) {
    Section(footerContent = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("!moblin music add <song>")
            Text(localized("Add given song to the queue."))
            Text(localized("<song> is either a share link or text search."))
            Text("")
            Text("!moblin music play")
            Text(localized("Play."))
            Text("")
            Text("!moblin music pause")
            Text(localized("Pause."))
            Text("")
            Text("!moblin music next")
            Text(localized("Next song."))
            Text("")
            Text("!moblin music previous")
            Text(localized("Previous song."))
            Text("")
            Text("!moblin music status")
            Text(localized("Show status."))
            Text("")
            Text("!moblin music clear")
            Text(localized("Clear the queue."))
        }
    }) {
        PermissionsSettingsView(title = "!moblin music ...", permissions = permissions)
    }
}

@Composable
private fun ChatBotCommandsSettingsView(model: Model = LocalModel.current) {
    val permissions = model.database.chat.botCommandPermissions
    Form(title = "Commands") {
        AiPermissionsSettingsView(
            permissions = permissions.ai,
            ai = model.database.chat.botCommandAi,
        )
        AlertPermissionsSettingsView(permissions = permissions.alert)
        FaxPermissionsSettingsView(permissions = permissions.fax)
        FilterPermissionsSettingsView(permissions = permissions.filter)
        FixPermissionsSettingsView(permissions = permissions.fix)
        GimbalPermissionsSettingsView(permissions = permissions.gimbal)
        LocationPermissionsSettingsView(permissions = permissions.location)
        MacroPermissionsSettingsView(permissions = permissions.macro)
        MapPermissionsSettingsView(permissions = permissions.map)
        MuteUnmutePermissionsSettingsView(permissions = permissions.audio)
        ReactionPermissionsSettingsView(permissions = permissions.reaction)
        ScenePermissionsSettingsView(permissions = permissions.scene)
        SnapshotPermissionsSettingsView(
            permissions = permissions.snapshot,
            snapshot = model.database.chat.botCommandSnapshot,
        )
        StreamPermissionsSettingsView(permissions = permissions.stream)
        TeslaPermissionsSettingsView(permissions = permissions.tesla)
        TorchPermissionsSettingsView(permissions = permissions.torch)
        VibratePermissionsSettingsView(permissions = permissions.vibrate)
        SendPermissionsSettingsView(permissions = permissions.send)
        AppleMusicPermissionsSettingsView(permissions = permissions.music)
        TtsSayPermissionsSettingsView(permissions = permissions.tts)
        WidgetPermissionsSettingsView(permissions = permissions.widget)
        TwitchPermissionsSettingsView(permissions = permissions.twitch)
        ZoomPermissionsSettingsView(permissions = permissions.zoom)
    }
}

@Composable
private fun ChatBotAliasSettingsView(alias: SettingsChatBotAlias) {
    fun onAliasChange(value: String): String? {
        if (value.isEmpty()) {
            return localized("The alias must not be empty.")
        }
        if (!value.startsWith("!")) {
            return localized("The alias must start with !.")
        }
        if (value.length <= 1) {
            return localized("The alias is too short.")
        }
        if (value.startsWith("!moblin")) {
            return localized("The alias must not start with !moblin.")
        }
        if (value.split(" ").filter { it.isNotEmpty() }.size != 1) {
            return localized("The alias must be exactly one word.")
        }
        return null
    }

    fun onReplacementChange(value: String): String? {
        if (value.isEmpty()) {
            return localized("The replacement must not be empty.")
        }
        if (!value.startsWith("!moblin")) {
            return localized("The replacement must start with !moblin.")
        }
        if (value.split(" ").filter { it.isNotEmpty() }.size <= 1) {
            return localized("The replacement must be more than one word.")
        }
        return null
    }

    NavigationLink(destination = {
        Form(title = "Alias") {
            Section {
                NavigationLink(destination = {
                    TextEditView(
                        title = localized("Alias"),
                        value = alias.alias,
                        onChange = { onAliasChange(it) },
                        onSubmit = { alias.alias = it },
                    )
                }) {
                    TextItemLocalizedView(name = "Alias", value = alias.alias)
                }
                NavigationLink(destination = {
                    TextEditView(
                        title = localized("Replacement"),
                        value = alias.replacement,
                        onChange = { onReplacementChange(it) },
                        onSubmit = { alias.replacement = it },
                    )
                }) {
                    TextItemLocalizedView(name = "Replacement", value = alias.replacement)
                }
            }
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(alias.alias)
            Spacer(Modifier.weight(1f))
            GrayTextView(text = alias.replacement)
        }
    }
}

@Composable
private fun ChatBotCustomCommandTextSettingsView(
    model: Model = LocalModel.current,
    customCommand: SettingsChatBotCustomCommand,
    value: String,
) {
    var valueState by remember { mutableStateOf(value) }
    fun onValueChange(newValue: String) {
        valueState = newValue
        customCommand.formatString = newValue
        model.chatBotCustomCommandsTextChanged()
    }

    Form(title = "Text") {
        TextWidgetTextView(value = valueState, onChange = { onValueChange(it) })
        TextFormatWarningsView(
            model = model,
            location = model.database.location,
            value = valueState,
            onChange = { onValueChange(it) },
        )
        Section {
            TextWidgetSuggestionsView(
                widget = false,
                text = valueState,
                onChange = { onValueChange(it) },
            )
        }
        TextFormatVariablesView(
            model = model,
            widget = false,
            value = valueState,
            onChange = { onValueChange(it) },
        )
    }
}

@Composable
private fun ChatBotCustomCommandSettingsView(customCommand: SettingsChatBotCustomCommand) {
    fun onNameChange(value: String): String? {
        if (value.isEmpty()) {
            return localized("The name must not be empty.")
        }
        return null
    }

    NavigationLink(destination = {
        Form(title = "Command") {
            Section(footer = "Send the text to chat when a user sends !moblin custom <name>.") {
                NavigationLink(destination = {
                    TextEditView(
                        title = localized("Name"),
                        value = customCommand.name,
                        onChange = { onNameChange(it) },
                        onSubmit = { customCommand.name = it },
                    )
                }) {
                    TextItemLocalizedView(name = "Name", value = customCommand.name)
                }
                NavigationLink(destination = {
                    ChatBotCustomCommandTextSettingsView(
                        customCommand = customCommand,
                        value = customCommand.formatString,
                    )
                }) {
                    TextItemLocalizedView(name = "Text", value = customCommand.formatString)
                }
            }
            PermissionsSettingsInnerView(permissions = customCommand.permissions)
        }
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(customCommand.name)
            Spacer(Modifier.weight(1f))
            GrayTextView(text = customCommand.formatString)
        }
    }
}

@Composable
private fun ChatBotCustomCommandsSettingsView(
    model: Model = LocalModel.current,
    chat: SettingsChat,
) {
    Form(title = "Custom commands") {
        Section(footerContent = {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("!moblin custom <name>")
                Text(localized("Send the text of the command with given name to chat."))
            }
        }) {
            ForEach(
                items = chat.customCommands,
                id = { it.id },
                onDelete = { offsets ->
                    chat.customCommands.remove(atOffsets = offsets)
                    model.chatBotCustomCommandsTextChanged()
                },
                onMove = { froms, to ->
                    chat.customCommands.move(fromOffsets = froms, toOffset = to)
                },
            ) { customCommand ->
                ContextMenuDeleteButton(
                    disabled = false,
                    action = {
                        chat.customCommands.removeAll { it.id == customCommand.id }
                        model.chatBotCustomCommandsTextChanged()
                    },
                ) {
                    ChatBotCustomCommandSettingsView(customCommand = customCommand)
                }
            }
            CreateButtonView {
                chat.customCommands.add(SettingsChatBotCustomCommand())
            }
        }
    }
}

@Composable
private fun ChatBotAliasesSettingsView(chat: SettingsChat) {
    Form(title = "Aliases") {
        Section {
            ForEach(
                items = chat.aliases,
                id = { it.id },
                onDelete = { offsets ->
                    chat.aliases.remove(atOffsets = offsets)
                },
                onMove = { froms, to ->
                    chat.aliases.move(fromOffsets = froms, toOffset = to)
                },
            ) { alias ->
                ContextMenuDeleteButton(
                    disabled = false,
                    action = {
                        chat.aliases.removeAll { it.id == alias.id }
                    },
                ) {
                    ChatBotAliasSettingsView(alias = alias)
                }
            }
            CreateButtonView {
                chat.aliases.add(SettingsChatBotAlias())
            }
        }
    }
}

private fun drawChatBotCard(context: DrawScope, time: Double) {
    val factor = context.size.width / 260.0f
    val color = Color(red = 0xFF, green = 0x3B, blue = 0x30)
        .copy(alpha = tradingCardPulseOpacity(time).toFloat())
    context.drawOval(
        color = color,
        topLeft = Offset((65.52f - 4.08f) * factor, (39.68f - 4.08f) * factor),
        size = Size(8.16f * factor, 8.16f * factor),
    )
}

@Composable
fun ChatBotSettingsView(model: Model = LocalModel.current) {
    Form(title = "Bot") {
        Section {
            TradingCardView(
                name = "ChatBot",
                background = Color(red = 0xEA, green = 0xF4, blue = 0xEA),
                hasFront = false,
                draw = ::drawChatBotCard,
            )
        }
        Section {
            NavigationLink(destination = {
                ChatBotCommandsSettingsView(model = model)
            }) {
                Text(localized("Commands"))
            }
            NavigationLink(destination = {
                ChatBotCustomCommandsSettingsView(model = model, chat = model.database.chat)
            }) {
                Text(localized("Custom commands"))
            }
            NavigationLink(destination = {
                ChatBotAliasesSettingsView(chat = model.database.chat)
            }) {
                Text(localized("Aliases"))
            }
        }
        Section {
            Toggle(
                isOn = model.database.chat.botSendLowBatteryWarning,
                onChange = { model.database.chat.botSendLowBatteryWarning = it },
            ) {
                Text(localized("Send low battery message"))
            }
        }
    }
}
