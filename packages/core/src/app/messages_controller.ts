import type { JsonMap } from '../lib/json';
import {
  newClientId,
  newUuid,
  UploadCancelledException,
  UploadTransferController,
  type GangApi,
  type MessagePage,
  type UploadProgressCallback,
} from '../protocol/api_client';
import {
  Message,
  MessageAttachment,
  UploadedAsset,
  type MessageQuote,
  type MessageRecallResult,
  type UserSummary,
} from '../protocol/models';
import { FileTransferState } from './file_transfer_state';

export type PendingMessageHandler = (pending: PendingMessage) => void;
export type PendingFileMessageHandler = (pending: PendingFileMessage) => void;
export type FileMessageProgressHandler = (
  pending: PendingFileMessage,
  opts: { sentBytes: number; totalBytes: number },
) => void;
export type FileMessageUploadedHandler = (pending: PendingFileMessage, attachment: MessageAttachment) => void;

export function canSendComposedMessage(opts: {
  body: string;
  type: string;
  attachments: MessageAttachment[];
}): boolean {
  if (opts.type === 'text') return opts.body.trim().length > 0;
  return opts.attachments.length > 0;
}

/**
 * One server message in the deterministic send plan produced from the
 * composer. Rich music components remain standalone protocol messages; when
 * text is present it follows immediately as a second plain-text message.
 */
export class ComposerMessageSendPart {
  constructor(opts: { body: string; type: string; attachments: MessageAttachment[] }) {
    this.body = opts.body;
    this.type = opts.type;
    this.attachments = opts.attachments;
  }

  readonly body: string;
  readonly type: string;
  readonly attachments: MessageAttachment[];

  get isComponent(): boolean {
    return this.type === 'playlist' || this.type === 'music_track';
  }
}

export function composerMessageSendPlan(opts: {
  body: string;
  component?: Message | null;
}): ComposerMessageSendPart[] {
  const component = opts.component ?? null;
  const normalizedBody = opts.body.trimEnd();
  const result: ComposerMessageSendPart[] = [];
  if (component != null) {
    result.push(
      new ComposerMessageSendPart({
        body: '',
        type: component.type,
        attachments: [
          new MessageAttachment({
            type: component.type,
            sourceMessageId: component.id,
          }),
        ],
      }),
    );
  }
  if (normalizedBody.trim().length > 0) {
    result.push(
      new ComposerMessageSendPart({
        body: normalizedBody,
        type: 'text',
        attachments: [],
      }),
    );
  }
  return result;
}

export type ComposerMessagePartSender = (
  part: ComposerMessageSendPart,
  opts: { hasFollowingPart: boolean },
) => Promise<boolean>;

/**
 * Runs a composed multi-message plan in order and stops on the first failure.
 * Already-successful parts are never replayed by this invocation.
 */
export async function sendComposerMessagePartsSequentially(opts: {
  parts: ComposerMessageSendPart[];
  send: ComposerMessagePartSender;
}): Promise<boolean> {
  const { parts, send } = opts;
  for (let index = 0; index < parts.length; index++) {
    const succeeded = await send(parts[index]!, {
      hasFollowingPart: index < parts.length - 1,
    });
    if (!succeeded) return false;
  }
  return true;
}

export class PendingMessage {
  constructor(opts: { clientMessageId: string; local: Message }) {
    this.clientMessageId = opts.clientMessageId;
    this.local = opts.local;
  }

  readonly clientMessageId: string;
  readonly local: Message;
}

export class PendingFileMessage {
  constructor(opts: {
    clientMessageId: string;
    transfer: FileTransferState;
    localAttachment: MessageAttachment;
    local: Message;
  }) {
    this.clientMessageId = opts.clientMessageId;
    this.transfer = opts.transfer;
    this.localAttachment = opts.localAttachment;
    this.local = opts.local;
  }

  readonly clientMessageId: string;
  readonly transfer: FileTransferState;
  readonly localAttachment: MessageAttachment;
  readonly local: Message;
}

export class FileMessageStatePatch {
  constructor(opts: { messages: Message[]; fileTransfers: Map<string, FileTransferState> }) {
    this.messages = opts.messages;
    this.fileTransfers = opts.fileTransfers;
  }

