import { HttpError, badRequest } from '../http/errors.js';
import { inTransaction } from '../db/database.js';
import { parseQueryInt, requireInt, requireObject, requireString } from '../validation.js';

const FUTURE_TOLERANCE_MS = 5 * 60 * 1000;
const CLIENT_ID = /^[A-Za-z0-9-]{8,64}$/;

export class SyncService {
  constructor({ db, songs, userData, recordings, clock, config, now }) {
    this.db = db;
    this.songs = songs;
    this.userData = userData;
    this.recordings = recordings;
    this.clock = clock;
    this.config = config;
    this.now = now;
  }

  push(user, input) {
    const body = requireObject(input);
    if (!Array.isArray(body.changes)) throw badRequest('changes: очікується масив', { field: 'changes' });
    if (body.changes.length > this.config.maxSyncChanges) {
      throw badRequest(`changes: не більше ${this.config.maxSyncChanges} змін за запит`, { field: 'changes' });
    }
    const results = inTransaction(this.db, () =>
      body.changes.map((change, index) => this.applyOne(user.id, change, index)),
    );
    return {
      results,
      applied: results.filter((result) => result.status === 'applied').length,
      serverTime: this.now(),
    };
  }

  applyOne(userId, raw, index) {
    try {
      const change = requireObject(raw, `changes[${index}]`);
      const type = requireString(change.type, 'type', { max: 20 });
      const op = requireString(change.op, 'op', { max: 10 });
      const at = requireInt(change.at, 'at', { min: 0 });
      if (at > this.now() + FUTURE_TOLERANCE_MS) return { index, status: 'rejected', reason: 'timestamp_in_future' };
      if (!['upsert', 'delete'].includes(op)) return { index, status: 'rejected', reason: 'unknown_op' };
      if (type === 'favorite') return this.applyFavorite(userId, change, op, at, index);
      if (type === 'performance') return this.applyPerformance(userId, change, op, at, index);
      return { index, status: 'rejected', reason: 'unknown_type' };
    } catch (error) {
      if (error instanceof HttpError) return { index, status: 'rejected', reason: error.message };
      throw error;
    }
  }

  applyFavorite(userId, change, op, at, index) {
    const slug = requireString(change.songSlug, 'songSlug', { max: 60 });
    const song = this.songs.findBySlug(slug);
    if (!song || song.deleted_at) return { index, status: 'rejected', reason: 'unknown_song' };
    const applied = this.userData.applyFavorite(userId, song.id, op === 'delete', at, this.clock.stamp());
    return { index, status: applied ? 'applied' : 'stale' };
  }

  applyPerformance(userId, change, op, at, index) {
    const clientId = requireString(change.clientId, 'clientId', { pattern: CLIENT_ID, max: 64 });
    if (op === 'delete') {
      const status = this.userData.deletePerformance(userId, clientId, at, this.clock.stamp());
      if (status === 'applied') this.recordings?.discard(userId, clientId);
      return { index, status };
    }
    const slug = requireString(change.songSlug, 'songSlug', { max: 60 });
    const song = this.songs.findBySlug(slug);
    if (!song || song.deleted_at) return { index, status: 'rejected', reason: 'unknown_song' };
    const score = change.score === null || change.score === undefined ? null : requireInt(change.score, 'score', { min: 0, max: 100 });
    const performance = {
      clientId,
      songId: song.id,
      performedAt: requireInt(change.performedAt, 'performedAt', { min: 0 }),
      durationMs: requireInt(change.durationMs, 'durationMs', { min: 1, max: 86_400_000 }),
      score,
    };
    const applied = this.userData.applyPerformance(userId, performance, at, this.clock.stamp());
    return { index, status: applied ? 'applied' : 'stale' };
  }

  pull(user, query) {
    const since = parseQueryInt(query.get('since'), 'since', { min: 0, fallback: 0 });
    const limit = this.config.pullPageSize;
    const rows = this.userData.feed(user.id, since, limit);
    const changes = rows.map((row) =>
      row.type === 'favorite'
        ? { type: 'favorite', songSlug: row.song_slug, deleted: row.deleted === 1, updatedAt: row.updated_at }
        : {
            type: 'performance',
            clientId: row.client_id,
            songSlug: row.song_slug,
            performedAt: row.performed_at,
            durationMs: row.duration_ms,
            score: row.score,
            deleted: row.deleted === 1,
            updatedAt: row.updated_at,
          },
    );
    return {
      changes,
      hasMore: rows.length === limit,
      nextSince: rows.length ? rows[rows.length - 1].updated_at : since,
    };
  }
}
