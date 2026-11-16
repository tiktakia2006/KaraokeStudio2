import { notFound, conflict } from '../http/errors.js';
import {
  SLUG_PATTERN,
  optionalString,
  parseQueryInt,
  requireHttpsUrl,
  requireInt,
  requireObject,
  requireString,
} from '../validation.js';

export const songDto = (row) =>
  row.deleted_at
    ? { slug: row.slug, deleted: true, updatedAt: row.updated_at }
    : {
        slug: row.slug,
        title: row.title,
        artist: row.artist,
        durationMs: row.duration_ms,
        genre: row.genre,
        lyrics: row.lyrics,
        audioSource: row.audio_source,
        coverUrl: row.cover_url ?? null,
        deleted: false,
        updatedAt: row.updated_at,
      };

const AUDIO_SOURCE = /^(demo:[a-z0-9-]{2,60}|https:\/\/\S+)$/;

function parseSong(input, slugOverride) {
  const body = requireObject(input);
  return {
    slug: slugOverride ?? requireString(body.slug, 'slug', { pattern: SLUG_PATTERN, max: 60 }),
    title: requireString(body.title, 'title', { max: 80 }),
    artist: requireString(body.artist, 'artist', { max: 80 }),
    durationMs: requireInt(body.durationMs, 'durationMs', { min: 1000, max: 3_600_000 }),
    genre: optionalString(body.genre, 'genre', { max: 30 }) ?? 'Інше',
    lyrics: typeof body.lyrics === 'string' ? requireString(body.lyrics, 'lyrics', { min: 0, max: 20_000, trim: false }) : '',
    audioSource: requireString(body.audioSource, 'audioSource', { pattern: AUDIO_SOURCE, max: 300 }),
    coverUrl: body.coverUrl ? requireHttpsUrl(body.coverUrl, 'coverUrl') : null,
  };
}

export class SongsService {
  constructor({ songs, clock }) {
    this.songs = songs;
    this.clock = clock;
  }

  list(query) {
    const since = parseQueryInt(query.get('since'), 'since', { min: 0, fallback: 0 });
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 200, fallback: 100 });
    const text = optionalString(query.get('q'), 'q', { max: 60 });
    const genre = optionalString(query.get('genre'), 'genre', { max: 30 });
    const rows = this.songs.list({ since, limit: limit + 1, query: text, genre });
    const page = rows.slice(0, limit);
    return {
      items: page.map(songDto),
      hasMore: rows.length > limit,
      nextSince: page.length ? page[page.length - 1].updated_at : since,
    };
  }

  get(slug) {
    const row = this.songs.findBySlug(slug);
    if (!row || row.deleted_at) throw notFound('Пісню не знайдено');
    return songDto(row);
  }

  create(input) {
    const song = parseSong(input);
    if (this.songs.findBySlug(song.slug)) throw conflict('slug_taken', 'Пісня з таким slug уже існує');
    this.songs.insert(song, this.clock.stamp());
    return songDto(this.songs.findBySlug(song.slug));
  }

  update(slug, input) {
    const existing = this.songs.findBySlug(slug);
    if (!existing) throw notFound('Пісню не знайдено');
    const song = parseSong(input, slug);
    this.songs.update(existing.id, song, this.clock.stamp());
    return songDto(this.songs.findBySlug(slug));
  }

  remove(slug) {
    const existing = this.songs.findBySlug(slug);
    if (!existing || existing.deleted_at) throw notFound('Пісню не знайдено');
    this.songs.softDelete(existing.id, this.clock.stamp());
  }
}
