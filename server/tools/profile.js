import { pathToFileURL } from 'node:url';
import { openDatabase } from '../src/db/database.js';
import { CacheRepository } from '../src/repositories/cache.js';
import { RecordingsRepository } from '../src/repositories/recordings.js';
import { RefreshTokensRepository } from '../src/repositories/refreshTokens.js';
import { SongsRepository } from '../src/repositories/songs.js';
import { UserDataRepository } from '../src/repositories/userData.js';
import { UsersRepository } from '../src/repositories/users.js';

export function seedBenchDatabase({ users = 20, songs = 200, performancesPerUser = 50 } = {}) {
  const db = openDatabase(':memory:');
  db.exec('BEGIN');
  for (let u = 1; u <= users; u += 1) {
    db.prepare("INSERT INTO users (username, password_hash, nickname, created_at) VALUES (?, 'h', ?, 1)").run(`user${u}`, `nick${u}`);
  }
  for (let s = 1; s <= songs; s += 1) {
    db.prepare(
      "INSERT INTO songs (slug, title, artist, search_key, duration_ms, audio_source, created_at, updated_at) VALUES (?, 't', 'a', ?, 1000, 'demo:x', 1, ?)",
    ).run(`song-${s}`, `t${s} a`, s);
  }
  let stamp = 1;
  for (let u = 1; u <= users; u += 1) {
    for (let s = 1; s <= Math.min(performancesPerUser, songs); s += 1) {
      db.prepare(
        'INSERT INTO performances (user_id, client_id, song_id, performed_at, duration_ms, score, client_at, updated_at) VALUES (?, ?, ?, 1, 1000, ?, 1, ?)',
      ).run(u, `c-${u}-${s}`, s, s % 100, stamp);
      stamp += 1;
      db.prepare('INSERT OR IGNORE INTO favorites (user_id, song_id, client_at, updated_at) VALUES (?, ?, 1, ?)').run(u, s, stamp);
      stamp += 1;
    }
  }
  db.exec('COMMIT');
  db.exec('ANALYZE');
  return db;
}

export function planFor(db, sql) {
  const params = new Array((sql.match(/\?/g) ?? []).length).fill(1);
  return db.prepare(`EXPLAIN QUERY PLAN ${sql}`).all(...params).map((row) => row.detail);
}

export function hotStatements(db) {
  const users = new UsersRepository(db);
  const userData = new UserDataRepository(db);
  const songs = new SongsRepository(db);
  const tokens = new RefreshTokensRepository(db);
  const recordings = new RecordingsRepository(db);
  const cache = new CacheRepository(db);
  return [
    ['користувач за іменем', users.byUsernameStmt],
    ['користувач за id', users.byIdStmt],
    ['пісня за slug', songs.bySlugStmt],
    ['стрічка змін обраного й виконань', userData.feedStmt],
    ['виконання за clientId', userData.performanceByClient],
    ['перевірка існування виконання', userData.performanceExists],
    ['рейтинг пісні', userData.leaderboardStmt],
    ['токен оновлення за хешем', tokens.byHashStmt],
    ['запис голосу за clientId', recordings.byClientStmt],
    ['записи користувача', recordings.listStmt],
    ['кеш інтеграцій', cache.getStmt],
    ['очищення кешу', cache.purgeStmt],
  ];
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const db = seedBenchDatabase();
  for (const [name, statement] of hotStatements(db)) {
    console.log(`\n${name}\n${statement.sourceSQL.replace(/\s+/g, ' ').trim()}`);
    for (const line of planFor(db, statement.sourceSQL)) console.log(`  ${line}`);
  }
  db.close();
}
