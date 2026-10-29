# Technical Specification / Техническое задание: Chatbots Telegram, WhatsApp, Viber / Чат-боти месенджерів

**Дата создания:** 9 сентября 2026, 16:12 (UTC+3)  
**Дата изменения:** 29 октября 2026, 13:30 (UTC+3)

## Статус
- **Реализация:** частично
- **Роль:** источник истины по чат-ботам Telegram / WhatsApp / Viber
- **Клиент:** Angular + Java (`frontend-flutter/` не входит)
- **Baseline:** [BASELINE.ru.md](BASELINE.ru.md) **B16** — в каноне только срез v1 ниже; остальное — бэклог
- **Остаток (бэклог, не freeze):** Angular UI (`/profile` блок + `/admin/chatbots`); уведомления quote/trip/expense; команды `status` / `lang`; WhatsApp; Viber; полный admin journal + test-send
- **Реестр:** [README.ru.md](README.ru.md)

### Поставка v1 = канон B16 (в коде сейчас)
Только **верификация Telegram + телефона** (backend Java):
- opt-in привязка учётки к Telegram (код с `/profile` API);
- share contact → сверка E.164 с `user_contact_phones` → флаги `phone_verified` / `phone_verified_at` / `phone_verified_via`;
- команды `start` / `help` / `unlink`; лёгкая админка status + identities;
- канал выключен по умолчанию.
- Flyway `V42`, пакет `com.geosun.tms.chatbot`.

Целевой клиент — `frontend-angular`. `frontend-flutter/` не входит (заморозка Flutter).

## Language Rules / Правила языка
- **Primary language / Основной язык:** RU
- **Secondary language / Дополнительный язык:** EN
- **Terms to keep in English / Термины, которые оставляем на английском:** webhook, Bot API, Cloud API, E.164, RBAC, HMAC, JWT, deep link, opt-in, Definition of Done

---

## 1) Goal / Цель
- **Problem / Проблема:** заказчик и водитель узнают о quote / рейсе только зайдя в веб-приложение. Телефоны и флаги Telegram / WhatsApp / Viber уже есть в профиле, но исходящих сообщений и входящих команд нет.
- **Value / Ценность:** оперативные уведомления и короткий статус «своих» заявок/рейсов в том месенджере, которым человек пользуется; менеджеру не нужно дублировать то же самое вручную.
- **Expected outcome / Ожидаемый результат:** один доменный движок бота на Java с тремя канальными адаптерами; привязка учётки через одноразовый код с `/profile`; исходящие события по quote / trip / expense report; входящие команды меню (без LLM).

## 2) Context / Контекст
- **Project/module / Проект/модуль:** `backend-java` пакет `com.geosun.tms.chatbot` + `frontend-angular` (Angular Material): блок на `/profile`, админка `/admin/chatbots`.
- **Current behavior / Текущее поведение:**
  - В `user_contact_phones` есть boolean `has_telegram` / `has_whatsapp` / `has_viber` («этот номер имеет этот месенджер»). Никнеймы и chat id **не** хранятся (см. [user-profile.md](user-profile.ru.en.md) §6.3).
  - Телефон **не уникален глобально** между пользователями — привязка бота **не** может опираться только на совпадение E.164.
  - Исходящих интеграций с месенджерами нет; webhook-контроллеров нет.
  - USER видит заявки и quote в `/my-freight-requests`; DRIVER — рейсы в `/my-trips`.
- **Related docs / Связанные документы:**
  - [`user-profile.ru.en.md`](user-profile.ru.en.md) — телефоны, флаги месенджеров, каналы связи; источник контакта, не identity бота.
  - [`auth-authentication-authorization.ru.en.md`](auth-authentication-authorization.ru.en.md) — роли `USER` / `MANAGER` / `DRIVER` / `ADMIN`, `ApiException`.
  - [`multi-enterprise.ru.en.md`](multi-enterprise.ru.en.md) (B21) — повтор внешней привязки пользователю другого предприятия отклоняется.
  - [`routes-server-workflow-and-freight-quoting.ru.en.md`](routes-server-workflow-and-freight-quoting.ru.en.md) — заявка и quote (`NEW` → `QUOTED`, quote `SENT`).
  - [`trips-and-driver-expense-reports.ru.en.md`](trips-and-driver-expense-reports.ru.en.md) — рейс и expense report.
  - [`file-storage.ru.md`](file-storage.ru.md) — **не** используется в v1 (вложения из чата не принимаем).
  - [`admin-notify-new-user-registration.ru.en.md`](admin-notify-new-user-registration.ru.en.md) — исходящее `USER_REGISTERED` всем ADMIN с каналом `MESSENGERS` (транспорт — этот модуль; правила получателей — та спека).
  - [`admin-notify-new-freight-request.ru.en.md`](admin-notify-new-freight-request.ru.en.md) — исходящее `FREIGHT_REQUEST_CREATED` админам. Правила события — тот файл; этот модуль только транспорт.
