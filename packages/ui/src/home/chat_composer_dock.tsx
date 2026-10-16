// Port of `chat_composer_dock.dart` (a part of `chat_pane.dart`).
import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type DragEvent as ReactDragEvent,
  type ReactNode,
  type RefObject,
} from 'react';
import { createPortal } from 'react-dom';

import {
  ComposerAttachmentStatus,
  composerAttachmentGlyph,
  type ComposerAttachmentView,
} from '@gang-chat/core/app/composer_attachment_display';
import * as message_mentions from '@gang-chat/core/app/message_mentions';
import * as room_display from '@gang-chat/core/app/room_display';
import * as sticker_display from '@gang-chat/core/app/sticker_display';
import * as voice_display from '@gang-chat/core/app/voice_message_display';
import type { Message, MessageQuote, Sticker } from '@gang-chat/core/protocol/models';
import { FileDropEvent, FileDropService } from '@gang-chat/core/shell/file_drop_service';
import { SelectedFile } from '@gang-chat/core/shell/file_selection_service';

import { EdgeInsets } from '../lib/edge_insets';
import { Center, Column, Expanded, Flexible, Row, SizedBox, Text } from '../lib/layout';
import { SystemMouseCursors } from '../lib/mouse_cursors';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import type { TextEditingController, TextInputFormatter } from '../lib/text_editing';
import { useElementSize } from '../lib/use_element_size';
import { useAppConfig } from '../ui/app_config_scope';
import { Avatar } from '../ui/avatar';
import { Button, ButtonIcon, ButtonTone } from '../ui/button';
import { BoxFit, CachedAssetImage } from '../ui/cached_asset_image';
import {
  ChatComposer,
  ComposerAction,
  ComposerActionAlignment,
  ComposerPanel,
  type ChatComposerController,
  type ComposerSuggestionAction,
  type ComposerSuggestionNavigation,
} from '../ui/chat_composer';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { roleBadgeForegroundColorForLabel } from '../ui/role_badge';
import { Segment, SegmentedControl } from '../ui/segmented_control';
import { PressableSurface } from '../ui/surface';
import { UiTooltip } from '../ui/tooltip';
import { UiColors, UiCurves, UiRadii, UiTypography, argb, boxShadow, withAlpha } from '../ui/tokens';
import { _composerBottomInset, _composerHorizontalInset } from './chat_pane';
import type { ChatImagePreviewActions } from './chat_image_preview';
import { _MessageQuoteCard } from './chat_messages';
import { _OverlayPortalBoundary, _useOverlayPortalHost } from './hover_card_anchor';

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
  _mentionPanelVerticalPadding + _mentionPanelVisibleRows * _mentionPanelTileHeight + (_mentionPanelVisibleRows - 1) * _mentionPanelTileGap;
const _mentionPanelGap = 8.0;

export interface _ComposerDockProps {
  controller: TextEditingController;
  composerController: ChatComposerController;
  timestampNow: Date;
  sending: boolean;
  sendError: string | null;
  stickerPanel: sticker_display.StickerPanelLoadState;
  voiceState: voice_display.VoiceRecorderState;
  attachments: readonly ComposerAttachmentView[];
  quotes: readonly MessageQuote[];
  onRemoveQuote?: ((messageId: string) => void) | null;
  component?: Message | null;
  onRemoveComponent?: (() => void) | null;
  imagePreviewActions: ChatImagePreviewActions;
  fileActionHighlighted: boolean;
  mentionOptions: readonly message_mentions.MessageMentionOption[];
  mentionLoading: boolean;
  mentionSelectedIndex: number;
  onSelectMention?: ((option: message_mentions.MessageMentionOption) => void) | null;
  inputFormatters?: readonly TextInputFormatter[] | null;
  onNavigateMentionSelection?: ((navigation: ComposerSuggestionNavigation) => boolean) | null;
  onConfirmMentionSelection?: ((action: ComposerSuggestionAction) => boolean) | null;
  onHighlightMentionSelection?: ((index: number) => void) | null;
  onSubmit: (text: string) => void;
  onSendSticker: (sticker: Sticker) => void;
  onOpenStickers: () => void;
  onRefreshStickers: () => void;
  onStickerSourceChanged: (source: sticker_display.StickerPanelSource) => void;
  onUploadFirstPersonalSticker?: (() => void) | null;
  onUploadFirstRoomSticker?: (() => void) | null;
  onStartVoice: () => void;
  onSendVoice: () => void;
  onCancelVoice: () => void;
  onPickFile: () => void;
  onPasteFiles: () => Promise<boolean>;
  onCanPasteFiles?: (() => Promise<boolean>) | null;
  onRemoveAttachment: (id: string) => void;
  onRetryAttachment: (id: string) => void;
  /** Dart `dropKey`: the composer's box, measured by the home shell for file drops. */
  dropKey?: RefObject<HTMLDivElement | null> | null;
}

