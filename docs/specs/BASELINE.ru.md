# Baseline канона (зафиксированное)

**Дата создания:** 17 сентября 2026, 13:58 (UTC+3)  
**Дата изменения:** 29 октября 2026, 13:30 (UTC+3)  
**Дата фиксации:** 17 сентября 2026  
**Назначение:** согласованный список того, что уже в продукте (Angular + Java) и где единственный источник истины.  
**Процесс после freeze:** [`docs/dev-workflow.ru.md`](../dev-workflow.ru.md) — новое через алгоритм **A**, изменение канона через **B**.  
**Архитектура:** [`docs/architecture/ddd-pragmatic.ru.md`](../architecture/ddd-pragmatic.ru.md) — pragmatic DDD в силе для нового/затрагиваемого кода в core.  
**Глоссарий:** [`docs/architecture/glossary.ru.en.ua.md`](../architecture/glossary.ru.en.ua.md) — единый язык (ua / ru / en).  
**Реестр статусов:** [`README.ru.md`](README.ru.md).

После явного «+» владельца по этому файлу:

- менять поведение кода только через A или B;
- правки текста baseline без смены кода — только если описание «как есть» ошибочно (алгоритм B, без реализации, если код уже совпадает);
- бэклог ниже — **не** часть freeze; открывать отдельной задачей (A);
- код в core (B05–B09, B14 и связанный freight) — по ddd-pragmatic; Big Bang-рефакторинг не начинать без задачи B.

Канонические имена файлов — с языковым суффиксом (`.ru.md`, `.ru.en.md`, `.ua.md`). Дубликаты без суффикса не считать источником истины.

---

## Канон (зафиксировано)

| ID | Тема | Файл | Роль | Граница «что зафиксировано» |
|----|------|------|------|------------------------------|
| B01 | Identity & Access | [auth-authentication-authorization.ru.en.md](auth-authentication-authorization.ru.en.md) | источник истины | login, JWT, refresh, RBAC, Angular auth-слой |
| B02 | Admin users | [admin-user-management.ru.en.md](admin-user-management.ru.en.md) | источник истины | `/admin/users`; MANAGER **читает**; мутации — только ADMIN |
| B03 | User profile | [user-profile.ru.en.md](user-profile.ru.en.md) | источник истины | self/admin профиль, телефоны, каналы |
| B04 | Notify: new registration | [admin-notify-new-user-registration.ru.en.md](admin-notify-new-user-registration.ru.en.md) | источник истины | after register → ADMIN (EMAIL/PHONE/MESSENGERS); SMS=SKIP как есть |
| B18 | Multi-role & switching | [user-multi-role-switching.ru.en.md](user-multi-role-switching.ru.en.md) | источник истины | available roles + active role; admin assign; self switch; регистрация → USER |
| B19 | Default ADMIN bootstrap | [default-admin-bootstrap.ru.en.md](default-admin-bootstrap.ru.en.md) | источник истины | первичный ADMIN из env при пустой БД; не заменяет `SUPER_ADMIN_PASSWORD` |
| B05 | Route planning (CRUD + lock) | [routes-server-workflow-and-freight-quoting.ru.en.md](routes-server-workflow-and-freight-quoting.ru.en.md) + [route-immutability-list-filters-deferred-country-breakdown.ru.md](route-immutability-list-filters-deferred-country-breakdown.ru.md) | источник истины | маршруты, soft-delete, duplicate/restore; lock = вариант A; несколько заявок на route **разрешены**; детали lock/`view`/breakdown — во втором файле |
| B06 | Route point operations | [route-point-operations-rules.ru.md](route-point-operations-rules.ru.md) | источник истины (`в силе`) | whitelist/FSM операций точек |
| B07 | Freight request & quoting | [routes-server-workflow-and-freight-quoting.ru.en.md](routes-server-workflow-and-freight-quoting.ru.en.md) | источник истины | заявка, очередь admin, draft/send quote, idempotency |
| B08 | Freight cost & NBU | [freight-cost-scenario-nbu-pricing.ru.md](freight-cost-scenario-nbu-pricing.ru.md) | источник истины | сценарии, toll, cost-preview, quote из расчёта (v1) |
| B09 | Freight formulas | [freight-trip-cost-calculation-rules-margin30-ua8150-driverpct.ru.md](freight-trip-cost-calculation-rules-margin30-ua8150-driverpct.ru.md) | дополнение | формулы калькулятора; при расхождении чисел с B08 приоритет у B09 |
| B10 | Currencies | [currencies-reference.ru.md](currencies-reference.ru.md) | источник истины | валюты + курсы НБУ |
| B11 | File storage | [file-storage.ru.md](file-storage.ru.md) | источник истины | `StoredFile`, disk/S3 |
| B12 | Fleet: vehicles | [vehicles-reference.ru.md](vehicles-reference.ru.md) | источник истины | справочник ТС и документы **как сейчас** (без FK document-types v2) |
| B13 | Fleet: drivers & combinations | [drivers-and-vehicle-combinations.ru.en.md](drivers-and-vehicle-combinations.ru.en.md) | источник истины | водители, автопоезда; виды документов — enum |
| B14 | Trips & expenses | [trips-and-driver-expense-reports.ru.en.md](trips-and-driver-expense-reports.ru.en.md) | источник истины | рейсы, статусы, expense report |
| B15 | Document types catalog | [document-types-reference.ru.md](document-types-reference.ru.md) + [document-types-ua-default-catalog.ua.md](document-types-ua-default-catalog.ua.md) | источник истины | CRUD видов + UA-seed V39 UUID `c2000000-…` (**не** `c1000000-…`); без интеграции с карточками ТС/водителей |
| B16 | Chatbot v1 (Telegram verify) | [chatbots-telegram-whatsapp-viber.ru.en.md](chatbots-telegram-whatsapp-viber.ru.en.md) | источник истины (срез v1) | link-code, webhook, phone_verified, admin status/identities, USER_REGISTERED; остальное спеки — бэклог |
| B17 | Country reference | код + куски B05/B08 | карточка-дыра | отдельной спеки нет; справочник стран для breakdown живёт в коде — при доработке завести спеку (A) или дописать канон (B) |
| B20 | Notify: new freight request | [admin-notify-new-freight-request.ru.en.md](admin-notify-new-freight-request.ru.en.md) | источник истины | after create RouteRequest → ADMIN з MESSENGERS; WhatsApp template SKIP |
| B21 | Несколько предприятий | [multi-enterprise.ru.en.md](multi-enterprise.ru.en.md) | источник истины | одно приложение, много предприятий; вход сотрудника — код + email + пароль; оператор платформы вне ролей; операционные данные изолированы; страны, валюты и курсы НБУ общие |

