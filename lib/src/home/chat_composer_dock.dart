part of 'chat_pane.dart';

const _stickerPanelHeight = 268.0;
const _stickerTileSize = 56.0;
const _voicePanelHeight = 150.0;
const _mentionPanelWidth = 300.0;
const _mentionPanelVisibleRows = 4;
const _mentionPanelEmptyHeight = 52.0;
const _mentionPanelVerticalPadding = 12.0;
const _mentionPanelTileHeight = 44.0;
const _mentionPanelTileGap = 2.0;
const _mentionPanelHeight =
    _mentionPanelVerticalPadding +
    _mentionPanelVisibleRows * _mentionPanelTileHeight +
    (_mentionPanelVisibleRows - 1) * _mentionPanelTileGap;
const _mentionPanelGap = 8.0;

class _ComposerDock extends StatelessWidget {
  const _ComposerDock({
    required this.controller,
    required this.composerController,
    required this.timestampNow,
    required this.sending,
    required this.sendError,
    required this.stickerPanel,
    required this.voiceState,
    required this.attachments,
    required this.quotes,
    required this.onRemoveQuote,
    required this.component,
    required this.onRemoveComponent,
    required this.imagePreviewActions,
    required this.fileActionHighlighted,
    required this.mentionOptions,
    required this.mentionLoading,
    required this.mentionSelectedIndex,
    required this.onSelectMention,
    required this.inputFormatters,
    required this.onNavigateMentionSelection,
    required this.onConfirmMentionSelection,
    required this.onHighlightMentionSelection,
    required this.onSubmit,
    required this.onSendSticker,
    required this.onOpenStickers,
    required this.onRefreshStickers,
    required this.onStickerSourceChanged,
    required this.onUploadFirstPersonalSticker,
    required this.onUploadFirstRoomSticker,
    required this.onStartVoice,
    required this.onSendVoice,
    required this.onCancelVoice,
    required this.onPickFile,
    required this.onPasteFiles,
    required this.onCanPasteFiles,
    required this.onRemoveAttachment,
    required this.onRetryAttachment,
    this.dropKey,
  });

  final TextEditingController controller;
  final ChatComposerController composerController;
  final DateTime timestampNow;
  final bool sending;
  final String? sendError;
  final sticker_display.StickerPanelLoadState stickerPanel;
  final voice_display.VoiceRecorderState voiceState;
  final List<composer_attachment.ComposerAttachmentView> attachments;
  final List<MessageQuote> quotes;
  final ValueChanged<String>? onRemoveQuote;
  final Message? component;
  final VoidCallback? onRemoveComponent;
  final ChatImagePreviewActions imagePreviewActions;
  final bool fileActionHighlighted;
  final List<message_mentions.MessageMentionOption> mentionOptions;
  final bool mentionLoading;
  final int mentionSelectedIndex;
  final ValueChanged<message_mentions.MessageMentionOption>? onSelectMention;
  final List<TextInputFormatter>? inputFormatters;
  final bool Function(ComposerSuggestionNavigation navigation)?
  onNavigateMentionSelection;
  final bool Function(ComposerSuggestionAction action)?
  onConfirmMentionSelection;
  final ValueChanged<int>? onHighlightMentionSelection;
  final ValueChanged<String> onSubmit;
  final ValueChanged<Sticker> onSendSticker;
  final VoidCallback onOpenStickers;
  final VoidCallback onRefreshStickers;
  final ValueChanged<sticker_display.StickerPanelSource> onStickerSourceChanged;
  final VoidCallback? onUploadFirstPersonalSticker;
  final VoidCallback? onUploadFirstRoomSticker;
  final VoidCallback onStartVoice;
  final VoidCallback onSendVoice;
  final VoidCallback onCancelVoice;
  final VoidCallback onPickFile;
  final Future<bool> Function() onPasteFiles;
  final Future<bool> Function()? onCanPasteFiles;
  final ValueChanged<String> onRemoveAttachment;
  final ValueChanged<String> onRetryAttachment;
  final Key? dropKey;

