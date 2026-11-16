import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { buildOpenApi } from '../src/openapi.js';
import { validate } from './schema-validator.js';
import { sendJson, startServer, startUpstream } from './helpers.js';

const spec = buildOpenApi('test');
const METHODS = ['get', 'post', 'put', 'patch', 'delete'];

describe('відповідність API специфікації OpenAPI', () => {
  let s;
  let upstream;
  let admin;
  let user;
  let userSession;
  const checked = new Set();

  before(async () => {
    upstream = await startUpstream();
    upstream.on('/api/get', (req, res) =>
      sendJson(res, 200, { trackName: 'Пісня', artistName: 'Виконавець', duration: 48, plainLyrics: 'рядок', syncedLyrics: '[00:01.00]рядок' }),
    );
    upstream.on('/api/search', (req, res) => sendJson(res, 200, []));
    upstream.on('/search', (req, res) =>
      sendJson(res, 200, {
        results: [{ trackName: 'Пісня', artistName: 'Виконавець', collectionName: 'Альбом', primaryGenreName: 'Pop', trackTimeMillis: 180000, artworkUrl100: 'https://img.example/100x100bb.jpg' }],
      }),
    );
    s = await startServer({
      config: { lrclibBaseUrl: upstream.baseUrl, itunesBaseUrl: upstream.baseUrl, metricsToken: 'metrics-secret' },
    });
    admin = (await s.register('admin')).token;
    userSession = await s.register('contract_user', 'contract-password', 'Контракт');
    user = userSession.token;
  });

  after(async () => {
    await s.close();
    await upstream.close();
  });

  async function call(method, template, actual, expectedStatus, options = {}) {
    const response = await s.request(method, actual, options);
    assert.equal(response.status, expectedStatus, `${method} ${actual}: ${response.text}`);
    const operation = spec.paths[template]?.[method.toLowerCase()];
    assert.ok(operation, `${method} ${template} не описано у специфікації`);
    const documented = operation.responses[String(expectedStatus)];
    assert.ok(documented, `${method} ${template}: статус ${expectedStatus} не описано`);
    const schema = documented.content?.['application/json']?.schema;
    if (schema && response.json !== null) {
      const errors = validate(schema, response.json, spec);
      assert.deepEqual(errors, [], `${method} ${actual}: ${errors.join('; ')}`);
    }
    checked.add(`${method.toUpperCase()} ${template}`);
    return response;
  }

  it('описує кожен зареєстрований маршрут і не містить зайвих', () => {
    const fromRouter = new Set(s.app.router.list().map((route) => `${route.method} ${route.pattern.replace(/:([A-Za-z_]+)/g, '{$1}')}`));
    const fromSpec = new Set();
    for (const [path, item] of Object.entries(spec.paths)) {
      for (const method of METHODS) if (item[method]) fromSpec.add(`${method.toUpperCase()} ${path}`);
    }
    assert.deepEqual([...fromRouter].filter((key) => !fromSpec.has(key)).sort(), []);
    assert.deepEqual([...fromSpec].filter((key) => !fromRouter.has(key)).sort(), []);
  });

  it('системні маршрути', async () => {
    await call('GET', '/health', '/health', 200);
    await call('GET', '/api/v1/meta', '/api/v1/meta', 200);
    await call('GET', '/api/v1/openapi.json', '/api/v1/openapi.json', 200);
    await call('GET', '/metrics', '/metrics', 200, { token: 'metrics-secret' });
    await call('GET', '/metrics', '/metrics', 401);
    await call('GET', '/privacy', '/privacy', 200);
    await call('GET', '/terms', '/terms', 200);
    await call('GET', '/admin', '/admin', 200);
  });

  it('автентифікація', async () => {
    const register = await call('POST', '/api/v1/auth/register', '/api/v1/auth/register', 201, {
      body: { username: 'contract_two', password: 'contract-password' },
    });
    await call('POST', '/api/v1/auth/register', '/api/v1/auth/register', 409, { body: { username: 'contract_two', password: 'contract-password' } });
    await call('POST', '/api/v1/auth/register', '/api/v1/auth/register', 400, { body: { username: 'x', password: '1' } });
    await call('POST', '/api/v1/auth/login', '/api/v1/auth/login', 200, { body: { username: 'contract_two', password: 'contract-password' } });
    await call('POST', '/api/v1/auth/login', '/api/v1/auth/login', 401, { body: { username: 'contract_two', password: 'wrong-password' } });
    const refreshed = await call('POST', '/api/v1/auth/refresh', '/api/v1/auth/refresh', 200, { body: { refreshToken: register.json.refreshToken } });
    await call('POST', '/api/v1/auth/refresh', '/api/v1/auth/refresh', 401, { body: { refreshToken: register.json.refreshToken } });
    await call('GET', '/api/v1/auth/me', '/api/v1/auth/me', 200, { token: user });
    await call('GET', '/api/v1/auth/me', '/api/v1/auth/me', 401);
    await call('PATCH', '/api/v1/auth/me', '/api/v1/auth/me', 200, { token: user, body: { nickname: 'Новий нік' } });
    await call('PATCH', '/api/v1/auth/me', '/api/v1/auth/me', 400, { token: user, body: { nickname: 'я' } });
    await call('GET', '/api/v1/auth/export', '/api/v1/auth/export', 200, { token: user });
    const changed = await call('POST', '/api/v1/auth/password', '/api/v1/auth/password', 200, {
      token: refreshed.json.token,
      body: { currentPassword: 'contract-password', newPassword: 'another-password' },
    });
    await call('POST', '/api/v1/auth/logout', '/api/v1/auth/logout', 204, { body: { refreshToken: changed.json.refreshToken } });
    await call('DELETE', '/api/v1/auth/me', '/api/v1/auth/me', 401, { token: changed.json.token, body: { password: 'wrong-password' } });
  });

  it('каталог і рейтинг', async () => {
    await call('GET', '/api/v1/songs', '/api/v1/songs?limit=5', 200);
    await call('GET', '/api/v1/songs', '/api/v1/songs?limit=0', 400);
    await call('GET', '/api/v1/songs/{slug}', '/api/v1/songs/morning-wave', 200);
    await call('GET', '/api/v1/songs/{slug}', '/api/v1/songs/unknown-song', 404);
    await call('GET', '/api/v1/songs/{slug}/leaderboard', '/api/v1/songs/morning-wave/leaderboard', 200);
    await call('GET', '/api/v1/songs/{slug}/leaderboard', '/api/v1/songs/unknown-song/leaderboard', 404);
    await call('GET', '/api/v1/leaderboard', '/api/v1/leaderboard', 200);
    await call('GET', '/api/v1/leaderboard', '/api/v1/leaderboard?limit=0', 400);
    const input = {
      slug: 'contract-song',
      title: 'Контрактна',
      artist: 'Тест',
      durationMs: 30000,
      genre: 'Поп',
      lyrics: '[00:01.00]рядок',
      audioSource: 'demo:contract-song',
      coverUrl: 'https://img.example/cover.jpg',
    };
    await call('POST', '/api/v1/songs', '/api/v1/songs', 201, { token: admin, body: input });
    await call('POST', '/api/v1/songs', '/api/v1/songs', 409, { token: admin, body: input });
    await call('POST', '/api/v1/songs', '/api/v1/songs', 403, { token: user, body: input });
    await call('POST', '/api/v1/songs', '/api/v1/songs', 401, { body: input });
    await call('POST', '/api/v1/songs', '/api/v1/songs', 400, { token: admin, body: { slug: 'x' } });
    await call('PUT', '/api/v1/songs/{slug}', '/api/v1/songs/contract-song', 200, { token: admin, body: { ...input, title: 'Змінена' } });
    await call('PUT', '/api/v1/songs/{slug}', '/api/v1/songs/missing-one', 404, { token: admin, body: input });
    await call('GET', '/api/v1/songs', '/api/v1/songs?since=0&limit=200', 200);
    await call('DELETE', '/api/v1/songs/{slug}', '/api/v1/songs/contract-song', 204, { token: admin });
    await call('DELETE', '/api/v1/songs/{slug}', '/api/v1/songs/contract-song', 404, { token: admin });
    await call('GET', '/api/v1/songs', '/api/v1/songs?since=1&limit=200', 200);
  });

  it('синхронізація й записи голосу', async () => {
    const now = Date.now();
    await call('POST', '/api/v1/sync/push', '/api/v1/sync/push', 200, {
      token: user,
      body: {
        changes: [
          { type: 'favorite', op: 'upsert', songSlug: 'neon-dance', at: now },
          { type: 'favorite', op: 'upsert', songSlug: 'unknown-song', at: now },
          { type: 'performance', op: 'upsert', clientId: 'contract-perf-01', songSlug: 'morning-wave', performedAt: now - 1000, durationMs: 48000, score: 88, at: now },
          { type: 'performance', op: 'upsert', clientId: 'contract-perf-02', songSlug: 'night-city', performedAt: now - 2000, durationMs: 50000, score: null, at: now },
        ],
      },
    });
    await call('POST', '/api/v1/sync/push', '/api/v1/sync/push', 400, { token: user, body: { changes: 'oops' } });
    await call('POST', '/api/v1/sync/push', '/api/v1/sync/push', 401, { body: { changes: [] } });
    await call('GET', '/api/v1/sync/pull', '/api/v1/sync/pull?since=0', 200, { token: user });
    await call('GET', '/api/v1/sync/pull', '/api/v1/sync/pull', 401);
    await call('GET', '/api/v1/songs/{slug}/leaderboard', '/api/v1/songs/morning-wave/leaderboard', 200, { token: user });
    await call('GET', '/api/v1/leaderboard', '/api/v1/leaderboard', 200, { token: user });

    const audio = Buffer.concat([Buffer.from([0, 0, 0, 24]), Buffer.from('ftypM4A '), Buffer.alloc(100, 1)]);
    const putUrl = '/api/v1/recordings/contract-perf-01';
    const put = (expected) => call('PUT', '/api/v1/recordings/{clientId}', putUrl, expected, { token: user, raw: audio, headers: { 'Content-Type': 'audio/mp4' } });
    await put(201);
    await put(200);
    await call('PUT', '/api/v1/recordings/{clientId}', '/api/v1/recordings/contract-perf-02', 415, {
      token: user,
      raw: audio,
      headers: { 'Content-Type': 'text/plain' },
    });
    await call('PUT', '/api/v1/recordings/{clientId}', '/api/v1/recordings/contract-perf-99', 409, {
      token: user,
      raw: audio,
      headers: { 'Content-Type': 'audio/mp4' },
    });
    await call('PUT', '/api/v1/recordings/{clientId}', putUrl, 401, { raw: audio, headers: { 'Content-Type': 'audio/mp4' } });
    await call('GET', '/api/v1/recordings', '/api/v1/recordings', 200, { token: user });
    await call('GET', '/api/v1/recordings/{clientId}', putUrl, 200, { token: user });
    await call('GET', '/api/v1/recordings/{clientId}', '/api/v1/recordings/contract-perf-02', 404, { token: user });
    await call('DELETE', '/api/v1/recordings/{clientId}', putUrl, 204, { token: user });
    await call('DELETE', '/api/v1/recordings/{clientId}', putUrl, 404, { token: user });
  });

  it('сторонні сервіси', async () => {
    const query = `title=${encodeURIComponent('Пісня')}&artist=${encodeURIComponent('Виконавець')}`;
    await call('GET', '/api/v1/integrations/lyrics', `/api/v1/integrations/lyrics?${query}`, 200, { token: user });
    await call('GET', '/api/v1/integrations/lyrics', `/api/v1/integrations/lyrics?${query}`, 200, { token: user });
    await call('GET', '/api/v1/integrations/lyrics', '/api/v1/integrations/lyrics', 400, { token: user });
    await call('GET', '/api/v1/integrations/lyrics', `/api/v1/integrations/lyrics?${query}`, 401);
    upstream.on('/api/get', (req, res) => sendJson(res, 404, {}));
    await call('GET', '/api/v1/integrations/lyrics', `/api/v1/integrations/lyrics?title=${encodeURIComponent('Немає такої')}&artist=X`, 200, { token: user });
    await call('GET', '/api/v1/integrations/metadata', `/api/v1/integrations/metadata?q=${encodeURIComponent('Пісня')}`, 200, { token: user });
    await call('GET', '/api/v1/integrations/metadata', '/api/v1/integrations/metadata', 400, { token: user });
    await call('GET', '/api/v1/integrations/metadata', '/api/v1/integrations/metadata?q=x', 401);
    upstream.on('/search', (req, res) => {
      res.writeHead(500);
      res.end();
    });
    await call('GET', '/api/v1/integrations/metadata', `/api/v1/integrations/metadata?q=${encodeURIComponent('Інший запит')}`, 502, { token: user });
  });

  it('адміністративні маршрути', async () => {
    await call('GET', '/api/v1/admin/stats', '/api/v1/admin/stats', 200, { token: admin });
    await call('GET', '/api/v1/admin/stats', '/api/v1/admin/stats', 403, { token: user });
    await call('GET', '/api/v1/admin/stats', '/api/v1/admin/stats', 401);
    await call('GET', '/api/v1/admin/songs', '/api/v1/admin/songs?includeDeleted=true', 200, { token: admin });
    await call('GET', '/api/v1/admin/songs/{slug}', '/api/v1/admin/songs/contract-song', 200, { token: admin });
    await call('GET', '/api/v1/admin/songs/{slug}', '/api/v1/admin/songs/none-such', 404, { token: admin });
    await call('GET', '/api/v1/admin/users', '/api/v1/admin/users', 200, { token: admin });
    await call('GET', '/api/v1/admin/logs', '/api/v1/admin/logs?limit=20', 200, { token: admin });
  });

  it('кожна описана операція перевірена хоча б одним викликом', () => {
    const documented = [];
    for (const [path, item] of Object.entries(spec.paths)) {
      for (const method of METHODS) if (item[method]) documented.push(`${method.toUpperCase()} ${path}`);
    }
    const missing = documented.filter((key) => !checked.has(key));
    assert.deepEqual(missing, []);
  });
});
