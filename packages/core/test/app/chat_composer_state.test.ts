import { expect, test } from 'vitest';

import {
  ChatComposerPanel,
  chatComposerInputHeightMeasured,
  chatComposerPanelClosed,
  chatComposerPanelResetForRoomChange,
  chatComposerPanelToggled,
} from '../../src/app/chat_composer_state';

test('chat composer panel toggle opens and closes the same panel', () => {
  const opened = chatComposerPanelToggled({ currentPanel: null, panel: ChatComposerPanel.tools });

  expect(opened.openPanel).toBe(ChatComposerPanel.tools);
  expect(opened.shouldLoadStickers).toBe(false);

  const closed = chatComposerPanelToggled({
    currentPanel: ChatComposerPanel.tools,
    panel: ChatComposerPanel.tools,
  });

  expect(closed.openPanel).toBeNull();
  expect(closed.shouldLoadStickers).toBe(false);
});

test('chat composer sticker panel open requests sticker loading', () => {
  const opened = chatComposerPanelToggled({ currentPanel: null, panel: ChatComposerPanel.stickers });

  expect(opened.openPanel).toBe(ChatComposerPanel.stickers);
  expect(opened.shouldLoadStickers).toBe(true);

  const switched = chatComposerPanelToggled({
    currentPanel: ChatComposerPanel.tools,
    panel: ChatComposerPanel.stickers,
  });

  expect(switched.openPanel).toBe(ChatComposerPanel.stickers);
  expect(switched.shouldLoadStickers).toBe(true);
});

test('chat composer close and room reset clear open panel', () => {
  expect(chatComposerPanelClosed({ currentPanel: null })).toBeNull();

  const closed = chatComposerPanelClosed({ currentPanel: ChatComposerPanel.stickers });

  expect(closed?.openPanel).toBeNull();
  expect(closed?.shouldLoadStickers).toBe(false);

  const reset = chatComposerPanelResetForRoomChange();

  expect(reset.openPanel).toBeNull();
  expect(reset.shouldLoadStickers).toBe(false);
});

test('chat composer input height patch ignores sub-threshold changes', () => {
  expect(chatComposerInputHeightMeasured({ currentHeight: 76, measuredHeight: 76.25 })).toBeNull();

  const patch = chatComposerInputHeightMeasured({ currentHeight: 76, measuredHeight: 84 });

  expect(patch?.inputHeight).toBe(84);
});
