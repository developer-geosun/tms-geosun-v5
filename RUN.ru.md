# Запуск проекта

**Дата создания:** 15 сентября 2026, 00:00 (UTC+3)  
**Дата изменения:** 28 октября 2026, 21:36 (UTC+3)

Этот файл описывает основные варианты запуска `tms-geosun-v4`.

## Требования

- Node.js `22.14` LTS (рекомендуется; минимум `22.13+` на ветке 22). Также поддерживаются `20.19+` LTS и `24+` (CI).
- npm `>=10`
- Версия зафиксирована в `frontend-angular/.nvmrc` (`22.14.0`); для nvm/fnm: `nvm use` / `fnm use` из каталога `frontend-angular`.
- Java `21`
- Maven `3.9+`
- MySQL `8` (для локального запуска backend без Docker)
- Docker Desktop + Docker Compose (для запуска всего стека в контейнерах)

## Хранение файлов (`local` | `s3`)

Backend хранит бинарники через `app.storage.type` (env `APP_STORAGE_TYPE`). Метаданные — в таблице `stored_files` (Flyway `V30`).

| Режим | `APP_STORAGE_TYPE` | Где лежат файлы | Когда использовать |
| --- | --- | --- | --- |
| Локальный диск | `local` (по умолчанию) | каталог `APP_STORAGE_LOCAL_BASE_PATH` | обычная разработка |
| S3 / MinIO | `s3` | bucket `APP_STORAGE_S3_BUCKET` | проверка object storage / ближе к прод |

### Переменные в корневом `.env`

```bash
# local | s3
APP_STORAGE_TYPE=local

# Режим local:
# - локальный mvn: ./data/uploads (относительно cwd backend-java)
# - Docker Compose: всегда /data/uploads (named volume backend_uploads; значение из .env не используется)
APP_STORAGE_LOCAL_BASE_PATH=./data/uploads

# Режим s3 (MinIO в compose или внешний S3)
APP_STORAGE_S3_ENDPOINT=http://minio:9000
APP_STORAGE_S3_REGION=us-east-1
APP_STORAGE_S3_BUCKET=tms-uploads
APP_STORAGE_S3_ACCESS_KEY=minioadmin
APP_STORAGE_S3_SECRET_KEY=minioadmin
APP_STORAGE_S3_PATH_STYLE=true

MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
```

Для локального `mvn spring-boot:run` без Docker:

- `APP_STORAGE_TYPE=local`
- `APP_STORAGE_LOCAL_BASE_PATH=./data/uploads` (создаётся автоматически; каталог в `.gitignore`)

Для S3 через MinIO в Compose:

```bash
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_ENDPOINT=http://minio:9000
APP_STORAGE_S3_ACCESS_KEY=minioadmin
APP_STORAGE_S3_SECRET_KEY=minioadmin
```

Затем поднимите MinIO вместе с backend (bucket создаёт сервис `minio-init`):

```bash
docker compose up --build mysql minio minio-init backend
```

> Смена `APP_STORAGE_TYPE` на уже заполненной БД **не** переносит файлы автоматически — нужны те же `storage_key` в новом хранилище.

Тестовая ADMIN-страница: `http://localhost:4210/admin/file-storage-test`  
Спека: [`docs/specs/file-storage.ru.md`](docs/specs/file-storage.ru.md)

## Публикация: frontend на GitHub Pages, API через ngrok или статический IP

Архитектура для публичного доступа:

- **Frontend (Angular)** — статика на GitHub Pages: `https://developer-geosun.github.io/<repo-name>/`
- **Frontend (Flutter Web)** — подпапка: `https://developer-geosun.github.io/<repo-name>/flutter/`

Оба клиента деплоятся одним workflow `.github/workflows/deploy.yml` через официальный GitHub Pages deploy (artifact + `deploy-pages`).
- **Backend** — локально (Docker), наружу по выбору: **ngrok** или **статический IP** провайдера (без проксирования UI).

Режим задаётся в корневом `.env`:

| Переменная | Значение | Эффект |
| --- | --- | --- |
| `PUBLIC_ACCESS_MODE` | `static-ip` | Без туннеля; API на `PUBLIC_API_URL` (порт `SERVER_PORT` на хосте) |
| `PUBLIC_ACCESS_MODE` | `ngrok` | Нужен `COMPOSE_PROFILES=ngrok` + `NGROK_*` |
| `PUBLIC_API_URL` | URL API | Для справки / совпадения с GitHub Secret `API_URL` |
| `COMPOSE_PROFILES` | `ngrok` или пусто | Включает сервис `ngrok` в Compose |

### 1) GitHub secrets (Settings → Secrets and variables → Actions)

