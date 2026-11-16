import { ok } from '../http/response.js';

export function registerSyncRoutes(router, { services }) {
  router.post('/api/v1/sync/push', (ctx) => ok(services.sync.push(ctx.user, ctx.body)), { auth: 'required' });
  router.get('/api/v1/sync/pull', (ctx) => ok(services.sync.pull(ctx.user, ctx.query)), { auth: 'required' });
}