  @override
  Widget build(BuildContext context) {
    return FloatingNoticeEmitter(
      notices: [
        if (sendError != null)
          FloatingNotice(
            message: sendError!,
            tone: FloatingNoticeTone.error,
            duration: null,
          ),
      ],
      child: SafeArea(
        top: false,
        minimum: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(
            _composerHorizontalInset,
            0,
            _composerHorizontalInset,
            _composerBottomInset,
          ),
          child: _ComposerMentionOverlay(
            visible: mentionOptions.isNotEmpty || mentionLoading,
            panel: _ComposerMentionPanel(
              options: mentionOptions,
              loading: mentionLoading,
              selectedIndex: mentionSelectedIndex,
              onHighlight: onHighlightMentionSelection,
              onSelected: onSelectMention,
            ),
            child: ChatComposer(
              key: dropKey,
              controller: controller,
              composerController: composerController,
              hintText: '写点什么…',
              maxLines: 5,
              onSubmitted: onSubmit,
              suggestionShortcutsEnabled: mentionOptions.isNotEmpty,
              onSuggestionNavigationPressed: onNavigateMentionSelection,
              onSuggestionActionPressed: onConfirmMentionSelection,
              inputFormatters: inputFormatters,
              onPasteFiles: onPasteFiles,
              onCanPasteFiles: onCanPasteFiles,
              header: quotes.isEmpty && component == null
                  ? null
                  : Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (var index = 0; index < quotes.length; index++) ...[
                          if (index > 0) const SizedBox(height: 6),
                          _MessageQuoteCard(
                            quote: quotes[index],
                            timestampNow: timestampNow,
                            onClose: onRemoveQuote == null
                                ? null
                                : () => onRemoveQuote!(quotes[index].messageId),
                            compact: true,
                            imagePreviewActions: imagePreviewActions,
                          ),
                        ],
                        if (component != null) ...[
                          if (quotes.isNotEmpty) const SizedBox(height: 6),
                          _ComposerMessageComponentCard(
                            message: component!,
                            onClose: onRemoveComponent,
                          ),
                        ],
                      ],
                    ),
              attachments: attachments.isEmpty
                  ? null
                  : _ComposerAttachmentStrip(
                      attachments: attachments,
                      onRemove: onRemoveAttachment,
                      onRetry: onRetryAttachment,
                    ),
              actions: [
                ComposerAction(
                  id: 'stickers',
                  icon: Icons.emoji_emotions_outlined,
                  label: '表情',
                  onPressed: onOpenStickers,
                  panel: ComposerPanel.static(
                    height: _stickerPanelHeight,
                    padding: const EdgeInsets.fromLTRB(12, 12, 12, 12),
                    child: _StickerPanel(
                      state: stickerPanel,
                      onSendSticker: onSendSticker,
                      onRefresh: onRefreshStickers,
                      onSourceChanged: onStickerSourceChanged,
                      onUploadFirstPersonalSticker:
                          onUploadFirstPersonalSticker,
                      onUploadFirstRoomSticker: onUploadFirstRoomSticker,
                    ),
                  ),
                ),
                ComposerAction(
                  id: 'voice',
                  icon: Icons.mic_none,
                  label: '语音',
                  onPressed: onCancelVoice,
                  panel: ComposerPanel.static(
                    height: _voicePanelHeight,
                    child: _VoicePanel(
                      state: voiceState,
                      onStart: onStartVoice,
                      onSend: onSendVoice,
                      onCancel: onCancelVoice,
                    ),
                  ),
                ),
                ComposerAction(
                  id: 'file',
                  icon: Icons.attach_file,
                  label: '文件',
                  selected: fileActionHighlighted,
                  onPressed: onPickFile,
                ),
                ComposerAction(
                  id: 'send',
                  icon: Icons.send_rounded,
                  label: '发送',
                  tooltip: sending ? '发送中' : '发送',
                  tone: ButtonTone.primary,
                  alignment: ComposerActionAlignment.trailing,
                  onPressed: () => onSubmit(controller.text),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ComposerMessageComponentCard extends StatelessWidget {
  const _ComposerMessageComponentCard({
    required this.message,
    required this.onClose,
  });

  final Message message;
  final VoidCallback? onClose;

  @override
  Widget build(BuildContext context) {
    final playlist = message.playlistAttachment?.playlist;
    final track = message.musicTrackAttachment?.track;
    final isPlaylist = playlist != null;
    final title = playlist?.name ?? track?.title ?? message.body.trim();
    final artists =
        track?.artists
            .map((value) => value.trim())
            .where((value) => value.isNotEmpty)
            .join(' / ') ??
        '';
    final subtitle = isPlaylist
        ? '${playlist.itemCount} 首歌曲'
        : artists.isEmpty
        ? '歌曲'
        : artists;
    return Container(
      key: const ValueKey('composer-message-component'),
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(10, 8, 6, 8),
      decoration: BoxDecoration(
        color: UiColors.surfaceRaised,
        border: Border.all(color: UiColors.border),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(
            isPlaylist ? Icons.queue_music_outlined : Icons.music_note,
            color: UiColors.accent,
            size: 22,
          ),
          const SizedBox(width: 9),
          Expanded(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: UiTypography.body.copyWith(
                    color: UiColors.text,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: UiTypography.label.copyWith(color: UiColors.textMuted),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            key: const ValueKey('remove-composer-message-component'),
            onPressed: onClose,
            icon: const Icon(Icons.close),
            iconSize: 16,
            color: UiColors.textMuted,
            tooltip: '移除组件',
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            splashRadius: 16,
          ),
        ],
      ),
    );
  }
}

class _ComposerMentionOverlay extends StatefulWidget {
  const _ComposerMentionOverlay({
    required this.visible,
    required this.panel,
    required this.child,
  });

  final bool visible;
  final Widget panel;
  final Widget child;

  @override
  State<_ComposerMentionOverlay> createState() =>
      _ComposerMentionOverlayState();
}

class _ComposerMentionOverlayState extends State<_ComposerMentionOverlay> {
  final GlobalKey _anchorKey = GlobalKey();
  final OverlayPortalController _portal = OverlayPortalController();

  @override
  void didUpdateWidget(_ComposerMentionOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    _schedulePortalSync();
  }

  @override
  void dispose() {
    if (_portal.isShowing) _portal.hide();
    super.dispose();
  }

  void _schedulePortalSync() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.visible) {
        if (!_portal.isShowing) _portal.show();
      } else if (_portal.isShowing) {
        _portal.hide();
      }
    });
  }

  Rect? _anchorRectInOverlay() {
    final anchorContext = _anchorKey.currentContext;
    final overlay = Overlay.maybeOf(context);
    final anchorBox = anchorContext?.findRenderObject();
    final overlayBox = overlay?.context.findRenderObject();
    if (anchorBox is! RenderBox ||
        overlayBox is! RenderBox ||
        !anchorBox.hasSize ||
        !overlayBox.hasSize) {
      return null;
    }

    final topLeft = anchorBox.localToGlobal(Offset.zero, ancestor: overlayBox);
    return topLeft & anchorBox.size;
  }

  @override
  Widget build(BuildContext context) {
    _schedulePortalSync();
    return OverlayPortal(
      controller: _portal,
      overlayChildBuilder: (context) {
        return Positioned.fill(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final anchorRect = _anchorRectInOverlay();
              if (anchorRect == null) return const SizedBox.shrink();
              return CustomSingleChildLayout(
                delegate: _ComposerMentionOverlayLayoutDelegate(
                  anchorRect: anchorRect,
                ),
                child: widget.panel,
              );
            },
          ),
        );
      },
      child: KeyedSubtree(key: _anchorKey, child: widget.child),
    );
  }
}

