package com.gangchat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.boxShadow
import com.gangchat.client.widgets.decoration

/**
 * Shared color-swatch palette offered by every avatar picker. Mirrors the
 * keys understood by [avatarFallbackColor].
 */
val kAvatarPresetKeys: List<String> =
    listOf(
        "blue-3", "navy-2", "azure-2", "sky-2", "cyan-2", "turquoise-2", "mint-2", "emerald-2", "green-2",
        "forest-2", "lime-2", "yellow-2", "amber-2", "gold-2", "orange-2", "coral-2", "red-2", "crimson-2",
        "pink-2", "magenta-2", "violet-2", "purple-2", "lavender-2", "indigo-2", "rose-2", "teal-2", "olive-2",
        "brown-2", "sand-2", "slate-2", "steel-2", "gray-2", "graphite-2", "black-2",
    )

/** Test tag of the picker's preview (Dart's `ValueKey('avatar-picker-preview')`). */
const val avatarPickerPreviewTag = "avatar-picker-preview"

/**
 * A live circular avatar preview, a row of preset color swatches, and an
 * upload button. Used by both the account profile editor and the room
 * settings dialog so the two stay in sync.
 *
 * Tapping a swatch switches to that preset (callers handle the state change
 * through [onPresetSelected]); the upload button opens the image flow.
 *
 * [label] is the heading shown above the picker, e.g. '头像' or '房间图标';
 * [displayName] derives the fallback initials; [imageUrl] is the resolved URL
 * of the uploaded image, ignored while [usingPreset] is true; [rounded] gives
 * the rounded-square preview used for room icons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(
    label: String,
    displayName: String,
    imageUrl: String?,
    defaultAvatarKey: String,
    usingPreset: Boolean,
    uploading: Boolean,
    enabled: Boolean,
    onUpload: () -> Unit,
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImagePreview: (() -> Unit)? = null,
    presetKeys: List<String> = kAvatarPresetKeys,
    uploadLabel: String = "上传图片",
    rounded: Boolean = false,
) {
    val normalizedDefaultAvatarKey = normalizeAvatarPresetKey(defaultAvatarKey)
    Column(modifier.fillMaxWidth()) {
        Text(label, style = UiTypography.label)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            _AvatarPickerPreview(
                label = displayName,
                imageUrl = if (usingPreset) null else imageUrl,
                defaultAvatarKey = normalizedDefaultAvatarKey,
                size = 88.dp,
                onPreview = onImagePreview,
                rounded = rounded,
            )
            Spacer(Modifier.width(16.dp))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (key in presetKeys) {
                    _AvatarPickerSwatch(
                        keyName = key,
                        selected = usingPreset && key == normalizedDefaultAvatarKey,
                        onTap = if (enabled) ({ onPresetSelected(key) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            width = Dp.Infinity,
            height = 38.dp,
            tone = if (usingPreset) ButtonTone.neutral else ButtonTone.primary,
            selected = !usingPreset,
            loading = uploading,
            onPressed = if (enabled && !uploading) onUpload else null,
            icon = { Icon(Icons.Outlined.UploadFile) },
        ) {
            Text(uploadLabel)
        }
    }
}

@Composable
private fun _AvatarPickerPreview(
    label: String,
    imageUrl: String?,
    defaultAvatarKey: String,
    size: Dp,
    onPreview: (() -> Unit)?,
    rounded: Boolean = false,
) {
    val fallback: @Composable () -> Unit = {
        Avatar(label = label, defaultAvatarKey = defaultAvatarKey, size = size, showBorder = false, rounded = rounded)
    }
    val url = imageUrl
    val shape: Shape = if (rounded) RoundedCornerShape(size * 0.3f) else CircleShape
    val tappable = url != null && onPreview != null
    Box(
        Modifier
            .testTag(avatarPickerPreviewTag)
            .then(
                if (tappable) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = { onPreview?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            .size(size)
            .boxShadow(UiShadows.raised, shape)
            .decoration(Color.Transparent, shape, UiColors.border)
            .clip(shape),
        propagateMinConstraints = true,
    ) {
        if (url == null) {
            fallback()
        } else {
            CachedAssetImage(
                url = url,
                fit = BoxFit.cover,
                modifier = Modifier.fillMaxSize(),
                errorBuilder = { _, _ -> fallback() },
            )
        }
    }
}

@Composable
private fun _AvatarPickerSwatch(keyName: String, selected: Boolean, onTap: (() -> Unit)?) {
    UiTooltip(message = avatarPresetLabel(keyName)) {
        Box(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onTap != null,
                    role = Role.Button,
                    onClick = { onTap?.invoke() },
                )
                .size(24.dp)
                .then(if (selected) Modifier.boxShadow(UiShadows.glow(UiColors.inverse, strength = 0.35f), CircleShape) else Modifier)
                .decoration(avatarFallbackColor(keyName), CircleShape, if (selected) UiColors.inverse else Color.Transparent, 2.dp),
        )
    }
}
