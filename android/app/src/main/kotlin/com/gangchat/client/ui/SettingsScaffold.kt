package com.gangchat.client.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import com.gangchat.client.widgets.rememberBuildContext

/** Test tag of the section navigation (Dart's `ValueKey('settings-side-navigation')`). */
const val settingsSideNavigationTag = "settings-side-navigation"

/** Test tag of the back button (Dart's `ValueKey('settings-back-button')`). */
const val settingsBackButtonTag = "settings-back-button"

/** Legacy in-content title bar strip (Dart's `titleBarHeight`): 0, native chrome is used. */
private val titleBarHeight = 0.dp

/**
 * Section navigation for [SettingsScaffold]. Wide pages render it as a
 * vertical list in a side column; narrow pages render it as a scrolling row
 * of pills above the content.
 */
abstract class SettingsNavigationBase {
    abstract val selectedLabel: String?
    abstract val selectedIcon: ImageVector?

    @Composable
    abstract fun buildVertical(context: BuildContext)

    @Composable
    abstract fun buildHorizontal(context: BuildContext)
}

open class SettingsNavigation<T>(
    val segments: List<Segment<T>>,
    val value: T,
    val onChanged: (T) -> Unit,
) : SettingsNavigationBase() {
    private val _selected: Segment<T>?
        get() {
            for (segment in segments) {
                if (segment.value == value) return segment
            }
            return segments.firstOrNull()
        }

    override val selectedLabel: String? get() = _selected?.label

    override val selectedIcon: ImageVector? get() = _selected?.icon

    @Composable
    override fun buildVertical(context: BuildContext) {
        Column(Modifier.fillMaxWidth()) {
            for (segment in segments) {
                _SettingsNavTile(segment = segment, selected = segment.value == value, onPressed = { onChanged(segment.value) })
                Spacer(Modifier.height(2.dp))
            }
        }
    }

    @Composable
    override fun buildHorizontal(context: BuildContext) {
        LazyRow(Modifier.height(34.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(segments.size) { index ->
                val segment = segments[index]
                _SettingsNavPill(segment = segment, selected = segment.value == value, onPressed = { onChanged(segment.value) })
            }
        }
    }
}

/**
 * Section navigation drawn as a strip of numbered tiles, each carrying a
 * one-line summary of the section's current values. Used by the app
 * settings page ("control room" layout).
 */
class SettingsTileNavigation<T>(
    segments: List<Segment<T>>,
    value: T,
    onChanged: (T) -> Unit,
    /** Current-value summaries keyed by section. */
    val summaries: Map<T, String> = emptyMap(),
) : SettingsNavigation<T>(segments, value, onChanged) {
    val selectedIndex: Int
        get() {
            for (i in segments.indices) {
                if (segments[i].value == value) return i
            }
            return 0
        }

    @Composable
    fun buildTiles(context: BuildContext, compact: Boolean, horizontalPadding: Dp, maxWidth: Dp) {
        val gap = 8.dp
        val count = segments.size
        val minTileWidth = 112.dp
        val available = maxWidth - horizontalPadding * 2
        val fits = available >= minTileWidth * count + gap * (count - 1)

        @Composable
        fun tile(index: Int, modifier: Modifier = Modifier, width: Dp? = null) {
            val segment = segments[index]
            _SettingsTile(
                index = index,
                segment = segment,
                summary = if (compact) null else summaries[segment.value],
                selected = segment.value == value,
                compact = compact,
                width = width,
                onPressed = { onChanged(segment.value) },
                modifier = modifier,
            )
        }

        if (fits) {
            Row(Modifier.fillMaxSize().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until count) {
                    if (i > 0) Spacer(Modifier.width(gap))
                    Box(Modifier.weight(1f), propagateMinConstraints = true) { tile(i) }
                }
            }
            return
        }
        // Only a handful of tiles: build them all so every section stays
        // reachable (and findable) even while scrolled out of view.
        Row(
            Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until count) {
                if (i > 0) Spacer(Modifier.width(gap))
                tile(i, width = minTileWidth + 12.dp)
            }
        }
    }
}

