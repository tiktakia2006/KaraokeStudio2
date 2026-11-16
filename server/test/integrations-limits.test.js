import assert from 'node:assert/strict';
import http from 'node:http';
import { after, before, describe, it } from 'node:test';
import { sendJson, startServer } from './helpers.js';

describe('дотримання умов сторонніх сервісів', () => {
  let s;
  let upstream;
  let token;
  const seen = [];
  let clock;

  before(async () => {
    clock = Date.now();
    upstream = http.createServer((req, res) => {
      seen.push({ url: req.url, userAgent: req.headers['user-agent'] });
      if (req.url.startsWith('/search')) {
        return sendJson(res, 200, { results: [{ trackName: 'Т', artistName: 'А', artworkUrl100: 'https://x/100x100bb.jpg' }] });
      }
      return sendJson(res, 404, {});
    });
    await new Promise((resolve) => upstream.listen(0, '127.0.0.1', resolve));
    const base = `http://127.0.0.1:${upstream.address().port}`;
    s = await startServer({
      now: () => clock,
      config: {
        lrclibBaseUrl: base,
        itunesBaseUrl: base,
        itunesMaxPerMinute: 2,
        userAgent: 'KaraokeStudio-Test/1.0 (+https://example.org/contact)',
        rateLimit: { integrationsMax: 6 },
      },
    });
    token = (await s.register('limits_user')).token;
  });

  after(async () => {
    await s.close();
    await new Promise((resolve) => {
      upstream.closeAllConnections();
      upstream.close(resolve);
    });
  });

  const metadata = (q) => s.request('GET', `/api/v1/integrations/metadata?q=${encodeURIComponent(q)}`, { token });

  it('передає у зовнішні сервіси ідентифікацію застосунку', async () => {
    await metadata('перший');
    assert.equal(seen[0].userAgent, 'KaraokeStudio-Test/1.0 (+https://example.org/contact)');
  });

  it('не перевищує ліміт запитів до iTunes і повідомляє, коли повторити', async () => {
    await metadata('другий');
    const blocked = await metadata('третій');
    assert.equal(blocked.status, 429);
    assert.ok(Number(blocked.headers.get('retry-after')) >= 1);
    assert.equal(seen.filter((hit) => hit.url.startsWith('/search')).length, 2);
  });

  it('відповідає з кешу, коли ліміт вичерпано', async () => {
    const cached = await metadata('перший');
    assert.equal(cached.status, 200);
    assert.equal(cached.json.cached, true);
  });

  it('після спливу хвилини знову звертається до сервісу', async () => {
    clock += 61_000;
    const fresh = await metadata('третій');
    assert.equal(fresh.status, 200);
    assert.equal(fresh.json.attribution.name, 'Apple iTunes Search API');
  });

  it('має власний ліміт частоти для інтеграцій', async () => {
    let blocked = null;
    for (let i = 0; i < 10 && !blocked; i += 1) {
      const response = await metadata('перший');
      if (response.status === 429) blocked = response;
    }
    assert.ok(blocked);
    assert.equal(blocked.json.error.code, 'rate_limited');
    const normal = await s.request('GET', '/api/v1/meta');
    assert.equal(normal.status, 200);
  });
});
