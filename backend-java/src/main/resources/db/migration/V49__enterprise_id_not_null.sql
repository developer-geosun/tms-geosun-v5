-- Після першого деплою раннер уже заповнив enterprise_id.
-- NOT NULL ставимо лише там, де порожніх рядків немає: інакше перший старт
-- зі старої бази (V46 додав NULL, раннер ще не відпрацював) обірвався б до переносу.
-- Залишок NULL закриває EnterpriseBootstrapRunner після заповнення.

SET @nulls := (SELECT COUNT(*) FROM users WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE users MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM vehicles WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE vehicles MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM drivers WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE drivers MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM vehicle_combinations WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE vehicle_combinations MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM routes WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE routes MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM route_requests WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE route_requests MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM freight_quotes WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE freight_quotes MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM freight_numeric_scenarios WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE freight_numeric_scenarios MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM toll_tariff_sets WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE toll_tariff_sets MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM freight_cost_calculations WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE freight_cost_calculations MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM trips WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE trips MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM stored_files WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE stored_files MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM document_types WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE document_types MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM bot_identities WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE bot_identities MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM bot_link_codes WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE bot_link_codes MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @nulls := (SELECT COUNT(*) FROM trip_number_seq WHERE enterprise_id IS NULL);
SET @ddl := IF(@nulls = 0, 'ALTER TABLE trip_number_seq MODIFY enterprise_id VARCHAR(36) NOT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
