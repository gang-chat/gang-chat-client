package com.gangchat.client.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.SessionListBodyState
import com.gangchat.client.app.SettingsSection
import com.gangchat.client.app.sessionDetailText
import com.gangchat.client.app.sessionDeviceLabel
import com.gangchat.client.app.sessionListBodyState
import com.gangchat.client.app.sessionStateText
import com.gangchat.client.protocol.UserSession
import com.gangchat.client.ui.HighlightedText
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.ReadOnlyTextBox
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiSwitch
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.uiTextStyle
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.ignorePointer
import com.gangchat.client.widgets.padding

internal val _settingsSectionSegments: List<Segment<SettingsSection>> =
    listOf(
        Segment(value = SettingsSection.profile, label = "用户资料", icon = Icons.Outlined.Badge),
        Segment(value = SettingsSection.preferences, label = "偏好设置", icon = Icons.Outlined.Tune),
        Segment(value = SettingsSection.security, label = "隐私和安全", icon = Icons.Outlined.Shield),
        Segment(value = SettingsSection.voice, label = "语音和视频", icon = Icons.Filled.GraphicEq),
        Segment(value = SettingsSection.stickers, label = "我的表情包", icon = Icons.Outlined.EmojiEmotions),
        Segment(value = SettingsSection.playlists, label = "我的歌单", icon = Icons.AutoMirrored.Outlined.QueueMusic),
        Segment(value = SettingsSection.about, label = "关于Gang Chat", icon = Icons.Outlined.Info),
    )

/** One searchable setting: where it lives and the words that find it. */
class SettingsSearchEntry(
    val section: SettingsSection,
    val label: String,
    val keywords: String,
    val androidOnly: Boolean = false,
    val desktopOnly: Boolean = false,
)

private val _settingsSearchIndex: List<SettingsSearchEntry> =
    listOf(
        SettingsSearchEntry(SettingsSection.profile, "个人 UID", "uid id 账号 编号"),
        SettingsSearchEntry(SettingsSection.profile, "登录用户名", "username 账号 登录"),
        SettingsSearchEntry(SettingsSection.profile, "用户名", "昵称 名称 name 显示"),
        SettingsSearchEntry(SettingsSection.profile, "性别", "gender 男 女 保密"),
        SettingsSearchEntry(SettingsSection.profile, "头像", "avatar 上传 图片 照片"),
        SettingsSearchEntry(SettingsSection.profile, "签名", "bio 简介 个性签名 介绍"),
        SettingsSearchEntry(SettingsSection.preferences, "关闭方式", "close 托盘 最小化 退出 窗口", desktopOnly = true),
        SettingsSearchEntry(SettingsSection.preferences, "系统通知", "notification 通知 消息 角标", androidOnly = true),
        SettingsSearchEntry(SettingsSection.preferences, "语言切换", "language 语言 中文 繁體 english"),
        SettingsSearchEntry(SettingsSection.security, "邮箱绑定", "email 邮件 验证"),
        SettingsSearchEntry(SettingsSection.security, "公开邮箱", "email 隐私 公开"),
        SettingsSearchEntry(SettingsSection.security, "手机号绑定", "phone 电话 手机"),
        SettingsSearchEntry(SettingsSection.security, "公开手机号", "phone 隐私 公开"),
        SettingsSearchEntry(SettingsSection.security, "重置密码", "password 密码 修改 更新"),
        SettingsSearchEntry(SettingsSection.security, "忘记密码", "password 密码 找回"),
        SettingsSearchEntry(SettingsSection.security, "账号活动", "session 登录 设备 会话 记录"),
        SettingsSearchEntry(SettingsSection.security, "注销账号", "delete 删除 注销"),
        SettingsSearchEntry(SettingsSection.voice, "输入源", "mic 麦克风 设备 输入"),
        SettingsSearchEntry(SettingsSection.voice, "输入音量", "mic 麦克风 音量 测试"),
        SettingsSearchEntry(SettingsSection.voice, "输出源", "speaker 耳机 扬声器 设备"),
        SettingsSearchEntry(SettingsSection.voice, "输出音量", "volume 音量 声音 测试"),
        SettingsSearchEntry(SettingsSection.voice, "屏幕共享", "screen share 分辨率 帧率 画质 直播"),
        SettingsSearchEntry(SettingsSection.stickers, "本地上传", "sticker 表情 表情包 上传 图片"),
        SettingsSearchEntry(SettingsSection.stickers, "批量管理", "sticker 表情 删除 下载 置顶 筛选"),
        SettingsSearchEntry(SettingsSection.playlists, "我的歌单", "playlist music 歌单 音乐 歌曲 导入"),
        SettingsSearchEntry(SettingsSection.about, "版本信息", "version 版本 发行 更新时间"),
        SettingsSearchEntry(SettingsSection.about, "自动提示更新", "update 更新 提示"),
        SettingsSearchEntry(SettingsSection.about, "检查更新", "update 更新 升级 下载"),
        SettingsSearchEntry(SettingsSection.about, "意见反馈", "feedback 反馈 邮件 bug"),
    )

