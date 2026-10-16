import * as file_display from './file_display';

/**
 * Lifecycle of a file staged in the composer. Files now upload as soon as they
 * are picked, so the chip reflects in-flight progress, success, or failure
 * before the message is ever sent.
 */
export enum ComposerAttachmentStatus {
  uploading = 'uploading',
  uploaded = 'uploaded',
  failed = 'failed',
}

/**
 * A file staged in the composer, waiting to ride the next outgoing message as
 * an attachment. This is the view model handed to the composer's chip strip;
 * the raw picked file (and its in-flight upload) is held by the shell and
 * matched back by [id].
 */
export class ComposerAttachmentView {
  constructor(opts: {
    id: string;
    filename: string;
    status: ComposerAttachmentStatus;
    sizeBytes?: number | null;
    mimeType?: string | null;
    progress?: number | null;
    errorMessage?: string | null;
  }) {
    this.id = opts.id;
    this.filename = opts.filename;
    this.status = opts.status;
    this.sizeBytes = opts.sizeBytes ?? null;
    this.mimeType = opts.mimeType ?? null;
    this.progress = opts.progress ?? null;
    this.errorMessage = opts.errorMessage ?? null;
  }

  readonly id: string;
  readonly filename: string;
  readonly status: ComposerAttachmentStatus;
  readonly sizeBytes: number | null;
  readonly mimeType: string | null;
  readonly errorMessage: string | null;

  /**
   * Upload progress in the range 0..1, or null when the total size is not yet
   * known (the chip then shows an indeterminate spinner).
   */
  readonly progress: number | null;

  get isUploading(): boolean {
    return this.status === ComposerAttachmentStatus.uploading;
  }

  get isUploaded(): boolean {
    return this.status === ComposerAttachmentStatus.uploaded;
  }

  get hasFailed(): boolean {
    return this.status === ComposerAttachmentStatus.failed;
  }

  /** Human-readable size, or null when the picker could not report one. */
  get sizeLabel(): string | null {
    return this.sizeBytes == null ? null : file_display.formatFileSize(this.sizeBytes);
  }
}

/**
 * The Material icon names [composerAttachmentGlyph] can return, keyed like
 * Flutter's `Icons.*` (and `Icons` in `@gang-chat/ui/ui/icons`).
 */
export type ComposerAttachmentGlyph =
  | 'image_outlined'
  | 'movie_outlined'
  | 'audiotrack_outlined'
  | 'picture_as_pdf_outlined'
  | 'description_outlined'
  | 'folder_zip_outlined'
  | 'insert_drive_file_outlined';

/**
 * Pick a representative glyph for a staged file from its mime type, with a
 * small extension fallback so common types still read sensibly when the picker
 * reports no mime.
 *
 * PORT: Dart returned `IconData`. `@gang-chat/core` cannot depend on the UI
 * icon table, so this returns the `Icons.*` key instead; the UI renders it
 * with `Icons[composerAttachmentGlyph(...)]`.
 */
export function composerAttachmentGlyph(opts: { mimeType?: string | null; filename: string }): ComposerAttachmentGlyph {
  const { filename } = opts;
  const mime = (opts.mimeType ?? '').toLowerCase();
  if (mime.startsWith('image/')) return 'image_outlined';
  if (mime.startsWith('video/')) return 'movie_outlined';
  if (mime.startsWith('audio/')) return 'audiotrack_outlined';
  if (mime === 'application/pdf') return 'picture_as_pdf_outlined';
  if (mime.startsWith('text/')) return 'description_outlined';

  const dot = filename.lastIndexOf('.');
  const ext = dot >= 0 ? filename.substring(dot + 1).toLowerCase() : '';
  const archive = new Set(['zip', 'rar', '7z', 'tar', 'gz']);
  if (archive.has(ext)) return 'folder_zip_outlined';
  return 'insert_drive_file_outlined';
}