**Исторический MVP (не канон):** [`docs/archive/TECHNICAL_SPECIFICATION_API_SERVER_v1.0.ru.md`](../archive/TECHNICAL_SPECIFICATION_API_SERVER_v1.0.ru.md); актуальный auth — только B01.

---

## Архитектура (зафиксировано)

| Тема | Документ | С |
|------|----------|---|
| Pragmatic DDD | [`docs/architecture/ddd-pragmatic.ru.md`](../architecture/ddd-pragmatic.ru.md) | 17 сентября 2026 |
| Glossary (ua/ru/en) | [`docs/architecture/glossary.ru.en.ua.md`](../architecture/glossary.ru.en.ua.md) | 17 сентября 2026 |

Новый и затрагиваемый код в **core** (Route Planning, Freight Commercial, Trip Operations) — по ADR. Остальное — transaction script, пока нет отдельной задачи B.

---

## Бэклог (вне freeze)

| Тема | Откуда | Алгоритм старта |
|------|--------|-----------------|
| Document-types v2 (FK в ТС/водителях) | остаток document-types-reference | A или B к B12/B13/B15 |
| OCR / suggest | [document-ocr.ru.md](document-ocr.ru.md) | A |
| Chatbot: Angular UI, quote/trip notify, WhatsApp, Viber, journal | остаток chatbots-… | A/B к B16 |
| Реальный SMS / WhatsApp template | остаток B04 | B |
| Опциональные хвосты NBU (PATCH, nameUk, эталонный unit-тест) | остаток B08 | B |
| Сверка trip expenses ↔ FreightCostCalculation | вне scope B14 | A/B по решению |
| Отдельная спека Country reference | B17 | A |

---

## Как обновлять этот файл

1. Сдача по A/B → синхрон со спекой и [`README.ru.md`](README.ru.md).
2. Закрыли кусок бэклога или сдвинули границу канона — правка строки здесь.
3. Не переносить живые `реализовано`/`в силе`/`частично` в archive без решения владельца.
