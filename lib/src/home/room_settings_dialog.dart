part of 'room_management.dart';

enum _RoomSettingsDialogMode { create, edit }

/// 房间设置的分段:房间信息、个人偏好与房间表情包管理。
enum _RoomSettingsSection { info, preferences, messageHistory, stickers }

class RoomSettingsDialog extends StatefulWidget {
  const RoomSettingsDialog({
    super.key,
    required this.controller,
    required this.room,
    required this.currentUser,
    required this.isInLive,
    required this.onLeaveLive,
    required this.onRoomUpdated,
    this.embedded = false,
    this.onClose,
    this.onResult,
    this.stickerImagePreviewOpener,
    this.messageHistoryBuilder,
    bool createMode = false,
  }) : _mode = createMode
           ? _RoomSettingsDialogMode.create
           : _RoomSettingsDialogMode.edit;

  factory RoomSettingsDialog.create({
    Key? key,
    required RoomsController controller,
    required CurrentUser currentUser,
    bool embedded = false,
    VoidCallback? onClose,
    ValueChanged<RoomManagementResult>? onResult,
    StickerImagePreviewOpener? stickerImagePreviewOpener,
  }) {
    return RoomSettingsDialog(
      key: key,
      controller: controller,
      room: _draftCreateRoom(currentUser),
      currentUser: currentUser,
      isInLive: false,
      onLeaveLive: () async {},
      onRoomUpdated: (_) {},
      embedded: embedded,
      onClose: onClose,
      onResult: onResult,
      stickerImagePreviewOpener: stickerImagePreviewOpener,
      createMode: true,
    );
  }

  final RoomsController controller;
  final RoomDetail room;
  final CurrentUser currentUser;
  final bool isInLive;
  final Future<void> Function() onLeaveLive;
  final ValueChanged<RoomDetail> onRoomUpdated;
  final bool embedded;
  final VoidCallback? onClose;
  final ValueChanged<RoomManagementResult>? onResult;
  final StickerImagePreviewOpener? stickerImagePreviewOpener;
  final WidgetBuilder? messageHistoryBuilder;
  final _RoomSettingsDialogMode _mode;

  @override
  State<RoomSettingsDialog> createState() => _RoomSettingsDialogState();
}

class _RoomSettingsDialogState extends State<RoomSettingsDialog> {
  late RoomDetail _room;
  late final TextEditingController _nameController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _remarkNameController;
  late final TextEditingController _roomDisplayNameController;
  late String _visibility;
  late String _joinPolicy;
  late bool _aiVoiceAnnouncementsEnabled;
  late String _notificationPolicy;
  late bool _isPinned;

  late String _defaultAvatarKey;
  late bool _usingPresetAvatar;
  String? _pendingAvatarAssetId;
  String? _pendingAvatarUrl;
  bool _uploadingAvatar = false;

  bool _saving = false;
  bool _savingPreferences = false;
  bool _refreshing = false;
  bool _leaving = false;
  bool _deleting = false;
  bool _changed = false;
  String? _error;
  String? _notice;
  int _floatingNoticeSerial = 0;
  final Map<String, int> _floatingNoticeEventKeys = {};

  bool get _creating => widget._mode == _RoomSettingsDialogMode.create;

  _RoomSettingsSection _section = _RoomSettingsSection.info;

  bool get _canManageRoom =>
      _creating ||
      room_display
          .roomAccessState(room: _room, currentUser: widget.currentUser)
          .canManageRoom;

  bool get _canDeleteRoom =>
      !_creating &&
      room_display
          .roomManagementPermissionState(
            room: _room,
            currentUser: widget.currentUser,
          )
          .canDeleteRoom;

  String get _avatarDisplayName {
    final name = _nameController.text.trim();
    if (name.isNotEmpty) return name;
    return _creating ? '房间' : room_display.roomDisplayName(_room);
  }

