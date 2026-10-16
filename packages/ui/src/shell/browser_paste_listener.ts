import { ClipboardService } from '@gang-chat/core/shell/clipboard_service';
import { SelectedFile } from '@gang-chat/core/shell/file_selection_service';

let _installed = false;

/**
 * Port of `setBrowserPasteHandler` (lib/src/shell/browser/browser_bridge_web.dart):
 * routes the page's paste events to the handler `ClipboardService
 * .setWebPasteHandler` installed (the home shell's `_handleWebPaste`), which
 * sees the pasted files and plain text and returns true to consume the paste
 * (the browser then inserts nothing). It must decide synchronously: the
 * clipboard data is gone after the event.
 *
 * Web build only. Pasted files come from the event itself, so no clipboard
 * permission prompt appears; the Electron renderer reads files and images
 * through the main process instead.
 */
export function installBrowserPasteListener(): void {
  if (_installed || typeof window === 'undefined') return;
  _installed = true;
  window.addEventListener(
    'paste',
    (event: ClipboardEvent) => {
      const data = event.clipboardData;
      if (!ClipboardService.hasWebPasteHandler || data == null) return;
      const files: SelectedFile[] = [];
      for (let index = 0; index < data.files.length; index += 1) {
        const file = data.files.item(index);
        if (file == null) continue;
        files.push(
          SelectedFile.deferred({
            name: file.name,
            mimeType: file.type,
            length: file.size,
            readAsBytes: async () => new Uint8Array(await file.arrayBuffer()),
          }),
        );
      }
      const text = data.getData('text/plain');
      if (ClipboardService.dispatchWebPaste(files, text.length === 0 ? null : text)) {
        event.preventDefault();
        event.stopPropagation();
      }
    },
    // Capture, so the decision is made before the page's text field sees it.
    true,
  );
}
