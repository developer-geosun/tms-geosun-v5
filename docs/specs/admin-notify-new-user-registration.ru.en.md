# Technical Specification / Техническое задание: Admin notify on new user registration / Уведомление ADMIN о новой регистрации

## Статус
- **Реализация:** реализовано
- **Роль:** источник истины по событию «новый пользователь зарегистрировался» → исходящие ADMIN
- **Клиент:** Angular + Java (`frontend-flutter/` не входит)
- **Остаток:** реальный SMS-провайдер (v1 = `none` / SKIP); WhatsApp template `admin_user_registered` (пока SKIP)
- **Реестр:** [README.ru.md](README.ru.md)

Целевой клиент — `frontend-angular`. `frontend-flutter/` не входит (заморозка Flutter).

## Language Rules / Правила языка
- **Primary language / Основной язык:** RU
- **Secondary language / Дополнительный язык:** EN
- **Terms to keep in English / Термины, которые оставляем на английском:** `RBAC`, `SMTP`, `SMS`, `E.164`, `afterCommit`, `opt-in`, `Definition of Done`

---

## 1) Goal / Цель
- **Problem / Проблема:** `POST /api/v1/auth/register` создаёт учётку `USER` и шлёт письмо верификации **только** новому адресу. ADMIN узнаёт о регистрации, только если сам откроет `/admin/users`.
- **Value / Ценность:** каждый активный ADMIN сразу видит факт новой регистрации тем каналом, который сам отметил в профиле (email / телефон / месенджеры), без обхода списка пользователей.
- **Expected outcome / Ожидаемый результат:** после успешной регистрации система best-effort доставляет короткое служебное сообщение всем получателям `role = ADMIN` по **их** `preferredChannels`. Сбой доставки **не** откатывает регистрацию и **не** меняет ответ `POST /auth/register`.

## 2) Context / Контекст
- **Project/module / Проект/модуль:** `backend-java` (хук после `AuthService.register`) + существующие транспорты: `JavaMailSender`, пакет `com.geosun.tms.chatbot`, опциональный SMS-адаптер. Angular: подсказка на `/profile` для роли ADMIN; отдельного экрана «журнал регистраций» нет.
- **Current behavior / Текущее поведение:**
  - Регистрация всегда ставит `Role.USER`, `emailVerified=false`; письмо верификации — `VerificationMailSender` (ошибка SMTP глотается, регистрация 200).
  - Каналы связи — три boolean в `user_profiles` (`contact_via_email` / `contact_via_phone` / `contact_via_messengers`), см. [user-profile.md](user-profile.ru.en.md) §6.4. Сейчас они описывают, **куда писать человеку**, а не системные push.
  - Исходящие месенджеры в chatbot-спеке заложены для quote/trip/expense; хука `USER_REGISTERED` нет; WhatsApp/Viber исходящие ещё не сданы.
  - SMS-провайдера в стеке нет.
- **Related docs / Связанные документы:**
  - [`user-profile.ru.en.md`](user-profile.ru.en.md) — источник флагов каналов и телефонов ADMIN.
  - [`chatbots-telegram-whatsapp-viber.ru.en.md`](chatbots-telegram-whatsapp-viber.ru.en.md) — транспорт `MESSENGERS` (`bot_identities`, `bot_message_log`).
  - [`TECHNICAL_SPECIFICATION_API_SERVER_v1.0.ru.md`](../archive/TECHNICAL_SPECIFICATION_API_SERVER_v1.0.ru.md) (архив, исторический MVP) — `POST /auth/register`.
  - [`admin-user-management.ru.en.md`](admin-user-management.ru.en.md) — карточка `/admin/users/{id}` как цель ссылки.
  - [`auth-authentication-authorization.ru.en.md`](auth-authentication-authorization.ru.en.md) — роли, JWT (этот хук публичный API не добавляет).
  - [`multi-enterprise.ru.en.md`](multi-enterprise.ru.en.md) (B21) — получатели только ADMIN того же предприятия.
- **Environment constraints / Ограничения окружения:** Java 21 / Spring Boot 3, MySQL, Angular 21 + Material, i18n ua/en/ru (файлы `uk.json` / `en.json` / `ru.json` не переименовывать). SMTP — тот же `app.email.*`, что верификация. Ссылка в сообщении — **только Angular** (`app.email.angular-app-base-url` + `/admin/users/{id}`), Flutter-URL не использовать.

