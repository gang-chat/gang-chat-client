package com.gangchat.client.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gangchat.client.widgets.BoxShadow
import com.gangchat.client.widgets.Curves
import com.gangchat.client.widgets.DefaultTextStyle
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.IconThemeData
import com.gangchat.client.widgets.LocalIconTheme
import com.gangchat.client.widgets.TapRegionSurface
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Flutter font family names, kept for reference: Microsoft YaHei and its
// fallbacks are desktop faces that Android does not ship, so the client face
// resolves to the platform default (Roboto + Noto Sans CJK), exactly as the
// Flutter Android build rendered it.
const val kClientFontFamily: String = "Microsoft YaHei"
val kClientFontFamilyFallback: List<String> = listOf("Microsoft YaHei UI", "PingFang SC", "Noto Sans SC", "Noto Sans CJK SC")

/** Latin display face for headings and numerals (CJK falls back to the platform font). */
const val kDisplayFontFamily: String = "Space Grotesk"

/** Monospace face for labels, tags, timestamps and counters. */
const val kMonoFontFamily: String = "JetBrains Mono"

/** Dot-matrix face for the wordmark and big numerals. */
const val kDotFontFamily: String = "Doto"

/**
 * A font bundled in the APK assets (the repository's `assets/` folder, as
 * declared in `pubspec.yaml`). The typeface loads lazily from the font
 * resolver's context, so the families below are plain constants.
 */
private class UiAssetFont(
    val path: String,
    override val weight: FontWeight,
    override val style: FontStyle = FontStyle.Normal,
) : AndroidFont(FontLoadingStrategy.Blocking, Loader, FontVariation.Settings()) {
    override fun equals(other: Any?): Boolean =
        other is UiAssetFont && other.path == path && other.weight == weight && other.style == style

    override fun hashCode(): Int = java.util.Objects.hash(path, weight, style)

    override fun toString(): String = "UiAssetFont($path, $weight)"

    object Loader : TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? =
            Typeface.createFromAsset(context.assets, (font as UiAssetFont).path)

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? = loadBlocking(context, font)
    }
}

/** The app's font families (Flutter `fontFamily:` strings resolved to Compose families). */
object UiFonts {
    /** [kClientFontFamily]: the platform default on Android. */
    val client: FontFamily = FontFamily.Default

    /** [kDisplayFontFamily]. */
    val display: FontFamily =
        FontFamily(
            UiAssetFont("fonts/space_grotesk/SpaceGrotesk-Medium.ttf", FontWeight.W500),
            UiAssetFont("fonts/space_grotesk/SpaceGrotesk-Bold.ttf", FontWeight.W700),
        )

    /** [kMonoFontFamily]. */
    val mono: FontFamily =
        FontFamily(
            UiAssetFont("fonts/jetbrains_mono/JetBrainsMono-Medium.ttf", FontWeight.W500),
            UiAssetFont("fonts/jetbrains_mono/JetBrainsMono-Bold.ttf", FontWeight.W700),
        )

    /** [kDotFontFamily]. */
    val dot: FontFamily = FontFamily(UiAssetFont("fonts/doto/Doto-Bold.ttf", FontWeight.W800))
}

/**
 * "Signal" palette: true-black canvas, graphite layers and a single acid-lime
 * signal colour. Lime means *live or actionable* (primary actions, voice,
 * speaking, focus). White pills mean *selected*. Everything else stays in
 * greys so the signal colour always reads.
 */
object UiColors {
    // Neutral layers, darkest to lightest.
    val surfacePressed = Color(0xFF000000)
    val background = Color(0xFF060607)
    val surfaceLow = Color(0xFF0D0D0F)

    /** Grouping cards that sit on a [surfaceLow] panel. */
    val card = Color(0xFF131316)
    val surface = Color(0xFF17171A)
    val surfaceRaised = Color(0xFF1E1E22)
    val surfaceHover = Color(0xFF27272C)

    val selected = Color(0xFF1C220C)
    val border = Color(0xFF222226)
    val borderStrong = Color(0xFF323238)
    val accentBorder = Color(0xFF465418)
    val selectedBorder = Color(0xFF9FC72A)
    val dangerBorder = Color(0xFF5E1F21)
    val disabledSurface = Color(0xFF111113)
    val disabledBorder = Color(0xFF1C1C1F)

