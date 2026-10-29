# Реестр спецификаций

**Дата создания:** 15 сентября 2026, 00:00 (UTC+3)  
**Дата изменения:** 29 октября 2026, 14:12 (UTC+3)

Единая точка входа перед работой по ТЗ. Сами требования — в файлах ниже; этот файл отвечает только на вопросы «сделано ли», «это источник истины или дополнение», «что ещё открыто».

**Baseline (канон vs бэклог):** [`BASELINE.ru.md`](BASELINE.ru.md) — читать перед новой фичей или доработкой.  
**Процесс:** [`docs/dev-workflow.ru.md`](../dev-workflow.ru.md) — алгоритм **A** (новое) / **B** (доработка).  
**Архитектура:** [`docs/architecture/ddd-pragmatic.ru.md`](../architecture/ddd-pragmatic.ru.md) — pragmatic DDD для core.  
**Архив:** [`docs/archive/README.ru.md`](../archive/README.ru.md) — не источник истины.

Клиент реализации: **Angular + Java**. `frontend-flutter/` в ТЗ не входит (заморозка).

Канонические имена файлов — с языковым суффиксом (`.ru.md`, `.ru.en.md`, `.ua.md`).

## Как читать

| Поле | Значения |
|------|----------|
| **Реализация** | `реализовано` — в проде, не открывать как новую фичу. `частично` — часть scope в коде, хвост в «Остаток» / бэклоге BASELINE. `не реализовано` — только спека (бэклог). `в силе` — контракт/правила, не отдельная фича. |
| **Роль** | `источник истины` — при расхождении править код под этот файл (или сначала обновить файл). `дополнение` — формулы, seed, канон правил. `исторический MVP` — в архиве; актуальный контур — в связанном источнике истины. |

Перед кодом: **BASELINE** или этот реестр → нужный файл → «Связанные». Не читать все спеки подряд.

---

## Канон (зафиксировано)

Готово в продукте или контракт «в силе». Изменения — алгоритм **B**. Детали границ — в [`BASELINE.ru.md`](BASELINE.ru.md).

### Auth и пользователи

| Файл | Реализация | Роль | Остаток в каноне |
|------|------------|------|------------------|
| [auth-authentication-authorization.ru.en.md](auth-authentication-authorization.ru.en.md) | реализовано | источник истины (B01) | нет |
| [admin-user-management.ru.en.md](admin-user-management.ru.en.md) | реализовано | источник истины (B02) | MANAGER **читает**; мутации — только ADMIN |
| [user-profile.ru.en.md](user-profile.ru.en.md) | реализовано | источник истины (B03) | нет |
| [admin-notify-new-user-registration.ru.en.md](admin-notify-new-user-registration.ru.en.md) | реализовано | источник истины (B04) | в каноне: SKIP SMS/WhatsApp template; расширение — бэклог |
| [admin-notify-new-freight-request.ru.en.md](admin-notify-new-freight-request.ru.en.md) | реализовано | источник истины (B20) | WhatsApp template SKIP; EMAIL/SMS по событию — вне scope |
| [user-multi-role-switching.ru.en.md](user-multi-role-switching.ru.en.md) | реализовано | источник истины (B18) | нет; синхронизация B01/B02 |
| [default-admin-bootstrap.ru.en.md](default-admin-bootstrap.ru.en.md) | реализовано | источник истины (B19) | первичный ADMIN из `ADMIN_EMAIL` / `ADMIN_PASSWORD`; дополняет B01 |
| [multi-enterprise.ru.en.md](multi-enterprise.ru.en.md) | реализовано | источник истины (B21) | граница предприятия; вход с кодом; оператор платформы. Остаток закрыт: V46–V49 на рабочей MySQL, `enterprise_id` NOT NULL |

### Маршруты и фрахт

