import { HttpError, badRequest, payloadTooLarge, unsupportedMedia } from './errors.js';

async function collect(req, maxBytes) {
  const declared = Number.parseInt(req.headers['content-length'] ?? '0', 10);
  if (declared > maxBytes) throw payloadTooLarge();
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBytes) throw payloadTooLarge();
    chunks.push(chunk);
  }
  return { size, buffer: Buffer.concat(chunks) };
}

export async function readJsonBody(req, maxBytes) {
  const { size, buffer } = await collect(req, maxBytes);
  if (size === 0) return undefined;
  const type = String(req.headers['content-type'] ?? '').split(';')[0].trim().toLowerCase();
  if (type !== 'application/json') throw unsupportedMedia('Очікується Content-Type: application/json');
  try {
    return JSON.parse(buffer.toString('utf8'));
  } catch {
    throw badRequest('Некоректний JSON у тілі запиту');
  }
}

export async function readRawBody(req, maxBytes) {
  const { buffer } = await collect(req, maxBytes);
  return buffer;
}

export { HttpError };