class _ComposerMentionOverlayLayoutDelegate extends SingleChildLayoutDelegate {
  const _ComposerMentionOverlayLayoutDelegate({required this.anchorRect});

  final Rect anchorRect;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    return BoxConstraints.loose(constraints.biggest);
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final maxLeft = math.max(0.0, size.width - childSize.width);
    final left = (anchorRect.left + 4).clamp(0.0, maxLeft).toDouble();
    final maxTop = math.max(0.0, size.height - childSize.height);
    final top = (anchorRect.top - _mentionPanelGap - childSize.height)
        .clamp(0.0, maxTop)
        .toDouble();
    return Offset(left, top);
  }

  @override
  bool shouldRelayout(_ComposerMentionOverlayLayoutDelegate oldDelegate) {
    return oldDelegate.anchorRect != anchorRect;
  }
}

class _ComposerMentionPanel extends StatefulWidget {
  const _ComposerMentionPanel({
    required this.options,
    required this.loading,
    required this.selectedIndex,
    required this.onHighlight,
    required this.onSelected,
  });

  final List<message_mentions.MessageMentionOption> options;
  final bool loading;
  final int selectedIndex;
  final ValueChanged<int>? onHighlight;
  final ValueChanged<message_mentions.MessageMentionOption>? onSelected;

  @override
  State<_ComposerMentionPanel> createState() => _ComposerMentionPanelState();
}