export function _ComposerDock(props: _ComposerDockProps) {
  const {
    controller,
    quotes,
    component = null,
    onRemoveQuote = null,
    attachments,
    mentionOptions,
    mentionLoading,
    sendError,
    sending,
    stickerPanel,
    voiceState,
  } = props;
  const notices =
    sendError != null ? [new FloatingNotice({ message: sendError, tone: FloatingNoticeTone.error, duration: null })] : [];

  const header: ReactNode =
    quotes.length === 0 && component == null ? null : (
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        {quotes.map((quote, index) => (
          <div key={quote.messageId}>
            {index > 0 ? <SizedBox height={6} /> : null}
            <_MessageQuoteCard
              quote={quote}
              timestampNow={props.timestampNow}
              onClose={onRemoveQuote == null ? undefined : () => onRemoveQuote(quote.messageId)}
              compact
              imagePreviewActions={props.imagePreviewActions}
            />
          </div>
        ))}
        {component != null ? (
          <>
            {quotes.length > 0 ? <SizedBox height={6} /> : null}
            <_ComposerMessageComponentCard message={component} onClose={props.onRemoveComponent ?? null} />
          </>
        ) : null}
      </Column>
    );

  const actions = [
    new ComposerAction({
      id: 'stickers',
      icon: Icons.emoji_emotions_outlined,
      label: '表情',
      onPressed: props.onOpenStickers,
      panel: ComposerPanel.static({
        height: _stickerPanelHeight,
        padding: EdgeInsets.fromLTRB(12, 12, 12, 12),
        child: (
          <_StickerPanel
            state={stickerPanel}
            onSendSticker={props.onSendSticker}
            onRefresh={props.onRefreshStickers}
            onSourceChanged={props.onStickerSourceChanged}
            onUploadFirstPersonalSticker={props.onUploadFirstPersonalSticker ?? null}
            onUploadFirstRoomSticker={props.onUploadFirstRoomSticker ?? null}
          />
        ),
      }),
    }),
    new ComposerAction({
      id: 'voice',
      icon: Icons.mic_none,
      label: '语音',
      onPressed: props.onCancelVoice,
      panel: ComposerPanel.static({
        height: _voicePanelHeight,
        child: (
          <_VoicePanel state={voiceState} onStart={props.onStartVoice} onSend={props.onSendVoice} onCancel={props.onCancelVoice} />
        ),
      }),
    }),
    new ComposerAction({
      id: 'file',
      icon: Icons.attach_file,
      label: '文件',
      selected: props.fileActionHighlighted,
      onPressed: props.onPickFile,
    }),
    new ComposerAction({
      id: 'send',
      icon: Icons.send_rounded,
      label: '发送',
      tooltip: sending ? '发送中' : '发送',
      tone: ButtonTone.primary,
      alignment: ComposerActionAlignment.trailing,
      onPressed: () => props.onSubmit(controller.text),
    }),
  ];

  return (
    <FloatingNoticeEmitter notices={notices}>
      {/* SafeArea(top: false): no system insets on the desktop. */}
      <div
        style={{
          paddingLeft: _composerHorizontalInset,
          paddingRight: _composerHorizontalInset,
          paddingBottom: _composerBottomInset,
          boxSizing: 'border-box',
          width: '100%',
          flexShrink: 0,
        }}
      >
        <_ComposerMentionOverlay
          visible={mentionOptions.length > 0 || mentionLoading}
          panel={
            <_ComposerMentionPanel
              options={mentionOptions}
              loading={mentionLoading}
              selectedIndex={props.mentionSelectedIndex}
              onHighlight={props.onHighlightMentionSelection ?? null}
              onSelected={props.onSelectMention ?? null}
            />
          }
        >
          <_ComposerDropTarget dropKey={props.dropKey ?? null}>
            <ChatComposer
              controller={controller}
              composerController={props.composerController}
              hintText="写点什么…"
              maxLines={5}
              onSubmitted={props.onSubmit}
              suggestionShortcutsEnabled={mentionOptions.length > 0}
              onSuggestionNavigationPressed={props.onNavigateMentionSelection ?? null}
              onSuggestionActionPressed={props.onConfirmMentionSelection ?? null}
              inputFormatters={props.inputFormatters ?? null}
              // Clipboard files / images pasted into the field are staged as
              // attachments here; ChatComposer only falls back to a text paste
              // when this reports nothing was consumed.
              onPasteFiles={props.onPasteFiles}
              onCanPasteFiles={props.onCanPasteFiles ?? null}
              header={header}
              attachments={
                attachments.length === 0 ? null : (
                  <_ComposerAttachmentStrip
                    attachments={attachments}
                    onRemove={props.onRemoveAttachment}
                    onRetry={props.onRetryAttachment}
                  />
                )
              }
              actions={actions}
            />
          </_ComposerDropTarget>
        </_ComposerMentionOverlay>
      </div>
    </FloatingNoticeEmitter>
  );
}

/**
 * Holds the composer box behind `dropKey` (Dart passed it as the
 * ChatComposer's key) and turns files dropped onto it into a
 * [FileDropService] drop at the pointer position.
 *
 * PORT: Flutter received drops from the native runner and the home shell
 * staged those landing inside the composer. Here the composer listens to the
 * DOM drop itself and publishes it through `FileDropService.dispatch`, which
 * reaches the same home-shell handler. A drop another listener already
 * handled (`defaultPrevented`) is left alone, and this one calls
 * `preventDefault()`, so a window-level drop target must skip
 * `defaultPrevented` drops to avoid staging a file twice.
 */
