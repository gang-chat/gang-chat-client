import { expect, test } from 'vitest';

import {
  ComposerAttachmentStatus,
  ComposerAttachmentView,
  composerAttachmentGlyph,
} from '../../src/app/composer_attachment_display';

test('size label is null when the picker reports no size', () => {
  const view = new ComposerAttachmentView({
    id: 'a',
    filename: 'a.txt',
    status: ComposerAttachmentStatus.uploaded,
  });
  expect(view.sizeLabel).toBeNull();
});

test('size label formats a known size', () => {
  const view = new ComposerAttachmentView({
    id: 'a',
    filename: 'a.txt',
    status: ComposerAttachmentStatus.uploaded,
    sizeBytes: 2048,
  });
  expect(view.sizeLabel).toBe('2.0 KB');
});

test('glyph follows the mime type family', () => {
  expect(composerAttachmentGlyph({ mimeType: 'image/png', filename: 'p.png' })).toBe('image_outlined');
  expect(composerAttachmentGlyph({ mimeType: 'video/mp4', filename: 'v.mp4' })).toBe('movie_outlined');
  expect(composerAttachmentGlyph({ mimeType: 'audio/mpeg', filename: 's.mp3' })).toBe('audiotrack_outlined');
  expect(composerAttachmentGlyph({ mimeType: 'application/pdf', filename: 'd.pdf' })).toBe(
    'picture_as_pdf_outlined',
  );
  expect(composerAttachmentGlyph({ mimeType: 'text/plain', filename: 'n.txt' })).toBe('description_outlined');
});

test('glyph falls back to the extension when no mime is reported', () => {
  expect(composerAttachmentGlyph({ mimeType: null, filename: 'bundle.zip' })).toBe('folder_zip_outlined');
  expect(composerAttachmentGlyph({ mimeType: '', filename: 'archive.tar.gz' })).toBe('folder_zip_outlined');
  expect(composerAttachmentGlyph({ mimeType: null, filename: 'mystery' })).toBe('insert_drive_file_outlined');
});
