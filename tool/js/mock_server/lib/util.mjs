// Small, dependency-free helpers shared by the mock server modules.
import { randomBytes } from 'node:crypto';

let idCounter = 0;

/**
 * Monotonic ids that also sort lexicographically in creation order
 * (`msg_0000002s`). The client breaks timestamp ties with `id.compareTo`, so
 * newer entities must compare greater.
 */
export function nextId(prefix) {
  idCounter += 1;
  return `${prefix}_${idCounter.toString(36).padStart(8, '0')}`;
}

/** An opaque random token such as `mock-at_3n2...`. */
export function randomToken(prefix, bytes = 24) {
  return `${prefix}_${randomBytes(bytes).toString('base64url')}`;
}

export const iso = (ms) => new Date(ms).toISOString();
export const unixSeconds = (ms) => Math.floor(ms / 1000);

export const SECOND = 1000;
export const MINUTE = 60 * SECOND;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

/** Deterministic PRNG (mulberry32) so seeded data is stable across restarts. */
export function createRng(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function pick(rng, list) {
  return list[Math.floor(rng() * list.length)];
}

export function randomInt(rng, min, max) {
  return min + Math.floor(rng() * (max - min + 1));
}

/** Parses an integer query/body value, clamped to [min, max]. */
export function clampInt(value, { min = 0, max = Number.MAX_SAFE_INTEGER, fallback }) {
  const parsed = typeof value === 'number' ? value : Number.parseInt(String(value ?? ''), 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, Math.trunc(parsed)));
}

/** Offset-cursor paging: the cursor is the stringified offset of the next page. */
export function paginateByCursor(items, { cursor, limit }) {
  const offset = Math.max(0, Number.parseInt(cursor ?? '0', 10) || 0);
  const page = items.slice(offset, offset + limit);
  const nextCursor = offset + limit < items.length ? String(offset + limit) : null;
  return { page, nextCursor, total: items.length };
}

/** 1-based page paging, shaped like the playlist endpoints' `pagination` object. */
export function paginateByPage(items, { page, pageSize }) {
  const start = (page - 1) * pageSize;
  return {
    page: items.slice(start, start + pageSize),
    pagination: { page, page_size: pageSize, total: items.length, has_more: start + pageSize < items.length },
  };
}

export function foldedIncludes(haystack, needle) {
  if (!needle) return true;
  return String(haystack ?? '').toLowerCase().includes(String(needle).toLowerCase());
}

export function isEmailLike(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(value ?? '').trim());
}

export function maskEmail(email) {
  const [local, domain] = String(email ?? '').split('@');
  if (!domain) return '***';
  return `${local.slice(0, 1)}***@${domain}`;
}

/** Reorders `items` (objects with `id`) so `ids` come first in the given order. */
export function moveIdsToFront(items, ids) {
  const wanted = ids.filter((id) => items.some((item) => item.id === id));
  const front = wanted.map((id) => items.find((item) => item.id === id));
  return [...front, ...items.filter((item) => !wanted.includes(item.id))];
}

/** Moves the item with `id` one step `up` or `down`. Returns a new array. */
export function moveOneStep(items, id, direction) {
  const index = items.findIndex((item) => item.id === id);
  if (index < 0) return items;
  const target = direction === 'up' ? index - 1 : index + 1;
  if (target < 0 || target >= items.length) return items;
  const next = [...items];
  [next[index], next[target]] = [next[target], next[index]];
  return next;
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