    /** Text-field wells sit slightly below the panel they live on. */
    val fieldFill = Color(0xFF09090A)
    val fieldBorder = Color(0xFF26262B)
    val fieldBorderHover = Color(0xFF3A3A41)

    val text = Color(0xFFF4F4F1)
    val textSecondary = Color(0xFFA3A3A8)
    val textMuted = Color(0xFF66666D)
    val onAccent = Color(0xFF0A0B05)

    /** Inverted "selected" pill (white surface, black text). */
    val inverse = Color(0xFFF4F4F1)
    val onInverse = Color(0xFF0A0A0B)

    /** Acid-lime signal colour for text and icons on dark surfaces. */
    val accent = Color(0xFFD4FF3F)

    /** Lime used for filled controls (primary buttons, toggles). */
    val accentStrong = Color(0xFFCCF43A)
    val accentSoft = Color(0xFF151A07)
    val controlAccent = Color(0xFF5CE1FF)
    val violet = Color(0xFFB69CFF)
    val amber = Color(0xFFFFB224)
    val danger = Color(0xFFFF4D4F)
    val dangerSurface = Color(0xFF250D0E)
    val success = Color(0xFFD4FF3F)
    val successSurface = Color(0xFF161C07)
    val successBorder = Color(0xFF465418)

    val presenceOnline = Color(0xFF4ADE80)
    val presenceOnlineSurface = Color(0xFF0D2015)
    val presenceOnlineBorder = Color(0xFF1E5534)
    val presenceOffline = Color(0xFF7C7C84)
    val presenceOfflineSurface = Color(0xFF18181B)
    val presenceOfflineBorder = Color(0xFF34343A)
    val presenceVoice = Color(0xFFD4FF3F)
    val presenceVoiceSurface = Color(0xFF161C07)
    val presenceVoiceBorder = Color(0xFF55661B)
    val presenceReconnecting = Color(0xFFFFB224)

    val roleMember = Color(0xFF5CB8FF)
    val roleMemberSurface = Color(0xFF0E1D2B)
    val roleMemberBorder = Color(0xFF24527A)
    val roleAdmin = Color(0xFFFFD84D)
    val roleAdminSurface = Color(0xFF261F08)
    val roleAdminBorder = Color(0xFF7A6420)
    val roleCreator = Color(0xFFFF8A3D)
    val roleCreatorSurface = Color(0xFF2A160A)
    val roleCreatorBorder = Color(0xFF8A4820)
    val roleSuperuser = Color(0xFFFF4D6A)
    val roleSuperuserSurface = Color(0xFF2B0D13)
    val roleSuperuserBorder = Color(0xFF8A2638)
}

/**
 * Interaction tints shared by every pressable control so hover and press
 * feedback reads identically across buttons, rows and cards.
 */
object UiStates {
    fun hover(base: Color): Color {
        if (base.alpha == 0f) return Color.White.copy(alpha = 0.05f)
        return lerp(base, Color.White, 0.055f)
    }

    fun pressed(base: Color): Color {
        if (base.alpha == 0f) return Color.White.copy(alpha = 0.03f)
        return lerp(base, Color.Black, 0.18f)
    }
}

object UiSpacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object UiRadii {
    val xs = 5.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 18.dp
    val xl = 26.dp
    val pill = 999.dp
}

object UiMotion {
    val fast: Duration = 120.milliseconds
    val standard: Duration = 180.milliseconds
    val slow: Duration = 260.milliseconds
    val curve = Curves.easeOutCubic
}

object UiShadows {
    val raised = listOf(BoxShadow(color = Color(0x40000000), blurRadius = 10.dp, offset = DpOffset(0.dp, 3.dp)))

    val popover =
        listOf(
            BoxShadow(color = Color(0x80000000), blurRadius = 32.dp, offset = DpOffset(0.dp, 14.dp)),
            BoxShadow(color = Color(0x33000000), blurRadius = 4.dp, offset = DpOffset(0.dp, 1.dp)),
        )

    val dialog = listOf(BoxShadow(color = Color(0x99000000), blurRadius = 56.dp, offset = DpOffset(0.dp, 24.dp)))

    fun glow(color: Color, strength: Float = 0.32f): List<BoxShadow> =
        listOf(BoxShadow(color = color.copy(alpha = strength), blurRadius = 18.dp, spreadRadius = (-2).dp))
}

