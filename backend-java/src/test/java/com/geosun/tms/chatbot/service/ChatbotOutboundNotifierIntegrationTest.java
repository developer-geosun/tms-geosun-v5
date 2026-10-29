package com.geosun.tms.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.client.TelegramApiClient;
import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.BotMessageLog;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.chatbot.repository.BotMessageLogRepository;
import com.geosun.tms.support.TestEnterprise;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Outbound USER_REGISTERED через Telegram stub. */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@ActiveProfiles("test")
@Transactional
class ChatbotOutboundNotifierIntegrationTest {

  @Autowired private ChatbotOutboundNotifier chatbotOutboundNotifier;
  @Autowired private UserRepository userRepository;
  @Autowired private BotIdentityRepository botIdentityRepository;
  @Autowired private BotMessageLogRepository botMessageLogRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;
  @MockBean private TelegramApiClient telegramApiClient;

  @DynamicPropertySource
  static void chatbotProps(DynamicPropertyRegistry registry) {
    registry.add("app.chatbot.enabled", () -> "true");
    registry.add("app.chatbot.telegram.enabled", () -> "true");
    registry.add("app.chatbot.telegram.bot-token", () -> "123456:TEST_TOKEN");
    registry.add("app.chatbot.telegram.secret-token", () -> "secret");
    registry.add("app.chatbot.telegram.bot-username", () -> "geosun_test_bot");
  }

  @BeforeEach
  void setUp() {
    doNothing().when(telegramApiClient).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void notifyUserRegistered_sendsOnce_andIdempotent() {
    User admin = saveAdmin();
    BotIdentity identity = new BotIdentity();
    identity.setUserId(Objects.requireNonNull(admin.getId()));
    identity.setChannel(ChatbotChannel.TELEGRAM);
    identity.setExternalUserId("777001");
    identity.setStatus(BotIdentityStatus.ACTIVE);
    identity.setLocale("ua");
    identity.setLinkedAt(Instant.now());
    botIdentityRepository.save(identity);

    String newUserId = Objects.requireNonNull(UUID.randomUUID().toString());
    String cardLink = Objects.requireNonNull("http://x/admin/users/" + newUserId);
    String text =
        ChatbotMessages.userRegistered(
            "new@example.com", Objects.requireNonNull(newUserId), cardLink);

    int first =
        chatbotOutboundNotifier.notifyUserRegistered(
            Objects.requireNonNull(admin.getId()),
            Objects.requireNonNull(newUserId),
            Objects.requireNonNull(text));
    int second =
        chatbotOutboundNotifier.notifyUserRegistered(
            Objects.requireNonNull(admin.getId()),
            Objects.requireNonNull(newUserId),
            Objects.requireNonNull(text));

    assertThat(first).isEqualTo(1);
    assertThat(second).isEqualTo(0);
    verify(telegramApiClient, times(1)).sendMessage(anyChatId(), anyMessageText(), anyBoolean());

    List<BotMessageLog> logs = botMessageLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getEventType()).isEqualTo(ChatbotOutboundNotifier.EVENT_USER_REGISTERED);
    assertThat(logs.get(0).getIdempotencyKey())
        .isEqualTo("USER_REGISTERED|" + newUserId + "|" + admin.getId() + "|TELEGRAM");
  }

  private User saveAdmin() {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setId(UUID.randomUUID().toString());
    user.setEmail("admin-outbound@" + UUID.randomUUID() + ".example.com");
    user.setPasswordHash(passwordEncoder.encode("Admin123!"));
    user.setRole(Role.ADMIN);
    user.setEmailVerified(true);
    return userRepository.save(Objects.requireNonNull(user));
  }

  /** Mockito anyString() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static String anyChatId() {
    return anyString();
  }

  /** Mockito anyString() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static String anyMessageText() {
    return anyString();
  }
}