/** Settings matching [query] by label, keyword or section name. */
fun settingsSearchMatches(query: String, android: Boolean): List<SettingsSearchEntry> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    fun sectionLabel(section: SettingsSection): String {
        for (segment in _settingsSectionSegments) {
            if (segment.value == section) return segment.label
        }
        return ""
    }

    return _settingsSearchIndex.filter { entry ->
        !(entry.androidOnly && !android) &&
            !(entry.desktopOnly && android) &&
            "${entry.label} ${entry.keywords} ${sectionLabel(entry.section)}".lowercase().contains(needle)
    }
}

/** Test tag of search result [index] (Dart's `ValueKey('settings-search-result-$index')`). */
fun settingsSearchResultTag(index: Int) = "settings-search-result-$index"

@Composable
internal fun _SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    query: String,
    onPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val segment = _settingsSectionSegments.first { segment -> segment.value == entry.section }
    val index = _settingsSectionSegments.indexOf(segment) + 1
    PressableSurface(
        height = 54.dp,
        onPressed = onPressed,
        backgroundColor = UiColors.card,
        hoverBackgroundColor = UiColors.surfaceRaised,
        borderColor = UiColors.border,
        borderRadius = UiRadii.md,
        padding = EdgeInsets.symmetric(horizontal = 14.dp),
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).decoration(UiColors.selected, RoundedCornerShape(UiRadii.sm)),
                propagateMinConstraints = true,
            ) {
                Icon(segment.icon, size = 16.dp, color = UiColors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                HighlightedText(
                    text = entry.label,
                    query = query,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.body.copyWith(fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${index.toString().padStart(2, '0')} · ${segment.label}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.mono.copyWith(fontSize = 10.5),
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, size = 16.dp, color = UiColors.textMuted)
        }
    }
}

/**
 * Lays [children] out like a Flutter `Column(crossAxisAlignment: stretch)`:
 * every child gets the full width as its minimum.
 */
@Composable
internal fun _StretchColumn(children: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        for (child in children) {
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { child() }
        }
    }
}

/** Flutter's `SizedBox(height:)` as a stretch-column child. */
internal fun _gap(height: Dp): @Composable () -> Unit = { Spacer(Modifier.height(height)) }

private val _settingsGroupWideMinWidth = 700.dp
private val _settingsGroupTitleColumnWidth = 188.dp

/**
 * A settings group laid out as a form row: on wide pages the title sits in
 * its own left column beside the controls' panel; narrow pages stack them.
 */
