package com.geosun.tms.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.config.AppClient;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.ForgotPasswordRequest;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.PasswordResetInfoRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.ResendVerificationRequest;
import com.geosun.tms.auth.dto.request.ResetPasswordRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Інтеграційні сценарії auth API (H2, Mock mail).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[?&]token=([^\\s\"'<>]+)");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private RateLimitService rateLimitService;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    rateLimitService.resetForTests();
    org.mockito.Mockito.reset(javaMailSender);
    stubMailSenderSuccess();
  }

  @Test
  void actuatorHealth_isOk() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void actuatorInfo_exposesServerMetadata() throws Exception {
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.server.apiVersion").value("v1"))
        .andExpect(
            jsonPath("$.server.repositoryUrl")
                .value("https://github.com/developer-geosun/tms-geosun-v4.git"))
        .andExpect(jsonPath("$.server.version").exists())
        .andExpect(jsonPath("$.server.commit").exists());
  }

  @Test
  void register_valid_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(TestEnterprise.CODE, "new@example.com", "Secret123"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.role").value("USER"));
    verify(javaMailSender, times(1)).send(anyMailMessage());
  }

  @Test
  void register_smtpFailure_stillReturns201() throws Exception {
    doThrow(new MailSendException("smtp down")).when(javaMailSender).send(anyMailMessage());
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "smtp-fail@example.com", "Secret123"))))
        .andExpect(status().isCreated());
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    String body = toJson(new RegisterRequest(TestEnterprise.CODE, "dup@example.com", "Secret123"));
    mockMvc
        .perform(post("/api/v1/auth/register").contentType(jsonContentType()).content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(post("/api/v1/auth/register").contentType(jsonContentType()).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void register_invalidPassword_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonContentType())
                .content(
                    toJson(new RegisterRequest(TestEnterprise.CODE, "bad@example.com", "short"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void login_beforeVerify_returns403() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "unverified@example.com", "Secret123"))));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new LoginRequest(
                            TestEnterprise.CODE, "unverified@example.com", "Secret123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
  }

  @Test
  void verify_then_login_me_logout_flow() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(new RegisterRequest(TestEnterprise.CODE, "flow@example.com", "Secret123"))));

    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String token = extractVerificationToken(requireMailText(capturedMail(mailCap)));

    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .contentType(jsonContentType())
                .content(toJson(new VerifyEmailRequest(token))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            new LoginRequest(
                                TestEnterprise.CODE, "flow@example.com", "Secret123"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andReturn();

    JsonNode loginJson = objectMapper.readTree(responseBody(loginResult));
    String access = loginJson.get("accessToken").asText();
    String refresh = loginJson.get("refreshToken").asText();

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("flow@example.com"));

    mockMvc
        .perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(refresh))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void register_withoutClientHeader_sendsAngularLinkAndClientUrl() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "angular-mail@example.com", "Secret123"))));

    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String text = requireMailText(capturedMail(mailCap));
    assertThat(text).contains("http://localhost:4200/verify-email?token=");
    assertThat(text).contains("Angular");
    assertThat(text).contains("http://localhost:4200");
    assertThat(text).doesNotContain("http://localhost:4300");
  }

  @Test
  void register_withFlutterClient_sendsFlutterLinkAndClientUrl() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .header(AppClient.HEADER_NAME, "flutter")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "flutter-mail@example.com", "Secret123"))));

    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String text = requireMailText(capturedMail(mailCap));
    assertThat(text).contains("http://localhost:4300/verify-email?token=");
    assertThat(text).contains("Flutter");
    assertThat(text).contains("http://localhost:4300");
    assertThat(text).doesNotContain("http://localhost:4200");
  }

  @Test
  void forgotPassword_withFlutterClient_sendsFlutterResetLink() throws Exception {
    registerVerifyLogin("flutter-reset@example.com");

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .header(AppClient.HEADER_NAME, "FLUTTER")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(
                            TestEnterprise.CODE, "flutter-reset@example.com"))))
        .andExpect(status().isOk());

    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String text = requireMailText(capturedMail(mailCap));
    assertThat(text).contains("http://localhost:4300/reset-password?token=");
    assertThat(text).contains("Flutter");
  }

  @Test
  void verifyEmail_invalidToken_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/verify-email")
                .contentType(jsonContentType())
                .content(toJson(new VerifyEmailRequest("invalid-token"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void me_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void resend_unknownEmail_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/resend-verification")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ResendVerificationRequest(TestEnterprise.CODE, "ghost@example.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(javaMailSender, times(0)).send(anyMailMessage());
  }

  @Test
  void resend_smtpFailure_returns503() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "resend503@example.com", "Secret123"))));
    verify(javaMailSender, times(1)).send(anyMailMessage());

    org.mockito.Mockito.reset(javaMailSender);
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doThrow(new MailSendException("fail")).when(javaMailSender).send(anyMailMessage());

    mockMvc
        .perform(
            post("/api/v1/auth/resend-verification")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ResendVerificationRequest(
                            TestEnterprise.CODE, "resend503@example.com"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
  }

  @Test
  void refresh_rotation_andReuse_invalidatesAllSessions() throws Exception {
    registerVerifyLogin("rotate@example.com");
    Session s0 = login("rotate@example.com", "Secret123");

    MvcResult r1 =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(jsonContentType())
                    .content(toJson(new RefreshRequest(s0.refresh()))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode j1 = objectMapper.readTree(responseBody(r1));
    String refresh1 = j1.get("refreshToken").asText();

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(s0.refresh()))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SESSION"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(refresh1))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_wrongPassword_then_rateLimited() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "ratelimit@example.com", "Secret123"))));
    ArgumentCaptor<MimeMessage> cap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, cap);
    mockMvc.perform(
        post("/api/v1/auth/verify-email")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new VerifyEmailRequest(
                        extractVerificationToken(requireMailText(capturedMail(cap)))))));

    String loginBody =
        toJson(new LoginRequest(TestEnterprise.CODE, "ratelimit@example.com", "WrongPass99"));
    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(post("/api/v1/auth/login").contentType(jsonContentType()).content(loginBody))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    mockMvc
        .perform(post("/api/v1/auth/login").contentType(jsonContentType()).content(loginBody))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
  }

  @Test
  void disabledUser_login_returns403() throws Exception {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail("disabled@example.com");
    u.setPasswordHash(passwordEncoder.encode("Secret123"));
    u.setRole(Role.USER);
    u.setEmailVerified(true);
    u.setActive(false);
    userRepository.save(u);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new LoginRequest(
                            TestEnterprise.CODE, "disabled@example.com", "Secret123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
  }

  @Test
  void softDeletedUser_login_returns403() throws Exception {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail("deletedlogin@example.com");
    u.setPasswordHash(passwordEncoder.encode("Secret123"));
    u.setRole(Role.USER);
    u.setEmailVerified(true);
    u.setDeleted(true);
    u.setActive(false);
    userRepository.save(u);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new LoginRequest(
                            TestEnterprise.CODE, "deletedlogin@example.com", "Secret123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_DELETED"));
  }

  @Test
  void adminSoftDelete_idempotent() throws Exception {
    User admin = new User();
    admin.setEnterpriseId(TestEnterprise.ID);
    admin.setEmail("admin@example.com");
    admin.setPasswordHash(passwordEncoder.encode("Admin123!"));
    admin.setRole(Role.ADMIN);
    admin.setEmailVerified(true);
    userRepository.save(admin);

    User victim = new User();
    victim.setEnterpriseId(TestEnterprise.ID);
    victim.setEmail("victim@example.com");
    victim.setPasswordHash(passwordEncoder.encode("Secret123"));
    victim.setRole(Role.USER);
    victim.setEmailVerified(true);
    userRepository.save(victim);
    String victimId = victim.getId();

    Session adminSession = login("admin@example.com", "Admin123!");

    mockMvc
        .perform(
            delete("/api/v1/users/" + victimId)
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete("/api/v1/users/" + victimId)
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/v1/users/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + adminSession.access()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void forgotPassword_unknownEmail_returns200_withoutMail() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(TestEnterprise.CODE, "ghost-reset@example.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    verify(javaMailSender, times(0)).send(anyMailMessage());
  }

  @Test
  void forgotPassword_disabledUser_returns403() throws Exception {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail("disabled-reset@example.com");
    u.setPasswordHash(passwordEncoder.encode("Secret123"));
    u.setRole(Role.USER);
    u.setEmailVerified(true);
    u.setActive(false);
    userRepository.save(u);

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(
                            TestEnterprise.CODE, "disabled-reset@example.com"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    verify(javaMailSender, times(0)).send(anyMailMessage());
  }

  @Test
  void forgotPassword_deletedUser_returns403() throws Exception {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail("deleted-reset@example.com");
    u.setPasswordHash(passwordEncoder.encode("Secret123"));
    u.setRole(Role.USER);
    u.setEmailVerified(true);
    u.setActive(false);
    u.setDeleted(true);
    u.setDeletedAt(java.time.Instant.now());
    userRepository.save(u);

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(
                            TestEnterprise.CODE, "deleted-reset@example.com"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_DELETED"));
    verify(javaMailSender, times(0)).send(anyMailMessage());
  }

  @Test
  void forgotPassword_unverifiedUser_returns403_withoutMail() throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(
                toJson(
                    new RegisterRequest(
                        TestEnterprise.CODE, "unverified-reset@example.com", "Secret123"))));
    verify(javaMailSender, times(1)).send(anyMailMessage());
    org.mockito.Mockito.reset(javaMailSender);
    stubMailSenderSuccess();

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(
                            TestEnterprise.CODE, "unverified-reset@example.com"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    verify(javaMailSender, times(0)).send(anyMailMessage());
  }

  @Test
  void resetPassword_flow_updatesPassword_andRevokesRefresh() throws Exception {
    registerVerifyLogin("reset-flow@example.com");
    Session session = login("reset-flow@example.com", "Secret123");

    mockMvc
        .perform(
            post("/api/v1/auth/forgot-password")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new ForgotPasswordRequest(TestEnterprise.CODE, "reset-flow@example.com"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String resetToken = extractVerificationToken(requireMailText(capturedMail(mailCap)));

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password-info")
                .contentType(jsonContentType())
                .content(toJson(new PasswordResetInfoRequest(resetToken))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("reset-flow@example.com"));

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(jsonContentType())
                .content(toJson(new ResetPasswordRequest(resetToken, "NewSecret99"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new LoginRequest(
                            TestEnterprise.CODE, "reset-flow@example.com", "Secret123"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        new LoginRequest(
                            TestEnterprise.CODE, "reset-flow@example.com", "NewSecret99"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(session.refresh()))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void resetPassword_invalidToken_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(jsonContentType())
                .content(toJson(new ResetPasswordRequest("invalid-reset-token", "NewSecret99"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void resetPassword_usedToken_returns400() throws Exception {
    registerVerifyLogin("reuse-reset@example.com");

    mockMvc.perform(
        post("/api/v1/auth/forgot-password")
            .contentType(jsonContentType())
            .content(
                toJson(new ForgotPasswordRequest(TestEnterprise.CODE, "reuse-reset@example.com"))));
    ArgumentCaptor<MimeMessage> mailCap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, mailCap);
    String resetToken = extractVerificationToken(requireMailText(capturedMail(mailCap)));

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(jsonContentType())
                .content(toJson(new ResetPasswordRequest(resetToken, "NewSecret99"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/reset-password")
                .contentType(jsonContentType())
                .content(toJson(new ResetPasswordRequest(resetToken, "AnotherSecret1"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
  }

  @Test
  void deleteUser_asUser_forbidden() throws Exception {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail("plain@example.com");
    u.setPasswordHash(passwordEncoder.encode("Secret123"));
    u.setRole(Role.USER);
    u.setEmailVerified(true);
    userRepository.save(u);
    String otherId = UUID.randomUUID().toString();

    Session s = login("plain@example.com", "Secret123");
    mockMvc
        .perform(delete("/api/v1/users/" + otherId).header("Authorization", "Bearer " + s.access()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  private void registerVerifyLogin(String email) throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(toJson(new RegisterRequest(TestEnterprise.CODE, email, "Secret123"))));
    ArgumentCaptor<MimeMessage> cap = mimeMessageCaptor();
    verifyMailSentAndCapture(javaMailSender, cap);
    String token = extractVerificationToken(requireMailText(capturedMail(cap)));
    mockMvc.perform(
        post("/api/v1/auth/verify-email")
            .contentType(jsonContentType())
            .content(toJson(new VerifyEmailRequest(token))));
    org.mockito.Mockito.reset(javaMailSender);
    stubMailSenderSuccess();
  }

  private Session login(String email, String password) throws Exception {
    MvcResult r =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, password))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode n = objectMapper.readTree(responseBody(r));
    return new Session(n.get("accessToken").asText(), n.get("refreshToken").asText());
  }

  private static String extractVerificationToken(String mailText) {
    Matcher matcher = TOKEN_PATTERN.matcher(mailText);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  @NonNull
  private MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private static String responseBody(@NonNull MvcResult result) {
    try {
      return Objects.requireNonNull(result.getResponse().getContentAsString());
    } catch (java.io.UnsupportedEncodingException ex) {
      throw new IllegalStateException("Cannot read response body", ex);
    }
  }

  @NonNull
  private static String requireMailText(@NonNull MimeMessage message) {
    try {
      Object content = message.getContent();
      if (content instanceof String text) {
        return text;
      }
      if (content instanceof Multipart multipart) {
        return findTextPart(multipart);
      }
      throw new IllegalStateException("Unsupported mail content type: " + content);
    } catch (Exception ex) {
      throw new IllegalStateException("Cannot read mail content", ex);
    }
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static MimeMessage anyMailMessage() {
    return any(MimeMessage.class);
  }

  /** Mockito forClass не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static ArgumentCaptor<MimeMessage> mimeMessageCaptor() {
    return ArgumentCaptor.forClass(MimeMessage.class);
  }

  @SuppressWarnings("null")
  private static void verifyMailSentAndCapture(
      JavaMailSender javaMailSender, @NonNull ArgumentCaptor<MimeMessage> mailCap) {
    verify(Objects.requireNonNull(javaMailSender), times(1)).send(mailCap.capture());
  }

  /** getValue() без @NonNull у Mockito — гарантуємо non-null для викликів requireMailText. */
  @NonNull
  private static MimeMessage capturedMail(@NonNull ArgumentCaptor<MimeMessage> mailCap) {
    return Objects.requireNonNull(mailCap.getValue(), "Expected captured MimeMessage");
  }

  @NonNull
  private static String findTextPart(@NonNull Multipart multipart) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      BodyPart part = multipart.getBodyPart(i);
      Object partContent = part.getContent();
      if (partContent instanceof String text
          && part.getContentType() != null
          && part.getContentType().toLowerCase().startsWith("text/plain")) {
        return text;
      }
      if (partContent instanceof Multipart nested) {
        return findTextPart(nested);
      }
    }
    throw new IOException("text/plain part not found");
  }

  private void stubMailSenderSuccess() {
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doNothing().when(javaMailSender).send(anyMailMessage());
  }

  private record Session(String access, String refresh) {}
}
