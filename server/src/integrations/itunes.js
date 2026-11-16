const upgradeCover = (url) => (typeof url === 'string' ? url.replace(/\/\d+x\d+bb\./, '/600x600bb.') : null);

export function createItunesClient({ baseUrl, getJson }) {
  return {
    async search({ term, limit }) {
      const params = new URLSearchParams({ term, media: 'music', entity: 'song', limit: String(limit) });
      const response = await getJson(`${baseUrl}/search?${params}`);
      if (response.status !== 200 || !Array.isArray(response.data?.results)) return [];
      return response.data.results
        .filter((item) => item.trackName && item.artistName)
        .map((item) => ({
          title: item.trackName,
          artist: item.artistName,
          album: item.collectionName ?? null,
          genre: item.primaryGenreName ?? null,
          durationMs: Number.isFinite(item.trackTimeMillis) ? item.trackTimeMillis : null,
          coverUrl: upgradeCover(item.artworkUrl100),
          source: 'itunes',
        }));
    },
  };
}
