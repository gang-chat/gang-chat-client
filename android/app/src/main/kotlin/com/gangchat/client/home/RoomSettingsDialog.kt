package com.gangchat.client.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gangchat.client.app.RoomsController
import com.gangchat.client.app.StickerManagementCapabilities
import com.gangchat.client.app.StickerManagementScope
import com.gangchat.client.app.defaultStickerPackName
import com.gangchat.client.app.normalizeRoomJoinPolicy
import com.gangchat.client.app.normalizeRoomNotificationPolicy
import com.gangchat.client.app.normalizeRoomVisibility
import com.gangchat.client.app.roomAccessState
import com.gangchat.client.app.roomCreatedAtLabel
import com.gangchat.client.app.roomDeletionConfirmationSpec
import com.gangchat.client.app.roomDisplayName
import com.gangchat.client.app.roomIdentifier
import com.gangchat.client.app.roomInfoSavedNotice
import com.gangchat.client.app.roomInfoUpdateDraftFromForm
import com.gangchat.client.app.roomLeaveConfirmationSpec
import com.gangchat.client.app.roomManagementAvatarPath
import com.gangchat.client.app.roomManagementPermissionState
import com.gangchat.client.app.roomPersonalPreferencesSavedNotice
import com.gangchat.client.app.roomProfileUpdateDraftFromForm
import com.gangchat.client.app.userFacingErrorMessage
import com.gangchat.client.config.AppConfig
import com.gangchat.client.foundation.DateTime
import com.gangchat.client.foundation.unawaited
import com.gangchat.client.protocol.CurrentUser
import com.gangchat.client.protocol.DownloadedFile
import com.gangchat.client.protocol.LiveState
import com.gangchat.client.protocol.RoomDetail
import com.gangchat.client.protocol.RoomMembership
import com.gangchat.client.protocol.StickerPack
import com.gangchat.client.ui.AppConfigScope
import com.gangchat.client.ui.AvatarPicker
import com.gangchat.client.ui.Button
import com.gangchat.client.ui.ButtonIcon
import com.gangchat.client.ui.ButtonTone
import com.gangchat.client.ui.DialogFrame
import com.gangchat.client.ui.FloatingNotice
import com.gangchat.client.ui.FloatingNoticeEmitter
import com.gangchat.client.ui.FloatingNoticeTone
import com.gangchat.client.ui.Input
import com.gangchat.client.ui.ReadOnlyTextBox
import com.gangchat.client.ui.ResponsiveDialogAction
import com.gangchat.client.ui.Segment
import com.gangchat.client.ui.SegmentedControl
import com.gangchat.client.ui.SettingsCard
import com.gangchat.client.ui.SettingsList
import com.gangchat.client.ui.StickerImagePreviewOpener
import com.gangchat.client.ui.StickerManagerBackend
import com.gangchat.client.ui.StickerManagerPanel
import com.gangchat.client.ui.UiColors
import com.gangchat.client.ui.UiTypography
import com.gangchat.client.ui.copyWith
import com.gangchat.client.ui.kDefaultAvatarPresetKey
import com.gangchat.client.widgets.BuildContext
import com.gangchat.client.widgets.Icon
import com.gangchat.client.widgets.Navigator
import com.gangchat.client.widgets.StatefulWidget
import com.gangchat.client.widgets.Text
import com.gangchat.client.widgets.TextEditingController
import com.gangchat.client.widgets.ViewState
import com.gangchat.client.widgets.showDialog
import kotlinx.coroutines.CancellationException

internal enum class _RoomSettingsDialogMode { create, edit }

/** 房间设置的分段:房间信息、个人偏好与房间资源管理。 */
enum class RoomSettingsSection {
    info,
    preferences,
    messageHistory,
    stickers,
    playlists,
}

// Test tags of the settings fields (Dart's `ValueKey`s).
const val roomSettingsNameInputTag = "room-settings-name-input"
const val roomSettingsDescriptionInputTag = "room-settings-description-input"
const val roomSettingsRidTag = "room-settings-rid"
const val roomSettingsCreatedAtTag = "room-settings-created-at"
const val roomSettingsVisibilitySegmentedTag = "room-settings-visibility-segmented"
const val roomSettingsJoinPolicySegmentedTag = "room-settings-join-policy-segmented"
const val roomSettingsGangBotToggleTag = "room-settings-gang-bot-toggle"
const val roomSettingsRemarkNameInputTag = "room-settings-remark-name-input"
const val roomSettingsRoomDisplayNameInputTag = "room-settings-room-display-name-input"
const val roomSettingsNotificationPolicySegmentedTag = "room-settings-notification-policy-segmented"

