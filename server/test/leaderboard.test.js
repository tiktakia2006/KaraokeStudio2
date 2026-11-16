import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

describe('рейтинг гравців', () => {
  let s;
  const users = {};
  before(async () => {
    s = await startServer();
    for (const name of ['lb_one', 'lb_two', 'lb_three', 'lb_four']) users[name] = (await s.register(name, 'password123', `Нік ${name}`)).token;
    const send = (token, clientId, score, performedAt) =>
      s.request('POST', '/api/v1/sync/push', {
        token,
        body: {
          changes: [
            {
              type: 'performance',
              op: 'upsert',
              clientId,
              songSlug: 'morning-wave',
              performedAt,
              durationMs: 48_000,
              score,
              at: Date.now(),
            },
          ],
        },
      });
    await send(users.lb_one, 'one-attempt-1', 70, 1000);
    await send(users.lb_one, 'one-attempt-2', 90, 2000);
    await send(users.lb_two, 'two-attempt-1', 90, 3000);
    await send(users.lb_three, 'three-attempt-1', 80, 4000);
    await send(users.lb_three, 'three-attempt-2', null, 5000);
    await send(users.lb_four, 'four-attempt-1', 100, 6000);
    await s.request('POST', '/api/v1/sync/push', {
      token: users.lb_four,
      body: { changes: [{ type: 'performance', op: 'delete', clientId: 'four-attempt-1', at: Date.now() + 10 }] },
    });
  });
  after(() => s.close());

  it('показує найкращий результат кожного гравця з рангами й нічиїми', async () => {
    const response = await s.request('GET', '/api/v1/songs/morning-wave/leaderboard');
    assert.equal(response.status, 200);
    assert.deepEqual(
      response.json.items.map((entry) => [entry.nickname, entry.bestScore, entry.rank]),
      [
        ['Нік lb_one', 90, 1],
        ['Нік lb_two', 90, 1],
        ['Нік lb_three', 80, 3],
      ],
    );
    assert.equal(response.json.items[0].attempts, 2);
    assert.equal(response.json.items[0].achievedAt, 2000);
    assert.equal(response.json.total, 3);
    assert.equal(response.json.me, null);
    assert.equal(response.json.items[0].userId, undefined);
  });

  it('додає місце поточного користувача й застосовує limit', async () => {
    const response = await s.request('GET', '/api/v1/songs/morning-wave/leaderboard?limit=1', { token: users.lb_three });
    assert.equal(response.json.items.length, 1);
    assert.equal(response.json.total, 3);
    assert.deepEqual([response.json.me.rank, response.json.me.bestScore], [3, 80]);
  });

  it('не рахує видалені виконання та виконання без оцінки', async () => {
    const response = await s.request('GET', '/api/v1/songs/morning-wave/leaderboard?limit=100', { token: users.lb_four });
    assert.equal(response.json.me, null);
    assert.ok(!response.json.items.some((entry) => entry.bestScore === 100));
  });

  it('повертає порожній рейтинг для пісні без результатів і 404 для невідомої', async () => {
    const empty = await s.request('GET', '/api/v1/songs/neon-dance/leaderboard');
    assert.deepEqual(empty.json, { items: [], total: 0, me: null });
    assert.equal((await s.request('GET', '/api/v1/songs/unknown/leaderboard')).status, 404);
    assert.equal((await s.request('GET', '/api/v1/songs/morning-wave/leaderboard?limit=0')).status, 400);
  });
});
