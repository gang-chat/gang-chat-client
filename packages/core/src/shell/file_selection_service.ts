import { HostPlatform } from '../config/host_platform';
import { platform } from '../platform/platform';
import type { PlatformFile, PlatformFileTypeGroup } from '../platform/platform';

export class FileTypeGroup {
  constructor(opts: { label: string; extensions: string[] }) {
    this.label = opts.label;
    this.extensions = opts.extensions;
  }

  readonly label: string;
  readonly extensions: string[];
}

export class SelectedFile {
  private constructor(opts: {
    name: string;
    mimeType: string | null;
    readAsBytes: () => Promise<Uint8Array>;
    length: () => Promise<number>;
  }) {
    this.name = opts.name;
    this.mimeType = opts.mimeType;
    this._readAsBytes = opts.readAsBytes;
    this._length = opts.length;
  }

  /**
   * Port of the private `SelectedFile._fromSelectorFile`: wraps a file the
   * host picked, dropped or pasted. Public so the web drop / paste adapters in
   * `@gang-chat/ui` can use it.
   */
  static fromPlatformFile(file: PlatformFile): SelectedFile {
    return new SelectedFile({
      name: file.name,
      mimeType: file.mimeType != null && file.mimeType.length > 0 ? file.mimeType : null,
      readAsBytes: () => file.readAsBytes(),
      length: async () => file.size,
    });
  }

  /** A file on disk; bytes and length are read through `platform().files` on demand. */
  static fromPath(path: string): SelectedFile {
    return new SelectedFile({
      name: _basename(path),
      mimeType: null,
      readAsBytes: () => platform().files.readFile(path),
      length: async () => (await platform().files.fileSize(path)) ?? 0,
    });
  }

  /**
   * A file whose bytes are loaded on demand, such as one dropped or pasted
   * into a browser, which exposes contents but no path.
   */
  static deferred(opts: {
    name: string;
    mimeType: string;
    length: number;
    readAsBytes: () => Promise<Uint8Array>;
  }): SelectedFile {
    const { length } = opts;
    return new SelectedFile({
      name: opts.name,
      mimeType: opts.mimeType.length === 0 ? null : opts.mimeType,
      readAsBytes: opts.readAsBytes,
      length: async () => length,
    });
  }

  static fromBytes(opts: { name: string; mimeType: string; bytes: Uint8Array }): SelectedFile {
    const data = Uint8Array.from(opts.bytes);
    return new SelectedFile({
      name: opts.name,
      mimeType: opts.mimeType,
      readAsBytes: async () => Uint8Array.from(data),
      length: async () => data.length,
    });
  }

  readonly name: string;
  readonly mimeType: string | null;
  private readonly _readAsBytes: () => Promise<Uint8Array>;
  private readonly _length: () => Promise<number>;

  readAsBytes(): Promise<Uint8Array> {
    return this._readAsBytes();
  }

  length(): Promise<number> {
    return this._length();
  }
}

export class SaveFileLocation {
  constructor(opts: { path: string; documentUri?: string | null }) {
    this.path = opts.path;
    this.documentUri = opts.documentUri ?? null;
  }

  readonly path: string;
  /** Android Storage Access Framework document; never set by the TypeScript hosts. */
  readonly documentUri: string | null;

  get requiresCommit(): boolean {
    return this.documentUri != null;
  }
}

/**
 * PORT: the Android branches (SAF `createDocument` staging files, MediaStore
 * downloads) went through `AndroidSystemService`; Android is the native client
 * under android/, so [getSaveLocation] always returns a plain path,
 * [commitLocation] / [discardLocation] are no-ops and [saveBytesToDownloads]
 * returns null.
 */