| Secret | Назначение |
| --- | --- |
| `API_URL` | Публичный URL backend: `https://<NGROK_DOMAIN>` **или** `http://178.136.237.7:8080` |
| `CARTO_API_KEY` | ключ CARTO Basemaps — убирает водяной знак «API KEY REQUIRED» на подложке |

> **HTTPS / mixed content:** GitHub Pages отдаёт UI по HTTPS. Браузер может блокировать запросы к `http://…` API. Для стабильной работы со статическим IP лучше HTTPS (домен + nginx/Caddy) или режим ngrok (HTTPS из коробки).

### 2) GitHub Pages

Settings → Pages → Build and deployment → Source: **GitHub Actions**.

Workflow `.github/workflows/deploy.yml` собирает **Angular** (корень сайта) и **Flutter Web** (`/flutter/`), загружает артефакт и публикует на Pages при push в `master`/`main` (изменения в `frontend-angular/**`, `frontend-flutter/**`) или вручную (**Actions → Deploy to GitHub Pages → Run workflow**).

Для job `deploy` нужны permissions `pages: write` и `id-token: write` (заданы в workflow). В Settings → Actions → General рекомендуется **Read and write permissions** для `GITHUB_TOKEN`.

| Клиент | URL на Pages |
| --- | --- |
| Angular | `https://developer-geosun.github.io/<repo-name>/` |
| Flutter | `https://developer-geosun.github.io/<repo-name>/flutter/` |

`<repo-name>` — имя репозитория на GitHub (например `tms-geosun-v4`).

### 3a) Backend + статический IP (Vodafone и т.п.)

В `.env`:

```bash
PUBLIC_ACCESS_MODE=static-ip
PUBLIC_API_URL=http://178.136.237.7:8080
# COMPOSE_PROFILES не задавать (или оставить пустым) — сервис ngrok не стартует
CORS_ALLOWED_ORIGIN_PATTERNS=https://developer-geosun.github.io
ANGULAR_APP_BASE_URL=https://developer-geosun.github.io/tms-geosun-v4
FLUTTER_APP_BASE_URL=https://developer-geosun.github.io/tms-geosun-v4/flutter
APP_STORAGE_TYPE=local
```

На роутере пробросьте TCP-порт `8080` (или ваш `SERVER_PORT`) на ПК, где крутится Docker.

Запуск API без туннеля:

```bash
docker compose up --build mysql backend
```

Проверка снаружи: `http://178.136.237.7:8080/actuator/health`

В GitHub Secrets `API_URL` = `http://178.136.237.7:8080`, затем перезапустите Deploy workflow.

### 3b) Backend + ngrok

В `.env`:

```bash
PUBLIC_ACCESS_MODE=ngrok
COMPOSE_PROFILES=ngrok
NGROK_AUTHTOKEN=<ваш_ngrok_authtoken>
NGROK_DOMAIN=<ваш_домен_из_ngrok>
PUBLIC_API_URL=https://<NGROK_DOMAIN>
CORS_ALLOWED_ORIGIN_PATTERNS=https://developer-geosun.github.io
ANGULAR_APP_BASE_URL=https://developer-geosun.github.io/tms-geosun-v4
FLUTTER_APP_BASE_URL=https://developer-geosun.github.io/tms-geosun-v4/flutter
APP_STORAGE_TYPE=local
```

Запуск API с публичным туннелем:

```bash
docker compose up --build mysql backend ngrok
```

или (если задан `COMPOSE_PROFILES=ngrok`):

```bash
docker compose up --build mysql backend
```

После старта туннеля значение `API_URL` в GitHub Secrets должно совпадать с `https://<NGROK_DOMAIN>` (и при смене домена — перезапустить Deploy workflow).

## Вариант 1: локальный запуск (frontend + backend по отдельности)

### 1) Backend

Нужен MySQL 8 (локально или только контейнер БД):

```bash
docker compose up -d mysql
```

Из корня проекта:

```bash
cd backend-java
mvn spring-boot:run
```

Перед запуском убедитесь, что:

- MySQL доступен (контейнер v4 публикует хост-порт `3308`, v1 занимает `3307` → см. `MYSQL_HOST_PORT` и `DB_URL` в `backend-java/.env.example` / `application.yml`);
- переменные из `backend-java/.env.example` и при необходимости корневого `.env` настроены;
- для **первого** ADMIN на пустой БД (опционально): `ADMIN_EMAIL` и `ADMIN_PASSWORD` в `.env` — при старте backend создаст учётку, если ещё нет пользователя с ролью ADMIN в available roles (см. [`docs/specs/default-admin-bootstrap.ru.en.md`](docs/specs/default-admin-bootstrap.ru.en.md)); не путать с `SUPER_ADMIN_PASSWORD` (step-up);
- для файлов: `APP_STORAGE_TYPE=local`, каталог `./data/uploads` (относительно cwd backend-java).

