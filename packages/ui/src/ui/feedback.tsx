import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';

import { useElementSize } from '../lib/use_element_size';
import { Button, ButtonTone } from './button';
import { Icon, Icons, type IconData } from './icon';
import { insertOverlay, showDialog } from './overlay';
import { UiColors, UiCurves, UiRadii, UiShadows, UiSpacing, UiTypography, boxShadow, kClientFontStack, withAlpha } from './tokens';

export const floatingNoticeVisibleDuration = 3000;
const _floatingNoticeAnimationDuration = 180;
const _floatingNoticeTopInset = 56;
// Notices sit above the overlay layers (dialogs, menus), like Flutter's
// MaterialApp.builder host above the Navigator.
const _floatingNoticeZIndex = 1100;

export enum FloatingNoticeTone {
  info = 'info',
  success = 'success',
  error = 'error',
}

export class FloatingNotice {
  constructor(opts: { message: string; tone?: FloatingNoticeTone; duration?: number | null; eventKey?: unknown }) {
    this.message = opts.message;
    this.tone = opts.tone ?? FloatingNoticeTone.info;
    this.duration = opts.duration === undefined ? floatingNoticeVisibleDuration : opts.duration;
    this.eventKey = opts.eventKey ?? null;
  }

  readonly message: string;
  readonly tone: FloatingNoticeTone;
  /** Visible time in ms; `null` keeps the notice until it is closed. */
  readonly duration: number | null;
  readonly eventKey: unknown;

  get normalized(): FloatingNotice | null {
    const trimmed = this.message.trim();
    if (trimmed.length === 0) return null;
    if (trimmed === this.message) return this;
    return new FloatingNotice({ message: trimmed, tone: this.tone, duration: this.duration, eventKey: this.eventKey });
  }

  equals(other: FloatingNotice | null | undefined): boolean {
    return (
      other != null &&
      other.message === this.message &&
      other.tone === this.tone &&
      other.duration === this.duration &&
      Object.is(other.eventKey, this.eventKey)
    );
  }
}

interface _FloatingNoticeEntry {
  id: number;
  notice: FloatingNotice;
}

interface _HostHandle {
  show(notice: FloatingNotice): void;
}

export class AppNotificationController {
  /** @internal Created by [AppNotificationHost]. */
  constructor() {}

  /** @internal */
  _host: _HostHandle | null = null;

  get isAttached(): boolean {
    return this._host != null;
  }

  show(message: string, opts: { tone?: FloatingNoticeTone; duration?: number | null } = {}): void {
    const notice = new FloatingNotice({ message, tone: opts.tone, duration: opts.duration }).normalized;
    if (notice == null) return;
    this._host?.show(notice);
  }
}

const _AppNotificationScope = createContext<AppNotificationController | null>(null);

// Mounted hosts, most recent last: the context-free lookups use the newest.
const _attachedHosts: AppNotificationController[] = [];

export interface AppNotificationHostProps {
  children?: ReactNode;
}

const _maxVisibleNotices = 3;

function AppNotificationHostImpl({ children }: AppNotificationHostProps) {
  const controllerRef = useRef<AppNotificationController | null>(null);
  controllerRef.current ??= new AppNotificationController();
  const controller = controllerRef.current;
  const [notices, setNotices] = useState<_FloatingNoticeEntry[]>([]);
  const nextNoticeId = useRef(0);

  useEffect(() => {
    controller._host = {
      show(notice) {
        setNotices((current) => {
          const next = [{ id: nextNoticeId.current++, notice }, ...current];
          return next.length > _maxVisibleNotices ? next.slice(0, _maxVisibleNotices) : next;
        });
      },
    };
    _attachedHosts.push(controller);
    return () => {
      controller._host = null;
      const index = _attachedHosts.lastIndexOf(controller);
      if (index >= 0) _attachedHosts.splice(index, 1);
    };
  }, [controller]);

  const remove = (id: number) => setNotices((current) => current.filter((entry) => entry.id !== id));

  return (
    <_AppNotificationScope.Provider value={controller}>
      {children}
      {notices.length > 0 ? <_FloatingNoticeStack entries={notices} onDismissed={remove} /> : null}
    </_AppNotificationScope.Provider>
  );
}

/** The nearest host's controller (Dart `AppNotificationHost.maybeOf(context)`), as a hook. */
export function useAppNotificationController(): AppNotificationController | null {
  return useContext(_AppNotificationScope);
}

