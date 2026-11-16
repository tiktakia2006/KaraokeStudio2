export class Router {
  constructor() {
    this.routes = [];
  }

  add(method, pattern, handler, options = {}) {
    const keys = [];
    const source = pattern.replace(/:([A-Za-z_]+)/g, (_, key) => {
      keys.push(key);
      return '([^/]+)';
    });
    this.routes.push({ method, pattern, regex: new RegExp(`^${source}$`), keys, handler, options });
    return this;
  }

  get(pattern, handler, options) {
    return this.add('GET', pattern, handler, options);
  }

  post(pattern, handler, options) {
    return this.add('POST', pattern, handler, options);
  }

  put(pattern, handler, options) {
    return this.add('PUT', pattern, handler, options);
  }

  patch(pattern, handler, options) {
    return this.add('PATCH', pattern, handler, options);
  }

  delete(pattern, handler, options) {
    return this.add('DELETE', pattern, handler, options);
  }

  list() {
    return this.routes.map((route) => ({ method: route.method, pattern: route.pattern, options: route.options }));
  }

  match(method, pathname) {
    const allowed = [];
    for (const route of this.routes) {
      const found = route.regex.exec(pathname);
      if (!found) continue;
      if (route.method !== method) {
        allowed.push(route.method);
        continue;
      }
      const params = {};
      route.keys.forEach((key, index) => {
        params[key] = decodeURIComponent(found[index + 1]);
      });
      return { route, params };
    }
    return { allowed };
  }
}
