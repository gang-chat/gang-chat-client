import type { FileSystemBridge, PlatformFile, PlatformFileTypeGroup } from '@gang-chat/core/platform/platform';

/**
 * Files in the browser.
 *
 * Picking goes through an `<input type="file">` (file_selector's web
 * implementation) and saving hands the bytes to the browser as a download
 * (`saveAsDownload`; `FileSelectionService.savesAsBrowserDownload` routes
 * every save there on the web). Browsers expose no file system, so the path
 * operations the shared code still performs — the sticker-pack cache, temp
 * files — run against a small in-memory tree that lasts until the page
 * reloads, like the Flutter web build's in-memory media cache.
 */

const _directories: { temporary: string; applicationSupport: string; applicationCache: string; downloads: string | null } = {
  temporary: '/tmp',
  applicationSupport: '/support',
  applicationCache: '/cache',
  downloads: null,
};

/** Keeps the in-memory tree from growing without bound. */
const _memoryLimitBytes = 64 * 1024 * 1024;

interface _MemoryFile {
  bytes: Uint8Array;
  modifiedMs: number;
}

function _normalize(path: string): string {
  const parts: string[] = [];
  for (const part of path.split('/')) {
    if (part.length === 0 || part === '.') continue;
    if (part === '..') parts.pop();
    else parts.push(part);
  }
  return `/${parts.join('/')}`;
}

function _parent(path: string): string {
  const normalized = _normalize(path);
  const index = normalized.lastIndexOf('/');
  return index <= 0 ? '/' : normalized.substring(0, index);
}

function _notFound(path: string): Error {
  const error = new Error(`No such file: ${path}`);
  error.name = 'NotFoundError';
  return error;
}

function _accept(groups: PlatformFileTypeGroup[] | undefined): string {
  const accept: string[] = [];
  for (const group of groups ?? []) {
    for (const extension of group.extensions ?? []) {
      const trimmed = extension.trim().replace(/^\./, '');
      if (trimmed.length > 0) accept.push(`.${trimmed}`);
    }
    for (const mimeType of group.mimeTypes ?? []) accept.push(mimeType);
  }
  return accept.join(',');
}

function _platformFile(file: File): PlatformFile {
  return {
    name: file.name,
    path: null,
    mimeType: file.type.length > 0 ? file.type : null,
    size: file.size,
    readAsBytes: async () => new Uint8Array(await file.arrayBuffer()),
  };
}

/** Opens the browser's file picker; resolves [] when the user cancels. */
function _pickFiles(options: { acceptedTypeGroups?: PlatformFileTypeGroup[]; multiple: boolean }): Promise<File[]> {
  return new Promise<File[]>((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = options.multiple;
    const accept = _accept(options.acceptedTypeGroups);
    if (accept.length > 0) input.accept = accept;
    input.style.display = 'none';
    let settled = false;
    const finish = (files: File[]) => {
      if (settled) return;
      settled = true;
      input.remove();
      resolve(files);
    };
    input.addEventListener('change', () => finish(input.files == null ? [] : Array.from(input.files)));
    input.addEventListener('cancel', () => finish([]));
    document.body.append(input);
    input.click();
  });
}

export function createWebFiles(): FileSystemBridge {
  const tree = new Map<string, _MemoryFile>();
  const directories = new Set<string>(['/', _directories.temporary, _directories.applicationSupport, _directories.applicationCache]);

  const usedBytes = () => {
    let total = 0;
    for (const file of tree.values()) total += file.bytes.length;
    return total;
  };
  const ensureParents = (path: string) => {
    for (let dir = _parent(path); !directories.has(dir); dir = _parent(dir)) directories.add(dir);
  };
  const store = (path: string, bytes: Uint8Array) => {
    const key = _normalize(path);
    const previous = tree.get(key)?.bytes.length ?? 0;
    if (usedBytes() - previous + bytes.length > _memoryLimitBytes) {
      // Drop the oldest files first; the callers treat these as caches.
      const oldest = [...tree.entries()].filter(([name]) => name !== key).sort((a, b) => a[1].modifiedMs - b[1].modifiedMs);
      for (const [name] of oldest) {
        if (usedBytes() - previous + bytes.length <= _memoryLimitBytes) break;
        tree.delete(name);
      }
    }
    ensureParents(key);
    tree.set(key, { bytes, modifiedMs: Date.now() });
  };
  const read = (path: string): _MemoryFile => {
    const file = tree.get(_normalize(path));
    if (file == null) throw _notFound(path);
    return file;
  };

  return {
    async openFile(options) {
      const [file] = await _pickFiles({ acceptedTypeGroups: options.acceptedTypeGroups, multiple: false });
      return file == null ? null : _platformFile(file);
    },
    async openFiles(options) {
      const files = await _pickFiles({ acceptedTypeGroups: options.acceptedTypeGroups, multiple: true });
      return files.map(_platformFile);
    },
    // Browsers have no save dialog; saves go through saveAsDownload.
    getSaveLocation: async () => null,
    // Browsers never hand out paths (drops and pastes carry contents).
    filesFromPaths: async () => [],
    async writeFile(path, bytes) {
      store(path, Uint8Array.from(bytes));
    },
    async appendFile(path, bytes) {
      const existing = tree.get(_normalize(path))?.bytes ?? new Uint8Array(0);
      const next = new Uint8Array(existing.length + bytes.length);
      next.set(existing, 0);
      next.set(bytes, existing.length);
      store(path, next);
    },
    async readFile(path) {
      return Uint8Array.from(read(path).bytes);
    },
    async deleteFile(path) {
      tree.delete(_normalize(path));
    },
    async rename(from, to) {
      const file = read(from);
      tree.delete(_normalize(from));
      ensureParents(to);
      tree.set(_normalize(to), file);
    },
    async exists(path) {
      const key = _normalize(path);
      return tree.has(key) || directories.has(key);
    },
    async fileSize(path) {
      return tree.get(_normalize(path))?.bytes.length ?? null;
    },
    async listDirectory(path) {
      const dir = _normalize(path);
      const prefix = dir === '/' ? '/' : `${dir}/`;
      const entries: Array<{ name: string; path: string; isDirectory: boolean; size: number; modifiedMs: number }> = [];
      for (const [name, file] of tree) {
        if (_parent(name) !== dir) continue;
        entries.push({ name: name.substring(prefix.length), path: name, isDirectory: false, size: file.bytes.length, modifiedMs: file.modifiedMs });
      }
      for (const name of directories) {
        if (name === dir || _parent(name) !== dir) continue;
        entries.push({ name: name.substring(prefix.length), path: name, isDirectory: true, size: 0, modifiedMs: 0 });
      }
      return entries;
    },
    async ensureDirectory(path) {
      const dir = _normalize(path);
      ensureParents(`${dir}/x`);
    },
    directories: async () => ({ ..._directories }),
    async setLastModified(path, modifiedMs) {
      const file = tree.get(_normalize(path));
      if (file != null) file.modifiedMs = modifiedMs;
    },
    joinPath: (...segments) =>
      segments
        .filter((s) => s.length > 0)
        .map((s, i) => (i === 0 ? s.replace(/\/+$/, '') : s.replace(/^\/+|\/+$/g, '')))
        .join('/'),
    // Nothing on the page maps to a local file the OS could open.
    openPath: async () => false,
    showInFolder: async () => {},
    async saveAsDownload({ bytes, filename, mimeType }) {
      const blob = new Blob([bytes as Uint8Array<ArrayBuffer>], { type: mimeType ?? 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      anchor.style.display = 'none';
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      // Give the browser time to start the download before revoking.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
  };
}