/**
 * Port of `AppNotificationHost`. Its static `maybeOf()` / `of()` drop the
 * `BuildContext`: they return the most recently mounted host's controller.
 * Inside components prefer [useAppNotificationController].
 */
export const AppNotificationHost = Object.assign(AppNotificationHostImpl, {
  maybeOf(): AppNotificationController | null {
    return _attachedHosts[_attachedHosts.length - 1] ?? null;
  },
  of(): AppNotificationController {
    const controller = _attachedHosts[_attachedHosts.length - 1] ?? null;
    if (controller == null) throw new Error('No AppNotificationHost found in context.');
    return controller;
  },
});

export interface FloatingNoticeEmitterProps {
  notices: readonly FloatingNotice[];
  children?: ReactNode;
}

/** Shows each notice in [notices] once, when it first appears in the list. */
export function FloatingNoticeEmitter({ notices, children }: FloatingNoticeEmitterProps) {
  const active = useRef<FloatingNotice[]>([]);
  const controller = useAppNotificationController();
  const controllerRef = useRef(controller);
  controllerRef.current = controller;

  // initState + didUpdateWidget: sync on every build.
  const next: FloatingNotice[] = [];
  for (const notice of notices) {
    const normalized = notice.normalized;
    if (normalized != null && !next.some((n) => n.equals(normalized))) next.push(normalized);
  }
  const fresh = next.filter((notice) => !active.current.some((previous) => previous.equals(notice)));
  active.current = next;
  const freshRef = useRef<FloatingNotice[]>([]);
  if (fresh.length > 0) freshRef.current = [...freshRef.current, ...fresh];

  useEffect(() => {
    const pending = freshRef.current;
    if (pending.length === 0) return;
    freshRef.current = [];
    for (const notice of pending) {
      _showFloatingNoticeVia(controllerRef.current, notice);
    }
  });

  return <>{children}</>;
}

function _showFloatingNoticeVia(controller: AppNotificationController | null, notice: FloatingNotice): void {
  const target = controller ?? AppNotificationHost.maybeOf();
  if (target != null && target.isAttached) {
    target.show(notice.message, { tone: notice.tone, duration: notice.duration });
    return;
  }
  _showStandaloneFloatingNotice(notice);
}

/**
 * Shows a floating notice in the app's notification host (or a standalone
 * overlay when no host is mounted). Port of `showFloatingNotice(context, …)`;
 * the `BuildContext` argument is dropped.
 */
export function showFloatingNotice(
  message: string,
  opts: { tone?: FloatingNoticeTone; duration?: number | null } = {},
): void {
  const notice = new FloatingNotice({ message, tone: opts.tone, duration: opts.duration }).normalized;
  if (notice == null) return;
  _showFloatingNoticeVia(null, notice);
}

export function showFloatingSuccessNotice(message: string, opts: { duration?: number | null } = {}): void {
  showFloatingNotice(message, { tone: FloatingNoticeTone.success, duration: opts.duration });
}

export function showFloatingErrorNotice(message: string, opts: { duration?: number | null } = {}): void {
  showFloatingNotice(message, { tone: FloatingNoticeTone.error, duration: opts.duration });
}

function _showStandaloneFloatingNotice(notice: FloatingNotice): void {
  const entry = insertOverlay(() => (
    <_FloatingNoticeStack entries={[{ id: 0, notice }]} onDismissed={() => entry.remove()} />
  ));
}

function _FloatingNoticeStack({ entries, onDismissed }: { entries: readonly _FloatingNoticeEntry[]; onDismissed: (id: number) => void }) {
  return (
    <div
      style={{
        position: 'fixed',
        // MediaQuery padding (system insets) is zero on desktop windows.
        top: _floatingNoticeTopInset,
        left: 16,
        right: 16,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
        // The stack never blocks the page; only the toasts take input.
        pointerEvents: 'none',
        zIndex: _floatingNoticeZIndex,
      }}
    >
      {entries.map((entry) => (
        <_AnimatedFloatingNotice key={entry.id} notice={entry.notice} onDismissed={() => onDismissed(entry.id)} />
      ))}
    </div>
  );
}

type _NoticePhase = 'entering' | 'visible' | 'exiting';