function _ComposerDropTarget({ dropKey, children }: { dropKey: RefObject<HTMLDivElement | null> | null; children: ReactNode }) {
  const carriesFiles = (event: ReactDragEvent<HTMLDivElement>) => Array.from(event.dataTransfer?.types ?? []).includes('Files');
  return (
    <div
      ref={(node) => {
        if (dropKey != null) dropKey.current = node;
      }}
      onDragOver={(event) => {
        if (!carriesFiles(event)) return;
        event.preventDefault();
        event.dataTransfer.dropEffect = 'copy';
      }}
      onDrop={(event) => {
        if (event.defaultPrevented || !carriesFiles(event)) return;
        const dropped = Array.from(event.dataTransfer.files);
        if (dropped.length === 0) return;
        event.preventDefault();
        FileDropService.dispatch(
          new FileDropEvent({
            files: dropped.map((file) =>
              SelectedFile.deferred({
                name: file.name,
                mimeType: file.type,
                length: file.size,
                readAsBytes: async () => new Uint8Array(await file.arrayBuffer()),
              }),
            ),
            x: event.clientX,
            y: event.clientY,
          }),
        );
      }}
      style={{ width: '100%' }}
    >
      {children}
    </div>
  );
}

/**
 * Port of Material's `IconButton` as the dock uses it (Material 3 defaults):
 * a round, transparent button whose hover / press overlay is the icon colour
 * at 8% / 10%. `padding` and `minSize` give the button box (Dart
 * `padding` + `constraints`).
 */
function _MaterialIconButton({
  icon,
  iconSize,
  color,
  tooltip,
  onPressed,
  padding = 8,
  minSize = 40,
  testId,
}: {
  icon: IconData;
  iconSize: number;
  color: string;
  tooltip: string;
  onPressed: (() => void) | null;
  padding?: number;
  minSize?: number;
  testId?: string;
}) {
  const [hovered, setHovered] = useState(false);
  const [pressed, setPressed] = useState(false);
  const enabled = onPressed != null;
  const size = Math.max(iconSize + padding * 2, minSize);
  const foreground = enabled ? color : withAlpha(UiColors.text, 0.38);
  const overlay = !enabled ? 'transparent' : pressed ? withAlpha(color, 0.1) : hovered ? withAlpha(color, 0.08) : 'transparent';
  return (
    <UiTooltip message={tooltip}>
      <div
        role="button"
        aria-label={tooltip}
        aria-disabled={!enabled}
        data-testid={testId}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => {
          setHovered(false);
          setPressed(false);
        }}
        onMouseDown={(event) => {
          if (event.button === 0) setPressed(true);
        }}
        onMouseUp={() => setPressed(false)}
        onClick={(event) => {
          if (event.button !== 0) return;
          onPressed?.();
        }}
        style={{
          width: size,
          height: size,
          flexShrink: 0,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: overlay,
          cursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        }}
      >
        <Icon icon={icon} size={iconSize} color={foreground} />
      </div>
    </UiTooltip>
  );
}

function _ComposerMessageComponentCard({ message, onClose }: { message: Message; onClose: (() => void) | null }) {
  const playlist = message.playlistAttachment?.playlist ?? null;
  const track = message.musicTrackAttachment?.track ?? null;
  const isPlaylist = playlist != null;
  const title = playlist?.name ?? track?.title ?? message.body.trim();
  const artists =
    track?.artists
      .map((value) => value.trim())
      .filter((value) => value.length > 0)
      .join(' / ') ?? '';
  const subtitle = isPlaylist ? `${playlist.itemCount} 首歌曲` : artists.length === 0 ? '歌曲' : artists;
  return (
    <div
      data-testid="composer-message-component"
      style={{
        width: '100%',
        boxSizing: 'border-box',
        padding: '8px 6px 8px 10px',
        backgroundColor: UiColors.surfaceRaised,
        border: `1px solid ${UiColors.border}`,
        borderRadius: 8,
      }}
    >
      <Row crossAxisAlignment="center">
        <Icon icon={isPlaylist ? Icons.queue_music_outlined : Icons.music_note} color={UiColors.accent} size={22} />
        <SizedBox width={9} />
        <Expanded>
          <Column mainAxisSize="min" crossAxisAlignment="start">
            <Text style={{ ...UiTypography.body, color: UiColors.text, fontWeight: 700, maxWidth: '100%' }}>{title}</Text>
            <SizedBox height={2} />
            <Text style={{ ...UiTypography.label, color: UiColors.textMuted, maxWidth: '100%' }}>{subtitle}</Text>
          </Column>
        </Expanded>
        <SizedBox width={8} />
        <_MaterialIconButton
          testId="remove-composer-message-component"
          onPressed={onClose}
          icon={Icons.close}
          iconSize={16}
          color={UiColors.textMuted}
          tooltip="移除组件"
          padding={0}
          minSize={28}
        />
      </Row>
    </div>
  );
}

/**
 * Port of `_ComposerMentionOverlay`: shows [panel] in the app overlay just
 * above the composer (left edge + 4, [_mentionPanelGap] above), clamped to
 * the window, while [visible].
 */
