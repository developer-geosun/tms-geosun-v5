package com.geosun.tms.enterprise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.config.DefaultAdminBootstrapProperties;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.auth.service.DefaultAdminBootstrapService;
import com.geosun.tms.enterprise.config.EnterpriseProperties;
import com.geosun.tms.enterprise.config.PlatformOperatorBootstrapProperties;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.storage.domain.StoredFile;
import com.geosun.tms.storage.repository.StoredFileRepository;
import com.geosun.tms.trips.repository.TripNumberSeqRepository;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@ActiveProfiles("test")
@Transactional
class EnterpriseBootstrapIntegrationTest {

  @Autowired private EnterpriseBootstrapService bootstrapService;
  @Autowired private DefaultAdminBootstrapService adminBootstrapService;
  @Autowired private EnterpriseProperties enterpriseProperties;
  @Autowired private PlatformOperatorBootstrapProperties operatorProperties;
  @Autowired private DefaultAdminBootstrapProperties adminProperties;
  @Autowired private EnterpriseRepository enterpriseRepository;
  @Autowired private PlatformOperatorRepository platformOperatorRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private StoredFileRepository storedFileRepository;
  @Autowired private TripNumberSeqRepository tripNumberSeqRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void reset() {
    userRepository.deleteAll();
    storedFileRepository.deleteAll();
    tripNumberSeqRepository.deleteAll();
    platformOperatorRepository.deleteAll();
    enterpriseRepository.deleteAll();
    enterpriseProperties.setDefaultCode("geosun");
    enterpriseProperties.setDefaultName("GeoSun");
    operatorProperties.setEmail("operator@example.com");
    operatorProperties.setPassword("Operator1");
    adminProperties.setEmail("admin@example.com");
    adminProperties.setPassword("Adminpass1");
  }

  @Test
  void emptyDatabaseCreatesEnterpriseAndOperatorOnce() {
    bootstrapService.ensureFirstEnterprise();
    String hash = soleOperator().getPasswordHash();

    operatorProperties.setPassword("Changed99");
    enterpriseProperties.setDefaultCode("other-code");
    enterpriseProperties.setDefaultName("Other");
    bootstrapService.ensureFirstEnterprise();

    assertThat(enterpriseRepository.count()).isEqualTo(1);
    assertThat(soleEnterprise().getCode()).isEqualTo("geosun");
    assertThat(soleEnterprise().getName()).isEqualTo("GeoSun");
    assertThat(platformOperatorRepository.count()).isEqualTo(1);
    assertThat(soleOperator().getPasswordHash()).isEqualTo(hash);
    assertThat(passwordEncoder.matches("Operator1", hash)).isTrue();
  }

  @Test
  void assignsExistingRowsToTheFirstEnterprise() {
    User user = new User();
    user.setEmail("person@example.com");
    user.setPasswordHash("hash");
    user.setRole(Role.USER);
    user.setActive(true);
    user.setEmailVerified(true);
    userRepository.saveAndFlush(user);

    StoredFile file = new StoredFile();
    file.setStorageKey("key-1");
    file.setOriginalFilename("a.txt");
    file.setContentType("text/plain");
    file.setSizeBytes(1);
    storedFileRepository.saveAndFlush(file);

    bootstrapService.ensureFirstEnterprise();
    entityManager.clear();

    String enterpriseId = Objects.requireNonNull(soleEnterprise().getId());
    assertThat(
            userRepository
                .findByEmailAndDeletedFalse("person@example.com")
                .orElseThrow()
                .getEnterpriseId())
        .isEqualTo(enterpriseId);
    assertThat(storedFileRepository.findAll().get(0).getEnterpriseId()).isEqualTo(enterpriseId);
  }

  @Test
  void defaultAdminIsCreatedInsideTheFirstEnterprise() {
    bootstrapService.ensureFirstEnterprise();
    adminBootstrapService.ensureDefaultAdmin();

    User admin = userRepository.findByEmailAndDeletedFalse("admin@example.com").orElseThrow();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.getEnterpriseId()).isEqualTo(soleEnterprise().getId());
  }

  @Test
  void missingCodeFailsClosed() {
    enterpriseProperties.setDefaultCode("");

    assertThatThrownBy(() -> bootstrapService.ensureFirstEnterprise())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEFAULT_ENTERPRISE_CODE");
  }

  @Test
  void operatorEmailCollisionWithUserFailsClosed() {
    User user = new User();
    user.setEmail("operator@example.com");
    user.setPasswordHash("hash");
    user.setRole(Role.USER);
    user.setActive(true);
    userRepository.saveAndFlush(user);

    assertThatThrownBy(() -> bootstrapService.ensureFirstEnterprise())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("existing user");
  }

  @Test
  void operatorEmailCollisionWithAdminEmailFailsClosed() {
    operatorProperties.setEmail("admin@example.com");

    assertThatThrownBy(() -> bootstrapService.ensureFirstEnterprise())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_EMAIL");
  }

  private Enterprise soleEnterprise() {
    return enterpriseRepository.findAll().get(0);
  }

  private PlatformOperator soleOperator() {
    return platformOperatorRepository.findAll().get(0);
  }
}
