# Technical Specification / Техническое задание: Admin notify on new freight request / Сообщение ADMIN о новом запросе на расчёт фрахта

**Дата создания:** 27 октября 2026, 11:12 (UTC+3)  
**Дата изменения:** 29 октября 2026, 13:30 (UTC+3)

## Статус
- **Реализация:** реализовано
- **Роль:** источник истины по событию «пользователь создал запрос на расчёт фрахта» → исходящее ADMIN в месенджер
- **Клиент:** Angular + Java (`frontend-flutter/` не входит)
- **Остаток:** WhatsApp template `admin_freight_request_created` (v1 = SKIP); EMAIL/SMS по этому событию — вне scope
- **Реестр:** [README.ru.md](README.ru.md)
- **Brief:** [admin-notify-new-freight-request.ru.md](../briefs/admin-notify-new-freight-request.ru.md)
- **План:** [admin-notify-new-freight-request.ru.md](../plans/admin-notify-new-freight-request.ru.md)

Целевой клиент — `frontend-angular`. `frontend-flutter/` не входит (заморозка Flutter).

## Language Rules / Правила языка
- **Primary language / Основной язык:** RU
- **Secondary language / Дополнительный язык:** EN
- **Terms to keep in English / Термины, которые оставляем на английском:** `RBAC`, `afterCommit`, `opt-in`, `idempotency`, `Definition of Done`, `eventType`

---

## 1) Goal / Цель
- **Problem / Проблема:** успешный запрос на расчёт фрахта (`RouteRequest`, статус `NEW`) виден только в админской очереди. ADMIN узнаёт о нём, если сам откроет очередь. Исходящее в месенджер сейчас есть только на новую регистрацию учётки (`USER_REGISTERED`).
- **Value / Ценность:** каждый подходящий ADMIN сразу видит факт нового запроса в уже привязанном месенджере и открывает очередь по ссылке.
- **Expected outcome / Ожидаемый результат:** после успешного создания запроса система best-effort шлёт одно короткое сообщение каждому активному ADMIN с каналом `MESSENGERS` и ACTIVE-привязкой. Сбой доставки **не** откатывает запрос и **не** меняет ответ создания.

## 2) Context / Контекст
- **Project/module / Проект/модуль:** хук после сохранения заявки в контуре маршрутов; транспорт — существующий пакет chatbot. Angular: ссылка открывает `/admin/route-requests`; подсказка каналов на `/profile` для ADMIN дополняется этим событием.
- **Current behavior / Текущее поведение:**
  - Создание заявки сохраняет `RouteRequest` со статусом `NEW` и строку истории. Исходящего сообщения нет.
  - Несколько заявок на один маршрут разрешены (B05). Каждая — отдельный факт.
  - Получатели `USER_REGISTERED` — активные учётки с доступной ролью `ADMIN`, каналы из профиля. Письмо и SMS к **этому** событию не относятся.
  - Тексты бота v1 сейчас на украинском. У привязки есть `locale` (`ua` / `en` / `ru`, по умолчанию `ua`).
  - Страница `/admin/route-requests` доступна `ADMIN` и `MANAGER`, но query-параметра «открыть заявку» нет.
- **Related docs / Связанные документы:**
  - [`admin-notify-new-freight-request.ru.md`](../briefs/admin-notify-new-freight-request.ru.md) — brief (алгоритм A).
  - [`routes-server-workflow-and-freight-quoting.ru.en.md`](routes-server-workflow-and-freight-quoting.ru.en.md) — заявка (B07). Это ТЗ **не** меняет FSM, quote и lock.
  - [`admin-notify-new-user-registration.ru.en.md`](admin-notify-new-user-registration.ru.en.md) — образец получателя ADMIN и best-effort (B04). Событие другое; EMAIL/SMS оттуда **не** копировать.
  - [`multi-enterprise.ru.en.md`](multi-enterprise.ru.en.md) (B21) — получатели только ADMIN предприятия автора заявки.
  - [`chatbots-telegram-whatsapp-viber.ru.en.md`](chatbots-telegram-whatsapp-viber.ru.en.md) — транспорт привязки, адаптер, `bot_message_log` (B16). Список quote/trip/expense в той спеке этим файлом не расширяется.
  - [`user-profile.ru.en.md`](user-profile.ru.en.md) — флаг канала `MESSENGERS`.
  - [`user-multi-role-switching.ru.en.md`](user-multi-role-switching.ru.en.md) — «есть роль ADMIN» = доступная роль, не только активная.