### 2) Frontend

Из корня проекта:

```bash
cd frontend-angular
npm install
npm start
```

Перед `npm start` в корневом `.env` задайте ключ карты — он автоматически попадёт в локальный `frontend-angular/src/assets/app-config.local.js` (файл игнорируется git):

- `CARTO_API_KEY=<ваш_ключ_carto>` — подложка Leaflet без водяного знака CARTO

Frontend: `http://localhost:4200` (`npm start` на хосте). Контейнер v1 тоже публикует `4200`; для параллельного запуска открывайте Docker-frontend v4 на `http://localhost:4210`.

### 2b) Flutter Web (experimental client)

Параллельный клиент для Web → Android/iOS. Пока только auth (`/login`, `/home`).

Требуется Flutter stable. Backend должен быть доступен на `:8080`.

```bash
cd frontend-flutter
flutter pub get
flutter run -d chrome --web-port=4300 --dart-define=API_URL=http://localhost:8080
```

Flutter Web: `http://localhost:4300`. Подробнее: [`frontend-flutter/README.md`](frontend-flutter/README.md).

## Вариант 2: запуск всего стека через Docker Compose

Стек v4 рассчитан на одновременную работу с уже запущенным `tms-geosun-v1`. У каждого проекта свой volume MySQL (`tms-geosun-v5_mysql_data` и `tms-geosun-v1_mysql_data`). Внутри сети Compose backend по-прежнему слушает `8080`, на хост публикуются другие порты:

| Сервис | v1 | v4 |
| --- | --- | --- |
| Backend API | `8080` | `8090` (`SERVER_HOST_PORT`) |
| Frontend | `4200` | `4210` (`FRONTEND_PORT`) |
| Gateway | `8081` | `8091` |
| Gateway dev | `8082` | `8092` |
| MySQL | `3307` | `3308` |
| ngrok inspector | `4040` / `4041` | `4042` / `4043` |

Один и тот же `NGROK_DOMAIN` нельзя занять двумя туннелями сразу. Локальные порты при этом не пересекаются. MinIO v4 (`9000` / `9001`) у v1 нет.

1. Создайте `.env` на основе шаблона `.env.example`.

```bash
cp .env.example .env
```

- Для production-сборки frontend оставьте `FRONTEND_BUILD_CONFIGURATION=production`.
- Для dev-сборки frontend в Docker укажите `FRONTEND_BUILD_CONFIGURATION=development` (в этом режиме будут видны dev-значения из `environment.ts`).
- Для разбивки пробега по странам на бекенде укажите `COUNTRY_BREAKDOWN_PROVIDER=here|geojson` и при `here` — `HERE_API_KEY=<ваш_ключ_here>`.
- Для подложки карты без водяного знака CARTO укажите `CARTO_API_KEY=<ваш_ключ_carto>`.
- Для хранилища файлов:
  - **local (по умолчанию):** `APP_STORAGE_TYPE=local` — volume `backend_uploads`
  - **MinIO/S3:** `APP_STORAGE_TYPE=s3` и параметры `APP_STORAGE_S3_*` / `MINIO_*` (поднять `minio` + `minio-init`)
- Для публичного API выберите режим в `.env` (`PUBLIC_ACCESS_MODE=static-ip` или `ngrok`):
  - **static-ip:** `PUBLIC_API_URL=http://178.136.237.7:8080`, без `COMPOSE_PROFILES`; на роутере — проброс порта `8080`
  - **ngrok:** `COMPOSE_PROFILES=ngrok`, `NGROK_AUTHTOKEN`, `NGROK_DOMAIN`, `PUBLIC_API_URL=https://<NGROK_DOMAIN>`
  - в обоих случаях: `CORS_ALLOWED_ORIGIN_PATTERNS=https://developer-geosun.github.io` и link-base на GitHub Pages
- **Telegram chatbot (верификация, выключен по умолчанию):**
  - `CHATBOT_ENABLED=true`, `CHATBOT_TELEGRAM_ENABLED=true`
  - `CHATBOT_TELEGRAM_BOT_TOKEN`, `CHATBOT_TELEGRAM_SECRET_TOKEN`, `CHATBOT_TELEGRAM_BOT_USERNAME`
  - `CHATBOT_PUBLIC_BASE_URL` = тот же публичный HTTPS API (напр. `https://<NGROK_DOMAIN>`), webhook: `{CHATBOT_PUBLIC_BASE_URL}/api/v1/webhooks/telegram`

2. Запуск контейнеров (из корня проекта):

