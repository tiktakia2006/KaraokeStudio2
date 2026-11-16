import { ok } from '../http/response.js';

export function registerIntegrationRoutes(router, { services }) {
  router.get('/api/v1/integrations/lyrics', async (ctx) => ok(await services.integrations.lyrics(ctx.query)), {
    auth: 'required',
    limit: 'integrations',
  });
  router.get('/api/v1/integrations/metadata', async (ctx) => ok(await services.integrations.metadata(ctx.query)), {
    auth: 'required',
    limit: 'integrations',
  });
}
