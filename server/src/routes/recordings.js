import { created, file, noContent, ok } from '../http/response.js';

export function registerRecordingRoutes(router, { services }) {
  router.get('/api/v1/recordings', (ctx) => ok(services.recordings.list(ctx.user)), { auth: 'required' });
  router.put(
    '/api/v1/recordings/:clientId',
    (ctx) => {
      const { replaced, item } = services.recordings.put(ctx.user, ctx.params.clientId, ctx.rawBody, ctx.req.headers['content-type']);
      return replaced ? ok(item) : created(item);
    },
    { auth: 'required', raw: true },
  );
  router.get(
    '/api/v1/recordings/:clientId',
    (ctx) => {
      const row = services.recordings.get(ctx.user, ctx.params.clientId);
      return file(services.recordings.pathFor(row), row.size_bytes, row.mime, {
        ETag: `"${row.sha256}"`,
        'Cache-Control': 'private, max-age=0, must-revalidate',
      });
    },
    { auth: 'required' },
  );
  router.delete(
    '/api/v1/recordings/:clientId',
    (ctx) => {
      services.recordings.remove(ctx.user, ctx.params.clientId);
      return noContent();
    },
    { auth: 'required' },
  );
}
