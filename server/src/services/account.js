import { publicUser } from './auth.js';

export class AccountService {
  constructor({ userData, recordings }) {
    this.userData = userData;
    this.recordings = recordings;
  }

  exportData(user) {
    const data = this.userData.exportAll(user.id);
    return {
      user: publicUser(user),
      exportedAt: Date.now(),
      favorites: data.favorites.map((row) => ({ songSlug: row.song_slug, updatedAt: row.updated_at })),
      performances: data.performances.map((row) => ({
        clientId: row.client_id,
        songSlug: row.song_slug,
        performedAt: row.performed_at,
        durationMs: row.duration_ms,
        score: row.score,
      })),
      recordings: this.recordings.list(user).items,
    };
  }
}
