import type { Message, MessageAttachment, UploadedAsset } from '../protocol/models';
import type { FileTransferState } from './file_transfer_state';

export enum FileAttachmentTrailingKind {
  sending = 'sending',
  failed = 'failed',
  activeTransfer = 'activeTransfer',
  placeholder = 'placeholder',
  download = 'download',
}

export class FileAttachmentTransferSlot {
  constructor(opts: { downloadKey: string; transfer: FileTransferState | null; usesUploadTransfer: boolean }) {
    this.downloadKey = opts.downloadKey;
    this.transfer = opts.transfer;
    this.usesUploadTransfer = opts.usesUploadTransfer;
  }

  readonly downloadKey: string;
  readonly transfer: FileTransferState | null;
  readonly usesUploadTransfer: boolean;
}

export class FileAttachmentInteractionState {
  constructor(opts: { canDownload: boolean; tooltip: string }) {
    this.canDownload = opts.canDownload;
    this.tooltip = opts.tooltip;
  }

  readonly canDownload: boolean;
  readonly tooltip: string;
}

export class FileTransferProgressState {
  constructor(opts: { value: number | null; label: string; failed: boolean }) {
    this.value = opts.value;
    this.label = opts.label;
    this.failed = opts.failed;
  }

  readonly value: number | null;
  readonly label: string;
  readonly failed: boolean;
}

export class FileAttachmentTrailingState {
  constructor(opts: {
    kind: FileAttachmentTrailingKind;
    showDismiss?: boolean;
    pauseResumeTooltip?: string | null;
    pauseResumeIsResume?: boolean;
    cancelTooltip?: string | null;
  }) {
    this.kind = opts.kind;
    this.showDismiss = opts.showDismiss ?? false;
    this.pauseResumeTooltip = opts.pauseResumeTooltip ?? null;
    this.pauseResumeIsResume = opts.pauseResumeIsResume ?? false;
    this.cancelTooltip = opts.cancelTooltip ?? null;
  }

  readonly kind: FileAttachmentTrailingKind;
  readonly showDismiss: boolean;
  readonly pauseResumeTooltip: string | null;
  readonly pauseResumeIsResume: boolean;
  readonly cancelTooltip: string | null;
}

export class FileImagePreviewSize {
  constructor(opts: { width: number; height: number }) {
    this.width = opts.width;
    this.height = opts.height;
  }

  readonly width: number;
  readonly height: number;
}

export function basename(value: string): string {
  const normalized = value.replaceAll('\\', '/').trim();
  if (normalized.length === 0) return 'file';
  const slash = normalized.lastIndexOf('/');
  const name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
  const query = name.indexOf('?');
  const fragment = name.indexOf('#');
  let end = name.length;
  if (query >= 0 && query < end) end = query;
  if (fragment >= 0 && fragment < end) end = fragment;
  const clean = name.substring(0, end).trim();
  return clean.length === 0 ? 'file' : clean;
}

export function normalizedFilePaths(paths: Iterable<string>): string[] {
  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const path of paths) {
    const trimmed = path.trim();
    if (trimmed.length === 0 || seen.has(trimmed)) continue;
    seen.add(trimmed);
    normalized.push(trimmed);
  }
  return normalized;
}

export function clipboardFilesReadFailureMessage(error: unknown): string {
  return '无法读取剪贴板中的文件';
}

export function clipboardImageUploadFilename(opts: { timestamp: Date; sequence: number; mimeType?: string }): string {
  const { timestamp: local, sequence, mimeType = 'image/png' } = opts;
  const serial = (sequence < 1 ? 1 : sequence).toString().padStart(3, '0');
  return (
    'clipboard-image-' +
    `${_fixedDigits(local.getFullYear(), 4)}` +
    `${_fixedDigits(local.getMonth() + 1, 2)}` +
    `${_fixedDigits(local.getDate(), 2)}-` +
    `${_fixedDigits(local.getHours(), 2)}` +
    `${_fixedDigits(local.getMinutes(), 2)}` +
    `${_fixedDigits(local.getSeconds(), 2)}-` +
    `${_fixedDigits(local.getMilliseconds(), 3)}-` +
    `${serial}.${_imageExtensionForMimeType(mimeType)}`
  );
}

export function filePickerOpenFailureMessage(error: unknown): string {
  return '无法打开文件选择器';
}

export function fileReadFailureMessage(error: unknown): string {
  return '无法读取文件';
}

export function fileEmptyMessage(): string {
  return '文件为空';
}