@Composable
private fun <T> _SettingsTile(
    index: Int,
    segment: Segment<T>,
    summary: String?,
    selected: Boolean,
    compact: Boolean,
    onPressed: () -> Unit,
    width: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val foreground = if (selected) UiColors.onInverse else UiColors.text
    val muted = if (selected) UiColors.onInverse.copy(alpha = 0.62f) else UiColors.textMuted
    val icon = segment.icon
    PressableSurface(
        width = width,
        height = if (compact) 64.dp else 84.dp,
        selected = selected,
        onPressed = onPressed,
        // Tiles can live in a horizontally scrolling strip: a touch swipe must
        // scroll it instead of opening the section under the finger.
        cancelTouchPressOnDrag = true,
        backgroundColor = UiColors.card,
        hoverBackgroundColor = UiColors.surfaceRaised,
        selectedBackgroundColor = UiColors.inverse,
        borderColor = UiColors.border,
        selectedBorderColor = UiColors.inverse,
        borderRadius = UiRadii.lg,
        padding = EdgeInsets.fromLTRB(12.dp, if (compact) 9.dp else 11.dp, 10.dp, if (compact) 9.dp else 11.dp),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = UiTypography.dot.copyWith(fontSize = 13.0, height = 1.0, color = if (selected) UiColors.onInverse else UiColors.accent),
                )
                Spacer(Modifier.weight(1f))
                if (segment.showBadge) {
                    BadgeDot(
                        modifier = segment.badgeKey?.let { Modifier.testTag(it) } ?: Modifier,
                        size = 7.dp,
                        borderColor = Color.Transparent,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (icon != null) Icon(icon, size = 15.dp, color = muted)
            }
            Column {
                Text(
                    segment.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTypography.body.copyWith(color = foreground, fontSize = 13.0, fontWeight = FontWeight.W700, height = 1.25),
                )
                if (!summary.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        summary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.mono.copyWith(color = muted, fontSize = 10.0),
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> _SettingsNavTile(segment: Segment<T>, selected: Boolean, onPressed: () -> Unit) {
    val icon = segment.icon
    PressableSurface(
        height = 38.dp,
        selected = selected,
        onPressed = onPressed,
        backgroundColor = Color.Transparent,
        hoverBackgroundColor = UiColors.surface,
        selectedBackgroundColor = UiColors.inverse,
        borderColor = Color.Transparent,
        selectedBorderColor = Color.Transparent,
        borderRadius = UiRadii.pill,
        padding = EdgeInsets.symmetric(horizontal = 14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, size = 17.dp, color = if (selected) UiColors.onInverse else UiColors.textMuted)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                segment.label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                    UiTypography.body.copyWith(
                        fontSize = 13.5,
                        color = if (selected) UiColors.onInverse else UiColors.textSecondary,
                        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                    ),
            )
            if (segment.showBadge) {
                BadgeDot(
                    modifier = segment.badgeKey?.let { Modifier.testTag(it) } ?: Modifier,
                    size = 7.dp,
                    borderColor = Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun <T> _SettingsNavPill(segment: Segment<T>, selected: Boolean, onPressed: () -> Unit) {
    val icon = segment.icon
    PressableSurface(
        height = 34.dp,
        selected = selected,
        onPressed = onPressed,
        // The pills live in a horizontally scrolling row: a touch swipe must
        // scroll it instead of opening the section under the finger.
        cancelTouchPressOnDrag = true,
        backgroundColor = UiColors.surface,
        selectedBackgroundColor = UiColors.inverse,
        borderColor = Color.Transparent,
        selectedBorderColor = Color.Transparent,
        borderRadius = UiRadii.pill,
        padding = EdgeInsets.symmetric(horizontal = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, size = 15.dp, color = if (selected) UiColors.onInverse else UiColors.textMuted)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                segment.label,
                style =
                    UiTypography.label.copyWith(
                        fontSize = 13.0,
                        color = if (selected) UiColors.onInverse else UiColors.textSecondary,
                        fontWeight = FontWeight.W600,
                    ),
            )
            if (segment.showBadge) {
                Spacer(Modifier.width(6.dp))
                BadgeDot(
                    modifier = segment.badgeKey?.let { Modifier.testTag(it) } ?: Modifier,
                    size = 7.dp,
                    borderColor = Color.Transparent,
                )
            }
        }
    }
}

/**
 * 统一的设置类页面外壳。
 *
 * 有分区导航时，宽屏采用"左侧竖向导航 + 右侧内容"的双栏布局，窄屏退化为
 * 顶部胶囊导航；没有分区导航时是带标题的单栏页面。调用方只需提供图标、
 * 标题、内容和可选的右侧操作，版式由模版统一管理。
 */
@Composable
fun SettingsScaffold(
    /** 页面图标。 */
    icon: ImageVector,
    /** 页面标题。 */
    title: String,
    /** 主体内容,通常是 [SettingsList] 或自定义滚动视图。 */
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** 右侧额外操作(如刷新按钮),可空。 */
    headerAction: (@Composable () -> Unit)? = null,
    /** 图标颜色;为空时使用默认强调色。 */
    iconColor: Color? = null,
    /** 返回回调;为空则不显示返回按钮。 */
    onBack: (() -> Unit)? = null,
    /** 内容上方的固定区域(如搜索与筛选),不随内容滚动。 */
    pinned: (@Composable () -> Unit)? = null,
    /** 分区导航。 */
    navigation: SettingsNavigationBase? = null,
    /** 页面已处在带标题的上下文里(如房间内的标签页)时可隐藏页面标题。 */
    showTitle: Boolean = true,
    /** Search field shown in the header of the tile ("control room") layout. */
    search: (@Composable () -> Unit)? = null,
    /**
     * Replaces the tile layout's section title and its `01 / 07` counter,
     * e.g. while search results are shown.
     */
    sectionTitleOverride: String? = null,
    sectionMetaOverride: String? = null,
) {
    val context = rememberBuildContext()
    val scaffold =
        _SettingsScaffold(
            icon, title, body, headerAction, iconColor, onBack, pinned, navigation, showTitle, search,
            sectionTitleOverride, sectionMetaOverride,
        )
    BoxWithConstraints(modifier.fillMaxSize().decoration(UiColors.surfaceLow)) {
        val navigation = navigation
        if (navigation is SettingsTileNavigation<*>) {
            scaffold._buildTileLayout(context, navigation, maxWidth)
        } else if (navigation != null && maxWidth >= _twoPaneMinWidth) {
            scaffold._buildTwoPane(context, navigation)
        } else {
            scaffold._buildSinglePane(context, navigation)
        }
    }
}

private val _sideNavWidth = 232.dp
private val _singlePaneHeaderContentHeight = 48.dp
private val _singlePaneContentTopGap = 14.dp
private val _twoPaneMinWidth = 720.dp
private val _contentTopGap = 16.dp
private val _horizontalPadding = 28.dp

private class _SettingsScaffold(
    val icon: ImageVector,
    val title: String,
    val body: @Composable () -> Unit,
    val headerAction: (@Composable () -> Unit)?,
    val iconColor: Color?,
    val onBack: (() -> Unit)?,
    val pinned: (@Composable () -> Unit)?,
    val navigation: SettingsNavigationBase?,
    val showTitle: Boolean,
    val search: (@Composable () -> Unit)?,
    val sectionTitleOverride: String?,
    val sectionMetaOverride: String?,
) {
    /**
     * Control-room layout: a header (back, wordmark, search, action), a strip
     * of numbered section tiles with live summaries, the section title, then
     * the section body.
     */
    @Composable
    fun _buildTileLayout(context: BuildContext, navigation: SettingsTileNavigation<*>, maxWidth: Dp) {
        val compact = maxWidth < _twoPaneMinWidth
        val padding = if (compact) 16.dp else _horizontalPadding
        val search = search
        val sectionTitle = sectionTitleOverride ?: navigation.selectedLabel ?: title
        val index = navigation.selectedIndex + 1
        val total = navigation.segments.size
        val sectionMeta =
            sectionMetaOverride ?: "${index.toString().padStart(2, '0')} / ${total.toString().padStart(2, '0')}"
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (onBack == null) padding else padding - 8.dp, top = if (compact) 14.dp else 20.dp, end = padding - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    ButtonIcon(
                        tooltip = "返回",
                        icon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack) },
                        onPressed = onBack,
                        size = 36.dp,
                        ghost = true,
                        modifier = Modifier.testTag(settingsBackButtonTag),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Column {
                    Text(
                        "SETTINGS",
                        maxLines = 1,
                        style = UiTypography.dot.copyWith(fontSize = if (compact) 18.0 else 22.0, height = 1.05, letterSpacing = 1.0),
                    )
                    if (showTitle) {
                        Spacer(Modifier.height(3.dp))
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = UiTypography.label.copyWith(color = UiColors.textMuted))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (search != null && !compact) {
                        Box(Modifier.widthIn(max = 340.dp)) { search() }
                    }
                }
                if (headerAction != null) {
                    Spacer(Modifier.width(8.dp))
                    headerAction.invoke()
                }
            }
            if (search != null && compact) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal = padding)) { search() }
            }
            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))
            Box(Modifier.testTag(settingsSideNavigationTag).fillMaxWidth().height(if (compact) 64.dp else 84.dp)) {
                navigation.buildTiles(context, compact = compact, horizontalPadding = padding, maxWidth = maxWidth)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = padding, top = if (compact) 16.dp else 22.dp, end = padding, bottom = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    sectionTitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (compact) UiTypography.title else UiTypography.headline,
                )
                Spacer(Modifier.width(12.dp))
                Text(sectionMeta, style = UiTypography.overline)
            }
            val pinned = pinned
            if (pinned != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = padding)) { pinned() }
                Spacer(Modifier.height(_contentTopGap))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) {
                _SettingsBodyInsets(horizontal = padding, child = body)
            }
        }
    }

    @Composable
    fun _buildTwoPane(context: BuildContext, navigation: SettingsNavigationBase) {
        val sectionTitle = navigation.selectedLabel ?: title
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .testTag(settingsSideNavigationTag)
                    .width(_sideNavWidth)
                    .fillMaxHeight()
                    .decoration(UiColors.background)
                    .rightBorder(UiColors.border)
                    // Flutter's Container adds the 1 dp right border to its padding.
                    .padding(start = 12.dp, top = 18.dp, end = 13.dp, bottom = 14.dp),
            ) {
                if (showTitle) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        _TitleIcon(icon = icon, color = iconColor)
                        Spacer(Modifier.width(10.dp))
                        Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = UiTypography.title)
                    }
                    Spacer(Modifier.height(18.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    navigation.buildVertical(context)
                }
                val onBack = onBack
                if (onBack != null) {
                    Spacer(Modifier.height(10.dp))
                    _BackLink(onPressed = onBack)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(start = _horizontalPadding, top = 18.dp, end = _horizontalPadding - 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sectionTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = UiTypography.headline)
                    headerAction?.invoke()
                }
                val pinned = pinned
                if (pinned != null) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = _horizontalPadding), contentAlignment = Alignment.TopCenter) { pinned() }
                    Spacer(Modifier.height(_contentTopGap))
                }
                Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) {
                    _SettingsBodyInsets(horizontal = _horizontalPadding, child = body)
                }
            }
        }
    }

    /**
     * Single-column page: header (back, icon, title, action), optional
     * pinned area, then the full-width body. With a [navigation] the section
     * pills sit under the header.
     */
    @Composable
    fun _buildSinglePane(context: BuildContext, navigation: SettingsNavigationBase?) {
        val horizontalPadding = 22.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(titleBarHeight + 16.dp + _singlePaneHeaderContentHeight)
                    .padding(start = horizontalPadding, top = titleBarHeight + 16.dp, end = horizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val onBack = onBack
                if (onBack != null) {
                    ButtonIcon(
                        tooltip = "返回",
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack) },
                        onPressed = onBack,
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                if (showTitle) {
                    Icon(icon, color = iconColor ?: UiColors.accent, size = 19.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = UiTypography.headline.copyWith(fontSize = 20.0),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                headerAction?.invoke()
            }
            if (navigation != null) {
                Spacer(Modifier.height(_singlePaneContentTopGap))
                Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) { navigation.buildHorizontal(context) }
            }
            val pinned = pinned
            if (pinned != null) {
                Spacer(Modifier.height(_singlePaneContentTopGap))
                Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPadding), contentAlignment = Alignment.TopCenter) { pinned() }
            }
            Spacer(Modifier.height(_singlePaneContentTopGap))
            Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) {
                _SettingsBodyInsets(horizontal = horizontalPadding, child = body)
            }
        }
    }
}

