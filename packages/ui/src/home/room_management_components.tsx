import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react';

import { avatarUploadFilename } from '@gang-chat/core/app/account_display';
import { FileSelectionService, FileTypeGroup, type SelectedFile } from '@gang-chat/core/shell/file_selection_service';

import { Center, Column, Expanded, Row, SizedBox, Text } from '../lib/layout';
import { TextEditingController } from '../lib/text_editing';
import { useElementSize } from '../lib/use_element_size';
import { useListenable } from '../lib/use_listenable';
import { AvatarCropDialog } from '../ui/avatar_crop_dialog';
import { ButtonIcon, ButtonTone } from '../ui/button';
import { DialogFrame, ResponsiveDialogAction } from '../ui/feedback';
import { Icon, Icons, type IconData } from '../ui/icon';
import { Input } from '../ui/input';
import { showDialog, useRoute } from '../ui/overlay';
import { SegmentedControl, type Segment } from '../ui/segmented_control';
import { SettingsScaffold } from '../ui/settings_scaffold';
import { UiSwitch } from '../ui/switch';
import { UiColors, UiTypography, kClientFontStack } from '../ui/tokens';
import { _panelRadius, _rowRadius } from './room_management';

// Port of `room_management_components.dart` (part of `room_management.dart`).

export interface _RoomDialogShellProps {
  title: string;
  icon: IconData;
  children: ReactNode;
  onClose: () => void;
  maxWidth: number;
  maxHeight: number;
  headerAction?: ReactNode;
  pinned?: ReactNode;
  embedded?: boolean;
}

export function _RoomDialogShell({
  title,
  icon,
  children,
  onClose,
  maxWidth,
  maxHeight,
  headerAction = null,
  pinned = null,
  embedded = false,
}: _RoomDialogShellProps) {
  if (embedded) {
    return <SettingsScaffold icon={icon} title={title} headerAction={headerAction} onBack={onClose} pinned={pinned} body={children} />;
  }

  // PORT: Flutter's `Dialog(insetPadding: 20/20)` keeps the panel 20px from
  // the window edges; the shared `showDialog` overlay centres dialogs inside a
  // fixed 24px inset, so the panel can come 4px less close to the edges.
  return (
    <div
      style={{
        width: maxWidth,
        height: maxHeight,
        maxWidth: '100%',
        maxHeight: '100%',
        minHeight: 0,
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: _panelRadius,
        // A DecoratedBox border paints over the padding without taking space.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}`,
        padding: '16px 22px 18px',
      }}
    >
      <Row>
        <Icon icon={icon} color={UiColors.accent} size={19} />
        <SizedBox width={8} />
        <Expanded>
          <Text style={{ fontFamily: kClientFontStack, color: UiColors.text, fontSize: 20, fontWeight: 600 }}>{title}</Text>
        </Expanded>
        {headerAction}
        {headerAction != null ? <SizedBox width={4} /> : null}
        <ButtonIcon tooltip="关闭" icon={<Icon icon={Icons.close} />} onPressed={onClose} size={38} />
      </Row>
      {pinned != null ? (
        <>
          <SizedBox height={14} />
          {pinned}
        </>
      ) : null}
      <SizedBox height={14} />
      <Expanded style={{ alignItems: 'stretch' }}>{children}</Expanded>
    </div>
  );
}

export function _RowSurface({ children, compact = false }: { children: ReactNode; compact?: boolean }) {
  return (
    <div
      style={{
        boxSizing: 'border-box',
        backgroundColor: UiColors.surfaceLow,
        borderRadius: _rowRadius,
        // DecoratedBox border: painted over the padding, not inset by it.
        boxShadow: `inset 0 0 0 1px ${UiColors.border}`,
        padding: `${compact ? 7 : 9}px ${compact ? 9 : 10}px`,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
      }}
    >
      {children}
    </div>
  );
}

export function _RoomFieldLabel({ label }: { label: string }) {
  return <Text style={{ ...UiTypography.label, color: UiColors.textMuted }}>{label}</Text>;
}

export function _EmptyState({ icon, title }: { icon: IconData; title: string }) {
  return (
    <Center style={{ flex: '1 1 0', minHeight: 0 }}>
      <Column mainAxisSize="min">
        <Icon icon={icon} color={UiColors.textMuted} size={30} />
        <SizedBox height={8} />
        <Text style={{ ...UiTypography.body, color: UiColors.textMuted }}>{title}</Text>
      </Column>
    </Center>
  );
}

export interface _LabeledSegmentedProps<T> {
  label: string;
  value: T;
  segments: readonly Segment<T>[];
  onChanged: (value: T) => void;
  enabled?: boolean;
  /** Test id of the control (Dart `controlKey`). */
  controlKey?: string | null;
}

export function _LabeledSegmented<T>({ label, value, segments, onChanged, enabled = true, controlKey = null }: _LabeledSegmentedProps<T>) {
  return (
    <Column crossAxisAlignment="stretch">
      <_RoomFieldLabel label={label} />
      <SizedBox height={8} />
      {/* IgnorePointer + Opacity. */}
      <div data-testid={controlKey ?? undefined} style={{ pointerEvents: enabled ? undefined : 'none', opacity: enabled ? 1 : 0.5 }}>
        <SegmentedControl<T> expanded value={value} segments={segments} onChanged={onChanged} />
      </div>
    </Column>
  );
}

