export class RefreshTokensRepository {
  constructor(db) {
    this.insertStmt = db.prepare(
      'INSERT INTO refresh_tokens (user_id, token_hash, family, created_at, expires_at) VALUES (?, ?, ?, ?, ?)',
    );
    this.byHashStmt = db.prepare('SELECT * FROM refresh_tokens WHERE token_hash = ?');
    this.revokeStmt = db.prepare('UPDATE refresh_tokens SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL');
    this.revokeFamilyStmt = db.prepare('UPDATE refresh_tokens SET revoked_at = ? WHERE family = ? AND revoked_at IS NULL');
    this.revokeUserStmt = db.prepare('UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL');
    this.purgeStmt = db.prepare('DELETE FROM refresh_tokens WHERE expires_at < ? OR (revoked_at IS NOT NULL AND revoked_at < ?)');
    this.activeStmt = db.prepare(
      'SELECT COUNT(*) AS total FROM refresh_tokens WHERE revoked_at IS NULL AND expires_at > ?',
    );
  }

  insert({ userId, tokenHash, family, createdAt, expiresAt }) {
    this.insertStmt.run(userId, tokenHash, family, createdAt, expiresAt);
  }

  findByHash(hash) {
    return this.byHashStmt.get(hash);
  }

  revoke(id, at) {
    this.revokeStmt.run(at, id);
  }

  revokeFamily(family, at) {
    this.revokeFamilyStmt.run(at, family);
  }

  revokeAllForUser(userId, at) {
    this.revokeUserStmt.run(at, userId);
  }

  purge(now, revokedBefore) {
    return this.purgeStmt.run(now, revokedBefore).changes;
  }

  countActive(now) {
    return this.activeStmt.get(now).total;
  }
}