function _AnimatedFloatingNotice({ notice, onDismissed }: { notice: FloatingNotice; onDismissed: () => void }) {
  const [phase, setPhase] = useState<_NoticePhase>('entering');
  const closing = useRef(false);
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);
  const onDismissedRef = useRef(onDismissed);
  onDismissedRef.current = onDismissed;

  const later = (ms: number, fn: () => void) => {
    timers.current.push(setTimeout(fn, ms));
  };

  const close = (animated: boolean) => {
    if (closing.current) return;
    closing.current = true;
    for (const timer of timers.current) clearTimeout(timer);
    timers.current = [];
    if (!animated) {
      onDismissedRef.current();
      return;
    }
    setPhase('exiting');
    later(_floatingNoticeAnimationDuration, () => onDismissedRef.current());
  };

  useLayoutEffect(() => {
    // Start the forward animation on the next frame (from the entering pose).
    const frame = requestAnimationFrame(() => setPhase((p) => (p === 'entering' ? 'visible' : p)));
    later(_floatingNoticeAnimationDuration, () => {
      if (closing.current) return;
      const duration = notice.duration;
      if (duration != null) later(duration, () => close(true));
    });
    return () => {
      cancelAnimationFrame(frame);
      for (const timer of timers.current) clearTimeout(timer);
      timers.current = [];
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const shown = phase === 'visible';
  const curve = phase === 'exiting' ? UiCurves.easeInCubic : UiCurves.easeOutCubic;
  return (
    <div
      style={{
        opacity: shown ? 1 : 0,
        // SlideTransition Offset(0, -0.16): a fraction of the toast's height.
        transform: shown ? 'translateY(0)' : 'translateY(-16%)',
        transition: `opacity ${_floatingNoticeAnimationDuration}ms ${curve}, transform ${_floatingNoticeAnimationDuration}ms ${curve}`,
        maxWidth: '100%',
        pointerEvents: 'auto',
      }}
    >
      <Toast message={notice.message} tone={notice.tone} icon={_noticeIcon(notice.tone)} onClose={() => close(false)} />
    </div>
  );
}

function _noticeIcon(tone: FloatingNoticeTone): IconData {
  switch (tone) {
    case FloatingNoticeTone.success:
      return Icons.check_circle_outline;
    case FloatingNoticeTone.error:
      return Icons.error_outline;
    case FloatingNoticeTone.info:
      return Icons.info_outline;
  }
}

function _noticeAccent(tone: FloatingNoticeTone): string {
  switch (tone) {
    case FloatingNoticeTone.success:
      return UiColors.success;
    case FloatingNoticeTone.error:
      return UiColors.danger;
    case FloatingNoticeTone.info:
      return UiColors.accent;
  }
}

export interface ToastProps {
  message: string;
  icon?: IconData | null;
  tone?: FloatingNoticeTone;
  onClose?: (() => void) | null;
}

export function Toast({ message, icon, tone = FloatingNoticeTone.info, onClose }: ToastProps) {
  const accent = _noticeAccent(tone);
  return (
    <div
      role="status"
      style={{
        boxSizing: 'border-box',
        maxWidth: 520,
        width: 'fit-content',
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: UiColors.surfaceRaised,
        borderRadius: UiRadii.md + 2,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow(UiShadows.popover),
        padding: `${UiSpacing.sm}px ${onClose == null ? UiSpacing.md : UiSpacing.xs}px ${UiSpacing.sm}px ${UiSpacing.sm}px`,
      }}
    >
      {icon != null ? (
        <>
          <div
            style={{
              width: 24,
              height: 24,
              flexShrink: 0,
              borderRadius: '50%',
              backgroundColor: withAlpha(accent, 0.16),
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon icon={icon} size={15} color={accent} />
          </div>
          <div style={{ width: 10, flexShrink: 0 }} />
        </>
      ) : null}
      <span
        style={{
          ...UiTypography.label,
          color: UiColors.text,
          textDecoration: 'none',
          fontFamily: kClientFontStack,
          fontWeight: 500,
          flex: '0 1 auto',
          minWidth: 0,
          whiteSpace: 'pre-wrap',
          overflowWrap: 'break-word',
        }}
      >
        {message}
      </span>
      {onClose != null ? (
        <>
          <div style={{ width: UiSpacing.sm, flexShrink: 0 }} />
          <_NoticeCloseButton onPressed={onClose} />
        </>
      ) : null}
    </div>
  );
}

function _NoticeCloseButton({ onPressed }: { onPressed: () => void }) {
  const [hovered, setHovered] = useState(false);
  const [pressed, setPressed] = useState(false);
  const active = hovered || pressed;
  return (
    <div
      role="button"
      aria-label="关闭"
      data-testid="floating-notice-close-button"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => {
        setHovered(false);
        setPressed(false);
      }}
      onPointerDown={(event) => {
        if (event.button === 0) setPressed(true);
      }}
      onPointerUp={() => setPressed(false)}
      onPointerCancel={() => setPressed(false)}
      onClick={(event) => {
        if (event.button !== 0) return;
        onPressed();
      }}
      style={{
        width: 26,
        height: 26,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: 'pointer',
        backgroundColor: pressed ? UiColors.surface : active ? UiColors.surfaceHover : UiColors.transparent,
        borderRadius: UiRadii.sm,
        transition: `background-color 95ms ${UiCurves.easeOutCubic}`,
      }}
    >
      <Icon icon={Icons.close} size={15} color={active ? UiColors.textSecondary : UiColors.textMuted} />
    </div>
  );
}

export class ResponsiveDialogAction {
  constructor(opts: {
    label: string;
    onPressed: (() => void) | null;
    buttonKey?: string | null;
    icon?: IconData | null;
    tone?: ButtonTone;
    loading?: boolean;
  }) {
    this.label = opts.label;
    this.onPressed = opts.onPressed;
    this.buttonKey = opts.buttonKey ?? null;
    this.icon = opts.icon ?? null;
    this.tone = opts.tone ?? ButtonTone.neutral;
    this.loading = opts.loading ?? false;
  }

  readonly label: string;
  readonly onPressed: (() => void) | null;
  /** Test id of the button (Dart `buttonKey`). */
  readonly buttonKey: string | null;
  readonly icon: IconData | null;
  readonly tone: ButtonTone;
  readonly loading: boolean;
}

export interface ResponsiveDialogActionBarProps {
  actions: readonly ResponsiveDialogAction[];
  leadingActionCount?: number;
  expanded?: boolean;
}

const _gap = 10;

export function ResponsiveDialogActionBar({ actions, leadingActionCount = 0, expanded = false }: ResponsiveDialogActionBarProps) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  if (actions.length === 0) return null;

  // LayoutBuilder: unmeasured counts as unbounded.
  const maxWidth = size?.width ?? Infinity;
  const finite = Number.isFinite(maxWidth);
  const actionWidths = actions.map((action) => Button.minimumWidthForLabel({ label: action.label, hasIcon: action.icon != null }));
  const requiredWithIcons = actionWidths.reduce((sum, width) => sum + width, 0) + _gap * (actions.length - 1);
  const actionWidthsWithoutIcons = actions.map((action) => Button.minimumWidthForLabel({ label: action.label }));
  const requiredWithoutIcons = actionWidthsWithoutIcons.reduce((sum, width) => sum + width, 0) + _gap * (actions.length - 1);
  const showIcons = !finite || requiredWithIcons <= maxWidth;
  const compact = !showIcons && finite;
  const stack = finite && requiredWithoutIcons > maxWidth;
  const splitIndex = Math.min(Math.max(leadingActionCount, 0), actions.length);

  const button = (action: ResponsiveDialogAction, key: number) => (
    <Button
      key={key}
      data-testid={action.buttonKey ?? undefined}
      onPressed={action.onPressed}
      tone={action.tone}
      loading={action.loading}
      icon={showIcons && action.icon != null ? <Icon icon={action.icon} /> : null}
      width={compact || expanded || stack ? Infinity : null}
    >
      {action.label}
    </Button>
  );

  let content: ReactNode;
  if (stack) {
    content = (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch', gap: _gap }}>
        {actions.map((action, index) => button(action, index))}
      </div>
    );
  } else if (compact || expanded) {
    if (finite) {
      // Use each action's real content width on every platform. Equal
      // widths can truncate a long label even when the row has enough
      // total space. Give every action its measured minimum first, then
      // share the spare width so a fitting row still fills the dialog.
      const widths = showIcons ? actionWidths : actionWidthsWithoutIcons;
      const buttonSpace = maxWidth - _gap * (actions.length - 1);
      const naturalWidth = widths.reduce((sum, width) => sum + width, 0);
      const extraPerButton = Math.max((buttonSpace - naturalWidth) / actions.length, 0);
      content = (
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: _gap }}>
          {actions.map((action, index) => (
            <div key={index} style={{ width: widths[index]! + extraPerButton, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
              {button(action, index)}
            </div>
          ))}
        </div>
      );
    } else {
      content = (
        <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', gap: _gap }}>
          {actions.map((action, index) => (
            <div key={index} style={{ flex: '1 1 0', minWidth: 0, display: 'flex', flexDirection: 'column' }}>
              {button(action, index)}
            </div>
          ))}
        </div>
      );
    }
  } else {
    const leading = actions.slice(0, splitIndex);
    const trailing = actions.slice(splitIndex);
    content = (
      <div
        style={{
          display: 'flex',
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: splitIndex === 0 ? 'flex-end' : 'flex-start',
          gap: _gap,
        }}
      >
        {leading.map((action, index) => button(action, index))}
        {leading.length > 0 && trailing.length > 0 ? <div style={{ flex: '1 1 0', minWidth: 0 }} /> : null}
        {trailing.map((action, index) => button(action, splitIndex + index))}
      </div>
    );
  }

  return (
    <div ref={ref} style={{ width: '100%', minWidth: 0 }}>
      {content}
    </div>
  );
}

export interface DialogFrameProps {
  title: string;
  children?: ReactNode;
  icon?: IconData | null;
  actions?: readonly ReactNode[];
  /**
   * Standard dialog actions that adapt to the available width.
   *
   * Each label is measured with the dialog's font. Icons are removed first
   * when needed, then actions stack only if the complete labels still cannot
   * fit on one row.
   */
  adaptiveActions?: readonly ResponsiveDialogAction[];
  actionBar?: ReactNode;
  maxWidth?: number;
  /** Extra style on the frame (DOM addition). */
  style?: CSSProperties;
}

// Material's Dialog minimum width.
const _dialogMinWidth = 280;

export function DialogFrame({
  title,
  children,
  icon,
  actions = [],
  adaptiveActions = [],
  actionBar,
  maxWidth = 480,
  style,
}: DialogFrameProps) {
  const resolvedActionBar = adaptiveActions.length > 0 ? <ResponsiveDialogActionBar expanded actions={adaptiveActions} /> : (actionBar ?? null);
  const resolvedActions: ReactNode[] =
    adaptiveActions.length > 0
      ? adaptiveActions.map((action, index) => (
          <Button
            key={index}
            data-testid={action.buttonKey ?? undefined}
            onPressed={action.onPressed}
            tone={action.tone}
            loading={action.loading}
            icon={action.icon == null ? null : <Icon icon={action.icon} />}
          >
            {action.label}
          </Button>
        ))
      : [...actions];
  return (
    <div
      role="dialog"
      aria-label={title}
      style={{
        width: maxWidth,
        maxWidth: '100%',
        minWidth: _dialogMinWidth,
        minHeight: 0,
        flexShrink: 1,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: UiRadii.xl,
        border: `1px solid ${UiColors.borderStrong}`,
        boxShadow: boxShadow(UiShadows.dialog),
        padding: '20px 22px',
        ...style,
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'row', alignItems: 'center', flexShrink: 0 }}>
        {icon != null ? (
          <>
            <div
              style={{
                width: 32,
                height: 32,
                flexShrink: 0,
                backgroundColor: UiColors.selected,
                borderRadius: UiRadii.md,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Icon icon={icon} size={17} color={UiColors.accent} />
            </div>
            <div style={{ width: 12, flexShrink: 0 }} />
          </>
        ) : null}
        <span style={{ ...UiTypography.title, flex: '1 1 0', minWidth: 0, overflowWrap: 'break-word' }}>{title}</span>
      </div>
      <div style={{ height: 16, flexShrink: 0 }} />
      {/* The dialog keeps its content within the window: let the content
          yield vertical space instead of overflowing below the action bar. */}
      <div style={{ flex: '0 1 auto', minHeight: 0, display: 'flex', flexDirection: 'column' }}>{children}</div>
      {resolvedActions.length > 0 || resolvedActionBar != null ? (
        <>
          <div style={{ height: 20, flexShrink: 0 }} />
          {resolvedActionBar != null ? (
            <div style={{ flexShrink: 0 }}>{resolvedActionBar}</div>
          ) : (
            // Flutter's Wrap(alignment: end) hands each action the full row
            // width, so buttons take whole rows and narrower widgets align
            // to the end.
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 10, flexShrink: 0 }}>
              {resolvedActions}
            </div>
          )}
        </>
      ) : null}
    </div>
  );
}

/** Port of `showUiDialog(context, …)`; the `BuildContext` argument is dropped. */
export function showUiDialog(opts: { title: string; child: ReactNode; icon?: IconData | null }): Promise<void> {
  return showDialog<void>((close) => (
    <DialogFrame
      title={opts.title}
      icon={opts.icon}
      actions={[
        <Button key="done" onPressed={() => close()} tone={ButtonTone.primary}>
          完成
        </Button>,
      ]}
    >
      {opts.child}
    </DialogFrame>
  )).then(() => undefined);
}
