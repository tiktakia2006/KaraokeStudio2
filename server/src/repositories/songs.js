const escapeLike = (text) => text.replace(/[\\%_]/g, (char) => `\\${char}`);

export class SongsRepository {
  constructor(db) {
    this.db = db;
    this.bySlugStmt = db.prepare('SELECT * FROM songs WHERE slug = ?');
    this.insertStmt = db.prepare(
      `INSERT INTO songs (slug, title, artist, search_key, duration_ms, genre, lyrics, audio_source, cover_url, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    );
    this.updateStmt = db.prepare(
      `UPDATE songs SET title = ?, artist = ?, search_key = ?, duration_ms = ?, genre = ?, lyrics = ?,
        audio_source = ?, cover_url = ?, updated_at = ?, deleted_at = NULL WHERE id = ?`,
    );
    this.deleteStmt = db.prepare('UPDATE songs SET deleted_at = ?, updated_at = ? WHERE id = ?');
    this.countStmt = db.prepare('SELECT COUNT(*) AS total FROM songs WHERE deleted_at IS NULL');
    this.countDeletedStmt = db.prepare('SELECT COUNT(*) AS total FROM songs WHERE deleted_at IS NOT NULL');
  }

  findBySlug(slug) {
    return this.bySlugStmt.get(slug);
  }

  count() {
    return this.countStmt.get().total;
  }

  countDeleted() {
    return this.countDeletedStmt.get().total;
  }

  listAll({ query, limit, offset, includeDeleted }) {
    const where = [];
    const params = [];
    if (!includeDeleted) where.push('deleted_at IS NULL');
    if (query) {
      where.push("search_key LIKE ? ESCAPE '\\'");
      params.push(`%${escapeLike(query.toLowerCase())}%`);
    }
    params.push(limit, offset);
    const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
    return this.db.prepare(`SELECT * FROM songs ${clause} ORDER BY id ASC LIMIT ? OFFSET ?`).all(...params);
  }

  list({ since, limit, query, genre }) {
    const where = ['updated_at > ?'];
    const params = [since];
    if (since === 0) where.push('deleted_at IS NULL');
    if (query) {
      where.push("search_key LIKE ? ESCAPE '\\'");
      params.push(`%${escapeLike(query.toLowerCase())}%`);
    }
    if (genre) {
      where.push('genre = ?');
      params.push(genre);
    }
    params.push(limit);
    return this.db
      .prepare(`SELECT * FROM songs WHERE ${where.join(' AND ')} ORDER BY updated_at ASC LIMIT ?`)
      .all(...params);
  }

  insert(song, stamp) {
    const result = this.insertStmt.run(
      song.slug,
      song.title,
      song.artist,
      `${song.title} ${song.artist}`.toLowerCase(),
      song.durationMs,
      song.genre,
      song.lyrics,
      song.audioSource,
      song.coverUrl ?? null,
      stamp,
      stamp,
    );
    return Number(result.lastInsertRowid);
  }

  update(id, song, stamp) {
    this.updateStmt.run(
      song.title,
      song.artist,
      `${song.title} ${song.artist}`.toLowerCase(),
      song.durationMs,
      song.genre,
      song.lyrics,
      song.audioSource,
      song.coverUrl ?? null,
      stamp,
      id,
    );
  }

  softDelete(id, stamp) {
    this.deleteStmt.run(stamp, stamp, id);
  }
}