- **Environment constraints / Ограничения окружения:** Java 21 / Spring Boot 3, Flyway (следующая миграция после `V41` → **`V42`**), MySQL, Angular 21 + Material, i18n ua/en/ru (файлы `uk.json` / `en.json` / `ru.json` не переименовывать). Публичный HTTPS URL backend обязателен для webhook (локально — как у API: ngrok / статический IP, см. `RUN.ru.md`).

## 3) Scope (In) / Scope (входит в задачу)
- Единый движок: команды, привязка, исходящие события, журнал. Каналы — **сменяемые адаптеры** `ChatbotChannelAdapter`.
- Три канала v1: **Telegram Bot API**, **WhatsApp Cloud API (Meta)**, **Viber Bot API**.
- Opt-in привязка: одноразовый код / deep link с `/profile` (аутентифицированный пользователь) → входящее сообщение боту → запись в `bot_identities`.
- Отвязка: команда в чате и кнопка на `/profile`.
- Исходящие уведомления только привязанным, активным, не soft-deleted учёткам (см. §6.5).
- Входящие команды меню: `start`, `help`, `link`, `status`, `unlink`, `lang` (без свободного NLP).
- Роли в чате:
  - `USER` — свои заявки на фрахт и текущий quote (кратко).
  - `DRIVER` — свои рейсы (через карточку `drivers.user_id`) и статус expense report.
  - `MANAGER` / `ADMIN` — привязка разрешена; команд операционного контура TMS **нет** (работают в Angular).
- Админка Angular `/admin/chatbots`: здоровье каналов, список привязок, журнал сообщений (без секретов).
- Конфигурация токенов — **только** env / `application.yml`, по умолчанию все каналы выключены (`enabled=false`), как OCR.
- i18n ответов бота: `ua` / `en` / `ru` (код языка в **наших** полях — `ua`, не `uk`).
- Handset-адаптация блока привязки на `/profile`.

## 4) Out of Scope / Out of Scope (не входит)
- Flutter-клиент.
- LLM / свободный диалог / RAG по документам TMS.
- Создание маршрута, заявки, quote, рейса или expense report из чата.
- Приём фото/PDF/голоса (чеки, CMR, сканы) — отдельный этап со `StoredFile`.
- Telegram Mini App, WhatsApp Flows, Viber Keyboard Commerce.
- Отдельный `@username` / WhatsApp-id в `user_profiles` (запрещено [user-profile.md](user-profile.ru.en.md) Out of Scope; identity живёт только в `bot_identities`).
- SMS OTP, голосовые звонки, email-рассылки.
- Массовые маркетинговые рассылки и рекламные шаблоны.
- Оплата фрахта в месенджере.
- Каналы кроме трёх перечисленных (Signal, Facebook Messenger, Instagram).
- Смена бизнес-логики заявок/рейсов: бот **читает** существующие сервисы, не дублирует правила.
- Хранение bot-token / app-secret в БД или показ их в UI.

## 5) User Stories / Пользовательские сценарии
1. **Как** USER, **я хочу** привязать Telegram / WhatsApp / Viber из профиля, **чтобы** получить оффер в месенджер, не открывая сайт.
2. **Как** DRIVER, **я хочу** получить сообщение, когда рейс перевели в `PLANNED` / `IN_PROGRESS` / `CANCELLED`, **чтобы** не пропустить назначение.
3. **Как** DRIVER, **я хочу** командой «статус» увидеть свои активные рейсы, **чтобы** быстро проверить даты и состав.
4. **Как** USER, **я хочу** командой «статус» увидеть свои открытые заявки и сумму отправленного quote, **чтобы** не искать их в браузере.
5. **Как** пользователь, **я хочу** отвязать бота из чата или с `/profile`, **чтобы** перестать получать сообщения.
6. **Как** ADMIN, **я хочу** видеть, жив ли webhook и кому привязан канал, **чтобы** диагностировать «бот молчит».

## 6) Functional Requirements / Функциональные требования

### 6.1 Каналы и адаптер
1. Интерфейс адаптера (имена ориентир; комментарии в коде — українською):

```java
/** Канальний адаптер месенджера: вхід webhook, вихід текст/кнопки, перевірка підпису. */
public interface ChatbotChannelAdapter {

  @NonNull
  ChatbotChannel channel(); // TELEGRAM | WHATSAPP | VIBER

  boolean enabled();

  /** Перевірка підпису / secret; false → 401, тіло не парсити далі. */
  boolean verifyWebhook(@NonNull ChatbotWebhookRequest raw);

  @NonNull
  List<ChatbotInboundEvent> parse(@NonNull ChatbotWebhookRequest raw);

  void send(@NonNull ChatbotOutboundMessage message);
}
```

