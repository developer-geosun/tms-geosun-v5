# Technical Specification / Техническое задание: Default ADMIN bootstrap

**Дата создания:** 1 октября 2026, 21:25 (UTC+3)  
**Дата изменения:** 29 октября 2026, 13:30 (UTC+3)

## Статус
- **Реализация:** реализовано
- **Роль:** источник истины по первичному ADMIN из окружения
- **Клиент:** Angular + Java (только backend)
- **Baseline:** [BASELINE.ru.md](BASELINE.ru.md) **B19**; дополняет B01 / B02
- **Остаток:** нет
- **Реестр:** [README.ru.md](README.ru.md)
- **Brief:** [default-admin-bootstrap.ru.md](../briefs/default-admin-bootstrap.ru.md)

## Language Rules / Правила языка
- **Primary language / Основной язык:** RU
- **Secondary language / Дополнительный язык:** EN
- **Terms in English:** `bootstrap`, `ApplicationRunner`, `available roles`, `Definition of Done`

## 1) Goal / Цель
- **Problem:** после чистой БД нет ADMIN; первый вход требует ручных SQL.
- **Value:** при старте backend создаётся учётка ADMIN из `.env`, если в БД ещё нет пользователя с `ADMIN` в available roles.
- **Outcome:** оператор входит по `ADMIN_EMAIL` / `ADMIN_PASSWORD` без seed-SQL.

## 2) Context
- **Module:** `backend-java` auth (Identity, supporting).
- **Related:** B01 auth, B02 admin users, B18 multi-role, [B21](multi-enterprise.ru.en.md) (ADMIN создаётся у первого предприятия, если у него ещё нет ADMIN); `SUPER_ADMIN_PASSWORD` — step-up, не логин.
- **Not:** Flutter, UI «create first admin», sync пароля при каждом рестарте.

## 3) Scope (In)
1. Env: `ADMIN_EMAIL`, `ADMIN_PASSWORD` → `app.bootstrap.admin.email` / `password` в `application.yml`.
2. На старте приложения (`ApplicationRunner`): если `countNonDeletedWithAvailableRole(ADMIN) == 0` и env заданы — создать пользователя:
   - email нормализован (`EmailNormalizer`);
   - `availableRoles = {ADMIN}`, `active role = ADMIN`;
   - `emailVerified = true`, `emailVerifiedAt = now`, `active = true`;
   - пароль — BCrypt-хеш (те же правила сложности, что `RegisterRequest`: ≥8, буква + цифра).
3. Если ADMIN уже есть (любой не удалённый с `ADMIN` в available) — **skip**, без изменения паролей.
4. Пустой email или password — **warn**, старт не прерывается.
5. Невалидный email / слабый password — **warn**, skip.
6. Email уже занят другим пользователем — **warn**, skip (без апгрейда роли).
7. Документация: `.env.example`, `RUN.ru.md`, docker-compose passthrough.

## 4) Out of Scope
- Несколько seed-пользователей из env.
- Перезапись пароля существующего ADMIN.
- Flyway SQL с plaintext-паролем.
- Angular-изменения.

## 5) Non-functional
- **Security:** пароль только в env; в логах не писать password.
- **Reliability:** идempotent skip при повторном старте; гонка двух инстансов — допустимо полагаться на unique email + повторную проверку count.
- **Logging:** INFO при успешном создании (без секретов); WARN при skip с причиной.

## 6) Definition of Done
- [x] `DefaultAdminBootstrapService` + `ApplicationRunner`
- [x] `@ConfigurationProperties` + `application.yml` + `.env.example` + docker-compose
- [x] Repository: `countNonDeletedWithAvailableRole`
- [x] Unit + integration tests
- [x] README / BASELINE B19; ссылка из B01 (см. реестр)
- [x] Spotless; Problems = 0 по изменённым Java

## 7) Acceptance
1. Пустая БД + заданные env → один ADMIN, login OK.
2. Повторный старт → тот же пользователь, пароль не меняется.
3. Уже есть ADMIN → новый не создаётся.
4. Пустые env → backend стартует, в логе warning.
