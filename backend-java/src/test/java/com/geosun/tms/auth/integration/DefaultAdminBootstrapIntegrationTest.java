package com.geosun.tms.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geosun.tms.auth.config.DefaultAdminBootstrapProperties;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.service.DefaultAdminBootstrapService;
import com.geosun.tms.support.TestEnterprise;
import java.util.EnumSet;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DefaultAdminBootstrapIntegrationTest {

  @Autowired private DefaultAdminBootstrapService bootstrapService;
  @Autowired private DefaultAdminBootstrapProperties properties;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void resetUsersAndProperties() {
    userRepository.deleteAll();
    properties.setEmail("bootstrap-admin@example.com");
    properties.setPassword("Bootstrap1");
  }

  @Test
  void createsAdminWhenDatabaseEmpty() {
    bootstrapService.ensureDefaultAdmin();

    User user =
        userRepository.findByEmailAndDeletedFalse("bootstrap-admin@example.com").orElseThrow();
    assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    assertThat(user.hasAvailableRole(Role.ADMIN)).isTrue();
    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.isActive()).isTrue();
    assertThat(passwordEncoder.matches("Bootstrap1", user.getPasswordHash())).isTrue();
  }

  @Test
  void skipsWhenAdminAlreadyPresent() {
    User existing = new User();
    existing.setEnterpriseId(TestEnterprise.ID);
    existing.setEmail("existing-admin@example.com");
    existing.setPasswordHash(passwordEncoder.encode("Admin123!"));
    existing.setRole(Role.ADMIN);
    existing.replaceAvailableRoles(Objects.requireNonNull(EnumSet.of(Role.ADMIN)));
    existing.setEmailVerified(true);
    existing.setActive(true);
    userRepository.save(existing);

    bootstrapService.ensureDefaultAdmin();

    assertThat(userRepository.findByEmailAndDeletedFalse("bootstrap-admin@example.com")).isEmpty();
    assertThat(userRepository.count()).isEqualTo(1);
  }

  @Test
  void idempotentSecondCallDoesNotDuplicate() {
    bootstrapService.ensureDefaultAdmin();
    bootstrapService.ensureDefaultAdmin();

    assertThat(userRepository.count()).isEqualTo(1);
  }
}
