import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { sendJson, startServer, startUpstream } from './helpers.js';

const PERF = 'e2e-device-perf-01';

class Device {
  constructor(server, name) {
    this.server = server;
    this.name = name;
    this.session = null;
    this.favoriteCursor = 0;
    this.songCursor = 0;
    this.songs = new Map();
    this.favorites = new Set();
    this.performances = new Map();
  }

  async signIn(username, password) {
    const response = await this.server.request('POST', '/api/v1/auth/login', { body: { username, password } });
    assert.equal(response.status, 200);
    this.session = response.json;
  }

  async call(method, path, options = {}) {
    let response = await this.server.request(method, path, { ...options, token: this.session.token });
    if (response.status === 401) {
      const renewed = await this.server.request('POST', '/api/v1/auth/refresh', { body: { refreshToken: this.session.refreshToken } });
      assert.equal(renewed.status, 200);
      this.session = renewed.json;
      response = await this.server.request(method, path, { ...options, token: this.session.token });
    }
    return response;
  }

  async pullCatalog() {
    for (;;) {
      const page = await this.server.request('GET', `/api/v1/songs?since=${this.songCursor}&limit=4`);
      assert.equal(page.status, 200);
      for (const item of page.json.items) {
        if (item.deleted) this.songs.delete(item.slug);
        else this.songs.set(item.slug, item);
      }
      this.songCursor = page.json.nextSince;
      if (!page.json.hasMore) return;
    }
  }

  async push(changes) {
    const response = await this.call('POST', '/api/v1/sync/push', { body: { changes } });
    assert.equal(response.status, 200);
    return response.json;
  }

  async pull() {
    for (;;) {
      const page = await this.call('GET', `/api/v1/sync/pull?since=${this.favoriteCursor}`);
      assert.equal(page.status, 200);
      for (const change of page.json.changes) {
        if (change.type === 'favorite') {
          if (change.deleted) this.favorites.delete(change.songSlug);
          else this.favorites.add(change.songSlug);
        } else if (change.deleted) {
          this.performances.delete(change.clientId);
        } else {
          this.performances.set(change.clientId, change);
        }
      }
      this.favoriteCursor = page.json.nextSince;
      if (!page.json.hasMore) return;
    }
  }
}