## 3) Scope (In) / Scope (входит в задачу)
- Триггер: успешный `POST /api/v1/auth/register` (учётка сохранена). Момент — **afterCommit**, не после verify-email.
- Получатели: все `users` с `role = ADMIN`, `is_active = true`, `is_deleted = false`. Роль `MANAGER` **не** получает это событие.
- Каналы доставки — **профиль каждого получателя-ADMIN**, не профиль нового USER:
  - `EMAIL` → SMTP на `users.email` этого ADMIN;
  - `PHONE` → SMS на **primary** E.164 из `user_contact_phones` этого ADMIN;
  - `MESSENGERS` → исходящие chatbot на **все ACTIVE** `bot_identities` этого ADMIN (Telegram / WhatsApp / Viber — кто реально привязан и чей адаптер `enabled`).
- Fallback, если у ADMIN нет строки профиля **или** ни один канал не выбран: слать **только EMAIL** на `users.email` (seed-админ без `/profile` не должен «молчать»).
- Best-effort: исключение транспорта не меняет HTTP регистрации; не глотать без лога.
- Идемпотентность на пару `(новый user id, admin id, канал доставки)`.
- Короткий hint на `/profile` **только для роли ADMIN**: выбранные каналы используются и для системных уведомлений (это событие).
- i18n текстов письма/SMS/бота: ua / en / ru. Письмо — триязычное в одном теле (как verification). SMS — один язык: **ua**. Месенджер — `bot_identities.locale` (по умолчанию `ua`).
- Feature-flag `app.notifications.user-registered.enabled` (default **true**): `false` → хук no-op. EMAIL не зависит от `CHATBOT_ENABLED`. `MESSENGERS` зависят от адаптера канала. `PHONE` зависит от SMS-провайдера.

## 4) Out of Scope / Out of Scope (не входит)
- Flutter-клиент.
- Уведомление `MANAGER` / `DRIVER` / самого нового USER (у USER уже есть verification email).
- Ждать `emailVerified=true`. Событие — факт **заявки на регистрацию**, в том числе ещё не подтверждённый email.
- Создание/invite пользователя админом (такого API нет; если появится — отдельное событие, не это ТЗ).
- Restore / activate / смена роли на ADMIN — не «регистрация».
- Голосовой звонок, WhatsApp call, push браузера, in-app inbox, колокольчик в Angular toolbar.
- Отдельная admin-страница журнала регистрационных алертов (v1: логи + `bot_message_log` для месенджеров).
- Смена правил профиля (обязательность каналов, ЄДРПОУ, телефоны) — остаётся [user-profile.md](user-profile.ru.en.md).
- Новые колонки на `users` / `user_profiles` / `user_contact_phones`.
- Выбор конкретного SMS-вендора (TurboSMS / Twilio и т.п.) и договор с ним. В v1 — интерфейс + реализация `none` (канал PHONE тогда SKIP).
- Дублирование бизнес-логики quote/trip из chatbot-спеки. Это ТЗ добавляет **один** `eventType`, не второй движок бота.
- Маркетинговые рассылки, дайджесты, «пользователь заполнил профиль».

## 5) User Stories / Пользовательские сценарии
1. **Как** ADMIN, **я хочу** получить письмо, если в профиле отмечен канал EMAIL, **чтобы** увидеть нового пользователя, не открывая админку вручную.
2. **Как** ADMIN, **я хочу** получить сообщение в привязанный месенджер, если отмечен канал MESSENGERS, **чтобы** узнать о регистрации с телефона.
3. **Как** ADMIN, **я хочу** получить SMS на основной телефон, если отмечен канал PHONE и SMS включён, **чтобы** канал «телефон» тоже работал как системный.
4. **Как** ADMIN без заполненного профиля, **я хочу** всё равно получить письмо на учётный email, **чтобы** seed-учётка не пропускала событие.
5. **Как** новый USER, **я хочу** чтобы сбой письма админам не ломал мою регистрацию, **чтобы** я по-прежнему получил (или нет) только verification-flow как сейчас.

## 6) Functional Requirements / Функциональные требования

