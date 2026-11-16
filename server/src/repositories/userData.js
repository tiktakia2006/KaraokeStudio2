export class UserDataRepository {
  constructor(db) {
    this.db = db;
    this.favoriteUpsert = db.prepare(
      `INSERT INTO favorites (user_id, song_id, deleted, client_at, updated_at) VALUES (?, ?, ?, ?, ?)
       ON CONFLICT (user_id, song_id) DO UPDATE SET
         deleted = excluded.deleted, client_at = excluded.client_at, updated_at = excluded.updated_at
       WHERE excluded.client_at >= favorites.client_at`,
    );
    this.performanceUpsert = db.prepare(
      `INSERT INTO performances (user_id, client_id, song_id, performed_at, duration_ms, score, deleted, client_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
       ON CONFLICT (user_id, client_id) DO UPDATE SET
         song_id = excluded.song_id, performed_at = excluded.performed_at, duration_ms = excluded.duration_ms,
         score = excluded.score, deleted = 0, client_at = excluded.client_at, updated_at = excluded.updated_at
       WHERE excluded.client_at >= performances.client_at`,
    );
    this.performanceDelete = db.prepare(
      `UPDATE performances SET deleted = 1, client_at = ?, updated_at = ?
       WHERE user_id = ? AND client_id = ? AND client_at <= ?`,
    );
    this.performanceExists = db.prepare('SELECT 1 AS found FROM performances WHERE user_id = ? AND client_id = ?');
    this.performanceByClient = db.prepare('SELECT * FROM performances WHERE user_id = ? AND client_id = ? AND deleted = 0');
    this.exportFavorites = db.prepare(
      `SELECT s.slug AS song_slug, f.updated_at AS updated_at FROM favorites f JOIN songs s ON s.id = f.song_id
       WHERE f.user_id = ? AND f.deleted = 0 ORDER BY f.updated_at ASC`,
    );
    this.exportPerformances = db.prepare(
      `SELECT p.client_id, s.slug AS song_slug, p.performed_at, p.duration_ms, p.score FROM performances p
       JOIN songs s ON s.id = p.song_id WHERE p.user_id = ? AND p.deleted = 0 ORDER BY p.performed_at ASC`,
    );
    this.totalsStmt = db.prepare(
      `SELECT (SELECT COUNT(*) FROM performances WHERE deleted = 0) AS performances,
              (SELECT COUNT(*) FROM favorites WHERE deleted = 0) AS favorites`,
    );
    this.feedStmt = db.prepare(
      `SELECT * FROM (
         SELECT 'favorite' AS type, f.updated_at AS updated_at, s.slug AS song_slug, f.deleted AS deleted,
                NULL AS client_id, NULL AS performed_at, NULL AS duration_ms, NULL AS score
         FROM favorites f JOIN songs s ON s.id = f.song_id
         WHERE f.user_id = ? AND f.updated_at > ?
         UNION ALL
         SELECT 'performance', p.updated_at, s.slug, p.deleted, p.client_id, p.performed_at, p.duration_ms, p.score
         FROM performances p JOIN songs s ON s.id = p.song_id
         WHERE p.user_id = ? AND p.updated_at > ?
       ) ORDER BY updated_at ASC LIMIT ?`,
    );
    this.globalLeaderboardStmt = db.prepare(
      `WITH best AS (
         SELECT p.user_id, p.song_id, MAX(p.score) AS best_score, COUNT(*) AS attempts
         FROM performances p JOIN songs s ON s.id = p.song_id
         WHERE p.deleted = 0 AND p.score IS NOT NULL AND s.deleted_at IS NULL
         GROUP BY p.user_id, p.song_id
       )
       SELECT u.id AS user_id, u.nickname AS nickname, SUM(b.best_score) AS total_score,
              COUNT(*) AS songs_count, MAX(b.best_score) AS best_score, SUM(b.attempts) AS attempts
       FROM best b JOIN users u ON u.id = b.user_id
       GROUP BY u.id
       ORDER BY total_score DESC, songs_count DESC, u.id ASC`,
    );
    this.leaderboardStmt = db.prepare(
      `WITH ranked AS (
         SELECT p.user_id, p.score, p.performed_at,
                ROW_NUMBER() OVER (PARTITION BY p.user_id ORDER BY p.score DESC, p.performed_at ASC) AS rn,
                COUNT(*) OVER (PARTITION BY p.user_id) AS attempts
         FROM performances p
         WHERE p.song_id = ? AND p.deleted = 0 AND p.score IS NOT NULL
       )
       SELECT u.id AS user_id, u.nickname AS nickname, r.score AS best_score, r.performed_at AS achieved_at, r.attempts AS attempts
       FROM ranked r JOIN users u ON u.id = r.user_id
       WHERE r.rn = 1
       ORDER BY r.score DESC, r.performed_at ASC, u.id ASC`,
    );
  }

  applyFavorite(userId, songId, deleted, clientAt, stamp) {
    return this.favoriteUpsert.run(userId, songId, deleted ? 1 : 0, clientAt, stamp).changes > 0;
  }

  applyPerformance(userId, perf, clientAt, stamp) {
    return (
      this.performanceUpsert.run(
        userId,
        perf.clientId,
        perf.songId,
        perf.performedAt,
        perf.durationMs,
        perf.score ?? null,
        clientAt,
        stamp,
      ).changes > 0
    );
  }

  deletePerformance(userId, clientId, clientAt, stamp) {
    const changed = this.performanceDelete.run(clientAt, stamp, userId, clientId, clientAt).changes > 0;
    if (changed) return 'applied';
    return this.performanceExists.get(userId, clientId) ? 'stale' : 'applied';
  }

  findPerformance(userId, clientId) {
    return this.performanceByClient.get(userId, clientId);
  }

  exportAll(userId) {
    return {
      favorites: this.exportFavorites.all(userId),
      performances: this.exportPerformances.all(userId),
    };
  }

  totals() {
    return this.totalsStmt.get();
  }

  feed(userId, since, limit) {
    return this.feedStmt.all(userId, since, userId, since, limit);
  }

  globalLeaderboard() {
    return this.globalLeaderboardStmt.all();
  }

  leaderboard(songId) {
    return this.leaderboardStmt.all(songId);
  }
}
