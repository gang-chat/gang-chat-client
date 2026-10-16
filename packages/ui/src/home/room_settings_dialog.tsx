import type { ReactNode } from 'react';

import { userFacingErrorMessage } from '@gang-chat/core/app/error_display';
import {
  normalizeRoomJoinPolicy,
  normalizeRoomNotificationPolicy,
  normalizeRoomVisibility,
  roomAccessState,
  roomCreatedAtLabel,
  roomDeletionConfirmationSpec,
  roomDisplayName,
  roomIdentifier,
  roomInfoSavedNotice,
  roomLeaveConfirmationSpec,
  roomManagementAvatarPath,
  roomManagementPermissionState,
  roomPersonalPreferencesSavedNotice,
} from '@gang-chat/core/app/room_display';
import { roomInfoUpdateDraftFromForm, roomProfileUpdateDraftFromForm } from '@gang-chat/core/app/room_forms';
import type { RoomsController } from '@gang-chat/core/app/rooms_controller';
import {
  StickerManagementCapabilities,
  StickerManagementScope,
  defaultStickerPackName,
} from '@gang-chat/core/app/sticker_management';
import type { AppConfig } from '@gang-chat/core/config/app_config';
import { unawaited } from '@gang-chat/core/lib/async';
import type { DownloadedFile } from '@gang-chat/core/protocol/api_client';
import { LiveState, RoomDetail, RoomMembership, type CurrentUser, type StickerPack } from '@gang-chat/core/protocol/models';

import { Center, Column, SizedBox, Text } from '../lib/layout';
import { TextEditingController } from '../lib/text_editing';
import { ViewState, useViewState } from '../lib/view_state';
import { useAppConfig } from '../ui/app_config_scope';
import { kDefaultAvatarPresetKey } from '../ui/avatar';
import { AvatarPicker } from '../ui/avatar_picker';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import {
  DialogFrame,
  FloatingNotice,
  FloatingNoticeEmitter,
  FloatingNoticeTone,
  ResponsiveDialogAction,
} from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { ReadOnlyTextBox } from '../ui/read_only_text_box';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { SettingsCard, SettingsList } from '../ui/settings_scaffold';
import { StickerManagerBackend, StickerManagerPanel, type StickerImagePreviewOpener } from '../ui/sticker_manager';
import { UiColors, UiDefaultTextStyle, UiTypography } from '../ui/tokens';
import { RoomManagementResult, _dialogMaxHeight, _dialogMaxWidth } from './room_management';
import {
  _ConfirmDialog,
  _LabeledSegmented,
  _RoomDialogShell,
  _RoomFieldLabel,
  _StrongConfirmDialog,
  _ToggleRow,
  _pickAndCropRoomAvatar,
  type _CroppedRoomAvatar,
} from './room_management_components';

// Port of `room_settings_dialog.dart` (part of `room_management.dart`).

enum _RoomSettingsDialogMode {
  create = 'create',
  edit = 'edit',
}

/** 房间设置的分段:房间信息、个人偏好与房间资源管理。 */
export enum RoomSettingsSection {
  info = 'info',
  preferences = 'preferences',
  messageHistory = 'messageHistory',
  stickers = 'stickers',
  playlists = 'playlists',
}

export interface RoomSettingsDialogProps {
  controller: RoomsController;
  room: RoomDetail;
  currentUser: CurrentUser;
  isInLive: boolean;
  onLeaveLive: () => Promise<void>;
  onRoomUpdated: (room: RoomDetail) => void;
  embedded?: boolean;
  onClose?: (() => void) | null;
  onResult?: ((result: RoomManagementResult) => void) | null;
  stickerImagePreviewOpener?: StickerImagePreviewOpener | null;
  /** Dart `WidgetBuilder`; the `BuildContext` argument is dropped. */
  messageHistoryBuilder?: (() => ReactNode) | null;
  /** Dart `WidgetBuilder`; the `BuildContext` argument is dropped. */
  musicPlaylistsBuilder?: (() => ReactNode) | null;
  initialSection?: RoomSettingsSection;
  /** Dart's `bool createMode = false` constructor flag (sets the private `_mode`). */
  createMode?: boolean;
}

export interface RoomSettingsDialogCreateOptions {
  controller: RoomsController;
  currentUser: CurrentUser;
  embedded?: boolean;
  onClose?: (() => void) | null;
  onResult?: ((result: RoomManagementResult) => void) | null;
  stickerImagePreviewOpener?: StickerImagePreviewOpener | null;
}

function _modeOf(props: RoomSettingsDialogProps): _RoomSettingsDialogMode {
  return (props.createMode ?? false) ? _RoomSettingsDialogMode.create : _RoomSettingsDialogMode.edit;
}

