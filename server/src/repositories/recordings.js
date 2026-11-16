export class RecordingsRepository {
  constructor(db) {
    this.byClientStmt = db.prepare('SELECT * FROM recordings WHERE user_id = ? AND client_id = ?');
    this.upsertStmt = db.prepare(
      `INSERT INTO recordings (user_id, client_id, mime, size_bytes, sha256, file_name, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT (user_id, client_id) DO UPDATE SET
         mime = excluded.mime, size_bytes = excluded.size_bytes, sha256 = excluded.sha256,
         file_name = excluded.file_name, created_at = excluded.created_at`,
    );
    this.deleteStmt = db.prepare('DELETE FROM recordings WHERE id = ?');
    this.listStmt = db.prepare(
      'SELECT client_id, mime, size_bytes, sha256, created_at FROM recordings WHERE user_id = ? ORDER BY created_at DESC',
    );
    this.usageStmt = db.prepare(
      'SELECT COUNT(*) AS files, COALESCE(SUM(size_bytes), 0) AS bytes FROM recordings WHERE user_id = ?',
    );
    this.forUserStmt = db.prepare('SELECT * FROM recordings WHERE user_id = ?');
    this.totalStmt = db.prepare('SELECT COUNT(*) AS files, COALESCE(SUM(size_bytes), 0) AS bytes FROM recordings');
  }

  find(userId, clientId) {
    return this.byClientStmt.get(userId, clientId);
  }

  upsert(userId, item) {
    this.upsertStmt.run(userId, item.clientId, item.mime, item.sizeBytes, item.sha256, item.fileName, item.createdAt);
  }

  remove(id) {
    this.deleteStmt.run(id);
  }

  list(userId) {
    return this.listStmt.all(userId);
  }

  usage(userId) {
    return this.usageStmt.get(userId);
  }

  allForUser(userId) {
    return this.forUserStmt.all(userId);
  }

  totals() {
    return this.totalStmt.get();
  }
}
