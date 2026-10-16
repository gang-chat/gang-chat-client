import { useEffect, type ReactNode } from 'react';

import { FileDropEvent, FileDropService } from '@gang-chat/core/shell/file_drop_service';
import { SelectedFile } from '@gang-chat/core/shell/file_selection_service';

/** The preload bridge member this file needs (`webUtils.getPathForFile`). */
interface _DropPathBridge {
  getPathForFile?(file: File): string;
}

function _dropPathBridge(): _DropPathBridge | null {
  if (typeof window === 'undefined') return null;
  return ((window as unknown as { gangDesktop?: _DropPathBridge }).gangDesktop ?? null) as _DropPathBridge | null;
}

function _carriesFiles(event: DragEvent): boolean {
  const types = event.dataTransfer?.types;
  if (types == null) return false;
  for (let index = 0; index < types.length; index += 1) {
    if (types[index] === 'Files') return true;
  }
  return false;
}

/**
 * Turns a DOM drop into the [FileDropEvent] the Flutter runners produced.
 *
 * Desktop drops carry absolute paths, like the Windows/macOS native
 * `gang_chat/file_drop` channel (`dropFiles {paths, x, y}`); a drop with any
 * file the host cannot map to a path (or a plain browser) carries contents
 * instead, like the Flutter web bridge. Coordinates are window logical pixels.
 */
export function _fileDropEventFromDataTransfer(dataTransfer: DataTransfer, x: number, y: number): FileDropEvent | null {
  const list = dataTransfer.files;
  const files: File[] = [];
  for (let index = 0; index < list.length; index += 1) {
    const file = list.item(index);
    if (file != null) files.push(file);
  }
  if (files.length === 0) return null;

  const bridge = _dropPathBridge();
  if (bridge?.getPathForFile != null) {
    const paths: string[] = [];
    for (const file of files) {
      let path = '';
      try {
        path = bridge.getPathForFile(file);
      } catch {
        path = '';
      }
      if (path.length === 0) break;
      paths.push(path);
    }
    if (paths.length === files.length) {
      return new FileDropEvent({ paths, x, y });
    }
  }

  return new FileDropEvent({
    files: files.map((file) =>
      SelectedFile.deferred({
        name: file.name,
        mimeType: file.type,
        length: file.size,
        readAsBytes: async () => new Uint8Array(await file.arrayBuffer()),
      }),
    ),
    x,
    y,
  });
}

export interface FileDropTargetProps {
  children?: ReactNode;
  /** Defaults to the app-wide [FileDropService]. */
  service?: FileDropService;
}

/**
 * Window-wide file drop source for [FileDropService].
 *
 * PORT: the Flutter client received OS file drops from a transparent native
 * view over the whole window (macOS `FileDropView`, Windows `DragAcceptFiles`
 * on the Flutter child HWND) and published every drop with its window
 * position; listeners (the chat composer) hit-test that point themselves.
 * Here the same happens with DOM drag events on `window`:
 *
 *  - `dragenter` / `dragover` carrying files are accepted (`preventDefault`)
 *    so Electron does not navigate the page to the dropped file, which is
 *    also what the native overlay effectively did.
 *  - `drop` resolves paths through the preload's `getPathForFile`
 *    (`webUtils.getPathForFile`) and dispatches one [FileDropEvent].
 *
 * The listeners run in the bubble phase on `window`, after every element: a
 * descendant that handles a drop itself (`preventDefault` /
 * `stopPropagation`) keeps it, and the drop is not published again.
 */
export function FileDropTarget({ children, service }: FileDropTargetProps) {
  useEffect(() => {
    const dispatch = (event: FileDropEvent) => {
      if (service != null) {
        service.dispatch(event);
      } else {
        FileDropService.dispatch(event);
      }
    };

    const onDragEnterOrOver = (event: DragEvent) => {
      if (!_carriesFiles(event)) return;
      if (event.defaultPrevented) return;
      event.preventDefault();
      if (event.dataTransfer != null) event.dataTransfer.dropEffect = 'copy';
    };

    const onDrop = (event: DragEvent) => {
      if (!_carriesFiles(event)) return;
      if (event.defaultPrevented) return;
      // Never let the renderer open the file in place of the app.
      event.preventDefault();
      const dataTransfer = event.dataTransfer;
      if (dataTransfer == null) return;
      const drop = _fileDropEventFromDataTransfer(dataTransfer, event.clientX, event.clientY);
      if (drop == null) return;
      dispatch(drop);
    };

    window.addEventListener('dragenter', onDragEnterOrOver);
    window.addEventListener('dragover', onDragEnterOrOver);
    window.addEventListener('drop', onDrop);
    return () => {
      window.removeEventListener('dragenter', onDragEnterOrOver);
      window.removeEventListener('dragover', onDragEnterOrOver);
      window.removeEventListener('drop', onDrop);
    };
  }, [service]);

  return <>{children}</>;
}