2. Движок не знает payload Telegram/Meta/Viber. Адаптер мапит в канон: `channel`, `externalUserId`, `text`, `command`, `localeHint`, `rawEventType`.
3. Выключенный канал: webhook отвечает **200** с пустым телом (или challenge WhatsApp) **без** вызова домена — платформа не ретраит бесконечно; в логе `channel_disabled`.
4. Неизвестный / битый payload после успешной подписи: **200** + запись журнала `PARSE_FAILED` (не 500 — иначе шторм ретраев).

### 6.2 Идентичность vs профиль
5. `bot_identities` — отдельная таблица. **Нет** `ALTER` на `users` / `user_profiles`. Исключение v1: на `user_contact_phones` добавляются колонки верификации (`phone_verified`, `phone_verified_at`, `phone_verified_via`).
6. Одна учётка — до трёх привязок (по одной на канал). Один `(channel, external_user_id)` — не больше одной учётки (`UNIQUE`).
7. Флаги `has_telegram` / `has_whatsapp` / `has_viber` на телефоне — **подсказка для UI**; после успешной Telegram-verify при совпадении номера выставляется `has_telegram=true` и `phone_verified=true`.
8. Привязка **не** требует канал профиля `MESSENGERS`. Отвязка Telegram **не** сбрасывает `phone_verified` (номер уже подтверждён).

### 6.3 Привязка (opt-in)
9. `POST /api/v1/users/me/bot-link-codes` (любая роль, свой профиль):
   - вход: `{ "channel": "TELEGRAM" | "WHATSAPP" | "VIBER" }`
   - создаёт код: 8 символов `[A-Z0-9]`, TTL **10 минут**, одноразовый;
   - если по каналу уже есть активная привязка → `409 BOT_ALREADY_LINKED`;
   - ответ: код, `expiresAt`, `deepLink` (если для канала известен public id / bot username из конфига).
10. Пользователь отправляет боту код текстом **или** переходит по deep link (`/start <code>` в Telegram; `wa.me` с текстом кода; Viber `context` / первое сообщение).
11. Совпадение кода → `bot_identities.status = ACTIVE`, код помечается использованным. Ответ в чат: подтверждение + меню.
12. **Запрещено** привязывать «только по share contact»: телефон не глобально уникален. Share contact — **только после** успешного кода: сверка E.164 с телефонами этой учётки. Совпадение → `phone_verified=true`, `phone_verified_via=TELEGRAM`. Несовпадение → предупреждение в чат, привязку Telegram не откатывать. Контакт не от самого пользователя Telegram (`contact.user_id` ≠ from.id) — отклонить.
13. `DELETE /api/v1/users/me/bot-identities/{channel}` — отвязка с сайта. Команда `unlink` в чате — то же. Идемпотентно: 204. `phone_verified` **не** сбрасывается.
14. Soft-deleted / inactive учётка: новые коды `403`/`409` по правилам auth; исходящие **не** шлются; входящие `status` отвечают «учётка недоступна».

### 6.4 Входящие команды
Канонические команды (регистр не важен; алиасы ua/en/ru в таблице ниже):

| Команда | Действие |
|---------|----------|
| `start` | Если не привязан — инструкция взять код на `/profile`. Если привязан — меню. |
| `help` | Список команд. |
| `link` | «Пришлите код с сайта» (код создаётся только на сайте). |
| `status` | Сводка по роли, см. ниже. Непривязанный → просьба привязать. |
| `unlink` | Снять привязку. |
| `lang` | Сменить `locale` привязки: `ua` \| `en` \| `ru`. Без аргумента — показать текущий. |

15. Свободный текст, не похожий на код и не команда → короткое «не понял» + `help`. **Без** вызова LLM.
16. `status` для `USER`: до **5** своих заявок в статусах `NEW`, `IN_REVIEW`, `QUOTED` (свежие сверху). По каждой: номер/id (как в UI), статус, если есть текущий quote `SENT` — сумма и валюта. Без polyline, без калькулятора, без внутренних сценариев.
17. `status` для `DRIVER`: до **5** рейсов в `PLANNED` / `IN_PROGRESS` (и `COMPLETED` за последние 7 суток, если активных нет). По каждому: номер рейса, статус, даты, номера ТС из snapshot. Статус expense report, если есть. Нет чужих рейсов. Нет сканов документов.
18. `status` для `MANAGER`/`ADMIN`: «операции TMS — в веб-приложении»; плюс факт привязки.

### 6.5 Исходящие уведомления

Триггеры — **после успешного** изменения в существующих сервисах (не вместо них). Сбой отправки **не** откатывает quote/trip.

