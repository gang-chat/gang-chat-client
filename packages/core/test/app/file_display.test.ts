import { describe, expect, test } from 'vitest';

import {
  FileAttachmentTrailingKind,
  basename,
  canCancelActiveFileTransfer,
  canPauseFileTransfer,
  canResumeFileTransfer,
  clipboardFilesReadFailureMessage,
  clipboardImageUploadFilename,
  extensionOf,
  fileAttachmentInteractionState,
  fileAttachmentMeta,
  fileAttachmentPreviewPath,
  fileAttachmentPreviewSize,
  fileAttachmentTitle,
  fileAttachmentTrailingState,
  fileAttachmentTransferSlot,
  fileDownloadKey,
  fileDownloadUnavailableMessage,
  fileDownloadUri,
  fileDownloadedNotice,
  fileEmptyMessage,
  filePickerOpenFailureMessage,
  fileReadFailureMessage,
  fileTransferLabel,
  fileTransferProgressState,
  filenameFromAssetUrl,
  formatFileSize,
  isImageMimeType,
  mimeTypeFromFilename,
  normalizedFilePaths,
} from '../../src/app/file_display';
import { FileTransferState } from '../../src/app/file_transfer_state';
import { UploadTransferController } from '../../src/protocol/api_client';
import { Message, MessageAttachment, UploadedAsset, UserSummary } from '../../src/protocol/models';

function _asset(
  opts: {
    id?: string;
    url?: string;
    thumbnailUrl?: string | null;
    filename?: string | null;
    mimeType?: string;
    sizeBytes?: number | null;
    width?: number | null;
    height?: number | null;
  } = {},
): UploadedAsset {
  return new UploadedAsset({
    id: opts.id ?? 'asset',
    url: opts.url ?? '/uploads/file.txt',
    thumbnailUrl: opts.thumbnailUrl ?? null,
    mimeType: opts.mimeType ?? 'text/plain',
    filename: opts.filename === undefined ? 'file.txt' : opts.filename,
    sizeBytes: opts.sizeBytes ?? null,
    width: opts.width ?? null,
    height: opts.height ?? null,
  });
}

function _message(): Message {
  return new Message({
    id: 'message_1',
    roomId: 'room_1',
    sender: new UserSummary({
      id: 'user_1',
      username: 'logan',
      displayName: 'Logan',
      avatarUrl: null,
      defaultAvatarKey: 'blue-3',
    }),
    clientMessageId: 'client_1',
    body: '',
    createdAt: new Date(Date.UTC(2026, 5, 4)),
  });
}

function _attachment(id: string): MessageAttachment {
  return new MessageAttachment({ type: 'file', name: `${id}.txt`, asset: _asset({ id }) });
}

function _upload(totalBytes: number): FileTransferState {
  return FileTransferState.upload({ controller: new UploadTransferController(), totalBytes });
}