  String get _currentUserDefaultDisplayName {
    final displayName = widget.currentUser.displayName.trim();
    if (displayName.isNotEmpty) return displayName;
    return widget.currentUser.username;
  }

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _descriptionController = TextEditingController();
    _remarkNameController = TextEditingController();
    _roomDisplayNameController = TextEditingController();
    _resetFromWidgetRoom(clearTransientFeedback: false);
  }

  @override
  void didUpdateWidget(RoomSettingsDialog oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget._mode != widget._mode ||
        oldWidget.room.id != widget.room.id) {
      _resetFromWidgetRoom();
    }
  }

  void _resetFromWidgetRoom({bool clearTransientFeedback = true}) {
    _room = widget.room;
    _nameController.text = _room.name;
    _descriptionController.text = _room.description;
    _visibility = room_display.normalizeRoomVisibility(_room.visibility);
    _joinPolicy = room_display.normalizeRoomJoinPolicy(_room.joinPolicy);
    _aiVoiceAnnouncementsEnabled = _room.aiVoiceAnnouncementsEnabled;
    _notificationPolicy = room_display.normalizeRoomNotificationPolicy(
      _room.notificationPolicy,
    );
    _isPinned = _room.isPinned;
    _remarkNameController.text = _room.remarkName ?? '';
    _roomDisplayNameController.text = _room.personalProfile.displayName ?? '';
    _defaultAvatarKey = _room.defaultAvatarKey;
    _usingPresetAvatar = _room.avatarUrl == null;
    _pendingAvatarAssetId = null;
    _pendingAvatarUrl = null;
    _uploadingAvatar = false;
    _saving = false;
    _savingPreferences = false;
    _refreshing = false;
    _leaving = false;
    _deleting = false;
    _changed = false;
    _section = _RoomSettingsSection.info;
    if (clearTransientFeedback) {
      _error = null;
      _notice = null;
    }
  }

  @override
  void dispose() {
    _nameController.dispose();
    _descriptionController.dispose();
    _remarkNameController.dispose();
    _roomDisplayNameController.dispose();
    super.dispose();
  }

  void _close() {
    if (widget.embedded) {
      if (!_creating && _changed) {
        widget.onResult?.call(RoomManagementResult.updated(_room));
      }
      widget.onClose?.call();
      return;
    }
    Navigator.of(
      context,
    ).pop(!_creating && _changed ? RoomManagementResult.updated(_room) : null);
  }

  void _emitResult(RoomManagementResult result) {
    if (widget.embedded) {
      widget.onResult?.call(result);
      widget.onClose?.call();
      return;
    }
    Navigator.of(context).pop(result);
  }

  void _markFloatingNoticeEvent(String channel, String? message) {
    if (message == null || message.trim().isEmpty) return;
    _floatingNoticeEventKeys[channel] = ++_floatingNoticeSerial;
  }

  Object? _floatingNoticeEventKey(String channel) {
    return _floatingNoticeEventKeys[channel];
  }

  List<FloatingNotice> _floatingNotices() {
    return [
      if (_notice != null)
        FloatingNotice(
          message: _notice!,
          tone: FloatingNoticeTone.success,
          eventKey: _floatingNoticeEventKey('notice'),
        ),
      if (_error != null)
        FloatingNotice(
          message: _error!,
          tone: FloatingNoticeTone.error,
          duration: null,
          eventKey: _floatingNoticeEventKey('error'),
        ),
    ];
  }

  Future<void> _save() async {
    if (_saving ||
        _savingPreferences ||
        _leaving ||
        _deleting ||
        !_canManageRoom) {
      return;
    }
    final draft = room_forms.roomInfoUpdateDraftFromForm(
      name: _nameController.text,
      description: _descriptionController.text,
      visibility: _visibility,
      joinPolicy: _joinPolicy,
      pendingAvatarAssetId: _pendingAvatarAssetId,
      usingPresetAvatar: _usingPresetAvatar,
      defaultAvatarKey: _defaultAvatarKey,
    );
    if (!draft.isValid) {
      setState(() {
        _error = draft.error;
        _notice = null;
        _markFloatingNoticeEvent('error', _error);
      });
      return;
    }
    try {
      setState(() {
        _saving = true;
      });
      final confirmedJoinPolicyChange =
          await _confirmJoinPolicyAutoReviewIfNeeded(draft.joinPolicy);
      if (!mounted) return;
      if (!confirmedJoinPolicyChange) {
        setState(() {
          _joinPolicy = 'approval_required';
          _saving = false;
          _error = null;
          _notice = null;
        });
        return;
      }
      final updated = _creating
          ? await widget.controller.createRoom(
              name: draft.name!,
              description: draft.description,
              visibility: draft.visibility,
              joinPolicy: draft.joinPolicy,
              avatarAssetId: draft.avatarAssetId,
              defaultAvatarKey: draft.defaultAvatarKey,
            )
          : await widget.controller.updateRoom(
              roomId: _room.id,
              name: draft.name,
              description: draft.description,
              visibility: draft.visibility,
              joinPolicy: draft.joinPolicy,
              avatarAssetId: draft.avatarAssetId,
              defaultAvatarKey: draft.defaultAvatarKey,
            );
      if (!mounted) return;
      if (_creating) {
        _emitResult(RoomManagementResult.created(updated));
        return;
      }
      setState(() {
        _room = updated;
        _notificationPolicy = room_display.normalizeRoomNotificationPolicy(
          updated.notificationPolicy,
        );
        _defaultAvatarKey = updated.defaultAvatarKey;
        _usingPresetAvatar = updated.avatarUrl == null;
        _pendingAvatarAssetId = null;
        _pendingAvatarUrl = null;
        _saving = false;
        _changed = true;
        _error = null;
        _notice = room_display.roomInfoSavedNotice();
        _markFloatingNoticeEvent('notice', _notice);
      });
      widget.onRoomUpdated(updated);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = userFacingErrorMessage(error);
        _notice = null;
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<bool> _confirmJoinPolicyAutoReviewIfNeeded(
    String? nextJoinPolicy,
  ) async {
    if (_creating) return true;
    final currentPolicy = room_display.normalizeRoomJoinPolicy(
      _room.joinPolicy,
    );
    final normalizedNext = room_display.normalizeRoomJoinPolicy(
      nextJoinPolicy ?? currentPolicy,
    );
    if (currentPolicy != 'approval_required') return true;
    if (normalizedNext != 'open' && normalizedNext != 'closed') return true;

    final pendingRequests = await widget.controller.listJoinRequests(_room.id);
    if (!mounted || pendingRequests.isEmpty) return true;

    final decisionLabel = normalizedNext == 'open' ? '批准' : '拒绝';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => DialogFrame(
        title: '确认修改加入方式？',
        icon: Icons.warning_amber_outlined,
        adaptiveActions: [
          ResponsiveDialogAction(
            label: '取消',
            onPressed: () => Navigator.of(context).pop(false),
          ),
          ResponsiveDialogAction(
            label: '确认修改',
            tone: ButtonTone.primary,
            onPressed: () => Navigator.of(context).pop(true),
          ),
        ],
        child: Text(
          '当前仍有 ${pendingRequests.length} 个未处理申请。'
          '确认修改后，将自动$decisionLabel所有未处理申请。',
          style: UiTypography.body.copyWith(color: UiColors.textSecondary),
        ),
      ),
    );
    return confirmed ?? false;
  }

  Future<void> _savePersonalPreferences() async {
    if (_creating || _saving || _savingPreferences || _leaving || _deleting) {
      return;
    }
    setState(() {
      _savingPreferences = true;
    });
    try {
      final draft = room_forms.roomProfileUpdateDraftFromForm(
        remarkName: _remarkNameController.text,
        notificationPolicy: _notificationPolicy,
        usingGlobalProfile: false,
        roomDisplayName: _roomDisplayNameController.text,
        usingProfilePresetAvatar: true,
        defaultAvatarKey: '',
      );
      final updated = await widget.controller.updateMyRoomSettings(
        roomId: _room.id,
        remarkName: draft.remarkName,
        notificationPolicy: draft.notificationPolicy,
        roomDisplayName: draft.roomDisplayName,
        isPinned: _isPinned,
        aiVoiceAnnouncementsEnabled: _aiVoiceAnnouncementsEnabled,
      );
      if (!mounted) return;
      setState(() {
        _room = updated;
        _notificationPolicy = room_display.normalizeRoomNotificationPolicy(
          updated.notificationPolicy,
        );
        _isPinned = updated.isPinned;
        _aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled;
        _remarkNameController.text = updated.remarkName ?? '';
        _roomDisplayNameController.text =
            updated.personalProfile.displayName ?? '';
        _savingPreferences = false;
        _changed = true;
        _error = null;
        _notice = room_display.roomPersonalPreferencesSavedNotice();
        _markFloatingNoticeEvent('notice', _notice);
      });
      widget.onRoomUpdated(updated);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _savingPreferences = false;
        _error = userFacingErrorMessage(error);
        _notice = null;
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _refreshRoom() async {
    if (_creating || _refreshing) return;
    setState(() {
      _refreshing = true;
      _error = null;
      _notice = null;
    });
    try {
      final updated = await widget.controller.getRoom(_room.id);
      if (!mounted) return;
      setState(() {
        _room = updated;
        _nameController.text = updated.name;
        _descriptionController.text = updated.description;
        _visibility = room_display.normalizeRoomVisibility(updated.visibility);
        _joinPolicy = room_display.normalizeRoomJoinPolicy(updated.joinPolicy);
        _aiVoiceAnnouncementsEnabled = updated.aiVoiceAnnouncementsEnabled;
        _notificationPolicy = room_display.normalizeRoomNotificationPolicy(
          updated.notificationPolicy,
        );
        _isPinned = updated.isPinned;
        _remarkNameController.text = updated.remarkName ?? '';
        _roomDisplayNameController.text =
            updated.personalProfile.displayName ?? '';
        _defaultAvatarKey = updated.defaultAvatarKey;
        _usingPresetAvatar = updated.avatarUrl == null;
        _pendingAvatarAssetId = null;
        _pendingAvatarUrl = null;
        _refreshing = false;
      });
      widget.onRoomUpdated(updated);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _refreshing = false;
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _pickAvatar() async {
    if (_uploadingAvatar || _saving) return;
    _CroppedRoomAvatar? cropped;
    try {
      cropped = await _pickAndCropRoomAvatar(context);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = userFacingErrorMessage(error);
        _notice = null;
        _markFloatingNoticeEvent('error', _error);
      });
      return;
    }
    if (cropped == null || !mounted) return;
    setState(() {
      _uploadingAvatar = true;
      _error = null;
      _notice = null;
    });
    try {
      final asset = await widget.controller.uploadImageAsset(
        bytes: cropped.bytes,
        filename: cropped.filename,
        purpose: 'avatar',
      );
      if (!mounted) return;
      setState(() {
        _uploadingAvatar = false;
        _usingPresetAvatar = false;
        _pendingAvatarAssetId = asset.id;
        _pendingAvatarUrl = asset.url;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _uploadingAvatar = false;
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  void _selectPreset(String key) {
    setState(() {
      _usingPresetAvatar = true;
      _defaultAvatarKey = key;
      _pendingAvatarAssetId = null;
      _pendingAvatarUrl = null;
      _error = null;
    });
  }

  String? _avatarPreviewUrl(AppConfig appConfig) {
    return appConfig.resolveAssetUrl(
      room_display.roomManagementAvatarPath(
        usingPresetAvatar: _usingPresetAvatar,
        pendingAvatarUrl: _pendingAvatarUrl,
        roomAvatarUrl: _room.avatarUrl,
      ),
    );
  }

  Future<void> _openRoomAvatarPreview(String? imageUrl) async {
    final url = imageUrl?.trim();
    final opener = widget.stickerImagePreviewOpener;
    if (url == null || url.isEmpty || opener == null) return;
    await opener(
      context,
      imageUrl: url,
      suggestedName: '$_avatarDisplayName-icon.png',
      forceSquare: true,
    );
  }

  Future<void> _leaveRoom() async {
    if (_saving || _savingPreferences || _leaving || _deleting) return;
    final confirmation = room_display.roomLeaveConfirmationSpec(
      room: _room,
      isInLive: widget.isInLive,
    );
    final confirmed = confirmation.requiresStrongConfirmation
        ? await showDialog<bool>(
            context: context,
            builder: (context) => _StrongConfirmDialog(
              title: confirmation.title,
              message: confirmation.body,
              expectedText: confirmation.expectedText!,
              confirmLabel: confirmation.confirmLabel,
            ),
          )
        : await showDialog<bool>(
            context: context,
            builder: (context) => _ConfirmDialog(
              title: confirmation.title,
              message: confirmation.body,
              confirmLabel: confirmation.confirmLabel,
              danger: true,
            ),
          );
    if (confirmed != true || !mounted) return;
    setState(() {
      _leaving = true;
      _error = null;
      _notice = null;
    });
    try {
      if (widget.isInLive) await widget.onLeaveLive();
      await widget.controller.leaveRoom(
        roomId: _room.id,
        confirmDeleteIfEmpty: confirmation.confirmDeleteIfEmpty,
      );
      if (!mounted) return;
      _emitResult(const RoomManagementResult.left());
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _leaving = false;
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  Future<void> _deleteRoom() async {
    if (!_canDeleteRoom ||
        _saving ||
        _savingPreferences ||
        _leaving ||
        _deleting) {
      return;
    }
    final confirmation = room_display.roomDeletionConfirmationSpec(_room);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => _StrongConfirmDialog(
        title: confirmation.title,
        message: confirmation.body,
        expectedText: confirmation.expectedText,
        confirmLabel: confirmation.confirmLabel,
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _deleting = true;
      _error = null;
      _notice = null;
    });
    try {
      if (widget.isInLive) await widget.onLeaveLive();
      await widget.controller.deleteRoom(
        roomId: _room.id,
        confirmName: _room.name,
      );
      if (!mounted) return;
      _emitResult(const RoomManagementResult.deleted());
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _deleting = false;
        _error = userFacingErrorMessage(error);
        _markFloatingNoticeEvent('error', _error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return FloatingNoticeEmitter(
      notices: _floatingNotices(),
      child: _RoomDialogShell(
        title: _creating ? '创建房间' : '房间设置',
        icon: _creating ? Icons.add_circle_outline : Icons.tune,
        maxWidth: _dialogMaxWidth,
        maxHeight: _dialogMaxHeight,
        embedded: widget.embedded,
        onClose: _close,
        headerAction: _creating
            ? null
            : ButtonIcon(
                tooltip: '刷新设置',
                onPressed: _refreshing ? null : _refreshRoom,
                icon: const Icon(Icons.refresh),
                size: 38,
                loading: _refreshing,
              ),
        pinned: _creating
            ? null
            : SegmentedControl<_RoomSettingsSection>(
                expanded: true,
                value: _section,
                onChanged: (section) => setState(() => _section = section),
                segments: [
                  const Segment(
                    value: _RoomSettingsSection.info,
                    label: '房间信息',
                    icon: Icons.info_outline,
                  ),
                  const Segment(
                    value: _RoomSettingsSection.preferences,
                    label: '个人偏好',
                    icon: Icons.tune_outlined,
                  ),
                  if (widget.messageHistoryBuilder != null)
                    const Segment(
                      value: _RoomSettingsSection.messageHistory,
                      label: '消息记录',
                      icon: Icons.history_outlined,
                    ),
                  const Segment(
                    value: _RoomSettingsSection.stickers,
                    label: '表情包',
                    icon: Icons.emoji_emotions_outlined,
                  ),
                ],
              ),
        child: _creating
            ? _buildSettingsBody(context)
            : switch (_section) {
                _RoomSettingsSection.info => _buildSettingsBody(context),
                _RoomSettingsSection.preferences => _buildPreferencesBody(),
                _RoomSettingsSection.messageHistory =>
                  widget.messageHistoryBuilder?.call(context) ??
                      _buildPreferencesBody(),
                _RoomSettingsSection.stickers => StickerManagerPanel(
                  backend: _RoomStickerBackend(
                    controller: widget.controller,
                    roomId: _room.id,
                    canManage: _canManageRoom,
                  ),
                  imagePreviewOpener: widget.stickerImagePreviewOpener,
                  title: '房间表情包',
                  unavailableText: '房间表情包需要登录后从服务端读取',
                ),
              },
      ),
    );
  }

  Widget _buildSettingsBody(BuildContext context) {
    return SettingsList(
      physics: const ClampingScrollPhysics(),
      children: [
        SettingsCard(
          title: '房间信息',
          children: [
            _LabeledRoomInput(
              fieldKey: const ValueKey('room-settings-name-input'),
              label: '名称',
              controller: _nameController,
              enabled: _canManageRoom && !_saving,
              readOnly: !_canManageRoom,
            ),
            _LabeledRoomInput(
              fieldKey: const ValueKey('room-settings-description-input'),
              label: '简介',
              controller: _descriptionController,
              enabled: _canManageRoom && !_saving,
              readOnly: !_canManageRoom,
              maxLines: null,
            ),
            AvatarPicker(
              label: '图标',
              displayName: _avatarDisplayName,
              imageUrl: _avatarPreviewUrl(AppConfigScope.of(context)),
              defaultAvatarKey: _defaultAvatarKey,
              usingPreset: _usingPresetAvatar,
              uploading: _uploadingAvatar,
              enabled: _canManageRoom && !_saving,
              onUpload: _pickAvatar,
              onPresetSelected: _selectPreset,
              onImagePreview: widget.stickerImagePreviewOpener == null
                  ? null
                  : () => unawaited(
                      _openRoomAvatarPreview(
                        _avatarPreviewUrl(AppConfigScope.of(context)),
                      ),
                    ),
              uploadLabel: '上传图标',
            ),
            if (!_creating)
              _ReadOnlyRoomRid(value: room_display.roomIdentifier(_room)),
            if (!_creating)
              _ReadOnlyRoomCreatedAt(
                value: room_display.roomCreatedAtLabel(_room.createdAt),
              ),
            _LabeledSegmented<String>(
              controlKey: const ValueKey('room-settings-visibility-segmented'),
              label: '可见性',
              value: _visibility,
              enabled: _canManageRoom && !_saving,
              segments: const [
                Segment(value: 'public', label: '公开'),
                Segment(value: 'private', label: '私密'),
              ],
              onChanged: (value) => setState(() => _visibility = value),
            ),
            _LabeledSegmented<String>(
              controlKey: const ValueKey('room-settings-join-policy-segmented'),
              label: '加入方式',
              value: _joinPolicy,
              enabled: _canManageRoom && !_saving,
              segments: const [
                Segment(value: 'open', label: '开放'),
                Segment(value: 'approval_required', label: '需审批'),
                Segment(value: 'closed', label: '关闭'),
              ],
              onChanged: (value) => setState(() => _joinPolicy = value),
            ),
            Button(
              width: double.infinity,
              tone: ButtonTone.primary,
              loading: _saving,
              onPressed: _canManageRoom ? _save : null,
              icon: Icon(
                _creating ? Icons.check_circle_outline : Icons.save_outlined,
              ),
              child: Text(_creating ? '确定' : '保存房间设置'),
            ),
          ],
        ),
        if (!_creating)
          SettingsCard(
            title: '离开房间',
            children: [
              Button(
                width: double.infinity,
                tone: ButtonTone.danger,
                loading: _leaving,
                onPressed: _leaveRoom,
                icon: const Icon(Icons.logout),
                child: const Text('离开房间'),
              ),
            ],
          ),
        if (_canDeleteRoom)
          SettingsCard(
            title: '删除房间',
            danger: true,
            children: [
              Button(
                width: double.infinity,
                tone: ButtonTone.danger,
                loading: _deleting,
                onPressed: _deleteRoom,
                icon: const Icon(Icons.delete_forever_outlined),
                child: const Text('删除房间'),
              ),
            ],
          ),
      ],
    );
  }

  Widget _buildPreferencesBody() {
    return SettingsList(
      physics: const ClampingScrollPhysics(),
      children: [
        SettingsCard(
          title: '个人偏好',
          children: [
            _LabeledRoomInput(
              fieldKey: const ValueKey('room-settings-remark-name-input'),
              label: '房间备注名',
              controller: _remarkNameController,
              hintText: _room.name,
              enabled: !_savingPreferences,
            ),
            _LabeledRoomInput(
              fieldKey: const ValueKey('room-settings-room-display-name-input'),
              label: '房间内用户名',
              controller: _roomDisplayNameController,
              hintText: _currentUserDefaultDisplayName,
              enabled: !_savingPreferences,
            ),
            _LabeledSegmented<String>(
              controlKey: const ValueKey(
                'room-settings-notification-policy-segmented',
              ),
              label: '房间消息',
              value: _notificationPolicy,
              enabled: !_savingPreferences,
              segments: const [
                Segment(value: 'all', label: '全部'),
                Segment(value: 'silent', label: '接收但不提醒'),
                Segment(value: 'blocked', label: '屏蔽'),
              ],
              onChanged: (value) {
                setState(() {
                  _notificationPolicy = room_display
                      .normalizeRoomNotificationPolicy(value);
                });
              },
            ),
            _ToggleRow(
              label: '置顶房间',
              value: _isPinned,
              enabled: !_savingPreferences,
              onChanged: (value) => setState(() => _isPinned = value),
            ),
            _ToggleRow(
              label: 'AI 语音播报',
              value: _aiVoiceAnnouncementsEnabled,
              enabled: !_savingPreferences,
              onChanged: (value) =>
                  setState(() => _aiVoiceAnnouncementsEnabled = value),
            ),
            Button(
              width: double.infinity,
              tone: ButtonTone.primary,
              loading: _savingPreferences,
              onPressed: _savePersonalPreferences,
              icon: const Icon(Icons.save_outlined),
              child: const Text('保存个人偏好'),
            ),
          ],
        ),
      ],
    );
  }
}

class _ReadOnlyRoomRid extends StatelessWidget {
  const _ReadOnlyRoomRid({required this.value});

  final String value;

  @override
  Widget build(BuildContext context) {
    return _ReadOnlyRoomInfoField(
      label: '房间 RID',
      value: value,
      fieldKey: const ValueKey('room-settings-rid'),
    );
  }
}

class _ReadOnlyRoomCreatedAt extends StatelessWidget {
  const _ReadOnlyRoomCreatedAt({required this.value});

  final String value;

  @override
  Widget build(BuildContext context) {
    return _ReadOnlyRoomInfoField(
      label: '创建时间',
      value: value,
      fieldKey: const ValueKey('room-settings-created-at'),
    );
  }
}

class _ReadOnlyRoomInfoField extends StatelessWidget {
  const _ReadOnlyRoomInfoField({
    required this.label,
    required this.value,
    required this.fieldKey,
  });

  final String label;
  final String value;
  final Key fieldKey;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _RoomFieldLabel(label),
        const SizedBox(height: 8),
        ReadOnlyTextBox(
          value: value,
          fieldKey: fieldKey,
          style: UiTypography.body.copyWith(color: UiColors.text, fontSize: 13),
        ),
      ],
    );
  }
}

class _LabeledRoomInput extends StatelessWidget {
  const _LabeledRoomInput({
    required this.fieldKey,
    required this.label,
    required this.controller,
    this.hintText = '',
    this.enabled = true,
    this.readOnly = false,
    this.maxLines = 1,
  });

  final Key fieldKey;
  final String label;
  final TextEditingController controller;
  final String hintText;
  final bool enabled;
  final bool readOnly;
  final int? maxLines;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _RoomFieldLabel(label),
        const SizedBox(height: 8),
        if (readOnly)
          ReadOnlyTextBox(
            key: fieldKey,
            value: controller.text,
            maxLines: maxLines,
            style: UiTypography.body.copyWith(
              color: UiColors.text,
              fontSize: 13,
            ),
          )
        else
          Input(
            key: fieldKey,
            controller: controller,
            hintText: hintText,
            enabled: enabled,
            minLines: 1,
            maxLines: maxLines,
          ),
      ],
    );
  }
}

RoomDetail _draftCreateRoom(CurrentUser currentUser) {
  final now = DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);
  return RoomDetail(
    id: '__new_room__',
    name: '',
    visibility: 'public',
    joinPolicy: 'approval_required',
    avatarUrl: null,
    defaultAvatarKey: kDefaultAvatarPresetKey,
    memberCount: 1,
    onlineMemberCount: 1,
    createdBy: currentUser.toSummary(),
    myMembership: RoomMembership(joinedAt: now, role: 'owner'),
    live: LiveState(
      roomId: '__new_room__',
      participantCount: 0,
      participants: const [],
      updatedAt: now,
    ),
    createdAt: now,
    updatedAt: now,
  );
}

/// 房间表情包的数据来源:把 [RoomsController] 的房间作用域接口适配到
/// 通用的 [StickerManagerPanel]。
class _RoomStickerBackend extends StickerManagerBackend {
  _RoomStickerBackend({
    required this.controller,
    required this.roomId,
    required this.canManage,
  });

  final RoomsController controller;
  final String roomId;
  final bool canManage;

  @override
  StickerManagementScope get scope => StickerManagementScope.room;

  @override
  bool get hasApi => true;

  @override
  StickerManagementCapabilities get capabilities => canManage
      ? const StickerManagementCapabilities()
      : const StickerManagementCapabilities.readOnlyDownloads();

  @override
  Future<List<StickerPack>> loadPacks() {
    return controller.listRoomStickerPacks(roomId);
  }

  @override
  Future<StickerPack> createDefaultPack({int? sortOrder}) {
    return controller.createRoomStickerPack(
      roomId: roomId,
      name: defaultStickerPackName(StickerManagementScope.room),
      sortOrder: sortOrder,
    );
  }

  @override
  Future<String> uploadImageAsset({
    required Uint8List bytes,
    required String filename,
    required String purpose,
  }) async {
    final asset = await controller.uploadImageAsset(
      bytes: bytes,
      filename: filename,
      purpose: purpose,
    );
    return asset.id;
  }

  @override
  Future<void> addSticker({
    required String packId,
    required String assetId,
    required String name,
    int? sortOrder,
  }) {
    return controller.addRoomSticker(
      roomId: roomId,
      packId: packId,
      assetId: assetId,
      name: name,
      sortOrder: sortOrder,
    );
  }

  @override
  Future<void> deleteSticker({
    required String packId,
    required String stickerId,
  }) {
    return controller.deleteRoomSticker(
      roomId: roomId,
      packId: packId,
      stickerId: stickerId,
    );
  }

  @override
  Future<String?> renameSticker({
    required String packId,
    required String stickerId,
    required String name,
  }) async {
    final updated = await controller.updateSticker(
      packId: packId,
      stickerId: stickerId,
      name: name,
    );
    return updated.name;
  }

  @override
  Future<void> reorderStickers({
    required String packId,
    required List<String> stickerIds,
  }) {
    return controller.reorderStickers(packId: packId, stickerIds: stickerIds);
  }

  @override
  Future<DownloadedFile> downloadStickers({required List<String> stickerIds}) {
    return controller.downloadStickers(stickerIds: stickerIds);
  }
}
