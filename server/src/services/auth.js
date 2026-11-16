import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { badRequest, conflict, locked, unauthorized } from '../http/errors.js';
import { hashPassword, verifyPassword } from '../security/password.js';
import { signToken, verifyToken } from '../security/jwt.js';
import { USERNAME_PATTERN, optionalString, requireObject, requireString } from '../validation.js';

const DUMMY_HASH = hashPassword('dummy-password-for-timing');
const sha256 = (value) => createHash('sha256').update(value).digest('hex');

export const publicUser = (user) => ({
  id: user.id,
  username: user.username,
  nickname: user.nickname,
  role: user.role,
});

const passwordField = (value, field) => requireString(value, field, { min: 8, max: 128, trim: false });

export class AuthService {
  constructor({ users, refreshTokens, recordings, config, now }) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.recordings = recordings;
    this.config = config;
    this.now = now;
  }

  issue(user, family = randomUUID()) {
    const current = this.now();
    const ttl = this.config.accessTokenTtlSeconds;
    const token = signToken({ sub: user.id, role: user.role, tv: user.token_version }, this.config.jwtSecret, ttl, current);
    const refreshToken = randomBytes(32).toString('base64url');
    this.refreshTokens.insert({
      userId: user.id,
      tokenHash: sha256(refreshToken),
      family,
      createdAt: current,
      expiresAt: current + this.config.refreshTokenTtlSeconds * 1000,
    });
    return { token, refreshToken, expiresIn: ttl, user: publicUser(user) };
  }

  register(input) {
    const body = requireObject(input);
    const username = requireString(body.username, 'username', { min: 3, max: 24 }).toLowerCase();
    if (!USERNAME_PATTERN.test(username)) {
      throw badRequest('username: дозволені латиниця, цифри, крапка, дефіс і підкреслення', { field: 'username' });
    }
    const password = passwordField(body.password, 'password');
    const nickname = optionalString(body.nickname, 'nickname', { min: 2, max: 24 }) ?? username;
    if (this.users.findByUsername(username)) throw conflict('username_taken', 'Таке ім’я користувача вже зайняте');
    const role = this.config.adminUsernames.includes(username) ? 'admin' : 'user';
    let id;
    try {
      id = this.users.create({ username, passwordHash: hashPassword(password), nickname, role }, this.now());
    } catch (error) {
      if (/UNIQUE/i.test(error.message)) throw conflict('username_taken', 'Таке ім’я користувача вже зайняте');
      throw error;
    }
    return this.issue(this.users.findById(id));
  }

  login(input) {
    const body = requireObject(input);
    const username = requireString(body.username, 'username', { max: 24 }).toLowerCase();
    const password = requireString(body.password, 'password', { max: 128, trim: false });
    const user = this.users.findByUsername(username);
    const current = this.now();
    if (user && user.locked_until > current) throw locked(Math.ceil((user.locked_until - current) / 1000));
    const valid = verifyPassword(password, user ? user.password_hash : DUMMY_HASH);
    if (!user || !valid) {
      if (user) this.registerFailure(user, current);
      throw unauthorized('Невірне ім’я користувача або пароль');
    }
    if (user.failed_logins > 0) this.users.resetFailures(user.id);
    return this.issue(user);
  }

  registerFailure(user, current) {
    const failures = user.failed_logins + 1;
    if (failures >= this.config.maxFailedLogins) {
      this.users.lock(user.id, current + this.config.lockoutSeconds * 1000);
    } else {
      this.users.setFailures(user.id, failures);
    }
  }

  refresh(input) {
    const body = requireObject(input);
    const presented = requireString(body.refreshToken, 'refreshToken', { max: 200, trim: false });
    const row = this.refreshTokens.findByHash(sha256(presented));
    const current = this.now();
    if (!row) throw unauthorized('Сесія недійсна');
    if (row.revoked_at) {
      this.refreshTokens.revokeFamily(row.family, current);
      throw unauthorized('Сесія недійсна');
    }
    if (row.expires_at <= current) throw unauthorized('Сесію завершено, увійдіть знову');
    const user = this.users.findById(row.user_id);
    if (!user) throw unauthorized('Сесія недійсна');
    this.refreshTokens.revoke(row.id, current);
    return this.issue(user, row.family);
  }

  logout(input) {
    const body = requireObject(input ?? {});
    if (typeof body.refreshToken !== 'string') return;
    const row = this.refreshTokens.findByHash(sha256(body.refreshToken));
    if (row) this.refreshTokens.revokeFamily(row.family, this.now());
  }

  authenticate(token) {
    const claims = verifyToken(token, this.config.jwtSecret, this.now());
    if (!claims) return null;
    const user = this.users.findById(claims.sub);
    if (!user) return null;
    if ((claims.tv ?? 0) !== user.token_version) return null;
    return user;
  }

  updateNickname(userId, input) {
    const body = requireObject(input);
    const nickname = requireString(body.nickname, 'nickname', { min: 2, max: 24 });
    this.users.updateNickname(userId, nickname);
    return publicUser(this.users.findById(userId));
  }

  changePassword(user, input) {
    const body = requireObject(input);
    const currentPassword = requireString(body.currentPassword, 'currentPassword', { max: 128, trim: false });
    const newPassword = passwordField(body.newPassword, 'newPassword');
    if (!verifyPassword(currentPassword, user.password_hash)) throw unauthorized('Поточний пароль невірний');
    if (currentPassword === newPassword) {
      throw badRequest('newPassword: новий пароль має відрізнятися від поточного', { field: 'newPassword' });
    }
    this.users.updatePassword(user.id, hashPassword(newPassword));
    this.refreshTokens.revokeAllForUser(user.id, this.now());
    return this.issue(this.users.findById(user.id));
  }

  deleteAccount(user, input) {
    const body = requireObject(input);
    const password = requireString(body.password, 'password', { max: 128, trim: false });
    if (!verifyPassword(password, user.password_hash)) throw unauthorized('Пароль невірний');
    this.recordings.purgeUser(user.id);
    this.users.remove(user.id);
  }
}