describe('наскрізний сценарій двох пристроїв одного користувача', () => {
  let s;
  let upstream;
  let phone;
  let tablet;
  let clock;

  before(async () => {
    clock = Date.now();
    upstream = await startUpstream();
    upstream.on('/api/get', (req, res) =>
      sendJson(res, 200, { trackName: 'Пісня', artistName: 'Гурт', duration: 48, plainLyrics: 'а', syncedLyrics: '[00:01.00]а' }),
    );
    s = await startServer({
      now: () => clock,
      config: { pullPageSize: 2, lrclibBaseUrl: upstream.baseUrl, itunesBaseUrl: upstream.baseUrl },
    });
    phone = new Device(s, 'phone');
    tablet = new Device(s, 'tablet');
  });

  after(async () => {
    await s.close();
    await upstream.close();
  });

  it('обидва пристрої отримують однаковий каталог посторінково', async () => {
    await phone.pullCatalog();
    await tablet.pullCatalog();
    assert.equal(phone.songs.size, 10);
    assert.deepEqual([...phone.songs.keys()].sort(), [...tablet.songs.keys()].sort());
  });

  it('користувач реєструється на одному пристрої й входить на іншому', async () => {
    const registered = await s.register('e2e_user', 'e2e-password', 'Двоє пристроїв');
    assert.equal(registered.response.status, 201);
    await phone.signIn('e2e_user', 'e2e-password');
    await tablet.signIn('e2e_user', 'e2e-password');
  });

  it('зміни з телефона доходять до планшета, включно з виконанням і рейтингом', async () => {
    const result = await phone.push([
      { type: 'favorite', op: 'upsert', songSlug: 'neon-dance', at: clock },
      { type: 'favorite', op: 'upsert', songSlug: 'night-city', at: clock },
      { type: 'performance', op: 'upsert', clientId: PERF, songSlug: 'morning-wave', performedAt: clock - 5000, durationMs: 48000, score: 91, at: clock },
    ]);
    assert.equal(result.applied, 3);
    await tablet.pull();
    assert.deepEqual([...tablet.favorites].sort(), ['neon-dance', 'night-city']);
    assert.equal(tablet.performances.get(PERF).score, 91);
    const board = await s.request('GET', '/api/v1/songs/morning-wave/leaderboard', { token: tablet.session.token });
    assert.equal(board.json.me.rank, 1);
    assert.equal(board.json.me.bestScore, 91);
    assert.equal(board.json.me.nickname, 'Двоє пристроїв');
  });

  it('конфлікт вирішується за часом клієнта: пізніша зміна перемагає, застаріла повертається як stale', async () => {
    clock += 1000;
    const laterOnTablet = await tablet.push([{ type: 'favorite', op: 'delete', songSlug: 'neon-dance', at: clock + 500 }]);
    assert.equal(laterOnTablet.results[0].status, 'applied');
    const olderOnPhone = await phone.push([{ type: 'favorite', op: 'upsert', songSlug: 'neon-dance', at: clock }]);
    assert.equal(olderOnPhone.results[0].status, 'stale');
    await phone.pull();
    assert.ok(!phone.favorites.has('neon-dance'));
    assert.ok(phone.favorites.has('night-city'));
  });

  it('токен доступу спливає, пристрій тихо оновлює сесію й продовжує синхронізацію', async () => {
    clock += 2 * 3600 * 1000;
    const before = phone.session.refreshToken;
    await phone.push([{ type: 'favorite', op: 'upsert', songSlug: 'rain-window', at: clock }]);
    assert.notEqual(phone.session.refreshToken, before);
    await tablet.pull();
    assert.ok(tablet.favorites.has('rain-window'));
  });

  it('видалення виконання на одному пристрої прибирає його на іншому та з рейтингу', async () => {
    clock += 1000;
    const removed = await tablet.push([{ type: 'performance', op: 'delete', clientId: PERF, at: clock }]);
    assert.equal(removed.results[0].status, 'applied');
    await phone.pull();
    assert.ok(!phone.performances.has(PERF));
    const board = await s.request('GET', '/api/v1/songs/morning-wave/leaderboard', { token: phone.session.token });
    assert.equal(board.json.total, 0);
    assert.equal(board.json.me, null);
  });

  it('зміни каталогу адміністратором, включно з видаленням, доходять до пристроїв за курсором', async () => {
    const admin = (await s.register('admin')).token;
    await s.request('DELETE', '/api/v1/songs/autumn-waltz', { token: admin });
    await s.request('POST', '/api/v1/songs', {
      token: admin,
      body: { slug: 'e2e-new-song', title: 'Нова', artist: 'Гурт', durationMs: 30000, audioSource: 'demo:e2e-new-song' },
    });
    await phone.pullCatalog();
    assert.ok(!phone.songs.has('autumn-waltz'));
    assert.ok(phone.songs.has('e2e-new-song'));
    assert.equal(phone.songs.size, 10);
  });

  it('онлайн-пошук тексту з кешуванням працює для користувача з активною сесією', async () => {
    const query = `title=${encodeURIComponent('Пісня')}&artist=${encodeURIComponent('Гурт')}&durationSec=48`;
    const first = await phone.call('GET', `/api/v1/integrations/lyrics?${query}`);
    assert.equal(first.json.found, true);
    assert.equal(first.json.attribution.name, 'LRCLIB');
    const second = await tablet.call('GET', `/api/v1/integrations/lyrics?${query}`);
    assert.equal(second.json.cached, true);
    assert.equal(upstream.hitsFor('/api/get').length, 1);
  });

  it('вихід скасовує сесію, а видалення облікового запису очищає все', async () => {
    await tablet.call('POST', '/api/v1/auth/logout', { body: { refreshToken: tablet.session.refreshToken } });
    const renewed = await s.request('POST', '/api/v1/auth/refresh', { body: { refreshToken: tablet.session.refreshToken } });
    assert.equal(renewed.status, 401);
    const removed = await phone.call('DELETE', '/api/v1/auth/me', { body: { password: 'e2e-password' } });
    assert.equal(removed.status, 204);
    const again = await s.request('POST', '/api/v1/auth/login', { body: { username: 'e2e_user', password: 'e2e-password' } });
    assert.equal(again.status, 401);
  });
});
