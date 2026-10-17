package com.gangchat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FocusNode
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.showDialog
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val out = "build/outputs/roborazzi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", shadows = [ShadowNoOpMagnifier::class])
class UiGalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Section(title: String, content: @Composable () -> Unit) {
        Column {
            Text(title, style = UiTypography.overline)
            Spacer(Modifier.height(6.dp))
            content()
            Spacer(Modifier.height(14.dp))
        }
    }

    @Test
    fun buttons() {
        compose.setUiContent { _ ->
            Column(Modifier.padding(16.dp)) {
                Section("TONES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onPressed = {}) { Text("普通") }
                        Button(onPressed = {}, tone = ButtonTone.primary, icon = { Icon(Icons.Rounded.Send) }) { Text("发送") }
                        Button(onPressed = {}, tone = ButtonTone.danger, icon = { Icon(Icons.Rounded.Delete) }) { Text("删除") }
                    }
                }
                Section("STATES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onPressed = null) { Text("禁用") }
                        Button(onPressed = {}, selected = true) { Text("已选") }
                        Button(onPressed = {}, loading = true, icon = { Icon(Icons.Outlined.Download) }) { Text("加载") }
                        Button(toggleValue = true, onToggleChanged = {}) { Text("开") }
                    }
                }
                Section("SIZES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onPressed = {}, height = 32.dp, padding = EdgeInsets.symmetric(horizontal = 12.dp)) { Text("紧凑") }
                        Button(onPressed = {}, width = 140.dp, tone = ButtonTone.primary) { Text("固定宽度") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onPressed = {}, width = androidx.compose.ui.unit.Dp.Infinity) { Text("撑满宽度的按钮") }
                }
                Section("ICON BUTTONS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ButtonIcon(icon = { Icon(Icons.Rounded.Mic) }, onPressed = {})
                        ButtonIcon(icon = { Icon(Icons.Rounded.Mic) }, onPressed = {}, tone = ButtonTone.primary)
                        ButtonIcon(icon = { Icon(Icons.Rounded.Delete) }, onPressed = {}, tone = ButtonTone.danger)
                        ButtonIcon(icon = { Icon(Icons.Rounded.VolumeUp) }, onPressed = {}, ghost = true)
                        ButtonIcon(icon = { Icon(Icons.Rounded.VolumeUp) }, onPressed = {}, ghost = true, selected = true)
                        ButtonIcon(icon = { Icon(Icons.Rounded.Settings) }, onPressed = null)
                        ButtonIcon(icon = { Icon(Icons.Rounded.Settings) }, onPressed = {}, loading = true, size = 32.dp)
                        ButtonIcon(icon = { Icon(Icons.Rounded.Tune) }, onPressed = {}, width = 56.dp, radius = UiRadii.md, size = 36.dp)
                        ButtonIconPlain(icon = { Icon(Icons.Rounded.Info) }, onPressed = {})
                    }
                }
                Section("CATEGORIES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactCategoryButton(label = "全部", count = 16, selected = true, onPressed = {})
                        CompactCategoryButton(label = "消息", count = 3, selected = false, onPressed = {})
                        CompactCategoryButton(label = "成员", selected = false, onPressed = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("$out/ui_buttons.png")
    }

    @Test
    fun inputs() {
        val filled = TextEditingController("hello world")
        val search = TextEditingController("绝不认输")
        val focusNode = FocusNode()
        compose.setUiContent { _ ->
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Section("INPUT EMPTY") { Input(hintText = "输入消息") }
                Section("INPUT FILLED") { Input(controller = filled) }
                Section("INPUT FOCUSED") { Input(hintText = "聚焦", focusNode = focusNode) }
                Section("SEARCH + CLEAR") { Input(controller = search, hintText = "搜索成员", prefixIcon = Icons.Filled.Search, showClearButton = true) }
                Section("COMPACT 30") {
                    Input(controller = TextEditingController("132"), hintText = "搜索歌曲点歌", prefixIcon = Icons.Filled.Search, showClearButton = true, height = 30.dp)
                }
                Section("DISABLED") { Input(hintText = "不可用", enabled = false) }
                Section("TEXT INPUT") {
                    TextInput(label = "用户名", hint = "请输入用户名", prefixIcon = Icons.Rounded.Person)
                }
                Section("READ ONLY") { ReadOnlyTextBox(value = "https://example.com/invite/abc") }
            }
        }
        focusNode.requestFocus()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("$out/ui_inputs.png")
    }

    @Test
    fun controls() {
        compose.setUiContent { _ ->
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Section("SWITCH") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UiSwitch(value = true, onChanged = {})
                        UiSwitch(value = false, onChanged = {})
                        UiSwitch(value = true, onChanged = null)
                        UiSwitch(value = false, onChanged = null)
                    }
                }
                Section("CHECKBOX") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UiCheckbox(value = true, onChanged = {})
                        UiCheckbox(value = false, onChanged = {})
                        UiCheckbox(value = true, onChanged = null)
                        UiCheckbox(value = false, onChanged = null)
                    }
                }
                Section("SLIDER") {
                    UiSlider(value = 0.35, onChanged = {})
                    Spacer(Modifier.height(12.dp))
                    UiSlider(value = 0.7, onChanged = null)
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Box(Modifier.height(120.dp)) { UiSlider(value = 0.6, onChanged = {}, axis = com.gangchat.client.widgets.Axis.vertical) }
                    }
                }
                Section("SEGMENTED") {
                    var value = "info"
                    SegmentedControl(
                        segments =
                            listOf(
                                Segment("info", "房间信息", Icons.Rounded.Info),
                                Segment("members", "成员", showBadge = true),
                                Segment("history", "消息记录"),
                            ),
                        value = value,
                        onChanged = { value = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        segments = listOf(Segment(0, "全部 16"), Segment(1, "语音 0"), Segment(2, "在线 2"), Segment(3, "离线 14")),
                        value = 2,
                        onChanged = {},
                        expanded = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(220.dp)) {
                        SegmentedControl(
                            segments =
                                listOf(
                                    Segment("info", "房间信息", Icons.Rounded.Info),
                                    Segment("profile", "个性化设置", Icons.Rounded.Tune),
                                    Segment("messages", "消息记录", Icons.Rounded.History),
                                    Segment("stickers", "表情包管理", Icons.Rounded.EmojiEmotions),
                                ),
                            value = "info",
                            onChanged = {},
                            expanded = true,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("$out/ui_controls.png")
    }

    @Test
    fun signals() {
        compose.setUiContent { _ ->
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Section("SIGNAL TAGS") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignalTag(label = "LIVE 3", color = UiColors.accent, dot = true)
                        SignalTag(label = "LIVE", color = UiColors.accent, filled = true)
                        SignalTag(label = "MUTED", icon = Icons.Rounded.Mic)
                        SignalTag(label = "WARN", color = UiColors.amber)
                    }
                }
                Section("EQUALIZER / COUNTER / BRAND") {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        EqualizerBars()
                        DotCounter(value = 7, label = "ONLINE")
                        BrandMark(size = 40.dp)
                        BrandMark(size = 40.dp, tile = false)
                    }
                }
                Section("LATENCY") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LatencySignalBadge(activeBars = 3, activeColor = UiColors.presenceOnline, tooltip = "36 ms")
                        LatencySignalBadge(activeBars = 2, activeColor = UiColors.presenceReconnecting, tooltip = "228 ms")
                        LatencySignalBadge(activeBars = 1, activeColor = UiColors.danger, tooltip = "900 ms")
                        LatencySignalBadge(activeBars = 0, activeColor = UiColors.danger, tooltip = "--", size = 24.dp)
                    }
                }
                Section("BADGES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BadgeDot()
                        BadgeDot(size = 12.dp, color = UiColors.accent)
                        val male = genderMark("male")!!
                        val female = genderMark("f")!!
                        Text(male.symbol, style = uiTextStyle(color = male.color, fontSize = 16.0))
                        Text(female.symbol, style = uiTextStyle(color = female.color, fontSize = 16.0))
                        Icon(fileIconForMime("application/pdf"), color = UiColors.textSecondary)
                        Icon(fileIconForMime("image/png"), color = UiColors.textSecondary)
                    }
                }
                Section("ROLE BADGES") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RoleBadge(label = "成员")
                        RoleBadge(label = "管理员")
                        RoleBadge(label = "创建者")
                        RoleBadge(label = "超级用户")
                        RoleBadge(label = "访客", query = "访")
                    }
                }
                Section("HIGHLIGHT") {
                    HighlightedText(text = "Alpha beta alpha", query = "ALPHA", style = UiTypography.body)
                }
                Section("TRACK SURFACE") {
                    MusicPlaylistTrackSurface(highlighted = true) { Text("张顺飞 - 绝不认输", style = UiTypography.body) }
                    Spacer(Modifier.height(8.dp))
                    MusicPlaylistTrackSurface { Text("侯顺玉 - MV正式发布", style = UiTypography.body) }
                }
                Section("DOT GRID") {
                    Box(Modifier.fillMaxWidth().height(60.dp)) { DotGridBackground() }
                }
            }
        }
        compose.onRoot().captureRoboImage("$out/ui_signals.png")
    }

    @Test
    fun feedbackAndDialog() {
        compose.setUiContent(notifications = true) { context ->
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Toast(message = "已保存", tone = FloatingNoticeTone.success, icon = Icons.Rounded.Info)
                Spacer(Modifier.height(8.dp))
                Toast(message = "网络连接失败，请稍后再试", tone = FloatingNoticeTone.error, icon = Icons.Rounded.Info, onClose = {})
                Spacer(Modifier.height(8.dp))
                Toast(message = "普通提示", onClose = {})
                Spacer(Modifier.height(8.dp))
                Button(
                    onPressed = {
                        showFloatingNotice(context, "builder notice", duration = null)
                        unawaited {
                            showDialog<Unit>(context) { _ ->
                                DialogFrame(
                                    title = "下载新版本",
                                    icon = Icons.Outlined.Download,
                                    maxWidth = 360.dp,
                                    adaptiveActions =
                                        listOf(
                                            ResponsiveDialogAction(label = "取消", onPressed = {}),
                                            ResponsiveDialogAction(label = "下载新版本", icon = Icons.Outlined.Download, tone = ButtonTone.primary, onPressed = {}),
                                        ),
                                    child = { Text("发现新版本 1.4.0，是否立即下载？", style = UiTypography.body) },
                                )
                            }
                        }
                    },
                ) { Text("open") }
            }
        }
        compose.onNodeWithText("open").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("$out/ui_feedback_dialog.png")
    }

    @Test
    fun contextMenu() {
        compose.setUiContent { context ->
            Box(Modifier.fillMaxSize()) {
                Button(
                    onPressed = {
                        unawaited {
                            showUiContextMenu(
                                context,
                                position = DpOffset(40.dp, 120.dp),
                                sections =
                                    listOf(
                                        UiContextMenuSection(
                                            listOf(
                                                UiContextMenuItem(label = "回复", icon = Icons.Rounded.Send, onPressed = {}),
                                                UiContextMenuItem(label = "复制", shortcut = "Ctrl+C", onPressed = {}),
                                                UiContextMenuItem(label = "已选中", selected = true, icon = Icons.Rounded.Info, onPressed = {}),
                                                UiContextMenuItem(label = "不可用", onPressed = null),
                                            ),
                                        ),
                                        UiContextMenuSection(listOf(UiContextMenuItem(label = "删除消息", danger = true, icon = Icons.Rounded.Delete, onPressed = {}))),
                                    ),
                            )
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                ) { Text("menu") }
            }
        }
        compose.onNodeWithText("menu").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("$out/ui_context_menu.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun textContextMenu() {
        val controller = TextEditingController("hello world")
        compose.setUiContent { _ ->
            Column(Modifier.padding(16.dp).padding(top = 120.dp).fillMaxWidth()) {
                Input(controller = controller)
            }
        }
        compose.onNodeWithText("hello world").performTouchInput { doubleClick(androidx.compose.ui.geometry.Offset(60f, centerY)) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        check(!controller.selection.collapsed) { "double tap should select a word" }
        captureScreenRoboImage("$out/ui_text_context_menu.png")
    }

    @Test
    fun chatComposer() {
        compose.setUiContent { _ ->
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Bottom) {
                ChatComposer(
                    hintText = "输入消息",
                    actions =
                        listOf(
                            ComposerAction(
                                id = "emoji",
                                icon = Icons.Rounded.EmojiEmotions,
                                label = "表情",
                                panel =
                                    ComposerPanel.list(
                                        itemCount = 12,
                                        itemBuilder = { index ->
                                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text("😀$index") }
                                        },
                                        height = 160.dp,
                                    ),
                            ),
                            ComposerAction(id = "file", icon = Icons.Rounded.AttachFile, label = "文件", selected = true),
                            ComposerAction(id = "send", icon = Icons.Rounded.Send, label = "发送", tone = ButtonTone.primary, alignment = ComposerActionAlignment.trailing),
                        ),
                )
            }
        }
        compose.onRoot().captureRoboImage("$out/ui_chat_composer.png")
        compose.onNode(hasTooltip("表情")).performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("$out/ui_chat_composer_panel.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsScaffoldNarrow() {
        settingsScaffold("ui_settings_narrow")
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-mdpi")
    fun settingsScaffoldWide() {
        settingsScaffold("ui_settings_wide")
    }

    @Test
    @Config(qualifiers = "w1024dp-h768dp-mdpi")
    fun settingsTilesWide() {
        settingsTiles("ui_settings_tiles_wide")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun settingsTilesNarrow() {
        settingsTiles("ui_settings_tiles_narrow")
    }

    private val sections =
        listOf(
            Segment("account", "账号", Icons.Rounded.Person),
            Segment("notify", "通知", Icons.Rounded.Notifications, showBadge = true),
            Segment("audio", "语音和视频", Icons.Rounded.GraphicEq),
            Segment("privacy", "隐私和安全", Icons.Rounded.Shield),
            Segment("profile", "用户资料", Icons.Rounded.Badge),
        )

    private fun settingsScaffold(name: String) {
        compose.setUiContent { _ ->
            SettingsScaffold(
                icon = Icons.Rounded.Settings,
                title = "设置",
                onBack = {},
                navigation = SettingsNavigation(segments = sections, value = "notify", onChanged = {}),
                headerAction = { ButtonIcon(icon = { Icon(Icons.Rounded.Info) }, onPressed = {}, ghost = true, size = 36.dp) },
                body = { settingsBody() },
            )
        }
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    private fun settingsTiles(name: String) {
        compose.setUiContent { _ ->
            SettingsScaffold(
                icon = Icons.Rounded.Settings,
                title = "应用设置",
                onBack = {},
                navigation =
                    SettingsTileNavigation(
                        segments = sections,
                        value = "audio",
                        onChanged = {},
                        summaries = mapOf("account" to "alice", "notify" to "全部开启", "audio" to "默认设备 · 48 kHz"),
                    ),
                search = { Input(hintText = "搜索设置", prefixIcon = Icons.Filled.Search, height = 36.dp) },
                body = { settingsBody() },
            )
        }
        compose.onRoot().captureRoboImage("$out/$name.png")
    }

    @Composable
    private fun settingsBody() {
        SettingsList(
            children =
                listOf(
                    {
                        SettingsCard(
                            title = "消息通知",
                            children =
                                listOf(
                                    {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("桌面通知", modifier = Modifier.weight(1f), style = UiTypography.body)
                                            UiSwitch(value = true, onChanged = {})
                                        }
                                    },
                                    {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("提示音", modifier = Modifier.weight(1f), style = UiTypography.body)
                                            UiSwitch(value = false, onChanged = {})
                                        }
                                    },
                                ),
                        )
                    },
                    {
                        SettingsCard(
                            title = "危险操作",
                            danger = true,
                            children = listOf({ Button(onPressed = {}, tone = ButtonTone.danger) { Text("注销账号") } }),
                        )
                    },
                ),
        )
    }
}
