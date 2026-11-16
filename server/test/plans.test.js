import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { hotStatements, planFor, seedBenchDatabase } from '../tools/profile.js';

const usesIndex = (lines) => lines.some((line) => /USING (COVERING )?INDEX|USING INTEGER PRIMARY KEY|USING PRIMARY KEY/.test(line));
const fullScan = (lines) => lines.some((line) => /^SCAN (?!.*USING)/.test(line) && !/CO-ROUTINE|subquery/i.test(line));

describe('плани виконання гарячих SQL-запитів', () => {
  const db = seedBenchDatabase();
  const hot = hotStatements(db).filter(([name]) => name !== 'рейтинг пісні');
  const userDataLeaderboard = hotStatements(db).find(([name]) => name === 'рейтинг пісні')[1];

  for (const [name, statement] of hot) {
    it(`${name}: використовує індекс`, () => {
      const lines = planFor(db, statement.sourceSQL);
      assert.ok(usesIndex(lines), lines.join(' | '));
      assert.ok(!fullScan(lines), lines.join(' | '));
    });
  }

  it('стрічка змін користувача обирає складений індекс за користувачем і міткою', () => {
    const lines = planFor(db, hot.find(([name]) => name === 'стрічка змін обраного й виконань')[1].sourceSQL).join(' | ');
    assert.match(lines, /idx_favorites_user_updated/);
    assert.match(lines, /idx_performances_user_updated/);
  });

  it('рейтинг пісні користується частковим індексом за піснею й оцінкою', () => {
    const lines = db
      .prepare(`EXPLAIN QUERY PLAN ${userDataLeaderboard.sourceSQL}`)
      .all(1)
      .map((row) => row.detail)
      .join(' | ');
    assert.match(lines, /idx_performances_song_score/);
  });

  it('каталог за міткою since читається за індексом оновлення', () => {
    const lines = db
      .prepare("EXPLAIN QUERY PLAN SELECT * FROM songs WHERE updated_at > ? AND deleted_at IS NULL ORDER BY updated_at ASC LIMIT ?")
      .all(10, 50)
      .map((row) => row.detail)
      .join(' | ');
    assert.match(lines, /idx_songs_updated/);
    assert.ok(!/USE TEMP B-TREE/.test(lines), lines);
  });
});
