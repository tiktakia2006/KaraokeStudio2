const ref = (name) => ({ $ref: `#/components/schemas/${name}` });
const str = { type: 'string' };
const int = { type: 'integer' };
const bool = { type: 'boolean' };
const nullable = (schema) => ({ ...schema, type: [schema.type, 'null'] });
const array = (items) => ({ type: 'array', items });
const obj = (properties, required = Object.keys(properties)) => ({
  type: 'object',
  properties,
  required,
  additionalProperties: false,
});

const jsonBody = (schema, required = true) => ({ required, content: { 'application/json': { schema } } });
const jsonResponse = (description, schema) => ({ description, content: { 'application/json': { schema } } });
const errorResponse = (description) => jsonResponse(description, ref('Error'));
const bearer = [{ bearerAuth: [] }];

const pathParam = (name, description) => ({ name, in: 'path', required: true, description, schema: str });
const queryParam = (name, schema, description) => ({ name, in: 'query', required: false, description, schema });

const authErrors = {
  400: errorResponse('Помилка валідації'),
  401: errorResponse('Потрібна авторизація'),
  429: errorResponse('Перевищено ліміт запитів'),
};

const songSchemas = {
  Song: obj({
    slug: str,
    title: str,
    artist: str,
    durationMs: int,
    genre: str,
    lyrics: str,
    audioSource: str,
    coverUrl: nullable(str),
    deleted: { const: false },
    updatedAt: int,
  }),
  SongTombstone: obj({ slug: str, deleted: { const: true }, updatedAt: int }),
  SongPage: obj({
    items: array({ oneOf: [ref('Song'), ref('SongTombstone')] }),
    hasMore: bool,
    nextSince: int,
  }),
  LeaderboardEntry: obj({ rank: int, nickname: str, bestScore: int, attempts: int, achievedAt: int }),
  PlayerRank: obj({ rank: int, nickname: str, totalScore: int, songsCount: int, bestScore: int, attempts: int }),
  GlobalLeaderboard: obj({ items: array(ref('PlayerRank')), total: int, me: { oneOf: [ref('PlayerRank'), { type: 'null' }] } }),
  Leaderboard: obj({ items: array(ref('LeaderboardEntry')), total: int, me: { oneOf: [ref('LeaderboardEntry'), { type: 'null' }] } }),
};

const syncSchemas = {
  FavoriteChangeIn: obj({ type: { const: 'favorite' }, op: { enum: ['upsert', 'delete'] }, songSlug: str, at: int }),
  PerformanceChangeIn: obj(
    {
      type: { const: 'performance' },
      op: { enum: ['upsert', 'delete'] },
      clientId: str,
      songSlug: str,
      performedAt: int,
      durationMs: int,
      score: nullable(int),
      at: int,
    },
    ['type', 'op', 'clientId', 'at'],
  ),
  PushRequest: obj({ changes: array({ oneOf: [ref('FavoriteChangeIn'), ref('PerformanceChangeIn')] }) }),
  PushResult: obj({ index: int, status: { enum: ['applied', 'stale', 'rejected'] }, reason: str }, ['index', 'status']),
  PushResponse: obj({ results: array(ref('PushResult')), applied: int, serverTime: int }),
  FavoriteChangeOut: obj({ type: { const: 'favorite' }, songSlug: str, deleted: bool, updatedAt: int }),
  PerformanceChangeOut: obj({
    type: { const: 'performance' },
    clientId: str,
    songSlug: str,
    performedAt: int,
    durationMs: int,
    score: nullable(int),
    deleted: bool,
    updatedAt: int,
  }),
  PullResponse: obj({
    changes: array({ oneOf: [ref('FavoriteChangeOut'), ref('PerformanceChangeOut')] }),
    hasMore: bool,
    nextSince: int,
  }),
};

const authSchemas = {
  User: obj({ id: int, username: str, nickname: str, role: { enum: ['user', 'admin'] } }),
  Session: obj({ token: str, refreshToken: str, expiresIn: int, user: ref('User') }),
  UserEnvelope: obj({ user: ref('User') }),
  Credentials: obj({ username: str, password: str, nickname: str }, ['username', 'password']),
  RefreshRequest: obj({ refreshToken: str }),
  PasswordChange: obj({ currentPassword: str, newPassword: str }),
  PasswordConfirm: obj({ password: str }),
  Export: obj({
    user: ref('User'),
    exportedAt: int,
    favorites: array(obj({ songSlug: str, updatedAt: int })),
    performances: array(obj({ clientId: str, songSlug: str, performedAt: int, durationMs: int, score: nullable(int) })),
    recordings: array(ref('Recording')),
  }),
};