@Composable
internal fun _SettingsGroup(
    title: String,
    children: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    danger: Boolean = false,
) {
    val accent = if (danger) UiColors.danger else UiColors.accent
    val titleText: @Composable () -> Unit = {
        Text(title, style = UiTypography.subtitle.copyWith(color = if (danger) UiColors.danger else UiColors.text))
    }
    val panel: @Composable (Modifier) -> Unit = { panelModifier ->
        // DecoratedBox: the border does not inset the padding.
        _StretchColumn(
            children,
            panelModifier
                .decoration(
                    if (danger) Color(0xFF1A1012) else UiColors.card,
                    RoundedCornerShape(UiRadii.lg),
                    if (danger) UiColors.dangerBorder else UiColors.border,
                )
                .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
        )
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < _settingsGroupWideMinWidth) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 2.dp, top = 0.dp, end = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).height(14.dp).decoration(accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) { titleText() }
                    trailing?.invoke()
                }
                panel(Modifier)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .width(_settingsGroupTitleColumnWidth)
                        .padding(start = 2.dp, top = 14.dp, end = 18.dp),
                ) {
                    Box(Modifier.width(18.dp).height(3.dp).decoration(accent, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(10.dp))
                    titleText()
                    if (trailing != null) {
                        Spacer(Modifier.height(10.dp))
                        trailing()
                    }
                }
                Box(Modifier.weight(1f), propagateMinConstraints = true) { panel(Modifier) }
            }
        }
    }
}

/**
 * A dark, rounded sub-panel. [hoverable] panels highlight under a mouse;
 * [highlighted] keeps the highlight (selected cards).
 */
@Composable
internal fun _SettingsSubPanel(
    modifier: Modifier = Modifier,
    hoverable: Boolean = false,
    highlighted: Boolean = false,
    child: @Composable () -> Unit,
) = StatefulWidget(_SettingsSubPanelWidget(child, hoverable, highlighted, modifier)) { _SettingsSubPanelState() }

internal class _SettingsSubPanelWidget(
    val child: @Composable () -> Unit,
    val hoverable: Boolean,
    val highlighted: Boolean,
    val modifier: Modifier,
)

private class _SettingsSubPanelState : ViewState<_SettingsSubPanelWidget>() {
    private var _hovered = false

    private fun _setHovered(hovered: Boolean) {
        if (!widget.hoverable || _hovered == hovered) return
        setState { _hovered = hovered }
    }

    @Composable
    override fun build() {
        val highlighted = widget.highlighted || (widget.hoverable && _hovered)
        val spec = tween<Color>(90, easing = Curves.easeOutCubic)
        val background by animateColorAsState(if (highlighted) UiColors.selected else _primaryDark, spec, label = "subPanelBackground")
        val border by animateColorAsState(if (highlighted) UiColors.selectedBorder else _borderColor, spec, label = "subPanelBorder")
        val hover =
            if (!widget.hoverable) {
                Modifier
            } else {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.type == PointerType.Mouse }) continue
                            when (event.type) {
                                PointerEventType.Enter -> _setHovered(true)
                                PointerEventType.Exit -> _setHovered(false)
                                else -> Unit
                            }
                        }
                    }
                }
            }
        // Flutter's (Animated)Container adds the 1 dp border to its 14 dp padding.
        Box(
            widget.modifier
                .then(hover)
                .decoration(background, RoundedCornerShape(UiRadii.md), border)
                .padding(EdgeInsets.all(15.dp)),
            propagateMinConstraints = true,
        ) {
            widget.child()
        }
    }
}

@Composable
internal fun _LabeledTextField(
    label: String,
    controller: TextEditingController,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
    obscureText: Boolean = false,
    keyboardType: KeyboardType? = null,
    helperText: String? = null,
    suffix: (@Composable () -> Unit)? = null,
    onChanged: ((String) -> Unit)? = null,
    onTogglePasswordVisibility: (() -> Unit)? = null,
) {
    val children = mutableListOf<@Composable () -> Unit>()
    children.add { _FieldLabel(label) }
    children.add(_gap(8.dp))
    children.add {
        Input(
            controller = controller,
            hintText = "",
            enabled = enabled,
            obscureText = obscureText,
            keyboardType = keyboardType,
            minLines = 1,
            maxLines = if (obscureText) 1 else maxLines,
            onChanged = onChanged,
            suffix =
                if (onTogglePasswordVisibility == null) {
                    suffix
                } else {
                    { _PasswordVisibilityToggle(obscure = obscureText, enabled = enabled, onPressed = onTogglePasswordVisibility) }
                },
        )
    }
    if (helperText != null) {
        children.add(_gap(6.dp))
        children.add { Text(helperText, style = uiTextStyle(color = _textMuted, fontSize = 12.0)) }
    }
    _StretchColumn(children, modifier)
}