class _ComposerMentionPanelState extends State<_ComposerMentionPanel> {
  late final ScrollController _scrollController = ScrollController();

  @override
  void didUpdateWidget(_ComposerMentionPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.selectedIndex != widget.selectedIndex ||
        oldWidget.options.length != widget.options.length) {
      _scheduleSelectedVisible();
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _scheduleSelectedVisible();
  }

  @override
  Widget build(BuildContext context) {
    final config = AppConfigScope.of(context);
    final rows = widget.options;
    final height = _mentionPanelHeightFor(rows.length);
    final selectedIndex = _effectiveSelectedIndex(rows.length);
    return SizedBox(
      width: _mentionPanelWidth,
      height: height,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: UiColors.surfaceRaised,
          borderRadius: BorderRadius.circular(UiRadii.md),
          border: Border.all(color: UiColors.borderStrong),
          boxShadow: const [
            BoxShadow(
              color: Color(0x66000000),
              blurRadius: 18,
              offset: Offset(0, 10),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(UiRadii.md),
          child: rows.isEmpty
              ? SizedBox(
                  child: Center(
                    child: widget.loading
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: UiColors.accent,
                            ),
                          )
                        : Text(
                            '没有匹配的成员',
                            style: UiTypography.label.copyWith(
                              color: UiColors.textMuted,
                            ),
                          ),
                  ),
                )
              : Scrollbar(
                  controller: _scrollController,
                  thumbVisibility: rows.length > 4,
                  child: ListView.separated(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(vertical: 6),
                    itemCount: rows.length,
                    separatorBuilder: (context, index) =>
                        const SizedBox(height: _mentionPanelTileGap),
                    itemBuilder: (context, index) {
                      final option = rows[index];
                      final user = option.member?.user;
                      final nameColor = option.isUser
                          ? roleBadgeForegroundColorForLabel(option.roleLabel)
                          : UiColors.accent;
                      return _ComposerMentionTile(
                        option: option,
                        avatarUrl: config.resolveAssetUrl(user?.avatarUrl),
                        nameColor: nameColor,
                        highlighted: selectedIndex == index,
                        onHover: () => widget.onHighlight?.call(index),
                        onSelected: widget.onSelected,
                      );
                    },
                  ),
                ),
        ),
      ),
    );
  }

  int _effectiveSelectedIndex(int rowCount) {
    if (rowCount <= 0) return 0;
    return widget.selectedIndex.clamp(0, rowCount - 1).toInt();
  }

  void _scheduleSelectedVisible() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      final rowCount = widget.options.length;
      if (rowCount <= 0) return;
      final index = _effectiveSelectedIndex(rowCount);
      final rowTop =
          6.0 + index * (_mentionPanelTileHeight + _mentionPanelTileGap);
      final rowBottom = rowTop + _mentionPanelTileHeight;
      final position = _scrollController.position;
      var target = position.pixels;
      if (rowTop < position.pixels) {
        target = rowTop;
      } else if (rowBottom > position.pixels + position.viewportDimension) {
        target = rowBottom - position.viewportDimension;
      }
      target = target
          .clamp(position.minScrollExtent, position.maxScrollExtent)
          .toDouble();
      if ((target - position.pixels).abs() < 0.5) return;
      _scrollController.animateTo(
        target,
        duration: const Duration(milliseconds: 90),
        curve: Curves.easeOutCubic,
      );
    });
  }
}

double _mentionPanelHeightFor(int rowCount) {
  if (rowCount <= 0) return _mentionPanelEmptyHeight;
  if (rowCount >= _mentionPanelVisibleRows) return _mentionPanelHeight;
  return _mentionPanelVerticalPadding +
      rowCount * _mentionPanelTileHeight +
      (rowCount - 1) * _mentionPanelTileGap;
}

class _ComposerMentionTile extends StatelessWidget {
  const _ComposerMentionTile({
    required this.option,
    required this.avatarUrl,
    required this.nameColor,
    required this.highlighted,
    required this.onHover,
    required this.onSelected,
  });

