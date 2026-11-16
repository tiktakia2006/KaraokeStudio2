import { created, json, ok, withEtag } from '../http/response.js';

export function registerSongRoutes(router, { services }) {
  router.get('/api/v1/songs', (ctx) => withEtag(ok(services.songs.list(ctx.query))));
  router.get('/api/v1/leaderboard', (ctx) => ok(services.leaderboard.global(ctx.query, ctx.user)), {
    auth: 'optional',
  });
  router.get('/api/v1/songs/:slug', (ctx) => withEtag(ok(services.songs.get(ctx.params.slug))));
  router.get(
    '/api/v1/songs/:slug/leaderboard',
    (ctx) => ok(services.leaderboard.get(ctx.params.slug, ctx.query, ctx.user)),
    { auth: 'optional' },
  );
  router.post('/api/v1/songs', (ctx) => created(services.songs.create(ctx.body)), { auth: 'admin' });
  router.put(
    '/api/v1/songs/:slug',
    (ctx) => ok(services.songs.update(ctx.params.slug, ctx.body)),
    { auth: 'admin' },
  );
  router.delete(
    '/api/v1/songs/:slug',
    (ctx) => {
      services.songs.remove(ctx.params.slug);
      return json(204);
    },
    { auth: 'admin' },
  );
}
