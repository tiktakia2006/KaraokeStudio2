import assert from 'node:assert/strict';
import fs from 'node:fs';
import { after, before, describe, it } from 'node:test';
import { startServer } from './helpers.js';

const CLIENT = 'rec-client-0001';
const m4a = (size = 300) => Buffer.concat([Buffer.from([0, 0, 0, 24]), Buffer.from('ftypM4A '), Buffer.alloc(size, 7)]);

describe('записи голосу', () => {
  let s;
  let token;
  before(async () => {
    s = await startServer({ config: { recordingMaxBytes: 2000, recordingQuotaBytes: 3000, recordingMaxFiles: 3 } });
    token = (await s.register('singer')).token;
    await syncPerformance(token, CLIENT);
  });
  after(() => s.close());

  async function syncPerformance(userToken, clientId, extra = {}) {
    const result = await s.request('POST', '/api/v1/sync/push', {
      token: userToken,
      body: {
        changes: [
          {
            type: 'performance',
            op: 'upsert',
            clientId,
            songSlug: 'morning-wave',
            performedAt: Date.now() - 5000,
            durationMs: 48_000,
            score: 70,
            at: Date.now(),
            ...extra,
          },
        ],
      },
    });
    assert.equal(result.json.results[0].status, 'applied');
  }

  const upload = (clientId, body, type = 'audio/mp4', userToken = token) =>
    s.request('PUT', `/api/v1/recordings/${clientId}`, { token: userToken, raw: body, headers: { 'Content-Type': type } });

  it('вимагає авторизації й не читає тіло без неї', async () => {
    const response = await s.request('PUT', `/api/v1/recordings/${CLIENT}`, { raw: m4a(), headers: { 'Content-Type': 'audio/mp4' } });
    assert.equal(response.status, 401);
  });

  it('зберігає запис, віддає його назад і рахує використання', async () => {
    const body = m4a();
    const saved = await upload(CLIENT, body);
    assert.equal(saved.status, 201);
    assert.equal(saved.json.sizeBytes, body.length);
    assert.equal(saved.json.mime, 'audio/mp4');
    const download = await fetch(`${s.baseUrl}/api/v1/recordings/${CLIENT}`, { headers: { Authorization: `Bearer ${token}` } });
    assert.equal(download.status, 200);
    assert.equal(download.headers.get('content-type'), 'audio/mp4');
    assert.deepEqual(Buffer.from(await download.arrayBuffer()), body);
    const list = await s.request('GET', '/api/v1/recordings', { token });
    assert.equal(list.json.items.length, 1);
    assert.equal(list.json.usedBytes, body.length);
    assert.equal(list.json.quotaBytes, 3000);
  });

  it('замінює запис при повторному завантаженні й прибирає старий файл', async () => {
    const before = fs.readdirSync(s.recordingsDir).length;
    const replaced = await upload(CLIENT, m4a(500));
    assert.equal(replaced.status, 200);
    assert.equal(fs.readdirSync(s.recordingsDir).length, before);
    const list = await s.request('GET', '/api/v1/recordings', { token });
    assert.equal(list.json.items.length, 1);
    assert.equal(list.json.usedBytes, m4a(500).length);
  });

  it('відхиляє запис без синхронізованого виконання', async () => {
    const response = await upload('rec-orphan-0001', m4a());
    assert.equal(response.status, 409);
    assert.equal(response.json.error.code, 'performance_not_synced');
  });

  it('перевіряє тип, вміст, розмір і формат ідентифікатора', async () => {
    assert.equal((await upload(CLIENT, m4a(), 'text/plain')).status, 415);
    assert.equal((await upload(CLIENT, Buffer.from('це не аудіо, а звичайний текст довжиною більше дванадцяти байтів'))).status, 415);
    assert.equal((await upload(CLIENT, Buffer.concat([Buffer.from('RIFF....WAVE'), Buffer.alloc(50)]))).status, 415);
    assert.equal((await upload(CLIENT, Buffer.alloc(0))).status, 400);
    assert.equal((await upload(CLIENT, m4a(5000))).status, 413);
    assert.equal((await upload('bad id', m4a())).status, 400);
  });

  it('обмежує квоту місця та кількість файлів', async () => {
    await syncPerformance(token, 'rec-client-0002');
    await syncPerformance(token, 'rec-client-0003');
    await syncPerformance(token, 'rec-client-0004');
    assert.equal((await upload('rec-client-0002', m4a(1500))).status, 201);
    const over = await upload('rec-client-0003', m4a(1500));
    assert.equal(over.status, 409);
    assert.equal(over.json.error.code, 'quota_exceeded');
    assert.equal((await upload('rec-client-0003', m4a(100))).status, 201);
    const tooMany = await upload('rec-client-0004', m4a(50));
    assert.equal(tooMany.status, 409);
    assert.equal(tooMany.json.error.code, 'quota_exceeded');
  });

  it('не віддає чужі записи', async () => {
    const other = (await s.register('other_singer')).token;
    const response = await s.request('GET', `/api/v1/recordings/${CLIENT}`, { token: other });
    assert.equal(response.status, 404);
    assert.equal((await s.request('DELETE', `/api/v1/recordings/${CLIENT}`, { token: other })).status, 404);
  });

  it('видаляє файл разом із записом і разом із виконанням', async () => {
    const removed = await s.request('DELETE', `/api/v1/recordings/rec-client-0002`, { token });
    assert.equal(removed.status, 204);
    assert.equal((await s.request('GET', '/api/v1/recordings/rec-client-0002', { token })).status, 404);
    const filesBefore = fs.readdirSync(s.recordingsDir).length;
    const deleted = await s.request('POST', '/api/v1/sync/push', {
      token,
      body: { changes: [{ type: 'performance', op: 'delete', clientId: CLIENT, at: Date.now() + 10 }] },
    });
    assert.equal(deleted.json.results[0].status, 'applied');
    assert.equal(fs.readdirSync(s.recordingsDir).length, filesBefore - 1);
    assert.equal((await s.request('GET', `/api/v1/recordings/${CLIENT}`, { token })).status, 404);
  });

  it('прибирає файли при видаленні облікового запису', async () => {
    const { token: temp } = await s.register('temp_singer', 'temp-password');
    await syncPerformance(temp, 'temp-client-0001');
    assert.equal((await upload('temp-client-0001', m4a(), 'audio/mp4', temp)).status, 201);
    const filesBefore = fs.readdirSync(s.recordingsDir).length;
    await s.request('DELETE', '/api/v1/auth/me', { token: temp, body: { password: 'temp-password' } });
    assert.equal(fs.readdirSync(s.recordingsDir).length, filesBefore - 1);
  });
});