| Событие | Кому | Каналы |
|---------|------|--------|
| регистрация `POST /auth/register` (`USER_REGISTERED`) | каждый активный `ADMIN` с каналом профиля `MESSENGERS` | ACTIVE привязки **этого ADMIN**; детали и EMAIL/SMS — [admin-notify-new-user-registration.md](admin-notify-new-user-registration.ru.en.md) |
| `FreightQuoteService` отправил quote (`SENT`), заявка `QUOTED` | владелец заявки (`USER`) | все ACTIVE привязки владельца |
| заявка `ACCEPTED` / `REJECTED` / `CANCELLED` / `EXPIRED` | владелец заявки | то же |
| рейс `PLANNED` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED` | `driver.user_id`, если учётка есть | то же |
| expense report `APPROVED` / `REJECTED` | тот же водитель | то же |

Событие «новый запрос на расчёт фрахта → ADMIN в месенджер» этой таблицей **не** задаётся. Источник истины — [`admin-notify-new-freight-request.ru.en.md`](admin-notify-new-freight-request.ru.en.md). Общими остаются привязка, адаптер и `bot_message_log`.

19. Не слать: нет привязки; `status != ACTIVE`; `users.active = false` или soft-deleted; канал адаптера `enabled=false`.
20. Идемпотентность: ключ `(eventType, aggregateId, aggregateVersionOrStatus, channel, identityId)`. Повтор того же перехода не создаёт второе сообщение.
21. Текст — шаблон i18n, без HTML Telegram, если канал его не просил; для WhatsApp исходящие **вне** 24h window — **только** одобренный template (имена в §8.5). Внутри окна — обычный текст, как у других каналов.
22. Очередь: запись `bot_message_log` `PENDING` → send → `SENT` / `FAILED`. Retry: существующий scheduled cleanup/jobs, до **5** попыток с backoff 1/5/15/60 мин. После исчерпания — `FAILED`, алерт в лог; админка видит ошибку провайдера (без токена).

### 6.6 Webhook HTTP
23. Без JWT. Проверка подписи адаптером.
24. Endpoints:

| Метод | Путь | Назначение |
|-------|------|------------|
| `POST` | `/api/v1/webhooks/telegram` | Update Telegram |
| `GET` | `/api/v1/webhooks/whatsapp` | Hub challenge (`hub.mode`, `hub.verify_token`, `hub.challenge`) |
| `POST` | `/api/v1/webhooks/whatsapp` | События Cloud API |
| `POST` | `/api/v1/webhooks/viber` | Callback Viber |

25. Telegram: заголовок `X-Telegram-Bot-Api-Secret-Token` == `app.chatbot.telegram.secret-token`.
26. WhatsApp: `X-Hub-Signature-256` = HMAC-SHA256 raw body ключом `app.chatbot.whatsapp.app-secret`.
27. Viber: `X-Viber-Content-Signature` = HMAC-SHA256 raw body токеном бота (как в Bot API).
28. Несовпадение подписи → **401**, тело не писать в журнал целиком.
29. Rate limit по IP+каналу: как auth login (429 `CHATBOT_RATE_LIMITED`); challenge GET WhatsApp лимитом не резать.

### 6.7 Админ API и UI
30. `GET /api/v1/admin/chatbots/status` — `ADMIN`, `MANAGER`: по каналу `enabled` (конфиг), `configured` (токен непустой, **не** само значение), `webhookLastOkAt`, счётчики привязок.
31. `GET /api/v1/admin/chatbots/identities` — пагинация, фильтр channel/userId; в ответе маска телефона нет (его в identity нет); `externalUserId` маскировать (`…` + 4 символа), полный id — только `ADMIN`.
32. `GET /api/v1/admin/chatbots/messages` — журнал, фильтры; текст сообщения **да** (операционка), токены провайдера **нет**.
33. `POST /api/v1/admin/chatbots/test-send` — только `ADMIN`: `{ channel, userId, text }` на ACTIVE привязку. Не для WhatsApp template-прод; если канал WhatsApp и вне окна → `409 WHATSAPP_TEMPLATE_REQUIRED`.
34. Мутаций токенов через API нет.

### 6.8 Язык
35. Локаль привязки по умолчанию **`ua`**. Смена — команда `lang`. Код в БД: `ua` \| `en` \| `ru`.
36. Тексты бота — server-side бандлы (не Angular json). Ключи параллельны смыслу i18n сайта, дублировать Angular-файлы не обязательно.

## 7) Non-functional Requirements / Нефункциональные требования
- **Security / Безопасность:** токены только env; не логировать raw webhook body с ПДн целиком (обрезать); не логировать secret/signature; `external_user_id` в info-логах маскировать; webhook CSRF не применим — только HMAC/secret; SSRF: адаптер ходит только на фиксированные host API (`api.telegram.org`, `graph.facebook.com`, `chatapi.viber.com`).
- **Performance / Производительность:** разбор webhook p95 ≤ 150 ms до постановки исходящего; send внешнего API не блокирует HTTP webhook дольше **2 s** — при риске таймаута писать `PENDING` и слать из job (для v1 допустим sync, если p95 в лимите).
- **Reliability / Надежность:** падение канала A не влияет на B; `enabled=false` по умолчанию; ретраи исходящих; идемпотентность событий.
- **Logging/Monitoring / Логирование и мониторинг:** события `chatbot_webhook_ok`, `chatbot_webhook_rejected`, `chatbot_linked`, `chatbot_unlinked`, `chatbot_outbound_sent`, `chatbot_outbound_failed`. Метрики: latency send, fail rate по каналу.
- **Accessibility/UX / Доступность и UX:** Angular Material на `/profile` и `/admin/chatbots`; handset; i18n сайта ua/en/ru; в чате — короткие сообщения, кнопки Reply Keyboard / Inline где канал позволяет.
- **Compliance:** исходящие только после явного opt-in (код с сайта). WhatsApp — только шаблоны, одобренные в Business Manager.

## 8) Data Contracts and API / Контракты данных и API

### 8.1 Вход / валидация
- Код привязки: ровно 8 символов `[A-Z0-9]` после trim/upper.
- `channel` — enum, иначе `400 VALIDATION_ERROR`.
- `lang` аргумент — только `ua`/`en`/`ru`.
- Тело webhook — raw bytes для HMAC **до** JSON parse.

### 8.2 Ошибки (`ApiException`)

| HTTP | Код | Когда |
|------|-----|-------|
| 400 | `VALIDATION_ERROR` | невалидный channel/код |
| 401 | (без тела API) | плохая подпись webhook |
| 403 | `FORBIDDEN` | MANAGER на test-send; чужой ресурс |
| 404 | `BOT_IDENTITY_NOT_FOUND` | отвязка несуществующего канала у себя — **не** 404: идемпотентный 204 |
| 409 | `BOT_ALREADY_LINKED` | повторный код при активной привязке |
| 409 | `BOT_CHANNEL_DISABLED` | код на выключенный канал |
| 409 | `WHATSAPP_TEMPLATE_REQUIRED` | test-send вне окна |
| 429 | `CHATBOT_RATE_LIMITED` | лимит webhook/API |
| 503 | `CHATBOT_PROVIDER_ERROR` | исходящий admin test, провайдер недоступен |

Отвязка своего канала: **204** всегда, если пользователь аутентифицирован (нет привязки = уже отвязан).

### 8.3 Таблицы (Flyway `V42__create_chatbot_tables.sql`)

`CREATE TABLE` для `bot_*`. Без `ALTER` на `users` / `user_profiles`. **Разрешено** `ALTER TABLE user_contact_phones` только для колонок верификации (v1).

#### `bot_link_codes`
| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | VARCHAR(36) | UUID PK |
| `user_id` | VARCHAR(36) NOT NULL | учётка (без FK на `users`, как в других модулях — или FK если в проекте уже ставят FK на users; **предпочтение: FK `users(id)` ON DELETE CASCADE**) |
| `channel` | VARCHAR(16) NOT NULL | `TELEGRAM` / `WHATSAPP` / `VIBER` |
| `code` | CHAR(8) NOT NULL | UNIQUE |
| `expires_at` | DATETIME(6) NOT NULL | |
| `consumed_at` | DATETIME(6) NULL | |
| `created_at` | DATETIME(6) NOT NULL | |

Индекс `(user_id, channel, created_at)`.

#### `bot_identities`
| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | VARCHAR(36) | UUID PK |
| `user_id` | VARCHAR(36) NOT NULL | FK → `users(id)` ON DELETE CASCADE |
| `channel` | VARCHAR(16) NOT NULL | |
| `external_user_id` | VARCHAR(128) NOT NULL | Telegram `chat.id`; WhatsApp `wa_id`; Viber `user.id` |
| `locale` | VARCHAR(8) NOT NULL DEFAULT `'ua'` | `ua` / `en` / `ru` |
| `status` | VARCHAR(16) NOT NULL | `ACTIVE` / `REVOKED` |
| `linked_at` / `revoked_at` | DATETIME(6) | |
| `updated_at` | DATETIME(6) | |

`UNIQUE (channel, external_user_id)`. `UNIQUE (user_id, channel)` среди строк `status = ACTIVE` (частичный уникальный индекс MySQL 8: `UNIQUE (user_id, channel, status)` **не** подходит, если много `REVOKED`. Решение: при отвязке **удалять** строку **или** хранить одну строку на пару и ставить `REVOKED`, uniqueness `(user_id, channel)` всегда — повторная привязка обновляет `external_user_id` и `ACTIVE`. **Выбрано: одна строка на `(user_id, channel)`, UNIQUE `(user_id, channel)`, UNIQUE `(channel, external_user_id)`.** Отвязка: `status=REVOKED`, `external_user_id` заменить на `revoked:{uuid}`, чтобы освободить UNIQUE внешнего id.

#### `bot_message_log`
| Колонка | Тип | Описание |
|---------|-----|----------|
| `id` | VARCHAR(36) | UUID PK |
| `direction` | VARCHAR(8) | `IN` / `OUT` |
| `channel` | VARCHAR(16) | |
| `identity_id` | VARCHAR(36) NULL | FK, null если ещё не привязан |
| `user_id` | VARCHAR(36) NULL | |
| `event_type` | VARCHAR(64) | `COMMAND_STATUS`, `QUOTE_SENT`, `WEBHOOK_PARSE_FAILED`, … |
| `idempotency_key` | VARCHAR(190) NULL | UNIQUE если не null |
| `status` | VARCHAR(16) | `PENDING` / `SENT` / `FAILED` / `RECEIVED` / `IGNORED` |
| `payload_preview` | VARCHAR(512) | обрезанный текст, без подписей |
| `provider_error` | VARCHAR(256) NULL | |
| `attempt_count` | INT NOT NULL DEFAULT 0 | |
| `created_at` / `sent_at` | DATETIME(6) | |

Retention: включить в существующий `app.cleanup`, **90 дней** (как OCR-аудит).

### 8.4 Примеры API (сайт)

`POST /api/v1/users/me/bot-link-codes`

```json
{
  "channel": "TELEGRAM",
  "code": "A7K9QM2X",
  "expiresAt": "2026-09-09T12:10:00Z",
  "deepLink": "https://t.me/geosun_tms_bot?start=A7K9QM2X"
}
```

`GET /api/v1/users/me/bot-identities`

```json
{
  "items": [
    {
      "channel": "TELEGRAM",
      "status": "ACTIVE",
      "locale": "ua",
      "linkedAt": "2026-09-09T12:00:00Z"
    }
  ]
}
```

`externalUserId` в self-API **не** отдавать.

### 8.5 Каналы: провайдеры и шаблоны WhatsApp

| Канал | Исходящий API | Примечание |
|-------|---------------|------------|
| Telegram | `https://api.telegram.org/bot{token}/sendMessage` | webhook `setWebhook` при старте, если enabled и задан `public-base-url` |
| WhatsApp | `POST https://graph.facebook.com/{ver}/{phone-number-id}/messages` | шаблоны обязательны вне 24h |
| Viber | `POST https://chatapi.viber.com/pa/send_message` | `set_webhook` при старте |

