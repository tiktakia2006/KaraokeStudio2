import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildOpenApi } from '../src/openapi.js';
import { validate } from './schema-validator.js';

const spec = buildOpenApi('test');
const schema = (name) => ({ $ref: `#/components/schemas/${name}` });

describe('перевірка валідатора контракту', () => {
  const user = { id: 1, username: 'a', nickname: 'b', role: 'user' };

  it('приймає коректний об’єкт', () => {
    assert.deepEqual(validate(schema('User'), user, spec), []);
  });

  it('помічає зайве, відсутнє й неправильне поле', () => {
    assert.equal(validate(schema('User'), { ...user, extra: 1 }, spec).length, 1);
    const { nickname, ...missing } = user;
    assert.equal(validate(schema('User'), missing, spec).length, 1);
    assert.equal(validate(schema('User'), { ...user, id: '1' }, spec).length, 1);
    assert.equal(validate(schema('User'), { ...user, role: 'root' }, spec).length, 1);
  });

  it('розрізняє варіанти oneOf і допускає null', () => {
    const live = { slug: 's', title: 't', artist: 'a', durationMs: 1, genre: 'g', lyrics: '', audioSource: 'demo:s', coverUrl: null, deleted: false, updatedAt: 1 };
    assert.deepEqual(validate(schema('SongPage'), { items: [live, { slug: 's', deleted: true, updatedAt: 2 }], hasMore: false, nextSince: 2 }, spec), []);
    assert.ok(validate(schema('SongPage'), { items: [{ slug: 's', deleted: false, updatedAt: 2 }], hasMore: false, nextSince: 2 }, spec).length > 0);
  });
});
