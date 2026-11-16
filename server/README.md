# Karaoke Studio Server

REST API для мобільного застосунку: каталог пісень, акаунти, синхронізація обраного та виконань, рейтинг гравців, записи голосу, інтеграція зі сторонніми сервісами (LRCLIB для текстів, iTunes Search для метаданих і обкладинок), адміністративна сторінка, метрики.

Node.js 22.5+, без зовнішніх залежностей (вбудовані `node:sqlite`, `node:test`, `node:crypto`).

Докладний опис: [docs/11-server-architecture.md](../docs/11-server-architecture.md). Розгортання: [deploy/README.md](../deploy/README.md).

## Запуск

```
cd server
npm start
```

Налаштування через змінні середовища: `.env.example`. Для production обов'язково задайте `JWT_SECRET` (із типовим значенням сервер у production не стартує). Користувач з іменем зі списку `ADMIN_USERNAMES` отримує роль admin при реєстрації; сторінка адміністрування: `/admin`.

Для емулятора Android адреса сервера: `http://10.0.2.2:8080`.

## Тести та інструменти

```
npm test                              # 131 тест: API, безпека, записи, адмінка, контракт OpenAPI, плани запитів, копії, наскрізний сценарій
npm run loadtest -- --duration 10     # навантажувальний тест (параметри: --concurrency, --users, --url, --assert-errors, --assert-p95)
npm run profile                       # EXPLAIN QUERY PLAN гарячих запитів
npm run backup -- data/karaoke.db data/backups 7
```

## Архітектура

```
routes  ->  services  ->  repositories  ->  SQLite
              |
              +-> integrations (LRCLIB, iTunes) + кеш у БД
              +-> recordings (файли на диску)
```

- `src/app.js`: обробник запиту (ліміт частоти, автентифікація, тіло, метрики, журнал)
- `src/http`: роутер, розбір тіла, обмеження частоти, помилки
- `src/security`: scrypt для паролів, JWT HS256
- `src/db`: підключення, міграції (`migrations/*.sql`), транзакції
- `src/services`: бізнес-логіка
- `src/integrations`: клієнти сторонніх сервісів із таймаутами, повторами, кешем і стелею запитів
- `src/openapi.js`: специфікація OpenAPI 3.1
- `src/metrics.js`, `src/logger.js`, `src/maintenance.js`: метрики, журнал, очищення й резервні копії
- `src/pages`: адмін-сторінка, політика конфіденційності, умови використання

## API (префікс `/api/v1`, повна специфікація: `GET /api/v1/openapi.json`)

| Метод і шлях | Доступ | Призначення |
|---|---|---|
| `GET /health` (без префікса) | всі | стан сервера |
| `POST /auth/register`, `POST /auth/login` | всі | реєстрація та вхід, повертає токен доступу й токен оновлення |
| `POST /auth/refresh`, `POST /auth/logout` | всі | оновлення сесії, вихід |
| `POST /auth/password` | токен | зміна пароля, анулює старі сесії |
| `GET /auth/me`, `PATCH /auth/me`, `DELETE /auth/me` | токен | профіль, нікнейм, видалення облікового запису з даними |
| `GET /auth/export` | токен | усі дані користувача |
| `GET /songs?since=&limit=&q=&genre=` | всі | каталог; `since` дає зміни після мітки, включно з видаленими |
| `GET /songs/:slug` | всі | пісня |
| `GET /songs/:slug/leaderboard?limit=` | всі, токен додає `me` | найкращі результати гравців |
| `GET /leaderboard?limit=` | всі, токен додає `me` | загальний рейтинг гравців за сумою найкращих результатів |
| `POST/PUT/DELETE /songs` | admin | керування каталогом |
| `POST /sync/push` | токен | відправити зміни обраного та виконань |
| `GET /sync/pull?since=` | токен | отримати зміни; відповідь містить `nextSince` і `hasMore` |
| `GET /integrations/lyrics?title=&artist=&durationSec=` | токен | текст пісні (LRC) з LRCLIB |
| `GET /integrations/metadata?q=&limit=` | токен | метадані й обкладинки з iTunes |
| `PUT/GET/DELETE /recordings/:clientId`, `GET /recordings` | токен | записи голосу користувача й використання квоти |
| `GET /admin/stats`, `/admin/songs`, `/admin/songs/:slug`, `/admin/users`, `/admin/logs` | admin | статистика, каталог з видаленими, користувачі, журнал |
| `GET /metrics`, `/privacy`, `/terms`, `/admin` (без префікса) | токен метрик чи admin / всі | метрики Prometheus, документи, сторінка адміністрування |

Помилки мають вигляд `{ "error": { "code", "message", "details" }, "requestId" }`.

## Синхронізація

Кожен запис на сервері має монотонну мітку `updated_at`, за якою клієнт отримує лише нові зміни (`since`). Конфлікти розв'язуються за часом клієнта `at` (остання зміна перемагає); застарілі зміни повертаються зі статусом `stale`. Виконання ідемпотентні за парою (користувач, `clientId`).