export class FileSelectionService {
  async openFile(opts: { acceptedTypeGroups?: FileTypeGroup[] } = {}): Promise<SelectedFile | null> {
    const { acceptedTypeGroups = [] } = opts;
    const file = await platform().files.openFile(
      acceptedTypeGroups.length === 0 ? {} : { acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups) },
    );
    return file == null ? null : SelectedFile.fromPlatformFile(file);
  }

  async openFiles(opts: { acceptedTypeGroups?: FileTypeGroup[] } = {}): Promise<SelectedFile[]> {
    const { acceptedTypeGroups = [] } = opts;
    const files = await platform().files.openFiles(
      acceptedTypeGroups.length === 0 ? {} : { acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups) },
    );
    return files.map((file) => SelectedFile.fromPlatformFile(file));
  }

  async getSaveLocation(opts: {
    suggestedName: string;
    acceptedTypeGroups?: FileTypeGroup[];
    confirmButtonText?: string | null;
  }): Promise<SaveFileLocation | null> {
    const { suggestedName, acceptedTypeGroups = [], confirmButtonText } = opts;
    const path = await platform().files.getSaveLocation({
      suggestedName,
      ...(acceptedTypeGroups.length === 0 ? {} : { acceptedTypeGroups: _selectorTypeGroups(acceptedTypeGroups) }),
      ...(confirmButtonText != null ? { confirmButtonText } : {}),
    });
    return path == null ? null : new SaveFileLocation({ path });
  }

  filesFromPaths(paths: Iterable<string>): SelectedFile[] {
    return Array.from(paths, (path) => SelectedFile.fromPath(path));
  }

  saveBytesToPath(opts: { bytes: Uint8Array; path: string; filename: string; mimeType?: string | null }): Promise<void> {
    return platform().files.writeFile(opts.path, opts.bytes);
  }

  async saveBytesToLocation(opts: {
    bytes: Uint8Array;
    location: SaveFileLocation;
    filename: string;
    mimeType?: string | null;
  }): Promise<void> {
    await this.saveBytesToPath({
      bytes: opts.bytes,
      path: opts.location.path,
      filename: opts.filename,
      mimeType: opts.mimeType,
    });
  }

  async commitLocation(_location: SaveFileLocation): Promise<void> {}

  async discardLocation(_location: SaveFileLocation): Promise<void> {}

  /**
   * Whether files are saved by handing them to the browser as a download.
   * Browsers have no save dialog or writable paths to offer, so on the web
   * use [saveBytesAsBrowserDownload] instead of picking a location.
   */
  get savesAsBrowserDownload(): boolean {
    return HostPlatform.isWeb;
  }

  /**
   * Web only: hands [bytes] to the browser as a download named [filename];
   * the browser decides where it goes.
   */
  async saveBytesAsBrowserDownload(opts: { bytes: Uint8Array; filename: string; mimeType?: string | null }): Promise<void> {
    const save = platform().files.saveAsDownload;
    if (save == null) throw new Error('Browser downloads only exist on the web.');
    await save.call(platform().files, {
      bytes: opts.bytes,
      filename: opts.filename,
      mimeType: opts.mimeType ?? _mimeTypeForSuggestedName(opts.filename),
    });
  }

  async saveBytesToDownloads(_opts: { bytes: Uint8Array; filename: string; mimeType?: string | null }): Promise<string | null> {
    return null;
  }
}

function _selectorTypeGroups(groups: FileTypeGroup[]): PlatformFileTypeGroup[] {
  return groups.map((group) => ({ label: group.label, extensions: group.extensions }));
}

export function _mimeTypeForSuggestedName(filename: string): string {
  const extension = (filename.split('.').pop() ?? '').toLowerCase();
  switch (extension) {
    case 'png':
      return 'image/png';
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'webp':
      return 'image/webp';
    case 'gif':
      return 'image/gif';
    case 'zip':
      return 'application/zip';
    case 'mp3':
      return 'audio/mpeg';
    case 'm4a':
      return 'audio/mp4';
    case 'wav':
      return 'audio/wav';
    case 'mp4':
      return 'video/mp4';
    case 'pdf':
      return 'application/pdf';
    case 'txt':
      return 'text/plain';
    default:
      return 'application/octet-stream';
  }
}

function _basename(path: string): string {
  const slash = path.lastIndexOf('/');
  const backslash = path.lastIndexOf('\\');
  const index = Math.max(slash, backslash);
  return index < 0 ? path : path.slice(index + 1);
}