/**
 * Room settings: room info (name, description, icon, visibility, join
 * policy, gang-bot), personal preferences, message history, room stickers
 * and playlists, plus leaving and deleting the room. In create mode (see
 * [RoomSettingsDialog.create]) only the info form shows and saving creates
 * the room.
 *
 * Embedded (the home shell) it is a page that reports through [onResult] and
 * [onClose]; otherwise it is a dialog popped with its [RoomManagementResult].
 */
@Composable
fun RoomSettingsDialog(
    controller: RoomsController,
    room: RoomDetail,
    currentUser: CurrentUser,
    isInLive: Boolean,
    onLeaveLive: suspend () -> Unit,
    onRoomUpdated: (RoomDetail) -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onClose: (() -> Unit)? = null,
    onResult: ((RoomManagementResult) -> Unit)? = null,
    stickerImagePreviewOpener: StickerImagePreviewOpener? = null,
    messageHistoryBuilder: (@Composable (BuildContext) -> Unit)? = null,
    musicPlaylistsBuilder: (@Composable (BuildContext) -> Unit)? = null,
    initialSection: RoomSettingsSection = RoomSettingsSection.info,
    createMode: Boolean = false,
) = StatefulWidget(
    RoomSettingsDialogWidget(
        controller, room, currentUser, isInLive, onLeaveLive, onRoomUpdated, embedded, onClose, onResult,
        stickerImagePreviewOpener, messageHistoryBuilder, musicPlaylistsBuilder, initialSection,
        if (createMode) _RoomSettingsDialogMode.create else _RoomSettingsDialogMode.edit, modifier,
    ),
) { _RoomSettingsDialogState() }

/** Dart's `RoomSettingsDialog.create(...)` factory. */
object RoomSettingsDialog {
    @Composable
    fun create(
        controller: RoomsController,
        currentUser: CurrentUser,
        modifier: Modifier = Modifier,
        embedded: Boolean = false,
        onClose: (() -> Unit)? = null,
        onResult: ((RoomManagementResult) -> Unit)? = null,
        stickerImagePreviewOpener: StickerImagePreviewOpener? = null,
    ) {
        RoomSettingsDialog(
            controller = controller,
            room = _draftCreateRoom(currentUser),
            currentUser = currentUser,
            isInLive = false,
            onLeaveLive = {},
            onRoomUpdated = {},
            modifier = modifier,
            embedded = embedded,
            onClose = onClose,
            onResult = onResult,
            stickerImagePreviewOpener = stickerImagePreviewOpener,
            createMode = true,
        )
    }
}

class RoomSettingsDialogWidget internal constructor(
    val controller: RoomsController,
    val room: RoomDetail,
    val currentUser: CurrentUser,
    val isInLive: Boolean,
    val onLeaveLive: suspend () -> Unit,
    val onRoomUpdated: (RoomDetail) -> Unit,
    val embedded: Boolean,
    val onClose: (() -> Unit)?,
    val onResult: ((RoomManagementResult) -> Unit)?,
    val stickerImagePreviewOpener: StickerImagePreviewOpener?,
    val messageHistoryBuilder: (@Composable (BuildContext) -> Unit)?,
    val musicPlaylistsBuilder: (@Composable (BuildContext) -> Unit)?,
    val initialSection: RoomSettingsSection,
    internal val _mode: _RoomSettingsDialogMode,
    val modifier: Modifier,
)

private class _RoomSettingsDialogState : ViewState<RoomSettingsDialogWidget>() {
    private lateinit var _room: RoomDetail
    private lateinit var _nameController: TextEditingController
    private lateinit var _descriptionController: TextEditingController
    private lateinit var _remarkNameController: TextEditingController
    private lateinit var _roomDisplayNameController: TextEditingController
    private var _visibility = ""
    private var _joinPolicy = ""
    private var _aiVoiceAnnouncementsEnabled = false
    private var _gangBotEnabled = false
    private var _notificationPolicy = ""
    private var _isPinned = false

    private var _defaultAvatarKey = ""
    private var _usingPresetAvatar = true
    private var _pendingAvatarAssetId: String? = null
    private var _pendingAvatarUrl: String? = null
    private var _uploadingAvatar = false

    private var _saving = false
    private var _savingPreferences = false
    private var _refreshing = false
    private var _leaving = false
    private var _deleting = false
    private var _changed = false
    private var _error: String? = null
    private var _notice: String? = null
    private var _floatingNoticeSerial = 0
    private val _floatingNoticeEventKeys = HashMap<String, Int>()

    private val _creating: Boolean get() = widget._mode == _RoomSettingsDialogMode.create

    private lateinit var _section: RoomSettingsSection

    private val _canManageRoom: Boolean
        get() = _creating || roomAccessState(room = _room, currentUser = widget.currentUser).canManageRoom

    private val _canDeleteRoom: Boolean
        get() = !_creating && roomManagementPermissionState(room = _room, currentUser = widget.currentUser).canDeleteRoom

