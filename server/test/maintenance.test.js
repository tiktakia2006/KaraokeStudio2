import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { describe, it } from 'node:test';
import { openDatabase } from '../src/db/database.js';
import { backupDatabase, purgeExpired } from '../src/maintenance.js';
import { CacheRepository } from '../src/repositories/cache.js';
import { RefreshTokensRepository } from '../src/repositories/refreshTokens.js';

describe('технічне обслуговування', () => {
  it('створює придатну до читання копію бази й залишає задану кількість копій', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'karaoke-backup-'));
    const db = openDatabase(':memory:');
    db.prepare("INSERT INTO users (username, password_hash, nickname, created_at) VALUES ('backup_user', 'h', 'n', 1)").run();
    let clock = Date.UTC(2026, 10, 1);
    const made = [];
    for (let i = 0; i < 4; i += 1) {
      made.push(backupDatabase(db, dir, { keep: 3, now: () => clock }));
      clock += 24 * 3600 * 1000;
    }
    const files = fs.readdirSync(dir).filter((name) => name.endsWith('.db')).sort();
    assert.equal(files.length, 3);
    assert.equal(made[3].removed.length, 1);
    const copy = new DatabaseSync(path.join(dir, files[2]));
    assert.equal(copy.prepare('SELECT username FROM users').get().username, 'backup_user');
    assert.equal(copy.prepare('PRAGMA integrity_check').get().integrity_check, 'ok');
    copy.close();
    db.close();
    fs.rmSync(dir, { recursive: true, force: true });
  });

  it('очищає прострочений кеш і непотрібні токени оновлення', () => {
    const db = openDatabase(':memory:');
    db.prepare("INSERT INTO users (username, password_hash, nickname, created_at) VALUES ('u', 'h', 'n', 1)").run();
    const cache = new CacheRepository(db);
    const tokens = new RefreshTokensRepository(db);
    const now = 1_000_000_000;
    cache.set('old', { a: 1 }, now - 1);
    cache.set('fresh', { a: 2 }, now + 1000);
    tokens.insert({ userId: 1, tokenHash: 'expired', family: 'f1', createdAt: 1, expiresAt: now - 1 });
    tokens.insert({ userId: 1, tokenHash: 'active', family: 'f2', createdAt: 1, expiresAt: now + 100_000 });
    tokens.insert({ userId: 1, tokenHash: 'revoked-long-ago', family: 'f3', createdAt: 1, expiresAt: now + 100_000 });
    tokens.revoke(tokens.findByHash('revoked-long-ago').id, now - 30 * 24 * 3600 * 1000);
    const result = purgeExpired({ repositories: { cache, refreshTokens: tokens }, now: () => now });
    assert.deepEqual(result, { cache: 1, refreshTokens: 2 });
    assert.ok(cache.get('fresh'));
    assert.equal(cache.get('old'), null);
    assert.ok(tokens.findByHash('active'));
    db.close();
  });
});
