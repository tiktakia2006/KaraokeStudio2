import fs from 'node:fs';
import path from 'node:path';

export function purgeExpired({ repositories, now }) {
  const current = now();
  return {
    cache: repositories.cache.purgeExpired(current),
    refreshTokens: repositories.refreshTokens.purge(current, current - 7 * 24 * 3600 * 1000),
  };
}

export function backupDatabase(db, dir, { keep, now = () => Date.now() }) {
  fs.mkdirSync(dir, { recursive: true });
  const stamp = new Date(now()).toISOString().replace(/[:.]/g, '-');
  const target = path.join(dir, `karaoke-${stamp}.db`);
  if (fs.existsSync(target)) fs.rmSync(target);
  db.exec(`VACUUM INTO '${target.replace(/'/g, "''")}'`);
  const files = fs
    .readdirSync(dir)
    .filter((name) => /^karaoke-.*\.db$/.test(name))
    .sort();
  const removed = [];
  while (files.length > keep) {
    const oldest = files.shift();
    fs.rmSync(path.join(dir, oldest));
    removed.push(oldest);
  }
  return { file: target, removed };
}

export function startMaintenance({ db, repositories, config, logger, now = () => Date.now() }) {
  const timers = [];
  if (config.maintenanceIntervalMinutes > 0) {
    timers.push(
      setInterval(() => {
        try {
          logger.info('maintenance', purgeExpired({ repositories, now }));
        } catch (error) {
          logger.error('maintenance failed', { reason: error.message });
        }
      }, config.maintenanceIntervalMinutes * 60_000),
    );
  }
  if (config.backupIntervalHours > 0) {
    timers.push(
      setInterval(() => {
        try {
          const result = backupDatabase(db, config.backupDir, { keep: config.backupKeep, now });
          logger.info('backup created', { file: result.file, removed: result.removed.length });
        } catch (error) {
          logger.error('backup failed', { reason: error.message });
        }
      }, config.backupIntervalHours * 3_600_000),
    );
  }
  timers.forEach((timer) => timer.unref());
  return () => timers.forEach((timer) => clearInterval(timer));
}