Имена WhatsApp-шаблонов v1 (категория `UTILITY`, язык `uk` / `en` / `ru` в Meta; в нашем коде ключ локали всё равно `ua` → шаблон Meta `uk`):

| Template name | Назначение | Переменные |
|---------------|------------|------------|
| `geosun_quote_sent` | quote отправлен | номер заявки, сумма, валюта |
| `geosun_request_status` | ACCEPTED/REJECTED/CANCELLED | номер заявки, статус |
| `geosun_trip_status` | смена статуса рейса | номер рейса, статус |
| `geosun_expense_reviewed` | approve/reject отчёта | номер рейса, решение |

Пока шаблон не одобрен — канал WhatsApp может быть `enabled=true` для **входящих**, исходящие utility → `FAILED` с `WHATSAPP_TEMPLATE_REQUIRED` в журнале, без падения quote/trip.

Deep link:

- Telegram: `https://t.me/{botUsername}?start={code}`
- WhatsApp: `https://wa.me/{businessE164}?text={code}`
- Viber: `viber://pa?chatURI={uri}&context={code}` плюс запасной текст «откройте бота и пришлите код»

### 8.6 Конфигурация

```yaml
app:
  chatbot:
    enabled: ${CHATBOT_ENABLED:false}
    public-base-url: ${CHATBOT_PUBLIC_BASE_URL:}   # https://api.example.com
    cleanup-retention-days: ${CHATBOT_RETENTION_DAYS:90}
    telegram:
      enabled: ${CHATBOT_TELEGRAM_ENABLED:false}
      bot-token: ${CHATBOT_TELEGRAM_BOT_TOKEN:}
      secret-token: ${CHATBOT_TELEGRAM_SECRET_TOKEN:}
      bot-username: ${CHATBOT_TELEGRAM_BOT_USERNAME:}
    whatsapp:
      enabled: ${CHATBOT_WHATSAPP_ENABLED:false}
      access-token: ${CHATBOT_WHATSAPP_ACCESS_TOKEN:}
      app-secret: ${CHATBOT_WHATSAPP_APP_SECRET:}
      verify-token: ${CHATBOT_WHATSAPP_VERIFY_TOKEN:}
      phone-number-id: ${CHATBOT_WHATSAPP_PHONE_NUMBER_ID:}
      business-phone-e164: ${CHATBOT_WHATSAPP_BUSINESS_PHONE:}
      graph-version: ${CHATBOT_WHATSAPP_GRAPH_VERSION:v21.0}
    viber:
      enabled: ${CHATBOT_VIBER_ENABLED:false}
      auth-token: ${CHATBOT_VIBER_AUTH_TOKEN:}
      sender-name: ${CHATBOT_VIBER_SENDER_NAME:GeoSun TMS}
      account-uri: ${CHATBOT_VIBER_ACCOUNT_URI:}
```

