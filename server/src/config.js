import path from 'node:path';

const toInt = (value, fallback) => {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export function loadConfig(env = process.env, overrides = {}) {
  const base = {
    port: toInt(env.PORT, 8080),
    host: env.HOST ?? '0.0.0.0',
    dbPath: env.DB_PATH ?? path.resolve('data', 'karaoke.db'),
    jwtSecret: env.JWT_SECRET ?? 'dev-secret-change-me',
    accessTokenTtlSeconds: toInt(env.ACCESS_TOKEN_TTL_SECONDS, 3600),
    refreshTokenTtlSeconds: toInt(env.REFRESH_TOKEN_TTL_SECONDS, 30 * 24 * 3600),
    maxFailedLogins: toInt(env.MAX_FAILED_LOGINS, 5),
    lockoutSeconds: toInt(env.LOCKOUT_SECONDS, 900),
    adminUsernames: (env.ADMIN_USERNAMES ?? 'admin')
      .split(',')
      .map((name) => name.trim().toLowerCase())
      .filter(Boolean),
    rateLimit: {
      windowMs: toInt(env.RATE_LIMIT_WINDOW_MS, 60_000),
      max: toInt(env.RATE_LIMIT_MAX, 240),
      authMax: toInt(env.RATE_LIMIT_AUTH_MAX, 20),
      integrationsMax: toInt(env.RATE_LIMIT_INTEGRATIONS_MAX, 30),
    },
    trustProxy: env.TRUST_PROXY === 'true',
    lrclibBaseUrl: env.LRCLIB_URL ?? 'https://lrclib.net',
    itunesBaseUrl: env.ITUNES_URL ?? 'https://itunes.apple.com',
    userAgent: env.UPSTREAM_USER_AGENT ?? 'KaraokeStudio/1.0 (+https://github.com/tiktakia2006/KaraokeStudio)',
    itunesMaxPerMinute: toInt(env.ITUNES_MAX_PER_MINUTE, 20),
    upstreamTimeoutMs: toInt(env.UPSTREAM_TIMEOUT_MS, 6000),
    upstreamRetries: toInt(env.UPSTREAM_RETRIES, 1),
    cacheTtlSeconds: toInt(env.CACHE_TTL_SECONDS, 86_400),
    negativeCacheTtlSeconds: toInt(env.NEGATIVE_CACHE_TTL_SECONDS, 3_600),
    logLevel: env.LOG_LEVEL ?? 'info',
    seedOnStart: env.SEED_ON_START !== 'false',
    maxBodyBytes: 1_000_000,
    recordingsDir: env.RECORDINGS_DIR ?? path.resolve('data', 'recordings'),
    recordingMaxBytes: toInt(env.RECORDING_MAX_BYTES, 10 * 1024 * 1024),
    recordingQuotaBytes: toInt(env.RECORDING_QUOTA_BYTES, 200 * 1024 * 1024),
    recordingMaxFiles: toInt(env.RECORDING_MAX_FILES, 200),
    metricsToken: env.METRICS_TOKEN ?? '',
    logBufferSize: toInt(env.LOG_BUFFER_SIZE, 500),
    backupDir: env.BACKUP_DIR ?? path.resolve('data', 'backups'),
    backupIntervalHours: toInt(env.BACKUP_INTERVAL_HOURS, 24),
    backupKeep: toInt(env.BACKUP_KEEP, 7),
    maintenanceIntervalMinutes: toInt(env.MAINTENANCE_INTERVAL_MINUTES, 60),
    maxSyncChanges: 200,
    pullPageSize: 500,
  };
  return { ...base, ...overrides, rateLimit: { ...base.rateLimit, ...(overrides.rateLimit ?? {}) } };
}
