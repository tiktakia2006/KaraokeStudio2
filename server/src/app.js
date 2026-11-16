import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import { readJsonBody, readRawBody } from './http/body.js';
import { HttpError, badRequest, forbidden, tooManyRequests, unauthorized } from './http/errors.js';
import { RateLimiter } from './http/rateLimiter.js';
import { Router } from './http/router.js';
import { fetchJson } from './integrations/httpClient.js';
import { createItunesClient } from './integrations/itunes.js';
import { createLrclibClient } from './integrations/lrclib.js';
import { UpstreamThrottle } from './integrations/throttle.js';
import { LogBuffer } from './logger.js';
import { Metrics } from './metrics.js';
import { CacheRepository } from './repositories/cache.js';
import { RecordingsRepository } from './repositories/recordings.js';
import { RefreshTokensRepository } from './repositories/refreshTokens.js';
import { SongsRepository } from './repositories/songs.js';
import { UserDataRepository } from './repositories/userData.js';
import { UsersRepository } from './repositories/users.js';
import { registerAdminRoutes } from './routes/admin.js';
import { registerAuthRoutes } from './routes/auth.js';
import { registerIntegrationRoutes } from './routes/integrations.js';
import { registerRecordingRoutes } from './routes/recordings.js';
import { registerSongRoutes } from './routes/songs.js';
import { registerSyncRoutes } from './routes/sync.js';
import { registerSystemRoutes } from './routes/system.js';
import { AccountService } from './services/account.js';
import { AdminService } from './services/admin.js';
import { AuthService } from './services/auth.js';
import { MonotonicClock, readMaxStamp } from './services/clock.js';
import { IntegrationsService } from './services/integrations.js';
import { LeaderboardService } from './services/leaderboard.js';
import { RecordingsService } from './services/recordings.js';
import { SongsService } from './services/songs.js';
import { SyncService } from './services/sync.js';

export const SERVER_VERSION = '2.0.0';

const BODY_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function bearerToken(req) {
  const header = req.headers.authorization ?? '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  return match ? match[1] : null;
}

function clientAddress(req, config) {
  if (config.trustProxy) {
    const forwarded = String(req.headers['x-forwarded-for'] ?? '').split(',')[0].trim();
    if (forwarded) return forwarded;
  }
  return req.socket.remoteAddress ?? 'unknown';
}

export function buildContainer({ config, db, logger, now, fetchImpl, metrics, logBuffer }) {
  const clock = new MonotonicClock(now, readMaxStamp(db));
  const repositories = {
    users: new UsersRepository(db),
    refreshTokens: new RefreshTokensRepository(db),
    recordings: new RecordingsRepository(db),
    songs: new SongsRepository(db),
    userData: new UserDataRepository(db),
    cache: new CacheRepository(db),
  };
  const getJson = (url) =>
    fetchJson(url, {
      timeoutMs: config.upstreamTimeoutMs,
      retries: config.upstreamRetries,
      fetchImpl,
      headers: { 'User-Agent': config.userAgent },
    });
  const recordings = new RecordingsService({
    recordings: repositories.recordings,
    userData: repositories.userData,
    config,
    now,
  });
  const auth = new AuthService({
    users: repositories.users,
    refreshTokens: repositories.refreshTokens,
    recordings,
    config,
    now,
  });
  const services = {
    auth,
    recordings,
    account: new AccountService({ userData: repositories.userData, recordings }),
    songs: new SongsService({ songs: repositories.songs, clock }),
    sync: new SyncService({
      db,
      songs: repositories.songs,
      userData: repositories.userData,
      recordings,
      clock,
      config,
      now,
    }),
    leaderboard: new LeaderboardService({ songs: repositories.songs, userData: repositories.userData }),
    integrations: new IntegrationsService({
      cache: repositories.cache,
      lrclib: createLrclibClient({ baseUrl: config.lrclibBaseUrl, getJson }),
      itunes: createItunesClient({ baseUrl: config.itunesBaseUrl, getJson }),
      itunesThrottle: new UpstreamThrottle({ max: config.itunesMaxPerMinute, now }),
      config,
      now,
      logger,
      metrics,
    }),
  };
  services.admin = new AdminService({
    db,
    config,
    repositories,
    metrics,
    logBuffer,
    now,
    version: SERVER_VERSION,
  });
  return { clock, repositories, services };
}

