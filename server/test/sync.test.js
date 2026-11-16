import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

const CLIENT_A = 'aaaaaaaa-1111-4222-8333-444444444444';
const CLIENT_B = 'bbbbbbbb-1111-4222-8333-444444444444';

describe('синхронізація', () => {
  let s;
  before(async () => {
    s = await startServer({ config: { pullPageSize: 2, maxSyncChanges: 6 } });
  });
  after(() => s.close());

  const push = (token, changes) => s.request('POST', '/api/v1/sync/push', { token, body: { changes } });
  const pull = (token, since = 0) => s.request('GET', `/api/v1/sync/pull?since=${since}`, { token });
  const favorite = (slug, op, at) => ({ type: 'favorite', op, songSlug: slug, at });
  const performance = (clientId, extra = {}) => ({
    type: 'performance',
    op: 'upsert',
    clientId,
    songSlug: 'morning-wave',
    performedAt: 1_700_000_000_000,
    durationMs: 48_000,
    score: 80,
    at: Date.now(),
    ...extra,
  });

  it('вимагає токен', async () => {
    assert.equal((await s.request('GET', '/api/v1/sync/pull')).status, 401);
    assert.equal((await s.request('POST', '/api/v1/sync/push', { body: { changes: [] } })).status, 401);
  });

  it('застосовує обране й повертає його при pull', async () => {
    const { token } = await s.register('sync_fav');
    const result = await push(token, [favorite('neon-dance', 'upsert', Date.now())]);
    assert.equal(result.status, 200);
    assert.equal(result.json.applied, 1);
    const pulled = await pull(token);
    assert.deepEqual(
      pulled.json.changes.map((change) => [change.type, change.songSlug, change.deleted]),
      [['favorite', 'neon-dance', false]],
    );
  });

  it('вирішує конфлікти за останнім часом клієнта', async () => {
    const { token } = await s.register('sync_lww');
    const base = Date.now();
    await push(token, [favorite('night-city', 'upsert', base)]);
    const removed = await push(token, [favorite('night-city', 'delete', base + 1000)]);
    assert.equal(removed.json.results[0].status, 'applied');
    const stale = await push(token, [favorite('night-city', 'upsert', base + 500)]);
    assert.equal(stale.json.results[0].status, 'stale');
    const state = await pull(token);
    assert.equal(state.json.changes[0].deleted, true);
  });

  it('відхиляє невідомі пісні, типи, операції та майбутній час', async () => {
    const { token } = await s.register('sync_bad');
    const now = Date.now();
    const result = await push(token, [
      favorite('no-such-song', 'upsert', now),
      { type: 'bookmark', op: 'upsert', at: now },
      { type: 'favorite', op: 'merge', songSlug: 'neon-dance', at: now },
      favorite('neon-dance', 'upsert', now + 3_600_000),
      performance(CLIENT_A, { score: 101 }),
    ]);
    assert.deepEqual(
      result.json.results.map((item) => item.status),
      ['rejected', 'rejected', 'rejected', 'rejected', 'rejected'],
    );
    assert.equal(result.json.results[0].reason, 'unknown_song');
    assert.equal(result.json.results[3].reason, 'timestamp_in_future');
    assert.equal(result.json.applied, 0);
  });

  it('перевіряє форму тіла запиту та ліміт змін', async () => {
    const { token } = await s.register('sync_shape');
    assert.equal((await s.request('POST', '/api/v1/sync/push', { token, body: { changes: 'x' } })).status, 400);
    assert.equal((await s.request('POST', '/api/v1/sync/push', { token, body: [] })).status, 400);
    const many = Array.from({ length: 7 }, () => favorite('neon-dance', 'upsert', Date.now()));
    assert.equal((await push(token, many)).status, 400);
  });

  it('зберігає виконання ідемпотентно за clientId, оновлює й видаляє', async () => {
    const { token } = await s.register('sync_perf');
    const at = Date.now();
    await push(token, [performance(CLIENT_A, { at })]);
    await push(token, [performance(CLIENT_A, { at })]);
    let state = (await pull(token)).json.changes.filter((change) => change.type === 'performance');
    assert.equal(state.length, 1);
    assert.equal(state[0].score, 80);

    await push(token, [performance(CLIENT_A, { at: at + 10, score: 95 })]);
    state = (await pull(token)).json.changes.filter((change) => change.type === 'performance');
    assert.equal(state.length, 1);
    assert.equal(state[0].score, 95);

    const staleUpdate = await push(token, [performance(CLIENT_A, { at: at + 5, score: 10 })]);
    assert.equal(staleUpdate.json.results[0].status, 'stale');

    await push(token, [{ type: 'performance', op: 'delete', clientId: CLIENT_A, at: at + 20 }]);
    state = (await pull(token)).json.changes.filter((change) => change.type === 'performance');
    assert.equal(state[0].deleted, true);

    const unknownDelete = await push(token, [{ type: 'performance', op: 'delete', clientId: CLIENT_B, at: at + 30 }]);
    assert.equal(unknownDelete.json.results[0].status, 'applied');
  });

  it('приймає виконання без оцінки', async () => {
    const { token } = await s.register('sync_null');
    const result = await push(token, [performance(CLIENT_B, { score: null })]);
    assert.equal(result.json.results[0].status, 'applied');
    const state = (await pull(token)).json.changes.find((change) => change.type === 'performance');
    assert.equal(state.score, null);
  });

  it('віддає зміни сторінками й зсуває курсор', async () => {
    const { token } = await s.register('sync_pages');
    const now = Date.now();
    const slugs = ['morning-wave', 'night-city', 'summer-road', 'rain-window', 'neon-dance'];
    for (const slug of slugs) await push(token, [favorite(slug, 'upsert', now)]);
    const seen = [];
    let since = 0;
    let pages = 0;
    for (let guard = 0; guard < 10; guard += 1) {
      const page = await pull(token, since);
      pages += 1;
      seen.push(...page.json.changes.map((change) => change.songSlug));
      since = page.json.nextSince;
      if (!page.json.hasMore) break;
    }
    assert.deepEqual(seen, slugs);
    assert.ok(pages >= 3);
    const empty = await pull(token, since);
    assert.deepEqual(empty.json.changes, []);
    assert.equal(empty.json.nextSince, since);
  });

  it('ізолює дані користувачів', async () => {
    const first = await s.register('iso_one');
    const second = await s.register('iso_two');
    await push(first.token, [favorite('summer-road', 'upsert', Date.now())]);
    assert.deepEqual((await pull(second.token)).json.changes, []);
  });
});