  readonly messages: Message[];
  readonly fileTransfers: Map<string, FileTransferState>;
}

export class MessageSendStatePatch {
  constructor(opts: { messages: Message[]; sending: boolean; error: string | null }) {
    this.messages = opts.messages;
    this.sending = opts.sending;
    this.error = opts.error;
  }

  readonly messages: Message[];
  readonly sending: boolean;
  readonly error: string | null;
}

export class MessagesController {
  constructor(opts: { api?: GangApi | null } = {}) {
    this.api = opts.api ?? null;
  }

  readonly api: GangApi | null;

  private get _client(): GangApi {
    const client = this.api;
    if (client == null) {
      throw new Error('MessagesController requires an authenticated API');
    }
    return client;
  }

  /**
   * Mint a client-side id with the given [prefix]. Exposed so callers can tag
   * staged composer attachments before any message exists.
   */
  mintClientId(prefix: string): string {
    return newClientId(prefix);
  }

  createPendingMessage(opts: {
    roomId: string;
    sender: UserSummary;
    body: string;
    type?: string;
    attachments?: MessageAttachment[];
    mentions?: JsonMap[];
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
  }): PendingMessage {
    const { roomId, sender, body, type = 'text', attachments = [], mentions = [], quotes = [] } = opts;
    const quote = opts.quote ?? null;
    const clientMessageId = newClientId('cmsg');
    return new PendingMessage({
      clientMessageId,
      local: Message.local({
        roomId,
        sender,
        clientMessageId,
        type,
        body,
        attachments,
        mentions,
        quote,
        quotes,
      }),
    });
  }

  createPendingFileMessage(opts: {
    roomId: string;
    sender: UserSummary;
    filename: string;
    sizeBytes: number;
    mimeType: string;
  }): PendingFileMessage {
    const { roomId, sender, filename, sizeBytes, mimeType } = opts;
    const clientMessageId = newClientId('cmsg');
    const transfer = FileTransferState.upload({
      controller: new UploadTransferController(),
      totalBytes: sizeBytes,
    });
    const localAttachment = this.fileAttachment({
      name: filename,
      asset: new UploadedAsset({
        id: `local_${clientMessageId}`,
        url: '',
        thumbnailUrl: null,
        mimeType,
        filename,
        sizeBytes,
      }),
    });
    return new PendingFileMessage({
      clientMessageId,
      transfer,
      localAttachment,
      local: Message.local({
        roomId,
        sender,
        clientMessageId,
        body: filename,
        type: 'file',
        attachments: [localAttachment],
      }),
    });
  }

  createPendingVoiceMessage(opts: {
    roomId: string;
    sender: UserSummary;
    filename: string;
    sizeBytes: number;
    mimeType: string;
    /** Milliseconds. */
    duration: number;
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
  }): PendingFileMessage {
    const { roomId, sender, filename, sizeBytes, mimeType, duration, quotes = [] } = opts;
    const quote = opts.quote ?? null;
    const clientMessageId = newClientId('cmsg');
    const transfer = FileTransferState.upload({
      controller: new UploadTransferController(),
      totalBytes: sizeBytes,
    });
    const localAttachment = this.fileAttachment({
      type: 'audio',
      name: filename,
      asset: new UploadedAsset({
        id: `local_${clientMessageId}`,
        url: '',
        thumbnailUrl: null,
        mimeType,
        filename,
        sizeBytes,
      }),
      duration,
    });
    return new PendingFileMessage({
      clientMessageId,
      transfer,
      localAttachment,
      local: Message.local({
        roomId,
        sender,
        clientMessageId,
        body: filename,
        type: 'audio',
        attachments: [localAttachment],
        quote,
        quotes,
      }),
    });
  }

  fileAttachment(opts: {
    type?: string;
    name: string;
    asset: UploadedAsset;
    /** Milliseconds. */
    duration?: number | null;
  }): MessageAttachment {
    const { type = 'file', name, asset } = opts;
    const duration = opts.duration ?? null;
    const durationMs = duration == null || duration <= 0 ? null : Math.trunc(duration);
    return new MessageAttachment({
      type,
      name,
      asset,
      durationMs,
    });
  }