  final message_mentions.MessageMentionOption option;
  final String? avatarUrl;
  final Color nameColor;
  final bool highlighted;
  final VoidCallback onHover;
  final ValueChanged<message_mentions.MessageMentionOption>? onSelected;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => onHover(),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onSelected == null ? null : () => onSelected!(option),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 120),
          curve: Curves.easeOutCubic,
          margin: const EdgeInsets.symmetric(horizontal: 6),
          padding: const EdgeInsets.symmetric(horizontal: 8),
          constraints: const BoxConstraints.tightFor(
            height: _mentionPanelTileHeight,
          ),
          decoration: BoxDecoration(
            color: highlighted ? UiColors.selected : Colors.transparent,
            borderRadius: BorderRadius.circular(UiRadii.sm),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              _ComposerMentionTileLeading(option: option, avatarUrl: avatarUrl),
              const SizedBox(width: 10),
              Flexible(
                child: Text(
                  option.label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: UiTypography.body.copyWith(
                    color: nameColor,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ComposerMentionTileLeading extends StatelessWidget {
  const _ComposerMentionTileLeading({
    required this.option,
    required this.avatarUrl,
  });

  final message_mentions.MessageMentionOption option;
  final String? avatarUrl;

  @override
  Widget build(BuildContext context) {
    final user = option.member?.user;
    if (user != null) {
      return Avatar(
        label: room_display.userAvatarLabel(user),
        imageUrl: avatarUrl,
        defaultAvatarKey: user.defaultAvatarKey,
        size: 30,
        showBorder: false,
      );
    }
    final icon = switch (option.kind) {
      message_mentions.MessageMentionKind.everyone => Icons.campaign_outlined,
      message_mentions.MessageMentionKind.admins =>
        Icons.admin_panel_settings_outlined,
      message_mentions.MessageMentionKind.user => Icons.alternate_email,
    };
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.selected,
        shape: BoxShape.circle,
        border: Border.all(color: UiColors.selectedBorder),
      ),
      child: SizedBox.square(
        dimension: 30,
        child: Icon(icon, color: UiColors.accent, size: 17),
      ),
    );
  }
}

/// The composer's sticker picker. Shows the user's personal packs and the
/// current room's packs behind a source toggle, sending the real managed
/// sticker (asset + id) rather than a decorative placeholder.
class _StickerPanel extends StatelessWidget {
  const _StickerPanel({
    required this.state,
    required this.onSendSticker,
    required this.onRefresh,
    required this.onSourceChanged,
    required this.onUploadFirstPersonalSticker,
    required this.onUploadFirstRoomSticker,
  });

  final sticker_display.StickerPanelLoadState state;
  final ValueChanged<Sticker> onSendSticker;
  final VoidCallback onRefresh;
  final ValueChanged<sticker_display.StickerPanelSource> onSourceChanged;
  final VoidCallback? onUploadFirstPersonalSticker;
  final VoidCallback? onUploadFirstRoomSticker;

  @override
  Widget build(BuildContext context) {
    final stickers = sticker_display.stickerPanelStickers(
      source: state.source,
      personalPacks: state.personalPacks,
      roomPacks: state.roomPacks,
    );
    final bodyState = sticker_display.stickerPanelBodyState(
      loading: state.loading,
      error: state.error,
      stickers: stickers,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SegmentedControl<sticker_display.StickerPanelSource>(
          value: state.source,
          expanded: true,
          onChanged: onSourceChanged,
          segments: const [
            Segment(
              value: sticker_display.StickerPanelSource.personal,
              label: '我的表情包',
            ),
            Segment(
              value: sticker_display.StickerPanelSource.room,
              label: '房间表情包',
            ),
          ],
        ),
        const SizedBox(height: 12),
        Expanded(
          child: switch (bodyState) {
            sticker_display.StickerPanelBodyState.loading =>
              const _StickerPanelCentered(
                child: SizedBox.square(
                  dimension: 22,
                  child: CircularProgressIndicator(
                    strokeWidth: 2.2,
                    color: UiColors.accent,
                  ),
                ),
              ),
            sticker_display.StickerPanelBodyState.error => _StickerPanelMessage(
              icon: Icons.warning_amber_rounded,
              text: state.error ?? '加载表情失败',
              actionKey: const ValueKey<String>('sticker-panel-refresh'),
              actionIcon: Icons.refresh,
              actionLabel: '刷新',
              onAction: onRefresh,
            ),
            sticker_display.StickerPanelBodyState.empty => _StickerPanelMessage(
              icon: Icons.emoji_emotions_outlined,
              text: sticker_display.stickerPanelEmptyText(state.source),
              actionKey: ValueKey<String>(
                state.source == sticker_display.StickerPanelSource.personal
                    ? 'sticker-panel-upload-first-personal'
                    : 'sticker-panel-upload-first-room',
              ),
              actionIcon: Icons.upload_file_outlined,
              actionLabel: '上传第一个表情',
              actionTone: ButtonTone.primary,
              onAction:
                  state.source == sticker_display.StickerPanelSource.personal
                  ? onUploadFirstPersonalSticker
                  : onUploadFirstRoomSticker,
            ),
            sticker_display.StickerPanelBodyState.results => _StickerList(
              stickers: stickers,
              onSendSticker: onSendSticker,
            ),
          },
        ),
      ],
    );
  }
}

class _StickerList extends StatelessWidget {
  const _StickerList({required this.stickers, required this.onSendSticker});

  final List<Sticker> stickers;
  final ValueChanged<Sticker> onSendSticker;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: EdgeInsets.zero,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final sticker in stickers)
              _StickerTile(
                sticker: sticker,
                onPressed: () => onSendSticker(sticker),
              ),
          ],
        ),
      ],
    );
  }
}