@Composable
internal fun _CopyableField(label: String, value: String, modifier: Modifier = Modifier) {
    _StretchColumn(
        listOf(
            { _FieldLabel(label) },
            _gap(8.dp),
            {
                ReadOnlyTextBox(
                    value = value,
                    backgroundColor = _primaryDark,
                    borderColor = _borderColor,
                    style = uiTextStyle(color = _textSecondary, fontSize = 14.0, fontWeight = FontWeight.W500),
                )
            },
        ),
        modifier,
    )
}

@Composable
internal fun _ReadOnlyLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(110.dp)) { _FieldLabel(label) }
        Text(
            value,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = uiTextStyle(color = _textSecondary, fontSize = 14.0, fontWeight = FontWeight.W500),
        )
    }
}

/** Test tag of a [_ToggleSetting]'s switch. */
fun settingsToggleTag(label: String) = "settings-toggle:$label"

@Composable
internal fun _ToggleSetting(
    label: String,
    value: Boolean,
    onChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { _FieldLabel(label) }
        Box(Modifier.ignorePointer(!enabled).alpha(if (enabled) 1f else 0.55f)) {
            UiSwitch(value = value, onChanged = onChanged, modifier = Modifier.testTag(settingsToggleTag(label)))
        }
    }
}

internal class _SegmentOption(val value: String, val label: String)

@Composable
internal fun _SegmentedSetting(
    label: String,
    value: String,
    options: List<_SegmentOption>,
    onChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    _StretchColumn(
        listOf(
            { _FieldLabel(label) },
            _gap(8.dp),
            {
                Box(Modifier.ignorePointer(!enabled).alpha(if (enabled) 1f else 0.55f), propagateMinConstraints = true) {
                    SegmentedControl(
                        expanded = true,
                        value = value,
                        onChanged = onChanged,
                        segments = options.map { option -> Segment(value = option.value, label = option.label) },
                    )
                }
            },
        ),
        modifier,
    )
}

/** Flutter's Material `CircularProgressIndicator` as the settings page uses it (36 dp, 4 dp stroke). */
@Composable
internal fun _SettingsProgressIndicator(size: Dp = 36.dp, strokeWidth: Dp = 4.dp, color: Color = _cyan) {
    CircularProgressIndicator(
        modifier = Modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
        trackColor = Color.Transparent,
        strokeCap = StrokeCap.Square,
    )
}

@Composable
internal fun _SessionList(sessions: List<UserSession>, loading: Boolean) {
    when (sessionListBodyState(loading = loading, sessions = sessions)) {
        SessionListBodyState.loading ->
            Box(Modifier.fillMaxWidth().height(82.dp), contentAlignment = Alignment.Center) { _SettingsProgressIndicator() }
        SessionListBodyState.empty -> _SettingsEmptyState(text = "暂无近期账号活动")
        SessionListBodyState.results ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                for (session in sessions) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .decoration(_primaryDark, RoundedCornerShape(UiRadii.md), _borderColor)
                                .padding(12.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (session.isCurrent) Icons.Filled.RadioButtonChecked else Icons.Filled.DevicesOther,
                                    color = if (session.isCurrent) _cyan else _textMuted,
                                    size = 16.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sessionDeviceLabel(session),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = uiTextStyle(color = _textPrimary, fontSize = 13.0, fontWeight = FontWeight.W600),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    sessionStateText(session),
                                    style =
                                        uiTextStyle(
                                            color = if (session.isActive) _cyan else _textMuted,
                                            fontSize = 12.0,
                                            fontWeight = FontWeight.W600,
                                        ),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                sessionDetailText(session),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = uiTextStyle(color = _textMuted, fontSize = 12.0, height = 1.35),
                            )
                        }
                    }
                }
            }
    }
}

@Composable
internal fun _FieldLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = uiTextStyle(color = _textMuted, fontSize = 12.0, fontWeight = FontWeight.W600),
    )
}

@Composable
internal fun _SettingsEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, style = uiTextStyle(color = _textMuted, fontSize = 13.0))
    }
}