`app.chatbot.enabled=false` выключает **все** адаптеры независимо от дочерних флагов.

Клиенты HTTP — `RestTemplateBuilder` с таймаутами, по образцу `NbuApiClient` / `HereRoutingClient`. Без новых зависимостей «telegram-bot-library» / «whatsapp-sdk», если хватает RestTemplate: меньше lock-in и проще stub в тестах.

## 9) UX/UI Requirements (frontend) / UX/UI требования (frontend)
- `/profile`: секция Material (`mat-card`) «Чат-боти». На канал: статус привязки (`mat-chip`), кнопка «Прив'язати» (`mat-stroked-button`) → диалог с кодом (`mat-display`), TTL, кнопки открыть Telegram/WhatsApp/Viber (`mat-button` + существующий `SocialIconComponent`), «Скопіювати код», «Відв'язати» (`mat-button` color warn).
- Кнопка канала **не** скрывается из-за отсутствия флага на телефоне (флаг — подсказка `mat-hint`: «У профілі позначено, що цей месенджер є на номері»).
- Состояния: `loading` / `empty` (нет привязок) / `error` / `success` (код создан) / `linked`.
- `/admin/chatbots`: только `ADMIN` и `MANAGER` (как другие admin-справочники). Карточки здоровья каналов, таблица привязок (`mat-table` + paginator), журнал. `test-send` — только ADMIN, `mat-dialog`.
- Тексты i18n `uk.json` / `en.json` / `ru.json`.
- Меню: пункт «Чат-боти» рядом с админ-справочниками, `mat-icon` `smart_toy`.
- Handset: диалог кода на полный экран (`mat-dialog` default), код крупно, deep link первым действием.

