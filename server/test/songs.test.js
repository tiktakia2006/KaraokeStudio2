import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

const newSong = (overrides = {}) => ({
  slug: 'test-song',
  title: 'Тестова пісня',
  artist: 'Автор',
  durationMs: 30_000,
  genre: 'Поп',
  lyrics: '[00:01.00]Рядок',
  audioSource: 'demo:test-song',
  ...overrides,
});

describe('каталог пісень', () => {
  let s;
  let admin;
  let user;
  before(async () => {
    s = await startServer();
    admin = (await s.register('admin')).token;
    user = (await s.register('regular')).token;
  });
  after(() => s.close());

  it('повертає початковий каталог за зростанням updatedAt', async () => {
    const response = await s.request('GET', '/api/v1/songs');
    assert.equal(response.status, 200);
    assert.equal(response.json.items.length, 10);
    assert.equal(response.json.hasMore, false);
    const stamps = response.json.items.map((item) => item.updatedAt);
    assert.deepEqual(stamps, [...stamps].sort((a, b) => a - b));
    assert.equal(new Set(stamps).size, 10);
    const first = response.json.items[0];
    assert.equal(first.slug, 'morning-wave');
    assert.equal(first.audioSource, 'demo:morning-wave');
    assert.equal(first.deleted, false);
  });

  it('пагінує через since і limit без пропусків і повторів', async () => {
    const seen = [];
    let since = 0;
    for (let guard = 0; guard < 10; guard += 1) {
      const page = await s.request('GET', `/api/v1/songs?since=${since}&limit=4`);
      seen.push(...page.json.items.map((item) => item.slug));
      since = page.json.nextSince;
      if (!page.json.hasMore) break;
    }
    assert.equal(seen.length, 10);
    assert.equal(new Set(seen).size, 10);
  });

  it('підтримує ETag і 304', async () => {
    const first = await s.request('GET', '/api/v1/songs?limit=2');
    const etag = first.headers.get('etag');
    assert.ok(etag);
    const second = await s.request('GET', '/api/v1/songs?limit=2', { headers: { 'If-None-Match': etag } });
    assert.equal(second.status, 304);
  });

  it('шукає без урахування регістру кирилиці та фільтрує за жанром', async () => {
    const lower = await s.request('GET', `/api/v1/songs?q=${encodeURIComponent('ранк')}`);
    assert.deepEqual(lower.json.items.map((item) => item.slug), ['morning-wave']);
    const upper = await s.request('GET', `/api/v1/songs?q=${encodeURIComponent('РАНКОВА')}`);
    assert.equal(upper.json.items.length, 1);
    const genre = await s.request('GET', `/api/v1/songs?genre=${encodeURIComponent('Поп')}`);
    assert.deepEqual(genre.json.items.map((item) => item.slug).sort(), ['morning-wave', 'star-path']);
    const wildcard = await s.request('GET', `/api/v1/songs?q=${encodeURIComponent('%')}`);
    assert.equal(wildcard.json.items.length, 0);
  });

  it('перевіряє параметри запиту', async () => {
    for (const query of ['limit=0', 'limit=1000', 'since=abc', 'since=-5']) {
      const response = await s.request('GET', `/api/v1/songs?${query}`);
      assert.equal(response.status, 400, query);
    }
  });

  it('віддає пісню за slug або 404', async () => {
    const found = await s.request('GET', '/api/v1/songs/neon-dance');
    assert.equal(found.status, 200);
    assert.equal(found.json.title, 'Neon Dance');
    assert.equal((await s.request('GET', '/api/v1/songs/unknown-song')).status, 404);
  });

  it('обмежує керування каталогом адміністратором', async () => {
    assert.equal((await s.request('POST', '/api/v1/songs', { body: newSong() })).status, 401);
    assert.equal((await s.request('POST', '/api/v1/songs', { token: user, body: newSong() })).status, 403);
  });

  it('створює, оновлює й видаляє пісню з tombstone для синхронізації', async () => {
    const before = (await s.request('GET', '/api/v1/songs')).json.nextSince;
    const created = await s.request('POST', '/api/v1/songs', { token: admin, body: newSong() });
    assert.equal(created.status, 201);
    assert.ok(created.json.updatedAt > before);
    const duplicate = await s.request('POST', '/api/v1/songs', { token: admin, body: newSong() });
    assert.equal(duplicate.status, 409);

    const delta = await s.request('GET', `/api/v1/songs?since=${before}`);
    assert.deepEqual(delta.json.items.map((item) => item.slug), ['test-song']);

    const updated = await s.request('PUT', '/api/v1/songs/test-song', { token: admin, body: newSong({ title: 'Нова назва' }) });
    assert.equal(updated.status, 200);
    assert.equal(updated.json.title, 'Нова назва');
    assert.ok(updated.json.updatedAt > created.json.updatedAt);

    const afterUpdate = (await s.request('GET', '/api/v1/songs?limit=200')).json.nextSince;
    const removed = await s.request('DELETE', '/api/v1/songs/test-song', { token: admin });
    assert.equal(removed.status, 204);
    assert.equal((await s.request('GET', '/api/v1/songs/test-song')).status, 404);

    const full = await s.request('GET', '/api/v1/songs?limit=200');
    assert.ok(!full.json.items.some((item) => item.slug === 'test-song'));
    const tombstones = await s.request('GET', `/api/v1/songs?since=${afterUpdate}`);
    assert.deepEqual(tombstones.json.items.map((item) => [item.slug, item.deleted]), [['test-song', true]]);
  });

  it('перевіряє дані нової пісні', async () => {
    const bodies = [
      newSong({ slug: 'Bad Slug' }),
      newSong({ audioSource: 'http://insecure.example/a.mp3' }),
      newSong({ durationMs: 10 }),
      newSong({ coverUrl: 'http://insecure.example/c.jpg' }),
      newSong({ title: '' }),
    ];
    for (const body of bodies) {
      const response = await s.request('POST', '/api/v1/songs', { token: admin, body });
      assert.equal(response.status, 400, JSON.stringify(body));
    }
  });
});
