import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { openDatabase } from '../src/db/database.js';
import { LogBuffer, createLogger } from '../src/logger.js';
import { seedIfEmpty } from '../src/seed/index.js';

export async function startServer({ config = {}, now, fetchImpl, seed = true } = {}) {
  const recordingsDir = fs.mkdtempSync(path.join(os.tmpdir(), 'karaoke-rec-'));
  const cfg = loadConfig({}, {
    dbPath: ':memory:',
    recordingsDir,
    jwtSecret: 'test-secret',
    logLevel: 'silent',
    upstreamTimeoutMs: 400,
    upstreamRetries: 0,
    ...config,
    rateLimit: { max: 100_000, authMax: 100_000, windowMs: 60_000, ...(config.rateLimit ?? {}) },
  });
  const db = openDatabase(':memory:');
  const logBuffer = new LogBuffer(200);
  const app = createApp({ config: cfg, db, logger: createLogger('info', () => {}, logBuffer), now, fetchImpl, logBuffer });
  if (seed) seedIfEmpty({ songs: app.repositories.songs, clock: app.clock });
  const server = http.createServer((req, res) => {
    app.handler(req, res).catch(() => res.destroy());
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;

  async function request(method, requestPath, { body, token, headers = {}, raw } = {}) {
    const init = { method, headers: { ...headers } };
    if (token) init.headers.Authorization = `Bearer ${token}`;
    if (raw !== undefined) {
      init.body = raw;
    } else if (body !== undefined) {
      init.body = JSON.stringify(body);
      init.headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(baseUrl + requestPath, init);
    const text = await response.text();
    let json = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      json = null;
    }
    return { status: response.status, headers: response.headers, json, text };
  }

  async function register(username, password = 'password123', nickname) {
    const response = await request('POST', '/api/v1/auth/register', { body: { username, password, nickname } });
    return {
      token: response.json?.token,
      refreshToken: response.json?.refreshToken,
      user: response.json?.user,
      response,
    };
  }

  return {
    baseUrl,
    request,
    register,
    db,
    app,
    config: cfg,
    logBuffer,
    recordingsDir,
    close: async () => {
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
      db.close();
      fs.rmSync(recordingsDir, { recursive: true, force: true });
    },
  };
}

export async function startUpstream() {
  const state = { hits: [], routes: new Map() };
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://upstream');
    state.hits.push({ path: url.pathname, params: Object.fromEntries(url.searchParams) });
    const handler = state.routes.get(url.pathname);
    if (handler) return handler(req, res, url);
    res.writeHead(404);
    return res.end();
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    baseUrl: `http://127.0.0.1:${server.address().port}`,
    state,
    on: (path, handler) => state.routes.set(path, handler),
    hitsFor: (path) => state.hits.filter((hit) => hit.path === path),
    close: async () => {
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
    },
  };
}

export const sendJson = (res, status, data) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(data));
};
