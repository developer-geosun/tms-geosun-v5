package com.geosun.tms.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geosun.tms.auth.config.DefaultAdminBootstrapProperties;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DefaultAdminBootstrapServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private EnterpriseRepository enterpriseRepository;

  private DefaultAdminBootstrapProperties properties;
  private DefaultAdminBootstrapService service;

  @BeforeEach
  void setUp() {
    properties = new DefaultAdminBootstrapProperties();
    when(enterpriseRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
    service =
        new DefaultAdminBootstrapService(
            userRepository, passwordEncoder, properties, enterpriseRepository);
  }

  @Test
  void ensureDefaultAdmin_skipsWhenAdminAlreadyExists() {
    when(userRepository.countNonDeletedWithAvailableRole(Role.ADMIN)).thenReturn(1L);

    service.ensureDefaultAdmin();

    verify(userRepository, never()).save(anyUser());
  }

  @Test
  void ensureDefaultAdmin_skipsWhenEnvEmpty() {
    when(userRepository.countNonDeletedWithAvailableRole(Role.ADMIN)).thenReturn(0L);
    properties.setEmail("");
    properties.setPassword("");

    service.ensureDefaultAdmin();

    verify(userRepository, never()).save(anyUser());
  }

  @Test
  void ensureDefaultAdmin_createsAdminWhenConfigured() {
    when(userRepository.countNonDeletedWithAvailableRole(Role.ADMIN)).thenReturn(0L);
    when(userRepository.existsByEmailAndDeletedFalse("admin@example.com")).thenReturn(false);
    when(passwordEncoder.encode("Secret123")).thenReturn("hash");

    properties.setEmail("admin@example.com");
    properties.setPassword("Secret123");

    service.ensureDefaultAdmin();

    ArgumentCaptor<User> captor = userCaptor();
    verify(userRepository).save(captureUser(captor));
    User saved = capturedUser(captor);
    assertThat(saved.getEmail()).isEqualTo("admin@example.com");
    assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
    assertThat(saved.hasAvailableRole(Role.ADMIN)).isTrue();
    assertThat(saved.isEmailVerified()).isTrue();
    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getPasswordHash()).isEqualTo("hash");
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static User anyUser() {
    return any(User.class);
  }

  /** Mockito forClass() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static ArgumentCaptor<User> userCaptor() {
    return ArgumentCaptor.forClass(User.class);
  }

  /** Mockito capture() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static User captureUser(@NonNull ArgumentCaptor<User> captor) {
    return captor.capture();
  }

  @NonNull
  private static User capturedUser(@NonNull ArgumentCaptor<User> captor) {
    return Objects.requireNonNull(captor.getValue(), "Expected captured User");
  }
}
