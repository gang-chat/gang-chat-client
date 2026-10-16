import { BrowserWindow, dialog, ipcMain, shell, type FileFilter } from 'electron';
import fs from 'node:fs/promises';
import path from 'node:path';

import { IpcChannels, type FileEntry, type PickedFile } from '../shared/ipc';
import { mimeTypeForPath } from './mime';

/**
 * File dialogs and file-system access (the `file_selector`, `path_provider`
 * and `dart:io` File/Directory calls of the Flutter client).
 */

interface TypeGroup {
  label?: string;
  extensions?: string[];
  mimeTypes?: string[];
}

function filtersFor(groups: TypeGroup[] | undefined): FileFilter[] | undefined {
  if (!groups || groups.length === 0) return undefined;
  const filters: FileFilter[] = [];
  for (const group of groups) {
    const extensions = (group.extensions ?? []).map((e) => e.replace(/^\./, ''));
    if (extensions.length === 0) continue;
    filters.push({ name: group.label ?? extensions.join(', '), extensions });
  }
  return filters.length > 0 ? filters : undefined;
}

async function picked(paths: string[]): Promise<PickedFile[]> {
  const result: PickedFile[] = [];
  for (const filePath of paths) {
    try {
      const stat = await fs.stat(filePath);
      if (!stat.isFile()) continue;
      result.push({ name: path.basename(filePath), path: filePath, size: stat.size, mimeType: mimeTypeForPath(filePath) });
    } catch {
      // file vanished between pick and stat
    }
  }
  return result;
}

type Handler = (window: BrowserWindow | null, ...args: never[]) => Promise<unknown>;

const handlers: Record<string, Handler> = {
  async openFiles(window, options: { acceptedTypeGroups?: TypeGroup[]; confirmButtonText?: string; multiple: boolean }) {
    const dialogOptions: Electron.OpenDialogOptions = {
      properties: options.multiple ? ['openFile', 'multiSelections'] : ['openFile'],
      filters: filtersFor(options.acceptedTypeGroups),
      buttonLabel: options.confirmButtonText,
    };
    const result = window ? await dialog.showOpenDialog(window, dialogOptions) : await dialog.showOpenDialog(dialogOptions);
    if (result.canceled) return [];
    return picked(result.filePaths);
  },

  async getSaveLocation(
    window,
    options: { suggestedName: string; acceptedTypeGroups?: TypeGroup[]; confirmButtonText?: string; defaultDirectory?: string },
  ) {
    const dialogOptions: Electron.SaveDialogOptions = {
      defaultPath: options.defaultDirectory ? path.join(options.defaultDirectory, options.suggestedName) : options.suggestedName,
      filters: filtersFor(options.acceptedTypeGroups),
      buttonLabel: options.confirmButtonText,
    };
    const result = window ? await dialog.showSaveDialog(window, dialogOptions) : await dialog.showSaveDialog(dialogOptions);
    if (result.canceled || !result.filePath) return null;
    return result.filePath;
  },

  async filesFromPaths(_window, paths: string[]) {
    return picked(paths);
  },

  async readFile(_window, filePath: string) {
    const buffer = await fs.readFile(filePath);
    return new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
  },

  async readFileChunk(_window, filePath: string, offset: number, length: number) {
    const handle = await fs.open(filePath, 'r');
    try {
      const buffer = Buffer.alloc(length);
      const { bytesRead } = await handle.read(buffer, 0, length, offset);
      return new Uint8Array(buffer.buffer, buffer.byteOffset, bytesRead);
    } finally {
      await handle.close();
    }
  },

  async setLastModified(_window, filePath: string, modifiedMs: number) {
    const time = new Date(modifiedMs);
    await fs.utimes(filePath, time, time);
  },

  async writeFile(_window, filePath: string, bytes: Uint8Array) {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, bytes);
  },

  async appendFile(_window, filePath: string, bytes: Uint8Array) {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.appendFile(filePath, bytes);
  },

  async deleteFile(_window, filePath: string) {
    await fs.rm(filePath, { force: true });
  },

  async rename(_window, from: string, to: string) {
    await fs.mkdir(path.dirname(to), { recursive: true });
    await fs.rename(from, to);
  },

  async exists(_window, filePath: string) {
    try {
      await fs.access(filePath);
      return true;
    } catch {
      return false;
    }
  },

  async fileSize(_window, filePath: string) {
    try {
      const stat = await fs.stat(filePath);
      return stat.isFile() ? stat.size : null;
    } catch {
      return null;
    }
  },

  async listDirectory(_window, dirPath: string) {
    let names: string[];
    try {
      names = await fs.readdir(dirPath);
    } catch {
      return [];
    }
    const entries: FileEntry[] = [];
    for (const name of names) {
      const full = path.join(dirPath, name);
      try {
        const stat = await fs.stat(full);
        entries.push({ name, path: full, isDirectory: stat.isDirectory(), size: stat.size, modifiedMs: stat.mtimeMs });
      } catch {
        // skip unreadable entries
      }
    }
    return entries;
  },

  async ensureDirectory(_window, dirPath: string) {
    await fs.mkdir(dirPath, { recursive: true });
  },

  async openPath(_window, filePath: string) {
    const error = await shell.openPath(filePath);
    return error.length === 0;
  },

  async showInFolder(_window, filePath: string) {
    shell.showItemInFolder(filePath);
  },
};

export function registerFiles(): void {
  ipcMain.handle(IpcChannels.filesInvoke, async (event, op: string, ...args: unknown[]) => {
    const handler = handlers[op];
    if (!handler) throw new Error(`Unknown files operation: ${op}`);
    const window = BrowserWindow.fromWebContents(event.sender);
    return (handler as (window: BrowserWindow | null, ...rest: unknown[]) => Promise<unknown>)(window, ...args);
  });
}
