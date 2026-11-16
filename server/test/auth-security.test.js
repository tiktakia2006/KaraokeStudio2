import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

describe('сесії, блокування та керування обліковим записом', () => {
  let s;
  let clock;
  before(async () => {
    clock = Date.now();
    s = await startServer({ now: () => clock });
  });
  after(() => s.close());

  const login = (username, password) => s.request('POST', '/api/v1/auth/login', { body: { username, password } });
  const refresh = (refreshToken) => s.request('POST', '/api/v1/auth/refresh', { body: { refreshToken } });

  it('видає пару токенів і термін дії', async () => {
    const { response } = await s.register('session_user');
    assert.equal(response.status, 201);
    assert.ok(response.json.token);
    assert.ok(response.json.refreshToken.length >= 40);
    assert.equal(response.json.expiresIn, 3600);
  });

  it('оновлює сесію й змінює токен оновлення при кожному використанні', async () => {
    const { refreshToken } = await s.register('rotate_user');
    const next = await refresh(refreshToken);
    assert.equal(next.status, 200);
    assert.notEqual(next.json.refreshToken, refreshToken);
    const me = await s.request('GET', '/api/v1/auth/me', { token: next.json.token });
    assert.equal(me.json.user.username, 'rotate_user');
  });

  it('виявляє повторне використання токена оновлення й скасовує всю гілку', async () => {
    const { refreshToken } = await s.register('reuse_user');
    const first = await refresh(refreshToken);
    assert.equal(first.status, 200);
    const replay = await refresh(refreshToken);
    assert.equal(replay.status, 401);
    const afterReplay = await refresh(first.json.refreshToken);
    assert.equal(afterReplay.status, 401);
  });

  it('відхиляє невідомий і прострочений токен оновлення', async () => {
    assert.equal((await refresh('x'.repeat(43))).status, 401);
    assert.equal((await s.request('POST', '/api/v1/auth/refresh', { body: {} })).status, 400);
    const { refreshToken } = await s.register('expire_user');
    clock += 31 * 24 * 3600 * 1000;
    assert.equal((await refresh(refreshToken)).status, 401);
    clock -= 31 * 24 * 3600 * 1000;
  });

  it('вважає доступовий токен простроченим через годину, а оновлення дає новий', async () => {
    const { token, refreshToken } = await s.register('ttl_user');
    clock += 3601 * 1000;
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token })).status, 401);
    const next = await refresh(refreshToken);
    assert.equal(next.status, 200);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token: next.json.token })).status, 200);
  });

  it('блокує обліковий запис після серії невдалих входів і розблоковує за часом', async () => {
    await s.register('lock_user', 'right-password');
    for (let i = 0; i < 5; i += 1) assert.equal((await login('lock_user', 'wrong-password')).status, 401);
    const blocked = await login('lock_user', 'right-password');
    assert.equal(blocked.status, 423);
    assert.equal(blocked.json.error.code, 'account_locked');
    assert.ok(Number(blocked.headers.get('retry-after')) > 0);
    clock += 901 * 1000;
    assert.equal((await login('lock_user', 'right-password')).status, 200);
  });

  it('скидає лічильник невдач після успішного входу', async () => {
    await s.register('counter_user', 'right-password');
    for (let i = 0; i < 4; i += 1) await login('counter_user', 'wrong-password');
    assert.equal((await login('counter_user', 'right-password')).status, 200);
    for (let i = 0; i < 4; i += 1) assert.equal((await login('counter_user', 'wrong-password')).status, 401);
    assert.equal((await login('counter_user', 'right-password')).status, 200);
  });

  it('змінює пароль, анулює старі токени й повертає нову сесію', async () => {
    const { token, refreshToken } = await s.register('pass_user', 'old-password');
    const wrong = await s.request('POST', '/api/v1/auth/password', { token, body: { currentPassword: 'nope-nope', newPassword: 'new-password' } });
    assert.equal(wrong.status, 401);
    const same = await s.request('POST', '/api/v1/auth/password', { token, body: { currentPassword: 'old-password', newPassword: 'old-password' } });
    assert.equal(same.status, 400);
    const short = await s.request('POST', '/api/v1/auth/password', { token, body: { currentPassword: 'old-password', newPassword: '123' } });
    assert.equal(short.status, 400);
    const changed = await s.request('POST', '/api/v1/auth/password', { token, body: { currentPassword: 'old-password', newPassword: 'new-password' } });
    assert.equal(changed.status, 200);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token })).status, 401);
    assert.equal((await refresh(refreshToken)).status, 401);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token: changed.json.token })).status, 200);
    assert.equal((await login('pass_user', 'old-password')).status, 401);
    assert.equal((await login('pass_user', 'new-password')).status, 200);
  });

  it('завершує сесію при виході', async () => {
    const { refreshToken } = await s.register('logout_user');
    const out = await s.request('POST', '/api/v1/auth/logout', { body: { refreshToken } });
    assert.equal(out.status, 204);
    assert.equal((await refresh(refreshToken)).status, 401);
    assert.equal((await s.request('POST', '/api/v1/auth/logout', {})).status, 204);
  });

  it('видаляє обліковий запис разом із даними та дозволяє зайняти ім’я знову', async () => {
    const { token, user } = await s.register('delete_user', 'delete-password');
    await s.request('POST', '/api/v1/sync/push', {
      token,
      body: { changes: [{ type: 'favorite', op: 'upsert', songSlug: 'neon-dance', at: clock }] },
    });
    const wrong = await s.request('DELETE', '/api/v1/auth/me', { token, body: { password: 'wrong-password' } });
    assert.equal(wrong.status, 401);
    const missing = await s.request('DELETE', '/api/v1/auth/me', { token, body: {} });
    assert.equal(missing.status, 400);
    const removed = await s.request('DELETE', '/api/v1/auth/me', { token, body: { password: 'delete-password' } });
    assert.equal(removed.status, 204);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token })).status, 401);
    assert.equal(s.db.prepare('SELECT COUNT(*) AS n FROM favorites WHERE user_id = ?').get(user.id).n, 0);
    assert.equal(s.db.prepare('SELECT COUNT(*) AS n FROM refresh_tokens WHERE user_id = ?').get(user.id).n, 0);
    assert.equal((await s.register('delete_user', 'another-password')).response.status, 201);
  });

  it('віддає експорт даних користувача', async () => {
    const { token } = await s.register('export_user');
    await s.request('POST', '/api/v1/sync/push', {
      token,
      body: {
        changes: [
          { type: 'favorite', op: 'upsert', songSlug: 'night-city', at: clock },
          {
            type: 'performance',
            op: 'upsert',
            clientId: 'export-client-0001',
            songSlug: 'night-city',
            performedAt: clock - 1000,
            durationMs: 50_000,
            score: 77,
            at: clock,
          },
        ],
      },
    });
    const exported = await s.request('GET', '/api/v1/auth/export', { token });
    assert.equal(exported.status, 200);
    assert.deepEqual(exported.json.favorites.map((item) => item.songSlug), ['night-city']);
    assert.equal(exported.json.performances[0].score, 77);
    assert.equal(exported.json.user.password_hash, undefined);
    assert.equal((await s.request('GET', '/api/v1/auth/export')).status, 401);
  });
});
