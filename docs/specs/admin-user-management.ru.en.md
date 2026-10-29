# Technical Specification / Техническое задание: Admin User Management

## Статус
- **Реализация:** реализовано
- **Роль:** источник истины по `/admin/users` (мутации учётки)
- **Клиент:** Angular + Java
- **Baseline:** [BASELINE.ru.md](BASELINE.ru.md) **B02**
- **Остаток:** нет. **Канон RBAC:** MANAGER **читает** список и карточку; PUT профиля и roles/active/delete/restore — только ADMIN (`user-profile.ru.en.md`). Multi-role: [`user-multi-role-switching.ru.en.md`](user-multi-role-switching.ru.en.md). Пункт Out of Scope «Доступ MANAGER к user-admin» устарел — не восстанавливать
- **Реестр:** [README.ru.md](README.ru.md)

## Language Rules / Правила языка
- **Primary language / Основной язык:** RU
- **Secondary language / Дополнительный язык:** EN
- **Terms to keep in English / Термины, которые оставляем на английском:** `auth`, `access token`, `refresh token`, `RBAC`, `soft-delete`, `handset`, `Definition of Done`

## 1) Goal / Цель
- **Problem / Проблема:** роли и статус пользователей меняются только вручную в БД; у админа нет UI/API для управления учётными записями.
- **Value / Ценность:** ADMIN может безопасно выдавать роли, отключать и мягко удалять пользователей без доступа к БД.
- **Expected outcome / Ожидаемый результат:** endpoint-ы `/api/v1/admin/users` и страница `/admin/users` (desktop + handset).

## 2) Context / Контекст
- **Project/module / Проект/модуль:** `backend-java` auth + `frontend-angular` admin pages.
- **Current behavior / Текущее поведение:** регистрация всегда ставит `USER`; есть только `DELETE /api/v1/users/{id}` (soft-delete, ADMIN).
- **Related docs / Связанные документы:** `docs/system.ru.md`, `docs/archive/TECHNICAL_SPECIFICATION_API_SERVER_v1.0.ru.md` (исторический MVP), `docs/specs/auth-authentication-authorization.ru.en.md`, `docs/specs/user-profile.ru.en.md`, `docs/specs/admin-notify-new-user-registration.ru.en.md` (ссылка из алерта регистрации ведёт на карточку `/admin/users/{id}`), [`multi-enterprise.ru.en.md`](multi-enterprise.ru.en.md) (B21: список и последний ADMIN считаются внутри предприятия), `docs/specs/BASELINE.ru.md`.
- **Environment constraints / Ограничения окружения:** Java 21 / Spring Boot 3, Angular 21 + Angular Material, единые breakpoints (`LayoutService`, `bp.handset`).

## 3) Scope (In) / Scope (входит в задачу)
- Список пользователей с пагинацией и фильтрами.
- Смена роли (`USER` | `MANAGER` | `DRIVER` | `ADMIN`).
- Activate / deactivate (`isActive`).
- Soft-delete.
- Restore после soft-delete.
- UI `/admin/users` только для роли `admin`, с обязательной handset-адаптацией.
- Обратная совместимость: `DELETE /api/v1/users/{id}` остаётся.

## 4) Out of Scope / Out of Scope (не входит)
- Permission matrix / fine-grained permissions (матрица отдельных разрешений).
- Invite / создание пользователя админом.
- Смена email / пароля админом.
- Доступ `MANAGER` к user-admin (устарело: GET списка/карточки разрешён; см. блок Статус).

> Multi-role и переключение активной роли — канон: [`user-multi-role-switching.ru.en.md`](user-multi-role-switching.ru.en.md).

## 5) User Stories / Пользовательские сценарии
1. **Как** ADMIN, **я хочу** видеть список пользователей с фильтрами, **чтобы** быстро найти нужный аккаунт.
2. **Как** ADMIN, **я хочу** менять роль пользователя, **чтобы** выдавать доступ к admin-функциям без правок БД.
3. **Как** ADMIN, **я хочу** деактивировать или soft-delete пользователя, **чтобы** отозвать доступ.
4. **Как** ADMIN, **я хочу** восстановить soft-deleted пользователя, **чтобы** снова разрешить вход.
5. **Как** ADMIN на телефоне, **я хочу** пользоваться `/admin/users` на handset, **чтобы** управлять пользователями без десктопа.

## 6) Functional Requirements / Функциональные требования
1. Доступ ко всем `/api/v1/admin/users/**` — только `ADMIN` (`403` иначе).
2. `GET /api/v1/admin/users` — пагинация; фильтры `email` (contains), `role`, `active`, `deleted` (default `false`), `sort`/`order`/`page`/`size`.
3. `GET /api/v1/admin/users/{id}` — карточка пользователя без `passwordHash`.
4. `PATCH .../role` — тело `{ "role": "..." }`; revoke всех refresh-токенов цели.
5. `PATCH .../active` — тело `{ "active": true|false }`; при `false` — revoke refresh.
6. `DELETE /api/v1/admin/users/{id}` — soft-delete (идемпотентный `204`); revoke refresh.
7. `POST /api/v1/admin/users/{id}/restore` — снять soft-delete, `active=true`; идемпотентно если не удалён; при конфликте email → `409` `EMAIL_ALREADY_EXISTS`.
8. Запрет операций над собой: роль / deactivate / soft-delete / restore → `400` `SELF_OPERATION_FORBIDDEN`.
9. Защита последнего активного ADMIN при demote / deactivate / soft-delete → `409` `LAST_ADMIN_PROTECTED`.
10. Role/active на soft-deleted → `409` `USER_DELETED`.
11. Frontend: `/admin/users`, toolbar item, i18n uk/en/ru; действия над текущим пользователем в UI заблокированы; restore для deleted.