class _StickerTile extends StatelessWidget {
  const _StickerTile({required this.sticker, required this.onPressed});

  final Sticker sticker;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final imageUrl = AppConfigScope.of(
      context,
    ).resolveAssetUrl(sticker.asset.thumbnailUrl ?? sticker.asset.url);

    return Tooltip(
      message: sticker.name,
      child: PressableSurface(
        width: _stickerTileSize,
        height: _stickerTileSize,
        onPressed: onPressed,
        padding: const EdgeInsets.all(6),
        backgroundColor: UiColors.surfaceLow,
        borderColor: UiColors.border,
        child: Center(
          child: imageUrl == null
              ? const Icon(
                  Icons.image_not_supported_outlined,
                  color: UiColors.textMuted,
                  size: 20,
                )
              : CachedAssetImage(
                  url: imageUrl,
                  filename: sticker.asset.filename,
                  mimeType: sticker.asset.mimeType,
                  expectedBytes: sticker.asset.sizeBytes,
                  fit: BoxFit.contain,
                  errorBuilder: (context, error, stackTrace) => const Icon(
                    Icons.broken_image_outlined,
                    color: UiColors.textMuted,
                    size: 20,
                  ),
                ),
        ),
      ),
    );
  }
}

class _StickerPanelCentered extends StatelessWidget {
  const _StickerPanelCentered({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Center(child: child);
  }
}

class _StickerPanelMessage extends StatelessWidget {
  const _StickerPanelMessage({
    required this.icon,
    required this.text,
    this.actionKey,
    this.actionIcon,
    this.actionLabel,
    this.actionTone = ButtonTone.neutral,
    this.onAction,
  });

  final IconData icon;
  final String text;
  final Key? actionKey;
  final IconData? actionIcon;
  final String? actionLabel;
  final ButtonTone actionTone;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final actionWidth = actionLabel == null
        ? null
        : Button.minimumWidthForLabel(
            context,
            label: actionLabel!,
            hasIcon: actionIcon != null,
          );

    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: UiColors.textMuted, size: 26),
          const SizedBox(height: 10),
          Text(
            text,
            maxLines: 2,
            textAlign: TextAlign.center,
            overflow: TextOverflow.ellipsis,
            style: UiTypography.label.copyWith(color: UiColors.textMuted),
          ),
          if (actionLabel != null && onAction != null) ...[
            const SizedBox(height: 12),
            Button(
              key: actionKey,
              icon: actionIcon == null ? null : Icon(actionIcon),
              tone: actionTone,
              width: actionWidth,
              onPressed: onAction,
              child: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}

/// The strip of staged-file chips shown above the composer input. Each chip
/// names a file queued for the next message and offers a remove button. The
/// files ride out as attachments when the message is sent.
class _ComposerAttachmentStrip extends StatelessWidget {
  const _ComposerAttachmentStrip({
    required this.attachments,
    required this.onRemove,
    required this.onRetry,
  });

  final List<composer_attachment.ComposerAttachmentView> attachments;
  final ValueChanged<String> onRemove;
  final ValueChanged<String> onRetry;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Wrap(
        spacing: 8,
        runSpacing: 8,
        children: [
          for (final attachment in attachments)
            _ComposerAttachmentChip(
              attachment: attachment,
              onRemove: () => onRemove(attachment.id),
              onRetry: () => onRetry(attachment.id),
            ),
        ],
      ),
    );
  }
}