    private val _avatarDisplayName: String
        get() {
            val name = _nameController.text.trim()
            if (name.isNotEmpty()) return name
            return if (_creating) "房间" else roomDisplayName(_room)
        }

    private val _currentUserDefaultDisplayName: String
        get() {
            val displayName = widget.currentUser.displayName.trim()
            if (displayName.isNotEmpty()) return displayName
            return widget.currentUser.username
        }

    override fun initState() {
        _nameController = TextEditingController()
        _descriptionController = TextEditingController()
        _remarkNameController = TextEditingController()
        _roomDisplayNameController = TextEditingController()
        _resetFromWidgetRoom(clearTransientFeedback = false)
    }

    override fun didUpdateWidget(oldWidget: RoomSettingsDialogWidget) {
        if (oldWidget._mode != widget._mode || oldWidget.room.id != widget.room.id) {
            _resetFromWidgetRoom()
        }
    }

    private fun _resetFromWidgetRoom(clearTransientFeedback: Boolean = true) {
        _room = widget.room
        _nameController.text = _room.name
        _descriptionController.text = _room.description
        _visibility = normalizeRoomVisibility(_room.visibility)
        _joinPolicy = normalizeRoomJoinPolicy(_room.joinPolicy)
        _aiVoiceAnnouncementsEnabled = _room.aiVoiceAnnouncementsEnabled
        _gangBotEnabled = _room.gangBotEnabled
        _notificationPolicy = normalizeRoomNotificationPolicy(_room.notificationPolicy)
        _isPinned = _room.isPinned
        _remarkNameController.text = _room.remarkName ?: ""
        _roomDisplayNameController.text = _room.personalProfile.displayName ?: ""
        _defaultAvatarKey = _room.defaultAvatarKey
        _usingPresetAvatar = _room.avatarUrl == null
        _pendingAvatarAssetId = null
        _pendingAvatarUrl = null
        _uploadingAvatar = false
        _saving = false
        _savingPreferences = false
        _refreshing = false
        _leaving = false
        _deleting = false
        _changed = false
        _section = if (_creating) RoomSettingsSection.info else widget.initialSection
        if (clearTransientFeedback) {
            _error = null
            _notice = null
        }
    }

    override fun dispose() {
        _nameController.dispose()
        _descriptionController.dispose()
        _remarkNameController.dispose()
        _roomDisplayNameController.dispose()
    }

    private fun _close() {
        if (widget.embedded) {
            if (!_creating && _changed) widget.onResult?.invoke(RoomManagementResult.updated(_room))
            widget.onClose?.invoke()
            return
        }
        Navigator.of(context).pop(if (!_creating && _changed) RoomManagementResult.updated(_room) else null)
    }

    private fun _emitResult(result: RoomManagementResult) {
        if (widget.embedded) {
            widget.onResult?.invoke(result)
            widget.onClose?.invoke()
            return
        }
        Navigator.of(context).pop(result)
    }

    private fun _markFloatingNoticeEvent(channel: String, message: String?) {
        if (message == null || message.trim().isEmpty()) return
        _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial
    }

    private fun _floatingNoticeEventKey(channel: String): Any? = _floatingNoticeEventKeys[channel]

    private fun _floatingNotices(): List<FloatingNotice> =
        buildList {
            val notice = _notice
            if (notice != null) {
                add(FloatingNotice(message = notice, tone = FloatingNoticeTone.success, eventKey = _floatingNoticeEventKey("notice")))
            }
            val error = _error
            if (error != null) {
                add(FloatingNotice(message = error, tone = FloatingNoticeTone.error, duration = null, eventKey = _floatingNoticeEventKey("error")))
            }
        }