/**
 * Flutter `TextStyle.height` keeps the leading split evenly above and below
 * the glyphs here: the app's root style is Material's `bodyMedium`, whose
 * `TextLeadingDistribution.even` every merged style inherits.
 */
internal val uiLineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * Builds a [TextStyle] with Flutter's `TextStyle` semantics: [height] is a
 * multiplier of the font size (kept when the size changes later, like
 * Flutter), sizes are logical pixels.
 */
fun uiTextStyle(
    color: Color = Color.Unspecified,
    fontFamily: FontFamily? = null,
    fontSize: Double? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: Double? = null,
    height: Double? = null,
    decoration: TextDecoration? = null,
    backgroundColor: Color = Color.Unspecified,
): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing?.sp ?: TextUnit.Unspecified,
        background = backgroundColor,
        textDecoration = decoration,
        lineHeight = height?.em ?: TextUnit.Unspecified,
        lineHeightStyle = if (height == null) null else uiLineHeightStyle,
    )

/** Flutter's `TextStyle.copyWith` (non-null arguments replace the field). */
fun TextStyle.copyWith(
    color: Color? = null,
    fontFamily: FontFamily? = null,
    fontSize: Double? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: Double? = null,
    height: Double? = null,
    decoration: TextDecoration? = null,
    backgroundColor: Color? = null,
): TextStyle =
    this.copy(
        color = color ?: this.color,
        fontFamily = fontFamily ?: this.fontFamily,
        fontSize = fontSize?.sp ?: this.fontSize,
        fontWeight = fontWeight ?: this.fontWeight,
        fontStyle = fontStyle ?: this.fontStyle,
        letterSpacing = letterSpacing?.sp ?: this.letterSpacing,
        lineHeight = height?.em ?: this.lineHeight,
        lineHeightStyle = if (height != null) uiLineHeightStyle else this.lineHeightStyle,
        textDecoration = decoration ?: this.textDecoration,
        background = backgroundColor ?: this.background,
    )

object UiTypography {
    /** Big display text (hero greetings, page titles). */
    val display =
        uiTextStyle(
            color = UiColors.text,
            fontFamily = UiFonts.display,
            fontSize = 30.0,
            fontWeight = FontWeight.W700,
            letterSpacing = -0.6,
            height = 1.15,
        )

    val headline =
        uiTextStyle(
            color = UiColors.text,
            fontFamily = UiFonts.display,
            fontSize = 21.0,
            fontWeight = FontWeight.W700,
            letterSpacing = -0.3,
            height = 1.25,
        )

    val title =
        uiTextStyle(
            color = UiColors.text,
            fontFamily = UiFonts.display,
            fontSize = 17.0,
            fontWeight = FontWeight.W700,
            letterSpacing = -0.2,
            height = 1.3,
        )

    val subtitle =
        uiTextStyle(
            color = UiColors.text,
            fontFamily = UiFonts.display,
            fontSize = 15.0,
            fontWeight = FontWeight.W700,
            letterSpacing = -0.1,
            height = 1.35,
        )

    val body = uiTextStyle(color = UiColors.text, fontSize = 14.0, height = 1.4)

    val label = uiTextStyle(color = UiColors.textSecondary, fontSize = 12.0, fontWeight = FontWeight.W500, height = 1.35)

    val caption = uiTextStyle(color = UiColors.textMuted, fontSize = 11.5, fontWeight = FontWeight.W500, height = 1.35)

    /**
     * Monospace uppercase label ("tag" look) for section headers, counters
     * and timestamps.
     */
    val overline =
        uiTextStyle(
            color = UiColors.textMuted,
            fontFamily = UiFonts.mono,
            fontSize = 10.5,
            fontWeight = FontWeight.W700,
            letterSpacing = 1.2,
            height = 1.3,
        )

    val mono =
        uiTextStyle(
            color = UiColors.textMuted,
            fontFamily = UiFonts.mono,
            fontSize = 11.0,
            fontWeight = FontWeight.W500,
            letterSpacing = 0.2,
            height = 1.3,
        )

    /** Dot-matrix numerals and wordmark. */
    val dot = uiTextStyle(color = UiColors.text, fontFamily = UiFonts.dot, fontSize = 28.0, fontWeight = FontWeight.W800, height = 1.0)
}

/** The Material 3 (2021) text styles the Flutter theme supplies, in the app's colours. */
@Immutable
class UiTextTheme(
    /** Material's default body text: the root `DefaultTextStyle` under a `Material`. */
    val bodyMedium: TextStyle,
    /** The base style every `TextField` merges its `style:` onto. */
    val bodyLarge: TextStyle,
    val titleMedium: TextStyle,
)

