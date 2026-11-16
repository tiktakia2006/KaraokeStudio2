const clean = (value) => (typeof value === 'string' ? value.trim() : '');

function toResult(entry) {
  if (!entry || entry.instrumental) return null;
  const synced = clean(entry.syncedLyrics);
  const plain = clean(entry.plainLyrics);
  const lyrics = synced || plain;
  if (!lyrics) return null;
  return {
    source: 'lrclib',
    synced: Boolean(synced),
    lyrics,
    trackName: clean(entry.trackName),
    artistName: clean(entry.artistName),
    durationSec: Number.isFinite(entry.duration) ? Math.round(entry.duration) : null,
  };
}

export function createLrclibClient({ baseUrl, getJson }) {
  return {
    async find({ title, artist, durationSec }) {
      if (artist) {
        const params = new URLSearchParams({ track_name: title, artist_name: artist });
        if (durationSec) params.set('duration', String(durationSec));
        const direct = await getJson(`${baseUrl}/api/get?${params}`);
        const result = direct.status === 200 ? toResult(direct.data) : null;
        if (result) return result;
      }
      const search = new URLSearchParams({ track_name: title });
      if (artist) search.set('artist_name', artist);
      const found = await getJson(`${baseUrl}/api/search?${search}`);
      if (found.status !== 200 || !Array.isArray(found.data)) return null;
      const candidates = found.data.map((entry) => ({ entry, result: toResult(entry) })).filter((item) => item.result);
      if (!candidates.length) return null;
      const distance = (item) =>
        durationSec && item.result.durationSec !== null ? Math.abs(item.result.durationSec - durationSec) : 0;
      const prefersSynced = (item) => (item.result.synced ? 0 : 1);
      candidates.sort((a, b) => distance(a) - distance(b) || prefersSynced(a) - prefersSynced(b));
      return candidates[0].result;
    },
  };
}