- **Environment constraints / Ограничения окружения:** Java 21 / Spring Boot 3, MySQL, Angular 21 + Material, i18n сайта ua/en/ru (файлы `uk.json` / `en.json` / `ru.json` не переименовывать). Ссылка — только Angular base URL. Новых SDK месенджеров нет.

## 3) Scope (In) / Scope (входит в задачу)
- Триггер: успешное создание запроса на расчёт фрахта пользователем. Момент — **afterCommit**.
- Получатели: активные не удалённые учётки с доступной ролью `ADMIN`, у которых в профиле включён канал `MESSENGERS`. Автор запроса в рассылку не входит, даже если у него есть роль `ADMIN`.
- Канал доставки — **только месенджер**: все ACTIVE-привязки получателя, чей адаптер включён. Один канал — одно сообщение (если привязок одного канала несколько, уходит первая, вторая тем же ключом не дублируется).
- Текст: факт нового запроса, id заявки, email автора, ссылка в админскую очередь с `requestId`. Язык — `locale` привязки, неизвестный locale → `ua`.
- Идемпотентность на пару «заявка + админ + канал».
- Best-effort: ошибка транспорта не меняет HTTP создания заявки.
- Feature-flag, по умолчанию включён. Выключенный флаг — тишина в месенджерах, заявка создаётся как сейчас.
- `/admin/route-requests?requestId={id}` открывает карточку этой заявки. Невалидный или чужой id не ломает очередь.
- Подсказка каналов на `/profile` только для `ADMIN`: к примеру про регистрацию добавить пример нового запроса на расчёт. Новых полей профиля нет.

## 4) Out of Scope / Out of Scope (не входит)
- Flutter.
- Письмо и SMS по этому событию. `USER_REGISTERED` (письмо / SMS / месенджер) не менять.
- Сообщение `MANAGER`, `DRIVER` и автору запроса.
- Уведомления о quote, смене статуса заявки, рейсе, расходах (остаются бэклогом chatbot).
- Дайджест, колокольчик в Angular, отдельный журнал алертов.
- Пересчёт breakdown, preview стоимости, черновик quote — не «новый запрос».
- Новые колонки профиля и новая Flyway-таблица outbox. Журнал месенджера — существующий `bot_message_log`.
- Одобрение WhatsApp-template в Meta. Пока шаблона нет — SKIP, свободный текст в WhatsApp не слать.
- Смена правил, кому видна очередь (MANAGER по-прежнему открывает `/admin/route-requests` в вебе, но это событие ему не шлётся).

## 5) User Stories / Пользовательские сценарии
1. **Как** ADMIN с включёнными месенджерами и привязанным ботом, **я хочу** получить сообщение о новом запросе на расчёт, **чтобы** не обновлять очередь вручную.
2. **Как** ADMIN, **я хочу** открыть по ссылке именно эту заявку, **чтобы** сразу видеть карточку, а не искать строку.
3. **Как** пользователь, **я хочу** чтобы сбой бота не отменял мой запрос, **чтобы** заявка сохранилась как сейчас.
4. **Как** ADMIN без канала «месенджеры» или без привязки, **я хочу** не получать пустые ошибки, **чтобы** тишина была штатным пропуском.
5. **Как** автор запроса, даже если я ADMIN, **я хочу** не получать это сообщение о своей же заявке.

## 6) Functional Requirements / Функциональные требования

### 6.1 Триггер
1. Единственная точка: успешное сохранение новой заявки в сценарии создания пользователем (статус `NEW`, история «создано»). Публикация — **после commit**. Вызов внутри той же транзакции до commit запрещён.
2. Не вызывать хук, если создание не состоялось (маршрут не найден, валидация, нет прав).
3. Не вызывать хук на смене статуса, quote, breakdown, cost-preview, правке маршрута.
4. Повторное создание другой заявки на тот же маршрут — новое событие (другой id).
5. `app.notifications.freight-request-created.enabled=false` — хук не шлёт сообщения. В логе один раз: `freight_request_notify_disabled`. Создание заявки без изменений.

