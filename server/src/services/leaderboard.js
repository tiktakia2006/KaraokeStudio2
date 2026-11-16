import { notFound } from '../http/errors.js';
import { parseQueryInt } from '../validation.js';

export class LeaderboardService {
  constructor({ songs, userData }) {
    this.songs = songs;
    this.userData = userData;
  }

  get(slug, query, user) {
    const song = this.songs.findBySlug(slug);
    if (!song || song.deleted_at) throw notFound('Пісню не знайдено');
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 100, fallback: 10 });
    const rows = this.userData.leaderboard(song.id);
    let previousScore = null;
    let previousRank = 0;
    const ranked = rows.map((row, index) => {
      const rank = row.best_score === previousScore ? previousRank : index + 1;
      previousScore = row.best_score;
      previousRank = rank;
      return {
        rank,
        userId: row.user_id,
        nickname: row.nickname,
        bestScore: row.best_score,
        attempts: row.attempts,
        achievedAt: row.achieved_at,
      };
    });
    const mine = user ? ranked.find((entry) => entry.userId === user.id) : undefined;
    const strip = ({ userId, ...rest }) => rest;
    return {
      items: ranked.slice(0, limit).map(strip),
      total: ranked.length,
      me: mine ? strip(mine) : null,
    };
  }

  global(query, user) {
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 100, fallback: 20 });
    const rows = this.userData.globalLeaderboard();
    let previousScore = null;
    let previousRank = 0;
    const ranked = rows.map((row, index) => {
      const rank = row.total_score === previousScore ? previousRank : index + 1;
      previousScore = row.total_score;
      previousRank = rank;
      return {
        rank,
        userId: row.user_id,
        nickname: row.nickname,
        totalScore: row.total_score,
        songsCount: row.songs_count,
        bestScore: row.best_score,
        attempts: row.attempts,
      };
    });
    const mine = user ? ranked.find((entry) => entry.userId === user.id) : undefined;
    const strip = ({ userId, ...rest }) => rest;
    return {
      items: ranked.slice(0, limit).map(strip),
      total: ranked.length,
      me: mine ? strip(mine) : null,
    };
  }
}