describe('file_display', () => {
  test('basename removes folders query and fragment', () => {
    expect(basename('C:\\Users\\kai\\report.pdf?download=1#top')).toBe('report.pdf');
    expect(basename('/tmp/')).toBe('file');
    expect(basename('  image.png  ')).toBe('image.png');
  });

  test('normalizedFilePaths trims empty and duplicate file paths', () => {
    expect(normalizedFilePaths([' /tmp/a.png ', '', ' /tmp/b.png', '/tmp/a.png', '   '])).toEqual([
      '/tmp/a.png',
      '/tmp/b.png',
    ]);
  });

  test('file interaction notices and download url parsing stay outside UI', () => {
    expect(clipboardFilesReadFailureMessage('denied')).toBe('无法读取剪贴板中的文件');
    expect(filePickerOpenFailureMessage('blocked')).toBe('无法打开文件选择器');
    expect(fileReadFailureMessage('missing')).toBe('无法读取文件');
    expect(fileEmptyMessage()).toBe('文件为空');
    expect(new URL(fileDownloadUri('https://example.test/file.txt')!).host).toBe('example.test');
    expect(fileDownloadUri('/uploads/file.txt')).toBe('/uploads/file.txt');
    expect(fileDownloadUri('://bad')).toBeNull();
    expect(fileDownloadUnavailableMessage()).toBe('无法下载文件');
    expect(fileDownloadedNotice()).toBe('文件已下载');
  });

  test('clipboard image upload filenames include timestamp and sequence', () => {
    const timestamp = new Date(2026, 5, 11, 17, 55, 1, 23);

    expect(clipboardImageUploadFilename({ timestamp, sequence: 1 })).toBe('clipboard-image-20260611-175501-023-001.png');
    expect(clipboardImageUploadFilename({ timestamp, sequence: 2, mimeType: 'image/jpeg; charset=binary' })).toBe(
      'clipboard-image-20260611-175501-023-002.jpg',
    );
  });

  test('extension and mime type are inferred from normalized basename', () => {
    expect(extensionOf('/tmp/photo.JPEG?token=1')).toBe('jpeg');
    expect(mimeTypeFromFilename('/tmp/photo.JPEG?token=1')).toBe('image/jpeg');
    expect(mimeTypeFromFilename('archive.unknown')).toBe('application/octet-stream');
  });

  test('fileAttachmentTitle falls back from explicit name to asset fields', () => {
    expect(
      fileAttachmentTitle(
        new MessageAttachment({
          type: 'file',
          name: ' explicit.txt ',
          asset: _asset({ filename: 'asset.txt', url: '/ignored/url.txt' }),
        }),
      ),
    ).toBe('explicit.txt');
    expect(
      fileAttachmentTitle(
        new MessageAttachment({ type: 'file', asset: _asset({ filename: ' asset-name.txt ', url: '/ignored/url.txt' }) }),
      ),
    ).toBe('asset-name.txt');
    expect(
      fileAttachmentTitle(
        new MessageAttachment({ type: 'file', asset: _asset({ filename: null, url: '/uploads/final%20name.txt?download=1' }) }),
      ),
    ).toBe('final name.txt');
  });

  test('filenameFromAssetUrl reads the last decoded path segment', () => {
    expect(filenameFromAssetUrl('https://cdn.example.test/a/%E6%8A%A5%E5%91%8A.pdf?x=1')).toBe('报告.pdf');
    expect(filenameFromAssetUrl('https://cdn.example.test')).toBe('cdn.example.test');
    expect(filenameFromAssetUrl('  ')).toBeNull();
  });

  test('fileAttachmentMeta combines mime type and formatted size', () => {
    expect(fileAttachmentMeta(_asset({ mimeType: 'application/pdf', sizeBytes: 1536 }))).toBe('application/pdf - 1.5 KB');
    expect(fileAttachmentMeta(_asset({ mimeType: '', sizeBytes: 1048576 }))).toBe('1.0 MB');
    expect(fileAttachmentMeta(null)).toBe('');
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(20 * 1024)).toBe('20 KB');
  });

  test('fileAttachmentPreviewPath uses image thumbnail or asset URL only', () => {
    expect(
      fileAttachmentPreviewPath(
        _asset({ url: '/uploads/image.png', mimeType: 'image/png', thumbnailUrl: '/uploads/thumb.png' }),
      ),
    ).toBe('/uploads/thumb.png');
    expect(fileAttachmentPreviewPath(_asset({ url: '/uploads/image.png', mimeType: 'image/png' }))).toBe(
      '/uploads/image.png',
    );
    expect(fileAttachmentPreviewPath(_asset({ url: '/uploads/file.pdf', mimeType: 'application/pdf' }))).toBeNull();
  });

  test('isImageMimeType normalizes mime casing', () => {
    expect(isImageMimeType('IMAGE/PNG')).toBe(true);
    expect(isImageMimeType('text/plain')).toBe(false);
    expect(isImageMimeType(null)).toBe(false);
  });

  test('fileAttachmentPreviewSize preserves image ratio within bounds', () => {
    let size = fileAttachmentPreviewSize(_asset({ mimeType: 'image/png', width: 1600, height: 900 }));
    expect(size.width).toBe(320);
    expect(size.height).toBe(180);

    size = fileAttachmentPreviewSize(_asset({ mimeType: 'image/png', width: 900, height: 1600 }));
    expect(size.width).toBe(135);
    expect(size.height).toBe(240);

    size = fileAttachmentPreviewSize(_asset({ mimeType: 'image/png', width: 128, height: 96 }));
    expect(size.width).toBe(128);
    expect(size.height).toBe(96);

    size = fileAttachmentPreviewSize(_asset({ mimeType: 'image/png' }));
    expect(size.width).toBe(220);
    expect(size.height).toBe(150);
  });

  test('fileTransferLabel reflects state progress and speed', () => {
    const transfer = _upload(4096);
    transfer.sentBytes = 2048;
    transfer.bytesPerSecond = 2048;

    expect(fileTransferLabel(transfer)).toBe('上传中 50% · 2.0 KB/s');

    transfer.pauseTransfer();
    expect(fileTransferLabel(transfer)).toBe('已暂停 50%');

    transfer.resumeTransfer();
    transfer.markFailed('network failed');
    expect(fileTransferLabel(transfer)).toBe('传输失败');
  });

  test('fileTransferProgressState exposes progress label and error state', () => {
    const transfer = _upload(100);
    transfer.sentBytes = 25;

    let state = fileTransferProgressState(transfer);
    expect(state.value).toBe(0.25);
    expect(state.label).toBe('上传中 25%');
    expect(state.failed).toBe(false);

    transfer.markSendingMessage();
    state = fileTransferProgressState(transfer);
    expect(state.value).toBe(1);
    expect(state.label).toBe('发送中');

    const failedTransfer = _upload(100);
    failedTransfer.sentBytes = 25;
    failedTransfer.markFailed('network failed');
    state = fileTransferProgressState(failedTransfer);
    expect(state.value).toBe(0.25);
    expect(state.label).toBe('网络连接失败，请检查网络后重试');
    expect(state.failed).toBe(true);
  });

  test('fileDownloadKey is stable from client message and asset identity', () => {
    const attachment = new MessageAttachment({ type: 'file', name: 'fallback.txt', asset: _asset({ id: 'asset_1' }) });

    expect(fileDownloadKey(_message(), attachment, 2)).toBe('client_1:2:asset_1');
  });

  test('fileAttachmentTransferSlot binds upload only to first attachment', () => {
    const message = _message();
    const first = _attachment('first');
    const second = _attachment('second');
    const upload = _upload(100);
    const download = FileTransferState.download({
      controller: new UploadTransferController(),
      totalBytes: 200,
      destinationPath: '/tmp/second.txt',
    });
    const secondKey = fileDownloadKey(message, second, 1);

    const firstSlot = fileAttachmentTransferSlot({
      message,
      attachment: first,
      index: 0,
      uploadTransfer: upload,
      downloads: new Map([[secondKey, download]]),
    });
    expect(firstSlot.transfer).toBe(upload);
    expect(firstSlot.usesUploadTransfer).toBe(true);

    const secondSlot = fileAttachmentTransferSlot({
      message,
      attachment: second,
      index: 1,
      uploadTransfer: upload,
      downloads: new Map([[secondKey, download]]),
    });
    expect(secondSlot.transfer).toBe(download);
    expect(secondSlot.usesUploadTransfer).toBe(false);
    expect(secondSlot.downloadKey).toBe(secondKey);
  });

  test('fileAttachmentInteractionState allows download only without transfer', () => {
    expect(
      fileAttachmentInteractionState({ title: 'report.pdf', url: '/uploads/report.pdf', transfer: null }).canDownload,
    ).toBe(true);
    expect(fileAttachmentInteractionState({ title: 'report.pdf', url: null, transfer: null }).tooltip).toBe('report.pdf');
    expect(
      fileAttachmentInteractionState({ title: 'report.pdf', url: '/uploads/report.pdf', transfer: _upload(100) })
        .canDownload,
    ).toBe(false);
  });

  test('fileAttachmentTrailingState describes transfer actions', () => {
    const upload = _upload(100);
    let state = fileAttachmentTrailingState({ transfer: upload, canDownload: false });
    expect(state.kind).toBe(FileAttachmentTrailingKind.activeTransfer);
    expect(state.pauseResumeTooltip).toBe('暂停上传');
    expect(state.pauseResumeIsResume).toBe(false);
    expect(state.cancelTooltip).toBe('取消上传');

    upload.pauseTransfer();
    state = fileAttachmentTrailingState({ transfer: upload, canDownload: false });
    expect(state.pauseResumeTooltip).toBe('继续上传');
    expect(state.pauseResumeIsResume).toBe(true);

    upload.resumeTransfer();
    upload.markSendingMessage();
    expect(fileAttachmentTrailingState({ transfer: upload, canDownload: false }).kind).toBe(
      FileAttachmentTrailingKind.sending,
    );
  });

  test('fileAttachmentTrailingState separates failed and idle states', () => {
    const upload = _upload(100);
    upload.markFailed('upload failed');
    const download = FileTransferState.download({
      controller: new UploadTransferController(),
      totalBytes: 100,
      destinationPath: '/tmp/file.txt',
    });
    download.markFailed('download failed');

    let state = fileAttachmentTrailingState({ transfer: upload, canDownload: false });
    expect(state.kind).toBe(FileAttachmentTrailingKind.failed);
    expect(state.showDismiss).toBe(false);

    state = fileAttachmentTrailingState({ transfer: download, canDownload: false });
    expect(state.kind).toBe(FileAttachmentTrailingKind.failed);
    expect(state.showDismiss).toBe(true);

    expect(fileAttachmentTrailingState({ transfer: null, canDownload: false }).kind).toBe(
      FileAttachmentTrailingKind.placeholder,
    );
    expect(fileAttachmentTrailingState({ transfer: null, canDownload: true }).kind).toBe(FileAttachmentTrailingKind.download);
  });

  test('file transfer action guards follow active paused and failed state', () => {
    const transfer = _upload(100);

    expect(canPauseFileTransfer(transfer)).toBe(true);
    expect(canResumeFileTransfer(transfer)).toBe(false);
    expect(canCancelActiveFileTransfer(transfer)).toBe(true);

    transfer.pauseTransfer();
    expect(canPauseFileTransfer(transfer)).toBe(false);
    expect(canResumeFileTransfer(transfer)).toBe(true);
    expect(canCancelActiveFileTransfer(transfer)).toBe(true);

    transfer.resumeTransfer();
    transfer.markFailed('network failed');
    expect(canPauseFileTransfer(transfer)).toBe(false);
    expect(canResumeFileTransfer(transfer)).toBe(false);
    expect(canCancelActiveFileTransfer(transfer)).toBe(false);

    const sending = _upload(100);
    sending.markSendingMessage();
    expect(canCancelActiveFileTransfer(sending)).toBe(false);

    expect(canPauseFileTransfer(null)).toBe(false);
    expect(canResumeFileTransfer(null)).toBe(false);
    expect(canCancelActiveFileTransfer(null)).toBe(false);
  });
});
