import { ok } from '../http/response.js';

export function registerAdminRoutes(router, { services }) {
  router.get('/api/v1/admin/stats', () => ok(services.admin.stats()), { auth: 'admin' });
  router.get('/api/v1/admin/songs', (ctx) => ok(services.admin.songs(ctx.query)), { auth: 'admin' });
  router.get('/api/v1/admin/songs/:slug', (ctx) => ok(services.admin.song(ctx.params.slug)), { auth: 'admin' });
  router.get('/api/v1/admin/users', (ctx) => ok(services.admin.users(ctx.query)), { auth: 'admin' });
  router.get('/api/v1/admin/logs', (ctx) => ok(services.admin.logs(ctx.query)), { auth: 'admin' });
}
