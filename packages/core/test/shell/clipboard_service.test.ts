import { beforeEach, describe, expect, test } from 'vitest';
import { ClipboardService } from '../../src/shell/clipboard_service';
import { SelectedFile } from '../../src/shell/file_selection_service';
import { installFakePlatform } from '../support/fake_platform';
import type { FakePlatform } from '../support/fake_platform';

describe('ClipboardService', () => {
  let fake: FakePlatform;

  beforeEach(() => {
    fake = installFakePlatform({ os: 'windows' });
  });

  test('SelectedFile.fromBytes keeps clipboard image bytes uploadable', async () => {
    const source = Uint8Array.from([1, 2, 3]);
    const file = SelectedFile.fromBytes({ name: 'clipboard-image.png', mimeType: 'image/png', bytes: source });

    source[0] = 9;
    const firstRead = await file.readAsBytes();
    firstRead[1] = 8;

    expect(file.name).toBe('clipboard-image.png');
    expect(file.mimeType).toBe('image/png');
    expect(await file.length()).toBe(3);
    expect([...(await file.readAsBytes())]).toEqual([1, 2, 3]);
  });

  test('readImageFile parses the native image payload', async () => {
    fake.clipboard.image = { filename: 'screenshot.png', mimeType: 'image/png', bytes: Uint8Array.from([137, 80, 78, 71]) };

    const image = await new ClipboardService().readImageFile();

    expect(image).not.toBeNull();
    expect(image!.filename).toBe('screenshot.png');
    expect(image!.mimeType).toBe('image/png');
    expect([...image!.bytes]).toEqual([137, 80, 78, 71]);
  });

  test('readImageFile falls back to default name/type and ignores empty images', async () => {
    fake.clipboard.image = { filename: '', mimeType: '', bytes: Uint8Array.from([1]) };
    const image = await new ClipboardService().readImageFile();
    expect(image?.filename).toBe('clipboard-image.png');
    expect(image?.mimeType).toBe('image/png');

    fake.clipboard.image = { filename: 'x.png', mimeType: 'image/png', bytes: new Uint8Array(0) };
    expect(await new ClipboardService().readImageFile()).toBeNull();
  });

  test('file paths are trimmed and only supported on Windows/macOS', async () => {
    const service = new ClipboardService();
    expect(await service.writeFilePaths([' C:\\a.txt ', '  '])).toBe(true);
    expect(fake.clipboard.calls.at(-1)).toEqual({ method: 'writeFilePaths', args: [['C:\\a.txt']] });
    expect(await service.writeFilePaths(['   '])).toBe(false);

    fake = installFakePlatform({ os: 'linux' });
    fake.clipboard.filePaths = ['/tmp/a'];
    expect(await service.readFilePaths()).toEqual([]);
    expect(await service.writeFilePaths(['/tmp/a'])).toBe(false);
  });

  test('readText treats an empty clipboard as null', async () => {
    const service = new ClipboardService();
    fake.clipboard.text = '';
    expect(await service.readText()).toBeNull();
    await service.writeText('hello');
    expect(await service.readText()).toBe('hello');
  });

  test('writeImage labels the bytes with their sniffed type', async () => {
    const jpeg = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0]);
    expect(await new ClipboardService().writeImage(jpeg)).toBe(true);
    expect(fake.clipboard.calls.at(-1)?.args[1]).toBe('image/jpeg');
    expect(await new ClipboardService().writeImage(new Uint8Array(0))).toBe(false);
  });

  test('web paste handler receives dispatched pastes', () => {
    const service = new ClipboardService();
    const seen: Array<string | null> = [];
    expect(ClipboardService.dispatchWebPaste([], 'ignored')).toBe(false);
    service.setWebPasteHandler((files, text) => {
      seen.push(text);
      return files.length > 0;
    });
    expect(ClipboardService.hasWebPasteHandler).toBe(true);
    const file = SelectedFile.fromBytes({ name: 'a.png', mimeType: 'image/png', bytes: Uint8Array.from([1]) });
    expect(ClipboardService.dispatchWebPaste([file], null)).toBe(true);
    expect(ClipboardService.dispatchWebPaste([], 'text')).toBe(false);
    expect(seen).toEqual([null, 'text']);
    service.setWebPasteHandler(null);
    expect(ClipboardService.hasWebPasteHandler).toBe(false);
  });
});
