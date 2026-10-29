-- Крок 2.3 multi-enterprise: номери рейсів унікальні в межах підприємства.

-- 1) Унікальність trip_number: глобальна -> (enterprise_id, trip_number).
ALTER TABLE trips DROP INDEX uk_trips_trip_number;
ALTER TABLE trips
    ADD UNIQUE KEY uk_trips_enterprise_trip_number (enterprise_id, trip_number);

-- 2) Лічильник номерів: ключ (enterprise_id, seq_year).
-- Flyway виконується до EnterpriseBootstrapRunner, тому на першому деплої enterprise_id
-- у наявних рядках ще NULL, а PRIMARY KEY у MySQL вимагає NOT NULL. Тому PK стає
-- сурогатним (id), а природний ключ — UNIQUE (enterprise_id, seq_year).
-- JPA-сутність мапить складений ключ через @IdClass; ddl-auto=validate PK у БД не перевіряє.
ALTER TABLE trip_number_seq
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
    ADD PRIMARY KEY (id),
    ADD UNIQUE KEY uk_trip_number_seq_enterprise_year (enterprise_id, seq_year);
