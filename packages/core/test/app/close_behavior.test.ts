import { describe, expect, test } from 'vitest';

import {
  CloseBehavior,
  CloseBehaviorStorage,
  closeBehaviorDescription,
  closeBehaviorFromStorageValue,
  closeBehaviorLabel,
  closeBehaviorStorageValue,
  defaultCloseBehavior,
} from '../../src/app/close_behavior';

describe('close_behavior', () => {
  test('unknown stored close behavior falls back to asking every time', () => {
    expect(closeBehaviorFromStorageValue('older-client-value')).toBe(defaultCloseBehavior);
    expect(closeBehaviorFromStorageValue(null)).toBe(CloseBehavior.askEveryTime);
  });

  test('storage values round-trip', () => {
    for (const behavior of Object.values(CloseBehavior)) {
      expect(closeBehaviorFromStorageValue(closeBehaviorStorageValue(behavior))).toBe(behavior);
      expect(CloseBehaviorStorage.storageValue(behavior)).toBe(closeBehaviorStorageValue(behavior));
    }
    expect(closeBehaviorStorageValue(CloseBehavior.minimizeToTray)).toBe('minimize_to_tray');
  });

  test('labels and descriptions', () => {
    expect(closeBehaviorLabel(CloseBehavior.askEveryTime)).toBe('每次询问');
    expect(closeBehaviorLabel(CloseBehavior.exitProgram)).toBe('直接退出程序');
    expect(closeBehaviorDescription(CloseBehavior.minimizeToTray)).toBe('关闭窗口时保留当前状态并在后台运行。');
  });
});
