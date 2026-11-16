import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { backupDatabase } from '../src/maintenance.js';

const dbPath = process.argv[2] ?? path.resolve('data', 'karaoke.db');
const dir = process.argv[3] ?? path.resolve('data', 'backups');
const keep = Number.parseInt(process.argv[4] ?? '7', 10);

const db = new DatabaseSync(dbPath);
db.exec('PRAGMA busy_timeout = 5000');
const result = backupDatabase(db, dir, { keep });
db.close();
console.log(`Копію створено: ${result.file}`);
if (result.removed.length) console.log(`Видалено старі копії: ${result.removed.join(', ')}`);
