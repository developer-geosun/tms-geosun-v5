/** Максимальна довжина коду підприємства (як на backend). */
export const ENTERPRISE_CODE_MAX_LENGTH = 32;

/** Код підприємства: 2–32 символи, латиниця, цифри, дефіс (не на краях). */
export const ENTERPRISE_CODE_PATTERN = /^[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])$/;

/** Зарезервований код — не може бути кодом підприємства. */
export const ENTERPRISE_CODE_RESERVED = 'platform';

/** Нормалізація коду перед відправкою: без пробілів по краях, нижній регістр. */
export function normalizeEnterpriseCode(raw: string): string {
  return raw.trim().toLowerCase();
}