    private suspend fun _save() {
        if (_saving || _savingPreferences || _leaving || _deleting || !_canManageRoom) return
        val draft =
            roomInfoUpdateDraftFromForm(
                name = _nameController.text,
                description = _descriptionController.text,
                visibility = _visibility,
                joinPolicy = _joinPolicy,
                pendingAvatarAssetId = _pendingAvatarAssetId,
                usingPresetAvatar = _usingPresetAvatar,
                defaultAvatarKey = _defaultAvatarKey,
            )
        if (!draft.isValid) {
            setState {
                _error = draft.error
                _notice = null
                _markFloatingNoticeEvent("error", _error)
            }
            return
        }
        try {
            setState { _saving = true }
            val confirmedJoinPolicyChange = _confirmJoinPolicyAutoReviewIfNeeded(draft.joinPolicy)
            if (!mounted) return
            if (!confirmedJoinPolicyChange) {
                setState {
                    _joinPolicy = "approval_required"
                    _saving = false
                    _error = null
                    _notice = null
                }
                return
            }
            val updated =
                if (_creating) {
                    widget.controller.createRoom(
                        name = draft.name!!,
                        description = draft.description,
                        visibility = draft.visibility,
                        joinPolicy = draft.joinPolicy,
                        avatarAssetId = draft.avatarAssetId,
                        defaultAvatarKey = draft.defaultAvatarKey,
                    )
                } else {
                    widget.controller.updateRoom(
                        roomId = _room.id,
                        name = draft.name,
                        description = draft.description,
                        visibility = draft.visibility,
                        joinPolicy = draft.joinPolicy,
                        gangBotEnabled = if (_room.gangBotAvailable) _gangBotEnabled else null,
                        avatarAssetId = draft.avatarAssetId,
                        defaultAvatarKey = draft.defaultAvatarKey,
                    )
                }
            if (!mounted) return
            if (_creating) {
                _emitResult(RoomManagementResult.created(updated))
                return
            }
            setState {
                _room = updated
                _gangBotEnabled = updated.gangBotEnabled
                _notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy)
                _defaultAvatarKey = updated.defaultAvatarKey
                _usingPresetAvatar = updated.avatarUrl == null
                _pendingAvatarAssetId = null
                _pendingAvatarUrl = null
                _saving = false
                _changed = true
                _error = null
                _notice = roomInfoSavedNotice()
                _markFloatingNoticeEvent("notice", _notice)
            }
            widget.onRoomUpdated(updated)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _saving = false
                _error = userFacingErrorMessage(error)
                _notice = null
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _confirmJoinPolicyAutoReviewIfNeeded(nextJoinPolicy: String?): Boolean {
        if (_creating) return true
        val currentPolicy = normalizeRoomJoinPolicy(_room.joinPolicy)
        val normalizedNext = normalizeRoomJoinPolicy(nextJoinPolicy ?: currentPolicy)
        if (currentPolicy != "approval_required") return true
        if (normalizedNext != "open" && normalizedNext != "closed") return true

        val pendingRequests = widget.controller.listJoinRequests(_room.id)
        if (!mounted || pendingRequests.isEmpty()) return true

        val decisionLabel = if (normalizedNext == "open") "批准" else "拒绝"
        val confirmed =
            showDialog<Boolean>(context) { dialogContext ->
                DialogFrame(
                    title = "确认修改加入方式？",
                    icon = Icons.Outlined.WarningAmber,
                    adaptiveActions =
                        listOf(
                            ResponsiveDialogAction(label = "取消", onPressed = { Navigator.of(dialogContext).pop(false) }),
                            ResponsiveDialogAction(
                                label = "确认修改",
                                tone = ButtonTone.primary,
                                onPressed = { Navigator.of(dialogContext).pop(true) },
                            ),
                        ),
                    child = {
                        Text(
                            "当前仍有 ${pendingRequests.size} 个未处理申请。" + "确认修改后，将自动${decisionLabel}所有未处理申请。",
                            style = UiTypography.body.copyWith(color = UiColors.textSecondary),
                        )
                    },
                )
            }
        return confirmed ?: false
    }

    private suspend fun _savePersonalPreferences() {
        if (_creating || _saving || _savingPreferences || _leaving || _deleting) return
        setState { _savingPreferences = true }
        try {
            val draft =
                roomProfileUpdateDraftFromForm(
                    remarkName = _remarkNameController.text,
                    notificationPolicy = _notificationPolicy,
                    usingGlobalProfile = false,
                    roomDisplayName = _roomDisplayNameController.text,
                    usingProfilePresetAvatar = true,
                    defaultAvatarKey = "",
                )
            val updated =
                widget.controller.updateMyRoomSettings(
                    roomId = _room.id,
                    remarkName = draft.remarkName,
                    notificationPolicy = draft.notificationPolicy,
                    roomDisplayName = draft.roomDisplayName,
                    isPinned = _isPinned,
                    aiVoiceAnnouncementsEnabled = _aiVoiceAnnouncementsEnabled,
                )
            if (!mounted) return
            setState {
                _room = updated
                _notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy)
                _isPinned = updated.isPinned
                _aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled
                _remarkNameController.text = updated.remarkName ?: ""
                _roomDisplayNameController.text = updated.personalProfile.displayName ?: ""
                _savingPreferences = false
                _changed = true
                _error = null
                _notice = roomPersonalPreferencesSavedNotice()
                _markFloatingNoticeEvent("notice", _notice)
            }
            widget.onRoomUpdated(updated)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _savingPreferences = false
                _error = userFacingErrorMessage(error)
                _notice = null
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _refreshRoom() {
        if (_creating || _refreshing) return
        setState {
            _refreshing = true
            _error = null
            _notice = null
        }
        try {
            val updated = widget.controller.getRoom(_room.id)
            if (!mounted) return
            setState {
                _room = updated
                _nameController.text = updated.name
                _descriptionController.text = updated.description
                _visibility = normalizeRoomVisibility(updated.visibility)
                _joinPolicy = normalizeRoomJoinPolicy(updated.joinPolicy)
                _aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled
                _gangBotEnabled = updated.gangBotEnabled
                _notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy)
                _isPinned = updated.isPinned
                _remarkNameController.text = updated.remarkName ?: ""
                _roomDisplayNameController.text = updated.personalProfile.displayName ?: ""
                _defaultAvatarKey = updated.defaultAvatarKey
                _usingPresetAvatar = updated.avatarUrl == null
                _pendingAvatarAssetId = null
                _pendingAvatarUrl = null
                _refreshing = false
            }
            widget.onRoomUpdated(updated)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _refreshing = false
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _pickAvatar() {
        if (_uploadingAvatar || _saving) return
        val cropped: _CroppedRoomAvatar?
        try {
            cropped = _pickAndCropRoomAvatar(context)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _error = userFacingErrorMessage(error)
                _notice = null
                _markFloatingNoticeEvent("error", _error)
            }
            return
        }
        if (cropped == null || !mounted) return
        setState {
            _uploadingAvatar = true
            _error = null
            _notice = null
        }
        try {
            val asset = widget.controller.uploadImageAsset(bytes = cropped.bytes, filename = cropped.filename, purpose = "avatar")
            if (!mounted) return
            setState {
                _uploadingAvatar = false
                _usingPresetAvatar = false
                _pendingAvatarAssetId = asset.id
                _pendingAvatarUrl = asset.url
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _uploadingAvatar = false
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private fun _selectPreset(key: String) {
        setState {
            _usingPresetAvatar = true
            _defaultAvatarKey = key
            _pendingAvatarAssetId = null
            _pendingAvatarUrl = null
            _error = null
        }
    }

    private fun _avatarPreviewUrl(appConfig: AppConfig): String? =
        appConfig.resolveAssetUrl(
            roomManagementAvatarPath(
                usingPresetAvatar = _usingPresetAvatar,
                pendingAvatarUrl = _pendingAvatarUrl,
                roomAvatarUrl = _room.avatarUrl,
            ),
        )

    private suspend fun _openRoomAvatarPreview(imageUrl: String?) {
        val url = imageUrl?.trim()
        val opener = widget.stickerImagePreviewOpener
        if (url == null || url.isEmpty() || opener == null) return
        opener(context, url, "$_avatarDisplayName-icon.png", true)
    }

    private suspend fun _leaveRoom() {
        if (_saving || _savingPreferences || _leaving || _deleting) return
        val confirmation = roomLeaveConfirmationSpec(room = _room, isInLive = widget.isInLive)
        val confirmed =
            if (confirmation.requiresStrongConfirmation) {
                showDialog<Boolean>(context) { _ ->
                    _StrongConfirmDialog(
                        title = confirmation.title,
                        message = confirmation.body,
                        expectedText = confirmation.expectedText!!,
                        confirmLabel = confirmation.confirmLabel,
                    )
                }
            } else {
                showDialog<Boolean>(context) { _ ->
                    _ConfirmDialog(
                        title = confirmation.title,
                        message = confirmation.body,
                        confirmLabel = confirmation.confirmLabel,
                        danger = true,
                    )
                }
            }
        if (confirmed != true || !mounted) return
        setState {
            _leaving = true
            _error = null
            _notice = null
        }
        try {
            if (widget.isInLive) widget.onLeaveLive()
            widget.controller.leaveRoom(roomId = _room.id, confirmDeleteIfEmpty = confirmation.confirmDeleteIfEmpty)
            if (!mounted) return
            _emitResult(RoomManagementResult.left())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _leaving = false
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    private suspend fun _deleteRoom() {
        if (!_canDeleteRoom || _saving || _savingPreferences || _leaving || _deleting) return
        val confirmation = roomDeletionConfirmationSpec(_room)
        val confirmed =
            showDialog<Boolean>(context) { _ ->
                _StrongConfirmDialog(
                    title = confirmation.title,
                    message = confirmation.body,
                    expectedText = confirmation.expectedText,
                    confirmLabel = confirmation.confirmLabel,
                )
            }
        if (confirmed != true || !mounted) return
        setState {
            _deleting = true
            _error = null
            _notice = null
        }
        try {
            if (widget.isInLive) widget.onLeaveLive()
            widget.controller.deleteRoom(roomId = _room.id, confirmName = _room.name)
            if (!mounted) return
            _emitResult(RoomManagementResult.deleted())
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (!mounted) return
            setState {
                _deleting = false
                _error = userFacingErrorMessage(error)
                _markFloatingNoticeEvent("error", _error)
            }
        }
    }

    @Composable
    override fun build() {
        val creating = _creating
        FloatingNoticeEmitter(notices = _floatingNotices()) {
            _RoomDialogShell(
                modifier = widget.modifier,
                title = if (creating) "创建房间" else "房间设置",
                icon = if (creating) Icons.Filled.AddCircleOutline else Icons.Filled.Tune,
                maxWidth = _dialogMaxWidth,
                maxHeight = _dialogMaxHeight,
                embedded = widget.embedded,
                onClose = ::_close,
                headerAction =
                    if (creating) {
                        null
                    } else {
                        {
                            ButtonIcon(
                                tooltip = "刷新设置",
                                onPressed = if (_refreshing) null else ({ unawaited { _refreshRoom() } }),
                                icon = { Icon(Icons.Filled.Refresh) },
                                size = 38.dp,
                                loading = _refreshing,
                            )
                        }
                    },
                pinned =
                    if (creating) {
                        null
                    } else {
                        {
                            SegmentedControl(
                                expanded = true,
                                value = _section,
                                onChanged = { section -> setState { _section = section } },
                                segments =
                                    buildList {
                                        add(Segment(value = RoomSettingsSection.info, label = "房间信息", icon = Icons.Outlined.Info))
                                        add(Segment(value = RoomSettingsSection.preferences, label = "个人偏好", icon = Icons.Outlined.Tune))
                                        if (widget.messageHistoryBuilder != null) {
                                            add(
                                                Segment(
                                                    value = RoomSettingsSection.messageHistory,
                                                    label = "消息记录",
                                                    icon = Icons.Outlined.History,
                                                ),
                                            )
                                        }
                                        add(
                                            Segment(
                                                value = RoomSettingsSection.stickers,
                                                label = "表情包",
                                                icon = Icons.Outlined.EmojiEmotions,
                                            ),
                                        )
                                        if (widget.musicPlaylistsBuilder != null) {
                                            add(
                                                Segment(
                                                    value = RoomSettingsSection.playlists,
                                                    label = "歌单",
                                                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                                                ),
                                            )
                                        }
                                    },
                            )
                        }
                    },
            ) {
                if (creating) {
                    _buildSettingsBody()
                } else {
                    when (_section) {
                        RoomSettingsSection.info -> _buildSettingsBody()
                        RoomSettingsSection.preferences -> _buildPreferencesBody()
                        RoomSettingsSection.messageHistory -> {
                            val builder = widget.messageHistoryBuilder
                            if (builder != null) builder(context) else _buildPreferencesBody()
                        }
                        RoomSettingsSection.stickers ->
                            StickerManagerPanel(
                                backend =
                                    _RoomStickerBackend(controller = widget.controller, roomId = _room.id, canManage = _canManageRoom),
                                imagePreviewOpener = widget.stickerImagePreviewOpener,
                                title = "房间表情包",
                                unavailableText = "房间表情包需要登录后从服务端读取",
                            )
                        RoomSettingsSection.playlists -> {
                            val builder = widget.musicPlaylistsBuilder
                            if (builder != null) {
                                builder(context)
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("房间歌单暂不可用", style = TextStyle(color = UiColors.textMuted))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun _buildSettingsBody() {
        val creating = _creating
        val canManageRoom = _canManageRoom
        val config = AppConfigScope.of(context)
        SettingsList(
            children =
                buildList {
                    add {
                        SettingsCard(
                            title = "房间信息",
                            children =
                                buildList {
                                    add {
                                        _LabeledRoomInput(
                                            fieldTag = roomSettingsNameInputTag,
                                            label = "名称",
                                            controller = _nameController,
                                            enabled = canManageRoom && !_saving,
                                            readOnly = !canManageRoom,
                                        )
                                    }
                                    add {
                                        _LabeledRoomInput(
                                            fieldTag = roomSettingsDescriptionInputTag,
                                            label = "简介",
                                            controller = _descriptionController,
                                            enabled = canManageRoom && !_saving,
                                            readOnly = !canManageRoom,
                                            maxLines = null,
                                        )
                                    }
                                    add {
                                        AvatarPicker(
                                            label = "图标",
                                            displayName = _avatarDisplayName,
                                            imageUrl = _avatarPreviewUrl(config),
                                            defaultAvatarKey = _defaultAvatarKey,
                                            usingPreset = _usingPresetAvatar,
                                            uploading = _uploadingAvatar,
                                            enabled = canManageRoom && !_saving,
                                            onUpload = { unawaited { _pickAvatar() } },
                                            onPresetSelected = ::_selectPreset,
                                            onImagePreview =
                                                if (widget.stickerImagePreviewOpener == null) {
                                                    null
                                                } else {
                                                    { unawaited { _openRoomAvatarPreview(_avatarPreviewUrl(AppConfigScope.of(context))) } }
                                                },
                                            uploadLabel = "上传图标",
                                        )
                                    }
                                    if (!creating) add { _ReadOnlyRoomRid(value = roomIdentifier(_room)) }
                                    if (!creating) add { _ReadOnlyRoomCreatedAt(value = roomCreatedAtLabel(_room.createdAt)) }
                                    add {
                                        _LabeledSegmented(
                                            controlTag = roomSettingsVisibilitySegmentedTag,
                                            label = "可见性",
                                            value = _visibility,
                                            enabled = canManageRoom && !_saving,
                                            segments = listOf(Segment(value = "public", label = "公开"), Segment(value = "private", label = "私密")),
                                            onChanged = { value -> setState { _visibility = value } },
                                        )
                                    }
                                    add {
                                        _LabeledSegmented(
                                            controlTag = roomSettingsJoinPolicySegmentedTag,
                                            label = "加入方式",
                                            value = _joinPolicy,
                                            enabled = canManageRoom && !_saving,
                                            segments =
                                                listOf(
                                                    Segment(value = "open", label = "开放"),
                                                    Segment(value = "approval_required", label = "需审批"),
                                                    Segment(value = "closed", label = "关闭"),
                                                ),
                                            onChanged = { value -> setState { _joinPolicy = value } },
                                        )
                                    }
                                    if (!creating && _room.gangBotAvailable) {
                                        add {
                                            _ToggleRow(
                                                modifier = Modifier.testTag(roomSettingsGangBotToggleTag),
                                                label = "gang-bot（AI 群友）",
                                                value = _gangBotEnabled,
                                                enabled = canManageRoom && !_saving,
                                                onChanged = { value -> setState { _gangBotEnabled = value } },
                                            )
                                        }
                                    }
                                    add {
                                        Button(
                                            width = Dp.Infinity,
                                            tone = ButtonTone.primary,
                                            loading = _saving,
                                            onPressed = if (canManageRoom) ({ unawaited { _save() } }) else null,
                                            icon = { Icon(if (creating) Icons.Filled.CheckCircleOutline else Icons.Outlined.Save) },
                                        ) {
                                            Text(if (creating) "确定" else "保存房间设置")
                                        }
                                    }
                                },
                        )
                    }
                    if (!creating) {
                        add {
                            SettingsCard(
                                title = "离开房间",
                                children =
                                    listOf {
                                        Button(
                                            width = Dp.Infinity,
                                            tone = ButtonTone.danger,
                                            loading = _leaving,
                                            onPressed = { unawaited { _leaveRoom() } },
                                            icon = { Icon(Icons.AutoMirrored.Filled.Logout) },
                                        ) {
                                            Text("离开房间")
                                        }
                                    },
                            )
                        }
                    }
                    if (_canDeleteRoom) {
                        add {
                            SettingsCard(
                                title = "删除房间",
                                danger = true,
                                children =
                                    listOf {
                                        Button(
                                            width = Dp.Infinity,
                                            tone = ButtonTone.danger,
                                            loading = _deleting,
                                            onPressed = { unawaited { _deleteRoom() } },
                                            icon = { Icon(Icons.Outlined.DeleteForever) },
                                        ) {
                                            Text("删除房间")
                                        }
                                    },
                            )
                        }
                    }
                },
        )
    }

    @Composable
    private fun _buildPreferencesBody() {
        SettingsList(
            children =
                listOf {
                    SettingsCard(
                        title = "个人偏好",
                        children =
                            listOf(
                                {
                                    _LabeledRoomInput(
                                        fieldTag = roomSettingsRemarkNameInputTag,
                                        label = "房间备注名",
                                        controller = _remarkNameController,
                                        hintText = _room.name,
                                        enabled = !_savingPreferences,
                                    )
                                },
                                {
                                    _LabeledRoomInput(
                                        fieldTag = roomSettingsRoomDisplayNameInputTag,
                                        label = "房间内用户名",
                                        controller = _roomDisplayNameController,
                                        hintText = _currentUserDefaultDisplayName,
                                        enabled = !_savingPreferences,
                                    )
                                },
                                {
                                    _LabeledSegmented(
                                        controlTag = roomSettingsNotificationPolicySegmentedTag,
                                        label = "房间消息",
                                        value = _notificationPolicy,
                                        enabled = !_savingPreferences,
                                        segments =
                                            listOf(
                                                Segment(value = "all", label = "全部"),
                                                Segment(value = "silent", label = "接收但不提醒"),
                                                Segment(value = "blocked", label = "屏蔽"),
                                            ),
                                        onChanged = { value -> setState { _notificationPolicy = normalizeRoomNotificationPolicy(value) } },
                                    )
                                },
                                {
                                    _ToggleRow(
                                        label = "置顶房间",
                                        value = _isPinned,
                                        enabled = !_savingPreferences,
                                        onChanged = { value -> setState { _isPinned = value } },
                                    )
                                },
                                {
                                    _ToggleRow(
                                        label = "AI 语音播报",
                                        value = _aiVoiceAnnouncementsEnabled,
                                        enabled = !_savingPreferences,
                                        onChanged = { value -> setState { _aiVoiceAnnouncementsEnabled = value } },
                                    )
                                },
                                {
                                    Button(
                                        width = Dp.Infinity,
                                        tone = ButtonTone.primary,
                                        loading = _savingPreferences,
                                        onPressed = { unawaited { _savePersonalPreferences() } },
                                        icon = { Icon(Icons.Outlined.Save) },
                                    ) {
                                        Text("保存个人偏好")
                                    }
                                },
                            ),
                    )
                },
        )
    }
}

@Composable
private fun _ReadOnlyRoomRid(value: String) {
    _ReadOnlyRoomInfoField(label = "房间 RID", value = value, fieldTag = roomSettingsRidTag)
}

@Composable
private fun _ReadOnlyRoomCreatedAt(value: String) {
    _ReadOnlyRoomInfoField(label = "创建时间", value = value, fieldTag = roomSettingsCreatedAtTag)
}

@Composable
private fun _ReadOnlyRoomInfoField(label: String, value: String, fieldTag: String) {
    Column(Modifier.fillMaxWidth()) {
        _RoomFieldLabel(label)
        Spacer(Modifier.height(8.dp))
        ReadOnlyTextBox(
            value = value,
            fieldKey = fieldTag,
            style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 13.0),
        )
    }
}

/** [fieldTag] tags the field's root (Dart's `fieldKey`, the field widget's key). */
@Composable
private fun _LabeledRoomInput(
    fieldTag: String,
    label: String,
    controller: TextEditingController,
    hintText: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int? = 1,
) {
    Column(Modifier.fillMaxWidth()) {
        _RoomFieldLabel(label)
        Spacer(Modifier.height(8.dp))
        if (readOnly) {
            ReadOnlyTextBox(
                modifier = Modifier.testTag(fieldTag),
                value = controller.text,
                maxLines = maxLines,
                style = UiTypography.body.copyWith(color = UiColors.text, fontSize = 13.0),
            )
        } else {
            Input(
                modifier = Modifier.testTag(fieldTag),
                controller = controller,
                hintText = hintText,
                enabled = enabled,
                minLines = 1,
                maxLines = maxLines,
            )
        }
    }
}

private fun _draftCreateRoom(currentUser: CurrentUser): RoomDetail {
    val now = DateTime.fromMillisecondsSinceEpoch(0, isUtc = true)
    return RoomDetail(
        id = "__new_room__",
        name = "",
        visibility = "public",
        joinPolicy = "approval_required",
        avatarUrl = null,
        defaultAvatarKey = kDefaultAvatarPresetKey,
        memberCount = 1,
        onlineMemberCount = 1,
        createdBy = currentUser.toSummary(),
        myMembership = RoomMembership(joinedAt = now, role = "owner"),
        live = LiveState(roomId = "__new_room__", participantCount = 0, participants = emptyList(), updatedAt = now),
        createdAt = now,
        updatedAt = now,
    )
}

/**
 * 房间表情包的数据来源:把 [RoomsController] 的房间作用域接口适配到
 * 通用的 [StickerManagerPanel]。
 */
private class _RoomStickerBackend(
    val controller: RoomsController,
    val roomId: String,
    val canManage: Boolean,
) : StickerManagerBackend() {
    override val scope: StickerManagementScope get() = StickerManagementScope.room

    override val hasApi: Boolean get() = true

    override val capabilities: StickerManagementCapabilities
        get() = if (canManage) StickerManagementCapabilities() else StickerManagementCapabilities.readOnlyDownloads()

    override suspend fun loadPacks(): List<StickerPack> = controller.listRoomStickerPacks(roomId)

    override suspend fun createDefaultPack(sortOrder: Int?): StickerPack =
        controller.createRoomStickerPack(
            roomId = roomId,
            name = defaultStickerPackName(StickerManagementScope.room),
            sortOrder = sortOrder,
        )

    override suspend fun uploadImageAsset(bytes: ByteArray, filename: String, purpose: String): String {
        val asset = controller.uploadImageAsset(bytes = bytes, filename = filename, purpose = purpose)
        return asset.id
    }

    override suspend fun addSticker(packId: String, assetId: String, name: String, sortOrder: Int?) {
        controller.addRoomSticker(roomId = roomId, packId = packId, assetId = assetId, name = name, sortOrder = sortOrder)
    }

    override suspend fun deleteSticker(packId: String, stickerId: String) {
        controller.deleteRoomSticker(roomId = roomId, packId = packId, stickerId = stickerId)
    }

    override suspend fun renameSticker(packId: String, stickerId: String, name: String): String? {
        val updated = controller.updateSticker(packId = packId, stickerId = stickerId, name = name)
        return updated.name
    }

    override suspend fun reorderStickers(packId: String, stickerIds: List<String>) {
        controller.reorderStickers(packId = packId, stickerIds = stickerIds)
    }

    override suspend fun downloadStickers(stickerIds: List<String>): DownloadedFile = controller.downloadStickers(stickerIds = stickerIds)
}