const integrationSchemas = {
  Attribution: obj({ name: str, url: str }),
  LyricsFound: obj(
    {
      found: { const: true },
      source: str,
      synced: bool,
      lyrics: str,
      trackName: str,
      artistName: str,
      durationSec: nullable(int),
      attribution: ref('Attribution'),
      cached: bool,
      stale: bool,
    },
    ['found', 'source', 'synced', 'lyrics', 'trackName', 'artistName', 'durationSec', 'attribution', 'cached'],
  ),
  LyricsMissing: obj({ found: { const: false }, attribution: ref('Attribution'), cached: bool, stale: bool }, [
    'found',
    'attribution',
    'cached',
  ]),
  MetadataItem: obj({
    title: str,
    artist: str,
    album: nullable(str),
    genre: nullable(str),
    durationMs: nullable(int),
    coverUrl: nullable(str),
    source: str,
  }),
  Metadata: obj({ items: array(ref('MetadataItem')), attribution: ref('Attribution'), cached: bool, stale: bool }, [
    'items',
    'attribution',
    'cached',
  ]),
};

const recordingSchemas = {
  Recording: obj({ clientId: str, mime: str, sizeBytes: int, sha256: str, createdAt: int }),
  RecordingList: obj({ items: array(ref('Recording')), usedBytes: int, quotaBytes: int, maxFiles: int }),
};

const systemSchemas = {
  Error: obj(
    {
      error: obj({ code: str, message: str, details: { type: 'object' } }, ['code', 'message']),
      requestId: str,
    },
    ['error', 'requestId'],
  ),
  Health: obj({ status: str, time: int, db: str }),
  Meta: obj({ apiVersion: int, serverTime: int, songs: int }),
  SongInput: obj(
    {
      slug: str,
      title: str,
      artist: str,
      durationMs: int,
      genre: str,
      lyrics: str,
      audioSource: str,
      coverUrl: nullable(str),
    },
    ['slug', 'title', 'artist', 'durationMs', 'audioSource'],
  ),
  AdminStats: obj({
    version: str,
    uptimeSec: int,
    users: int,
    songs: int,
    deletedSongs: int,
    performances: int,
    favorites: int,
    recordings: obj({ files: int, bytes: int }),
    database: obj({ sizeBytes: int }),
    activeSessions: int,
    recordingsDirExists: bool,
  }),
  AdminSongs: obj({
    items: array(
      obj({
        id: int,
        slug: str,
        title: str,
        artist: str,
        durationMs: int,
        genre: str,
        audioSource: str,
        coverUrl: nullable(str),
        deleted: bool,
        updatedAt: int,
      }),
    ),
    limit: int,
    offset: int,
  }),
  AdminSong: obj({
    id: int,
    slug: str,
    title: str,
    artist: str,
    durationMs: int,
    genre: str,
    lyrics: str,
    audioSource: str,
    coverUrl: nullable(str),
    deleted: bool,
    updatedAt: int,
  }),
  AdminUsers: obj({
    items: array(
      obj({ id: int, username: str, nickname: str, role: str, createdAt: int, performances: int, favorites: int }),
    ),
    total: int,
    limit: int,
    offset: int,
  }),
  AdminLogs: obj({ items: array({ type: 'object' }) }),
};

