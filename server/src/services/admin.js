import fs from 'node:fs';
import { notFound } from '../http/errors.js';
import { parseQueryInt, optionalString } from '../validation.js';

export class AdminService {
  constructor({ db, config, repositories, metrics, logBuffer, now, version }) {
    this.db = db;
    this.config = config;
    this.repositories = repositories;
    this.metrics = metrics;
    this.logBuffer = logBuffer;
    this.now = now;
    this.version = version;
  }

  databaseSize() {
    const pages = this.db.prepare('PRAGMA page_count').get().page_count;
    const size = this.db.prepare('PRAGMA page_size').get().page_size;
    return pages * size;
  }

  gauges() {
    const totals = this.repositories.userData.totals();
    const recordings = this.repositories.recordings.totals();
    return {
      users: this.repositories.users.count(),
      songs: this.repositories.songs.count(),
      performances: totals.performances,
      favorites: totals.favorites,
      recordings_files: recordings.files,
      recordings_bytes: recordings.bytes,
      db_size_bytes: this.databaseSize(),
      active_refresh_tokens: this.repositories.refreshTokens.countActive(this.now()),
    };
  }

  stats() {
    const gauges = this.gauges();
    return {
      version: this.version,
      uptimeSec: Math.floor((this.now() - this.metrics.startedAt) / 1000),
      users: gauges.users,
      songs: gauges.songs,
      deletedSongs: this.repositories.songs.countDeleted(),
      performances: gauges.performances,
      favorites: gauges.favorites,
      recordings: { files: gauges.recordings_files, bytes: gauges.recordings_bytes },
      database: { sizeBytes: gauges.db_size_bytes },
      activeSessions: gauges.active_refresh_tokens,
      recordingsDirExists: fs.existsSync(this.config.recordingsDir),
    };
  }

  songs(query) {
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 200, fallback: 50 });
    const offset = parseQueryInt(query.get('offset'), 'offset', { min: 0, fallback: 0 });
    const text = optionalString(query.get('q'), 'q', { max: 60 });
    const includeDeleted = query.get('includeDeleted') === 'true';
    const rows = this.repositories.songs.listAll({ query: text, limit, offset, includeDeleted });
    return {
      items: rows.map((row) => ({
        id: row.id,
        slug: row.slug,
        title: row.title,
        artist: row.artist,
        durationMs: row.duration_ms,
        genre: row.genre,
        audioSource: row.audio_source,
        coverUrl: row.cover_url ?? null,
        deleted: Boolean(row.deleted_at),
        updatedAt: row.updated_at,
      })),
      limit,
      offset,
    };
  }

  song(slug) {
    const row = this.repositories.songs.findBySlug(slug);
    if (!row) throw notFound('Пісню не знайдено');
    return {
      id: row.id,
      slug: row.slug,
      title: row.title,
      artist: row.artist,
      durationMs: row.duration_ms,
      genre: row.genre,
      lyrics: row.lyrics,
      audioSource: row.audio_source,
      coverUrl: row.cover_url ?? null,
      deleted: Boolean(row.deleted_at),
      updatedAt: row.updated_at,
    };
  }

  users(query) {
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 200, fallback: 50 });
    const offset = parseQueryInt(query.get('offset'), 'offset', { min: 0, fallback: 0 });
    return {
      items: this.repositories.users.list({ limit, offset }).map((row) => ({
        id: row.id,
        username: row.username,
        nickname: row.nickname,
        role: row.role,
        createdAt: row.created_at,
        performances: row.performances,
        favorites: row.favorites,
      })),
      total: this.repositories.users.count(),
      limit,
      offset,
    };
  }

  logs(query) {
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 500, fallback: 100 });
    const level = optionalString(query.get('level'), 'level', { max: 10 }) ?? 'debug';
    return { items: this.logBuffer.recent({ limit, level }) };
  }
}