  uploadFileAsset(opts: {
    bytes: Uint8Array;
    filename: string;
    controller?: UploadTransferController | null;
    onProgress?: UploadProgressCallback | null;
  }): Promise<UploadedAsset> {
    return this._client.uploadFileAsset({
      bytes: opts.bytes,
      filename: opts.filename,
      controller: opts.controller,
      onProgress: opts.onProgress,
    });
  }

  async loadMessages(roomId: string): Promise<Message[]> {
    const page = await this._client.listMessages({ roomId });
    return page.messages;
  }

  loadMessageHistory(opts: {
    roomId: string;
    query?: string;
    category?: string;
    senderUserId?: string | null;
    startAt?: Date | null;
    endAt?: Date | null;
    limit?: number;
    before?: string | null;
  }): Promise<MessagePage> {
    const { roomId, query = '', category = 'all', limit = 50 } = opts;
    return this._client.listMessageHistory({
      roomId,
      query,
      category,
      senderUserId: opts.senderUserId,
      startAt: opts.startAt,
      endAt: opts.endAt,
      limit,
      before: opts.before,
    });
  }

  async hideMessageHistory(opts: { roomId: string; messageIds: Iterable<string> }): Promise<number> {
    const { roomId } = opts;
    const ids = [...new Set(opts.messageIds)];
    let deletedCount = 0;
    for (let start = 0; start < ids.length; start += 100) {
      const end = Math.min(Math.max(start + 100, 0), ids.length);
      deletedCount += await this._client.hideMessageHistory({
        roomId,
        messageIds: ids.slice(start, end),
      });
    }
    return deletedCount;
  }

  async loadMessagesUntil(opts: {
    roomId: string;
    messageId: string;
    pageLimit?: number;
    maxPages?: number;
  }): Promise<Message[]> {
    const { roomId, messageId, pageLimit = 100, maxPages = 20 } = opts;
    const messages: Message[] = [];
    let before: string | null = null;
    for (let pageIndex = 0; pageIndex < maxPages; pageIndex++) {
      const page: MessagePage = await this._client.listMessages({
        roomId,
        limit: pageLimit,
        before,
      });
      messages.unshift(...page.messages);
      if (messages.some((message) => message.id === messageId)) {
        return messages;
      }
      if (!page.hasMore || page.nextBefore == null) return messages;
      before = page.nextBefore;
    }
    return messages;
  }

  markRead(opts: { roomId: string; lastReadMessageId: string }): Promise<number> {
    return this._client.markRead({
      roomId: opts.roomId,
      lastReadMessageId: opts.lastReadMessageId,
    });
  }

  recallMessage(opts: { roomId: string; messageId: string; reason?: string | null }): Promise<MessageRecallResult> {
    return this._client.recallMessage({
      roomId: opts.roomId,
      messageId: opts.messageId,
      reason: opts.reason,
    });
  }

  sendMessage(opts: {
    roomId: string;
    clientMessageId: string;
    body: string;
    type?: string;
    attachments?: MessageAttachment[];
    mentions?: JsonMap[];
    quoteMessageId?: string | null;
    quoteMessageIds?: string[];
  }): Promise<Message> {
    const { roomId, clientMessageId, body, type = 'text', attachments = [], mentions = [], quoteMessageIds = [] } = opts;
    return this._client.sendMessage({
      roomId,
      clientMessageId,
      body,
      type,
      attachments,
      mentions,
      quoteMessageId: opts.quoteMessageId,
      quoteMessageIds,
      idempotencyKey: newUuid(),
    });
  }

  sendComposedMessage(opts: {
    roomId: string;
    sender: UserSummary;
    body: string;
    type?: string;
    attachments?: MessageAttachment[];
    mentions?: JsonMap[];
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
    onPending?: PendingMessageHandler | null;
  }): Promise<Message> {
    const { roomId, sender, body, type = 'text', attachments = [], mentions = [], quotes = [] } = opts;
    const quote = opts.quote ?? null;
    const pending = this.createPendingMessage({
      roomId,
      sender,
      body,
      type,
      attachments,
      mentions,
      quote,
      quotes,
    });
    opts.onPending?.(pending);
    return this.sendMessage({
      roomId,
      clientMessageId: pending.clientMessageId,
      body,
      type,
      attachments,
      mentions,
      quoteMessageId: quote?.messageId,
      quoteMessageIds: quotes.map((quote) => quote.messageId),
    });
  }

