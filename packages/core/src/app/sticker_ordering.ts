import type { Sticker, StickerPack } from '../protocol/models';

export class StickerPlacementData {
  constructor(opts: { pack: StickerPack; sticker: Sticker; index: number; total: number }) {
    this.pack = opts.pack;
    this.sticker = opts.sticker;
    this.index = opts.index;
    this.total = opts.total;
  }

  readonly pack: StickerPack;
  readonly sticker: Sticker;
  readonly index: number;
  readonly total: number;

  get canMoveUp(): boolean {
    return this.index > 0;
  }

  get canMoveDown(): boolean {
    return this.index < this.total - 1;
  }

  get canPin(): boolean {
    return this.index > 0;
  }
}

export type StickerOrderForPack = (pack: StickerPack) => readonly string[] | null | undefined;

export function orderedStickers(pack: StickerPack, opts: { order?: readonly string[] | null } = {}): Sticker[] {
  const remaining = new Map<string, Sticker>();
  for (const sticker of pack.stickers) remaining.set(sticker.id, sticker);
  const targetOrder = opts.order ?? pack.stickers.map((sticker) => sticker.id);
  const ordered: Sticker[] = [];
  for (const stickerId of targetOrder) {
    const sticker = remaining.get(stickerId);
    if (sticker === undefined) continue;
    remaining.delete(stickerId);
    ordered.push(sticker);
  }
  ordered.push(...remaining.values());
  return ordered;
}

export function orderedStickerIds(pack: StickerPack, opts: { order?: readonly string[] | null } = {}): string[] {
  return orderedStickers(pack, { order: opts.order }).map((sticker) => sticker.id);
}

export function stickerSortOrdersBeforeExisting(pack: StickerPack, count: number): number[] {
  if (count <= 0) return [];
  if (pack.stickers.length === 0) {
    return Array.from({ length: count }, (_, index) => (index + 1) * 10);
  }
  let minSortOrder = pack.stickers[0]!.sortOrder;
  for (const sticker of pack.stickers.slice(1)) {
    if (sticker.sortOrder < minSortOrder) {
      minSortOrder = sticker.sortOrder;
    }
  }
  const firstSortOrder = minSortOrder - count * 10;
  return Array.from({ length: count }, (_, index) => firstSortOrder + index * 10);
}

export function stickerPackById(packs: readonly StickerPack[], packId: string): StickerPack | null {
  for (const pack of packs) {
    if (pack.id === packId) return pack;
  }
  return null;
}

export function stickerPackForSticker(packs: readonly StickerPack[], stickerId: string): StickerPack | null {
  for (const pack of packs) {
    if (pack.stickers.some((sticker) => sticker.id === stickerId)) return pack;
  }
  return null;
}

export function stickerPlacement(
  packs: readonly StickerPack[],
  stickerId: string,
  opts: { orderForPack?: StickerOrderForPack | null } = {},
): StickerPlacementData | null {
  for (const pack of packs) {
    const ordered = orderedStickers(pack, { order: opts.orderForPack?.(pack) });
    const index = ordered.findIndex((sticker) => sticker.id === stickerId);
    if (index < 0) continue;
    return new StickerPlacementData({
      pack,
      sticker: ordered[index]!,
      index,
      total: ordered.length,
    });
  }
  return null;
}

export function movedStickerOrder(
  pack: StickerPack,
  stickerId: string,
  delta: number,
  opts: { order?: readonly string[] | null } = {},
): string[] | null {
  const ids = orderedStickerIds(pack, { order: opts.order });
  const from = ids.indexOf(stickerId);
  if (from < 0) return null;
  const to = Math.trunc(Math.min(Math.max(from + delta, 0), ids.length - 1));
  if (from === to) return null;
  const [moving] = ids.splice(from, 1);
  ids.splice(to, 0, moving!);
  return ids;
}

export function pinnedStickerOrder(
  pack: StickerPack,
  stickerId: string,
  opts: { order?: readonly string[] | null } = {},
): string[] | null {
  const ids = orderedStickerIds(pack, { order: opts.order });
  const index = ids.indexOf(stickerId);
  if (index <= 0) return null;
  const [moving] = ids.splice(index, 1);
  ids.splice(0, 0, moving!);
  return ids;
}

export function stickerOrderWithStickerIdsPinnedToFront(
  pack: StickerPack,
  stickerIds: readonly string[],
  opts: { order?: readonly string[] | null } = {},
): string[] | null {
  if (stickerIds.length === 0) return null;
  const pinnedSet = new Set(stickerIds);
  const currentOrder = orderedStickerIds(pack, { order: opts.order });
  const nextOrder = [
    ...stickerIds.filter((stickerId) => currentOrder.includes(stickerId)),
    ...currentOrder.filter((stickerId) => !pinnedSet.has(stickerId)),
  ];
  if (_sameStringList(currentOrder, nextOrder)) return null;
  return nextOrder;
}

export function stickerSelectionWouldChangePinnedOrder(opts: {
  packs: readonly StickerPack[];
  selectedStickerIds: readonly string[];
  orderByPack?: ReadonlyMap<string, readonly string[]>;
}): boolean {
  const { packs, selectedStickerIds, orderByPack = new Map<string, readonly string[]>() } = opts;
  const selectedByPack = selectedStickerIdsByPack(packs, selectedStickerIds);
  for (const pack of packs) {
    const selectedInPack = selectedByPack.get(pack.id);
    if (selectedInPack == null || selectedInPack.length === 0) continue;
    if (
      stickerOrderWithStickerIdsPinnedToFront(pack, selectedInPack, {
        order: orderByPack.get(pack.id),
      }) != null
    ) {
      return true;
    }
  }
  return false;
}

/** Insertion-ordered like the Dart `LinkedHashMap` it replaces. */
export function selectedStickerIdsByPack(
  packs: readonly StickerPack[],
  selectedIds: readonly string[],
): Map<string, string[]> {
  const selectedByPack = new Map<string, string[]>();
  for (const stickerId of selectedIds) {
    const pack = stickerPackForSticker(packs, stickerId);
    if (pack == null) continue;
    let ids = selectedByPack.get(pack.id);
    if (ids === undefined) {
      ids = [];
      selectedByPack.set(pack.id, ids);
    }
    ids.push(stickerId);
  }
  return selectedByPack;
}

function _sameStringList(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i += 1) {
    if (a[i] !== b[i]) return false;
  }
  return true;
}
