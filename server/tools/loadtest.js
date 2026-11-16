import http from 'node:http';
import { performance } from 'node:perf_hooks';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { openDatabase } from '../src/db/database.js';
import { createLogger } from '../src/logger.js';
import { seedIfEmpty } from '../src/seed/index.js';

function parseArgs(argv) {
  const options = { concurrency: 16, duration: 10, users: 12, url: null, assertErrors: null, assertP95: null };
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i].replace(/^--/, '').replace(/-([a-z])/g, (_, ch) => ch.toUpperCase());
    options[key] = key === 'url' ? argv[i + 1] : Number(argv[i + 1]);
  }
  return options;
}

async function startLocalServer() {
  const config = loadConfig({}, {
    dbPath: ':memory:',
    jwtSecret: 'loadtest-secret',
    logLevel: 'silent',
    seedOnStart: false,
    recordingsDir: '/tmp/karaoke-loadtest-recordings',
    rateLimit: { max: 10_000_000, authMax: 10_000_000, integrationsMax: 10_000_000, windowMs: 60_000 },
  });
  const db = openDatabase(':memory:');
  const app = createApp({ config, db, logger: createLogger('silent') });
  seedIfEmpty({ songs: app.repositories.songs, clock: app.clock });
  const server = http.createServer((req, res) => {
    app.handler(req, res).catch(() => res.destroy());
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  return {
    url: `http://127.0.0.1:${server.address().port}`,
    close: async () => {
      server.closeAllConnections();
      await new Promise((resolve) => server.close(resolve));
      db.close();
    },
  };
}

const percentile = (sorted, p) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] : 0);

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const local = options.url ? null : await startLocalServer();
  const base = options.url ?? local.url;
  const slugs = ['morning-wave', 'night-city', 'summer-road', 'rain-window', 'neon-dance', 'quiet-river'];
  const suffix = Date.now().toString(36);

  const sessions = [];
  for (let i = 0; i < options.users; i += 1) {
    const response = await fetch(`${base}/api/v1/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: `load_${suffix}_${i}`, password: 'load-test-password' }),
    });
    if (response.status !== 201) throw new Error(`Не вдалося зареєструвати користувача: ${response.status}`);
    sessions.push((await response.json()).token);
  }

  const scenarios = [
    { name: 'GET /songs (каталог)', weight: 40, run: () => fetch(`${base}/api/v1/songs?limit=50`) },
    { name: 'GET /songs?since (зміни)', weight: 15, run: () => fetch(`${base}/api/v1/songs?since=${Date.now() - 86_400_000}&limit=100`) },
    { name: 'GET /songs/:slug', weight: 15, run: () => fetch(`${base}/api/v1/songs/${slugs[Math.floor(Math.random() * slugs.length)]}`) },
    { name: 'GET /leaderboard', weight: 10, run: () => fetch(`${base}/api/v1/songs/${slugs[Math.floor(Math.random() * slugs.length)]}/leaderboard`) },
    {
      name: 'POST /sync/push',
      weight: 12,
      run: (token) =>
        fetch(`${base}/api/v1/sync/push`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify({
            changes: [
              {
                type: 'performance',
                op: 'upsert',
                clientId: `perf-${Math.random().toString(36).slice(2)}-${Date.now()}`.slice(0, 60),
                songSlug: slugs[Math.floor(Math.random() * slugs.length)],
                performedAt: Date.now() - 1000,
                durationMs: 40_000,
                score: Math.floor(Math.random() * 100),
                at: Date.now(),
              },
            ],
          }),
        }),
    },
    { name: 'GET /sync/pull', weight: 8, run: (token) => fetch(`${base}/api/v1/sync/pull?since=0`, { headers: { Authorization: `Bearer ${token}` } }) },
  ];
  const totalWeight = scenarios.reduce((sum, item) => sum + item.weight, 0);
  const pick = () => {
    let roll = Math.random() * totalWeight;
    for (const item of scenarios) {
      roll -= item.weight;
      if (roll <= 0) return item;
    }
    return scenarios[0];
  };

  const stats = new Map(scenarios.map((item) => [item.name, { latencies: [], errors: 0 }]));
  const deadline = performance.now() + options.duration * 1000;
  const started = performance.now();
  let total = 0;
  let failures = 0;

  async function worker(index) {
    const token = sessions[index % sessions.length];
    while (performance.now() < deadline) {
      const scenario = pick();
      const begin = performance.now();
      try {
        const response = await scenario.run(token);
        await response.arrayBuffer();
        const entry = stats.get(scenario.name);
        entry.latencies.push(performance.now() - begin);
        if (response.status >= 400) {
          entry.errors += 1;
          failures += 1;
        }
      } catch {
        stats.get(scenario.name).errors += 1;
        failures += 1;
      }
      total += 1;
    }
  }

  await Promise.all(Array.from({ length: options.concurrency }, (_, index) => worker(index)));
  const elapsed = (performance.now() - started) / 1000;
  const all = [...stats.values()].flatMap((entry) => entry.latencies).sort((a, b) => a - b);

  console.log(`Цільовий сервер: ${options.url ?? 'локальний (у пам’яті)'}`);
  console.log(`Паралельних клієнтів: ${options.concurrency}, тривалість: ${elapsed.toFixed(1)} с`);
  console.log(`Запитів: ${total}, пропускна здатність: ${(total / elapsed).toFixed(0)} запитів/с, помилок: ${failures}`);
  console.log(`Затримка, мс: p50 ${percentile(all, 50).toFixed(1)}, p95 ${percentile(all, 95).toFixed(1)}, p99 ${percentile(all, 99).toFixed(1)}, max ${(all.at(-1) ?? 0).toFixed(1)}`);
  console.log('\nЗа сценаріями:');
  for (const [name, entry] of stats) {
    const sorted = entry.latencies.sort((a, b) => a - b);
    console.log(
      `  ${name.padEnd(28)} n=${String(sorted.length).padEnd(7)} p50=${percentile(sorted, 50).toFixed(1).padStart(6)}  p95=${percentile(sorted, 95).toFixed(1).padStart(6)}  помилок=${entry.errors}`,
    );
  }

  if (local) await local.close();
  const p95 = percentile(all, 95);
  if (options.assertErrors !== null && failures > options.assertErrors) {
    console.error(`\nПомилок ${failures} більше допустимих ${options.assertErrors}`);
    process.exit(1);
  }
  if (options.assertP95 !== null && p95 > options.assertP95) {
    console.error(`\np95 ${p95.toFixed(1)} мс перевищує поріг ${options.assertP95} мс`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