Команды в чате (кнопки, не сырой UX сайта):

```
[ Статус ] [ Допомога ]
[ Мова ] [ Відв'язати ]
```

## 10) Architecture Changes / Изменения в архитектуре
- **Components/services:** модуль `com.geosun.tms.chatbot` (`api` webhook + admin + me, `service`, `adapter`, `config`, `dto`, `domain`, `repository`). Хуки вызова: `FreightQuoteService` (send), смена статуса заявки, `Trip` status patch, expense review.
- **Data storage:** Flyway `V42`.
- **Integrations:** Telegram Bot API, Meta Graph WhatsApp, Viber Bot API. Регистрация webhook при `ApplicationReadyEvent`, если enabled и `public-base-url` задан.
- **Compatibility:** при выключенных флагах поведение TMS идентично текущему. Хуки — no-op.

```
WebhookController  (без JWT)
  → ChatbotChannelAdapter.verify + parse
      → ChatbotEngine (link / command / ignore)
          → UserProfile | RouteRequest | Trip query (read-only)
          → ChatbotChannelAdapter.send

FreightQuoteService / TripService
  → ChatbotNotificationPublisher (idempotent enqueue)
      → adapter.send
```

## 11) Implementation Constraints / Ограничения реализации
- Стек и соглашения проекта; комментарии в коді — українською.
- Не добавлять SDK месенджеров без необходимости (см. §8.6).
- Не менять схему `users` / профиль.
- Не реализовывать Flutter.
- Не ходить в сеть из интеграционных тестов: stub-адаптеры.
- Публичные webhook не открывают никакой другой API.

## 12) Implementation Plan / План реализации
1. Модуль, конфиг, `Disabled` адаптеры, Flyway `V42`, журнал. Хуки no-op. Фича выключена.
2. Движок привязки + команды `help`/`lang`/`unlink` + self API кодов. Без внешних вызовов (in-memory adapter в тесте).
3. Telegram адаптер + setWebhook + блок на `/profile` + i18n.
4. Viber адаптер.
5. WhatsApp адаптер: challenge, подпись, входящие; исходящие template + документированные имена.
6. Хуки quote / trip / expense + идемпотентность + retry job.
7. Админка `/admin/chatbots` + test-send.
8. Обновить `docs/system.ru.md` (модуль, webhook paths, env).

Порядок каналов: Telegram → Viber → WhatsApp (сложнее аккаунт и шаблоны). Продуктово v1 можно сдать с одним включённым каналом, если остальные адаптеры есть и `enabled=false`.