class _ComposerAttachmentChip extends StatelessWidget {
  const _ComposerAttachmentChip({
    required this.attachment,
    required this.onRemove,
    required this.onRetry,
  });

  final composer_attachment.ComposerAttachmentView attachment;
  final VoidCallback onRemove;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final size = attachment.sizeLabel;
    final failed = attachment.hasFailed;
    final subtitle = switch (attachment.status) {
      composer_attachment.ComposerAttachmentStatus.failed =>
        attachment.errorMessage ?? '上传失败，点击重试',
      composer_attachment.ComposerAttachmentStatus.uploading =>
        attachment.progress == null
            ? '上传中…'
            : '上传中 ${(attachment.progress! * 100).round()}%',
      composer_attachment.ComposerAttachmentStatus.uploaded => size,
    };
    return DecoratedBox(
      decoration: BoxDecoration(
        color: UiColors.surface,
        borderRadius: BorderRadius.circular(UiRadii.md),
        border: Border.all(color: failed ? UiColors.danger : UiColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 6, 4, 6),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            _ComposerAttachmentLeading(
              attachment: attachment,
              onRetry: onRetry,
            ),
            const SizedBox(width: 8),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 180),
              child: Tooltip(
                message: attachment.filename,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      attachment.filename,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: UiTypography.label.copyWith(color: UiColors.text),
                    ),
                    if (subtitle != null)
                      Text(
                        subtitle,
                        style: UiTypography.label.copyWith(
                          color: failed ? UiColors.danger : UiColors.textMuted,
                          fontSize: 11,
                        ),
                      ),
                  ],
                ),
              ),
            ),
            IconButton(
              onPressed: onRemove,
              icon: const Icon(Icons.close),
              iconSize: 16,
              color: UiColors.textMuted,
              splashRadius: 16,
              tooltip: '移除',
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            ),
          ],
        ),
      ),
    );
  }
}

/// The chip's leading slot: a spinner while uploading, a tappable retry icon
/// when the upload failed, and the file-type glyph once it has landed.
class _ComposerAttachmentLeading extends StatelessWidget {
  const _ComposerAttachmentLeading({
    required this.attachment,
    required this.onRetry,
  });

  final composer_attachment.ComposerAttachmentView attachment;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    switch (attachment.status) {
      case composer_attachment.ComposerAttachmentStatus.uploading:
        return SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            value: attachment.progress,
            color: UiColors.accent,
          ),
        );
      case composer_attachment.ComposerAttachmentStatus.failed:
        return InkResponse(
          onTap: onRetry,
          radius: 16,
          child: const Icon(Icons.refresh, size: 18, color: UiColors.danger),
        );
      case composer_attachment.ComposerAttachmentStatus.uploaded:
        return Icon(
          composer_attachment.composerAttachmentGlyph(
            mimeType: attachment.mimeType,
            filename: attachment.filename,
          ),
          size: 18,
          color: UiColors.textSecondary,
        );
    }
  }
}

class _VoicePanel extends StatelessWidget {
  const _VoicePanel({
    required this.state,
    required this.onStart,
    required this.onSend,
    required this.onCancel,
  });

  final voice_display.VoiceRecorderState state;
  final VoidCallback onStart;
  final VoidCallback onSend;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: switch (state.phase) {
        voice_display.VoiceRecorderPhase.idle => _VoiceIdle(
          error: state.error,
          onStart: onStart,
        ),
        voice_display.VoiceRecorderPhase.recording => _VoiceActive(
          elapsed: state.elapsed,
          recording: true,
          busy: false,
          onSend: onSend,
          onCancel: onCancel,
        ),
        voice_display.VoiceRecorderPhase.review => _VoiceActive(
          elapsed: state.elapsed,
          recording: false,
          busy: false,
          error: state.error,
          onSend: onSend,
          onCancel: onCancel,
        ),
        voice_display.VoiceRecorderPhase.sending => _VoiceActive(
          elapsed: state.elapsed,
          recording: false,
          busy: true,
          onSend: onSend,
          onCancel: onCancel,
        ),
      },
    );
  }
}

class _VoiceIdle extends StatelessWidget {
  const _VoiceIdle({required this.onStart, this.error});

