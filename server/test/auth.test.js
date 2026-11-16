import assert from 'node:assert/strict';
import { after, before, describe, it } from 'node:test';
import { signToken } from '../src/security/jwt.js';
import { startServer } from './helpers.js';

describe('автентифікація', () => {
  let s;
  before(async () => {
    s = await startServer();
  });
  after(() => s.close());

  it('реєструє користувача й повертає токен', async () => {
    const { token, user, response } = await s.register('taras', 'password123', 'Тарас');
    assert.equal(response.status, 201);
    assert.ok(token);
    assert.equal(user.username, 'taras');
    assert.equal(user.nickname, 'Тарас');
    assert.equal(user.role, 'user');
    assert.equal(response.json.user.password_hash, undefined);
  });

  it('видає роль admin для імені зі списку адміністраторів', async () => {
    const { user } = await s.register('admin');
    assert.equal(user.role, 'admin');
  });

  it('відхиляє дубль імені без урахування регістру', async () => {
    await s.register('andrii');
    const response = await s.request('POST', '/api/v1/auth/register', {
      body: { username: 'ANDRII', password: 'password123' },
    });
    assert.equal(response.status, 409);
    assert.equal(response.json.error.code, 'username_taken');
  });

  it('перевіряє вхідні дані реєстрації', async () => {
    const short = await s.request('POST', '/api/v1/auth/register', { body: { username: 'valid_name', password: '123' } });
    assert.equal(short.status, 400);
    assert.equal(short.json.error.details.field, 'password');
    const badName = await s.request('POST', '/api/v1/auth/register', { body: { username: 'bad name!', password: 'password123' } });
    assert.equal(badName.status, 400);
    const empty = await s.request('POST', '/api/v1/auth/register', {});
    assert.equal(empty.status, 400);
  });

  it('виконує вхід і відхиляє невірний пароль однаковим повідомленням', async () => {
    await s.register('login_user', 'secret-pass');
    const good = await s.request('POST', '/api/v1/auth/login', { body: { username: 'Login_User', password: 'secret-pass' } });
    assert.equal(good.status, 200);
    assert.ok(good.json.token);
    const wrong = await s.request('POST', '/api/v1/auth/login', { body: { username: 'login_user', password: 'wrong-pass' } });
    const unknown = await s.request('POST', '/api/v1/auth/login', { body: { username: 'nobody', password: 'wrong-pass' } });
    assert.equal(wrong.status, 401);
    assert.equal(unknown.status, 401);
    assert.equal(wrong.json.error.message, unknown.json.error.message);
  });

  it('захищає /auth/me токеном', async () => {
    const { token } = await s.register('me_user');
    assert.equal((await s.request('GET', '/api/v1/auth/me')).status, 401);
    const me = await s.request('GET', '/api/v1/auth/me', { token });
    assert.equal(me.status, 200);
    assert.equal(me.json.user.username, 'me_user');
    const tampered = await s.request('GET', '/api/v1/auth/me', { token: `${token.slice(0, -3)}abc` });
    assert.equal(tampered.status, 401);
  });

  it('відхиляє прострочений токен і токен неіснуючого користувача', async () => {
    const expired = signToken({ sub: 1, role: 'user' }, 'test-secret', -10);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token: expired })).status, 401);
    const ghost = signToken({ sub: 987654, role: 'user' }, 'test-secret', 600);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token: ghost })).status, 401);
    const foreign = signToken({ sub: 1, role: 'user' }, 'other-secret', 600);
    assert.equal((await s.request('GET', '/api/v1/auth/me', { token: foreign })).status, 401);
  });

  it('оновлює нікнейм і перевіряє довжину', async () => {
    const { token } = await s.register('nick_user');
    const changed = await s.request('PATCH', '/api/v1/auth/me', { token, body: { nickname: 'Зірка' } });
    assert.equal(changed.status, 200);
    assert.equal(changed.json.user.nickname, 'Зірка');
    const bad = await s.request('PATCH', '/api/v1/auth/me', { token, body: { nickname: 'я' } });
    assert.equal(bad.status, 400);
  });
});
