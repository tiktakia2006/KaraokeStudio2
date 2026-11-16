import { SEED_SONGS } from './songs.js';

export function seedIfEmpty({ songs, clock }) {
  if (songs.count() > 0) return 0;
  for (const song of SEED_SONGS) songs.insert(song, clock.stamp());
  return SEED_SONGS.length;
}
