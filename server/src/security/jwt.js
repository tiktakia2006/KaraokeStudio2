import { createHmac, timingSafeEqual } from 'node:crypto';

const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const sign = (data, secret) => createHmac('sha256', secret).update(data).digest('base64url');

export function signToken(claims, secret, ttlSeconds, nowMs = Date.now()) {
  const issuedAt = Math.floor(nowMs / 1000);
  const header = encode({ alg: 'HS256', typ: 'JWT' });
  const payload = encode({ ...claims, iat: issuedAt, exp: issuedAt + ttlSeconds });
  return `${header}.${payload}.${sign(`${header}.${payload}`, secret)}`;
}

export function verifyToken(token, secret, nowMs = Date.now()) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [header, payload, signature] = parts;
  let parsedHeader;
  let claims;
  try {
    parsedHeader = JSON.parse(Buffer.from(header, 'base64url').toString('utf8'));
    claims = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
  if (parsedHeader.alg !== 'HS256') return null;
  const expected = Buffer.from(sign(`${header}.${payload}`, secret));
  const actual = Buffer.from(signature);
  if (expected.length !== actual.length || !timingSafeEqual(expected, actual)) return null;
  if (typeof claims.exp !== 'number' || claims.exp * 1000 <= nowMs) return null;
  return claims;
}
