import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { hashPassword, verifyPassword } from '../src/security/password.js';
import { signToken, verifyToken } from '../src/security/jwt.js';
import { RateLimiter } from '../src/http/rateLimiter.js';
import { startServer } from './helpers.js';

describe('захист і обробка запитів', () => {
  it('обмежує частоту автентифікації', async () => {
    const s = await startServer({ config: { rateLimit: { authMax: 3 } } });
    const attempt = () => s.request('POST', '/api/v1/auth/login', { body: { username: 'nobody', password: 'password123' } });
    for (let i = 0; i < 3; i += 1) assert.equal((await attempt()).status, 401);
    const blocked = await attempt();
    assert.equal(blocked.status, 429);
    assert.ok(Number(blocked.headers.get('retry-after')) >= 1);
    assert.equal(blocked.json.error.code, 'rate_limited');
    await s.close();
  });

  it('обмежує загальну частоту, але не для /health', async () => {
    const s = await startServer({ config: { rateLimit: { max: 5 } } });
    for (let i = 0; i < 5; i += 1) assert.equal((await s.request('GET', '/api/v1/meta')).status, 200);
    assert.equal((await s.request('GET', '/api/v1/meta')).status, 429);
    for (let i = 0; i < 10; i += 1) assert.equal((await s.request('GET', '/health')).status, 200);
    await s.close();
  });

  it('повертає узгоджені помилки маршрутизації', async () => {
    const s = await startServer();
    const missing = await s.request('GET', '/api/v1/nothing');
    assert.equal(missing.status, 404);
    assert.equal(missing.json.error.code, 'not_found');
    assert.ok(missing.json.requestId);
    assert.equal(missing.headers.get('x-request-id'), missing.json.requestId);
    const wrongMethod = await s.request('DELETE', '/api/v1/songs');
    assert.equal(wrongMethod.status, 405);
    assert.match(wrongMethod.headers.get('allow'), /GET/);
    assert.equal((await s.request('GET', '/api/v1/songs/%E0%A4%A')).status, 400);
    assert.equal(missing.headers.get('x-content-type-options'), 'nosniff');
    await s.close();
  });

  it('відхиляє завеликі тіла, неправильний тип і некоректний JSON', async () => {
    const s = await startServer({ config: { maxBodyBytes: 120 } });
    const large = await s.request('POST', '/api/v1/auth/login', { body: { username: 'x'.repeat(300), password: 'y' } });
    assert.equal(large.status, 413);
    const wrongType = await s.request('POST', '/api/v1/auth/login', {
      raw: 'username=a',
      headers: { 'Content-Type': 'text/plain' },
    });
    assert.equal(wrongType.status, 415);
    const broken = await s.request('POST', '/api/v1/auth/login', {
      raw: '{broken',
      headers: { 'Content-Type': 'application/json' },
    });
    assert.equal(broken.status, 400);
    await s.close();
  });

  it('перевіряє стан сервера', async () => {
    const s = await startServer();
    const health = await s.request('GET', '/health');
    assert.equal(health.json.status, 'ok');
    const meta = await s.request('GET', '/api/v1/meta');
    assert.equal(meta.json.songs, 10);
    await s.close();
  });
});

describe('криптографія', () => {
  it('хешує паролі з унікальною сіллю й перевіряє їх', () => {
    const first = hashPassword('secret-pass');
    const second = hashPassword('secret-pass');
    assert.notEqual(first, second);
    assert.ok(verifyPassword('secret-pass', first));
    assert.ok(!verifyPassword('other-pass', first));
    assert.ok(!verifyPassword('secret-pass', 'garbage'));
  });

  it('підписує та перевіряє токени', () => {
    const now = 1_700_000_000_000;
    const token = signToken({ sub: 7 }, 'k', 60, now);
    assert.equal(verifyToken(token, 'k', now + 1000).sub, 7);
    assert.equal(verifyToken(token, 'k', now + 61_000), null);
    assert.equal(verifyToken(token, 'wrong', now), null);
    assert.equal(verifyToken('a.b', 'k', now), null);
    assert.equal(verifyToken(undefined, 'k', now), null);
  });

  it('відхиляє токен з алгоритмом none', () => {
    const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
    const payload = Buffer.from(JSON.stringify({ sub: 1, exp: 9_999_999_999 })).toString('base64url');
    assert.equal(verifyToken(`${header}.${payload}.`, 'k'), null);
  });

  it('скидає вікно обмеження частоти', () => {
    let current = 0;
    const limiter = new RateLimiter(1000, () => current);
    assert.ok(limiter.hit('a', 2).allowed);
    assert.ok(limiter.hit('a', 2).allowed);
    assert.ok(!limiter.hit('a', 2).allowed);
    current = 1001;
    assert.ok(limiter.hit('a', 2).allowed);
  });
});