/** Flutter's `TooltipThemeData` as `uiTheme()` sets it. */
@Immutable
class UiTooltipTheme(
    val color: Color,
    val radius: Dp,
    val borderColor: Color,
    val shadows: List<BoxShadow>,
    val textStyle: TextStyle,
    val padding: EdgeInsets,
    val waitDuration: Duration,
    /** Flutter's default tooltip height on Android/iOS (`_getDefaultTooltipHeight`). */
    val height: Dp,
    val verticalOffset: Dp,
)

/**
 * The values of the Flutter `ThemeData` returned by Dart's `uiTheme()` that
 * the ported widgets read. Install it at the app root with [UiTheme].
 */
@Immutable
class UiThemeData(
    val textTheme: UiTextTheme,
    val textSelectionColors: TextSelectionColors,
    val cursorColor: Color,
    val tooltipTheme: UiTooltipTheme,
    val scaffoldBackgroundColor: Color,
    val canvasColor: Color,
    val dividerColor: Color,
    val dialogBackgroundColor: Color,
    val popupMenuColor: Color,
    val progressIndicatorColor: Color,
)

private val uiThemeData: UiThemeData by lazy {
    UiThemeData(
        textTheme =
            UiTextTheme(
                bodyMedium =
                    uiTextStyle(color = UiColors.text, fontFamily = UiFonts.client, fontSize = 14.0, fontWeight = FontWeight.W400, letterSpacing = 0.25, height = 1.43),
                bodyLarge =
                    uiTextStyle(color = UiColors.text, fontFamily = UiFonts.client, fontSize = 16.0, fontWeight = FontWeight.W400, letterSpacing = 0.5, height = 1.5),
                titleMedium =
                    uiTextStyle(color = UiColors.text, fontFamily = UiFonts.client, fontSize = 16.0, fontWeight = FontWeight.W500, letterSpacing = 0.15, height = 1.5),
            ),
        textSelectionColors =
            TextSelectionColors(handleColor = UiColors.accent, backgroundColor = UiColors.accentStrong.copy(alpha = 0.3f)),
        cursorColor = UiColors.accent,
        tooltipTheme =
            UiTooltipTheme(
                color = UiColors.surfaceHover,
                radius = UiRadii.sm,
                borderColor = UiColors.borderStrong,
                shadows = UiShadows.raised,
                textStyle = uiTextStyle(color = UiColors.text, fontFamily = UiFonts.client, fontSize = 12.0, fontWeight = FontWeight.W500),
                padding = EdgeInsets.symmetric(horizontal = 9.dp, vertical = 5.dp),
                waitDuration = 380.milliseconds,
                height = 32.dp,
                verticalOffset = 24.dp,
            ),
        scaffoldBackgroundColor = UiColors.background,
        canvasColor = UiColors.background,
        dividerColor = UiColors.border,
        dialogBackgroundColor = UiColors.surfaceLow,
        popupMenuColor = UiColors.surfaceRaised,
        progressIndicatorColor = UiColors.accent,
    )
}

/** Dart's `uiTheme()`: the app theme's values. */
fun uiTheme(): UiThemeData = uiThemeData

/**
 * The app's theme root (what `MaterialApp(theme: uiTheme())` provided):
 * Material's dark colour scheme for any Material component, no ripples or
 * focus/hover overlays (the kit draws its own states), the text selection
 * colours, the root `DefaultTextStyle` (Material's `bodyMedium`), the icon
 * theme, and a [TapRegionSurface] for `TapRegion`s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiTheme(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = uiTheme()
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = UiColors.accentStrong,
                onPrimary = UiColors.onAccent,
                secondary = UiColors.accent,
                surface = UiColors.surfaceLow,
                onSurface = UiColors.text,
                error = UiColors.danger,
                background = UiColors.background,
            ),
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides theme.textSelectionColors,
            LocalIconTheme provides IconThemeData(color = Color.White, size = 24.dp),
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
        ) {
            DefaultTextStyle(theme.textTheme.bodyMedium, merge = false) {
                TapRegionSurface(modifier) { content() }
            }
        }
    }
}

/** Flutter's `NoSplash.splashFactory` with transparent highlight/hover/focus colours. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = 0
}
