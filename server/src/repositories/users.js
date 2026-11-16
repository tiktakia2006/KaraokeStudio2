export class UsersRepository {
  constructor(db) {
    this.db = db;
    this.insertStmt = db.prepare(
      'INSERT INTO users (username, password_hash, nickname, role, created_at) VALUES (?, ?, ?, ?, ?)',
    );
    this.byUsernameStmt = db.prepare('SELECT * FROM users WHERE username = ?');
    this.byIdStmt = db.prepare('SELECT * FROM users WHERE id = ?');
    this.nicknameStmt = db.prepare('UPDATE users SET nickname = ? WHERE id = ?');
    this.failuresStmt = db.prepare('UPDATE users SET failed_logins = ? WHERE id = ?');
    this.lockStmt = db.prepare('UPDATE users SET failed_logins = 0, locked_until = ? WHERE id = ?');
    this.resetStmt = db.prepare('UPDATE users SET failed_logins = 0, locked_until = 0 WHERE id = ?');
    this.passwordStmt = db.prepare(
      'UPDATE users SET password_hash = ?, token_version = token_version + 1, failed_logins = 0, locked_until = 0 WHERE id = ?',
    );
    this.deleteStmt = db.prepare('DELETE FROM users WHERE id = ?');
    this.countStmt = db.prepare('SELECT COUNT(*) AS total FROM users');
    this.listStmt = db.prepare(
      `SELECT u.id, u.username, u.nickname, u.role, u.created_at,
              (SELECT COUNT(*) FROM performances p WHERE p.user_id = u.id AND p.deleted = 0) AS performances,
              (SELECT COUNT(*) FROM favorites f WHERE f.user_id = u.id AND f.deleted = 0) AS favorites
       FROM users u ORDER BY u.id ASC LIMIT ? OFFSET ?`,
    );
  }

  create(user, now) {
    const result = this.insertStmt.run(user.username, user.passwordHash, user.nickname, user.role, now);
    return Number(result.lastInsertRowid);
  }

  findByUsername(username) {
    return this.byUsernameStmt.get(username);
  }

  findById(id) {
    return this.byIdStmt.get(id);
  }

  updateNickname(id, nickname) {
    this.nicknameStmt.run(nickname, id);
  }

  setFailures(id, count) {
    this.failuresStmt.run(count, id);
  }

  lock(id, until) {
    this.lockStmt.run(until, id);
  }

  resetFailures(id) {
    this.resetStmt.run(id);
  }

  updatePassword(id, passwordHash) {
    this.passwordStmt.run(passwordHash, id);
  }

  remove(id) {
    this.deleteStmt.run(id);
  }

  count() {
    return this.countStmt.get().total;
  }

  list({ limit, offset }) {
    return this.listStmt.all(limit, offset);
  }
}
