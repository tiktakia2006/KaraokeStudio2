import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

describe('адміністрування, метрики та службові сторінки', () => {
  let s;
  let admin;
  let user;
  before(async () => {
    s = await startServer({ config: { metricsToken: 'metrics-secret' } });
    admin = (await s.register('admin')).token;
    user = (await s.register('regular_user')).token;
  });
  after(() => s.close());

  it('закриває адміністративні маршрути від гостей і звичайних користувачів', async () => {
    for (const path of ['stats', 'songs', 'users', 'logs']) {
      assert.equal((await s.request('GET', `/api/v1/admin/${path}`)).status, 401);
      assert.equal((await s.request('GET', `/api/v1/admin/${path}`, { token: user })).status, 403);
    }
  });

  it('повертає статистику', async () => {
    const stats = await s.request('GET', '/api/v1/admin/stats', { token: admin });
    assert.equal(stats.status, 200);
    assert.equal(stats.json.songs, 10);
    assert.equal(stats.json.users, 2);
    assert.ok(stats.json.database.sizeBytes > 0);
    assert.equal(stats.json.version, '2.0.0');
  });

  it('показує видалені пісні й дозволяє відновити їх через PUT', async () => {
    const full = await s.request('GET', '/api/v1/songs/star-path');
    await s.request('DELETE', '/api/v1/songs/star-path', { token: admin });
    const hidden = await s.request('GET', '/api/v1/admin/songs?limit=200', { token: admin });
    assert.ok(!hidden.json.items.some((item) => item.slug === 'star-path'));
    const all = await s.request('GET', '/api/v1/admin/songs?includeDeleted=true&limit=200', { token: admin });
    const found = all.json.items.find((item) => item.slug === 'star-path');
    assert.equal(found.deleted, true);
    const one = await s.request('GET', '/api/v1/admin/songs/star-path', { token: admin });
    assert.equal(one.json.deleted, true);
    assert.ok(one.json.lyrics.length > 0);
    const { deleted, updatedAt, ...input } = full.json;
    const restored = await s.request('PUT', '/api/v1/songs/star-path', { token: admin, body: input });
    assert.equal(restored.status, 200);
    assert.equal((await s.request('GET', '/api/v1/songs/star-path')).status, 200);
    assert.equal((await s.request('GET', '/api/v1/admin/songs/missing-song', { token: admin })).status, 404);
  });

  it('фільтрує пісні за пошуком і сторінками', async () => {
    const found = await s.request('GET', `/api/v1/admin/songs?q=${encodeURIComponent('нічне')}`, { token: admin });
    assert.deepEqual(found.json.items.map((item) => item.slug), ['night-city']);
    const page = await s.request('GET', '/api/v1/admin/songs?limit=3&offset=3', { token: admin });
    assert.equal(page.json.items.length, 3);
  });

  it('показує користувачів без чутливих полів', async () => {
    const users = await s.request('GET', '/api/v1/admin/users', { token: admin });
    assert.equal(users.json.total, 2);
    assert.deepEqual(Object.keys(users.json.items[0]).sort(), ['createdAt', 'favorites', 'id', 'nickname', 'performances', 'role', 'username']);
  });

  it('повертає останні записи журналу з фільтром за рівнем', async () => {
    await s.request('GET', '/api/v1/songs/not-existing-slug');
    const logs = await s.request('GET', '/api/v1/admin/logs?limit=50&level=info', { token: admin });
    assert.equal(logs.status, 200);
    assert.ok(logs.json.items.length > 0);
    assert.ok(logs.json.items.some((entry) => entry.path === '/api/v1/songs/not-existing-slug' && entry.status === 404));
    const errors = await s.request('GET', '/api/v1/admin/logs?level=error', { token: admin });
    assert.ok(errors.json.items.every((entry) => entry.level === 'error'));
  });

  it('віддає сторінку адміністрування з політикою безпеки контенту', async () => {
    const page = await s.request('GET', '/admin');
    assert.equal(page.status, 200);
    assert.match(page.headers.get('content-type'), /text\/html/);
    const policy = page.headers.get('content-security-policy');
    const nonce = /nonce-([A-Za-z0-9+/=]+)/.exec(policy)[1];
    assert.ok(page.text.includes(`<script nonce="${nonce}">`));
    assert.ok(!page.text.includes('__NONCE__'));
    assert.ok(!/ on[a-z]+=/i.test(page.text.replace(/\{[^}]*\}/g, '')));
    const second = await s.request('GET', '/admin');
    assert.notEqual(second.headers.get('content-security-policy'), policy);
  });

  it('віддає політику конфіденційності й умови використання', async () => {
    const privacy = await s.request('GET', '/privacy');
    assert.equal(privacy.status, 200);
    assert.ok(privacy.text.includes('Політика конфіденційності'));
    assert.ok(privacy.text.includes('scrypt'));
    const terms = await s.request('GET', '/terms');
    assert.equal(terms.status, 200);
    assert.ok(terms.text.includes('Умови використання'));
  });

  it('віддає метрики лише за токеном метрик або адміністратору', async () => {
    assert.equal((await s.request('GET', '/metrics')).status, 401);
    assert.equal((await s.request('GET', '/metrics', { token: user })).status, 401);
    const byAdmin = await s.request('GET', '/metrics', { token: admin });
    assert.equal(byAdmin.status, 200);
    const byToken = await s.request('GET', '/metrics', { token: 'metrics-secret' });
    assert.equal(byToken.status, 200);
    assert.match(byToken.headers.get('content-type'), /text\/plain/);
    assert.match(byToken.text, /karaoke_http_requests_total\{method="GET",route="\/api\/v1\/admin\/stats",status="200"\}/);
    assert.match(byToken.text, /karaoke_http_request_duration_ms_bucket\{[^}]*le="\+Inf"\}/);
    assert.match(byToken.text, /^karaoke_users 2$/m);
    assert.match(byToken.text, /^karaoke_songs 10$/m);
  });

  it('віддає специфікацію OpenAPI', async () => {
    const spec = await s.request('GET', '/api/v1/openapi.json');
    assert.equal(spec.status, 200);
    assert.equal(spec.json.openapi, '3.1.0');
    assert.ok(spec.json.paths['/api/v1/sync/push']);
  });
});