  async sendFileMessage(opts: {
    roomId: string;
    sender: UserSummary;
    filename: string;
    sizeBytes: number;
    mimeType: string;
    readBytes: () => Promise<Uint8Array>;
    onPending?: PendingFileMessageHandler | null;
    onProgress?: FileMessageProgressHandler | null;
    onUploaded?: FileMessageUploadedHandler | null;
  }): Promise<Message> {
    const { roomId, sender, filename, sizeBytes, mimeType, readBytes } = opts;
    const onProgress = opts.onProgress ?? null;
    const pending = this.createPendingFileMessage({
      roomId,
      sender,
      filename,
      sizeBytes,
      mimeType,
    });
    opts.onPending?.(pending);

    const transfer = pending.transfer;
    const bytes = await readBytes();
    if (bytes.length === 0) throw new Error('文件为空');
    if (transfer.cancelled) throw new UploadCancelledException();

    const asset = await this.uploadFileAsset({
      bytes,
      filename,
      controller: transfer.controller,
      onProgress:
        onProgress == null
          ? null
          : ({ sentBytes, totalBytes }) => {
              onProgress(pending, { sentBytes, totalBytes });
            },
    });
    if (transfer.cancelled) throw new UploadCancelledException();

    const attachment = this.fileAttachment({ name: filename, asset });
    opts.onUploaded?.(pending, attachment);

    return this.sendMessage({
      roomId,
      clientMessageId: pending.clientMessageId,
      body: filename,
      type: 'file',
      attachments: [attachment],
    });
  }

  async sendVoiceMessage(opts: {
    roomId: string;
    sender: UserSummary;
    filename: string;
    sizeBytes: number;
    mimeType: string;
    /** Milliseconds. */
    duration: number;
    readBytes: () => Promise<Uint8Array>;
    quote?: MessageQuote | null;
    quotes?: MessageQuote[];
    onPending?: PendingFileMessageHandler | null;
    onProgress?: FileMessageProgressHandler | null;
    onUploaded?: FileMessageUploadedHandler | null;
  }): Promise<Message> {
    const { roomId, sender, filename, sizeBytes, mimeType, duration, readBytes, quotes = [] } = opts;
    const quote = opts.quote ?? null;
    const onProgress = opts.onProgress ?? null;
    const pending = this.createPendingVoiceMessage({
      roomId,
      sender,
      filename,
      sizeBytes,
      mimeType,
      duration,
      quote,
      quotes,
    });
    opts.onPending?.(pending);

    const transfer = pending.transfer;
    const bytes = await readBytes();
    // PORT: kept verbatim from the Dart source (a mis-encoded '文件为空').
    if (bytes.length === 0) throw new Error('鏂囦欢涓虹┖');
    if (transfer.cancelled) throw new UploadCancelledException();

    const asset = await this.uploadFileAsset({
      bytes,
      filename,
      controller: transfer.controller,
      onProgress:
        onProgress == null
          ? null
          : ({ sentBytes, totalBytes }) => {
              onProgress(pending, { sentBytes, totalBytes });
            },
    });
    if (transfer.cancelled) throw new UploadCancelledException();

    const attachment = this.fileAttachment({
      type: 'audio',
      name: filename,
      asset,
      duration,
    });
    opts.onUploaded?.(pending, attachment);

    return this.sendMessage({
      roomId,
      clientMessageId: pending.clientMessageId,
      body: filename,
      type: 'audio',
      attachments: [attachment],
      quoteMessageId: quote?.messageId,
      quoteMessageIds: quotes.map((quote) => quote.messageId),
    });
  }

  patchPendingMessage(opts: { messages: Message[]; pending: PendingMessage }): Message[] {
    return this.appendLocalMessage(opts.messages, opts.pending.local);
  }

  patchSentMessage(opts: { messages: Message[]; sent: Message }): Message[] {
    return this.replaceByClientId(opts.messages, opts.sent);
  }

  patchFailedMessage(opts: { messages: Message[]; clientMessageId: string }): Message[] {
    return this.markFailedByClientId(opts.messages, opts.clientMessageId);
  }

  patchMessageSendStarted(opts: { messages: Message[] }): MessageSendStatePatch {
    return new MessageSendStatePatch({
      messages: opts.messages,
      sending: true,
      error: null,
    });
  }

