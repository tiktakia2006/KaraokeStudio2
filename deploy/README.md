# Розгортання сервера

Потрібен Linux-сервер із Docker та Docker Compose, домен з A-записом на цей сервер і відкриті порти 80 та 443.

## Перший запуск

```
git clone https://github.com/tiktakia2006/KaraokeStudio.git /opt/karaoke-studio
cd /opt/karaoke-studio
cp deploy/.env.example deploy/.env
```

У `deploy/.env` задайте `DOMAIN`, довгі випадкові `JWT_SECRET` і `METRICS_TOKEN` (наприклад, `openssl rand -base64 48`) та `ADMIN_USERNAMES`. Файл `.env` у git не потрапляє.

```
docker compose up -d --build
```

Caddy сам отримає сертифікат Let's Encrypt для домену. Перевірка: `https://<домен>/health`. Адміністративна сторінка: `https://<домен>/admin`. Користувач зі списку `ADMIN_USERNAMES` отримує роль адміністратора під час реєстрації, тому зареєструйтеся з таким іменем до публікації адреси застосунку.

База, записи голосу й резервні копії лежать у томі `karaoke-data` (`/data` у контейнері): `karaoke.db`, `recordings/`, `backups/`.

## Моніторинг

Метрики віддаються на `/metrics` лише з токеном і не проксюються назовні. Щоб підняти Prometheus:

```
printf '%s' "$METRICS_TOKEN" > deploy/metrics-token
docker compose --profile monitoring up -d
```

Prometheus слухає лише `127.0.0.1:9090`. Для перегляду з комп’ютера: `ssh -L 9090:127.0.0.1:9090 користувач@сервер`.

Журнал: `docker compose logs -f server` або вкладка «Журнал» на сторінці `/admin`.

## Оновлення

Автоматично: workflow `Deploy` збирає образ, публікує його в GitHub Container Registry і перезапускає сервіс по SSH. Вручну:

```
git pull --ff-only
docker compose up -d --build
```

## Резервне копіювання

Сервер сам створює копію бази раз на `BACKUP_INTERVAL_HOURS` годин і зберігає `BACKUP_KEEP` останніх. Копію можна зробити й вручну:

```
docker compose exec server node tools/backup.js /data/karaoke.db /data/backups 7
```

Відновлення: зупиніть сервер, замініть `karaoke.db` у томі потрібною копією, запустіть знову.
