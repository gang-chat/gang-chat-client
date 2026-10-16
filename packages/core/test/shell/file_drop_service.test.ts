import { expect, test } from 'vitest';
import { FileDropEvent, FileDropService } from '../../src/shell/file_drop_service';
import { SelectedFile } from '../../src/shell/file_selection_service';

test('dispatched drops reach every FileDropService listener', () => {
  const received: FileDropEvent[] = [];
  const subscription = new FileDropService().drops.listen((event) => received.push(event));

  new FileDropService().dispatch(new FileDropEvent({ paths: ['/tmp/a.png'], x: 10, y: 20 }));
  FileDropService.dispatch(
    new FileDropEvent({
      files: [SelectedFile.deferred({ name: 'b.txt', mimeType: '', length: 3, readAsBytes: async () => new Uint8Array(3) })],
      x: 1.5,
      y: 2.5,
    }),
  );
  // Empty or malformed drops are ignored like the native channel handler did.
  FileDropService.dispatch(new FileDropEvent({ x: 0, y: 0 }));
  FileDropService.dispatch(new FileDropEvent({ paths: ['/x'], x: Number.NaN, y: 0 }));
  subscription.cancel();
  FileDropService.dispatch(new FileDropEvent({ paths: ['/late'], x: 0, y: 0 }));

  expect(received.map((event) => [event.paths, event.files.map((file) => file.name), event.x, event.y])).toEqual([
    [['/tmp/a.png'], [], 10, 20],
    [[], ['b.txt'], 1.5, 2.5],
  ]);
  expect(received[1]!.files[0]!.mimeType).toBeNull();
});
