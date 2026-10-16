import type { RoomDetail } from '@gang-chat/core/protocol/models';

import { UiRadii } from '../ui/tokens';

/*
 * Port of `lib/src/home/room_management.dart`, the library root of the room
 * management part files:
 *
 *  - `room_members_dialog.tsx`       成员面板（房间成员 / 新成员 / 黑名单）
 *  - `room_settings_dialog.tsx`      房间设置 / 创建房间
 *  - `room_members_components.tsx`   成员面板的行、筛选与分区
 *  - `room_management_components.tsx` 共用外壳、确认框与图标裁剪
 *
 * The part files are separate modules (docs/js-port-conventions.md §2); the
 * shared private constants below are exported with their leading underscore.
 * Part modules read them only inside functions, never at module evaluation,
 * so the import cycle with the re-exports at the bottom is safe.
 */

export const _dialogMaxWidth = 680;
export const _dialogMaxHeight = 700;
export const _panelRadius = UiRadii.lg;
export const _rowRadius = UiRadii.md;

export enum RoomManagementResultKind {
  created = 'created',
  updated = 'updated',
  left = 'left',
  deleted = 'deleted',
}

export class RoomManagementResult {
  private constructor(opts: { kind: RoomManagementResultKind; room?: RoomDetail | null }) {
    this.kind = opts.kind;
    this.room = opts.room ?? null;
  }

  static created(room: RoomDetail): RoomManagementResult {
    return new RoomManagementResult({ kind: RoomManagementResultKind.created, room });
  }

  static updated(room: RoomDetail): RoomManagementResult {
    return new RoomManagementResult({ kind: RoomManagementResultKind.updated, room });
  }

  static left(): RoomManagementResult {
    return new RoomManagementResult({ kind: RoomManagementResultKind.left });
  }

  static deleted(): RoomManagementResult {
    return new RoomManagementResult({ kind: RoomManagementResultKind.deleted });
  }

  readonly kind: RoomManagementResultKind;
  readonly room: RoomDetail | null;
}

// The Dart library exposes its part files' public widgets to everyone who
// imports `room_management.dart`; keep that surface for importers of this
// module (the definitions live in the part modules).
export { RoomMembersDialog, type RoomMembersDialogProps } from './room_members_dialog';
export { RoomSettingsDialog, RoomSettingsSection, type RoomSettingsDialogProps } from './room_settings_dialog';