/**
 * Publishes the page's horizontal inset so [SettingsList] and
 * [SettingsFixedHeaderCard] align with the scaffold's header.
 */
private val _LocalSettingsBodyInsets = compositionLocalOf<Dp?> { null }

@Composable
private fun _SettingsBodyInsets(horizontal: Dp, child: @Composable () -> Unit) {
    CompositionLocalProvider(_LocalSettingsBodyInsets provides horizontal, content = child)
}

@Composable
private fun _settingsBodyInset(fallback: Dp): Dp = _LocalSettingsBodyInsets.current ?: fallback

@Composable
private fun _TitleIcon(icon: ImageVector, color: Color?) {
    val resolved = color ?: UiColors.accent
    Box(
        Modifier.size(32.dp).decoration(resolved.copy(alpha = resolved.alpha * 0.14f), RoundedCornerShape(UiRadii.md)),
        contentAlignment = Alignment.TopStart,
        propagateMinConstraints = true,
    ) {
        Icon(icon, color = resolved, size = 18.dp)
    }
}

@Composable
private fun _BackLink(onPressed: () -> Unit) {
    PressableSurface(
        height = 36.dp,
        tooltip = "返回",
        onPressed = onPressed,
        backgroundColor = Color.Transparent,
        hoverBackgroundColor = UiColors.surface,
        borderColor = UiColors.border,
        borderRadius = UiRadii.md - 2.dp,
        padding = EdgeInsets.symmetric(horizontal = 10.dp),
        modifier = Modifier.fillMaxWidth().testTag(settingsBackButtonTag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, size = 16.dp, color = UiColors.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text("返回", style = UiTypography.label.copyWith(color = UiColors.textSecondary, fontSize = 13.0))
        }
    }
}