export function createApp({ config, db, logger, now = () => Date.now(), fetchImpl, metrics, logBuffer }) {
  const appMetrics = metrics ?? new Metrics(now);
  const appLogBuffer = logBuffer ?? new LogBuffer(config.logBufferSize ?? 500);
  const container = buildContainer({ config, db, logger, now, fetchImpl, metrics: appMetrics, logBuffer: appLogBuffer });
  const { services, repositories } = container;
  const router = new Router();
  const deps = { services, db, repositories, now, config, metrics: appMetrics, version: SERVER_VERSION };
  registerSystemRoutes(router, deps);
  registerAuthRoutes(router, deps);
  registerSongRoutes(router, deps);
  registerSyncRoutes(router, deps);
  registerIntegrationRoutes(router, deps);
  registerRecordingRoutes(router, deps);
  registerAdminRoutes(router, deps);
  const limiter = new RateLimiter(config.rateLimit.windowMs, now);

  function send(req, res, result) {
    const headers = {
      'Content-Type': result.contentType ?? 'application/json; charset=utf-8',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
      'X-Frame-Options': 'DENY',
      'Cache-Control': 'no-store',
      ...result.headers,
    };
    if (result.file) {
      headers['Content-Length'] = String(result.file.size);
      res.writeHead(result.status, headers);
      const stream = fs.createReadStream(result.file.path);
      stream.on('error', () => res.destroy());
      stream.pipe(res);
      return;
    }
    let status = result.status;
    let payload;
    if (status === 204) payload = '';
    else if (result.text !== undefined) payload = result.text;
    else payload = result.body === undefined ? '' : JSON.stringify(result.body);
    if (result.etag && status === 200) {
      const etag = `W/"${createHash('sha1').update(payload).digest('base64url').slice(0, 22)}"`;
      headers.ETag = etag;
      headers['Cache-Control'] = 'private, max-age=0, must-revalidate';
      if (req.headers['if-none-match'] === etag) {
        status = 304;
        payload = '';
      }
    }
    res.writeHead(status, headers);
    res.end(payload);
  }

  function errorResult(error, requestId) {
    if (error instanceof HttpError) {
      const headers = {};
      if (error.retryAfterSeconds) headers['Retry-After'] = String(error.retryAfterSeconds);
      return {
        status: error.status,
        headers,
        body: { error: { code: error.code, message: error.message, details: error.details }, requestId },
      };
    }
    logger.error('unhandled error', { requestId, stack: error.stack });
    return {
      status: 500,
      headers: {},
      body: { error: { code: 'internal_error', message: 'Внутрішня помилка сервера' }, requestId },
    };
  }

  function limitScope(route) {
    if (route.options.limit === 'auth') return { scope: 'auth', max: config.rateLimit.authMax };
    if (route.options.limit === 'integrations') return { scope: 'integrations', max: config.rateLimit.integrationsMax };
    return { scope: 'api', max: config.rateLimit.max };
  }

  async function handler(req, res) {
    const started = Date.now();
    const requestId = randomUUID();
    res.setHeader('X-Request-Id', requestId);
    let status = 500;
    let pathname = req.url;
    let routeLabel = 'unmatched';
    let bodyConsumed = false;
    try {
      let url;
      try {
        url = new URL(req.url, 'http://localhost');
      } catch {
        throw badRequest('Некоректна адреса запиту');
      }
      pathname = url.pathname;
      let match;
      try {
        match = router.match(req.method, pathname);
      } catch {
        throw badRequest('Некоректне кодування адреси');
      }
      if (!match.route) {
        if (match.allowed.length) {
          res.setHeader('Allow', [...new Set(match.allowed)].join(', '));
          throw new HttpError(405, 'method_not_allowed', 'Метод не підтримується');
        }
        throw new HttpError(404, 'not_found', 'Маршрут не знайдено');
      }
      const { route, params } = match;
      routeLabel = route.pattern;
      if (!route.options.skipRateLimit) {
        const { scope, max } = limitScope(route);
        const verdict = limiter.hit(`${scope}:${clientAddress(req, config)}`, max);
        res.setHeader('X-RateLimit-Limit', String(verdict.limit));
        res.setHeader('X-RateLimit-Remaining', String(verdict.remaining));
        if (!verdict.allowed) throw tooManyRequests(verdict.retryAfterSeconds);
      }
      const bearer = bearerToken(req);
      let user = null;
      if (route.options.auth) {
        user = bearer ? services.auth.authenticate(bearer) : null;
        if (route.options.auth !== 'optional' && !user) throw unauthorized();
        if (route.options.auth === 'admin' && user.role !== 'admin') throw forbidden();
      }
      let body;
      let rawBody;
      if (route.options.raw) {
        bodyConsumed = true;
        rawBody = await readRawBody(req, config.recordingMaxBytes);
      } else if (BODY_METHODS.has(req.method)) {
        bodyConsumed = true;
        body = await readJsonBody(req, config.maxBodyBytes);
      }
      const ctx = { req, url, query: url.searchParams, params, body, rawBody, user, bearer, requestId, config };
      const result = await route.handler(ctx);
      status = result.status;
      send(req, res, result);
    } catch (error) {
      const result = errorResult(error, requestId);
      status = result.status;
      const unread = !bodyConsumed && Number.parseInt(req.headers['content-length'] ?? '0', 10) > 0;
      if (status === 413 || unread) res.setHeader('Connection', 'close');
      send(req, res, result);
    } finally {
      const ms = Date.now() - started;
      appMetrics.observe({ method: req.method, route: routeLabel, status, ms });
      logger.info('request', {
        requestId,
        method: req.method,
        path: pathname,
        status,
        ms,
        ip: clientAddress(req, config),
      });
    }
  }

  return { handler, ...container, router, metrics: appMetrics, logBuffer: appLogBuffer };
}