export class _RoomSettingsDialogState extends ViewState<RoomSettingsDialogProps> {
  _room!: RoomDetail;
  readonly _nameController = new TextEditingController();
  readonly _descriptionController = new TextEditingController();
  readonly _remarkNameController = new TextEditingController();
  readonly _roomDisplayNameController = new TextEditingController();
  _visibility = '';
  _joinPolicy = '';
  _aiVoiceAnnouncementsEnabled = false;
  _gangBotEnabled = false;
  _notificationPolicy = '';
  _isPinned = false;

  _defaultAvatarKey = '';
  _usingPresetAvatar = true;
  _pendingAvatarAssetId: string | null = null;
  _pendingAvatarUrl: string | null = null;
  _uploadingAvatar = false;

  _saving = false;
  _savingPreferences = false;
  _refreshing = false;
  _leaving = false;
  _deleting = false;
  _changed = false;
  _error: string | null = null;
  _notice: string | null = null;
  _floatingNoticeSerial = 0;
  readonly _floatingNoticeEventKeys = new Map<string, number>();

  _section: RoomSettingsSection = RoomSettingsSection.info;

  /** `AppConfigScope.of(context)`; refreshed on every build. */
  _appConfig: AppConfig | null = null;
  /** `Navigator.of(context).pop` of the enclosing dialog route; set on every build. */
  _popRoute: (result?: RoomManagementResult | null) => void = () => {};

  constructor(props: RoomSettingsDialogProps) {
    super();
    // The first build runs before initState: prepare the form up front, as
    // the Dart initState did before its first build.
    this.props = props;
    this._resetFromWidgetRoom({ clearTransientFeedback: false });
  }

  get _creating(): boolean {
    return _modeOf(this.widget) === _RoomSettingsDialogMode.create;
  }

  get _canManageRoom(): boolean {
    return this._creating || roomAccessState({ room: this._room, currentUser: this.widget.currentUser }).canManageRoom;
  }

  get _canDeleteRoom(): boolean {
    return (
      !this._creating && roomManagementPermissionState({ room: this._room, currentUser: this.widget.currentUser }).canDeleteRoom
    );
  }

  get _avatarDisplayName(): string {
    const name = this._nameController.text.trim();
    if (name.length > 0) return name;
    return this._creating ? '房间' : roomDisplayName(this._room);
  }

  get _currentUserDefaultDisplayName(): string {
    const displayName = this.widget.currentUser.displayName.trim();
    if (displayName.length > 0) return displayName;
    return this.widget.currentUser.username;
  }

  override didUpdateProps(oldWidget: RoomSettingsDialogProps): void {
    if (_modeOf(oldWidget) !== _modeOf(this.widget) || oldWidget.room.id !== this.widget.room.id) {
      // didUpdateWidget runs before build in Flutter; here it runs after the
      // render, so the reset schedules the rebuild itself.
      this.setState(() => this._resetFromWidgetRoom());
    }
  }

  _resetFromWidgetRoom(opts: { clearTransientFeedback?: boolean } = {}): void {
    const { clearTransientFeedback = true } = opts;
    this._room = this.widget.room;
    this._nameController.text = this._room.name;
    this._descriptionController.text = this._room.description;
    this._visibility = normalizeRoomVisibility(this._room.visibility);
    this._joinPolicy = normalizeRoomJoinPolicy(this._room.joinPolicy);
    this._aiVoiceAnnouncementsEnabled = this._room.aiVoiceAnnouncementsEnabled;
    this._gangBotEnabled = this._room.gangBotEnabled;
    this._notificationPolicy = normalizeRoomNotificationPolicy(this._room.notificationPolicy);
    this._isPinned = this._room.isPinned;
    this._remarkNameController.text = this._room.remarkName ?? '';
    this._roomDisplayNameController.text = this._room.personalProfile.displayName ?? '';
    this._defaultAvatarKey = this._room.defaultAvatarKey;
    this._usingPresetAvatar = this._room.avatarUrl == null;
    this._pendingAvatarAssetId = null;
    this._pendingAvatarUrl = null;
    this._uploadingAvatar = false;
    this._saving = false;
    this._savingPreferences = false;
    this._refreshing = false;
    this._leaving = false;
    this._deleting = false;
    this._changed = false;
    this._section = this._creating ? RoomSettingsSection.info : (this.widget.initialSection ?? RoomSettingsSection.info);
    if (clearTransientFeedback) {
      this._error = null;
      this._notice = null;
    }
  }

  override dispose(): void {
    this._nameController.dispose();
    this._descriptionController.dispose();
    this._remarkNameController.dispose();
    this._roomDisplayNameController.dispose();
  }

  _close(): void {
    if (this.widget.embedded ?? false) {
      if (!this._creating && this._changed) {
        this.widget.onResult?.(RoomManagementResult.updated(this._room));
      }
      this.widget.onClose?.();
      return;
    }
    this._popRoute(!this._creating && this._changed ? RoomManagementResult.updated(this._room) : null);
  }

  _emitResult(result: RoomManagementResult): void {
    if (this.widget.embedded ?? false) {
      this.widget.onResult?.(result);
      this.widget.onClose?.();
      return;
    }
    this._popRoute(result);
  }

