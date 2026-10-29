-- Email унікальний всередині підприємства (не видалені). Глобальний uk_users_active_email знімаємо.

ALTER TABLE users DROP INDEX uk_users_active_email;

ALTER TABLE users
    ADD UNIQUE KEY uk_users_enterprise_active_email (enterprise_id, active_email);
