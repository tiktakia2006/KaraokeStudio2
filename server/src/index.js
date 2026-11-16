import http from 'node:http';
import { createApp } from './app.js';
import { loadConfig } from './config.js';
import { openDatabase } from './db/database.js';
import { LogBuffer, createLogger } from './logger.js';
import { startMaintenance } from './maintenance.js';
import { seedIfEmpty } from './seed/index.js';

const config = loadConfig();
const logBuffer = new LogBuffer(config.logBufferSize);
const logger = createLogger(config.logLevel, undefined, logBuffer);

if (process.env.NODE_ENV === 'production' && config.jwtSecret === 'dev-secret-change-me') {
  logger.error('JWT_SECRET не задано для production');
  process.exit(1);
}

const db = openDatabase(config.dbPath);
const app = createApp({ config, db, logger, logBuffer });

if (config.seedOnStart) {
  const added = seedIfEmpty({ songs: app.repositories.songs, clock: app.clock });
  if (added) logger.info('catalog seeded', { songs: added });
}

const stopMaintenance = startMaintenance({ db, repositories: app.repositories, config, logger });

const server = http.createServer((req, res) => {
  app.handler(req, res).catch((error) => {
    logger.error('fatal handler error', { stack: error.stack });
    res.destroy();
  });
});

server.listen(config.port, config.host, () => {
  logger.info('server started', { host: config.host, port: config.port, db: config.dbPath });
});

function shutdown(signal) {
  logger.info('shutting down', { signal });
  stopMaintenance();
  server.close(() => {
    db.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
