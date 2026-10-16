export enum ChatComposerPanel {
  stickers = 'stickers',
  voice = 'voice',
  file = 'file',
  tools = 'tools',
}

export class ChatComposerPanelPatch {
  constructor(opts: { openPanel: ChatComposerPanel | null; shouldLoadStickers: boolean }) {
    this.openPanel = opts.openPanel;
    this.shouldLoadStickers = opts.shouldLoadStickers;
  }

  readonly openPanel: ChatComposerPanel | null;
  readonly shouldLoadStickers: boolean;
}

export class ChatComposerInputHeightPatch {
  constructor(opts: { inputHeight: number }) {
    this.inputHeight = opts.inputHeight;
  }

  readonly inputHeight: number;
}

export function chatComposerPanelToggled(opts: {
  currentPanel: ChatComposerPanel | null;
  panel: ChatComposerPanel;
}): ChatComposerPanelPatch {
  const { currentPanel, panel } = opts;
  const opening = currentPanel !== panel;
  return new ChatComposerPanelPatch({
    openPanel: opening ? panel : null,
    shouldLoadStickers: opening && panel === ChatComposerPanel.stickers,
  });
}

export function chatComposerPanelClosed(opts: {
  currentPanel: ChatComposerPanel | null;
}): ChatComposerPanelPatch | null {
  if (opts.currentPanel == null) return null;
  return new ChatComposerPanelPatch({
    openPanel: null,
    shouldLoadStickers: false,
  });
}

export function chatComposerPanelResetForRoomChange(): ChatComposerPanelPatch {
  return new ChatComposerPanelPatch({
    openPanel: null,
    shouldLoadStickers: false,
  });
}

export function chatComposerInputHeightMeasured(opts: {
  currentHeight: number;
  measuredHeight: number;
  threshold?: number;
}): ChatComposerInputHeightPatch | null {
  const { currentHeight, measuredHeight, threshold = 0.5 } = opts;
  if (Math.abs(currentHeight - measuredHeight) < threshold) return null;
  return new ChatComposerInputHeightPatch({ inputHeight: measuredHeight });
}
