import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

describe('загальний рейтинг гравців', () => {
  let s;
  let tokens;
  const now = Date.now();

  before(async () => {
    s = await startServer();
    tokens = {};
    for (const name of ['anna', 'bohdan', 'clara', 'dmytro']) tokens[name] = (await s.register(name, 'password-1234', name.toUpperCase())).token;
    const perf = (clientId, slug, score, at = now) => ({
      type: 'performance',
      op: 'upsert',
      clientId,
      songSlug: slug,
      performedAt: at - 1000,
      durationMs: 40_000,
      score,
      at,
    });
    await s.request('POST', '/api/v1/sync/push', {
      token: tokens.anna,
      body: { changes: [perf('anna-perf-0001', 'morning-wave', 90), perf('anna-perf-0002', 'morning-wave', 70), perf('anna-perf-0003', 'night-city', 80)] },
    });
    await s.request('POST', '/api/v1/sync/push', {
      token: tokens.bohdan,
      body: { changes: [perf('bohdan-perf-0001', 'morning-wave', 95), perf('bohdan-perf-0002', 'night-city', 75)] },
    });
    await s.request('POST', '/api/v1/sync/push', {
      token: tokens.clara,
      body: { changes: [perf('clara-perf-0001', 'morning-wave', 100), perf('clara-perf-0002', 'neon-dance', null)] },
    });
  });
  after(() => s.close());

  it('сумує найкращі результати по різних піснях, а не всі спроби', async () => {
    const board = await s.request('GET', '/api/v1/leaderboard');
    assert.equal(board.status, 200);
    assert.deepEqual(board.json.items.map((i) => [i.nickname, i.totalScore, i.songsCount, i.bestScore, i.attempts]), [
      ['ANNA', 170, 2, 90, 3],
      ['BOHDAN', 170, 2, 95, 2],
      ['CLARA', 100, 1, 100, 1],
    ]);
  });

  it('однакова сума дає однакове місце, наступне місце пропускається', async () => {
    const board = await s.request('GET', '/api/v1/leaderboard');
    assert.deepEqual(board.json.items.map((i) => i.rank), [1, 1, 3]);
    assert.equal(board.json.total, 3);
    assert.equal(board.json.me, null);
  });

  it('показує місце користувача, навіть якщо він поза обмеженням, і не виводить чужі ідентифікатори', async () => {
    const limited = await s.request('GET', '/api/v1/leaderboard?limit=1', { token: tokens.clara });
    assert.equal(limited.json.items.length, 1);
    assert.equal(limited.json.me.nickname, 'CLARA');
    assert.equal(limited.json.me.rank, 3);
    assert.equal(limited.json.items[0].userId, undefined);
    const noScores = await s.request('GET', '/api/v1/leaderboard', { token: tokens.dmytro });
    assert.equal(noScores.json.me, null);
  });

  it('прибирає виконання, яке видалено, і пісні, яких уже немає в каталозі', async () => {
    await s.request('POST', '/api/v1/sync/push', {
      token: tokens.clara,
      body: { changes: [{ type: 'performance', op: 'delete', clientId: 'clara-perf-0001', at: now + 5000 }] },
    });
    const board = await s.request('GET', '/api/v1/leaderboard');
    assert.deepEqual(board.json.items.map((i) => i.nickname), ['ANNA', 'BOHDAN']);
    const admin = (await s.register('admin', 'password-1234')).token;
    await s.request('DELETE', '/api/v1/songs/night-city', { token: admin });
    const afterDelete = await s.request('GET', '/api/v1/leaderboard');
    assert.deepEqual(afterDelete.json.items.map((i) => [i.nickname, i.totalScore]), [['BOHDAN', 95], ['ANNA', 90]]);
  });

  it('перевіряє межі limit', async () => {
    assert.equal((await s.request('GET', '/api/v1/leaderboard?limit=0')).status, 400);
    assert.equal((await s.request('GET', '/api/v1/leaderboard?limit=101')).status, 400);
  });
});
