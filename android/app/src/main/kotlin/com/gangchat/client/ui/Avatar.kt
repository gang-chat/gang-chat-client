package com.gangchat.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.LocalDefaultTextStyle
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.padding
import java.text.BreakIterator

/**
 * Fallback initials shown when an avatar has no image. Takes the first two
 * characters of the label so list avatars, the settings preview, and every
 * other fallback stay identical. Returns '?' for an empty label.
 */
fun avatarInitials(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return "?"
    // Dart's `characters.take(2)`: grapheme clusters, not UTF-16 units.
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(trimmed)
    var end = 0
    repeat(2) {
        val next = iterator.next()
        if (next == BreakIterator.DONE) return@repeat
        end = next
    }
    return trimmed.substring(0, end).uppercase()
}

const val kDefaultAvatarPresetKey: String = "blue-3"

val kLegacyAvatarPresetAliases: Map<String, String> = mapOf("room-1" to kDefaultAvatarPresetKey)

fun normalizeAvatarPresetKey(key: String): String {
    val trimmed = key.trim()
    if (trimmed.isEmpty()) return kDefaultAvatarPresetKey
    return kLegacyAvatarPresetAliases[trimmed] ?: trimmed
}

fun avatarFallbackColor(key: String): Color =
    when (normalizeAvatarPresetKey(key)) {
        kDefaultAvatarPresetKey -> Color(0xFF4E68D0)
        "navy-2" -> Color(0xFF3B589B)
        "azure-2" -> Color(0xFF367FC9)
        "sky-2" -> Color(0xFF3095C0)
        "cyan-2" -> Color(0xFF2B95A1)
        "turquoise-2" -> Color(0xFF319B8D)
        "mint-2" -> Color(0xFF419F7A)
        "emerald-2" -> Color(0xFF2C9661)
        "green-2" -> Color(0xFF418B52)
        "forest-2" -> Color(0xFF427740)
        "lime-2" -> Color(0xFF6F913B)
        "yellow-2" -> Color(0xFFAE9932)
        "amber-2" -> Color(0xFFA87F38)
        "gold-2" -> Color(0xFFA38533)
        "orange-2" -> Color(0xFFC47131)
        "coral-2" -> Color(0xFFD46049)
        "red-2" -> Color(0xFFC9363F)
        "crimson-2" -> Color(0xFFAB3655)
        "pink-2" -> Color(0xFFC25191)
        "magenta-2" -> Color(0xFFA744A7)
        "violet-2" -> Color(0xFF8B57C7)
        "purple-2" -> Color(0xFF7F44A7)
        "lavender-2" -> Color(0xFF8573C9)
        "indigo-2" -> Color(0xFF4A4EBF)
        "rose-2" -> Color(0xFFBE6073)
        "teal-2" -> Color(0xFF407782)
        "olive-2" -> Color(0xFF767E44)
        "brown-2" -> Color(0xFF7E5B44)
        "sand-2" -> Color(0xFFA3875C)
        "slate-2" -> Color(0xFF5E6782)
        "steel-2" -> Color(0xFF517085)
        "gray-2" -> Color(0xFF696E77)
        "graphite-2" -> Color(0xFF50525E)
        "black-2" -> Color(0xFF31333F)
        else -> Color(0xFF4E68D0)
    }

/**
 * Simplified-Chinese display name for a persisted avatar color key.
 *
 * The key remains stable in API payloads and snapshots. Only the picker copy
 * is localized so older accounts and room records remain compatible.
 */
fun avatarPresetLabel(key: String): String =
    when (normalizeAvatarPresetKey(key)) {
        kDefaultAvatarPresetKey -> "蓝色"
        "navy-2" -> "深海蓝"
        "azure-2" -> "蔚蓝"
        "sky-2" -> "天蓝色"
        "cyan-2" -> "青色"
        "turquoise-2" -> "绿松石色"
        "mint-2" -> "薄荷绿"
        "emerald-2" -> "翡翠绿"
        "green-2" -> "绿色"
        "forest-2" -> "森林绿"
        "lime-2" -> "青柠绿"
        "yellow-2" -> "黄色"
        "amber-2" -> "琥珀色"
        "gold-2" -> "金色"
        "orange-2" -> "橙色"
        "coral-2" -> "珊瑚色"
        "red-2" -> "红色"
        "crimson-2" -> "绯红色"
        "pink-2" -> "粉色"
        "magenta-2" -> "品红色"
        "violet-2" -> "紫罗兰色"
        "purple-2" -> "紫色"
        "lavender-2" -> "薰衣草色"
        "indigo-2" -> "靛蓝色"
        "rose-2" -> "玫瑰色"
        "teal-2" -> "蓝绿色"
        "olive-2" -> "橄榄绿"
        "brown-2" -> "棕色"
        "sand-2" -> "沙色"
        "slate-2" -> "石板灰"
        "steel-2" -> "钢蓝色"
        "gray-2" -> "灰色"
        "graphite-2" -> "石墨灰"
        "black-2" -> "墨黑色"
        else -> "蓝色"
    }

