import { createHash, randomUUID } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { HttpError, badRequest, conflict, notFound, unsupportedMedia } from '../http/errors.js';

const CLIENT_ID = /^[A-Za-z0-9-]{8,64}$/;

const DECLARED_FAMILY = new Map([
  ['audio/mp4', 'mp4'],
  ['audio/m4a', 'mp4'],
  ['audio/x-m4a', 'mp4'],
  ['audio/aac', 'aac'],
  ['audio/mpeg', 'mpeg'],
  ['audio/mp3', 'mpeg'],
  ['audio/wav', 'wav'],
  ['audio/x-wav', 'wav'],
  ['audio/ogg', 'ogg'],
]);

const CANONICAL_MIME = { mp4: 'audio/mp4', aac: 'audio/aac', mpeg: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg' };

export function detectFamily(buffer) {
  if (buffer.length < 12) return null;
  if (buffer.toString('latin1', 4, 8) === 'ftyp') return 'mp4';
  if (buffer.toString('latin1', 0, 4) === 'RIFF' && buffer.toString('latin1', 8, 12) === 'WAVE') return 'wav';
  if (buffer.toString('latin1', 0, 4) === 'OggS') return 'ogg';
  if (buffer.toString('latin1', 0, 3) === 'ID3') return 'mpeg';
  if (buffer[0] === 0xff && (buffer[1] & 0xf6) === 0xf0) return 'aac';
  if (buffer[0] === 0xff && (buffer[1] & 0xe0) === 0xe0) return 'mpeg';
  return null;
}

export class RecordingsService {
  constructor({ recordings, userData, config, now }) {
    this.recordings = recordings;
    this.userData = userData;
    this.config = config;
    this.now = now;
  }

  pathFor(row) {
    return path.join(this.config.recordingsDir, row.file_name);
  }

  checkId(clientId) {
    if (!CLIENT_ID.test(clientId)) throw badRequest('clientId: недопустимий формат', { field: 'clientId' });
  }

  put(user, clientId, buffer, contentType) {
    this.checkId(clientId);
    const declared = DECLARED_FAMILY.get(String(contentType ?? '').split(';')[0].trim().toLowerCase());
    if (!declared) throw unsupportedMedia('Підтримуються лише аудіофайли mp4/m4a, aac, mp3, wav, ogg');
    if (buffer.length === 0) throw badRequest('Порожній файл');
    if (buffer.length > this.config.recordingMaxBytes) {
      throw new HttpError(413, 'payload_too_large', `Файл завеликий: максимум ${this.config.recordingMaxBytes} байтів`);
    }
    const detected = detectFamily(buffer);
    if (!detected || detected !== declared) throw unsupportedMedia('Вміст файлу не відповідає заявленому аудіоформату');
    if (!this.userData.findPerformance(user.id, clientId)) {
      throw conflict('performance_not_synced', 'Спочатку синхронізуйте виконання, до якого належить запис');
    }
    const existing = this.recordings.find(user.id, clientId);
    const usage = this.recordings.usage(user.id);
    const usedBytes = usage.bytes - (existing ? existing.size_bytes : 0);
    const files = usage.files - (existing ? 1 : 0);
    if (files >= this.config.recordingMaxFiles) throw conflict('quota_exceeded', 'Досягнуто ліміт кількості записів');
    if (usedBytes + buffer.length > this.config.recordingQuotaBytes) {
      throw conflict('quota_exceeded', 'Перевищено ліміт місця для записів');
    }
    fs.mkdirSync(this.config.recordingsDir, { recursive: true });
    const fileName = `${user.id}-${randomUUID()}.bin`;
    const target = path.join(this.config.recordingsDir, fileName);
    const temporary = `${target}.part`;
    fs.writeFileSync(temporary, buffer);
    fs.renameSync(temporary, target);
    const sha256 = createHash('sha256').update(buffer).digest('hex');
    const createdAt = this.now();
    this.recordings.upsert(user.id, {
      clientId,
      mime: CANONICAL_MIME[detected],
      sizeBytes: buffer.length,
      sha256,
      fileName,
      createdAt,
    });
    if (existing) this.deleteFile(existing);
    return { replaced: Boolean(existing), item: { clientId, mime: CANONICAL_MIME[detected], sizeBytes: buffer.length, sha256, createdAt } };
  }

  get(user, clientId) {
    this.checkId(clientId);
    const row = this.recordings.find(user.id, clientId);
    if (!row || !fs.existsSync(this.pathFor(row))) throw notFound('Запис не знайдено');
    return row;
  }

  list(user) {
    const usage = this.recordings.usage(user.id);
    return {
      items: this.recordings.list(user.id).map((row) => ({
        clientId: row.client_id,
        mime: row.mime,
        sizeBytes: row.size_bytes,
        sha256: row.sha256,
        createdAt: row.created_at,
      })),
      usedBytes: usage.bytes,
      quotaBytes: this.config.recordingQuotaBytes,
      maxFiles: this.config.recordingMaxFiles,
    };
  }

  remove(user, clientId) {
    this.checkId(clientId);
    const row = this.recordings.find(user.id, clientId);
    if (!row) throw notFound('Запис не знайдено');
    this.recordings.remove(row.id);
    this.deleteFile(row);
  }

  discard(userId, clientId) {
    const row = this.recordings.find(userId, clientId);
    if (!row) return;
    this.recordings.remove(row.id);
    this.deleteFile(row);
  }

  purgeUser(userId) {
    for (const row of this.recordings.allForUser(userId)) this.deleteFile(row);
  }

  deleteFile(row) {
    try {
      fs.rmSync(this.pathFor(row), { force: true });
    } catch {
      return;
    }
  }
}