  final VoidCallback onStart;
  final String? error;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const SizedBox(height: 14),
        // Plain icon trigger — no raised surface or circular background.
        IconButton(
          onPressed: onStart,
          icon: const Icon(Icons.mic_none),
          iconSize: 30,
          color: UiColors.accent,
          tooltip: '点击录音',
          splashRadius: 24,
        ),
        const SizedBox(height: 8),
        Text(
          error ?? '点击录音',
          textAlign: TextAlign.center,
          style: UiTypography.label.copyWith(
            color: error != null ? UiColors.danger : UiColors.textMuted,
          ),
        ),
      ],
    );
  }
}

class _VoiceActive extends StatelessWidget {
  const _VoiceActive({
    required this.elapsed,
    required this.recording,
    required this.busy,
    required this.onSend,
    required this.onCancel,
    this.error,
  });

  final Duration elapsed;
  final bool recording;
  final bool busy;
  final VoidCallback onSend;
  final VoidCallback onCancel;
  final String? error;

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const SizedBox(height: 14),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              recording ? Icons.fiber_manual_record : Icons.mic_none,
              size: 16,
              color: recording ? UiColors.danger : UiColors.textSecondary,
            ),
            const SizedBox(width: 8),
            Text(
              voice_display.formatVoiceDuration(elapsed),
              style: UiTypography.body.copyWith(
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          error ?? (recording ? '正在录音…' : '点击发送或取消'),
          textAlign: TextAlign.center,
          style: UiTypography.label.copyWith(
            color: error != null ? UiColors.danger : UiColors.textMuted,
          ),
        ),
        const SizedBox(height: 12),
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            ButtonIcon(
              onPressed: busy ? null : onCancel,
              icon: const Icon(Icons.delete_outline),
              tooltip: '取消',
              tone: ButtonTone.danger,
              size: 34,
            ),
            const SizedBox(width: 10),
            ButtonIcon(
              onPressed: busy ? null : onSend,
              icon: const Icon(Icons.send_rounded),
              tooltip: recording ? '完成并发送' : '发送',
              tone: ButtonTone.primary,
              loading: busy,
              size: 34,
            ),
          ],
        ),
      ],
    );
  }
}

/// Test-only entry point for the otherwise-private composer attachment strip,
/// so widget tests can pump it directly without standing up the whole dock.
@visibleForTesting
class ComposerAttachmentStripForTest extends StatelessWidget {
  const ComposerAttachmentStripForTest({
    super.key,
    required this.attachments,
    required this.onRemove,
    this.onRetry,
  });

  final List<composer_attachment.ComposerAttachmentView> attachments;
  final ValueChanged<String> onRemove;
  final ValueChanged<String>? onRetry;

  @override
  Widget build(BuildContext context) {
    return _ComposerAttachmentStrip(
      attachments: attachments,
      onRemove: onRemove,
      onRetry: onRetry ?? (_) {},
    );
  }
}

/// Test-only entry point for the otherwise-private voice panel, so widget
/// tests can pump it directly without standing up the whole composer dock.
@visibleForTesting
class VoicePanelForTest extends StatelessWidget {
  const VoicePanelForTest({
    super.key,
    required this.state,
    required this.onStart,
    required this.onSend,
    required this.onCancel,
  });

  final voice_display.VoiceRecorderState state;
  final VoidCallback onStart;
  final VoidCallback onSend;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return _VoicePanel(
      state: state,
      onStart: onStart,
      onSend: onSend,
      onCancel: onCancel,
    );
  }
}

/// Test-only entry point for the otherwise-private sticker panel.
@visibleForTesting
class StickerPanelForTest extends StatelessWidget {
  const StickerPanelForTest({
    super.key,
    required this.state,
    required this.onSendSticker,
    required this.onRefresh,
    required this.onSourceChanged,
    this.onUploadFirstPersonalSticker,
    this.onUploadFirstRoomSticker,
  });

  final sticker_display.StickerPanelLoadState state;
  final ValueChanged<Sticker> onSendSticker;
  final VoidCallback onRefresh;
  final ValueChanged<sticker_display.StickerPanelSource> onSourceChanged;
  final VoidCallback? onUploadFirstPersonalSticker;
  final VoidCallback? onUploadFirstRoomSticker;

  @override
  Widget build(BuildContext context) {
    return _StickerPanel(
      state: state,
      onSendSticker: onSendSticker,
      onRefresh: onRefresh,
      onSourceChanged: onSourceChanged,
      onUploadFirstPersonalSticker: onUploadFirstPersonalSticker,
      onUploadFirstRoomSticker: onUploadFirstRoomSticker,
    );
  }
}