### 6.2 Получатели
6. Выборка: доступная роль `ADMIN`, `is_active = true`, `is_deleted = false`. Активная роль в этот момент может быть другой — учётка всё равно получатель, если ADMIN есть среди доступных ролей.
7. Исключить `userId` автора заявки.
8. `MANAGER` без роли `ADMIN`, `DRIVER`, `USER` — не получатели.
9. Дальше оставить только тех, у кого канал профиля `MESSENGERS` включён. Нет профиля, флаг выключен или каналы пустые — **не** писать и **не** подменять письмом (fallback EMAIL из B04 здесь не действует).
10. Сбой одному получателю не отменяет остальных.

### 6.3 Месенджер
11. Только ACTIVE-привязки этого ADMIN. Флаг «на номере есть Telegram» без identity не является адресом.
12. Адаптер канала выключен — SKIP `freight_request_notify_messenger_channel_disabled`.
13. Нет ни одной ACTIVE-привязки — SKIP `freight_request_notify_messenger_skipped_no_identity`.
14. WhatsApp: свободный текст не слать. Пока нет одобренного template `admin_freight_request_created` — SKIP `freight_request_notify_whatsapp_template_missing`.
15. `eventType` в журнале: `FREIGHT_REQUEST_CREATED`.
16. Сбой send не откатывает заявку. Запись журнала: `SENT` или `FAILED` с усечённой ошибкой провайдера, без токена бота.

### 6.4 Текст
Обязательные поля, без HTML:

| Поле | В сообщении |
|------|-------------|
| Факт: новый запрос на расчёт фрахта | да |
| Id заявки | да |
| Email автора | да |
| Ссылка `{angularAppBaseUrl}/admin/route-requests?requestId={id}` | да |

17. Не включать: пароль, токены, polyline, суммы калькулятора, комментарий заявки, телефон автора.
18. Три текста: `ua`, `en`, `ru`. Выбор — `bot_identities.locale`. Пустой или неизвестный locale → `ua`.
19. Пример смысла (`ua`):

```
GeoSun TMS: новий запит на розрахунок фрахту.
Id: 1042
Email: user@example.com
Черга: https://app.example/admin/route-requests?requestId=1042
```

### 6.5 Надёжность
20. Ключ: `FREIGHT_REQUEST_CREATED|{requestId}|{adminUserId}|{TELEGRAM|WHATSAPP|VIBER}`. Повтор того же ключа не создаёт второе сообщение.
21. Ключ писать в `bot_message_log.idempotency_key` (UNIQUE), как у `USER_REGISTERED`.
22. Рассылка не блокирует HTTP создания. p95 ответа создания не растёт на время вызова провайдера. Хук — async после commit.
23. Отдельный outbox для этого события не вводить.

### 6.6 Безопасность
24. В логах не писать bot token. Email автора в логе допустим усечённым preview, как у соседнего admin-notify (ориентир ≤ 80 символов preview текста).
25. Ссылка ведёт на уже закрытый ролью маршрут `/admin/route-requests`. Без входа аноним видит логин, не карточку заявки.
26. Отдельный rate limit алертов не добавлять. Защита создания заявки остаётся как в B07.

### 6.7 Frontend
27. Нового экрана нет.
28. Если в адресе есть `requestId` и это целое число > 0: после входа на `/admin/route-requests` открыть карточку этой заявки (существующий admin read по id). `404` или нечисло — очередь как сейчас, snack об ошибке загрузки карточки, страница жива.
29. Подсказка `pages.profile.adminNotifyChannelsHint` (только роль ADMIN): упомянуть и регистрацию, и новый запрос на расчёт. Валидацию каналов не менять.

## 7) Non-functional Requirements / Нефункциональные требования
- **Security / Безопасность:** в тексте нет секретов; ссылка под существующим RBAC; токен бота в логи не попадает.
- **Performance / Производительность:** afterCommit + async; выборка ADMIN одним запросом; профили и identity — batch, без N+1 по админам.
- **Reliability / Надежность:** best-effort; падение одного канала не отменяет другой канал того же ADMIN и не отменяет заявку; `enabled=false` у флага или адаптера = SKIP, не 500 клиенту.
- **Logging/Monitoring / Логирование и мониторинг:** `freight_request_notify_disabled`, `freight_request_notify_queued`, `freight_request_notify_messenger_queued`, `freight_request_notify_messenger_skipped_no_identity`, `freight_request_notify_messenger_channel_disabled`, `freight_request_notify_whatsapp_template_missing`, `freight_request_notify_messenger_failed`. Не смешивать префикс с `admin_notify_*` регистрации.
- **Accessibility/UX / Доступность и UX:** очередь остаётся Angular Material; подсказка профиля — i18n ua/en/ru; handset без отдельной вёрстки.