  _markFloatingNoticeEvent(channel: string, message: string | null): void {
    if (message == null || message.trim().length === 0) return;
    this._floatingNoticeEventKeys.set(channel, ++this._floatingNoticeSerial);
  }

  _floatingNoticeEventKey(channel: string): unknown {
    return this._floatingNoticeEventKeys.get(channel) ?? null;
  }

  _floatingNotices(): FloatingNotice[] {
    const notices: FloatingNotice[] = [];
    if (this._notice != null) {
      notices.push(
        new FloatingNotice({
          message: this._notice,
          tone: FloatingNoticeTone.success,
          eventKey: this._floatingNoticeEventKey('notice'),
        }),
      );
    }
    if (this._error != null) {
      notices.push(
        new FloatingNotice({
          message: this._error,
          tone: FloatingNoticeTone.error,
          duration: null,
          eventKey: this._floatingNoticeEventKey('error'),
        }),
      );
    }
    return notices;
  }

  async _save(): Promise<void> {
    if (this._saving || this._savingPreferences || this._leaving || this._deleting || !this._canManageRoom) {
      return;
    }
    const draft = roomInfoUpdateDraftFromForm({
      name: this._nameController.text,
      description: this._descriptionController.text,
      visibility: this._visibility,
      joinPolicy: this._joinPolicy,
      pendingAvatarAssetId: this._pendingAvatarAssetId,
      usingPresetAvatar: this._usingPresetAvatar,
      defaultAvatarKey: this._defaultAvatarKey,
    });
    if (!draft.isValid) {
      this.setState(() => {
        this._error = draft.error;
        this._notice = null;
        this._markFloatingNoticeEvent('error', this._error);
      });
      return;
    }
    try {
      this.setState(() => {
        this._saving = true;
      });
      const confirmedJoinPolicyChange = await this._confirmJoinPolicyAutoReviewIfNeeded(draft.joinPolicy);
      if (!this.mounted) return;
      if (!confirmedJoinPolicyChange) {
        this.setState(() => {
          this._joinPolicy = 'approval_required';
          this._saving = false;
          this._error = null;
          this._notice = null;
        });
        return;
      }
      const updated = this._creating
        ? await this.widget.controller.createRoom({
            name: draft.name!,
            description: draft.description,
            visibility: draft.visibility,
            joinPolicy: draft.joinPolicy,
            avatarAssetId: draft.avatarAssetId,
            defaultAvatarKey: draft.defaultAvatarKey,
          })
        : await this.widget.controller.updateRoom({
            roomId: this._room.id,
            name: draft.name,
            description: draft.description,
            visibility: draft.visibility,
            joinPolicy: draft.joinPolicy,
            gangBotEnabled: this._room.gangBotAvailable ? this._gangBotEnabled : null,
            avatarAssetId: draft.avatarAssetId,
            defaultAvatarKey: draft.defaultAvatarKey,
          });
      if (!this.mounted) return;
      if (this._creating) {
        this._emitResult(RoomManagementResult.created(updated));
        return;
      }
      this.setState(() => {
        this._room = updated;
        this._gangBotEnabled = updated.gangBotEnabled;
        this._notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy);
        this._defaultAvatarKey = updated.defaultAvatarKey;
        this._usingPresetAvatar = updated.avatarUrl == null;
        this._pendingAvatarAssetId = null;
        this._pendingAvatarUrl = null;
        this._saving = false;
        this._changed = true;
        this._error = null;
        this._notice = roomInfoSavedNotice();
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      this.widget.onRoomUpdated(updated);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._saving = false;
        this._error = userFacingErrorMessage(error);
        this._notice = null;
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _confirmJoinPolicyAutoReviewIfNeeded(nextJoinPolicy: string | null): Promise<boolean> {
    if (this._creating) return true;
    const currentPolicy = normalizeRoomJoinPolicy(this._room.joinPolicy);
    const normalizedNext = normalizeRoomJoinPolicy(nextJoinPolicy ?? currentPolicy);
    if (currentPolicy !== 'approval_required') return true;
    if (normalizedNext !== 'open' && normalizedNext !== 'closed') return true;

    const pendingRequests = await this.widget.controller.listJoinRequests(this._room.id);
    if (!this.mounted || pendingRequests.length === 0) return true;

    const decisionLabel = normalizedNext === 'open' ? '批准' : '拒绝';
    const confirmed = await showDialog<boolean>((close) => (
      <DialogFrame
        title="确认修改加入方式？"
        icon={Icons.warning_amber_outlined}
        adaptiveActions={[
          new ResponsiveDialogAction({ label: '取消', onPressed: () => close(false) }),
          new ResponsiveDialogAction({ label: '确认修改', tone: ButtonTone.primary, onPressed: () => close(true) }),
        ]}
      >
        <Text style={{ ...UiTypography.body, color: UiColors.textSecondary }}>
          {`当前仍有 ${pendingRequests.length} 个未处理申请。` + `确认修改后，将自动${decisionLabel}所有未处理申请。`}
        </Text>
      </DialogFrame>
    ));
    return confirmed ?? false;
  }

  async _savePersonalPreferences(): Promise<void> {
    if (this._creating || this._saving || this._savingPreferences || this._leaving || this._deleting) {
      return;
    }
    this.setState(() => {
      this._savingPreferences = true;
    });
    try {
      const draft = roomProfileUpdateDraftFromForm({
        remarkName: this._remarkNameController.text,
        notificationPolicy: this._notificationPolicy,
        usingGlobalProfile: false,
        roomDisplayName: this._roomDisplayNameController.text,
        usingProfilePresetAvatar: true,
        defaultAvatarKey: '',
      });
      const updated = await this.widget.controller.updateMyRoomSettings({
        roomId: this._room.id,
        remarkName: draft.remarkName,
        notificationPolicy: draft.notificationPolicy,
        roomDisplayName: draft.roomDisplayName,
        isPinned: this._isPinned,
        aiVoiceAnnouncementsEnabled: this._aiVoiceAnnouncementsEnabled,
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._room = updated;
        this._notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy);
        this._isPinned = updated.isPinned;
        this._aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled;
        this._remarkNameController.text = updated.remarkName ?? '';
        this._roomDisplayNameController.text = updated.personalProfile.displayName ?? '';
        this._savingPreferences = false;
        this._changed = true;
        this._error = null;
        this._notice = roomPersonalPreferencesSavedNotice();
        this._markFloatingNoticeEvent('notice', this._notice);
      });
      this.widget.onRoomUpdated(updated);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._savingPreferences = false;
        this._error = userFacingErrorMessage(error);
        this._notice = null;
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _refreshRoom(): Promise<void> {
    if (this._creating || this._refreshing) return;
    this.setState(() => {
      this._refreshing = true;
      this._error = null;
      this._notice = null;
    });
    try {
      const updated = await this.widget.controller.getRoom(this._room.id);
      if (!this.mounted) return;
      this.setState(() => {
        this._room = updated;
        this._nameController.text = updated.name;
        this._descriptionController.text = updated.description;
        this._visibility = normalizeRoomVisibility(updated.visibility);
        this._joinPolicy = normalizeRoomJoinPolicy(updated.joinPolicy);
        this._aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled;
        this._gangBotEnabled = updated.gangBotEnabled;
        this._notificationPolicy = normalizeRoomNotificationPolicy(updated.notificationPolicy);
        this._isPinned = updated.isPinned;
        this._remarkNameController.text = updated.remarkName ?? '';
        this._roomDisplayNameController.text = updated.personalProfile.displayName ?? '';
        this._defaultAvatarKey = updated.defaultAvatarKey;
        this._usingPresetAvatar = updated.avatarUrl == null;
        this._pendingAvatarAssetId = null;
        this._pendingAvatarUrl = null;
        this._refreshing = false;
      });
      this.widget.onRoomUpdated(updated);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._refreshing = false;
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _pickAvatar(): Promise<void> {
    if (this._uploadingAvatar || this._saving) return;
    let cropped: _CroppedRoomAvatar | null;
    try {
      cropped = await _pickAndCropRoomAvatar(() => this.mounted);
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._error = userFacingErrorMessage(error);
        this._notice = null;
        this._markFloatingNoticeEvent('error', this._error);
      });
      return;
    }
    if (cropped == null || !this.mounted) return;
    const picked = cropped;
    this.setState(() => {
      this._uploadingAvatar = true;
      this._error = null;
      this._notice = null;
    });
    try {
      const asset = await this.widget.controller.uploadImageAsset({
        bytes: picked.bytes,
        filename: picked.filename,
        purpose: 'avatar',
      });
      if (!this.mounted) return;
      this.setState(() => {
        this._uploadingAvatar = false;
        this._usingPresetAvatar = false;
        this._pendingAvatarAssetId = asset.id;
        this._pendingAvatarUrl = asset.url;
      });
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._uploadingAvatar = false;
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  _selectPreset(key: string): void {
    this.setState(() => {
      this._usingPresetAvatar = true;
      this._defaultAvatarKey = key;
      this._pendingAvatarAssetId = null;
      this._pendingAvatarUrl = null;
      this._error = null;
    });
  }

  _avatarPreviewUrl(appConfig: AppConfig): string | null {
    return appConfig.resolveAssetUrl(
      roomManagementAvatarPath({
        usingPresetAvatar: this._usingPresetAvatar,
        pendingAvatarUrl: this._pendingAvatarUrl,
        roomAvatarUrl: this._room.avatarUrl,
      }),
    );
  }

  async _openRoomAvatarPreview(imageUrl: string | null): Promise<void> {
    const url = imageUrl?.trim();
    const opener = this.widget.stickerImagePreviewOpener;
    if (url == null || url.length === 0 || opener == null) return;
    await opener({
      imageUrl: url,
      suggestedName: `${this._avatarDisplayName}-icon.png`,
      forceSquare: true,
    });
  }

  async _leaveRoom(): Promise<void> {
    if (this._saving || this._savingPreferences || this._leaving || this._deleting) return;
    const confirmation = roomLeaveConfirmationSpec({ room: this._room, isInLive: this.widget.isInLive });
    const confirmed = confirmation.requiresStrongConfirmation
      ? await showDialog<boolean>(() => (
          <_StrongConfirmDialog
            title={confirmation.title}
            message={confirmation.body}
            expectedText={confirmation.expectedText!}
            confirmLabel={confirmation.confirmLabel}
          />
        ))
      : await showDialog<boolean>(() => (
          <_ConfirmDialog title={confirmation.title} message={confirmation.body} confirmLabel={confirmation.confirmLabel} danger />
        ));
    if (confirmed !== true || !this.mounted) return;
    this.setState(() => {
      this._leaving = true;
      this._error = null;
      this._notice = null;
    });
    try {
      if (this.widget.isInLive) await this.widget.onLeaveLive();
      await this.widget.controller.leaveRoom({
        roomId: this._room.id,
        confirmDeleteIfEmpty: confirmation.confirmDeleteIfEmpty,
      });
      if (!this.mounted) return;
      this._emitResult(RoomManagementResult.left());
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._leaving = false;
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  async _deleteRoom(): Promise<void> {
    if (!this._canDeleteRoom || this._saving || this._savingPreferences || this._leaving || this._deleting) {
      return;
    }
    const confirmation = roomDeletionConfirmationSpec(this._room);
    const confirmed = await showDialog<boolean>(() => (
      <_StrongConfirmDialog
        title={confirmation.title}
        message={confirmation.body}
        expectedText={confirmation.expectedText}
        confirmLabel={confirmation.confirmLabel}
      />
    ));
    if (confirmed !== true || !this.mounted) return;
    this.setState(() => {
      this._deleting = true;
      this._error = null;
      this._notice = null;
    });
    try {
      if (this.widget.isInLive) await this.widget.onLeaveLive();
      await this.widget.controller.deleteRoom({ roomId: this._room.id, confirmName: this._room.name });
      if (!this.mounted) return;
      this._emitResult(RoomManagementResult.deleted());
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._deleting = false;
        this._error = userFacingErrorMessage(error);
        this._markFloatingNoticeEvent('error', this._error);
      });
    }
  }

  build(): ReactNode {
    const creating = this._creating;
    const segments: Segment<RoomSettingsSection>[] = [
      new Segment({ value: RoomSettingsSection.info, label: '房间信息', icon: Icons.info_outline }),
      new Segment({ value: RoomSettingsSection.preferences, label: '个人偏好', icon: Icons.tune_outlined }),
    ];
    if (this.widget.messageHistoryBuilder != null) {
      segments.push(new Segment({ value: RoomSettingsSection.messageHistory, label: '消息记录', icon: Icons.history_outlined }));
    }
    segments.push(new Segment({ value: RoomSettingsSection.stickers, label: '表情包', icon: Icons.emoji_emotions_outlined }));
    if (this.widget.musicPlaylistsBuilder != null) {
      segments.push(new Segment({ value: RoomSettingsSection.playlists, label: '歌单', icon: Icons.queue_music_outlined }));
    }

    let child: ReactNode;
    if (creating) {
      child = this._buildSettingsBody();
    } else {
      switch (this._section) {
        case RoomSettingsSection.info:
          child = this._buildSettingsBody();
          break;
        case RoomSettingsSection.preferences:
          child = this._buildPreferencesBody();
          break;
        case RoomSettingsSection.messageHistory: {
          const builder = this.widget.messageHistoryBuilder;
          child = builder != null ? builder() : this._buildPreferencesBody();
          break;
        }
        case RoomSettingsSection.stickers:
          child = (
            <StickerManagerPanel
              backend={
                new _RoomStickerBackend({
                  controller: this.widget.controller,
                  roomId: this._room.id,
                  canManage: this._canManageRoom,
                })
              }
              imagePreviewOpener={this.widget.stickerImagePreviewOpener}
              title="房间表情包"
              unavailableText="房间表情包需要登录后从服务端读取"
            />
          );
          break;
        case RoomSettingsSection.playlists: {
          const builder = this.widget.musicPlaylistsBuilder;
          child =
            builder != null ? (
              builder()
            ) : (
              <Center style={{ flex: '1 1 0', minHeight: 0 }}>
                <Text style={{ ...UiDefaultTextStyle, color: UiColors.textMuted }}>房间歌单暂不可用</Text>
              </Center>
            );
          break;
        }
      }
    }

    return (
      <FloatingNoticeEmitter notices={this._floatingNotices()}>
        <_RoomDialogShell
          title={creating ? '创建房间' : '房间设置'}
          icon={creating ? Icons.add_circle_outline : Icons.tune}
          maxWidth={_dialogMaxWidth}
          maxHeight={_dialogMaxHeight}
          embedded={this.widget.embedded ?? false}
          onClose={() => this._close()}
          headerAction={
            creating ? null : (
              <ButtonIcon
                tooltip="刷新设置"
                onPressed={this._refreshing ? null : () => unawaited(this._refreshRoom())}
                icon={<Icon icon={Icons.refresh} />}
                size={38}
                loading={this._refreshing}
              />
            )
          }
          pinned={
            creating ? null : (
              <SegmentedControl<RoomSettingsSection>
                expanded
                value={this._section}
                onChanged={(section) =>
                  this.setState(() => {
                    this._section = section;
                  })
                }
                segments={segments}
              />
            )
          }
        >
          {child}
        </_RoomDialogShell>
      </FloatingNoticeEmitter>
    );
  }

  _buildSettingsBody(): ReactNode {
    const creating = this._creating;
    const canManageRoom = this._canManageRoom;
    const appConfig = this._appConfig!;
    const infoChildren: ReactNode[] = [
      <_LabeledRoomInput
        key="name"
        fieldKey="room-settings-name-input"
        label="名称"
        controller={this._nameController}
        enabled={canManageRoom && !this._saving}
        readOnly={!canManageRoom}
      />,
      <_LabeledRoomInput
        key="description"
        fieldKey="room-settings-description-input"
        label="简介"
        controller={this._descriptionController}
        enabled={canManageRoom && !this._saving}
        readOnly={!canManageRoom}
        maxLines={null}
      />,
      <AvatarPicker
        key="avatar"
        label="图标"
        displayName={this._avatarDisplayName}
        imageUrl={this._avatarPreviewUrl(appConfig)}
        defaultAvatarKey={this._defaultAvatarKey}
        usingPreset={this._usingPresetAvatar}
        uploading={this._uploadingAvatar}
        enabled={canManageRoom && !this._saving}
        onUpload={() => unawaited(this._pickAvatar())}
        onPresetSelected={(key) => this._selectPreset(key)}
        onImagePreview={
          this.widget.stickerImagePreviewOpener == null
            ? null
            : () => unawaited(this._openRoomAvatarPreview(this._avatarPreviewUrl(appConfig)))
        }
        uploadLabel="上传图标"
      />,
    ];
    if (!creating) {
      infoChildren.push(<_ReadOnlyRoomRid key="rid" value={roomIdentifier(this._room)} />);
      infoChildren.push(<_ReadOnlyRoomCreatedAt key="created-at" value={roomCreatedAtLabel(this._room.createdAt)} />);
    }
    infoChildren.push(
      <_LabeledSegmented<string>
        key="visibility"
        controlKey="room-settings-visibility-segmented"
        label="可见性"
        value={this._visibility}
        enabled={canManageRoom && !this._saving}
        segments={[new Segment({ value: 'public', label: '公开' }), new Segment({ value: 'private', label: '私密' })]}
        onChanged={(value) =>
          this.setState(() => {
            this._visibility = value;
          })
        }
      />,
      <_LabeledSegmented<string>
        key="join-policy"
        controlKey="room-settings-join-policy-segmented"
        label="加入方式"
        value={this._joinPolicy}
        enabled={canManageRoom && !this._saving}
        segments={[
          new Segment({ value: 'open', label: '开放' }),
          new Segment({ value: 'approval_required', label: '需审批' }),
          new Segment({ value: 'closed', label: '关闭' }),
        ]}
        onChanged={(value) =>
          this.setState(() => {
            this._joinPolicy = value;
          })
        }
      />,
    );
    if (!creating && this._room.gangBotAvailable) {
      infoChildren.push(
        <_ToggleRow
          key="gang-bot"
          testId="room-settings-gang-bot-toggle"
          label="gang-bot（AI 群友）"
          value={this._gangBotEnabled}
          enabled={canManageRoom && !this._saving}
          onChanged={(value) =>
            this.setState(() => {
              this._gangBotEnabled = value;
            })
          }
        />,
      );
    }
    infoChildren.push(
      <Button
        key="save"
        width={Infinity}
        tone={ButtonTone.primary}
        loading={this._saving}
        onPressed={canManageRoom ? () => unawaited(this._save()) : null}
        icon={creating ? Icons.check_circle_outline : Icons.save_outlined}
      >
        {creating ? '确定' : '保存房间设置'}
      </Button>,
    );

    const cards: ReactNode[] = [
      <SettingsCard key="info" title="房间信息">
        {infoChildren}
      </SettingsCard>,
    ];
    if (!creating) {
      cards.push(
        <SettingsCard key="leave" title="离开房间">
          {[
            <Button
              key="leave"
              width={Infinity}
              tone={ButtonTone.danger}
              loading={this._leaving}
              onPressed={() => unawaited(this._leaveRoom())}
              icon={Icons.logout}
            >
              离开房间
            </Button>,
          ]}
        </SettingsCard>,
      );
    }
    if (this._canDeleteRoom) {
      cards.push(
        <SettingsCard key="delete" title="删除房间" danger>
          {[
            <Button
              key="delete"
              width={Infinity}
              tone={ButtonTone.danger}
              loading={this._deleting}
              onPressed={() => unawaited(this._deleteRoom())}
              icon={Icons.delete_forever_outlined}
            >
              删除房间
            </Button>,
          ]}
        </SettingsCard>,
      );
    }
    return <SettingsList physics="clamping">{cards}</SettingsList>;
  }

  _buildPreferencesBody(): ReactNode {
    return (
      <SettingsList physics="clamping">
        {[
          <SettingsCard key="preferences" title="个人偏好">
            {[
              <_LabeledRoomInput
                key="remark-name"
                fieldKey="room-settings-remark-name-input"
                label="房间备注名"
                controller={this._remarkNameController}
                hintText={this._room.name}
                enabled={!this._savingPreferences}
              />,
              <_LabeledRoomInput
                key="room-display-name"
                fieldKey="room-settings-room-display-name-input"
                label="房间内用户名"
                controller={this._roomDisplayNameController}
                hintText={this._currentUserDefaultDisplayName}
                enabled={!this._savingPreferences}
              />,
              <_LabeledSegmented<string>
                key="notification-policy"
                controlKey="room-settings-notification-policy-segmented"
                label="房间消息"
                value={this._notificationPolicy}
                enabled={!this._savingPreferences}
                segments={[
                  new Segment({ value: 'all', label: '全部' }),
                  new Segment({ value: 'silent', label: '接收但不提醒' }),
                  new Segment({ value: 'blocked', label: '屏蔽' }),
                ]}
                onChanged={(value) => {
                  this.setState(() => {
                    this._notificationPolicy = normalizeRoomNotificationPolicy(value);
                  });
                }}
              />,
              <_ToggleRow
                key="pinned"
                label="置顶房间"
                value={this._isPinned}
                enabled={!this._savingPreferences}
                onChanged={(value) =>
                  this.setState(() => {
                    this._isPinned = value;
                  })
                }
              />,
              <_ToggleRow
                key="ai-voice"
                label="AI 语音播报"
                value={this._aiVoiceAnnouncementsEnabled}
                enabled={!this._savingPreferences}
                onChanged={(value) =>
                  this.setState(() => {
                    this._aiVoiceAnnouncementsEnabled = value;
                  })
                }
              />,
              <Button
                key="save"
                width={Infinity}
                tone={ButtonTone.primary}
                loading={this._savingPreferences}
                onPressed={() => unawaited(this._savePersonalPreferences())}
                icon={Icons.save_outlined}
              >
                保存个人偏好
              </Button>,
            ]}
          </SettingsCard>,
        ]}
      </SettingsList>
    );
  }
}

function RoomSettingsDialogImpl(props: RoomSettingsDialogProps) {
  const state = useViewState(() => new _RoomSettingsDialogState(props), props);
  state._appConfig = useAppConfig();
  state._popRoute = useRoute<RoomManagementResult | null>().close;
  return <>{state.build()}</>;
}

/**
 * Port of `RoomSettingsDialog.create(...)`: the create-room form, a
 * [RoomSettingsDialog] in create mode over a local draft room. It returns an
 * element, so it works both as `RoomSettingsDialog.create({...})` and as
 * `<RoomSettingsDialog.create ... />`.
 */
function create(opts: RoomSettingsDialogCreateOptions) {
  return (
    <RoomSettingsDialogImpl
      controller={opts.controller}
      room={_draftCreateRoom(opts.currentUser)}
      currentUser={opts.currentUser}
      isInLive={false}
      onLeaveLive={async () => {}}
      onRoomUpdated={() => {}}
      embedded={opts.embedded ?? false}
      onClose={opts.onClose}
      onResult={opts.onResult}
      stickerImagePreviewOpener={opts.stickerImagePreviewOpener}
      createMode
    />
  );
}

/** 房间设置：房间信息、个人偏好、消息记录、表情包与歌单；创建房间时只有房间信息。 */
export const RoomSettingsDialog = Object.assign(RoomSettingsDialogImpl, { create });

export function _ReadOnlyRoomRid({ value }: { value: string }) {
  return <_ReadOnlyRoomInfoField label="房间 RID" value={value} fieldKey="room-settings-rid" />;
}

export function _ReadOnlyRoomCreatedAt({ value }: { value: string }) {
  return <_ReadOnlyRoomInfoField label="创建时间" value={value} fieldKey="room-settings-created-at" />;
}

export function _ReadOnlyRoomInfoField({ label, value, fieldKey }: { label: string; value: string; fieldKey: string }) {
  return (
    <Column crossAxisAlignment="stretch">
      <_RoomFieldLabel label={label} />
      <SizedBox height={8} />
      <ReadOnlyTextBox value={value} fieldKey={fieldKey} style={{ ...UiTypography.body, color: UiColors.text, fontSize: 13 }} />
    </Column>
  );
}

export interface _LabeledRoomInputProps {
  /** Test id of the field (Dart `fieldKey`). */
  fieldKey: string;
  label: string;
  controller: TextEditingController;
  hintText?: string;
  enabled?: boolean;
  readOnly?: boolean;
  /** `null` grows without limit. */
  maxLines?: number | null;
}

export function _LabeledRoomInput({
  fieldKey,
  label,
  controller,
  hintText = '',
  enabled = true,
  readOnly = false,
  maxLines = 1,
}: _LabeledRoomInputProps) {
  return (
    <Column crossAxisAlignment="stretch">
      <_RoomFieldLabel label={label} />
      <SizedBox height={8} />
      {readOnly ? (
        <ReadOnlyTextBox
          fieldKey={fieldKey}
          value={controller.text}
          maxLines={maxLines}
          style={{ ...UiTypography.body, color: UiColors.text, fontSize: 13 }}
        />
      ) : (
        <div data-testid={fieldKey}>
          <Input controller={controller} hintText={hintText} enabled={enabled} minLines={1} maxLines={maxLines} />
        </div>
      )}
    </Column>
  );
}

export function _draftCreateRoom(currentUser: CurrentUser): RoomDetail {
  const now = new Date(0);
  return new RoomDetail({
    id: '__new_room__',
    name: '',
    visibility: 'public',
    joinPolicy: 'approval_required',
    avatarUrl: null,
    defaultAvatarKey: kDefaultAvatarPresetKey,
    memberCount: 1,
    onlineMemberCount: 1,
    createdBy: currentUser.toSummary(),
    myMembership: new RoomMembership({ joinedAt: now, role: 'owner' }),
    live: new LiveState({
      roomId: '__new_room__',
      participantCount: 0,
      participants: [],
      updatedAt: now,
    }),
    createdAt: now,
    updatedAt: now,
  });
}

/**
 * 房间表情包的数据来源:把 [RoomsController] 的房间作用域接口适配到
 * 通用的 [StickerManagerPanel]。
 */
export class _RoomStickerBackend extends StickerManagerBackend {
  constructor(opts: { controller: RoomsController; roomId: string; canManage: boolean }) {
    super();
    this.controller = opts.controller;
    this.roomId = opts.roomId;
    this.canManage = opts.canManage;
  }

  readonly controller: RoomsController;
  readonly roomId: string;
  readonly canManage: boolean;

  override get scope(): StickerManagementScope {
    return StickerManagementScope.room;
  }

  override get hasApi(): boolean {
    return true;
  }

  override get capabilities(): StickerManagementCapabilities {
    return this.canManage ? new StickerManagementCapabilities() : StickerManagementCapabilities.readOnlyDownloads();
  }

  override loadPacks(): Promise<StickerPack[]> {
    return this.controller.listRoomStickerPacks(this.roomId);
  }

  override createDefaultPack(opts: { sortOrder?: number | null } = {}): Promise<StickerPack> {
    return this.controller.createRoomStickerPack({
      roomId: this.roomId,
      name: defaultStickerPackName(StickerManagementScope.room),
      sortOrder: opts.sortOrder,
    });
  }

  override async uploadImageAsset(opts: { bytes: Uint8Array; filename: string; purpose: string }): Promise<string> {
    const asset = await this.controller.uploadImageAsset({
      bytes: opts.bytes,
      filename: opts.filename,
      purpose: opts.purpose,
    });
    return asset.id;
  }

  override addSticker(opts: { packId: string; assetId: string; name: string; sortOrder?: number | null }): Promise<void> {
    return this.controller.addRoomSticker({
      roomId: this.roomId,
      packId: opts.packId,
      assetId: opts.assetId,
      name: opts.name,
      sortOrder: opts.sortOrder,
    });
  }

  override deleteSticker(opts: { packId: string; stickerId: string }): Promise<void> {
    return this.controller.deleteRoomSticker({
      roomId: this.roomId,
      packId: opts.packId,
      stickerId: opts.stickerId,
    });
  }

  override async renameSticker(opts: { packId: string; stickerId: string; name: string }): Promise<string | null> {
    const updated = await this.controller.updateSticker({
      packId: opts.packId,
      stickerId: opts.stickerId,
      name: opts.name,
    });
    return updated.name;
  }

  override async reorderStickers(opts: { packId: string; stickerIds: string[] }): Promise<void> {
    await this.controller.reorderStickers({ packId: opts.packId, stickerIds: opts.stickerIds });
  }

  override downloadStickers(opts: { stickerIds: string[] }): Promise<DownloadedFile> {
    return this.controller.downloadStickers({ stickerIds: opts.stickerIds });
  }
}
