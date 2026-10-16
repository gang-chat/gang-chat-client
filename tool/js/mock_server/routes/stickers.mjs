// Sticker packs (personal and room scope), stickers, bulk download (ZIP) and
// saving a sticker seen in chat into one of my packs.
import { badRequest, binary, created, forbidden, notFound, ok } from '../lib/http.mjs';
import { optionalString, oneOf, requiredString, stringList } from '../lib/input.mjs';
import { buildZip } from '../lib/media.mjs';
import { route } from '../lib/router.mjs';
import { clampInt, moveIdsToFront, nextId } from '../lib/util.mjs';

/** Read access: my personal packs (superusers: anyone's), or packs of a room I belong to. */
function assertCanRead(c, pack) {
  if (pack.scope === 'room') return c.store.requireMember(c.store.requireRoom(pack.roomId), c.user);
  if (pack.ownerUserId !== c.user.id && !c.user.isSuperuser) throw forbidden('sticker pack not found');
}

/** Write access: my personal packs, or room packs as a room admin. */
function assertCanWrite(c, pack) {
  if (pack.scope === 'room') return c.store.requireAdmin(c.store.requireRoom(pack.roomId), c.user);
  if (pack.ownerUserId !== c.user.id && !c.user.isSuperuser) throw forbidden('sticker pack not found');
}

function requirePack(c, packId, { write = false } = {}) {
  const pack = c.store.stickerPacks.get(packId);
  if (!pack) throw notFound('sticker pack not found');
  if (write) assertCanWrite(c, pack);
  else assertCanRead(c, pack);
  return pack;
}