/**
 * A circular (people) or rounded-square (rooms) avatar: the uploaded image,
 * or the preset colour with the label's initials.
 *
 * [defaultAvatarKey] is the preset color key driving the fallback fill. When
 * null, the neutral surface color is used. Matches the swatch colors offered
 * by the avatar picker (see [avatarFallbackColor]) so list avatars and the
 * settings preview stay in sync. [rounded] gives the rounded-square
 * ("squircle") shape rooms use so they read differently from people, which
 * are always circles.
 */
@Composable
fun Avatar(
    label: String,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    defaultAvatarKey: String? = null,
    size: Dp = 40.dp,
    active: Boolean = false,
    activeBorderWidth: Dp = 2.dp,
    activeBorderColor: Color? = null,
    paintBorderOnForeground: Boolean = false,
    showBorder: Boolean = true,
    showFallbackText: Boolean = true,
    rounded: Boolean = false,
) {
    val initials = avatarInitials(label)
    val key = defaultAvatarKey?.let { normalizeAvatarPresetKey(it) }
    val fallbackColor = if (key == null) UiColors.surface else avatarFallbackColor(key)
    val fillColor = if (imageUrl == null) fallbackColor else Color.Transparent
    val activeColor = activeBorderColor ?: UiColors.accent
    val borderActive = showBorder && active
    val borderColor: Color? = if (showBorder) (if (borderActive) activeColor else Color.White.copy(alpha = 0.06f)) else null
    val borderWidth = if (borderActive) activeBorderWidth else 1.dp
    val shape: Shape = if (rounded) RoundedCornerShape(size * 0.3f) else CircleShape
    val backgroundBorder = if (paintBorderOnForeground) null else borderColor
    val foregroundBorder = if (paintBorderOnForeground) borderColor else null
    Box(
        modifier
            .size(size)
            .decoration(fillColor, shape, backgroundBorder, borderWidth)
            .then(if (foregroundBorder != null) Modifier.foregroundBorder(shape, foregroundBorder, borderWidth) else Modifier)
            // Flutter's Container insets its child by the decoration's border.
            .padding(if (backgroundBorder != null) EdgeInsets.all(borderWidth) else EdgeInsets.zero),
        propagateMinConstraints = true,
    ) {
        Box(Modifier.fillMaxSize().clip(shape), contentAlignment = Alignment.Center) {
            when {
                imageUrl != null -> CachedAssetImage(url = imageUrl, fit = BoxFit.cover, modifier = Modifier.fillMaxSize())
                showFallbackText -> _AvatarInitials(initials, size, if (key == null && borderActive) activeColor else Color.White)
                else -> Unit
            }
        }
    }
}

/**
 * The Android fallback initials: the platform sans-serif face at a fixed
 * scale (Dart's `TextScaler.noScaling`), centred with the face's own ascent
 * and descent.
 */
@Composable
private fun _AvatarInitials(initials: String, size: Dp, color: Color) {
    val density = LocalDensity.current
    val base = LocalDefaultTextStyle.current
    // TextScaler.noScaling: undo the system font scale on every sp metric.
    val fontSize = with(density) { (size * 0.36f).toSp() }
    val letterSpacing =
        if (base.letterSpacing.type == TextUnitType.Sp) (base.letterSpacing.value / density.fontScale).sp else base.letterSpacing
    Text(
        initials,
        textAlign = TextAlign.Center,
        style =
            base.copy(
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.W700,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = letterSpacing,
            ),
    )
}

/** Flutter's `foregroundDecoration: BoxDecoration(border:)`: the border drawn over the content, inside the box. */
private fun Modifier.foregroundBorder(shape: Shape, color: Color, width: Dp): Modifier =
    this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = width.toPx()
        var ring: Path? = null
        var generic: Path? = null
        when (outline) {
            is Outline.Rectangle ->
                ring = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(outline.rect)
                    addRect(outline.rect.deflate(stroke))
                }
            is Outline.Rounded -> {
                val outer = outline.roundRect
                fun CornerRadius.shrink() = CornerRadius((x - stroke).coerceAtLeast(0f), (y - stroke).coerceAtLeast(0f))
                val inner =
                    RoundRect(
                        outer.left + stroke,
                        outer.top + stroke,
                        outer.right - stroke,
                        outer.bottom - stroke,
                        outer.topLeftCornerRadius.shrink(),
                        outer.topRightCornerRadius.shrink(),
                        outer.bottomRightCornerRadius.shrink(),
                        outer.bottomLeftCornerRadius.shrink(),
                    )
                ring = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRoundRect(outer)
                    addRoundRect(inner)
                }
            }
            is Outline.Generic -> generic = outline.path
        }
        onDrawWithContent {
            drawContent()
            ring?.let { drawPath(it, color) }
            generic?.let { path -> clipPath(path) { drawPath(path, color, style = Stroke(stroke * 2)) } }
        }
    }

@Composable
fun StatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    active: Boolean = false,
    danger: Boolean = false,
) {
    val color =
        when {
            danger -> UiColors.danger
            active -> UiColors.accent
            else -> UiColors.textMuted
        }
    Row(
        modifier
            .decoration(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp), color.copy(alpha = 0.45f))
            .padding(EdgeInsets.symmetric(horizontal = 9.dp, vertical = 5.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, size = 13.dp, color = color)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, style = uiTextStyle(color = color, fontSize = 12.0, fontWeight = FontWeight.W600))
    }
}
