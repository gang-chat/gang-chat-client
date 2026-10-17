package com.gangchat.client.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.liveParticipantTileState
import com.gangchat.client.app.liveUserDisplayName
import com.gangchat.client.app.maxParticipantVoiceVolume
import com.gangchat.client.app.normalizedParticipantVoiceVolume
import com.gangchat.client.app.participantVoiceVolumePercentText
import com.gangchat.client.app.roomRoleLabel
import com.gangchat.client.app.userAvatarLabel
import com.gangchat.client.live.LiveVideoTrack
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.LiveParticipant
import com.gangchat.client.protocol.PublicRoom
import com.gangchat.client.protocol.UserSummary
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.Avatar
import com.gangchat.client.ui.PressableSurface
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiRadii
import com.gangchat.client.ui.UiTooltip
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.roleBadgeForegroundColorForLabel
import com.gangchat.client.widgets.BoxFit
import com.gangchat.client.widgets.EdgeInsets
import com.gangchat.client.widgets.FittedBox
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import kotlin.math.floor
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.decoration
import com.gangchat.client.widgets.rememberBuildContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun _LiveMemberStage(
    loading: Boolean,
    participants: List<LiveParticipant>,
    currentUser: CurrentUser,
    localMicMuted: Boolean,
    localHeadphonesMuted: Boolean,
    speakingUserIds: Set<String>,
    liveKitMicMutedByParticipantId: Map<String, Boolean>,
    videoTracks: List<LiveVideoTrack>,
    stageTrack: LiveVideoTrack?,
    onSelectStage: (LiveVideoTrack) -> Unit,
    onSelectScreenShareStage: (String) -> Unit,
    onSelectCameraStage: (String) -> Unit,
    onToggleMic: (() -> Unit)?,
    onToggleHeadphones: () -> Unit,
    participantVoiceVolume: (String) -> Double,
    onParticipantVoiceVolumeChanged: (String, Double) -> Unit,
    onParticipantVoiceMuteToggled: (String) -> Unit,
    canModerateParticipant: (LiveParticipant) -> Boolean,
    onToggleParticipantMicModeration: (LiveParticipant) -> Unit,
    onToggleParticipantHeadphonesModeration: (LiveParticipant) -> Unit,
    canRemoveParticipant: (LiveParticipant) -> Boolean,
    onRemoveParticipant: (LiveParticipant) -> Unit,
    onResolveParticipantProfile: UserProfileResolver? = null,
    onResolveParticipantRoomProfile: RoomProfileResolver? = null,
    onEnterParticipantProfileRoom: ((PublicRoom) -> Unit)? = null,
    participantProfileActionBuilder: UserProfileActionBuilder? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = UiColors.accent, strokeWidth = 4.dp)
            }
            return@BoxWithConstraints
        }
        if (participants.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("语音频道里还没有人", style = UiTypography.body.copyWith(color = UiColors.textMuted))
            }
            return@BoxWithConstraints
        }

        val maxWidthValue = maxWidth.value.toDouble()
        val twoColumnCardWidth = (maxWidthValue - _memberCardSpacing) / 2
        val logicalCardDimension =
            if (twoColumnCardWidth >= _memberCardMinTwoColumnWidth) {
                twoColumnCardWidth.coerceIn(_memberCardMinTwoColumnWidth, _memberCardWidth)
            } else {
                maxWidthValue.coerceIn(0.0, _memberCardWidth)
            }
        // Flutter's Wrap lays out fractional sizes; Compose rounds each card
        // to whole pixels, so snap down to keep two cards on one row.
        val pixelRatio = LocalDensity.current.density.toDouble()
        val cardDimension = floor(logicalCardDimension * pixelRatio) / pixelRatio
        val minHeight = maxHeight

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = minHeight), contentAlignment = Alignment.TopStart) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(_memberCardSpacing.dp),
                    verticalArrangement = Arrangement.spacedBy(_memberCardSpacing.dp),
                ) {
                    for (participant in participants) {
                        val local = participant.user.id == currentUser.id
                        key(participant.user.id) {
                            _LiveMemberCard(
                                dimension = cardDimension,
                                participant =
                                    if (local) {
                                        participant.copyWith(
                                            micMuted = localMicMuted,
                                            headphonesMuted = localHeadphonesMuted,
                                            headphonesListening = !localHeadphonesMuted && !participant.headphonesBlocked,
                                        )
                                    } else {
                                        participant
                                    },
                                currentUser = currentUser,
                                local = local,
                                speaking = speakingUserIds.contains(participant.user.id),
                                liveKitMicMuted = if (local) localMicMuted else liveKitMicMutedByParticipantId[participant.user.id],
                                screenShareFocused = stageTrack?.identity == participant.user.id && stageTrack.isScreenShare,
                                cameraFocused = stageTrack?.identity == participant.user.id && !stageTrack.isScreenShare,
                                previewTrack = _memberPreviewTrack(tracks = videoTracks, userId = participant.user.id, stageTrack = stageTrack),
                                selectableTrack = _selectableTrack(tracks = videoTracks, userId = participant.user.id, stageTrack = stageTrack),
                                onSelectPreview = onSelectStage,
                                onSelectScreenShare = onSelectScreenShareStage,
                                onSelectCamera = onSelectCameraStage,
                                onToggleMic = onToggleMic,
                                onToggleHeadphones = onToggleHeadphones,
                                participantVoiceVolume = participantVoiceVolume,
                                onParticipantVoiceVolumeChanged = onParticipantVoiceVolumeChanged,
                                onParticipantVoiceMuteToggled = onParticipantVoiceMuteToggled,
                                canModerateParticipant = canModerateParticipant,
                                onToggleParticipantMicModeration = onToggleParticipantMicModeration,
                                onToggleParticipantHeadphonesModeration = onToggleParticipantHeadphonesModeration,
                                canRemoveParticipant = canRemoveParticipant,
                                onRemoveParticipant = onRemoveParticipant,
                                onResolveParticipantProfile = onResolveParticipantProfile,
                                onResolveParticipantRoomProfile = onResolveParticipantRoomProfile,
                                onEnterParticipantProfileRoom = onEnterParticipantProfileRoom,
                                participantProfileActionBuilder = participantProfileActionBuilder,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun _LiveMemberCard(
    dimension: Double,
    participant: LiveParticipant,
    currentUser: CurrentUser,
    local: Boolean,
    speaking: Boolean,
    liveKitMicMuted: Boolean?,
    screenShareFocused: Boolean,
    cameraFocused: Boolean,
    previewTrack: LiveVideoTrack?,
    selectableTrack: LiveVideoTrack?,
    onSelectPreview: (LiveVideoTrack) -> Unit,
    onSelectScreenShare: (String) -> Unit,
    onSelectCamera: (String) -> Unit,
    onToggleMic: (() -> Unit)?,
    onToggleHeadphones: () -> Unit,
    participantVoiceVolume: (String) -> Double,
    onParticipantVoiceVolumeChanged: (String, Double) -> Unit,
    onParticipantVoiceMuteToggled: (String) -> Unit,
    canModerateParticipant: (LiveParticipant) -> Boolean,
    onToggleParticipantMicModeration: (LiveParticipant) -> Unit,
    onToggleParticipantHeadphonesModeration: (LiveParticipant) -> Unit,
    canRemoveParticipant: (LiveParticipant) -> Boolean,
    onRemoveParticipant: (LiveParticipant) -> Unit,
    onResolveParticipantProfile: UserProfileResolver?,
    onResolveParticipantRoomProfile: RoomProfileResolver?,
    onEnterParticipantProfileRoom: ((PublicRoom) -> Unit)?,
    participantProfileActionBuilder: UserProfileActionBuilder?,
) {
    val context = rememberBuildContext()
    val state = liveParticipantTileState(participant, speaking = speaking, liveKitMicMuted = liveKitMicMuted)
    val name = liveUserDisplayName(participant.user)
    val nameColor = _liveMemberNameColor(participant.user, local = local)
    val androidLocalCameraTap: (() -> Unit)? =
        if (local && previewTrack != null && previewTrack.isLocal && !previewTrack.isScreenShare) {
            { onSelectPreview(previewTrack) }
        } else {
            null
        }
    val borderColor = if (state.highlighted) UiColors.borderStrong else UiColors.border
    val activityIcon = _participantMetaIcon(participant, speaking = speaking)
    val canModerate = !local && canModerateParticipant(participant)
    val activityTag: (@Composable () -> Unit)? =
        if (activityIcon == null) {
            null
        } else {
            {
                _LiveMemberActivityTag(
                    modifier = Modifier.testTag("live-member-activity:${participant.user.id}"),
                    label = _participantMeta(participant, speaking = speaking),
                    icon = activityIcon,
                    color = _participantMetaColor(participant, speaking = speaking),
                )
            }
        }
    val statusRow: @Composable (Modifier) -> Unit = { rowModifier ->
        _LiveMemberStatusRow(
            modifier = rowModifier,
            participant = participant,
            participantName = name,
            micMutedForDisplay = state.micMutedForDisplay,
            moderationControls = canModerate,
            onToggleMic =
                when {
                    local -> onToggleMic
                    canModerate -> ({ onToggleParticipantMicModeration(participant) })
                    else -> null
                },
            onToggleHeadphones =
                when {
                    local -> onToggleHeadphones
                    canModerate -> ({ onToggleParticipantHeadphonesModeration(participant) })
                    else -> null
                },
            voiceVolume = if (local) null else participantVoiceVolume(participant.user.id),
            onVoiceVolumeChanged = if (local) null else ({ volume -> onParticipantVoiceVolumeChanged(participant.user.id, volume) }),
            onVoiceVolumeToggle = if (local) null else ({ onParticipantVoiceMuteToggled(participant.user.id) }),
            onRemoveMember = if (!local && canRemoveParticipant(participant)) ({ onRemoveParticipant(participant) }) else null,
        )
    }
    val showScreenSharePreview =
        !screenShareFocused && (previewTrack?.isScreenShare == true || (participant.screenSharing && previewTrack == null))
    val showCameraPreview =
        !cameraFocused &&
            !showScreenSharePreview &&
            (previewTrack?.isScreenShare == false || (participant.cameraOn && previewTrack == null))
    val showMediaCard =
        showScreenSharePreview || showCameraPreview || (previewTrack != null && !screenShareFocused && !cameraFocused)
    val surfaceBackground = if (state.highlighted) _memberSpeakingBackground else _memberIdleBackground
    if (showMediaCard) {
        val preview: @Composable () -> Unit =
            when {
                showScreenSharePreview -> ({ _StoppedLiveMediaThumbnail(kind = _StoppedLiveMediaKind.screenShare) })
                showCameraPreview ->
                    if (previewTrack != null && !previewTrack.isScreenShare) {
                        { _LiveMemberVideo(track = previewTrack, mirrored = participant.cameraMirrored, androidLocalCameraTap = androidLocalCameraTap) }
                    } else {
                        { _StoppedLiveMediaThumbnail(kind = _StoppedLiveMediaKind.camera) }
                    }
                else -> ({ _LiveMemberVideo(track = previewTrack!!, mirrored = participant.cameraMirrored) })
            }
        _ScaledSurface(dimension) {
            PressableSurface(
                modifier = Modifier.testTag(liveMemberCardTag(participant.user.id)),
                width = _memberCardWidth.dp,
                height = _memberCardWidth.dp,
                borderRadius = UiRadii.lg,
                backgroundColor = surfaceBackground,
                selectedBackgroundColor = _memberSpeakingBackground,
                borderColor = borderColor,
                selectedBorderColor = UiColors.borderStrong,
                selected = state.highlighted,
                padding = EdgeInsets.zero,
                interactive = true,
                pressEffect = false,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = 8.dp, top = 30.dp, end = 8.dp, bottom = 46.dp)
                            ._liveTap {
                                if (showScreenSharePreview) {
                                    onSelectScreenShare(participant.user.id)
                                    return@_liveTap
                                }
                                if (showCameraPreview) {
                                    if (previewTrack != null && !previewTrack.isScreenShare) {
                                        onSelectPreview(previewTrack)
                                        return@_liveTap
                                    }
                                    onSelectCamera(participant.user.id)
                                    return@_liveTap
                                }
                                if (previewTrack != null) {
                                    onSelectPreview(previewTrack)
                                    return@_liveTap
                                }
                            }
                            .clip(RoundedCornerShape(UiRadii.md))
                            .background(UiColors.surfacePressed),
                        propagateMinConstraints = true,
                    ) {
                        preview()
                    }
                    Row(
                        Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = 9.dp, top = 8.dp, end = 9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = UiTypography.label.copyWith(color = nameColor, fontWeight = FontWeight.W600),
                        )
                        if (activityTag != null) {
                            Spacer(Modifier.width(6.dp))
                            activityTag()
                        }
                    }
                    statusRow(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp))
                    // Dart's IgnorePointer border overlay: a Compose node without
                    // pointer input is already transparent to hit testing.
                    Box(
                        Modifier
                            .matchParentSize()
                            .decoration(shape = RoundedCornerShape(UiRadii.lg), borderColor = borderColor),
                    )
                }
            }
        }
        return
    }
    _ScaledSurface(dimension) {
        PressableSurface(
            modifier = Modifier.testTag(liveMemberCardTag(participant.user.id)),
            width = _memberCardWidth.dp,
            height = _memberCardWidth.dp,
            borderRadius = UiRadii.lg,
            backgroundColor = surfaceBackground,
            selectedBackgroundColor = _memberSpeakingBackground,
            borderColor = borderColor,
            selectedBorderColor = UiColors.borderStrong,
            selected = state.highlighted,
            padding = EdgeInsets.zero,
            onPressed = if (selectableTrack == null) ({}) else ({ onSelectPreview(selectableTrack) }),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (activityTag != null) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 9.dp)) { activityTag() }
                }
                Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 33.dp, end = 12.dp, bottom = 8.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        UserHoverCard(
                            user = participant.user,
                            currentUser = currentUser,
                            onResolveProfile = onResolveParticipantProfile,
                            onResolveRoomProfile = onResolveParticipantRoomProfile,
                            onEnterCommonRoom = onEnterParticipantProfileRoom,
                            profileActionBuilder = participantProfileActionBuilder,
                            inLive = true,
                            showRoomRole = true,
                        ) {
                            Avatar(
                                label = userAvatarLabel(participant.user),
                                imageUrl = AppConfigScope.of(context).resolveAssetUrl(participant.user.avatarUrl),
                                defaultAvatarKey = participant.user.defaultAvatarKey,
                                size = 42.dp,
                                showBorder = false,
                                modifier = Modifier.testTag(liveMemberAvatarTag(participant.user.id)),
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        name,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        style = UiTypography.body.copyWith(color = nameColor, fontWeight = FontWeight.W600),
                    )
                    Spacer(Modifier.weight(1f))
                    statusRow(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Test tag of a member card's 42 dp avatar. */
fun liveMemberAvatarTag(userId: String) = "live-member-avatar:$userId"

/** Test tag of a member card's surface (Dart finds it as the card's `PressableSurface`). */
fun liveMemberCardTag(userId: String) = "live-member-card:$userId"

/**
 * Dart's `_scaledSurface`: lays the card out at its 154 dp design size and
 * paints it scaled to [dimension] (a `FittedBox` filling a `SizedBox`).
 */
@Composable
private fun _ScaledSurface(dimension: Double, child: @Composable () -> Unit) {
    val scale = dimension / _memberCardWidth
    FittedBox(
        modifier = Modifier.size(dimension.dp, (_memberCardSurfaceHeight * scale).dp),
        fit = BoxFit.contain,
        alignment = Alignment.TopStart,
    ) {
        Box(Modifier.size(_memberCardWidth.dp, _memberCardSurfaceHeight.dp), propagateMinConstraints = true) { child() }
    }
}

@Composable
private fun _LiveMemberActivityTag(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    UiTooltip(message = label, modifier = modifier) {
        Box(Modifier.size(24.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Icon(icon, color = color, size = 15.dp)
        }
    }
}

@Composable
private fun _LiveMemberStatusRow(
    participant: LiveParticipant,
    participantName: String,
    micMutedForDisplay: Boolean,
    moderationControls: Boolean,
    modifier: Modifier = Modifier,
    onToggleMic: (() -> Unit)? = null,
    onToggleHeadphones: (() -> Unit)? = null,
    voiceVolume: Double? = null,
    onVoiceVolumeChanged: ((Double) -> Unit)? = null,
    onVoiceVolumeToggle: (() -> Unit)? = null,
    onRemoveMember: (() -> Unit)? = null,
) {
    val listening = participant.headphonesListening && !participant.headphonesMuted && !participant.headphonesBlocked
    val micModerated = participant.micBlocked || participant.voiceBlocked
    val headphonesModerated = participant.headphonesBlocked
    val userId = participant.user.id
    val buttons = mutableListOf<@Composable () -> Unit>()
    buttons += {
        _LiveMemberStatusButton(
            modifier = Modifier.testTag("live-member-status:mic:$userId"),
            icon = if (micMutedForDisplay) Icons.Filled.MicOff else Icons.Filled.Mic,
            active = !micMutedForDisplay,
            danger = micModerated,
            onPressed = onToggleMic,
            tooltip =
                _liveMemberMicTooltip(
                    micMutedForDisplay = micMutedForDisplay,
                    micModerated = micModerated,
                    headphonesModerated = headphonesModerated,
                    moderationControls = moderationControls,
                ),
        )
    }
    buttons += {
        _LiveMemberStatusButton(
            modifier = Modifier.testTag("live-member-status:headphones:$userId"),
            icon = if (listening) Icons.Filled.Headphones else Icons.Filled.HeadsetOff,
            active = listening,
            danger = headphonesModerated,
            onPressed = onToggleHeadphones,
            tooltip =
                _liveMemberHeadphonesTooltip(
                    listening = listening,
                    headphonesModerated = headphonesModerated,
                    moderationControls = moderationControls,
                ),
        )
    }
    if (voiceVolume != null && onVoiceVolumeChanged != null) {
        buttons += {
            val toggleLabel = _memberVoiceVolumeToggleLabel(participantName = participantName, voiceVolume = voiceVolume)
            _HoverVolumeButton(
                modifier = Modifier.testTag("live-member-status:voice-volume:$userId"),
                value = voiceVolume,
                semanticLabel = "${participantName}语音音量",
                infoMessage = toggleLabel,
                onChanged = onVoiceVolumeChanged,
                maxValue = maxParticipantVoiceVolume,
                valueFormatter = ::participantVoiceVolumePercentText,
                panelWidth = _memberStatusButtonDimension,
                panelHeight = _memberVoiceVolumePanelHeight(_memberStatusButtonDimension),
                childUsesTapGesture = true,
            ) {
                _LiveMemberStatusButton(
                    icon = _memberVoiceVolumeIcon(voiceVolume),
                    active = voiceVolume > 0,
                    tooltip = toggleLabel,
                    onPressed = onVoiceVolumeToggle,
                    showHoverInfo = false,
                )
            }
        }
    }
    if (onRemoveMember != null) {
        buttons += {
            _LiveMemberStatusButton(
                modifier = Modifier.testTag("live-member-status:kick:$userId"),
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                active = false,
                danger = true,
                tooltip = "踢出语音频道",
                onPressed = onRemoveMember,
            )
        }
    }

    BoxWithConstraints(modifier) {
        val dimension = maxWidth / 4
        val rowWidth = dimension * buttons.size
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.size(rowWidth, dimension), horizontalArrangement = Arrangement.Center) {
                for (button in buttons) {
                    Box(Modifier.size(dimension), propagateMinConstraints = true) { button() }
                }
            }
        }
    }
}

@Composable
private fun _LiveMemberStatusButton(
    icon: ImageVector,
    active: Boolean,
    tooltip: String,
    modifier: Modifier = Modifier,
    onPressed: (() -> Unit)? = null,
    danger: Boolean = false,
    showHoverInfo: Boolean = true,
) {
    val foreground =
        when {
            danger -> UiColors.danger
            active -> UiColors.accent
            else -> UiColors.textMuted
        }
    val enabled = onPressed != null
    val button: @Composable (Modifier) -> Unit = { buttonModifier ->
        Box(
            buttonModifier
                ._liveTap(onPressed)
                .semantics {
                    role = Role.Button
                    if (!enabled) disabled()
                    selected = active
                    contentDescription = tooltip
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, color = foreground, size = 13.dp, modifier = Modifier.semantics { liveIconTint = foreground })
        }
    }
    if (!showHoverInfo) {
        button(modifier)
        return
    }
    _HoverInfo(message = tooltip, modifier = modifier) { button(Modifier) }
}

internal fun _memberVoiceVolumeIcon(volume: Double): ImageVector = _volumeLevelIcon(normalizedParticipantVoiceVolume(volume))

private fun _memberVoiceVolumeToggleLabel(participantName: String, voiceVolume: Double): String =
    if (voiceVolume <= 0) "取消静音$participantName" else "静音$participantName"

internal const val _memberStatusButtonDimension: Double = (_memberCardWidth - 24.0) / 4

private fun _memberVoiceVolumePanelHeight(buttonDimension: Double): Double = _hoverVolumePanelHeight * buttonDimension / _controlButtonSize

private fun _liveMemberMicTooltip(
    micMutedForDisplay: Boolean,
    micModerated: Boolean,
    @Suppress("UNUSED_PARAMETER") headphonesModerated: Boolean,
    moderationControls: Boolean,
): String {
    if (moderationControls) {
        return if (micModerated) "取消麦克风静音" else "麦克风静音"
    }
    if (micModerated) return "已被麦克风静音"
    return if (micMutedForDisplay) "麦克风关闭" else "麦克风开启"
}

private fun _liveMemberHeadphonesTooltip(listening: Boolean, headphonesModerated: Boolean, moderationControls: Boolean): String {
    if (moderationControls) {
        return if (headphonesModerated) "取消耳机静音" else "耳机静音"
    }
    if (headphonesModerated) return "已被耳机静音"
    return if (listening) "正在收听" else "已关闭收听"
}

private fun _liveMemberNameColor(user: UserSummary, local: Boolean): Color {
    if (local) return UiColors.accent
    return roleBadgeForegroundColorForLabel(roomRoleLabel(user))
}

private fun _participantMetaColor(participant: LiveParticipant, speaking: Boolean): Color {
    if (participant.screenSharing || participant.cameraOn || speaking) return UiColors.accent
    if (participant.micMuted) return UiColors.textMuted
    return UiColors.textSecondary
}

private fun _participantMetaIcon(participant: LiveParticipant, speaking: Boolean): ImageVector? {
    if (participant.screenSharing) return Icons.AutoMirrored.Outlined.ScreenShare
    if (participant.cameraOn) return Icons.Filled.Videocam
    if (speaking) return Icons.Filled.Mic
    return null
}

@Composable
private fun _LiveMemberVideo(track: LiveVideoTrack, mirrored: Boolean, androidLocalCameraTap: (() -> Unit)? = null) =
    StatefulWidget(_LiveMemberVideoWidget(track, mirrored, androidLocalCameraTap)) { _LiveMemberVideoState() }

private class _LiveMemberVideoWidget(val track: LiveVideoTrack, val mirrored: Boolean, val androidLocalCameraTap: (() -> Unit)?)

private class _LiveMemberVideoState : ViewState<_LiveMemberVideoWidget>() {
    private var _hovered = false

    @Composable
    override fun build() {
        val track = widget.track
        val opacity by animateFloatAsState(if (_hovered) 1f else 0f, tween(120), label = "memberVideoHover")
        Box(
            Modifier
                .fillMaxSize()
                .testTag("live-member:video-thumbnail:${track.identity}:${track.isScreenShare}")
                ._liveMouseHover(onEnter = { setState { _hovered = true } }, onExit = { setState { _hovered = false } }),
        ) {
            val videoModifier = if (track.isScreenShare) Modifier.fillMaxSize() else Modifier.fillMaxSize().clip(RoundedCornerShape(UiRadii.lg))
            Box(videoModifier, propagateMinConstraints = true) { _LiveMediaVideo(track = track, mirrored = widget.mirrored) }
            Box(
                Modifier
                    .matchParentSize()
                    .testTag("live-member:video-hover:${track.identity}:${track.isScreenShare}")
                    .semantics { liveOpacity = if (_hovered) 1f else 0f }
                    .alpha(opacity)
                    .background(Color.Black.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, color = Color.White.copy(alpha = 0.92f), size = 32.dp)
            }
            val tap = widget.androidLocalCameraTap
            if (tap != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .testTag("live-member:android-local-camera-tap")
                        .semantics {
                            role = Role.Button
                            contentDescription = "预览自己的摄像头"
                        }
                        ._liveTap(tap),
                )
            }
        }
    }
}