## 8) Data Contracts and API / Контракты данных и API

### 8.1 Input Data / Входные данные
- Внешнего API нет. Вход хука после commit: id заявки, id автора, email автора.
- Конфиг:

| Ключ | Default | Смысл |
|------|---------|--------|
| `app.notifications.freight-request-created.enabled` | `true` | мастер-выключатель **этого** события |
| chatbot `enabled` по каналу | как сейчас | транспорт |

Флаг `app.notifications.user-registered.enabled` на это событие не влияет.

### 8.2 Output Data / Выходные данные
- `POST /api/v1/route-requests` **без изменений** контракта и кода ответа.
- Ошибки хука клиенту не возвращаются.

### 8.3 Endpoints (if any) / Эндпоинты (если есть)
- Новых endpoint-ов нет.
- Ссылка в сообщении открывает уже существующий экран `/admin/route-requests`. Карточка читается уже существующим admin read заявки по id.

### 8.4 Ошибки / SKIP (не HTTP клиенту)

| Код в логе | Когда |
|------------|--------|
| `freight_request_notify_disabled` | мастер-флаг false |
| `freight_request_notify_queued` | рассылка начата; в тексте число получателей-ADMIN после фильтра роли (до каналов) |
| `freight_request_notify_messenger_skipped_no_identity` | `MESSENGERS` включён, ACTIVE нет |
| `freight_request_notify_messenger_channel_disabled` | привязка есть, адаптер выключен |
| `freight_request_notify_whatsapp_template_missing` | WhatsApp без template |
| `freight_request_notify_messenger_failed` | исключение провайдера |
| `freight_request_notify_messenger_queued` | сообщение ушло (`SENT`) |

## 9) UX/UI Requirements (frontend) / UX/UI требования (frontend)
- States: очередь — `loading` / `empty` / `error` как сейчас. Дополнительно: открытие `requestId` из адреса не заменяет эти состояния списком-заглушкой при ошибке карточки.
- Form behavior: новых полей нет.
- Navigation: ссылка из месенджера → логин при необходимости → `/admin/route-requests?requestId={id}` → карточка заявки. Роли страницы не сужать и не расширять.
- UI texts: правка только `pages.profile.adminNotifyChannelsHint` в `uk.json` / `en.json` / `ru.json`.

## 10) Architecture Changes / Изменения в архитектуре
- **Components/services / Компоненты/сервисы:**
  - Заявка — core. Правило «когда слать» — в application-сценарии создания, **после commit**, не внутри агрегата и не в контроллере.
  - Пакет маршрутов не импортирует JPA-сущности пользователя и `bot_identities`. Получатели и отправка — за портом (notifier), реализация порта использует уже существующий outbound chatbot.
  - Не копировать адаптеры Telegram/WhatsApp/Viber. Не второй движок бота.
  - `USER_REGISTERED` остаётся в своём notifier. Общий кусок — транспорт и журнал, не текст и не выборка каналов EMAIL/SMS.
- **Data storage / Хранилище данных:** без новой таблицы. Журнал — `bot_message_log`.
- **Integrations / Интеграции:** те же адаптеры, что у chatbot. WhatsApp для этого события — SKIP до template.
- **Compatibility / Совместимость:** контракт создания заявки тот же. Флаг `false` возвращает сегодняшнее молчание.

## 11) Implementation Constraints / Ограничения реализации
- Use existing stack and project conventions. / Использовать существующий стек и соглашения проекта.
- Do not add dependencies without rationale. / Не добавлять SDK месенджеров.
- Do not change unrelated modules. / Не менять FSM заявки, quote, регистрацию учётки, Flutter.
- Preserve backward compatibility where required. / `POST /api/v1/route-requests` без смены тела ответа.
- Комментарі в коді — українською, лише для неочевидного.
- DDD: core-сценарий маршрутов не тянет чужие entity; уведомление — порт + transaction script получателей.

## 12) Implementation Plan / План реализации
Детальный план файлов — отдельный документ в `docs/plans/` после «+» на эту спеку. Фазы:

