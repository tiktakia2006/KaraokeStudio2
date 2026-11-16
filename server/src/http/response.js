export const json = (status, body, headers = {}) => ({ status, body, headers });
export const ok = (body, extra = {}) => json(200, body, extra.headers ?? {});
export const created = (body) => json(201, body);
export const noContent = () => json(204);
export const withEtag = (result) => ({ ...result, etag: true });
export const html = (text, headers = {}) => ({ status: 200, text, contentType: 'text/html; charset=utf-8', headers });
export const plainText = (text, contentType = 'text/plain; charset=utf-8', headers = {}) => ({
  status: 200,
  text,
  contentType,
  headers,
});
export const file = (path, size, contentType, headers = {}) => ({ status: 200, file: { path, size }, contentType, headers });
