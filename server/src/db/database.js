import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const defaultMigrationsDir = path.join(path.dirname(fileURLToPath(import.meta.url)), 'migrations');

export function migrate(db, dir = defaultMigrationsDir) {
  db.exec(
    'CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at INTEGER NOT NULL)',
  );
  const applied = new Set(db.prepare('SELECT version FROM schema_migrations').all().map((row) => row.version));
  const files = fs
    .readdirSync(dir)
    .filter((name) => /^\d+_.+\.sql$/.test(name))
    .sort();
  const executed = [];
  for (const file of files) {
    const version = Number.parseInt(file, 10);
    if (applied.has(version)) continue;
    const sql = fs.readFileSync(path.join(dir, file), 'utf8');
    db.exec('BEGIN');
    try {
      db.exec(sql);
      db.prepare('INSERT INTO schema_migrations (version, name, applied_at) VALUES (?, ?, ?)').run(version, file, Date.now());
      db.exec('COMMIT');
      executed.push(file);
    } catch (error) {
      db.exec('ROLLBACK');
      throw new Error(`Міграція ${file} не виконана: ${error.message}`);
    }
  }
  return executed;
}

export function openDatabase(dbPath, migrationsDir = defaultMigrationsDir) {
  if (dbPath !== ':memory:') fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA foreign_keys = ON');
  db.exec('PRAGMA busy_timeout = 5000');
  migrate(db, migrationsDir);
  return db;
}

export function inTransaction(db, work) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = work();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}
