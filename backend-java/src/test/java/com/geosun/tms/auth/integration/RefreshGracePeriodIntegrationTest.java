package com.geosun.tms.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grace-period для конкурентного reuse refresh-токена (декілька вкладок).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.jwt.refresh-reuse-grace-seconds=30")
@Transactional
class RefreshGracePeriodIntegrationTest {
  private static final Pattern TOKEN_PATTERN = Pattern.compile("[?&]token=([^\\s\"'<>]+)");

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RateLimitService rateLimitService;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    rateLimitService.resetForTests();
    org.mockito.Mockito.reset(javaMailSender);
    stubMailSenderSuccess();
  }

  @Test
  void refresh_reuseWithinGrace_rotatesWithoutRevokingAllSessions() throws Exception {
    registerVerifyLogin("grace@example.com");
    Session s0 = login("grace@example.com", "Secret123");

    MvcResult firstRotation =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(jsonContentType())
                    .content(toJson(new RefreshRequest(s0.refresh()))))
            .andExpect(status().isOk())
            .andReturn();
    String refresh1 =
        objectMapper.readTree(responseBody(firstRotation)).get("refreshToken").asText();

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(s0.refresh()))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(jsonContentType())
                .content(toJson(new RefreshRequest(refresh1))))
        .andExpect(status().isOk());
  }

  private void registerVerifyLogin(String email) throws Exception {
    mockMvc.perform(
        post("/api/v1/auth/register")
            .contentType(jsonContentType())
            .content(toJson(new RegisterRequest(TestEnterprise.CODE, email, "Secret123"))));
    ArgumentCaptor<MimeMessage> cap = mimeMessageCaptor_();
    verifyMailSentAndCapture_(javaMailSender, cap);
    String token = extractVerificationToken(requireMailText(capturedMail_(cap)));
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

  @NonNull
  private static String findTextPart(@NonNull Multipart multipart) throws Exception {
    for (int i = 0; i < multipart.getCount(); i++) {
      jakarta.mail.BodyPart part = multipart.getBodyPart(i);
      Object partContent = part.getContent();
      if (partContent instanceof String text
          && part.getContentType() != null
          && part.getContentType().toLowerCase().startsWith("text/plain")) {
        return text;
      }
      if (partContent instanceof MimeMultipart nested) {
        return findTextPart(nested);
      }
    }
    throw new IOException("text/plain part not found");
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static MimeMessage anyMailMessage_() {
    return any(MimeMessage.class);
  }

  /** Mockito forClass не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static ArgumentCaptor<MimeMessage> mimeMessageCaptor_() {
    return ArgumentCaptor.forClass(MimeMessage.class);
  }

  @SuppressWarnings("null")
  private static void verifyMailSentAndCapture_(
      JavaMailSender javaMailSender, @NonNull ArgumentCaptor<MimeMessage> mailCap) {
    verify(Objects.requireNonNull(javaMailSender), times(1)).send(mailCap.capture());
  }

  @NonNull
  private static MimeMessage capturedMail_(@NonNull ArgumentCaptor<MimeMessage> mailCap) {
    return Objects.requireNonNull(mailCap.getValue(), "Expected captured MimeMessage");
  }

  private void stubMailSenderSuccess() {
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doNothing().when(javaMailSender).send(anyMailMessage_());
  }

  private record Session(String access, String refresh) {}
}
