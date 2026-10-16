/**
 * Port of `lib/src/home/live_screen_share_picker.dart`.
 *
 * Shown with `showDialog<ScreenSource>(() => <LiveScreenSharePicker … />)`;
 * it resolves with the chosen source (`Navigator.pop(source)`) or
 * `undefined` when cancelled.
 */
import { useRef, type ReactNode } from 'react';

import * as live_display from '@gang-chat/core/app/live_display';
import { Timer, unawaited, type PeriodicTimer } from '@gang-chat/core/lib/async';
import type { StreamSubscription } from '@gang-chat/core/lib/event_stream';
import type { ScreenSource } from '@gang-chat/core/live/live_session';

import { Center, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { CircularProgressIndicator } from '../lib/progress_indicator';
import { useElementSize } from '../lib/use_element_size';
import { useViewState, ViewState } from '../lib/view_state';
import { ButtonTone } from '../ui/button';
import { FloatingNotice, FloatingNoticeEmitter, FloatingNoticeTone, ResponsiveDialogAction, ResponsiveDialogActionBar } from '../ui/feedback';
import { Icon, Icons } from '../ui/icon';
import { useRoute } from '../ui/overlay';
import { PressableSurface } from '../ui/surface';
import { UiColors, UiRadii, UiTypography } from '../ui/tokens';

export interface LiveScreenSharePickerProps {
  loadSources: () => Promise<ScreenSource[]>;
  refreshThumbnails: () => Promise<void>;
}

export class _LiveScreenSharePickerState extends ViewState<LiveScreenSharePickerProps> {
  _state: live_display.LiveScreenSourcePickerState<ScreenSource> = new live_display.LiveScreenSourcePickerState<ScreenSource>();
  _refreshTimer: PeriodicTimer | null = null;
  /** `Navigator.of(context).pop` of the dialog route. */
  _pop: (source?: ScreenSource) => void = () => {};

  override initState(): void {
    unawaited(this._load());
    this._refreshTimer = Timer.periodic(2000, () => {
      unawaited(this.widget.refreshThumbnails());
    });
  }

  override dispose(): void {
    this._refreshTimer?.cancel();
  }

  async _load(): Promise<void> {
    if (!live_display.canLoadLiveScreenSources(this._state)) return;
    this.setState(() => {
      this._state = live_display.liveScreenSourceLoadStarted(this._state);
    });

    try {
      const sources = await this.widget.loadSources();
      if (!this.mounted) return;
      this.setState(() => {
        this._state = live_display.liveScreenSourceLoadSucceeded({
          state: this._state,
          sources,
          sourceId: (source) => source.id,
        });
      });
      unawaited(this.widget.refreshThumbnails());
    } catch (error) {
      if (!this.mounted) return;
      this.setState(() => {
        this._state = live_display.liveScreenSourceLoadFailed({
          state: this._state,
          failure: error,
        });
      });
    }
  }

  _confirm = (): void => {
    const source = live_display.liveScreenSourceById(this._state.sources, {
      selectedId: this._state.selectedId,
      sourceId: (source) => source.id,
    });
    if (source == null) return;
    this._pop(source);
  };

  _select = (source: ScreenSource): void => {
    this.setState(() => {
      this._state = live_display.liveScreenSourceSelectedChanged(this._state, source.id);
    });
  };
}

export function LiveScreenSharePicker(props: LiveScreenSharePickerProps) {
  const state = useViewState(() => new _LiveScreenSharePickerState(), props);
  const route = useRoute<ScreenSource>();
  state._pop = route.close;
  const error = state._state.error?.trim() ?? null;
  return (
    <div
      role="dialog"
      aria-label="选择共享内容"
      style={{
        width: 720,
        maxWidth: '100%',
        minWidth: 280,
        maxHeight: 560,
        minHeight: 0,
        flexShrink: 1,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.lg,
        border: `1px solid ${UiColors.border}`,
        // Material paints the shape's side over the child, so the content
        // inset stays 22 / 20 / 22 / 18 from the outer edge.
        padding: '19px 21px 17px',
      }}
    >
      <FloatingNoticeEmitter
        notices={
          error != null && error.length > 0
            ? [new FloatingNotice({ message: error, tone: FloatingNoticeTone.error, duration: null })]
            : []
        }
      >
        <Text style={UiTypography.title}>选择共享内容</Text>
        <SizedBox height={16} />
        <div style={{ flex: '0 1 auto', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <_ScreenSourceList state={state} />
        </div>
        <SizedBox height={16} />
        <ResponsiveDialogActionBar
          actions={[
            new ResponsiveDialogAction({
              label: '取消',
              onPressed: () => state._pop(),
            }),
            new ResponsiveDialogAction({
              label: '共享',
              tone: ButtonTone.primary,
              onPressed: live_display.canConfirmLiveScreenSourceSelection(state._state.selectedId) ? state._confirm : null,
            }),
          ]}
        />
      </FloatingNoticeEmitter>
    </div>
  );
}

// SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 220,
// mainAxisExtent: 166, crossAxisSpacing: 12, mainAxisSpacing: 12).
const _gridMaxCrossAxisExtent = 220;
// The tile is a 158px PressableSurface, whose raised base/shadow adds
// hoverLift + baseDepth (8px) below the cap. Give the cell that extra
// room so the base isn't clipped, matching the dialog's buttons.
const _gridMainAxisExtent = 166;
const _gridCrossAxisSpacing = 12;
const _gridMainAxisSpacing = 12;

/** Dart `_buildSourceList`. */
function _ScreenSourceList({ state }: { state: _LiveScreenSharePickerState }) {
  const sources = state._state.sources;
  const bodyState = live_display.liveScreenSourceListBodyState(sources);
  switch (bodyState) {
    case live_display.LiveScreenSourceListBodyState.loading:
      return (
        <SizedBox height={200}>
          <Center style={{ height: '100%' }}>
            <CircularProgressIndicator color={UiColors.accent} />
          </Center>
        </SizedBox>
      );
    case live_display.LiveScreenSourceListBodyState.empty:
      return (
        <SizedBox height={200}>
          <Center style={{ height: '100%' }}>
            <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>没有可共享的屏幕或窗口</Text>
          </Center>
        </SizedBox>
      );
    case live_display.LiveScreenSourceListBodyState.results:
      return <_ScreenSourceGrid state={state} sources={sources!} />;
  }
}

/** The shrink-wrapped `GridView.builder` of the results state. */
function _ScreenSourceGrid({ state, sources }: { state: _LiveScreenSharePickerState; sources: readonly ScreenSource[] }) {
  // SliverGridDelegateWithMaxCrossAxisExtent: the cross-axis count follows
  // the viewport width.
  const gridRef = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(gridRef);
  const crossAxisExtent = size?.width ?? 0;
  const crossAxisCount = Math.max(1, Math.ceil(crossAxisExtent / (_gridMaxCrossAxisExtent + _gridCrossAxisSpacing)));
  return (
    <div ref={gridRef} style={{ flex: '0 1 auto', minHeight: 0, overflowY: 'auto', overflowX: 'hidden' }}>
      {size != null ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${crossAxisCount}, minmax(0, 1fr))`,
            gridAutoRows: _gridMainAxisExtent,
            columnGap: _gridCrossAxisSpacing,
            rowGap: _gridMainAxisSpacing,
          }}
        >
          {sources.map((source) => (
            <_ScreenSourceTile
              key={source.id}
              source={source}
              selected={live_display.liveScreenSourceSelected(source, {
                selectedId: state._state.selectedId,
                sourceId: (s) => s.id,
              })}
              onPressed={() => state._select(source)}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function _ScreenSourceTile({
  source,
  selected,
  onPressed,
}: {
  source: ScreenSource;
  selected: boolean;
  onPressed: () => void;
}): ReactNode {
  return (
    // PORT: Dart asks for a 158px surface, but the grid hands every cell tight
    // 166px constraints, so Flutter rendered the tile at the cell height; the
    // surface fills its cell here to match.
    <PressableSurface
      height={Infinity}
      selected={selected}
      onPressed={onPressed}
      backgroundColor={UiColors.surface}
      selectedBackgroundColor={UiColors.selected}
      borderColor={UiColors.border}
      selectedBorderColor={UiColors.selectedBorder}
      padding={8}
      aria-label={source.name}
    >
      <div style={{ flex: '1 1 0', minHeight: 0, display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}>
        <Expanded>
          <_ScreenSourceThumbnail source={source} />
        </Expanded>
        <SizedBox height={8} />
        <Row>
          <Icon
            icon={source.isWindow ? Icons.web_asset_outlined : Icons.desktop_windows_outlined}
            size={14}
            color={selected ? UiColors.accent : UiColors.textMuted}
          />
          <SizedBox width={6} />
          <Expanded>
            <Text
              maxLines={1}
              overflow="ellipsis"
              style={{ ...UiTypography.label, color: selected ? UiColors.text : UiColors.textSecondary }}
            >
              {source.name}
            </Text>
          </Expanded>
        </Row>
      </div>
    </PressableSurface>
  );
}

interface _ScreenSourceThumbnailProps {
  source: ScreenSource;
}

class _ScreenSourceThumbnailState extends ViewState<_ScreenSourceThumbnailProps> {
  /** PORT: a PNG data URL (Electron `desktopCapturer`), not `Uint8List` bytes. */
  _thumbnail: string | null;
  _imageError: unknown = null;
  _thumbnailSubscription: StreamSubscription | null = null;

  constructor(source: ScreenSource) {
    super();
    // `_bindSource` in initState: the first build already shows the source's
    // current thumbnail.
    this._thumbnail = source.thumbnail;
  }

  override initState(): void {
    this._bindSource(this.widget.source);
  }

  override didUpdateProps(oldWidget: _ScreenSourceThumbnailProps): void {
    if (oldWidget.source !== this.widget.source) {
      this.setState(() => this._bindSource(this.widget.source));
    }
  }

  override dispose(): void {
    this._thumbnailSubscription?.cancel();
  }

  _bindSource(source: ScreenSource): void {
    this._thumbnailSubscription?.cancel();
    this._thumbnail = source.thumbnail;
    this._imageError = null;
    this._thumbnailSubscription =
      source.thumbnailUpdates?.listen((thumbnail) => {
        if (!this.mounted) return;
        this.setState(() => {
          this._thumbnail = thumbnail;
          this._imageError = null;
        });
      }) ?? null;
  }
}

function _ScreenSourceThumbnail(props: _ScreenSourceThumbnailProps) {
  const state = useViewState(() => new _ScreenSourceThumbnailState(props.source), props);
  const thumbnail = live_display.visibleLiveScreenSourceThumbnail({
    thumbnail: state._thumbnail,
    imageError: state._imageError,
  });
  if (thumbnail == null) return <_ScreenSourceThumbnailFallback />;

  return (
    <div
      style={{
        position: 'relative',
        flex: '1 1 0',
        minHeight: 0,
        borderRadius: UiRadii.sm,
        overflow: 'hidden',
        backgroundColor: UiColors.surfacePressed,
      }}
    >
      {/* gaplessPlayback: the browser keeps the previous frame until the next
          data URL has decoded. */}
      <img
        src={thumbnail}
        alt=""
        draggable={false}
        onError={(event) => {
          // errorBuilder: show the fallback and remember the failure.
          state.setState(() => {
            state._imageError = event.type;
          });
        }}
        style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain', display: 'block' }}
      />
    </div>
  );
}

function _ScreenSourceThumbnailFallback() {
  return (
    <div
      style={{
        flex: '1 1 0',
        minHeight: 0,
        backgroundColor: UiColors.surfacePressed,
        borderRadius: UiRadii.sm,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Icon icon={Icons.desktop_windows_outlined} color={UiColors.textMuted} size={32} />
    </div>
  );
}
