import { describe, expect, test } from 'vitest';

import { _anchoredPanelPosition } from '../../src/ui/anchored_panel';
import { avatarFallbackColor, avatarInitials, avatarPresetLabel, normalizeAvatarPresetKey } from '../../src/ui/avatar';
import { _contextMenuPosition } from '../../src/ui/context_menu';
import { FloatingNotice, FloatingNoticeTone, floatingNoticeVisibleDuration } from '../../src/ui/feedback';
import { fileIconForMime } from '../../src/ui/file_icons';
import { genderMark } from '../../src/ui/gender_mark';
import { _highlightedSpans } from '../../src/ui/highlighted_text';
import { Icons } from '../../src/ui/icon';
import { RoleBadgeTone, roleBadgeForegroundColorForLabel, roleBadgeToneForLabel } from '../../src/ui/role_badge';
import { _fitSegmentWidths } from '../../src/ui/segmented_control';
import { UiColors } from '../../src/ui/tokens';
import { positionDependentBox } from '../../src/ui/tooltip';

describe('highlighted text', () => {
  test('highlights all case-insensitive query matches', () => {
    expect(_highlightedSpans({ text: 'Alice and ALICE', query: ' alice ' })).toEqual([
      { text: 'Alice', highlighted: true },
      { text: ' and ', highlighted: false },
      { text: 'ALICE', highlighted: true },
    ]);
  });

  test('uses plain text when query does not match', () => {
    expect(_highlightedSpans({ text: 'Bob', query: 'alice' })).toBeNull();
    expect(_highlightedSpans({ text: 'Bob', query: '  ' })).toBeNull();
  });
});

describe('avatar fallback', () => {
  test('initials take the first two characters', () => {
    expect(avatarInitials('  ab cd ')).toBe('AB');
    expect(avatarInitials('张三丰')).toBe('张三');
    expect(avatarInitials('')).toBe('?');
  });

  test('preset keys normalize legacy aliases and expose Chinese names', () => {
    expect(normalizeAvatarPresetKey('room-1')).toBe('blue-3');
    expect(normalizeAvatarPresetKey('  ')).toBe('blue-3');
    expect(avatarPresetLabel('mint-2')).toBe('薄荷绿');
    expect(avatarPresetLabel('unknown')).toBe('蓝色');
    expect(avatarFallbackColor('red-2')).toBe('#C9363F');
    expect(avatarFallbackColor('nope')).toBe('#4E68D0');
  });
});

describe('display mappings', () => {
  test('role badge maps role labels to blue yellow orange red colors', () => {
    expect(roleBadgeToneForLabel(' 成员 ')).toBe(RoleBadgeTone.member);
    expect(roleBadgeToneForLabel('管理员')).toBe(RoleBadgeTone.admin);
    expect(roleBadgeToneForLabel('创建者')).toBe(RoleBadgeTone.creator);
    expect(roleBadgeToneForLabel('超级用户')).toBe(RoleBadgeTone.superuser);
    expect(roleBadgeToneForLabel('访客')).toBe(RoleBadgeTone.neutral);
    expect(roleBadgeForegroundColorForLabel('管理员')).toBe(UiColors.roleAdmin);
  });

  test('gender marks and file icons', () => {
    expect(genderMark(' Male ')?.symbol).toBe('♂');
    expect(genderMark('f')?.symbol).toBe('♀');
    expect(genderMark('other')).toBeNull();
    expect(fileIconForMime('image/png')).toBe(Icons.image_outlined);
    expect(fileIconForMime('application/x-tar')).toBe(Icons.folder_zip_outlined);
    expect(fileIconForMime('application/json')).toBe(Icons.description_outlined);
    expect(fileIconForMime(null)).toBe(Icons.insert_drive_file_outlined);
  });
});

describe('floating notice', () => {
  test('normalizes messages and compares by value', () => {
    expect(new FloatingNotice({ message: '   ' }).normalized).toBeNull();
    const notice = new FloatingNotice({ message: ' 已保存 ', tone: FloatingNoticeTone.success });
    expect(notice.normalized?.message).toBe('已保存');
    expect(notice.duration).toBe(floatingNoticeVisibleDuration);
    expect(new FloatingNotice({ message: 'a', duration: null }).duration).toBeNull();
    expect(new FloatingNotice({ message: 'a', eventKey: 1 }).equals(new FloatingNotice({ message: 'a', eventKey: 1 }))).toBe(true);
    expect(new FloatingNotice({ message: 'a', eventKey: 1 }).equals(new FloatingNotice({ message: 'a', eventKey: 2 }))).toBe(false);
  });
});

describe('layout delegates', () => {
  test('tooltip prefers below and flips above near the bottom edge', () => {
    const size = { width: 800, height: 600 };
    const childSize = { width: 100, height: 24 };
    expect(positionDependentBox({ size, childSize, target: { x: 400, y: 100 }, preferBelow: true, verticalOffset: 24 })).toEqual({
      x: 350,
      y: 124,
    });
    expect(positionDependentBox({ size, childSize, target: { x: 400, y: 580 }, preferBelow: true, verticalOffset: 24 })).toEqual({
      x: 350,
      y: 532,
    });
    // Clamped 10px from the left edge.
    expect(positionDependentBox({ size, childSize, target: { x: 5, y: 100 }, preferBelow: true, verticalOffset: 24 }).x).toBe(10);
  });

  test('context menu stays inside the padded window', () => {
    const window = { width: 400, height: 300 };
    expect(_contextMenuPosition({ x: 100, y: 50 }, { width: 196, height: 100 }, window)).toEqual({ x: 100, y: 50 });
    expect(_contextMenuPosition({ x: 390, y: 290 }, { width: 196, height: 100 }, window)).toEqual({ x: 196, y: 192 });
  });

  test('anchored panel prefers above, then below, then the roomier side', () => {
    const anchorRect = { left: 100, top: 400, right: 140, bottom: 440, width: 40, height: 40 };
    const overlaySize = { width: 800, height: 600 };
    expect(_anchoredPanelPosition({ anchorRect, overlaySize, panelSize: { width: 320, height: 200 }, gap: 8 })).toEqual({
      left: 0,
      top: 192,
    });
    const nearTop = { left: 300, top: 20, right: 340, bottom: 60, width: 40, height: 40 };
    expect(_anchoredPanelPosition({ anchorRect: nearTop, overlaySize, panelSize: { width: 320, height: 200 }, gap: 8 })).toEqual({
      left: 160,
      top: 68,
    });
  });

  test('segment widths scale up when expanded and compress paddings when narrow', () => {
    const contentWidths = [40, 60];
    const preferredWidths = [60, 80];
    const expanded = _fitSegmentWidths({
      segmentCount: 2,
      contentWidths,
      preferredWidths,
      minimumWidth: 124,
      preferredWidth: 140,
      targetWidth: 280,
    });
    expect(expanded.widths).toEqual([120, 160]);
    expect(expanded.horizontalPadding).toBe(10);
    const narrow = _fitSegmentWidths({
      segmentCount: 2,
      contentWidths,
      preferredWidths,
      minimumWidth: 124,
      preferredWidth: 140,
      targetWidth: 132,
    });
    expect(narrow.horizontalPadding).toBe(8);
    expect(narrow.widths.reduce((a, b) => a + b, 0)).toBe(132);
  });
});
