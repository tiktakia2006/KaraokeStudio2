import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { inTransaction, migrate, openDatabase } from '../src/db/database.js';
import { MonotonicClock } from '../src/services/clock.js';
import { SongsRepository } from '../src/repositories/songs.js';
import { seedIfEmpty } from '../src/seed/index.js';

describe('база даних сервера', () => {
  it('застосовує міграції один раз', () => {
    const db = openDatabase(':memory:');
    assert.deepEqual(migrate(db), []);
    const versions = db.prepare('SELECT version FROM schema_migrations').all().map((row) => row.version);
    assert.deepEqual(versions, [1, 2]);
    db.close();
  });

  it('вмикає зовнішні ключі та обмеження', () => {
    const db = openDatabase(':memory:');
    assert.throws(() => db.prepare('INSERT INTO favorites (user_id, song_id, client_at, updated_at) VALUES (1, 1, 1, 1)').run());
    db.prepare("INSERT INTO users (username, password_hash, nickname, created_at) VALUES ('u', 'h', 'n', 1)").run();
    db.prepare(
      "INSERT INTO songs (slug, title, artist, search_key, duration_ms, audio_source, created_at, updated_at) VALUES ('s', 't', 'a', 'ta', 1000, 'demo:s', 1, 1)",
    ).run();
    const insert = db.prepare(
      'INSERT INTO performances (user_id, client_id, song_id, performed_at, duration_ms, score, client_at, updated_at) VALUES (1, ?, 1, 1, 1000, ?, 1, ?)',
    );
    insert.run('c1', 50, 10);
    assert.throws(() => insert.run('c1', 50, 11), /UNIQUE/);
    assert.throws(() => insert.run('c2', 101, 12), /CHECK/);
    assert.throws(() => insert.run('c3', 50, 10), /UNIQUE/);
    db.close();
  });

  it('відкочує транзакцію при помилці', () => {
    const db = openDatabase(':memory:');
    assert.throws(() =>
      inTransaction(db, () => {
        db.prepare("INSERT INTO users (username, password_hash, nickname, created_at) VALUES ('a', 'h', 'n', 1)").run();
        throw new Error('fail');
      }),
    );
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 0);
    db.close();
  });

  it('видає унікальні зростаючі мітки навіть при застиглому годиннику', () => {
    const clock = new MonotonicClock(() => 1000, 0);
    assert.deepEqual([clock.stamp(), clock.stamp(), clock.stamp()], [1000, 1001, 1002]);
    const behind = new MonotonicClock(() => 5, 2000);
    assert.equal(behind.stamp(), 2001);
  });

  it('наповнює каталог один раз', () => {
    const db = openDatabase(':memory:');
    const songs = new SongsRepository(db);
    const clock = new MonotonicClock(() => 1, 0);
    assert.equal(seedIfEmpty({ songs, clock }), 10);
    assert.equal(seedIfEmpty({ songs, clock }), 0);
    assert.equal(songs.count(), 10);
    db.close();
  });
});