1. Хук afterCommit на успешное создание заявки + флаг + выборка ADMIN без автора и без EMAIL-fallback.
2. Исходящее `FREIGHT_REQUEST_CREATED` через существующий outbound, тексты `ua`/`en`/`ru`, идемпотентный ключ, SKIP WhatsApp.
3. Query `requestId` на `/admin/route-requests` и правка hint профиля.
4. Тесты (unit матрицы получателей + integration create → stub send). Problems = 0. Обновить Статус, реестр, DoD; в BASELINE — строка бэклога становится границей канона только после сдачи.

## 13) Acceptance Criteria (Definition of Done) / Критерии приемки
- [x] После успешного создания запроса каждый активный не удалённый ADMIN с доступной ролью `ADMIN`, каналом `MESSENGERS` и ACTIVE-привязкой включённого адаптера получает одно сообщение с id, email автора и ссылкой `.../admin/route-requests?requestId={id}`.
- [x] Автор запроса это сообщение не получает.
- [x] `MANAGER` без роли `ADMIN` не получает.
- [x] ADMIN без флага `MESSENGERS` или без ACTIVE-привязки не получает письмо-замену; в логе SKIP; создание заявки успешно.
- [x] Повтор обработки того же ключа не шлёт второе сообщение.
- [x] Вторая заявка на тот же маршрут шлёт новое сообщение.
- [x] Сбой провайдера не меняет успешный ответ создания.
- [x] `app.notifications.freight-request-created.enabled=false` — сообщений нет, создание как сейчас. Флаг регистрации на это не влияет.
- [x] WhatsApp без template — SKIP, не свободный текст.
- [x] Ссылка с валидным `requestId` открывает карточку; битый id не ломает очередь.
- [x] Hint профиля ADMIN упоминает это событие; другим ролям hint не показывается.
- [x] В сообщении нет polyline, сумм, комментария, паролей и токенов.
- [x] Documentation updated / Документация обновлена (этот файл, README, BASELINE при сдаче).
- [x] Tests added/updated and passing / Тесты добавлены или обновлены и проходят.

## 14) Test Plan / Тест-план
- **Unit:** матрица получателей (флаг месенджеров выключен, нет профиля, нет identity, inactive, deleted, manager без ADMIN, автор-ADMIN исключён, два ADMIN — оба, сбой одного не гасит второго).
- **Unit:** ключ идемпотентности; locale `en` / пустой → `ua`; флаг события false не смотрит на флаг регистрации.
- **Integration:** создание заявки при stub-адаптере Telegram — одно исходящее на подходящего ADMIN; автор молчит; ответ создания успешный при исключении send; вторая заявка — второе сообщение; повтор ключа — без дубля.
- **Frontend:** `/admin/route-requests?requestId=` открывает карточку; нечисло и 404 оставляют очередь рабочей.
- **Edge cases / Граничные случаи:** ноль ADMIN; WhatsApp-only identity → SKIP; адаптер Telegram выключен → SKIP; несколько заявок на один маршрут.

## 15) Risks and Assumptions / Риски и допущения
- **Risks / Риски:** админ не привязал бота — сообщение некуда слать, это штатный SKIP, не баг доставки. Очередь без deep-link сейчас; ошибка в query не должна ломать список. Общий outbound сейчас заточен под `USER_REGISTERED` — расширение не должно менять тексты и ключи регистрации.
- **Assumptions / Допущения:** «запрос на расчёт фрахта» = создание `RouteRequest` пользователем. Канал только месенджеры, получатель только ADMIN, как в brief. Template WhatsApp в этой итерации не появится.
- **Rollback plan / План отката:** `app.notifications.freight-request-created.enabled=false`. Код создания заявки при этом не откатывается.

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
3. Follow current architecture and project style. / Следуй текущей архитектуре и стилю проекта. Core маршрутов — порт наружу, без импорта чужих JPA entity.
4. Show a brief plan before code changes. / Перед кодом нужен принятый план (`docs/plans/`), не этот §12.
5. After changes provide:
   - changed files list / список измененных файлов;
   - what changed and why / что и зачем изменено;
   - verification steps (commands + expected result) / как проверить;
   - risks and known gaps / риски и непокрытые случаи.
6. Do not add dependencies without explicit justification. / Не добавляй SDK месенджеров.
7. Add short comments only for non-obvious logic. / Коментарі українською, лише для неочевидного.