### 6.1 Триггер
1. Слушать успешное завершение `AuthService.register` **после commit** транзакции (`TransactionSynchronization.afterCommit` или доменное событие, публикуемое afterCommit). Публикация **внутри** той же `@Transactional`, что `userRepository.save`, без afterCommit — запрещена: при rollback админы получат ложный алерт.
2. Не вызывать хук, если регистрация не состоялась (`409` email занят, `429` rate limit, валидация).
3. Не вызывать хук на `verify-email`, `resend-verification`, login, restore.
4. Флаг `app.notifications.user-registered.enabled=false` — хук не выполняется (в логе один раз на событие: `admin_notify_disabled`).

### 6.2 Получатели
5. Выборка: `role = ADMIN AND is_active = true AND is_deleted = false`.
6. Новый пользователь в выборку не попадает (роль `USER`).
7. Inactive / soft-deleted ADMIN — не слать.
8. Несколько ADMIN — независимая доставка каждому; сбой одного не отменяет остальных.

### 6.3 Маппинг каналов профиля → транспорт

Для каждого получателя вычислить набор каналов:

| Условие профиля ADMIN | Каналы отправки |
|-----------------------|-----------------|
| Есть `user_profiles` и хотя бы один `contact_via_* = true` | Ровно отмеченные флаги |
| Нет профиля **или** все три флага `false` | Только `EMAIL` (fallback) |

9. **EMAIL** (флаг или fallback):
   - Кому: `users.email` получателя-ADMIN (не email нового USER в `To:`).
   - Транспорт: тот же `JavaMailSender` / `app.email.from`, что verification.
   - Тема и тело: §8.4. В теле — email и id **нового** пользователя, ссылка на Angular `/admin/users/{id}`.
   - Не класть пароль, verification token, hash.
10. **PHONE**:
    - Только если `contact_via_phone = true` (fallback EMAIL **не** включает SMS).
    - Номер: primary телефон профиля; если списка нет — SKIP `admin_notify_phone_skipped_no_number`.
    - Если SMS-адаптер `enabled=false` / `provider=none` — SKIP `admin_notify_phone_skipped_no_provider` (это штатно для v1, не ошибка сдачи EMAIL/MESSENGERS).
    - Текст SMS: короткий, ua, без HTML, ≤ 160 символов предпочтительно (если не влезает — обрезать email нового пользователя, id оставить).
    - Голосовые вызовы запрещены.
11. **MESSENGERS**:
    - Только если `contact_via_messengers = true`.
    - Не слать «по флажку `has_telegram` на телефоне» без `bot_identities.status = ACTIVE`: identity = opt-in, флаг телефона — подсказка UI ([chatbots-telegram-whatsapp-viber.md](chatbots-telegram-whatsapp-viber.ru.en.md) §6.2).
    - Для каждой ACTIVE-привязки ADMIN: если адаптер канала `enabled` — исходящее с `eventType = USER_REGISTERED` в `bot_message_log`; иначе SKIP по этому каналу.
    - Нет ни одной ACTIVE-привязки — SKIP `admin_notify_messenger_skipped_no_identity`.
    - Правила идемпотентности, ретраев и «сбой send не откатывает домен» — как chatbot §6.5 п.19–22. Домен здесь — регистрация (уже закоммичена).
    - WhatsApp вне 24h window — только одобренный template; имя шаблона зафиксировать в коде/конфиге (например `admin_user_registered`); пока шаблона нет — SKIP с логом, не слать свободный текст.

12. Один ADMIN с EMAIL+MESSENGERS → письмо **и** каждое привязанное ACTIVE-чат. Это не «один канал на выбор», а **все отмеченные**.

### 6.4 Содержание сообщения (канон)
Поля (все обязательны в письме; в SMS — подмножество):

| Поле | Письмо / бот | SMS |
|------|----------------|-----|
| Факт: новый пользователь зарегистрировался | да | да |
| `email` нового USER | да | да (можно обрезать) |
| `id` (UUID) | да | да |
| `createdAt` (ISO-8601 UTC) | да | нет |
| `emailVerified=false` на момент события | да | нет |
| Ссылка `{angularAppBaseUrl}/admin/users/{id}` | да | нет (длина) |

