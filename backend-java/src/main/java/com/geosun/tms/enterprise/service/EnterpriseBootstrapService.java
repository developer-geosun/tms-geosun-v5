package com.geosun.tms.enterprise.service;

import com.geosun.tms.auth.config.DefaultAdminBootstrapProperties;
import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.config.EnterpriseProperties;
import com.geosun.tms.enterprise.config.PlatformOperatorBootstrapProperties;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.domain.EnterpriseCodeRules;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Перше підприємство і оператор платформи. Повторний старт не змінює код і пароль. Порожні
 * enterprise_id після переносу зупиняють старт. У робочій базі після заповнення колонка стає NOT
 * NULL (V49, якщо порожніх уже не було, або цей раннер). У тестах DDL не виконується: H2-схема
 * лишає колонку nullable, щоб можна було вставити рядок до переносу.
 */
@Service
public class EnterpriseBootstrapService {

  private static final Logger log = LoggerFactory.getLogger(EnterpriseBootstrapService.class);

  /** Ті самі правила, що реєстрація співробітника. */
  private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  /** Корені з власним id в API плюс лічильник номерів рейсу. */
  private static final List<String> SCOPED_TABLES =
      List.of(
          "users",
          "vehicles",
          "drivers",
          "vehicle_combinations",
          "routes",
          "route_requests",
          "freight_quotes",
          "freight_numeric_scenarios",
          "toll_tariff_sets",
          "freight_cost_calculations",
          "trips",
          "stored_files",
          "document_types",
          "bot_identities",
          "bot_link_codes",
          "trip_number_seq");

  private final EnterpriseRepository enterpriseRepository;
  private final PlatformOperatorRepository platformOperatorRepository;
  private final UserRepository userRepository;
  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final EnterpriseProperties enterpriseProperties;
  private final PlatformOperatorBootstrapProperties operatorProperties;
  private final DefaultAdminBootstrapProperties adminProperties;
  private final Environment environment;

  public EnterpriseBootstrapService(
      EnterpriseRepository enterpriseRepository,
      PlatformOperatorRepository platformOperatorRepository,
      UserRepository userRepository,
      JdbcTemplate jdbcTemplate,
      PasswordEncoder passwordEncoder,
      EnterpriseProperties enterpriseProperties,
      PlatformOperatorBootstrapProperties operatorProperties,
      DefaultAdminBootstrapProperties adminProperties,
      Environment environment) {
    this.enterpriseRepository = enterpriseRepository;
    this.platformOperatorRepository = platformOperatorRepository;
    this.userRepository = userRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
    this.enterpriseProperties = enterpriseProperties;
    this.operatorProperties = operatorProperties;
    this.adminProperties = adminProperties;
    this.environment = environment;
  }

  @Transactional
  public void ensureFirstEnterprise() {
    Enterprise enterprise =
        enterpriseRepository
            .findFirstByOrderByCreatedAtAsc()
            .orElseGet(() -> createFirstEnterprise());
    String enterpriseId = Objects.requireNonNull(enterprise.getId(), "enterprise id");
    backfill(enterpriseId);
    ensureOperator();
    assertNoUnassignedRows();
    enforceNotNullColumns();
  }

  @NonNull
  private Enterprise createFirstEnterprise() {
    String code = EnterpriseCodeRules.normalize(enterpriseProperties.getDefaultCode());
    if (code == null) {
      throw new IllegalStateException(
          "DEFAULT_ENTERPRISE_CODE is missing or invalid (2-32 latin letters, digits, hyphen;"
              + " not 'platform')");
    }
    String name =
        enterpriseProperties.getDefaultName() == null
            ? ""
            : enterpriseProperties.getDefaultName().trim();
    if (!StringUtils.hasText(name) || name.length() > 200) {
      throw new IllegalStateException("DEFAULT_ENTERPRISE_NAME is missing or longer than 200");
    }
    Enterprise enterprise = new Enterprise();
    enterprise.setCode(code);
    enterprise.setName(name);
    enterprise.setActive(true);
    Enterprise saved = enterpriseRepository.saveAndFlush(enterprise);
    log.info("First enterprise created with code {}", code);
    return Objects.requireNonNull(saved);
  }

  private void backfill(@NonNull String enterpriseId) {
    for (String table : SCOPED_TABLES) {
      jdbcTemplate.update(
          "UPDATE " + table + " SET enterprise_id = ? WHERE enterprise_id IS NULL", enterpriseId);
    }
  }

  private void ensureOperator() {
    if (platformOperatorRepository.count() > 0) {
      log.debug("Platform operator bootstrap skipped: operator already present");
      return;
    }
    String email = normalizeOperatorEmail(operatorProperties.getEmail());
    String password = operatorProperties.getPassword();
    if (!StringUtils.hasText(password) || !PASSWORD_PATTERN.matcher(password).matches()) {
      throw new IllegalStateException(
          "PLATFORM_OPERATOR_PASSWORD must be at least 8 characters and contain a letter and a"
              + " digit");
    }
    String adminEmail = EmailNormalizer.normalize(adminProperties.getEmail());
    if (adminEmail != null && email.equals(adminEmail)) {
      throw new IllegalStateException("PLATFORM_OPERATOR_EMAIL collides with ADMIN_EMAIL");
    }
    if (userRepository.existsByEmail(email)) {
      throw new IllegalStateException("PLATFORM_OPERATOR_EMAIL collides with an existing user");
    }
    PlatformOperator operator = new PlatformOperator();
    operator.setEmail(email);
    operator.setPasswordHash(passwordEncoder.encode(password));
    operator.setActive(true);
    platformOperatorRepository.save(operator);
    log.info("Platform operator created");
  }

  @NonNull
  private static String normalizeOperatorEmail(String raw) {
    String normalized = EmailNormalizer.normalize(raw);
    if (!StringUtils.hasText(normalized) || !EMAIL_PATTERN.matcher(normalized).matches()) {
      throw new IllegalStateException("PLATFORM_OPERATOR_EMAIL is missing or invalid");
    }
    return Objects.requireNonNull(normalized);
  }

  /** У профілі test колонку не чіпаємо: тести вставляють рядок без підприємства до переносу. */
  private void enforceNotNullColumns() {
    if (environment.acceptsProfiles(Profiles.of("test"))) {
      return;
    }
    for (String table : SCOPED_TABLES) {
      String tableName = Objects.requireNonNull(table);
      String nullable =
          jdbcTemplate.queryForObject(
              "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                  + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                  + "AND COLUMN_NAME = 'enterprise_id'",
              String.class,
              tableName);
      if (nullable != null && "YES".equalsIgnoreCase(nullable)) {
        jdbcTemplate.execute(
            "ALTER TABLE " + tableName + " MODIFY enterprise_id VARCHAR(36) NOT NULL");
      }
    }
  }

  private void assertNoUnassignedRows() {
    for (String table : SCOPED_TABLES) {
      Long remaining =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE enterprise_id IS NULL", Long.class);
      if (remaining == null || remaining.longValue() > 0L) {
        throw new IllegalStateException(
            "Enterprise backfill left rows without enterprise_id in " + table);
      }
    }
  }
}
