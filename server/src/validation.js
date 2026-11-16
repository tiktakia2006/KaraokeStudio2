import { badRequest } from './http/errors.js';

const fail = (field, message) => badRequest(`${field}: ${message}`, { field });

export function requireObject(value, field = 'body') {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw fail(field, 'очікується об’єкт');
  return value;
}

export function requireString(value, field, { min = 1, max = 500, pattern, trim = true } = {}) {
  if (typeof value !== 'string') throw fail(field, 'очікується рядок');
  const result = trim ? value.trim() : value;
  if (result.length < min) throw fail(field, `мінімальна довжина ${min}`);
  if (result.length > max) throw fail(field, `максимальна довжина ${max}`);
  if (pattern && !pattern.test(result)) throw fail(field, 'недопустимий формат');
  return result;
}

export function optionalString(value, field, options = {}) {
  if (value === undefined || value === null || value === '') return undefined;
  return requireString(value, field, options);
}

export function requireInt(value, field, { min = Number.MIN_SAFE_INTEGER, max = Number.MAX_SAFE_INTEGER } = {}) {
  if (!Number.isInteger(value)) throw fail(field, 'очікується ціле число');
  if (value < min || value > max) throw fail(field, `значення має бути від ${min} до ${max}`);
  return value;
}

export function parseQueryInt(raw, field, { min, max, fallback }) {
  if (raw === null || raw === undefined || raw === '') return fallback;
  if (!/^-?\d+$/.test(raw)) throw fail(field, 'очікується ціле число');
  return requireInt(Number.parseInt(raw, 10), field, { min, max });
}

export function requireHttpsUrl(value, field) {
  const text = requireString(value, field, { max: 1000 });
  let parsed;
  try {
    parsed = new URL(text);
  } catch {
    throw fail(field, 'некоректна адреса');
  }
  if (parsed.protocol !== 'https:') throw fail(field, 'дозволені лише https-адреси');
  return text;
}

export const USERNAME_PATTERN = /^[a-z0-9][a-z0-9_.-]{2,23}$/;
export const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{1,59}$/;