13. Язык письма: три блока ua / en / ru в одном MIME (plain + html), стиль verification-шаблонов (`AuthMailComposer`, брендинг).
14. Бот: ключи server-side бандлов chatbot, locale привязки.
15. Не подставлять ФИО нового USER: на момент register профиля ещё нет.

### 6.5 Надёжность и идемпотентность
16. Ключ: `USER_REGISTERED|{newUserId}|{adminUserId}|{EMAIL\|PHONE\|TELEGRAM\|WHATSAPP\|VIBER}`. Повторная обработка того же ключа не создаёт второе SENT-сообщение.
17. Месенджеры: писать ключ в `bot_message_log.idempotency_key` (UNIQUE).
18. EMAIL/SMS v1: отдельную таблицу outbox **не** вводить. Повторный вызов хука на тот же `newUserId` не ожидается (email уникален среди активных). Ретраи SMTP/SMS внутри одного вызова — на усмотрение реализации (0 или 1 повтор), без бесконечного цикла.
19. Регистрация и verification-письмо **не ждут** рассылку админам. Хук — async после commit (executor / `@Async` / очередь в процессе). p95 `POST /register` не должен расти на N×SMTP.

### 6.6 Безопасность и антиспам
20. В логах: маска телефонов (`+380*****1234`); не логировать SMTP password, bot token, полный SMS-текст с PII целиком (preview ≤ 80 символов).
21. Получатель видит email нового USER — это служебные данные для ADMIN, не публичный API.
22. Rate limit регистрации уже есть; отдельный лимит «алертов админам» не нужен. Защита от фейковых регистраций — существующий `checkRegister`, не это ТЗ.
23. Ссылка в письме ведёт на защищённый `/admin/users/{id}`: без JWT страница логина, не раскрывает карточку анониму.

### 6.7 Frontend
24. Отдельного Angular-экрана нет.
25. На `/profile` для `role === ADMIN` — короткий hint под блоком каналов (i18n `pages.profile.adminNotifyChannelsHint`): системные уведомления (новая регистрация) идут на выбранные каналы. Другим ролям hint не показывать.
26. Не менять валидацию каналов и не делать каналы опциональными «ради алертов».

## 7) Non-functional Requirements / Нефункциональные требования
- **Security / Безопасность:** нет пароля/токена верификации в алертах; маска телефонов в логах; RBAC ссылки — существующий `/admin/users`.
- **Performance / Производительность:** хук не блокирует HTTP register; выборка ADMIN — один запрос (роль+флаги), профили/телефоны/identity — batch, без N+1 «по одному SELECT на админа».
- **Reliability / Надежность:** afterCommit; best-effort; падение Telegram не отменяет email тому же ADMIN; `enabled=false` на канале = SKIP, не 500.
- **Logging/Monitoring / Логирование и мониторинг:** события `admin_notify_queued`, `admin_notify_email_sent`, `admin_notify_email_failed`, `admin_notify_sms_sent` / `_skipped_*` / `_failed`, `admin_notify_messenger_queued`, `admin_notify_disabled`. Метрика: число SENT/FAILED/SKIP по каналу.
- **Accessibility/UX / Доступность и UX:** hint на `/profile` — Angular Material, i18n ua/en/ru, читаем на handset.

## 8) Data Contracts and API / Контракты данных и API

### 8.1 Input Data / Входные данные
- Внешнего API нет. Вход хука: `newUserId`, `newUserEmail`, `createdAt` (из сохранённого `User`).
- Конфиг:

| Ключ | Default | Смысл |
|------|---------|--------|
| `app.notifications.user-registered.enabled` | `true` | мастер-выключатель |
| `app.sms.enabled` | `false` | SMS-транспорт |
| `app.sms.provider` | `none` | `none` \| будущий код провайдера |
| chatbot `enabled` по каналу | как сейчас | транспорт MESSENGERS |

### 8.2 Output Data / Выходные данные
- `POST /api/v1/auth/register` **без изменений** контракта (`id`, `email`, `role`).
- Ошибки хука клиенту не возвращаются.

### 8.3 Endpoints (if any) / Эндпоинты (если есть)
- Новых endpoint-ов нет.
- Косвенно используется уже существующий `GET /api/v1/admin/users/{id}` (цель ссылки в письме/боте).

### 8.4 Пример письма (смысл, не финальная вёрстка)

