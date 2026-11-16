import { created, noContent, ok } from '../http/response.js';
import { publicUser } from '../services/auth.js';

export function registerAuthRoutes(router, { services }) {
  router.post('/api/v1/auth/register', (ctx) => created(services.auth.register(ctx.body)), { limit: 'auth' });
  router.post('/api/v1/auth/login', (ctx) => ok(services.auth.login(ctx.body)), { limit: 'auth' });
  router.post('/api/v1/auth/refresh', (ctx) => ok(services.auth.refresh(ctx.body)), { limit: 'auth' });
  router.post(
    '/api/v1/auth/logout',
    (ctx) => {
      services.auth.logout(ctx.body);
      return noContent();
    },
    { limit: 'auth' },
  );
  router.post('/api/v1/auth/password', (ctx) => ok(services.auth.changePassword(ctx.user, ctx.body)), {
    auth: 'required',
    limit: 'auth',
  });
  router.get('/api/v1/auth/me', (ctx) => ok({ user: publicUser(ctx.user) }), { auth: 'required' });
  router.patch('/api/v1/auth/me', (ctx) => ok({ user: services.auth.updateNickname(ctx.user.id, ctx.body) }), {
    auth: 'required',
  });
  router.delete(
    '/api/v1/auth/me',
    (ctx) => {
      services.auth.deleteAccount(ctx.user, ctx.body);
      return noContent();
    },
    { auth: 'required', limit: 'auth' },
  );
  router.get('/api/v1/auth/export', (ctx) => ok(services.account.exportData(ctx.user)), { auth: 'required' });
}