/**
 * 设置内容列表:在各 item 之间自动插入固定间距,避免手写 `SizedBox`。
 *
 * Dart's `physics:` is not ported: the only value callers pass,
 * `ClampingScrollPhysics`, is already Android's scroll physics.
 */
@Composable
fun SettingsList(
    /** 列表项。相邻项之间会自动插入 [spacing] 的间距。 */
    children: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    /** 列表外边距。为空时与外层 [SettingsScaffold] 的水平边距对齐。 */
    padding: EdgeInsets? = null,
    /** 相邻项之间的固定间距。 */
    spacing: Dp = 14.dp,
) {
    val inset = _settingsBodyInset(24.dp)
    val resolved = padding ?: EdgeInsets.fromLTRB(inset, 0.dp, inset, 24.dp)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = resolved.left, top = resolved.top, end = resolved.right, bottom = resolved.bottom),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items(children.size) { index ->
            // ListView children stretch across the list.
            Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { children[index]() }
        }
    }
}

/** 带卡片边框与小标题的分区。内部 children 之间自动插入固定间距。 */
@Composable
fun SettingsCard(
    /** 分区小标题。 */
    title: String,
    /** 分区内容。相邻项之间会自动插入 [spacing] 的间距。 */
    children: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    /** 自定义分区标题。为空时使用 [title] 与统一的标题文字样式。 */
    titleWidget: (@Composable () -> Unit)? = null,
    /** 标题右侧的额外元素(如加载指示器),可空。 */
    trailing: (@Composable () -> Unit)? = null,
    /** 危险分区(如删除操作),标题与边框使用警示色。 */
    danger: Boolean = false,
    /** 相邻内容项之间的固定间距。 */
    spacing: Dp = 12.dp,
) {
    _SettingsCard(title, children, modifier, titleWidget, trailing, danger, spacing, expandedBody = null)
}