  patchMessageSendPending(opts: {
    messages: Message[];
    pending: PendingMessage;
    error: string | null;
  }): MessageSendStatePatch {
    return new MessageSendStatePatch({
      messages: this.patchPendingMessage({ messages: opts.messages, pending: opts.pending }),
      sending: true,
      error: opts.error,
    });
  }

  patchMessageSendSucceeded(opts: { messages: Message[]; sent: Message; error: string | null }): MessageSendStatePatch {
    return new MessageSendStatePatch({
      messages: this.patchSentMessage({ messages: opts.messages, sent: opts.sent }),
      sending: true,
      error: opts.error,
    });
  }

  patchMessageSendFailed(opts: {
    messages: Message[];
    clientMessageId: string | null;
    error: string | null;
  }): MessageSendStatePatch {
    const { messages, clientMessageId } = opts;
    return new MessageSendStatePatch({
      messages:
        clientMessageId == null
          ? messages
          : this.patchFailedMessage({
              messages,
              clientMessageId,
            }),
      sending: true,
      error: opts.error,
    });
  }

  patchMessageSendFinished(opts: { messages: Message[]; error: string | null }): MessageSendStatePatch {
    return new MessageSendStatePatch({
      messages: opts.messages,
      sending: false,
      error: opts.error,
    });
  }

