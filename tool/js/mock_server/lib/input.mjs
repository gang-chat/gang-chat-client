// Request-body accessors with the validation errors the real API would send.
import { badRequest, forbidden } from './http.mjs';

export function requiredString(body, key, { max = 500, message } = {}) {
  const value = typeof body?.[key] === 'string' ? body[key].trim() : '';
  if (!value) throw badRequest(message ?? `${key} is required`, 'validation_failed');
  if (value.length > max) throw badRequest(`${key} is too long`, 'validation_failed');
  return value;
}

/** `undefined` when absent (leave unchanged), otherwise the trimmed string. */
export function optionalString(body, key) {
  const value = body?.[key];
  if (value === undefined || value === null) return undefined;
  return String(value).trim();
}

export function optionalBool(body, key) {
  const value = body?.[key];
  return typeof value === 'boolean' ? value : undefined;
}

export function stringList(body, key) {
  const value = body?.[key];
  return Array.isArray(value) ? value.filter((item) => typeof item === 'string' && item.length > 0) : [];
}

export function oneOf(value, allowed, key) {
  if (value === undefined) return undefined;
  if (!allowed.includes(value)) throw badRequest(`${key} must be one of ${allowed.join(', ')}`, 'validation_failed');
  return value;
}

export function requireSuperuser(user) {
  if (!user.isSuperuser) throw forbidden('super user required');
}