```bash
# Базовый стек (хранилище local)
docker compose up --build

# С MinIO (хранилище s3) — явно добавьте сервисы:
# docker compose up --build mysql minio minio-init backend frontend gateway
```

3. Остановка и удаление контейнеров:

```bash
docker compose down
```

Данные MySQL, uploads и MinIO сохраняются в named volumes (`mysql_data`, `backend_uploads`, `minio_data`), пока не выполнить `docker compose down -v`.

### Быстрый dev-цикл frontend (hot reload в Docker)

Когда вы активно меняете UI, удобнее запускать `frontend-dev` (Angular dev server), а не production-`frontend` через nginx.

1. Остановите production frontend (если уже запущен):

```bash
docker compose stop frontend
```

2. Запустите dev frontend с hot reload:

```bash
docker compose --profile dev up -d frontend-dev
```

3. Откройте приложение:

`http://localhost:4210`

Изменения в `frontend-angular/src/*` будут применяться автоматически без пересборки Docker-образа.

4. Остановить только dev frontend:

```bash
docker compose --profile dev stop frontend-dev
```

5. Полностью остановить dev-профиль (рекомендуется в конце сессии):

```bash
docker compose --profile dev down --remove-orphans
```

Примечания:

- `frontend` — это production preview (build + nginx), подходит для проверки итоговой сборки.
- `frontend-dev` — это режим разработки (ng serve), подходит для быстрых правок и тестирования.
- В Docker dev-режиме API проксируется через `frontend-angular/proxy.docker.conf.json` на `http://backend:8080`.
- Для публичного API: режим `static-ip` (порт на хосте) или профили Compose `ngrok` / `ngrok-dev`.
- Для ссылок из писем укажите `ANGULAR_APP_BASE_URL` и `FLUTTER_APP_BASE_URL` (локально или GitHub Pages). Backend сам добавит `/verify-email` или `/reset-password` по заголовку `X-App-Client`.
- На первом запуске `frontend-dev` установит зависимости (`npm ci`), далее старт обычно заметно быстрее.
- Если выполнить обычный `docker compose down` без `--profile dev`, может появиться `Network ... Resource is still in use`, потому что dev-контейнеры останутся запущенными.

### Dev-профиль (frontend-dev + backend)

```bash
docker compose --profile dev up -d --build mysql backend frontend-dev
```

С MinIO (s3):

```bash
docker compose --profile dev up -d --build mysql minio minio-init backend frontend-dev
```

С публичным API через ngrok в dev:

```bash
docker compose --profile dev --profile ngrok-dev up -d --build mysql backend frontend-dev ngrok-dev
```

```bash
# остановка (включая ngrok-dev, если был запущен)
docker compose --profile dev --profile ngrok-dev down --remove-orphans
```

Локальный вход через gateway (опционально): `http://localhost:8092` (или `GATEWAY_DEV_PORT`).

### Быстрые команды

Запуск:

```bash
docker compose up --build
```

Остановка:

```bash
docker compose down
```

Быстрый dev frontend (hot reload):

```bash
docker compose stop frontend
docker compose --profile dev up -d frontend-dev
```

Публичный API (backend + статический IP, без ngrok):

```bash
docker compose up --build mysql backend
```

Публичный API (backend + ngrok):

```bash
docker compose --profile ngrok up --build mysql backend ngrok
```

Backend + MinIO (проверка `APP_STORAGE_TYPE=s3`):

```bash
docker compose up --build mysql minio minio-init backend
```

Полная остановка dev-профиля (без "Network ... Resource is still in use"):

```bash
docker compose --profile dev --profile ngrok-dev down --remove-orphans
```

### Локальный стек с gateway (без публичного туннеля)

```bash
docker compose up --build mysql backend frontend gateway
```

## Полезные URL после запуска

- Frontend (Docker): `http://localhost:4210`
- Frontend (GitHub Pages): `https://developer-geosun.github.io/tms-geosun-v4/`
- Backend health: `http://localhost:8090/actuator/health`
- Swagger UI: `http://localhost:8090/swagger-ui.html`
- Gateway (единый локальный вход): `http://localhost:8091`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001` (логин/пароль из `MINIO_ROOT_*`)
- Тест хранилища файлов (ADMIN): `http://localhost:4210/admin/file-storage-test`
- ngrok Inspector: `http://localhost:4042` (только при профиле `ngrok`)
- Public API health (static-ip): `http://178.136.237.7:8080/actuator/health`
- Public API health (ngrok): `https://<NGROK_DOMAIN>/actuator/health`

## Быстрая проверка backend auth API

Префикс auth API: `/api/v1/auth`

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Тест файлов (только ADMIN): `/api/v1/admin/stored-files`
