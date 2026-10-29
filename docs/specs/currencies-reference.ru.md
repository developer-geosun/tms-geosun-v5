# Справочник валют и курсы НБУ

## Статус
- **Реализация:** реализовано (v1)
- **Роль:** источник истины
- **Клиент:** Angular + Java
- **Остаток:** выбор валюты в котировке, сторонние курсы, cron — вне v1. Запись валют и синхронизация НБУ — оператор платформы ([multi-enterprise.ru.en.md](multi-enterprise.ru.en.md), B21); сотрудник читает.
- **Реестр:** [README.ru.md](README.ru.md)

## Цель
- Единый справочник валют по перечню НБУ (без металлов и СПЗ).
- Флаг `isActive` — какие валюты используются в программе.
- Синхронизация официальных курсов НБУ по активным валютам с сохранением в БД.
- Дата курса = `exchangedate` из ответа НБУ (последний рабочий день с полным набором курсов).

## Вне области (v1)
- Выбор валюты в котировке (MatSelect).
- Курсы ПриватБанк / Monobank / Львів.
- Курсы на произвольную `calculationDate` в UI.
- Cron-автообновление.

## Данные

### `currencies`
| Поле | Описание |
|------|----------|
| `code` | ISO 4217 (PK) |
| `numeric_code` | r030 НБУ |
| `name_uk`, `name_en`, `name_ru` | Названия (`name_ru` добавлено в `V38`) |
| `nbu_units` | Единиц валюты для курса НБУ |
| `minor_units` | Знаков после запятой (справочно) |
| `is_active` | Использовать в программе |
| `display_order` | Сортировка в UI |

Seed: Flyway `V16`, ~40 валют + `UAH`. Активны по умолчанию: UAH, USD, EUR, PLN, GBP, CHF.

### `currency_nbu_rates`
| Поле | Описание |
|------|----------|
| `currency_code`, `rate_date` | PK |
| `rate` | UAH за `nbu_units` |
| `rate_per_unit` | UAH за 1 единицу |
| `special` | Y/N для USD (НБУ с 2026) |
| `fetched_at` | Время sync в TMS |

## Алгоритм sync НБУ
1. Взять активные валюты из `currencies`.
2. От «сегодня» (Europe/Kyiv) идти назад до 14 дней, пропуская субботу/воскресенье.
3. `GET {base}/exchange?date=YYYYMMDD&json`
4. Успех: непустой ответ, все активные (кроме UAH) присутствуют, одинаковый `exchangedate`.
5. Upsert в `currency_nbu_rates`; UAH: `rate_per_unit = 1`.

Кросс-курс (будущие расчёты): `A/B = ratePerUnit_A / ratePerUnit_B`.

## API (ADMIN, MANAGER)

| Метод | Путь |
|-------|------|
| GET | `/api/v1/admin/currencies?activeOnly=` |
| PATCH | `/api/v1/admin/currencies/{code}` — `{ isActive, displayOrder? }` |
| POST | `/api/v1/admin/currencies/nbu-rates/sync` |
| GET | `/api/v1/admin/currencies/nbu-rates` |

## Frontend
- `/admin/currencies` — таблица валют, slide-toggle `isActive`, кнопка «Оновити курси НБУ».
- Колонка «Название» — локализованная (`currencyLocalizedName`): `uk` → `nameUk`, `en` → `nameEn`, `ru` → `nameRu`, при пустом переводе fallback на `nameUk`.

## Конфигурация
`application.yml`:
```yaml
app:
  nbu:
    base-url: https://bank.gov.ua/NBUStatService/v1/statdirectory
    timeout-millis: 10000
    max-lookback-days: 14
```
