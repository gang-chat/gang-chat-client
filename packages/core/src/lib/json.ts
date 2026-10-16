/**
 * JSON helpers mirroring the Dart casts used by the Flutter models
 * (`json['x'] as String?`, `(json['list'] as List).cast<Map<...>>()`, ...).
 *
 * The `as*` helpers are lenient like the Dart code's `as T?` reads: a missing
 * or `null` value yields `null`; a wrong type yields `null` too instead of
 * throwing, unless the `require*` variant is used.
 */
export type JsonMap = Record<string, unknown>;
export type JsonValue = unknown;

export function isJsonMap(value: unknown): value is JsonMap {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function asString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

export function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** Dart `(json['x'] as num?)?.toInt()`. */
export function asInt(value: unknown): number | null {
  const n = asNumber(value);
  return n == null ? null : Math.trunc(n);
}

export function asBool(value: unknown): boolean | null {
  return typeof value === 'boolean' ? value : null;
}

export function asMap(value: unknown): JsonMap | null {
  return isJsonMap(value) ? value : null;
}

export function asList(value: unknown): unknown[] | null {
  return Array.isArray(value) ? value : null;
}

/** `(json['x'] as List?)?.cast<Map<String, Object?>>().map(f).toList() ?? []`. */
export function mapList<T>(value: unknown, parse: (json: JsonMap) => T): T[] {
  const list = asList(value);
  if (list == null) return [];
  const result: T[] = [];
  for (const item of list) {
    if (isJsonMap(item)) result.push(parse(item));
  }
  return result;
}

export function stringList(value: unknown): string[] {
  const list = asList(value);
  if (list == null) return [];
  return list.filter((item): item is string => typeof item === 'string');
}

/** `DateTime.parse(value)`; returns null for missing or invalid input. */
export function asDate(value: unknown): Date | null {
  if (typeof value !== 'string' || value.length === 0) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export class JsonFormatException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'FormatException';
  }
}

export function requireString(json: JsonMap, key: string): string {
  const value = json[key];
  if (typeof value !== 'string') throw new JsonFormatException(`Expected string for "${key}"`);
  return value;
}

export function requireNumber(json: JsonMap, key: string): number {
  const value = json[key];
  if (typeof value !== 'number') throw new JsonFormatException(`Expected number for "${key}"`);
  return value;
}

export function requireInt(json: JsonMap, key: string): number {
  return Math.trunc(requireNumber(json, key));
}

export function requireBool(json: JsonMap, key: string): boolean {
  const value = json[key];
  if (typeof value !== 'boolean') throw new JsonFormatException(`Expected bool for "${key}"`);
  return value;
}

export function requireMap(json: JsonMap, key: string): JsonMap {
  const value = json[key];
  if (!isJsonMap(value)) throw new JsonFormatException(`Expected object for "${key}"`);
  return value;
}

export function requireDate(json: JsonMap, key: string): Date {
  const date = asDate(json[key]);
  if (date == null) throw new JsonFormatException(`Expected ISO date for "${key}"`);
  return date;
}

/** Dart `DateTime.toIso8601String()` for a UTC instant. */
export function toIsoString(date: Date): string {
  return date.toISOString();
}