| Файл | Реализация | Роль | Остаток в каноне |
|------|------------|------|------------------|
| [routes-server-workflow-and-freight-quoting.ru.en.md](routes-server-workflow-and-freight-quoting.ru.en.md) | реализовано | источник истины (B05/B07) | lock/view/breakdown — immutability; ставка — NBU |
| [route-immutability-list-filters-deferred-country-breakdown.ru.md](route-immutability-list-filters-deferred-country-breakdown.ru.md) | реализовано | источник истины (B05) | §7.1 вариант A; несколько заявок на маршрут **разрешены** |
| [route-point-operations-rules.ru.md](route-point-operations-rules.ru.md) | в силе | источник истины (B06) | UI и backend менять вместе |
| [freight-cost-scenario-nbu-pricing.ru.md](freight-cost-scenario-nbu-pricing.ru.md) | реализовано (v1) | источник истины (B08) | опциональные хвосты — бэклог; v1 не блокируют |
| [freight-trip-cost-calculation-rules-margin30-ua8150-driverpct.ru.md](freight-trip-cost-calculation-rules-margin30-ua8150-driverpct.ru.md) | в силе | дополнение (B09): формулы | при расхождении чисел с NBU-ТЗ приоритет у **этого** файла |
| [currencies-reference.ru.md](currencies-reference.ru.md) | реализовано | источник истины (B10) | выбор валюты в котировке и cron — вне v1 |

### Справочники, файлы, рейсы

| Файл | Реализация | Роль | Остаток в каноне |
|------|------------|------|------------------|
| [file-storage.ru.md](file-storage.ru.md) | реализовано | источник истины (B11) | CDN/virus scan — вне v1 |
| [vehicles-reference.ru.md](vehicles-reference.ru.md) | реализовано | источник истины (B12) | FK document-types — бэклог v2 |
| [drivers-and-vehicle-combinations.ru.en.md](drivers-and-vehicle-combinations.ru.en.md) | реализовано | источник истины (B13) | виды документов — enum, не FK |
| [trips-and-driver-expense-reports.ru.en.md](trips-and-driver-expense-reports.ru.en.md) | реализовано | источник истины (B14) | сверка с FreightCostCalculation — бэклог |

### Виды документов (канон = CRUD + UA seed)

| Файл | Реализация | Роль | Остаток в каноне |
|------|------------|------|------------------|
| [document-types-reference.ru.md](document-types-reference.ru.md) | частично → **канон: CRUD** | источник истины (B15) | интеграция с ТС/водителями — **бэклог**; UUID `c1000000-…` устарели |
| [document-types-ua-default-catalog.ua.md](document-types-ua-default-catalog.ua.md) | реализовано | источник истины (B15) | seed V39, UUID `c2000000-…` |

### Chatbot v1 (канон)

| Файл | Реализация | Роль | Канон / не канон |
|------|------------|------|------------------|
| [chatbots-telegram-whatsapp-viber.ru.en.md](chatbots-telegram-whatsapp-viber.ru.en.md) | частично | источник истины | **Канон (B16):** Telegram verify + телефон, webhook, admin status/identities, USER_REGISTERED. **Бэклог:** Angular UI `/profile`+admin; уведомления quote/trip; status/lang; WhatsApp; Viber; полный admin journal |

---

## Бэклог / не в freeze

| Файл | Реализация | Роль | Остаток |
|------|------------|------|---------|
| [document-ocr.ru.md](document-ocr.ru.md) | не реализовано | источник истины по будущему OCR | после document-types v2 |
| Document-types v2 | — | см. BASELINE | FK в карточках ТС/водителей |
| Chatbot post-v1 | — | см. остаток chatbots-… | UI, WhatsApp, Viber, journal, quote/trip notify |
| SMS / WhatsApp template | — | см. B04 | реальный провайдер |
| Country reference (B17) | код без отдельной спеки | дыра | завести спеку при доработке |

---

## Архив (не читать как канон)

| Было | Куда | Примечание |
|------|------|------------|
| TECHNICAL_SPECIFICATION_API_SERVER_v1.0 | [`docs/archive/`](../archive/) | исторический MVP auth; новые правила — только B01 |
| auth-mvp-runbook, auth-validation-checklist | [`docs/archive/`](../archive/) | GAS/MVP-процедуры; не канон |
| release-notes-routes-quotes-rollout | [`docs/archive/`](../archive/) | одноразовый rollout |

---

## Как обновлять этот реестр

После сдачи по алгоритму A или B:

1. Обновить блок **Статус** в спеке.
2. Синхронизировать строку здесь и при сдвиге границы — [`BASELINE.ru.md`](BASELINE.ru.md).
3. Проставить `[x]` в Definition of Done.
4. Не оставлять пустой DoD у уже живущей фичи.