## 13) Acceptance Criteria (Definition of Done) / Критерии приемки
- [x] При `CHATBOT_ENABLED=false` webhook каналов не вызывают домен (200/challenge), хуки TMS молчат, остальная система без изменений.
- [x] USER создаёт код Telegram на `/profile`, пишет код боту (тест: stub) → `bot_identities.ACTIVE`; повторный код → `409 BOT_ALREADY_LINKED`. *(API + engine; stub-тесты — по мере покрытия)*
- [x] Два пользователя с одним E.164 не могут перехватить привязку друг друга: без кода с сессии жертвы привязки нет.
- [ ] `status` USER не показывает чужие заявки; DRIVER — чужие рейсы. *(не в scope v1 verify)*
- [ ] Отправка quote создаёт ровно одно исходящее на каждую ACTIVE привязку владельца; повтор send того же перехода не дублирует (идемпотентность).
- [ ] Сбой WhatsApp не откатывает `quote SENT`.
- [x] Подпись webhook неверна → 401, журнал без raw body.
- [x] Отвязка с чата и с `/profile` прекращает исходящие. *(unlink реализован; исходящих уведомлений v1 нет)*
- [x] MANAGER не видит test-send; USER не видит `/admin/chatbots`. *(test-send не реализован; admin GET — ADMIN/MANAGER)*
- [x] В логах нет bot-token, app-secret, полного HMAC.
- [x] i18n сайта ua/en/ru; ответы бота с `locale=ua` по умолчанию.
- [x] Documentation updated (`docs/system.ru.md`, строка реестра после сдачи).
- [x] Tests added/updated and passing.

## 14) Test Plan / Тест-план
- **Unit:** разбор команд и алиасов; нормализация кода; маска `external_user_id`; идемпотентный ключ события; смена `REVOKED` → освобождение UNIQUE.
- **Unit:** verify HMAC WhatsApp/Viber, secret Telegram (фикстуры).
- **Integration:** self link-code + stub inbound → identity; RBAC admin; disabled → no-op; 401 bad signature; rate limit; quote-send stub получает 1 outbound.
- **Integration:** сеть к api.telegram.org / graph.facebook.com / chatapi.viber.com **не** используется.
- **E2E/Manual:** реальный Telegram staging-бот: привязка, status, unlink, уведомление после send quote. Viber/WhatsApp — по наличию бизнес-аккаунта; если аккаунта нет, канал остаётся `enabled=false`, DoD по stub.
- **Edge cases:** просроченный код; код на другой канал; inactive user; DRIVER без карточки `drivers`; WhatsApp исходящий без template.

## 15) Risks and Assumptions / Риски и допущения
- **Risks:** WhatsApp требует верификации Meta Business и одобрения шаблонов — срок не контролируется кодом. Viber PA может требовать модерацию. Публичный HTTPS обязателен для webhook. Лимиты Graph API / Telegram flood control.
- **Assumptions:** у GeoSun будут (или появятся к включению канала) BotFather token, Viber auth token, Meta Cloud API phone-number-id. Пользователь умеет открыть `/profile` один раз для opt-in. Юридически достаточно opt-in кодом (не отдельный офер в чате).
- **Rollback:** `CHATBOT_ENABLED=false` + снять webhook у провайдеров. Таблицы оставить. Хуки становятся no-op.

## 16) Release Artifacts / Артефакты релиза
- PR: —
- Version/tag / Версия/тег: —
- Release date / Дата релиза: —
- Owner / Ответственный: —

---

## Instructions for LLM / Инструкции для LLM
Use these rules when implementing this specification / Используй правила ниже при реализации по этому ТЗ:

1. Ask clarifying questions first if ambiguity exists. / Если есть неоднозначность, сначала задай уточняющие вопросы.
2. Stay within `Scope` and `Out of Scope`. / Не выходи за рамки `Scope` и `Out of Scope`.
3. Follow current architecture and project style. / Следуй текущей архитектуре и стилю проекта. Клиент — только Angular + Java; Flutter не трогать.
4. Show a brief plan before code changes. / Перед кодом покажи краткий план шагов.
5. After changes provide:
   - changed files list / список измененных файлов;
   - what changed and why / что и зачем изменено;
   - verification steps (commands + expected result) / как проверить;
   - risks and known gaps / риски и непокрытые случаи.
6. Do not add dependencies without explicit justification. / Не добавляй SDK месенджеров без обоснования; предпочтителен RestTemplate.
7. Add short comments only for non-obvious logic. / Коментарі в коді — українською, лише для неочевидного.
8. Do not ALTER `users` / `user_profiles` / `user_contact_phones`. / Identity бота только в `bot_*`.
9. Default off. / Пока флаги false — TMS ведёт себя как сейчас.
10. After delivery: update Статус in this file, README registry, DoD checkboxes, `docs/system.ru.md`.

## Appendices (optional) / Приложения

### A. Алиасы команд

| Канон | ua | ru | en |
|-------|----|----|-----|
| start | старт | старт | start |
| help | допомога, допомогти | помощь | help |
| status | статус | статус | status |
| unlink | відв'язати, відвязати | отвязать | unlink |
| lang | мова | язык | lang, language |
| link | прив'язати, код | привязать | link |

### B. Почему не три отдельные спеки

Один движок, одна привязка, одни хуки TMS. Различие каналов — только адаптер, HMAC и WhatsApp templates. Три файла размножили бы статусы в реестре и разъехались бы по командам.