Subject: `Нова реєстрація / New registration / Новая регистрация`

```
Новий користувач зареєструвався.
Email: user@example.com
Id: 550e8400-e29b-41d4-a716-446655440000
Час: 2026-09-10T15:04:05Z
Email ще не підтверджено.
Картка: http://localhost:4200/admin/users/550e8400-e29b-41d4-a716-446655440000
```

Плюс EN и RU блоки. HTML — тот же смысл + брендинг GeoSun как в verification.

### 8.5 SMS (если провайдер включён)

```
GeoSun: нова реєстрація user@example.com id=550e8400-e29b-41d4-a716-446655440000
```

### 8.6 Ошибки / SKIP (не HTTP клиенту)

| Код в логе | Когда |
|------------|--------|
| `admin_notify_disabled` | мастер-флаг false |
| `admin_notify_phone_skipped_no_provider` | PHONE отмечен, SMS выключен |
| `admin_notify_phone_skipped_no_number` | PHONE отмечен, нет primary |
| `admin_notify_messenger_skipped_no_identity` | MESSENGERS отмечен, нет ACTIVE |
| `admin_notify_messenger_channel_disabled` | привязка есть, адаптер off |
| `admin_notify_email_failed` | SMTP exception |
| `admin_notify_whatsapp_template_missing` | нет одобренного template |

## 9) UX/UI Requirements (frontend) / UX/UI требования (frontend)
- States: hint статичный, без loading (каналы уже на форме профиля).
- Form behavior: без новых полей.
- Navigation: из письма/бота — `/admin/users/{id}` открывает список и диалог профиля (после закрытия URL → `/admin/users`); если id не найден — snack `userNotFound`.
- UI texts: `pages.profile.adminNotifyChannelsHint` в `uk.json` / `en.json` / `ru.json`.

## 10) Architecture Changes / Изменения в архитектуре
- **Components/services / Компоненты/сервисы:**
  - `UserRegisteredEvent` (или эквивалент) + `AdminNewUserNotifier` в `com.geosun.tms.auth` (оркестрация получателей и каналов).
  - `AdminNotifyMailSender` рядом с `VerificationMailSender` (отдельный шаблон, не смешивать с verify-token).
  - `SmsSender` interface + `DisabledSmsSender` (`provider=none`).
  - Вызов chatbot outbound API/сервиса с `eventType=USER_REGISTERED` — **не** копировать адаптеры.
- **Data storage / Хранилище данных:** без новой Flyway-таблицы в v1. Месенджеры — `bot_message_log`. EMAIL/SMS — только логи.
- **Integrations / Интеграции:** SMTP (есть); Telegram/WhatsApp/Viber (есть/частично); SMS — заглушка.
- **Compatibility / Совместимость:** контракт register без изменений; при `enabled=false` поведение = сегодня.

## 11) Implementation Constraints / Ограничения реализации
- Use existing stack and project conventions. / Использовать существующий стек и соглашения проекта.
- Do not add dependencies without rationale. / Не добавлять SMS/месенджер SDK без обоснования; HTTP-клиент как у chatbot.
- Do not change unrelated modules. / Не менять quote/trip workflow.
- Preserve backward compatibility where required. / `POST /auth/register` и verification-письмо без регрессий.
- Комментарі в коді — українською, лише для неочевидного.
- Клиент — только Angular + Java; Flutter не трогать.

## 12) Implementation Plan / План реализации
1. Событие afterCommit + выборка ADMIN + расчёт каналов (unit: fallback, флаги, skip inactive). Флаг `enabled=false` → no-op.
2. EMAIL-шаблон + sender; integration: register при stub `JavaMailSender` — N писем на N admin с EMAIL (плюс verification адресату).
3. Хук MESSENGERS через chatbot engine (`USER_REGISTERED`, идемпотентность); stub-адаптер в тесте.
4. `SmsSender` none + SKIP; контракт интерфейса готов к будущему провайдеру.
5. Hint на `/profile` для ADMIN; i18n.
6. Обновить Статус этого файла, строку реестра, DoD, `docs/system.ru.md` после сдачи.