/** Port of `Uri.tryParse`: the URL itself, or null when it cannot be parsed. */
export function fileDownloadUri(url: string): string | null {
  // A `:` before the first `/`, `?` or `#` ends the scheme, which must be
  // non-empty and well-formed (Dart rejects `://bad`).
  const schemeEnd = url.search(/[:/?#]/);
  const hasScheme = schemeEnd >= 0 && url[schemeEnd] === ':';
  if (hasScheme && !/^[A-Za-z][A-Za-z0-9+.-]*$/.test(url.substring(0, schemeEnd))) return null;
  try {
    if (hasScheme) new URL(url);
    else new URL(url, 'http://localhost/');
    return url;
  } catch {
    return null;
  }
}

export function fileDownloadUnavailableMessage(): string {
  return '无法下载文件';
}

export function fileDownloadedNotice(): string {
  return '文件已下载';
}

export function extensionOf(filename: string): string {
  const name = basename(filename).toLowerCase();
  const dot = name.lastIndexOf('.');
  if (dot < 0 || dot === name.length - 1) return '';
  return name.substring(dot + 1);
}

export function mimeTypeFromFilename(filename: string): string {
  switch (extensionOf(filename)) {
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'png':
      return 'image/png';
    case 'webp':
      return 'image/webp';
    case 'gif':
      return 'image/gif';
    case 'pdf':
      return 'application/pdf';
    case 'txt':
      return 'text/plain';
    case 'json':
      return 'application/json';
    case 'zip':
      return 'application/zip';
    case 'm4a':
      return 'audio/mp4';
    case 'mp3':
      return 'audio/mpeg';
    case 'wav':
      return 'audio/wav';
    case 'mp4':
      return 'video/mp4';
    default:
      return 'application/octet-stream';
  }
}

function _fixedDigits(value: number, width: number): string {
  return value.toString().padStart(width, '0');
}

function _imageExtensionForMimeType(mimeType: string): string {
  switch (mimeType.toLowerCase().split(';')[0]!.trim()) {
    case 'image/jpeg':
      return 'jpg';
    case 'image/webp':
      return 'webp';
    case 'image/gif':
      return 'gif';
    case 'image/bmp':
      return 'bmp';
    default:
      return 'png';
  }
}

/**
 * The file extension (without a leading dot) appropriate for an image of the
 * given [mimeType], e.g. `image/jpeg` -> `jpg`. Defaults to `png`.
 */
export function imageExtensionForMimeType(mimeType: string): string {
  return _imageExtensionForMimeType(mimeType);
}

export function fileAttachmentTitle(attachment: MessageAttachment): string {
  const explicitName = attachment.name?.trim();
  if (explicitName != null && explicitName.length > 0) return explicitName;
  const assetName = attachment.asset?.filename?.trim();
  if (assetName != null && assetName.length > 0) return assetName;
  return filenameFromAssetUrl(attachment.asset?.url) ?? 'file';
}

export function filenameFromAssetUrl(url: string | null | undefined): string | null {
  if (url == null || url.trim().length === 0) return null;
  const segments = _uriPathSegments(url);
  const raw = segments != null && segments.length > 0 ? segments[segments.length - 1]! : url;
  const decoded = _decodeComponent(raw);
  const name = basename(decoded);
  return name.trim().length === 0 ? null : name;
}

/**
 * Dart `Uri.tryParse(url)?.pathSegments`: the decoded segments of the path
 * (without the scheme, authority, query or fragment).
 */
function _uriPathSegments(url: string): string[] | null {
  let rest = url;
  const authority = /^[A-Za-z][A-Za-z0-9+.-]*:(\/\/[^/?#]*)?/.exec(rest);
  if (authority != null) rest = rest.substring(authority[0].length);
  const end = rest.search(/[?#]/);
  const path = end >= 0 ? rest.substring(0, end) : rest;
  if (path.length === 0) return [];
  const withoutLeadingSlash = path.startsWith('/') ? path.substring(1) : path;
  return withoutLeadingSlash.split('/').map(_decodeComponent);
}

function _decodeComponent(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    // PORT: Dart's `Uri.decodeComponent` throws on malformed escapes; a
    // file title falls back to the raw text instead.
    return value;
  }
}

export function fileAttachmentMeta(asset: UploadedAsset | null | undefined): string {
  if (asset == null) return '';
  const parts: string[] = [];
  const mimeType = asset.mimeType.trim();
  if (mimeType.length > 0) parts.push(mimeType);
  const sizeBytes = asset.sizeBytes;
  if (sizeBytes != null) parts.push(formatFileSize(sizeBytes));
  return parts.join(' - ');
}

export function isImageMimeType(mimeType: string | null | undefined): boolean {
  return (mimeType ?? '').toLowerCase().startsWith('image/');
}

export function isAudioMimeType(mimeType: string | null | undefined): boolean {
  return (mimeType ?? '').toLowerCase().startsWith('audio/');
}

export function canPauseFileTransfer(transfer: FileTransferState | null | undefined): boolean {
  return transfer != null && transfer.active && !transfer.paused;
}

export function canResumeFileTransfer(transfer: FileTransferState | null | undefined): boolean {
  return transfer != null && transfer.paused;
}

export function canCancelActiveFileTransfer(transfer: FileTransferState | null | undefined): boolean {
  return transfer != null && transfer.active;
}

export function fileAttachmentPreviewPath(asset: UploadedAsset | null | undefined): string | null {
  if (asset == null || !isImageMimeType(asset.mimeType)) return null;
  return asset.thumbnailUrl ?? asset.url;
}

export function fileAttachmentPreviewSize(
  asset: UploadedAsset | null | undefined,
  opts: { maxWidth?: number; maxHeight?: number; fallbackWidth?: number; fallbackHeight?: number } = {},
): FileImagePreviewSize {
  const { maxWidth = 320, maxHeight = 240, fallbackWidth = 220, fallbackHeight = 150 } = opts;
  const fallback = new FileImagePreviewSize({ width: fallbackWidth, height: fallbackHeight });
  const sourceWidth = asset?.width;
  const sourceHeight = asset?.height;
  if (
    sourceWidth == null ||
    sourceHeight == null ||
    sourceWidth <= 0 ||
    sourceHeight <= 0 ||
    maxWidth <= 0 ||
    maxHeight <= 0
  ) {
    return fallback;
  }

  const scale = Math.min(maxWidth / sourceWidth, maxHeight / sourceHeight);
  const safeScale = Number.isFinite(scale) && scale > 0 ? Math.min(1.0, scale) : 1.0;
  return new FileImagePreviewSize({ width: sourceWidth * safeScale, height: sourceHeight * safeScale });
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const digits = value < 10 ? 1 : 0;
  return `${value.toFixed(digits)} ${units[unitIndex]}`;
}

export function formatFileSpeed(bytesPerSecond: number): string {
  if (bytesPerSecond <= 0) return '0 B/s';
  return `${formatFileSize(Math.round(bytesPerSecond))}/s`;
}

export function formatPercent(value: number): string {
  return `${Math.round(Math.min(Math.max(value, 0.0), 1.0) * 100)}%`;
}

export function fileTransferLabel(transfer: FileTransferState): string {
  if (transfer.failed) return '传输失败';
  if (transfer.sendingMessage) return '发送中';

  const status = transfer.paused ? '已暂停' : transfer.isDownload ? '下载中' : '上传中';
  const progress = transfer.hasKnownTotal ? formatPercent(transfer.progress) : formatFileSize(transfer.sentBytes);
  const speed = transfer.paused || transfer.bytesPerSecond <= 0 ? '' : ` · ${formatFileSpeed(transfer.bytesPerSecond)}`;
  return `${status} ${progress}${speed}`;
}

export function fileTransferProgressState(transfer: FileTransferState): FileTransferProgressState {
  return new FileTransferProgressState({
    value: transfer.sendingMessage ? 1 : transfer.hasKnownTotal ? transfer.progress : null,
    label: transfer.error ?? fileTransferLabel(transfer),
    failed: transfer.failed,
  });
}

export function fileDownloadKey(message: Message, attachment: MessageAttachment, index: number): string {
  const asset = attachment.asset;
  const assetKey = asset?.id ?? asset?.url ?? attachment.name ?? fileAttachmentTitle(attachment);
  return `${message.clientMessageId}:${index}:${assetKey}`;
}

export function fileAttachmentTransferSlot(opts: {
  message: Message;
  attachment: MessageAttachment;
  index: number;
  uploadTransfer: FileTransferState | null;
  downloads: Map<string, FileTransferState>;
}): FileAttachmentTransferSlot {
  const { message, attachment, index, uploadTransfer, downloads } = opts;
  const downloadKey = fileDownloadKey(message, attachment, index);
  const activeUploadTransfer = index === 0 ? uploadTransfer : null;
  const activeDownloadTransfer = downloads.get(downloadKey) ?? null;
  const transfer = activeUploadTransfer ?? activeDownloadTransfer;
  return new FileAttachmentTransferSlot({
    downloadKey,
    transfer,
    usesUploadTransfer: activeUploadTransfer != null,
  });
}

export function fileAttachmentInteractionState(opts: {
  title: string;
  url: string | null;
  transfer: FileTransferState | null;
}): FileAttachmentInteractionState {
  const canDownload = opts.url != null && opts.transfer == null;
  return new FileAttachmentInteractionState({
    canDownload,
    tooltip: canDownload ? '下载文件' : opts.title,
  });
}

export function fileAttachmentTrailingState(opts: {
  transfer: FileTransferState | null;
  canDownload: boolean;
}): FileAttachmentTrailingState {
  const { transfer, canDownload } = opts;
  if (transfer != null) {
    if (transfer.sendingMessage) {
      return new FileAttachmentTrailingState({ kind: FileAttachmentTrailingKind.sending });
    }
    if (transfer.failed) {
      return new FileAttachmentTrailingState({
        kind: FileAttachmentTrailingKind.failed,
        showDismiss: transfer.isDownload,
      });
    }

    const action = transfer.isDownload ? '下载' : '上传';
    return new FileAttachmentTrailingState({
      kind: FileAttachmentTrailingKind.activeTransfer,
      pauseResumeTooltip: transfer.paused ? `继续${action}` : `暂停${action}`,
      pauseResumeIsResume: transfer.paused,
      cancelTooltip: `取消${action}`,
    });
  }

  return new FileAttachmentTrailingState({
    kind: canDownload ? FileAttachmentTrailingKind.download : FileAttachmentTrailingKind.placeholder,
  });
}
