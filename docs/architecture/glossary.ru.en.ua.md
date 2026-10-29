# Ubiquitous language / Глосарій / Глоссарий

**Дата создания:** 17 сентября 2026, 14:43 (UTC+3)  
**Дата изменения:** 29 октября 2026, 13:30 (UTC+3)

Единый язык домена TMS GeoSun. Колонка **Term** — канон в коде и API (английский).  
При споре о правилах побеждает спека из [`BASELINE.ru.md`](../specs/BASELINE.ru.md), не этот файл.

Связанные: [`ddd-pragmatic.ru.md`](ddd-pragmatic.ru.md), [`docs/dev-workflow.ru.md`](../dev-workflow.ru.md).

## Термины

| Term | Українська | Русский | English |
|------|------------|---------|---------|
| Enterprise | підприємство: межа даних в одному застосунку (код входу, свої люди й операції) | предприятие: граница данных в одном приложении (код входа, свои люди и операции) | enterprise: data boundary in one app (login code, own people and operations) |
| Platform operator | оператор платформи: обліковка поза підприємством, створює і призупиняє підприємства | оператор платформы: учётка вне предприятия, создаёт и приостанавливает предприятия | platform operator: account outside enterprises, creates and suspends enterprises |
| User | обліковий запис системи (email, роль) всередині одного підприємства | учётка системы (email, роль) внутри одного предприятия | system account (email, role) inside exactly one enterprise |
| UserProfile | контактна картка обліковки (ПІБ, телефони, канали) | контактная карточка учётки (ФИО, телефоны, каналы) | account contact card (name, phones, channels) |
| Role | роль RBAC: `admin`, `manager`, `driver`, `user` | роль RBAC: `admin`, `manager`, `driver`, `user` | RBAC role: `admin`, `manager`, `driver`, `user` |
| Route | збережений знімок маршруту (точки, polyline), **не** рейс | сохранённый снимок маршрута (точки, polyline), **не** рейс | saved route snapshot (points, polyline), **not** a trip |
| RoutePoint | точка маршруту (порядок, тип, операції) | точка маршрута (порядок, тип, операции) | route point (order, kind, operations) |
| RouteRequest | заявка на фрахт за збереженим маршрутом | заявка на фрахт по сохранённому маршруту | freight request for a saved route |
| FreightQuote | комерційна пропозиція (офер) за заявкою | коммерческое предложение (оффер) по заявке | commercial quote/offer for a request |
| FreightNumericScenario | числовий сценарій собівартості (параметри розрахунку) | числовой сценарий себестоимости (параметры расчёта) | numeric cost scenario (calculation parameters) |
| FreightCostCalculation | результат серверного розрахунку ставки | результат серверного расчёта ставки | server-side freight cost calculation result |
| CountryBreakdown | пробіг маршруту по країнах (HERE / GeoJSON) | пробег маршрута по странам (HERE / GeoJSON) | distance split by country (HERE / GeoJSON) |
| Route lock | заборона редагувати маршрут після появи заявки | запрет править маршрут после появления заявки | route edit blocked after a freight request exists |
| Driver | кадрова картка водія (**не** те саме, що User) | кадровая карточка водителя (**не** то же, что User) | driver HR card (**not** the same as User) |
| Vehicle | транспортний засіб у довіднику | транспортное средство в справочнике | vehicle in the fleet directory |
| VehicleCombination | автопоїзд (тягач + напівпричіп) | автопоезд (тягач + полуприцеп) | named tractor + trailer combination |
| DocumentType | вид документа в довіднику (UA-каталог) | вид документа в справочнике (UA-каталог) | document type in the catalog (UA seed) |
| StoredFile | метадані збереженого файлу (диск / S3) | метаданные сохранённого файла (диск / S3) | stored file metadata (disk / S3) |
| Trip | операційний рейс (виконання), **не** Route | операционный рейс (исполнение), **не** Route | operational trip (execution), **not** a Route |
| TripExpenseReport | звіт водія по витратах рейсу | отчёт водителя по затратам рейса | driver expense report for a trip |
| BotIdentity | привʼязка месенджера до обліковки | привязка мессенджера к учётке | messenger identity linked to an account |
| BotLinkCode | одноразовий код привʼязки Telegram | одноразовый код привязки Telegram | one-time Telegram link code |
| Currency / NBU rate | валюта та курс НБУ з БД | валюта и курс НБУ из БД | currency and NBU rate from DB |
| CountryReference | довідник країн (коди для breakdown) | справочник стран (коды для breakdown) | country reference (codes for breakdown) |

## Не плутати

| Не казати / Не говорить / Do not say | Замість / Вместо / Use |
|--------------------------------------|-------------------------|
| замовлення, order, booking (для заявки) | RouteRequest |
| трек, trip (для збереженого шляху) | Route |
| маршрут (для рейсу в дорозі) | Trip |
| ставка як окрема «заявка» | FreightQuote |
| водій = User | Driver (+ опційний `userId`) |
| фура, сет | VehicleCombination |

## Оновлення

Новий термін у коді/UI → додати рядок сюди в тій самій задачі A/B. Не дублювати API й DoD зі спек.
