import assert from 'node:assert/strict';
import { after, before, beforeEach, describe, it } from 'node:test';
import { sendJson, startServer, startUpstream } from './helpers.js';

describe('інтеграція зі сторонніми сервісами', () => {
  let s;
  let upstream;
  let token;
  before(async () => {
    upstream = await startUpstream();
    s = await startServer({
      config: { lrclibBaseUrl: upstream.baseUrl, itunesBaseUrl: upstream.baseUrl, upstreamTimeoutMs: 250 },
    });
    token = (await s.register('integrator')).token;
  });
  after(async () => {
    await s.close();
    await upstream.close();
  });
  beforeEach(() => {
    upstream.state.hits.length = 0;
    upstream.state.routes.clear();
    s.db.exec('DELETE FROM integration_cache');
  });

  const lyrics = (query) => s.request('GET', `/api/v1/integrations/lyrics?${query}`, { token });
  const syncedEntry = (extra = {}) => ({
    trackName: 'Ранкова хвиля',
    artistName: 'Demo Band',
    duration: 48,
    plainLyrics: 'перший рядок',
    syncedLyrics: '[00:01.00] перший рядок',
    instrumental: false,
    ...extra,
  });

  it('вимагає авторизації', async () => {
    assert.equal((await s.request('GET', '/api/v1/integrations/lyrics?title=x')).status, 401);
    assert.equal((await s.request('GET', '/api/v1/integrations/metadata?q=x')).status, 401);
  });

  it('знаходить синхронізований текст і кешує результат', async () => {
    upstream.on('/api/get', (req, res) => sendJson(res, 200, syncedEntry()));
    const query = `title=${encodeURIComponent('Ранкова хвиля')}&artist=${encodeURIComponent('Demo Band')}&durationSec=48`;
    const first = await lyrics(query);
    assert.equal(first.status, 200);
    assert.equal(first.json.found, true);
    assert.equal(first.json.synced, true);
    assert.equal(first.json.lyrics, '[00:01.00] перший рядок');
    assert.equal(first.json.cached, false);
    const hit = upstream.hitsFor('/api/get')[0];
    assert.equal(hit.params.track_name, 'Ранкова хвиля');
    assert.equal(hit.params.artist_name, 'Demo Band');
    assert.equal(hit.params.duration, '48');
    const second = await lyrics(query);
    assert.equal(second.json.cached, true);
    assert.equal(upstream.hitsFor('/api/get').length, 1);
  });

  it('повертає звичайний текст, якщо синхронізованого немає', async () => {
    upstream.on('/api/get', (req, res) => sendJson(res, 200, syncedEntry({ syncedLyrics: null })));
    const result = await lyrics('title=a&artist=b');
    assert.equal(result.json.synced, false);
    assert.equal(result.json.lyrics, 'перший рядок');
  });

  it('переходить до пошуку й обирає найближчу за тривалістю версію', async () => {
    upstream.on('/api/search', (req, res) =>
      sendJson(res, 200, [
        syncedEntry({ trackName: 'Далека', duration: 200, syncedLyrics: '[00:01.00] далека' }),
        syncedEntry({ trackName: 'Близька', duration: 50, syncedLyrics: '[00:01.00] близька' }),
        syncedEntry({ trackName: 'Інструментал', duration: 48, instrumental: true }),
      ]),
    );
    const result = await lyrics('title=song&artist=band&durationSec=48');
    assert.equal(result.json.found, true);
    assert.equal(result.json.trackName, 'Близька');
    assert.equal(upstream.hitsFor('/api/get').length, 1);
    assert.equal(upstream.hitsFor('/api/search').length, 1);
  });

  it('шукає без виконавця одразу через пошук', async () => {
    upstream.on('/api/search', (req, res) => sendJson(res, 200, [syncedEntry()]));
    const result = await lyrics('title=song');
    assert.equal(result.json.found, true);
    assert.equal(upstream.hitsFor('/api/get').length, 0);
  });

  it('кешує й негативний результат', async () => {
    upstream.on('/api/search', (req, res) => sendJson(res, 200, []));
    const first = await lyrics('title=nothing');
    assert.deepEqual([first.json.found, first.json.cached], [false, false]);
    const second = await lyrics('title=nothing');
    assert.deepEqual([second.json.found, second.json.cached], [false, true]);
    assert.equal(upstream.hitsFor('/api/search').length, 1);
  });

  it('повертає 502, коли сервіс недоступний, і не кешує помилку', async () => {
    upstream.on('/api/search', (req, res) => sendJson(res, 500, { message: 'boom' }));
    const failed = await lyrics('title=broken');
    assert.equal(failed.status, 502);
    assert.equal(failed.json.error.code, 'upstream_error');
    upstream.on('/api/search', (req, res) => sendJson(res, 200, [syncedEntry()]));
    const recovered = await lyrics('title=broken');
    assert.equal(recovered.status, 200);
    assert.equal(recovered.json.found, true);
  });

  it('обриває запит після таймауту', async () => {
    upstream.on('/api/search', (req, res) => setTimeout(() => sendJson(res, 200, []), 1000));
    const started = Date.now();
    const result = await lyrics('title=slow');
    assert.equal(result.status, 502);
    assert.ok(Date.now() - started < 900);
  });

  it('віддає застарілий кеш, якщо сервіс упав', async () => {
    const stale = await startServer({
      config: { lrclibBaseUrl: upstream.baseUrl, itunesBaseUrl: upstream.baseUrl, cacheTtlSeconds: 0 },
    });
    const staleToken = (await stale.register('stale_user')).token;
    upstream.on('/api/search', (req, res) => sendJson(res, 200, [syncedEntry()]));
    const request = () => stale.request('GET', '/api/v1/integrations/lyrics?title=cached', { token: staleToken });
    const fresh = await request();
    assert.equal(fresh.json.found, true);
    upstream.on('/api/search', (req, res) => sendJson(res, 503, {}));
    const fallback = await request();
    assert.equal(fallback.status, 200);
    assert.equal(fallback.json.stale, true);
    assert.equal(fallback.json.found, true);
    await stale.close();
  });

  it('перевіряє параметри', async () => {
    assert.equal((await lyrics('artist=x')).status, 400);
    assert.equal((await lyrics('title=x&durationSec=abc')).status, 400);
    assert.equal((await lyrics('title=x&durationSec=0')).status, 400);
    assert.equal((await s.request('GET', '/api/v1/integrations/metadata', { token })).status, 400);
    assert.equal((await s.request('GET', '/api/v1/integrations/metadata?q=x&limit=50', { token })).status, 400);
  });

  it('перетворює відповідь iTunes і збільшує обкладинку', async () => {
    upstream.on('/search', (req, res) =>
      sendJson(res, 200, {
        resultCount: 3,
        results: [
          {
            trackName: 'Song',
            artistName: 'Artist',
            collectionName: 'Album',
            primaryGenreName: 'Pop',
            trackTimeMillis: 201000,
            artworkUrl100: 'https://img.example/a/100x100bb.jpg',
          },
          { trackName: 'Без виконавця' },
          { trackName: 'Other', artistName: 'Someone' },
        ],
      }),
    );
    const response = await s.request('GET', '/api/v1/integrations/metadata?q=song&limit=5', { token });
    assert.equal(response.status, 200);
    assert.equal(response.json.items.length, 2);
    assert.deepEqual(response.json.items[0], {
      title: 'Song',
      artist: 'Artist',
      album: 'Album',
      genre: 'Pop',
      durationMs: 201000,
      coverUrl: 'https://img.example/a/600x600bb.jpg',
      source: 'itunes',
    });
    assert.equal(response.json.items[1].coverUrl, null);
    assert.equal(upstream.hitsFor('/search')[0].params.term, 'song');
    const again = await s.request('GET', '/api/v1/integrations/metadata?q=song&limit=5', { token });
    assert.equal(again.json.cached, true);
    assert.equal(upstream.hitsFor('/search').length, 1);
  });
});