## 7) Non-functional Requirements / Нефункциональные требования
- **Security / Безопасность:** только ADMIN; self-op и last-admin политики; revoke sessions при role/active(false)/delete.
- **Performance / Производительность:** server-side pagination, size ≤ 100.
- **Reliability / Надежность:** soft-delete идемпотентен.
- **Logging/Monitoring / Логирование и мониторинг:** без логирования секретов; стандартные API error codes.
- **Accessibility/UX / Доступность и UX:** Material; handset через `LayoutService` + `bp.handset`; `.table-shell`; dialogs через `getHandsetFriendlyDialogConfig`.

## 8) Data Contracts and API / Контракты данных и API
### 8.1 Input Data / Входные данные
- **Format / Формат:** JSON / query params.
- **Validation / Валидация:** UUID id; `role` из enum; `active` boolean; page/size bounds.

### 8.2 Output Data / Выходные данные
- **Format / Формат:** `UserAdminDto`: `id`, `email`, `role`, `active`, `deleted`, `emailVerified`, `createdAt`, `updatedAt`, `deletedAt`.
- **Errors / Ошибки:** `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_ERROR`, `SELF_OPERATION_FORBIDDEN`, `LAST_ADMIN_PROTECTED`, `USER_DELETED`, `EMAIL_ALREADY_EXISTS`.

### 8.3 Endpoints (if any) / Эндпоинты (если есть)
- `GET /api/v1/admin/users` — список.
- `GET /api/v1/admin/users/{id}` — детали.
- `PATCH /api/v1/admin/users/{id}/role` — смена роли.
- `PATCH /api/v1/admin/users/{id}/active` — activate/deactivate.
- `DELETE /api/v1/admin/users/{id}` — soft-delete.
- `POST /api/v1/admin/users/{id}/restore` — restore после soft-delete.
- `DELETE /api/v1/users/{id}` — legacy soft-delete (тот же сервис).

## 9) UX/UI Requirements (frontend) / UX/UI требования (frontend)
- States / Состояния: `loading` / `empty` / `error` / `success`.
- Form behavior / Поведение форм: confirm перед demote/deactivate/delete/restore; role select; slide-toggle active.
- Navigation / Навигация: `/admin/users`, `roles: ['admin']`.
- Handset: компактные фильтры (стек), уменьшенный pageSize, horizontal scroll таблицы, touch-friendly dialogs/actions.
- UI texts / Тексты интерфейса: `pages.adminUsers.*`, `navigation.adminUsers`.

## 10) Architecture Changes / Изменения в архитектуре
- **Components/services / Компоненты/сервисы:** `AdminUserController`, `AdminUserService`, расширенный `UserRepository`, Angular `AdminUsersComponent` + API service.
- **Data storage / Хранилище данных:** без новых миграций (поля уже есть).
- **Integrations / Интеграции:** нет.
- **Compatibility / Совместимость:** legacy `DELETE /users/{id}` сохраняется.

## 11) Implementation Constraints / Ограничения реализации
- Use existing stack and project conventions. / Использовать существующий стек и соглашения проекта.
- Do not add dependencies without rationale. / Не добавлять зависимости без обоснования.
- Do not change unrelated modules. / Не менять несвязанные модули.
- Preserve backward compatibility where required. / Сохранять обратную совместимость где требуется.

## 12) Implementation Plan / План реализации
1. Backend API + политики + тесты.
2. Frontend API client.
3. UI `/admin/users` (desktop + handset) + i18n/nav.
4. Обновить `system.ru.md` и auth ТЗ.

## 13) Acceptance Criteria (Definition of Done) / Критерии приемки
- [x] ADMIN может list/get/patch role/patch active/soft-delete через `/api/v1/admin/users`.
- [x] USER получает `403` на admin users API и не видит пункт меню. MANAGER видит меню и GET list/get; мутации — `403`.
- [x] Self-op и last-admin политики работают с заявленными кодами.
- [x] После deactivate/role/delete refresh цели недействителен.
- [x] Страница `/admin/users` работает на desktop и handset (фильтры, таблица `.table-shell`, диалоги).
- [x] Documentation updated / Документация обновлена.
- [x] Tests added/updated and passing / Тесты добавлены или обновлены и проходят (`AdminUserIntegrationTest`).

## 14) Test Plan / Тест-план
- **Unit:** frontend API service.
- **Integration:** MockMvc RBAC, self-op, last-admin, revoke sessions, filters.
- **E2E/Manual:** `/admin/users` desktop + handset breakpoint.
- **Edge cases / Граничные случаи:** идемпотентный soft-delete; операции над deleted; last ADMIN.

## 15) Risks and Assumptions / Риски и допущения
- **Risks / Риски:** админ может повысить другого до ADMIN; смягчается last-admin и self-op политиками.
- **Assumptions / Допущения:** JWT не содержит роль; фильтр читает роль из БД на каждый запрос.
- **Rollback plan / План отката:** откат PR; legacy soft-delete путь остаётся.

## 16) Release Artifacts / Артефакты релиза
- PR: —
- Version/tag / Версия/тег: —
- Release date / Дата релиза: —
- Owner / Ответственный: —

---

## Instructions for LLM / Инструкции для LLM
Use these rules when implementing this specification / Используй правила ниже при реализации по этому ТЗ:

1. Stay within `Scope` and `Out of Scope`.
2. Follow current architecture and project style.
3. Handset adaptation is mandatory for `/admin/users`.
4. Do not add dependencies without explicit justification.
5. Comments in code: Ukrainian.