## 13) Acceptance Criteria (Definition of Done) / Критерии приемки
- [x] После успешного register каждый активный не-удалённый ADMIN с `contact_via_email` получает одно письмо с email/id нового USER и Angular-ссылкой на карточку.
- [x] ADMIN без профиля (или без выбранных каналов) получает fallback-письмо на учётный email.
- [x] ADMIN с только `MESSENGERS` не получает письмо; получает исходящее на каждую ACTIVE-привязку при `enabled` адаптера; без привязки — SKIP, регистрация 200.
- [x] `MANAGER` и `DRIVER` писем/ботов по этому событию не получают.
- [x] Сбой SMTP админам не меняет 200 register и не отменяет verification-письмо новому USER (как сейчас: verification — свой try/catch).
- [x] `app.notifications.user-registered.enabled=false` — админам ничего не уходит, register как сейчас.
- [x] PHONE при `app.sms.enabled=false` — SKIP в логе, не падение.
- [x] В письме/SMS/боте нет пароля и verification token.
- [x] Hint каналов виден только ADMIN на `/profile`.
- [x] Documentation updated / Документация обновлена (этот файл, README, `docs/system.ru.md` при сдаче).
- [x] Tests added/updated and passing / Тесты добавлены или обновлены и проходят.

## 14) Test Plan / Тест-план
- **Unit:** матрица каналов (флаги / fallback / нет primary / нет identity / inactive admin / deleted admin / manager excluded).
- **Unit:** идемпотентный ключ; обрезка SMS; маска телефона в лог-хелпере.
- **Integration (MockMvc):** register → `JavaMailSender` получил verification **и** N admin-писем; получатели только ADMIN; MANAGER в БД есть — 0 писем ему.
- **Integration:** chatbot stub: 1 outbound `USER_REGISTERED` на ACTIVE Telegram; повтор хука не дублирует (unique key).
- **Integration:** SMTP admin throw → register всё ещё 200, verification вызван.
- **Integration:** флаг enabled=false → 0 admin-писем.
- **E2E/Manual:** два ADMIN (email vs telegram), register с Angular `/register`, проверить ящик и чат; открыть ссылку → карточка пользователя.
- **Edge cases / Граничные случаи:** ноль ADMIN в БД; ADMIN без emailVerified (всё равно слать — он уже в системе); два ADMIN, у одного SMTP ok, у второго fail; WhatsApp без template.

## 15) Risks and Assumptions / Риски и допущения
- **Risks / Риски:** публичная регистрация + несколько ADMIN с EMAIL даст пачку писем на каждый signup (в т.ч. боты до verify). Смягчение: существующий rate limit register, не новый продукт-фильтр. WhatsApp template может отсутствовать месяцами — канал будет SKIP. SMS в v1 фактически выключен: ADMIN, отметивший **только** PHONE, не получит алерт, пока нет провайдера (fallback EMAIL **не** срабатывает, потому что канал выбран). Это нужно явно сказать при онбординге админов: для гарантии оставьте EMAIL.
- **Assumptions / Допущения:** «каналы связи» профиля ADMIN переиспользуются как каналы **системных** уведомлений для этого события. Получатель = ADMIN, не MANAGER. Событие = register, не verify. Ссылка всегда Angular.
- **Rollback plan / План отката:** `app.notifications.user-registered.enabled=false`. Шаблоны писем и код можно оставить. Таблиц откатывать не нужно.

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
6. Do not add dependencies without explicit justification. / Не добавляй SMS SDK без обоснования.
7. Add short comments only for non-obvious logic. / Коментарі в коді — українською, лише для неочевидного.
8. Do not ALTER `users` / `user_profiles` / `user_contact_phones`. / Новых колонок на этих таблицах нет.
9. afterCommit + async. / Не блокировать `POST /auth/register` рассылкой и не слать до commit.
10. After delivery: update Статус in this file, README registry, DoD checkboxes, `docs/system.ru.md`.

## Appendices (optional) / Приложения (опционально)

### A. Последовательность

```
POST /auth/register
  → persist USER + verification token
  → commit
  → async AdminNewUserNotifier
       → find active ADMIN
       → per admin: channels from profile (or EMAIL fallback)
       → EMAIL / SMS / chatbot outbound
  → HTTP 200 register (не ждёт notifier)
```

Verification-письмо новому USER остаётся **в** `register` (как сейчас, до return) и **не** является частью этого ТЗ.
