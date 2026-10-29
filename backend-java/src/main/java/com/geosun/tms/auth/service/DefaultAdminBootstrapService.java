package com.geosun.tms.auth.service;

import com.geosun.tms.auth.config.DefaultAdminBootstrapProperties;
import com.geosun.tms.auth.domain.EmailNormalizer;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Створення первинного ADMIN з env, якщо в БД ще немає ADMIN у available roles. */
@Service
public class DefaultAdminBootstrapService {

  private static final Logger log = LoggerFactory.getLogger(DefaultAdminBootstrapService.class);

  /** Ті самі правила, що {@code RegisterRequest}. */
  private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final DefaultAdminBootstrapProperties properties;
  private final EnterpriseRepository enterpriseRepository;

  public DefaultAdminBootstrapService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      DefaultAdminBootstrapProperties properties,
      EnterpriseRepository enterpriseRepository) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.enterpriseRepository = enterpriseRepository;
  }

  @Transactional
  public void ensureDefaultAdmin() {
    Optional<Enterprise> firstEnterprise = enterpriseRepository.findFirstByOrderByCreatedAtAsc();
    if (adminAlreadyPresent(firstEnterprise)) {
      log.debug("Default ADMIN bootstrap skipped: ADMIN already present");
      return;
    }

    String rawEmail = properties.getEmail();
    String rawPassword = properties.getPassword();
    if (!StringUtils.hasText(rawEmail) || !StringUtils.hasText(rawPassword)) {
      log.warn(
          "Default ADMIN bootstrap skipped: ADMIN_EMAIL and/or ADMIN_PASSWORD are not configured");
      return;
    }

    String normalizedEmail = EmailNormalizer.normalize(rawEmail);
    if (!StringUtils.hasText(normalizedEmail)
        || !EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
      log.warn("Default ADMIN bootstrap skipped: ADMIN_EMAIL is not a valid email");
      return;
    }
    String email = Objects.requireNonNull(normalizedEmail);

    if (!PASSWORD_PATTERN.matcher(rawPassword).matches()) {
      log.warn(
          "Default ADMIN bootstrap skipped: ADMIN_PASSWORD must be at least 8 characters and"
              + " contain a letter and a digit");
      return;
    }
    String password = Objects.requireNonNull(rawPassword);

    if (userRepository.existsByEmailAndDeletedFalse(email)) {
      log.warn("Default ADMIN bootstrap skipped: email {} is already registered", maskEmail(email));
      return;
    }

    if (adminAlreadyPresent(firstEnterprise)) {
      return;
    }

    User user = new User();
    user.setEmail(email);
    if (firstEnterprise.isPresent()) {
      user.setEnterpriseId(Objects.requireNonNull(firstEnterprise.get().getId()));
    }
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.ADMIN);
    user.replaceAvailableRoles(Objects.requireNonNull(EnumSet.of(Role.ADMIN)));
    user.setEmailVerified(true);
    user.setEmailVerifiedAt(Instant.now());
    user.setActive(true);
    userRepository.save(user);

    log.info("Default ADMIN account created for {}", maskEmail(email));
  }

  private boolean adminAlreadyPresent(Optional<Enterprise> firstEnterprise) {
    if (firstEnterprise.isEmpty()) {
      return userRepository.countNonDeletedWithAvailableRole(Role.ADMIN) > 0;
    }
    String enterpriseId = Objects.requireNonNull(firstEnterprise.get().getId());
    return userRepository.countNonDeletedWithAvailableRoleAndEnterpriseId(Role.ADMIN, enterpriseId)
        > 0;
  }

  @NonNull
  private static String maskEmail(@NonNull String email) {
    int at = email.indexOf('@');
    if (at <= 1) {
      return "***";
    }
    return email.charAt(0) + "***" + Objects.requireNonNull(email.substring(at));
  }
}