function _ComposerMentionOverlay({ visible, panel, children }: { visible: boolean; panel: ReactNode; children: ReactNode }) {
  const anchorRef = useRef<HTMLDivElement | null>(null);
  const host = _useOverlayPortalHost(visible);
  return (
    <>
      <div ref={anchorRef} style={{ width: '100%' }}>
        {children}
      </div>
      {host != null && visible
        ? createPortal(
            <_OverlayPortalBoundary>
              <_ComposerMentionOverlayLayout anchorRef={anchorRef}>{panel}</_ComposerMentionOverlayLayout>
            </_OverlayPortalBoundary>,
            host,
          )
        : null}
    </>
  );
}

function _ComposerMentionOverlayLayout({ anchorRef, children }: { anchorRef: RefObject<HTMLDivElement | null>; children: ReactNode }) {
  const panelRef = useRef<HTMLDivElement | null>(null);
  const [position, setPosition] = useState<{ left: number; top: number } | null>(null);
  const [, setTick] = useState(0);
  // Re-run the layout when the composer grows / shrinks or the window resizes.
  useElementSize(anchorRef);
  useEffect(() => {
    const onResize = () => setTick((tick) => tick + 1);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  useLayoutEffect(() => {
    const anchor = anchorRef.current;
    const panel = panelRef.current;
    if (anchor == null || panel == null || !anchor.isConnected) return;
    const anchorRect = anchor.getBoundingClientRect();
    const size = { width: window.innerWidth, height: window.innerHeight };
    const childSize = { width: panel.offsetWidth, height: panel.offsetHeight };
    const maxLeft = Math.max(0.0, size.width - childSize.width);
    const left = Math.min(Math.max(anchorRect.left + 4, 0.0), maxLeft);
    const maxTop = Math.max(0.0, size.height - childSize.height);
    const top = Math.min(Math.max(anchorRect.top - _mentionPanelGap - childSize.height, 0.0), maxTop);
    if (position == null || Math.abs(position.left - left) > 0.5 || Math.abs(position.top - top) > 0.5) {
      setPosition({ left, top });
    }
  });
  if (anchorRef.current == null) return null;
  return (
    <div
      ref={panelRef}
      // Pressing the panel must not take focus from the composer's input
      // (Flutter overlay content is not focusable).
      onMouseDown={(event) => event.preventDefault()}
      style={{
        position: 'absolute',
        left: position?.left ?? 0,
        top: position?.top ?? 0,
        maxWidth: '100%',
        maxHeight: '100%',
        visibility: position == null ? 'hidden' : 'visible',
        pointerEvents: 'auto',
      }}
    >
      {children}
    </div>
  );
}

interface _ComposerMentionPanelProps {
  options: readonly message_mentions.MessageMentionOption[];
  loading: boolean;
  selectedIndex: number;
  onHighlight: ((index: number) => void) | null;
  onSelected: ((option: message_mentions.MessageMentionOption) => void) | null;
}

function _ComposerMentionPanel({ options, loading, selectedIndex: requestedIndex, onHighlight, onSelected }: _ComposerMentionPanelProps) {
  const config = useAppConfig();
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const rows = options;
  const height = _mentionPanelHeightFor(rows.length);
  const selectedIndex = _effectiveSelectedIndex(requestedIndex, rows.length);

  // initState + didUpdateWidget(selectedIndex / options.length changed).
  useEffect(() => {
    const frame = requestAnimationFrame(() => _scrollSelectedVisible(scrollRef.current, selectedIndex, rows.length));
    return () => cancelAnimationFrame(frame);
  }, [selectedIndex, rows.length]);

  return (
    <div
      style={{
        width: _mentionPanelWidth,
        height,
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceRaised,
        borderRadius: UiRadii.md,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow([{ color: argb(0x66000000), blurRadius: 18, offsetY: 10 }]),
        overflow: 'hidden',
      }}
    >
      {rows.length === 0 ? (
        <Center style={{ width: '100%', height: '100%' }}>
          {loading ? (
            <CircularProgressIndicator size={18} strokeWidth={2} color={UiColors.accent} />
          ) : (
            <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>没有匹配的成员</Text>
          )}
        </Center>
      ) : (
        <div
          ref={scrollRef}
          // Scrollbar(thumbVisibility: rows > 4): the app's classic scrollbar
          // shows whenever the list overflows, which is exactly rows > 4.
          style={{ height: '100%', overflowY: 'auto', boxSizing: 'border-box', padding: '6px 0' }}
        >
          <Column crossAxisAlignment="stretch" gap={_mentionPanelTileGap}>
            {rows.map((option, index) => {
              const user = option.member?.user ?? null;
              const nameColor = option.isUser ? roleBadgeForegroundColorForLabel(option.roleLabel) : UiColors.accent;
              return (
                <_ComposerMentionTile
                  key={`${option.kind}-${option.member?.user.id ?? option.label}-${index}`}
                  option={option}
                  avatarUrl={config.resolveAssetUrl(user?.avatarUrl)}
                  nameColor={nameColor}
                  highlighted={selectedIndex === index}
                  onHover={() => onHighlight?.(index)}
                  onSelected={onSelected}
                />
              );
            })}
          </Column>
        </div>
      )}
    </div>
  );
}

function _effectiveSelectedIndex(selectedIndex: number, rowCount: number): number {
  if (rowCount <= 0) return 0;
  return Math.min(Math.max(selectedIndex, 0), rowCount - 1);
}

function _scrollSelectedVisible(scroller: HTMLDivElement | null, index: number, rowCount: number): void {
  if (scroller == null || !scroller.isConnected) return;
  if (rowCount <= 0) return;
  const rowTop = 6.0 + index * (_mentionPanelTileHeight + _mentionPanelTileGap);
  const rowBottom = rowTop + _mentionPanelTileHeight;
  const pixels = scroller.scrollTop;
  const viewportDimension = scroller.clientHeight;
  let target = pixels;
  if (rowTop < pixels) {
    target = rowTop;
  } else if (rowBottom > pixels + viewportDimension) {
    target = rowBottom - viewportDimension;
  }
  const maxScrollExtent = Math.max(0, scroller.scrollHeight - viewportDimension);
  target = Math.min(Math.max(target, 0), maxScrollExtent);
  if (Math.abs(target - pixels) < 0.5) return;
  _animateScrollTo(scroller, target, 90);
}

/** `ScrollController.animateTo(target, duration, curve: Curves.easeOutCubic)`. */
function _animateScrollTo(scroller: HTMLElement, target: number, duration: number): void {
  const start = scroller.scrollTop;
  const startTime = performance.now();
  const step = (now: number) => {
    const t = Math.min(1, (now - startTime) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    scroller.scrollTop = start + (target - start) * eased;
    if (t < 1 && scroller.isConnected) requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function _mentionPanelHeightFor(rowCount: number): number {
  if (rowCount <= 0) return _mentionPanelEmptyHeight;
  if (rowCount >= _mentionPanelVisibleRows) return _mentionPanelHeight;
  return _mentionPanelVerticalPadding + rowCount * _mentionPanelTileHeight + (rowCount - 1) * _mentionPanelTileGap;
}

function _ComposerMentionTile({
  option,
  avatarUrl,
  nameColor,
  highlighted,
  onHover,
  onSelected,
}: {
  option: message_mentions.MessageMentionOption;
  avatarUrl: string | null;
  nameColor: string;
  highlighted: boolean;
  onHover: () => void;
  onSelected: ((option: message_mentions.MessageMentionOption) => void) | null;
}) {
  return (
    <div
      onMouseEnter={onHover}
      onClick={(event) => {
        if (event.button !== 0 || onSelected == null) return;
        onSelected(option);
      }}
      style={{
        margin: '0 6px',
        padding: '0 8px',
        height: _mentionPanelTileHeight,
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        borderRadius: UiRadii.sm,
        backgroundColor: highlighted ? UiColors.selected : UiColors.transparent,
        transition: `background-color 120ms ${UiCurves.easeOutCubic}`,
        cursor: onSelected == null ? SystemMouseCursors.basic : SystemMouseCursors.click,
      }}
    >
      <Row mainAxisSize="min" style={{ maxWidth: '100%' }}>
        <_ComposerMentionTileLeading option={option} avatarUrl={avatarUrl} />
        <SizedBox width={10} />
        <Flexible>
          <Text maxLines={1} style={{ ...UiTypography.body, color: nameColor, fontWeight: 700 }}>
            {option.label}
          </Text>
        </Flexible>
      </Row>
    </div>
  );
}

function _ComposerMentionTileLeading({ option, avatarUrl }: { option: message_mentions.MessageMentionOption; avatarUrl: string | null }) {
  const user = option.member?.user ?? null;
  if (user != null) {
    return (
      <Avatar label={room_display.userAvatarLabel(user)} imageUrl={avatarUrl} defaultAvatarKey={user.defaultAvatarKey} size={30} showBorder={false} />
    );
  }
  let icon: IconData;
  switch (option.kind) {
    case message_mentions.MessageMentionKind.everyone:
      icon = Icons.campaign_outlined;
      break;
    case message_mentions.MessageMentionKind.admins:
      icon = Icons.admin_panel_settings_outlined;
      break;
    case message_mentions.MessageMentionKind.user:
      icon = Icons.alternate_email;
      break;
    case message_mentions.MessageMentionKind.bot:
      icon = Icons.smart_toy_outlined;
      break;
  }
  return (
    <div
      style={{
        width: 30,
        height: 30,
        flexShrink: 0,
        boxSizing: 'border-box',
        borderRadius: '50%',
        backgroundColor: UiColors.selected,
        border: `1px solid ${UiColors.selectedBorder}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={icon} color={UiColors.accent} size={17} />
    </div>
  );
}

interface _StickerPanelProps {
  state: sticker_display.StickerPanelLoadState;
  onSendSticker: (sticker: Sticker) => void;
  onRefresh: () => void;
  onSourceChanged: (source: sticker_display.StickerPanelSource) => void;
  onUploadFirstPersonalSticker?: (() => void) | null;
  onUploadFirstRoomSticker?: (() => void) | null;
}

const _stickerSegments = [
  new Segment<sticker_display.StickerPanelSource>({ value: sticker_display.StickerPanelSource.personal, label: '我的表情包' }),
  new Segment<sticker_display.StickerPanelSource>({ value: sticker_display.StickerPanelSource.room, label: '房间表情包' }),
];

/**
 * The composer's sticker picker. Shows the user's personal packs and the
 * current room's packs behind a source toggle, sending the real managed
 * sticker (asset + id) rather than a decorative placeholder.
 */
export function _StickerPanel({
  state,
  onSendSticker,
  onRefresh,
  onSourceChanged,
  onUploadFirstPersonalSticker = null,
  onUploadFirstRoomSticker = null,
}: _StickerPanelProps) {
  const stickers = sticker_display.stickerPanelStickers({
    source: state.source,
    personalPacks: state.personalPacks,
    roomPacks: state.roomPacks,
  });
  const bodyState = sticker_display.stickerPanelBodyState({
    loading: state.loading,
    error: state.error,
    stickers,
  });

  let body: ReactNode;
  switch (bodyState) {
    case sticker_display.StickerPanelBodyState.loading:
      body = (
        <_StickerPanelCentered>
          <CircularProgressIndicator size={22} strokeWidth={2.2} color={UiColors.accent} />
        </_StickerPanelCentered>
      );
      break;
    case sticker_display.StickerPanelBodyState.error:
      body = (
        <_StickerPanelMessage
          icon={Icons.warning_amber_rounded}
          text={state.error ?? '加载表情失败'}
          actionKey="sticker-panel-refresh"
          actionIcon={Icons.refresh}
          actionLabel="刷新"
          onAction={onRefresh}
        />
      );
      break;
    case sticker_display.StickerPanelBodyState.empty: {
      const personal = state.source === sticker_display.StickerPanelSource.personal;
      body = (
        <_StickerPanelMessage
          icon={Icons.emoji_emotions_outlined}
          text={sticker_display.stickerPanelEmptyText(state.source)}
          actionKey={personal ? 'sticker-panel-upload-first-personal' : 'sticker-panel-upload-first-room'}
          actionIcon={Icons.upload_file_outlined}
          actionLabel="上传第一个表情"
          actionTone={ButtonTone.primary}
          onAction={personal ? onUploadFirstPersonalSticker : onUploadFirstRoomSticker}
        />
      );
      break;
    }
    case sticker_display.StickerPanelBodyState.results:
      body = <_StickerList stickers={stickers} onSendSticker={onSendSticker} />;
      break;
  }

  return (
    <Column crossAxisAlignment="stretch" style={{ flex: '1 1 0', minHeight: 0 }}>
      <SegmentedControl<sticker_display.StickerPanelSource>
        value={state.source}
        expanded
        onChanged={onSourceChanged}
        segments={_stickerSegments}
      />
      <SizedBox height={12} />
      <Expanded>{body}</Expanded>
    </Column>
  );
}

function _StickerList({ stickers, onSendSticker }: { stickers: readonly Sticker[]; onSendSticker: (sticker: Sticker) => void }) {
  return (
    <div style={{ flex: '1 1 0', minHeight: 0, overflowY: 'auto' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {stickers.map((sticker) => (
          <_StickerTile key={sticker.id} sticker={sticker} onPressed={() => onSendSticker(sticker)} />
        ))}
      </div>
    </div>
  );
}

function _StickerTile({ sticker, onPressed }: { sticker: Sticker; onPressed: () => void }) {
  const imageUrl = useAppConfig().resolveAssetUrl(sticker.asset.thumbnailUrl ?? sticker.asset.url);
  return (
    <UiTooltip message={sticker.name}>
      <PressableSurface
        width={_stickerTileSize}
        height={_stickerTileSize}
        onPressed={onPressed}
        padding={6}
        backgroundColor={UiColors.surfaceLow}
        borderColor={UiColors.border}
      >
        <Center style={{ flex: '1 1 0', minHeight: 0 }}>
          {imageUrl == null ? (
            <Icon icon={Icons.image_not_supported_outlined} color={UiColors.textMuted} size={20} />
          ) : (
            <CachedAssetImage
              url={imageUrl}
              filename={sticker.asset.filename}
              mimeType={sticker.asset.mimeType}
              expectedBytes={sticker.asset.sizeBytes}
              fit={BoxFit.contain}
              style={{ width: '100%', height: '100%' }}
              errorBuilder={() => <Icon icon={Icons.broken_image_outlined} color={UiColors.textMuted} size={20} />}
            />
          )}
        </Center>
      </PressableSurface>
    </UiTooltip>
  );
}

function _StickerPanelCentered({ children }: { children: ReactNode }) {
  return <Center style={{ flex: '1 1 0', minHeight: 0 }}>{children}</Center>;
}

function _StickerPanelMessage({
  icon,
  text,
  actionKey,
  actionIcon,
  actionLabel,
  actionTone = ButtonTone.neutral,
  onAction,
}: {
  icon: IconData;
  text: string;
  actionKey?: string;
  actionIcon?: IconData | null;
  actionLabel?: string | null;
  actionTone?: ButtonTone;
  onAction?: (() => void) | null;
}) {
  const actionWidth =
    actionLabel == null ? null : Button.minimumWidthForLabel({ label: actionLabel, hasIcon: actionIcon != null });
  return (
    <Center style={{ flex: '1 1 0', minHeight: 0 }}>
      <Column mainAxisSize="min">
        <Icon icon={icon} color={UiColors.textMuted} size={26} />
        <SizedBox height={10} />
        <Text maxLines={2} textAlign="center" style={{ ...UiTypography.label, color: UiColors.textMuted }}>
          {text}
        </Text>
        {actionLabel != null && onAction != null ? (
          <>
            <SizedBox height={12} />
            <Button
              data-testid={actionKey}
              icon={actionIcon == null ? null : <Icon icon={actionIcon} />}
              tone={actionTone}
              width={actionWidth}
              onPressed={onAction}
              style={{ alignSelf: 'center' }}
            >
              {actionLabel}
            </Button>
          </>
        ) : null}
      </Column>
    </Center>
  );
}

/**
 * The strip of staged-file chips shown above the composer input. Each chip
 * names a file queued for the next message and offers a remove button. The
 * files ride out as attachments when the message is sent.
 */
function _ComposerAttachmentStrip({
  attachments,
  onRemove,
  onRetry,
}: {
  attachments: readonly ComposerAttachmentView[];
  onRemove: (id: string) => void;
  onRetry: (id: string) => void;
}) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-start', alignItems: 'flex-start' }}>
      {attachments.map((attachment) => (
        <_ComposerAttachmentChip
          key={attachment.id}
          attachment={attachment}
          onRemove={() => onRemove(attachment.id)}
          onRetry={() => onRetry(attachment.id)}
        />
      ))}
    </div>
  );
}

function _ComposerAttachmentChip({
  attachment,
  onRemove,
  onRetry,
}: {
  attachment: ComposerAttachmentView;
  onRemove: () => void;
  onRetry: () => void;
}) {
  const size = attachment.sizeLabel;
  const failed = attachment.hasFailed;
  let subtitle: string | null;
  switch (attachment.status) {
    case ComposerAttachmentStatus.failed:
      subtitle = attachment.errorMessage ?? '上传失败，点击重试';
      break;
    case ComposerAttachmentStatus.uploading:
      subtitle = attachment.progress == null ? '上传中…' : `上传中 ${Math.round(attachment.progress * 100)}%`;
      break;
    case ComposerAttachmentStatus.uploaded:
      subtitle = size;
      break;
  }
  return (
    <div
      style={{
        boxSizing: 'border-box',
        backgroundColor: UiColors.surface,
        borderRadius: UiRadii.md,
        border: `1px solid ${failed ? UiColors.danger : UiColors.border}`,
        padding: '6px 4px 6px 10px',
      }}
    >
      <Row mainAxisSize="min">
        <_ComposerAttachmentLeading attachment={attachment} onRetry={onRetry} />
        <SizedBox width={8} />
        <UiTooltip message={attachment.filename}>
          <Column mainAxisSize="min" crossAxisAlignment="start" style={{ maxWidth: 180 }}>
            <Text maxLines={1} style={{ ...UiTypography.label, color: UiColors.text, maxWidth: '100%' }}>
              {attachment.filename}
            </Text>
            {subtitle != null ? (
              <Text
                style={{
                  ...UiTypography.label,
                  color: failed ? UiColors.danger : UiColors.textMuted,
                  fontSize: 11,
                  maxWidth: '100%',
                }}
              >
                {subtitle}
              </Text>
            ) : null}
          </Column>
        </UiTooltip>
        <_MaterialIconButton
          onPressed={onRemove}
          icon={Icons.close}
          iconSize={16}
          color={UiColors.textMuted}
          tooltip="移除"
          padding={0}
          minSize={28}
        />
      </Row>
    </div>
  );
}

/**
 * The chip's leading slot: a spinner while uploading, a tappable retry icon
 * when the upload failed, and the file-type glyph once it has landed.
 */
function _ComposerAttachmentLeading({ attachment, onRetry }: { attachment: ComposerAttachmentView; onRetry: () => void }) {
  switch (attachment.status) {
    case ComposerAttachmentStatus.uploading:
      return (
        <SizedBox width={18} height={18}>
          {attachment.progress == null ? (
            <CircularProgressIndicator size={18} strokeWidth={2} color={UiColors.accent} />
          ) : (
            <_DeterminateCircularProgressIndicator size={18} strokeWidth={2} value={attachment.progress} color={UiColors.accent} />
          )}
        </SizedBox>
      );
    case ComposerAttachmentStatus.failed:
      return (
        <div
          role="button"
          onClick={(event) => {
            if (event.button !== 0) return;
            onRetry();
          }}
          style={{ display: 'flex', cursor: SystemMouseCursors.click }}
        >
          <Icon icon={Icons.refresh} size={18} color={UiColors.danger} />
        </div>
      );
    case ComposerAttachmentStatus.uploaded:
      return (
        <Icon
          icon={Icons[composerAttachmentGlyph({ mimeType: attachment.mimeType, filename: attachment.filename })]}
          size={18}
          color={UiColors.textSecondary}
        />
      );
  }
}

/**
 * Material's determinate `CircularProgressIndicator(value:)`: an arc from
 * 12 o'clock, clockwise, over [value] of the circle (the kit's indicator is
 * indeterminate only).
 */
function _DeterminateCircularProgressIndicator({
  size,
  strokeWidth,
  value,
  color,
}: {
  size: number;
  strokeWidth: number;
  value: number;
  color: string;
}) {
  const radius = Math.max(0, (size - strokeWidth) / 2);
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.min(Math.max(value, 0), 1);
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="progressbar" aria-valuenow={Math.round(clamped * 100)} style={{ display: 'block' }}>
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeDasharray={`${circumference * clamped} ${circumference}`}
        transform={`rotate(-90 ${size / 2} ${size / 2})`}
      />
    </svg>
  );
}

interface _VoicePanelProps {
  state: voice_display.VoiceRecorderState;
  onStart: () => void;
  onSend: () => void;
  onCancel: () => void;
}

function _VoicePanel({ state, onStart, onSend, onCancel }: _VoicePanelProps) {
  let child: ReactNode;
  switch (state.phase) {
    case voice_display.VoiceRecorderPhase.idle:
      child = <_VoiceIdle error={state.error} onStart={onStart} />;
      break;
    case voice_display.VoiceRecorderPhase.recording:
      child = <_VoiceActive elapsed={state.elapsed} recording busy={false} onSend={onSend} onCancel={onCancel} />;
      break;
    case voice_display.VoiceRecorderPhase.review:
      child = (
        <_VoiceActive elapsed={state.elapsed} recording={false} busy={false} error={state.error} onSend={onSend} onCancel={onCancel} />
      );
      break;
    case voice_display.VoiceRecorderPhase.sending:
      child = <_VoiceActive elapsed={state.elapsed} recording={false} busy onSend={onSend} onCancel={onCancel} />;
      break;
  }
  return <Center style={{ flex: '1 1 0', minHeight: 0 }}>{child}</Center>;
}

function _VoiceIdle({ onStart, error = null }: { onStart: () => void; error?: string | null }) {
  return (
    <Column mainAxisSize="min">
      <SizedBox height={14} />
      {/* Plain icon trigger — no raised surface or circular background. */}
      <_MaterialIconButton onPressed={onStart} icon={Icons.mic_none} iconSize={30} color={UiColors.accent} tooltip="点击录音" />
      <SizedBox height={8} />
      <Text textAlign="center" style={{ ...UiTypography.label, color: error != null ? UiColors.danger : UiColors.textMuted }}>
        {error ?? '点击录音'}
      </Text>
    </Column>
  );
}

function _VoiceActive({
  elapsed,
  recording,
  busy,
  onSend,
  onCancel,
  error = null,
}: {
  elapsed: number;
  recording: boolean;
  busy: boolean;
  onSend: () => void;
  onCancel: () => void;
  error?: string | null;
}) {
  const durationStyle: CSSProperties = { ...UiTypography.body, fontVariantNumeric: 'tabular-nums' };
  return (
    <Column mainAxisSize="min">
      <SizedBox height={14} />
      <Row mainAxisSize="min">
        <Icon
          icon={recording ? Icons.fiber_manual_record : Icons.mic_none}
          size={16}
          color={recording ? UiColors.danger : UiColors.textSecondary}
        />
        <SizedBox width={8} />
        <Text style={durationStyle}>{voice_display.formatVoiceDuration(elapsed)}</Text>
      </Row>
      <SizedBox height={4} />
      <Text textAlign="center" style={{ ...UiTypography.label, color: error != null ? UiColors.danger : UiColors.textMuted }}>
        {error ?? (recording ? '正在录音…' : '点击发送或取消')}
      </Text>
      <SizedBox height={12} />
      <Row mainAxisSize="min">
        <ButtonIcon
          onPressed={busy ? null : onCancel}
          icon={<Icon icon={Icons.delete_outline} />}
          tooltip="取消"
          tone={ButtonTone.danger}
          size={34}
        />
        <SizedBox width={10} />
        <ButtonIcon
          onPressed={busy ? null : onSend}
          icon={<Icon icon={Icons.send_rounded} />}
          tooltip={recording ? '完成并发送' : '发送'}
          tone={ButtonTone.primary}
          loading={busy}
          size={34}
        />
      </Row>
    </Column>
  );
}

/**
 * Test-only entry point for the otherwise-private composer attachment strip,
 * so widget tests can pump it directly without standing up the whole dock.
 */
export function ComposerAttachmentStripForTest({
  attachments,
  onRemove,
  onRetry,
}: {
  attachments: readonly ComposerAttachmentView[];
  onRemove: (id: string) => void;
  onRetry?: ((id: string) => void) | null;
}) {
  return <_ComposerAttachmentStrip attachments={attachments} onRemove={onRemove} onRetry={onRetry ?? (() => {})} />;
}

/**
 * Test-only entry point for the otherwise-private voice panel, so widget
 * tests can pump it directly without standing up the whole composer dock.
 */
export function VoicePanelForTest(props: _VoicePanelProps) {
  return <_VoicePanel {...props} />;
}

/** Test-only entry point for the otherwise-private sticker panel. */
export function StickerPanelForTest(props: _StickerPanelProps) {
  return <_StickerPanel {...props} />;
}
