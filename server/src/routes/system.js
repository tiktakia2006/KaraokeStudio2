import { randomBytes } from 'node:crypto';
import { unauthorized } from '../http/errors.js';
import { html, ok, plainText } from '../http/response.js';
import { ADMIN_HTML } from '../pages/admin.js';
import { PRIVACY_HTML, TERMS_HTML } from '../pages/legal.js';
import { buildOpenApi } from '../openapi.js';

export function registerSystemRoutes(router, { db, repositories, services, config, now, metrics, version }) {
  const spec = buildOpenApi(version);
  router.get(
    '/health',
    () => {
      db.prepare('SELECT 1').get();
      return ok({ status: 'ok', time: now(), db: 'ok' });
    },
    { skipRateLimit: true },
  );
  router.get('/api/v1/meta', () => ok({ apiVersion: 1, serverTime: now(), songs: repositories.songs.count() }));
  router.get('/api/v1/openapi.json', () => ok(spec));
  router.get('/privacy', () => html(PRIVACY_HTML));
  router.get('/terms', () => html(TERMS_HTML));
  router.get('/admin', () => {
    const nonce = randomBytes(16).toString('base64');
    return html(ADMIN_HTML.replaceAll('__NONCE__', nonce), {
      'Content-Security-Policy': `default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`,
    });
  });
  router.get(
    '/metrics',
    (ctx) => {
      const allowed = (config.metricsToken && ctx.bearer === config.metricsToken) || ctx.user?.role === 'admin';
      if (!allowed) throw unauthorized('Потрібен токен метрик або права адміністратора');
      return plainText(metrics.render(services.admin.gauges()), 'text/plain; version=0.0.4; charset=utf-8');
    },
    { auth: 'optional', skipRateLimit: true },
  );
}
