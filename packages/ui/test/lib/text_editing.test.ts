import { describe, expect, test } from 'vitest';

import {
  FilteringTextInputFormatter,
  FocusNode,
  LengthLimitingTextInputFormatter,
  TextEditingController,
  TextEditingValue,
  TextSelection,
  UndoHistoryController,
} from '../../src/lib/text_editing';

describe('TextEditingController', () => {
  test('notifies on text and selection changes and clears to an empty value', () => {
    const controller = new TextEditingController({ text: 'alice' });
    let notifications = 0;
    controller.addListener(() => notifications++);
    controller.selection = TextSelection.collapsed({ offset: 2 });
    controller.text = 'bob';
    expect(controller.selection.isValid).toBe(false);
    controller.clear();
    expect(controller.text).toBe('');
    expect(controller.selection.baseOffset).toBe(0);
    expect(notifications).toBe(3);
  });

  test('formatters filter digits and limit length', () => {
    const empty = TextEditingValue.empty;
    const digits = FilteringTextInputFormatter.digitsOnly.formatEditUpdate(
      empty,
      new TextEditingValue({ text: 'a1b2', selection: TextSelection.collapsed({ offset: 4 }) }),
    );
    expect(digits.text).toBe('12');
    const limited = new LengthLimitingTextInputFormatter(3).formatEditUpdate(
      empty,
      new TextEditingValue({ text: 'abcdef', selection: TextSelection.collapsed({ offset: 6 }) }),
    );
    expect(limited.text).toBe('abc');
  });

  test('undo history restores committed values', () => {
    const controller = new TextEditingController({ text: 'a' });
    const undo = new UndoHistoryController();
    undo._attach(controller);
    controller.text = 'ab';
    controller.text = 'abc';
    expect(undo.canUndo).toBe(true);
    undo.undo();
    expect(controller.text).toBe('ab');
    undo.redo();
    expect(controller.text).toBe('abc');
  });

  test('focus requests wait for the field to attach', () => {
    const node = new FocusNode();
    let focused = 0;
    node.requestFocus();
    node._attach({ focus: () => focused++, blur: () => {} });
    expect(focused).toBe(1);
    node._setHasFocus(true);
    expect(node.hasFocus).toBe(true);
  });
});