function packsFor(c, { scope, roomId, ownerUserId }) {
  return [...c.store.stickerPacks.values()]
    .filter((pack) => (scope === 'room' ? pack.scope === 'room' && pack.roomId === roomId : pack.scope === 'personal' && pack.ownerUserId === ownerUserId))
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

function listPacks(c) {
  const scope = oneOf(c.query.get('scope') ?? 'personal', ['personal', 'room'], 'scope');
  const roomId = c.query.get('room_id');
  const ownerUserId = c.query.get('owner_user_id') || c.user.id;
  if (scope === 'room') c.store.requireMember(c.store.requireRoom(roomId), c.user);
  else if (ownerUserId !== c.user.id && !c.user.isSuperuser) throw forbidden('super user required');
  return { packs: packsFor(c, { scope, roomId, ownerUserId }).map((pack) => c.store.stickerPackJson(pack)) };
}

function createPack(c) {
  const scope = oneOf(c.body.scope ?? 'personal', ['personal', 'room'], 'scope');
  const roomId = scope === 'room' ? String(c.body.room_id ?? '') : null;
  const ownerUserId = scope === 'personal' ? c.body.owner_user_id || c.user.id : null;
  const pack = { id: nextId('pack'), scope, roomId, ownerUserId, name: requiredString(c.body, 'name', { max: 30 }), stickers: [], updatedAt: Date.now() };
  assertCanWrite(c, pack);
  const siblings = packsFor(c, { scope, roomId, ownerUserId });
  pack.sortOrder = c.body.sort_order ?? (siblings.length ? siblings[siblings.length - 1].sortOrder + 10 : 10);
  c.store.stickerPacks.set(pack.id, pack);
  return created({ pack: c.store.stickerPackJson(pack) });
}

function updatePack(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  const name = optionalString(c.body, 'name');
  if (name) pack.name = name;
  if (typeof c.body.sort_order === 'number') pack.sortOrder = c.body.sort_order;
  pack.updatedAt = Date.now();
  return { pack: c.store.stickerPackJson(pack) };
}

function deletePack(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  c.store.stickerPacks.delete(pack.id);
  return ok();
}

function nextSortOrder(pack) {
  return pack.stickers.reduce((max, sticker) => Math.max(max, sticker.sortOrder), 0) + 10;
}

/** Adding the same asset twice is idempotent (the client retries and verifies by asset id). */
function addSticker(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  const assetId = requiredString(c.body, 'asset_id');
  const asset = c.store.assets.get(assetId);
  if (!asset) throw notFound('asset not found');
  if (!asset.mimeType.startsWith('image/')) throw badRequest('image file is required');
  let sticker = pack.stickers.find((candidate) => candidate.assetId === assetId);
  if (!sticker) {
    sticker = {
      id: nextId('stk'),
      name: optionalString(c.body, 'name') || 'sticker',
      sortOrder: typeof c.body.sort_order === 'number' ? c.body.sort_order : nextSortOrder(pack),
      assetId,
    };
    pack.stickers.push(sticker);
    pack.updatedAt = Date.now();
  }
  return created({ sticker: c.store.stickerJson(sticker) });
}

function requireSticker(pack, stickerId) {
  const sticker = pack.stickers.find((candidate) => candidate.id === stickerId);
  if (!sticker) throw notFound('sticker not found');
  return sticker;
}

function updateSticker(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  const sticker = requireSticker(pack, c.params.stickerId);
  const name = optionalString(c.body, 'name');
  if (name) sticker.name = name;
  if (typeof c.body.sort_order === 'number') sticker.sortOrder = c.body.sort_order;
  pack.updatedAt = Date.now();
  return { sticker: c.store.stickerJson(sticker) };
}

function deleteSticker(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  requireSticker(pack, c.params.stickerId);
  pack.stickers = pack.stickers.filter((sticker) => sticker.id !== c.params.stickerId);
  pack.updatedAt = Date.now();
  return ok();
}

function reorderStickers(c) {
  const pack = requirePack(c, c.params.packId, { write: true });
  const ordered = moveIdsToFront([...pack.stickers].sort((a, b) => a.sortOrder - b.sortOrder), stringList(c.body, 'sticker_ids'));
  ordered.forEach((sticker, index) => {
    sticker.sortOrder = (index + 1) * 10;
  });
  pack.updatedAt = Date.now();
  return { pack: c.store.stickerPackJson(pack) };
}

/** GET /stickers/download?ids=a,b — one sticker as its image, several as a ZIP. */
function downloadStickers(c) {
  const ids = String(c.query.get('ids') ?? '').split(',').filter(Boolean);
  const found = ids.map((id) => c.store.findSticker(id)).filter(Boolean);
  if (found.length === 0) throw notFound('sticker not found');
  const files = found.map(({ pack, sticker }) => {
    assertCanRead(c, pack);
    const asset = c.store.assets.get(sticker.assetId);
    const ext = asset.mimeType.split('/')[1]?.replace('jpeg', 'jpg') ?? 'png';
    return { name: `${sticker.name}.${ext}`, data: asset.bytes ?? Buffer.alloc(0), mimeType: asset.mimeType };
  });
  if (files.length === 1) {
    return binary({ contentType: files[0].mimeType, body: files[0].data, filename: files[0].name, disposition: 'attachment' });
  }
  const unique = files.map((file, index) => ({ ...file, name: `${String(index + 1).padStart(2, '0')}-${file.name}` }));
  return binary({ contentType: 'application/zip', body: buildZip(unique), filename: 'stickers.zip', disposition: 'attachment' });
}

/** POST /rooms/:id/stickers/save — copies a sticker from chat into my (or the room's) pack. */
function saveSticker(c) {
  const room = c.store.requireRoom(c.params.roomId);
  c.store.requireMember(room, c.user);
  const stickerId = requiredString(c.body, 'sticker_id');
  const known = c.store.findSticker(stickerId);
  let assetId = known?.sticker.assetId;
  let name = known?.sticker.name;
  if (!assetId && c.body.source_message_id) {
    const message = c.store.findMessage(room, c.body.source_message_id);
    const attachment = message.attachments.find((item) => item.type === 'sticker' && item.stickerId === stickerId);
    assetId = attachment?.assetId;
    name = attachment?.name;
  }
  if (!assetId || !c.store.assets.has(assetId)) throw notFound('sticker file not found', 'sticker_asset_expired');

  const targetScope = oneOf(c.body.target_scope ?? 'personal', ['personal', 'room'], 'target_scope');
  let pack = c.body.target_pack_id ? requirePack(c, c.body.target_pack_id, { write: true }) : null;
  if (!pack) {
    const scopeQuery = targetScope === 'room' ? { scope: 'room', roomId: room.id } : { scope: 'personal', ownerUserId: c.user.id };
    pack = packsFor(c, scopeQuery)[0];
    if (!pack) {
      pack = { id: nextId('pack'), scope: targetScope, roomId: scopeQuery.roomId ?? null, ownerUserId: scopeQuery.ownerUserId ?? null, name: '我的表情', sortOrder: 10, stickers: [], updatedAt: Date.now() };
      assertCanWrite(c, pack);
      c.store.stickerPacks.set(pack.id, pack);
    }
    assertCanWrite(c, pack);
  }
  if (!pack.stickers.some((sticker) => sticker.assetId === assetId)) {
    pack.stickers.push({
      id: nextId('stk'),
      name: optionalString(c.body, 'name') || name || 'sticker',
      sortOrder: clampInt(c.body.sort_order, { min: 0, fallback: nextSortOrder(pack) }),
      assetId,
    });
    pack.updatedAt = Date.now();
  }
  return { pack: c.store.stickerPackJson(pack) };
}

export const stickerRoutes = [
  route('GET', '/sticker-packs', listPacks),
  route('POST', '/sticker-packs', createPack),
  route('PATCH', '/sticker-packs/:packId', updatePack),
  route('DELETE', '/sticker-packs/:packId', deletePack),
  route('POST', '/sticker-packs/:packId/stickers', addSticker),
  route('POST', '/sticker-packs/:packId/stickers/reorder', reorderStickers),
  route('PATCH', '/sticker-packs/:packId/stickers/:stickerId', updateSticker),
  route('DELETE', '/sticker-packs/:packId/stickers/:stickerId', deleteSticker),
  route('GET', '/stickers/download', downloadStickers),
  route('POST', '/rooms/:roomId/stickers/save', saveSticker),
];
