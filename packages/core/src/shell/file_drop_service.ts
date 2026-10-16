import type { EventStream } from '../lib/event_stream';
import { StreamController } from '../lib/event_stream';
import type { SelectedFile } from './file_selection_service';

export class FileDropEvent {
  constructor(opts: { paths?: string[]; files?: SelectedFile[]; x: number; y: number }) {
    this.paths = opts.paths ?? [];
    this.files = opts.files ?? [];
    this.x = opts.x;
    this.y = opts.y;
  }

  /** Desktop drops carry paths. */
  readonly paths: string[];

  /** Browser drops carry contents instead, since browsers expose no paths. */
  readonly files: SelectedFile[];

  /** Drop position in window coordinates, logical pixels. */
  readonly x: number;
  readonly y: number;
}

const _events = new StreamController<FileDropEvent>();

/**
 * App-wide stream of files dropped onto the window.
 *
 * PORT: the Flutter client received drops from the native runner
 * (`gang_chat/file_drop` channel on Windows/macOS) or from the browser bridge
 * on the web. Core has no DOM, so the drop source is pushed in from outside:
 * `packages/ui/src/shell/file_drop_target.web.tsx` listens to the DOM
 * drag/drop events (resolving desktop paths through the preload's
 * `webUtils.getPathForFile`) and calls [dispatch]. All instances share one
 * stream, like the Dart static controller.
 */
export class FileDropService {
  get drops(): EventStream<FileDropEvent> {
    return _events.stream;
  }

  /**
   * Publishes a drop to every [drops] listener. Drops without paths or files
   * are ignored, like the native channel handler did.
   */
  dispatch(event: FileDropEvent): void {
    FileDropService.dispatch(event);
  }

  static dispatch(event: FileDropEvent): void {
    if (event.paths.length === 0 && event.files.length === 0) return;
    if (!Number.isFinite(event.x) || !Number.isFinite(event.y)) return;
    _events.add(event);
  }
}
