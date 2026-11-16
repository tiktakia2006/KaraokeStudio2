export class HttpError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export const badRequest = (message, details) => new HttpError(400, 'validation_error', message, details);
export const unauthorized = (message = 'Потрібна авторизація') => new HttpError(401, 'unauthorized', message);
export const forbidden = (message = 'Недостатньо прав') => new HttpError(403, 'forbidden', message);
export const notFound = (message = 'Ресурс не знайдено') => new HttpError(404, 'not_found', message);
export const conflict = (code, message) => new HttpError(409, code, message);
export const upstreamFailure = (message) => new HttpError(502, 'upstream_error', message);
export const tooManyRequests = (retryAfterSeconds) => {
  const error = new HttpError(429, 'rate_limited', 'Забагато запитів, спробуйте пізніше');
  error.retryAfterSeconds = retryAfterSeconds;
  return error;
};
export const locked = (retryAfterSeconds) => {
  const error = new HttpError(423, 'account_locked', 'Забагато невдалих спроб входу. Спробуйте пізніше');
  error.retryAfterSeconds = retryAfterSeconds;
  return error;
};
export const unsupportedMedia = (message) => new HttpError(415, 'unsupported_media_type', message);
export const payloadTooLarge = (message = 'Тіло запиту завелике') => new HttpError(413, 'payload_too_large', message);