export function buildOpenApi(version = '2.0.0') {
  return {
    openapi: '3.1.0',
    info: {
      title: 'Karaoke Studio API',
      version,
      description: 'REST API мобільного застосунку Karaoke Studio: каталог, акаунти, синхронізація, рейтинг, записи голосу, сторонні сервіси.',
    },
    servers: [{ url: 'http://localhost:8080', description: 'Локальний запуск' }],
    tags: [
      { name: 'system' },
      { name: 'auth' },
      { name: 'songs' },
      { name: 'sync' },
      { name: 'integrations' },
      { name: 'recordings' },
      { name: 'admin' },
    ],
    paths: {
      '/health': {
        get: { tags: ['system'], summary: 'Стан сервера', responses: { 200: jsonResponse('Сервер працює', ref('Health')) } },
      },
      '/api/v1/meta': {
        get: { tags: ['system'], summary: 'Версія API та розмір каталогу', responses: { 200: jsonResponse('Метадані', ref('Meta')) } },
      },
      '/api/v1/openapi.json': {
        get: { tags: ['system'], summary: 'Специфікація OpenAPI', responses: { 200: { description: 'Документ OpenAPI' } } },
      },
      '/metrics': {
        get: {
          tags: ['system'],
          summary: 'Метрики у форматі Prometheus',
          security: bearer,
          responses: { 200: { description: 'Текст метрик' }, 401: errorResponse('Потрібен токен метрик або адміністратор') },
        },
      },
      '/privacy': { get: { tags: ['system'], summary: 'Політика конфіденційності', responses: { 200: { description: 'HTML' } } } },
      '/terms': { get: { tags: ['system'], summary: 'Умови використання', responses: { 200: { description: 'HTML' } } } },
      '/admin': { get: { tags: ['admin'], summary: 'Сторінка адміністрування каталогу', responses: { 200: { description: 'HTML' } } } },
      '/api/v1/auth/register': {
        post: {
          tags: ['auth'],
          summary: 'Реєстрація',
          requestBody: jsonBody(ref('Credentials')),
          responses: { 201: jsonResponse('Сесію створено', ref('Session')), ...authErrors, 409: errorResponse('Ім’я зайняте') },
        },
      },
      '/api/v1/auth/login': {
        post: {
          tags: ['auth'],
          summary: 'Вхід',
          requestBody: jsonBody(ref('Credentials')),
          responses: { 200: jsonResponse('Сесію створено', ref('Session')), ...authErrors, 423: errorResponse('Обліковий запис тимчасово заблоковано') },
        },
      },
      '/api/v1/auth/refresh': {
        post: {
          tags: ['auth'],
          summary: 'Оновлення сесії за токеном оновлення',
          requestBody: jsonBody(ref('RefreshRequest')),
          responses: { 200: jsonResponse('Нова пара токенів', ref('Session')), ...authErrors },
        },
      },
      '/api/v1/auth/logout': {
        post: {
          tags: ['auth'],
          summary: 'Вихід: скасування токена оновлення',
          requestBody: jsonBody(ref('RefreshRequest'), false),
          responses: { 204: { description: 'Сесію завершено' }, 429: errorResponse('Перевищено ліміт запитів') },
        },
      },
      '/api/v1/auth/password': {
        post: {
          tags: ['auth'],
          summary: 'Зміна пароля',
          security: bearer,
          requestBody: jsonBody(ref('PasswordChange')),
          responses: { 200: jsonResponse('Нова сесія', ref('Session')), ...authErrors },
        },
      },
      '/api/v1/auth/me': {
        get: {
          tags: ['auth'],
          summary: 'Поточний користувач',
          security: bearer,
          responses: { 200: jsonResponse('Користувач', ref('UserEnvelope')), 401: errorResponse('Потрібна авторизація') },
        },
        patch: {
          tags: ['auth'],
          summary: 'Зміна нікнейма',
          security: bearer,
          requestBody: jsonBody(obj({ nickname: str })),
          responses: { 200: jsonResponse('Користувач', ref('UserEnvelope')), 400: errorResponse('Помилка валідації'), 401: errorResponse('Потрібна авторизація') },
        },
        delete: {
          tags: ['auth'],
          summary: 'Видалення облікового запису з усіма даними',
          security: bearer,
          requestBody: jsonBody(ref('PasswordConfirm')),
          responses: { 204: { description: 'Обліковий запис видалено' }, 400: errorResponse('Помилка валідації'), 401: errorResponse('Невірний пароль') },
        },
      },
      '/api/v1/auth/export': {
        get: {
          tags: ['auth'],
          summary: 'Експорт усіх даних користувача',
          security: bearer,
          responses: { 200: jsonResponse('Дані користувача', ref('Export')), 401: errorResponse('Потрібна авторизація') },
        },
      },
      '/api/v1/songs': {
        get: {
          tags: ['songs'],
          summary: 'Каталог пісень, у тому числі зміни та видалення після мітки since',
          parameters: [
            queryParam('since', int, 'Мітка, після якої повертати зміни'),
            queryParam('limit', int, 'Розмір сторінки, 1..200'),
            queryParam('q', str, 'Пошук за назвою чи виконавцем'),
            queryParam('genre', str, 'Жанр'),
          ],
          responses: { 200: jsonResponse('Сторінка каталогу', ref('SongPage')), 304: { description: 'Без змін' }, 400: errorResponse('Помилка валідації') },
        },
        post: {
          tags: ['songs'],
          summary: 'Створення пісні',
          security: bearer,
          requestBody: jsonBody(ref('SongInput')),
          responses: { 201: jsonResponse('Пісню створено', ref('Song')), 400: errorResponse('Помилка валідації'), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора'), 409: errorResponse('slug зайнятий') },
        },
      },
      '/api/v1/leaderboard': {
        get: {
          tags: ['songs'],
          summary: 'Загальний рейтинг гравців за сумою найкращих результатів по піснях; із токеном додається місце користувача',
          parameters: [queryParam('limit', int, 'Скільки гравців повернути, 1..100')],
          responses: { 200: jsonResponse('Рейтинг', ref('GlobalLeaderboard')), 400: errorResponse('Помилка валідації') },
        },
      },
      '/api/v1/songs/{slug}': {
        parameters: [pathParam('slug', 'Ідентифікатор пісні')],
        get: { tags: ['songs'], summary: 'Пісня', responses: { 200: jsonResponse('Пісня', ref('Song')), 404: errorResponse('Не знайдено') } },
        put: {
          tags: ['songs'],
          summary: 'Оновлення або відновлення пісні',
          security: bearer,
          requestBody: jsonBody(ref('SongInput')),
          responses: { 200: jsonResponse('Пісню оновлено', ref('Song')), 400: errorResponse('Помилка валідації'), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора'), 404: errorResponse('Не знайдено') },
        },
        delete: {
          tags: ['songs'],
          summary: 'М’яке видалення пісні',
          security: bearer,
          responses: { 204: { description: 'Видалено' }, 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора'), 404: errorResponse('Не знайдено') },
        },
      },
      '/api/v1/songs/{slug}/leaderboard': {
        parameters: [pathParam('slug', 'Ідентифікатор пісні')],
        get: {
          tags: ['songs'],
          summary: 'Рейтинг гравців для пісні; із токеном додається місце користувача',
          parameters: [queryParam('limit', int, 'Скільки записів повернути, 1..100')],
          responses: { 200: jsonResponse('Рейтинг', ref('Leaderboard')), 404: errorResponse('Не знайдено') },
        },
      },
      '/api/v1/sync/push': {
        post: {
          tags: ['sync'],
          summary: 'Відправлення змін обраного та виконань',
          security: bearer,
          requestBody: jsonBody(ref('PushRequest')),
          responses: { 200: jsonResponse('Результат по кожній зміні', ref('PushResponse')), 400: errorResponse('Помилка валідації'), 401: errorResponse('Потрібна авторизація') },
        },
      },
      '/api/v1/sync/pull': {
        get: {
          tags: ['sync'],
          summary: 'Отримання змін користувача після мітки since',
          security: bearer,
          parameters: [queryParam('since', int, 'Мітка курсора')],
          responses: { 200: jsonResponse('Зміни', ref('PullResponse')), 401: errorResponse('Потрібна авторизація') },
        },
      },
      '/api/v1/integrations/lyrics': {
        get: {
          tags: ['integrations'],
          summary: 'Текст пісні з LRCLIB',
          security: bearer,
          parameters: [
            { ...queryParam('title', str, 'Назва'), required: true },
            queryParam('artist', str, 'Виконавець'),
            queryParam('durationSec', int, 'Тривалість у секундах'),
          ],
          responses: {
            200: jsonResponse('Результат пошуку', { oneOf: [ref('LyricsFound'), ref('LyricsMissing')] }),
            400: errorResponse('Помилка валідації'),
            401: errorResponse('Потрібна авторизація'),
            429: errorResponse('Перевищено ліміт'),
            502: errorResponse('Зовнішній сервіс недоступний'),
          },
        },
      },
      '/api/v1/integrations/metadata': {
        get: {
          tags: ['integrations'],
          summary: 'Метадані та обкладинки з iTunes Search',
          security: bearer,
          parameters: [{ ...queryParam('q', str, 'Пошуковий запит'), required: true }, queryParam('limit', int, '1..10')],
          responses: {
            200: jsonResponse('Результат пошуку', ref('Metadata')),
            400: errorResponse('Помилка валідації'),
            401: errorResponse('Потрібна авторизація'),
            429: errorResponse('Перевищено ліміт'),
            502: errorResponse('Зовнішній сервіс недоступний'),
          },
        },
      },
      '/api/v1/recordings': {
        get: {
          tags: ['recordings'],
          summary: 'Записи голосу користувача та використання квоти',
          security: bearer,
          responses: { 200: jsonResponse('Список', ref('RecordingList')), 401: errorResponse('Потрібна авторизація') },
        },
      },
      '/api/v1/recordings/{clientId}': {
        parameters: [pathParam('clientId', 'Ідентифікатор виконання на клієнті')],
        put: {
          tags: ['recordings'],
          summary: 'Завантаження запису голосу (тіло запиту — аудіофайл)',
          security: bearer,
          requestBody: { required: true, content: { 'audio/mp4': { schema: { type: 'string', format: 'binary' } } } },
          responses: {
            200: jsonResponse('Запис замінено', ref('Recording')),
            201: jsonResponse('Запис збережено', ref('Recording')),
            400: errorResponse('Помилка валідації'),
            401: errorResponse('Потрібна авторизація'),
            409: errorResponse('Виконання не синхронізовано або перевищено квоту'),
            413: errorResponse('Файл завеликий'),
            415: errorResponse('Непідтримуваний формат'),
          },
        },
        get: {
          tags: ['recordings'],
          summary: 'Завантаження запису голосу',
          security: bearer,
          responses: { 200: { description: 'Аудіофайл' }, 401: errorResponse('Потрібна авторизація'), 404: errorResponse('Не знайдено') },
        },
        delete: {
          tags: ['recordings'],
          summary: 'Видалення запису голосу',
          security: bearer,
          responses: { 204: { description: 'Видалено' }, 401: errorResponse('Потрібна авторизація'), 404: errorResponse('Не знайдено') },
        },
      },
      '/api/v1/admin/stats': {
        get: { tags: ['admin'], summary: 'Статистика сервера', security: bearer, responses: { 200: jsonResponse('Статистика', ref('AdminStats')), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора') } },
      },
      '/api/v1/admin/songs': {
        get: {
          tags: ['admin'],
          summary: 'Усі пісні, включно з видаленими',
          security: bearer,
          parameters: [queryParam('q', str, 'Пошук'), queryParam('includeDeleted', bool, 'Показувати видалені'), queryParam('limit', int, 'Розмір сторінки'), queryParam('offset', int, 'Зсув')],
          responses: { 200: jsonResponse('Список', ref('AdminSongs')), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора') },
        },
      },
      '/api/v1/admin/songs/{slug}': {
        parameters: [pathParam('slug', 'Ідентифікатор пісні')],
        get: {
          tags: ['admin'],
          summary: 'Пісня з усіма полями, включно з видаленою',
          security: bearer,
          responses: { 200: jsonResponse('Пісня', ref('AdminSong')), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора'), 404: errorResponse('Не знайдено') },
        },
      },
      '/api/v1/admin/users': {
        get: {
          tags: ['admin'],
          summary: 'Користувачі та їхня активність',
          security: bearer,
          parameters: [queryParam('limit', int, 'Розмір сторінки'), queryParam('offset', int, 'Зсув')],
          responses: { 200: jsonResponse('Список', ref('AdminUsers')), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора') },
        },
      },
      '/api/v1/admin/logs': {
        get: {
          tags: ['admin'],
          summary: 'Останні записи журналу',
          security: bearer,
          parameters: [queryParam('limit', int, 'Кількість записів'), queryParam('level', str, 'Мінімальний рівень')],
          responses: { 200: jsonResponse('Журнал', ref('AdminLogs')), 401: errorResponse('Потрібна авторизація'), 403: errorResponse('Лише для адміністратора') },
        },
      },
    },
    components: {
      securitySchemes: { bearerAuth: { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' } },
      schemas: {
        ...systemSchemas,
        ...authSchemas,
        ...songSchemas,
        ...syncSchemas,
        ...integrationSchemas,
        ...recordingSchemas,
      },
    },
  };
}
