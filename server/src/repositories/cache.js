export class CacheRepository {
  constructor(db) {
    this.getStmt = db.prepare('SELECT payload, expires_at FROM integration_cache WHERE cache_key = ?');
    this.setStmt = db.prepare(
      `INSERT INTO integration_cache (cache_key, payload, expires_at) VALUES (?, ?, ?)
       ON CONFLICT (cache_key) DO UPDATE SET payload = excluded.payload, expires_at = excluded.expires_at`,
    );
    this.purgeStmt = db.prepare('DELETE FROM integration_cache WHERE expires_at < ?');
  }

  get(key) {
    const row = this.getStmt.get(key);
    return row ? { value: JSON.parse(row.payload), expiresAt: row.expires_at } : null;
  }

  set(key, value, expiresAt) {
    this.setStmt.run(key, JSON.stringify(value), expiresAt);
  }

  purgeExpired(now) {
    return this.purgeStmt.run(now).changes;
  }
}