/**
 * [expandedBody] is Dart's trailing `Expanded(child: body)` child: it takes
 * the card's remaining height, and the card then fills its parent's height
 * (a `Column` with `MainAxisSize.max` under bounded height).
 */
@Composable
private fun _SettingsCard(
    title: String,
    children: List<@Composable () -> Unit>,
    modifier: Modifier,
    titleWidget: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    danger: Boolean,
    spacing: Dp,
    expandedBody: (@Composable () -> Unit)?,
) {
    // DecoratedBox, not Container: the border does not inset the padding.
    Column(
        modifier
            .fillMaxWidth()
            .then(if (expandedBody != null) Modifier.fillMaxHeight() else Modifier)
            .decoration(
                if (danger) Color(0xFF1C1418) else UiColors.card,
                RoundedCornerShape(UiRadii.lg),
                if (danger) UiColors.dangerBorder else UiColors.border,
            )
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), propagateMinConstraints = true) {
                if (titleWidget != null) {
                    titleWidget()
                } else {
                    Text(title, style = UiTypography.subtitle.copyWith(color = if (danger) UiColors.danger else UiColors.text))
                }
            }
            trailing?.invoke()
        }
        if (children.isNotEmpty() || expandedBody != null) {
            Spacer(Modifier.height(14.dp))
            for ((i, child) in children.withIndex()) {
                if (i > 0) Spacer(Modifier.height(spacing))
                // CrossAxisAlignment.stretch.
                Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) { child() }
            }
            if (expandedBody != null) {
                if (children.isNotEmpty()) Spacer(Modifier.height(spacing))
                Box(Modifier.weight(1f).fillMaxWidth(), propagateMinConstraints = true) { expandedBody() }
            }
        }
    }
}

/**
 * A settings card whose title and controls stay visible while only [body]
 * scrolls. The parent must provide bounded height, as [SettingsScaffold]
 * does for its body.
 */
@Composable
fun SettingsFixedHeaderCard(
    title: String,
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headerChildren: List<@Composable () -> Unit> = emptyList(),
    titleWidget: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    spacing: Dp = 10.dp,
    padding: EdgeInsets? = null,
) {
    val inset = _settingsBodyInset(24.dp)
    Box(modifier.fillMaxSize().padding(padding ?: EdgeInsets.fromLTRB(inset, 0.dp, inset, 24.dp)), propagateMinConstraints = true) {
        _SettingsCard(
            title = title,
            children = headerChildren,
            modifier = Modifier,
            titleWidget = titleWidget,
            trailing = trailing,
            danger = false,
            spacing = spacing,
            expandedBody = body,
        )
    }
}
