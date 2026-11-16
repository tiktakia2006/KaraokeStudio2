import { tooManyRequests, upstreamFailure } from '../http/errors.js';
import { optionalString, parseQueryInt, requireString } from '../validation.js';

const LRCLIB_ATTRIBUTION = { name: 'LRCLIB', url: 'https://lrclib.net' };
const ITUNES_ATTRIBUTION = {
  name: 'Apple iTunes Search API',
  url: 'https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/',
};

const normalize = (text) => String(text ?? '').trim().toLowerCase().replace(/\s+/g, ' ');

export class IntegrationsService {
  constructor({ cache, lrclib, itunes, itunesThrottle, config, now, logger, metrics }) {
    this.cache = cache;
    this.lrclib = lrclib;
    this.itunes = itunes;
    this.itunesThrottle = itunesThrottle;
    this.metrics = metrics;
    this.config = config;
    this.now = now;
    this.logger = logger;
  }

  async cached(key, produce, ttlSecondsFor, throttle) {
    const current = this.now();
    const hit = this.cache.get(key);
    if (hit && hit.expiresAt > current) {
      this.metrics?.count('integration_cache_hit');
      return { ...hit.value, cached: true };
    }
    if (throttle) {
      const verdict = throttle.take();
      if (!verdict.allowed) {
        this.metrics?.count('integration_throttled');
        if (hit) return { ...hit.value, cached: true, stale: true };
        throw tooManyRequests(verdict.retryAfterSeconds);
      }
    }
    this.metrics?.count('integration_upstream_call');
    try {
      const value = await produce();
      this.cache.set(key, value, current + ttlSecondsFor(value) * 1000);
      return { ...value, cached: false };
    } catch (error) {
      this.logger.warn('upstream failed', { key, reason: error.message });
      if (hit) return { ...hit.value, cached: true, stale: true };
      throw upstreamFailure('Зовнішній сервіс тимчасово недоступний');
    }
  }

  async lyrics(query) {
    const title = requireString(query.get('title'), 'title', { max: 120 });
    const artist = optionalString(query.get('artist'), 'artist', { max: 120 });
    const durationSec = parseQueryInt(query.get('durationSec'), 'durationSec', { min: 1, max: 3600, fallback: undefined });
    const bucket = durationSec ? Math.round(durationSec / 5) : '';
    const key = `lyrics:${normalize(title)}|${normalize(artist)}|${bucket}`;
    return this.cached(
      key,
      async () => {
        const found = await this.lrclib.find({ title, artist, durationSec });
        return found ? { found: true, ...found, attribution: LRCLIB_ATTRIBUTION } : { found: false, attribution: LRCLIB_ATTRIBUTION };
      },
      (value) => (value.found ? this.config.cacheTtlSeconds : this.config.negativeCacheTtlSeconds),
    );
  }

  async metadata(query) {
    const term = requireString(query.get('q'), 'q', { max: 120 });
    const limit = parseQueryInt(query.get('limit'), 'limit', { min: 1, max: 10, fallback: 5 });
    const key = `metadata:${normalize(term)}|${limit}`;
    return this.cached(
      key,
      async () => ({ items: await this.itunes.search({ term, limit }), attribution: ITUNES_ATTRIBUTION }),
      (value) => (value.items.length ? this.config.cacheTtlSeconds : this.config.negativeCacheTtlSeconds),
      this.itunesThrottle,
    );
  }
}
