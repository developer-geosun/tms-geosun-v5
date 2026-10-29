-- Підприємства і оператор платформи. enterprise_id на коренях поки NULL:
-- заповнює EnterpriseBootstrapRunner, інакше існуючі рядки не проходять міграцію.

CREATE TABLE enterprises (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(32) NOT NULL,
    is_active TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_enterprises_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_operators (
    id VARCHAR(36) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_active TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_operators_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_operator_refresh_tokens (
    id VARCHAR(36) NOT NULL,
    operator_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_token_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_operator_refresh_tokens_hash (token_hash),
    KEY idx_platform_operator_refresh_tokens_operator (operator_id),
    CONSTRAINT fk_platform_operator_refresh_tokens_operator
        FOREIGN KEY (operator_id) REFERENCES platform_operators (id),
    CONSTRAINT fk_platform_operator_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_token_id) REFERENCES platform_operator_refresh_tokens (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE users
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_users_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_users_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE vehicles
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_vehicles_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_vehicles_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE drivers
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_drivers_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_drivers_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE vehicle_combinations
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_vehicle_combinations_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_vehicle_combinations_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE routes
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_routes_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_routes_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE route_requests
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_route_requests_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_route_requests_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE freight_quotes
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_freight_quotes_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_freight_quotes_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE freight_numeric_scenarios
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_freight_numeric_scenarios_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_freight_numeric_scenarios_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE toll_tariff_sets
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_toll_tariff_sets_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_toll_tariff_sets_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE freight_cost_calculations
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_freight_cost_calculations_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_freight_cost_calculations_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE trips
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_trips_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_trips_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE stored_files
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_stored_files_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_stored_files_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE document_types
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_document_types_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_document_types_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE bot_identities
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_bot_identities_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_bot_identities_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

ALTER TABLE bot_link_codes
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_bot_link_codes_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_bot_link_codes_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);

-- Первинний ключ лишається (seq_year): видача номера ще читає рядок за роком.
-- Пара (підприємство, рік) — разом із зміною TripService.
ALTER TABLE trip_number_seq
    ADD COLUMN enterprise_id VARCHAR(36) NULL,
    ADD KEY idx_trip_number_seq_enterprise_id (enterprise_id),
    ADD CONSTRAINT fk_trip_number_seq_enterprise
        FOREIGN KEY (enterprise_id) REFERENCES enterprises (id);