export interface _ToggleRowProps {
  label: string;
  value: boolean;
  onChanged: (value: boolean) => void;
  enabled?: boolean;
  /** Test id (Dart `key`). */
  testId?: string;
}

export function _ToggleRow({ label, value, onChanged, enabled = true, testId }: _ToggleRowProps) {
  return (
    <Row data-testid={testId}>
      <Expanded>
        <_RoomFieldLabel label={label} />
      </Expanded>
      <UiSwitch value={value} onChanged={enabled ? onChanged : null} tooltip={label} />
    </Row>
  );
}

export interface _ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  danger?: boolean;
}

/** Shown with `showDialog<boolean>`; closes with `true` on confirm, `false` on cancel. */
export function _ConfirmDialog({ title, message, confirmLabel, danger = false }: _ConfirmDialogProps) {
  const route = useRoute<boolean>();
  return (
    <DialogFrame
      title={title}
      icon={danger ? Icons.warning_amber_outlined : Icons.info_outline}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close(false) }),
        new ResponsiveDialogAction({
          label: confirmLabel,
          tone: danger ? ButtonTone.danger : ButtonTone.primary,
          onPressed: () => route.close(true),
        }),
      ]}
    >
      <Text style={UiTypography.body}>{message}</Text>
    </DialogFrame>
  );
}

export interface _StrongConfirmDialogProps {
  title: string;
  message: string;
  expectedText: string;
  confirmLabel: string;
}

/** A danger confirmation that unlocks only after [expectedText] is typed. */
export function _StrongConfirmDialog({ title, message, expectedText, confirmLabel }: _StrongConfirmDialogProps) {
  const route = useRoute<boolean>();
  const controllerRef = useRef<TextEditingController | null>(null);
  controllerRef.current ??= new TextEditingController();
  const controller = controllerRef.current;
  // `_controller.addListener(() => setState(() {}))`.
  useListenable(controller);
  useEffect(() => () => controller.dispose(), [controller]);

  const matches = controller.text.trim() === expectedText;
  return (
    <DialogFrame
      title={title}
      icon={Icons.warning_amber_outlined}
      adaptiveActions={[
        new ResponsiveDialogAction({ label: '取消', onPressed: () => route.close(false) }),
        new ResponsiveDialogAction({
          label: confirmLabel,
          tone: ButtonTone.danger,
          onPressed: matches ? () => route.close(true) : null,
        }),
      ]}
    >
      <Column mainAxisSize="min" crossAxisAlignment="stretch">
        <Text style={UiTypography.body}>{message}</Text>
        <SizedBox height={12} />
        <Input controller={controller} hintText={expectedText} />
      </Column>
    </DialogFrame>
  );
}

export class _CroppedRoomAvatar {
  constructor(opts: { bytes: Uint8Array; filename: string }) {
    this.bytes = opts.bytes;
    this.filename = opts.filename;
  }

  readonly bytes: Uint8Array;
  readonly filename: string;
}

/**
 * Dart's plain `Exception(message)`: its `toString()` is `Exception: message`,
 * which `userFacingErrorMessage` passes through verbatim (it contains Chinese),
 * so the notice text stays identical to the Flutter build.
 */
class _Exception extends Error {
  override toString(): string {
    return `Exception: ${this.message}`;
  }
}

/**
 * Open a file picker, read the bytes, and run them through the shared
 * [AvatarCropDialog]. Returns null if the user cancels at any step.
 *
 * PORT: Dart checked `context.mounted` before opening the crop dialog; the
 * caller passes [isMounted] instead.
 */
export async function _pickAndCropRoomAvatar(isMounted: () => boolean = () => true): Promise<_CroppedRoomAvatar | null> {
  const fileSelectionService = new FileSelectionService();
  let file: SelectedFile | null;
  try {
    file = await fileSelectionService.openFile({
      acceptedTypeGroups: [new FileTypeGroup({ label: '图片', extensions: ['png', 'jpg', 'jpeg', 'webp'] })],
    });
  } catch {
    throw new _Exception('无法打开文件选择器');
  }
  if (file == null) return null;

  let bytes: Uint8Array;
  try {
    bytes = await file.readAsBytes();
  } catch {
    throw new _Exception('无法读取图片');
  }
  if (bytes.length === 0) {
    throw new _Exception('图片文件为空');
  }
  if (!isMounted()) return null;

  const cropped = await showDialog<Uint8Array>(() => <AvatarCropDialog bytes={bytes} title="裁剪图标" />, {
    barrierDismissible: false,
  });
  if (cropped == null) return null;
  return new _CroppedRoomAvatar({
    bytes: cropped,
    filename: avatarUploadFilename(file.name),
  });
}

/**
 * Port of Flutter's `LayoutBuilder` for these panels: renders [builder] with
 * the measured width of its own box (`Infinity` until the first measurement,
 * like unbounded constraints). The box takes [style], so it can be the
 * scrolling viewport whose width the content actually gets.
 */
export function _LayoutBuilder({
  builder,
  style,
  'data-testid': testId,
}: {
  builder: (maxWidth: number) => ReactNode;
  style?: CSSProperties;
  'data-testid'?: string;
}) {
  const ref = useRef<HTMLDivElement | null>(null);
  const size = useElementSize(ref);
  return (
    <div ref={ref} data-testid={testId} style={{ minWidth: 0, ...style }}>
      {builder(size?.width ?? Infinity)}
    </div>
  );
}