private enum class _StoppedLiveMediaKind { camera, screenShare }

@Composable
private fun _StoppedLiveMediaThumbnail(kind: _StoppedLiveMediaKind) =
    StatefulWidget(_StoppedLiveMediaThumbnailWidget(kind)) { _StoppedLiveMediaThumbnailState() }

private class _StoppedLiveMediaThumbnailWidget(val kind: _StoppedLiveMediaKind)

private class _StoppedLiveMediaThumbnailState : ViewState<_StoppedLiveMediaThumbnailWidget>() {
    private var _hovered = false

    @Composable
    override fun build() {
        val isCamera = widget.kind == _StoppedLiveMediaKind.camera
        val opacity by animateFloatAsState(if (_hovered) 1f else 0f, tween(120), label = "stoppedThumbnailHover")
        Box(
            Modifier
                .fillMaxSize()
                .testTag(if (isCamera) "live-member:camera-thumbnail" else "live-member:screen-share-thumbnail")
                ._liveMouseHover(onEnter = { setState { _hovered = true } }, onExit = { setState { _hovered = false } }),
        ) {
            Box(Modifier.matchParentSize().background(UiColors.surfacePressed), contentAlignment = Alignment.Center) {
                Icon(if (isCamera) Icons.Outlined.Videocam else Icons.AutoMirrored.Outlined.ScreenShare, color = UiColors.textMuted, size = 30.dp)
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.32f)))
            Box(Modifier.matchParentSize().alpha(opacity), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Search, color = Color.White.copy(alpha = 0.92f), size = 32.dp)
            }
        }
    }
}

private fun _participantMeta(participant: LiveParticipant, speaking: Boolean): String {
    if (participant.screenSharing) return "正在共享屏幕"
    if (participant.cameraOn) return "摄像头已开启"
    if (participant.micMuted) return "已静音"
    if (speaking) return "正在说话"
    return "正在收听"
}

internal fun _volumeLevelIcon(volume: Double): ImageVector {
    if (volume <= 0) return Icons.AutoMirrored.Filled.VolumeOff
    if (volume < 0.5) return Icons.AutoMirrored.Filled.VolumeDown
    return Icons.AutoMirrored.Filled.VolumeUp
}
