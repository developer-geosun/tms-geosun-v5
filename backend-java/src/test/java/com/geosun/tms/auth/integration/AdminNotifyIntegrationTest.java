package com.geosun.tms.auth.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.config.UserRegisteredNotifyProperties;
import com.geosun.tms.auth.domain.profile.PersonType;
import com.geosun.tms.auth.domain.profile.UserProfile;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.EmailVerificationTokenRepository;
import com.geosun.tms.auth.repository.PasswordResetTokenRepository;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserProfileRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.chatbot.repository.BotLinkCodeRepository;
import com.geosun.tms.chatbot.repository.BotMessageLogRepository;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.internet.MimeMessage;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Інтеграція сповіщення ADMIN після register (без {@code @Transactional}, щоб спрацював
 * afterCommit).
 */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminNotifyIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private UserProfileRepository userProfileRepository;
  @Autowired private UserContactPhoneRepository userContactPhoneRepository;
  @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private BotIdentityRepository botIdentityRepository;
  @Autowired private BotLinkCodeRepository botLinkCodeRepository;
  @Autowired private BotMessageLogRepository botMessageLogRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RateLimitService rateLimitService;
  @Autowired private UserRegisteredNotifyProperties notifyProperties;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    rateLimitService.resetForTests();
    notifyProperties.setEnabled(true);
    stubMailSenderSuccess();
    clearUsersAndDeps();
  }

  @AfterEach
  void tearDown() {
    // Без @Transactional дані комітяться — прибираємо, щоб не ламати LAST_ADMIN_* в інших тестах.
    clearUsersAndDeps();
    notifyProperties.setEnabled(true);
  }

  @Test
  void register_notifiesActiveAdminByEmail_fallbackWithoutProfile() throws Exception {
    saveUser("admin-fallback@example.com", "Admin123!", Role.ADMIN);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "newbie@example.com", "Secret123"))))
        .andExpect(status().isCreated());

    // verification + admin notify
    verify(javaMailSender, times(2)).send(anyMailMessage());
  }

  @Test
  void register_doesNotNotifyManager() throws Exception {
    saveUser("manager-only@example.com", "Manager123!", Role.MANAGER);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "u-mgr@example.com", "Secret123"))))
        .andExpect(status().isCreated());

    verify(javaMailSender, times(1)).send(anyMailMessage());
  }

  @Test
  void register_adminWithMessengersOnly_skipsEmail() throws Exception {
    User admin = saveUser("admin-msg@example.com", "Admin123!", Role.ADMIN);
    UserProfile profile = new UserProfile();
    profile.setUserId(Objects.requireNonNull(admin.getId()));
    profile.setLastName("Адмін");
    profile.setFirstName("Тест");
    profile.setPersonType(PersonType.INDIVIDUAL);
    profile.setContactViaEmail(false);
    profile.setContactViaPhone(false);
    profile.setContactViaMessengers(true);
    userProfileRepository.save(profile);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "u-msg@example.com", "Secret123"))))
        .andExpect(status().isCreated());

    // лише verification новому USER (немає ACTIVE bot identity → SKIP messenger)
    verify(javaMailSender, times(1)).send(anyMailMessage());
  }

  @Test
  void register_adminSmtpFail_stillReturns201() throws Exception {
    saveUser("admin-smtp@example.com", "Admin123!", Role.ADMIN);
    when(javaMailSender.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    doNothing()
        .doThrow(new MailSendException("admin smtp down"))
        .when(javaMailSender)
        .send(anyMailMessage());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "u-smtp@example.com", "Secret123"))))
        .andExpect(status().isCreated());
  }

  @Test
  void register_flagDisabled_skipsAdminMail() throws Exception {
    saveUser("admin-off@example.com", "Admin123!", Role.ADMIN);
    notifyProperties.setEnabled(false);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "u-off@example.com", "Secret123"))))
        .andExpect(status().isCreated());

    verify(javaMailSender, times(1)).send(anyMailMessage());
  }

  @Test
  void register_twoAdmins_sendsTwoNotifyMailsPlusVerification() throws Exception {
    saveUser("admin-a@example.com", "Admin123!", Role.ADMIN);
    saveUser("admin-b@example.com", "Admin123!", Role.ADMIN);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "u-two@example.com", "Secret123"))))
        .andExpect(status().isCreated());

    verify(javaMailSender, times(3)).send(anyMailMessage());
  }

  private void clearUsersAndDeps() {
    botMessageLogRepository.deleteAll();
    botLinkCodeRepository.deleteAll();
    botIdentityRepository.deleteAll();
    emailVerificationTokenRepository.deleteAll();
    passwordResetTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    userContactPhoneRepository.deleteAll();
    userProfileRepository.deleteAll();
    userRepository.deleteAll();
  }

  private User saveUser(String email, String password, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setId(UUID.randomUUID().toString());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    // id уже заданий → Spring Data робить merge, і @PrePersist не встигає заповнити user_roles.
    user.ensureRoleAssigned(Objects.requireNonNull(role));
    user.setEmailVerified(true);
    user.setActive(true);
    return userRepository.save(Objects.requireNonNull(user));
  }

  private void stubMailSenderSuccess() {
    when(javaMailSender.createMimeMessage())
        .thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    doNothing().when(javaMailSender).send(anyMailMessage());
  }

  @NonNull
  private MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static MimeMessage anyMailMessage() {
    return any(MimeMessage.class);
  }
}