  patchPendingFileMessage(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    pending: PendingFileMessage;
  }): FileMessageStatePatch {
    const { pending } = opts;
    return new FileMessageStatePatch({
      messages: this.appendLocalMessage(opts.messages, pending.local),
      fileTransfers: new Map([...opts.fileTransfers, [pending.clientMessageId, pending.transfer]]),
    });
  }

  updateFileTransferProgress(opts: {
    fileTransfers: Map<string, FileTransferState>;
    pending: PendingFileMessage;
    sentBytes: number;
    totalBytes: number;
  }): boolean {
    const current = opts.fileTransfers.get(opts.pending.clientMessageId);
    if (current == null || current.cancelled) return false;
    current.updateProgress({ sentBytes: opts.sentBytes, totalBytes: opts.totalBytes });
    return true;
  }

  patchFileTransferProgress(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    pending: PendingFileMessage;
    sentBytes: number;
    totalBytes: number;
  }): FileMessageStatePatch | null {
    const changed = this.updateFileTransferProgress({
      fileTransfers: opts.fileTransfers,
      pending: opts.pending,
      sentBytes: opts.sentBytes,
      totalBytes: opts.totalBytes,
    });
    if (!changed) return null;
    return new FileMessageStatePatch({
      messages: opts.messages,
      fileTransfers: opts.fileTransfers,
    });
  }

  pauseFileUpload(opts: { fileTransfers: Map<string, FileTransferState>; clientMessageId: string }): boolean {
    const transfer = opts.fileTransfers.get(opts.clientMessageId);
    if (transfer == null || transfer.isDownload) return false;
    return transfer.pauseTransfer();
  }

  patchPausedFileUpload(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    clientMessageId: string;
  }): FileMessageStatePatch | null {
    const changed = this.pauseFileUpload({
      fileTransfers: opts.fileTransfers,
      clientMessageId: opts.clientMessageId,
    });
    if (!changed) return null;
    return new FileMessageStatePatch({
      messages: opts.messages,
      fileTransfers: opts.fileTransfers,
    });
  }

  resumeFileUpload(opts: { fileTransfers: Map<string, FileTransferState>; clientMessageId: string }): boolean {
    const transfer = opts.fileTransfers.get(opts.clientMessageId);
    if (transfer == null || transfer.isDownload) return false;
    return transfer.resumeTransfer();
  }

  patchResumedFileUpload(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    clientMessageId: string;
  }): FileMessageStatePatch | null {
    const changed = this.resumeFileUpload({
      fileTransfers: opts.fileTransfers,
      clientMessageId: opts.clientMessageId,
    });
    if (!changed) return null;
    return new FileMessageStatePatch({
      messages: opts.messages,
      fileTransfers: opts.fileTransfers,
    });
  }

  cancelFileUpload(opts: { fileTransfers: Map<string, FileTransferState>; clientMessageId: string }): boolean {
    const transfer = opts.fileTransfers.get(opts.clientMessageId);
    if (transfer == null || transfer.isDownload) return false;
    return transfer.cancelTransfer();
  }

  patchUploadedFileMessage(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    pending: PendingFileMessage;
    attachment: MessageAttachment;
  }): FileMessageStatePatch {
    const { messages, fileTransfers, pending, attachment } = opts;
    if (pending.transfer.cancelled) {
      return new FileMessageStatePatch({
        messages,
        fileTransfers,
      });
    }
    pending.transfer.markSendingMessage();
    return new FileMessageStatePatch({
      messages: this.updateByClientId(messages, pending.clientMessageId, (message) =>
        this.copyMessage(message, { attachments: [attachment] }),
      ),
      fileTransfers,
    });
  }

  patchSentFileMessage(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    clientMessageId: string;
    sent: Message;
  }): FileMessageStatePatch {
    const nextTransfers = new Map(opts.fileTransfers);
    nextTransfers.delete(opts.clientMessageId);
    return new FileMessageStatePatch({
      messages: this.replaceByClientId(opts.messages, opts.sent),
      fileTransfers: nextTransfers,
    });
  }

  patchFailedFileMessage(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    clientMessageId: string;
    failure: unknown;
  }): FileMessageStatePatch {
    const transfer = opts.fileTransfers.get(opts.clientMessageId);
    transfer?.markFailed(opts.failure);
    return new FileMessageStatePatch({
      messages: this.markFailedByClientId(opts.messages, opts.clientMessageId),
      fileTransfers: opts.fileTransfers,
    });
  }

  patchRemovedFileMessage(opts: {
    messages: Message[];
    fileTransfers: Map<string, FileTransferState>;
    clientMessageId: string;
  }): FileMessageStatePatch {
    const nextTransfers = new Map(opts.fileTransfers);
    nextTransfers.delete(opts.clientMessageId);
    return new FileMessageStatePatch({
      messages: this.removeByClientId(opts.messages, opts.clientMessageId),
      fileTransfers: nextTransfers,
    });
  }

  replaceByClientId(messages: Message[], sent: Message): Message[] {
    let replaced = false;
    const next = messages.map((message) => {
      if (message.clientMessageId !== sent.clientMessageId) return message;
      replaced = true;
      return sent;
    });
    if (replaced) return next;
    return [...next, sent];
  }

  replaceByMessageId(messages: Message[], updated: Message): Message[] {
    let replaced = false;
    const next = messages.map((message) => {
      if (message.id !== updated.id) return message;
      replaced = true;
      return updated;
    });
    if (replaced) return next;
    return this.replaceByClientId(messages, updated);
  }

  appendLocalMessage(messages: Message[], message: Message): Message[] {
    return [...messages, message];
  }

  removeByClientId(messages: Message[], clientMessageId: string): Message[] {
    return messages.filter((message) => message.clientMessageId !== clientMessageId);
  }

  markFailedByClientId(messages: Message[], clientMessageId: string): Message[] {
    return this.updateByClientId(messages, clientMessageId, (message) => message.markFailed());
  }

  updateByClientId(messages: Message[], clientMessageId: string, update: (message: Message) => Message): Message[] {
    return messages.map((message) => {
      if (message.clientMessageId !== clientMessageId) return message;
      return update(message);
    });
  }

  copyMessage(
    message: Message,
    opts: { attachments?: MessageAttachment[] | null; pending?: boolean | null; failed?: boolean | null } = {},
  ): Message {
    return new Message({
      id: message.id,
      roomId: message.roomId,
      sender: message.sender,
      clientMessageId: message.clientMessageId,
      type: message.type,
      body: message.body,
      createdAt: message.createdAt,
      attachments: opts.attachments ?? message.attachments,
      mentions: message.mentions,
      quote: message.quote,
      quotes: message.quotes,
      isRecalled: message.isRecalled,
      recalledAt: message.recalledAt,
      recalledBy: message.recalledBy,
      isForceDeleted: message.isForceDeleted,
      forceDeletedAt: message.forceDeletedAt,
      forceDeletedBy: message.forceDeletedBy,
      pending: opts.pending ?? message.pending,
      failed: opts.failed ?? message.failed,
    });
  }
}
