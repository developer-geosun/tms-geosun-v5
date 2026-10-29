package com.geosun.tms.chatbot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.profile.UserContactPhone;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.client.TelegramApiClient;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.support.TestEnterprise;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Інтеграційні сценарії Telegram verify (stub HTTP). */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatbotTelegramIntegrationTest {

  private static final String SECRET = "test-telegram-secret";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private BotIdentityRepository botIdentityRepository;
  @Autowired private UserContactPhoneRepository userContactPhoneRepository;
  @Autowired private RateLimitService rateLimitService;
  @Autowired private EnterpriseRepository enterpriseRepository;

  @MockBean private JavaMailSender javaMailSender;
  @MockBean private TelegramApiClient telegramApiClient;

  @DynamicPropertySource
  static void chatbotProps(DynamicPropertyRegistry registry) {
    registry.add("app.chatbot.enabled", () -> "true");
    registry.add("app.chatbot.telegram.enabled", () -> "true");
    registry.add("app.chatbot.telegram.bot-token", () -> "123456:TEST_TOKEN");
    registry.add("app.chatbot.telegram.secret-token", () -> SECRET);
    registry.add("app.chatbot.telegram.bot-username", () -> "geosun_test_bot");
  }

  @BeforeEach
  void setUp() {
    rateLimitService.resetForTests();
    doNothing().when(telegramApiClient).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void webhook_badSecret_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong")
                .content("{\"update_id\":1}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void linkCode_start_and_contact_verifiesPhone() throws Exception {
    User user = saveUser("tg-verify@example.com", "Secret123", Role.USER);
    String userId = Objects.requireNonNull(user.getId());
    Session session = login(Objects.requireNonNull(user.getEmail()), "Secret123");
    putProfileWithPhone(session.access(), "+380671112233");

    MvcResult codeResult =
        mockMvc
            .perform(
                post("/api/v1/users/me/bot-link-codes")
                    .header("Authorization", "Bearer " + session.access())
                    .contentType(jsonContentType())
                    .content("{\"channel\":\"TELEGRAM\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").isString())
            .andExpect(
                jsonPath("$.deepLink")
                    .value(Objects.requireNonNull(org.hamcrest.Matchers.containsString("start="))))
            .andReturn();

    String code = objectMapper.readTree(responseBody(codeResult)).path("code").asText();

    String chatId = "991122";
    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .content(Objects.requireNonNull(telegramTextUpdate(chatId, "/start " + code))))
        .andExpect(status().isOk());

    assertThat(
            botIdentityRepository
                .findByUserIdAndChannel(userId, ChatbotChannel.TELEGRAM)
                .orElseThrow()
                .getStatus())
        .isEqualTo(BotIdentityStatus.ACTIVE);

    mockMvc
        .perform(
            post("/api/v1/users/me/bot-link-codes")
                .header("Authorization", "Bearer " + session.access())
                .contentType(jsonContentType())
                .content("{\"channel\":\"TELEGRAM\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BOT_ALREADY_LINKED"));

    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .content(
                    Objects.requireNonNull(
                        telegramContactUpdate(chatId, "380671112233", Long.parseLong(chatId)))))
        .andExpect(status().isOk());

    UserContactPhone phone =
        userContactPhoneRepository.findByUserIdAndPhone(userId, "+380671112233").orElseThrow();
    assertThat(phone.isPhoneVerified()).isTrue();
    assertThat(phone.getPhoneVerifiedVia()).isEqualTo("TELEGRAM");
    assertThat(phone.isTelegram()).isTrue();

    verify(telegramApiClient, atLeastOnce())
        .sendMessage(anyChatId(), anyMessageText(), anyBoolean());

    mockMvc
        .perform(
            get("/api/v1/users/me/bot-identities")
                .header("Authorization", "Bearer " + session.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].channel").value("TELEGRAM"))
        .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.items[0].phoneVerified").value(true));

    mockMvc
        .perform(
            delete("/api/v1/users/me/bot-identities/TELEGRAM")
                .header("Authorization", "Bearer " + session.access()))
        .andExpect(status().isNoContent());

    assertThat(
            botIdentityRepository
                .findByUserIdAndChannel(userId, ChatbotChannel.TELEGRAM)
                .orElseThrow()
                .getStatus())
        .isEqualTo(BotIdentityStatus.REVOKED);
    // Відв'язка не скидає verified
    assertThat(
            userContactPhoneRepository
                .findByUserIdAndPhone(userId, "+380671112233")
                .orElseThrow()
                .isPhoneVerified())
        .isTrue();
  }

  @Test
  void contact_mismatch_keepsIdentity_withoutVerified() throws Exception {
    User user = saveUser("tg-mismatch@example.com", "Secret123", Role.USER);
    String userId = Objects.requireNonNull(user.getId());
    Session session = login(Objects.requireNonNull(user.getEmail()), "Secret123");
    putProfileWithPhone(session.access(), "+380671112233");

    String code =
        objectMapper
            .readTree(
                responseBody(
                    mockMvc
                        .perform(
                            post("/api/v1/users/me/bot-link-codes")
                                .header("Authorization", "Bearer " + session.access())
                                .contentType(jsonContentType())
                                .content("{\"channel\":\"TELEGRAM\"}"))
                        .andExpect(status().isOk())
                        .andReturn()))
            .path("code")
            .asText();

    String chatId = "554433";
    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .content(Objects.requireNonNull(telegramTextUpdate(chatId, "/start " + code))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .content(
                    Objects.requireNonNull(
                        telegramContactUpdate(chatId, "380999999999", Long.parseLong(chatId)))))
        .andExpect(status().isOk());

    assertThat(
            botIdentityRepository
                .findByUserIdAndChannel(userId, ChatbotChannel.TELEGRAM)
                .orElseThrow()
                .getStatus())
        .isEqualTo(BotIdentityStatus.ACTIVE);
    assertThat(
            userContactPhoneRepository
                .findByUserIdAndPhone(userId, "+380671112233")
                .orElseThrow()
                .isPhoneVerified())
        .isFalse();
  }

  @Test
  void adminStatus_managerOk_userForbidden() throws Exception {
    User manager = saveUser("tg-mgr@example.com", "Secret123", Role.MANAGER);
    User plain = saveUser("tg-user@example.com", "Secret123", Role.USER);
    Session mgr = login(Objects.requireNonNull(manager.getEmail()), "Secret123");
    Session usr = login(Objects.requireNonNull(plain.getEmail()), "Secret123");

    mockMvc
        .perform(
            get("/api/v1/admin/chatbots/status").header("Authorization", "Bearer " + mgr.access()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleEnabled").value(true))
        .andExpect(jsonPath("$.channels[0].channel").value("TELEGRAM"));

    mockMvc
        .perform(
            get("/api/v1/admin/chatbots/status").header("Authorization", "Bearer " + usr.access()))
        .andExpect(status().isForbidden());
  }

  @Test
  void link_rejectedWhenChatIsBoundToUserOfAnotherEnterprise() throws Exception {
    User first = saveUser("tg-ent-a@example.com", "Secret123", Role.USER);
    String firstId = Objects.requireNonNull(first.getId());
    String chatId = "778899";
    String firstCode =
        requestLinkCode(login(Objects.requireNonNull(first.getEmail()), "Secret123"));
    sendStart(chatId, firstCode);
    assertThat(
            botIdentityRepository
                .findByUserIdAndChannel(firstId, ChatbotChannel.TELEGRAM)
                .orElseThrow()
                .getStatus())
        .isEqualTo(BotIdentityStatus.ACTIVE);

    // Друге підприємство: користувач намагається прив'язати той самий чат
    Enterprise other = new Enterprise();
    other.setCode("tg-other-ent");
    other.setName("Other");
    other.setActive(true);
    String otherEnterpriseId =
        Objects.requireNonNull(enterpriseRepository.saveAndFlush(other).getId());
    User second = saveUser("tg-ent-b@example.com", "Secret123", Role.USER);
    second.setEnterpriseId(otherEnterpriseId);
    userRepository.saveAndFlush(second);
    String secondCode =
        requestLinkCode(
            loginIn("tg-other-ent", Objects.requireNonNull(second.getEmail()), "Secret123"));
    sendStart(chatId, secondCode);

    assertThat(
            botIdentityRepository
                .findByUserIdAndChannel(firstId, ChatbotChannel.TELEGRAM)
                .orElseThrow()
                .getStatus())
        .isEqualTo(BotIdentityStatus.ACTIVE);
    assertThat(
            botIdentityRepository.findByUserIdAndChannel(
                Objects.requireNonNull(second.getId()), ChatbotChannel.TELEGRAM))
        .isEmpty();
  }

  private String requestLinkCode(Session session) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/users/me/bot-link-codes")
                    .header("Authorization", "Bearer " + session.access())
                    .contentType(jsonContentType())
                    .content("{\"channel\":\"TELEGRAM\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(responseBody(result)).path("code").asText();
  }

  private void sendStart(String chatId, String code) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/webhooks/telegram")
                .contentType(jsonContentType())
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET)
                .content(Objects.requireNonNull(telegramTextUpdate(chatId, "/start " + code))))
        .andExpect(status().isOk());
  }

  private Session loginIn(String enterpriseCode, String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(
                        Objects.requireNonNull(
                            objectMapper.writeValueAsString(
                                new LoginRequest(enterpriseCode, email, password)))))
            .andExpect(status().isOk())
            .andReturn();
    return new Session(objectMapper.readTree(responseBody(result)).path("accessToken").asText());
  }

  private void putProfileWithPhone(String accessToken, String phone) throws Exception {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("lastName", "Тест");
    body.put("firstName", "Юзер");
    body.putNull("patronymic");
    body.put("personType", "INDIVIDUAL");
    body.putNull("legalEntityEdrpou");
    ArrayNode channels = body.putArray("preferredChannels");
    channels.add("EMAIL");
    channels.add("PHONE");
    ArrayNode phones = body.putArray("phones");
    ObjectNode p = phones.addObject();
    p.put("phone", phone);
    p.put("primary", true);
    p.put("telegram", true);
    p.put("whatsapp", false);
    p.put("viber", false);

    mockMvc
        .perform(
            put("/api/v1/users/me/profile")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(jsonContentType())
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phones[0].verified").value(false));
  }

  private static String telegramTextUpdate(String chatId, String text) {
    return """
    {"update_id":1,"message":{"message_id":1,"chat":{"id":%s,"type":"private"},"text":"%s"}}
    """
        .formatted(chatId, text);
  }

  private static String telegramContactUpdate(String chatId, String phone, long contactUserId) {
    return
"""
{"update_id":2,"message":{"message_id":2,"chat":{"id":%s,"type":"private"},"contact":{"phone_number":"%s","user_id":%d}}}
"""
        .formatted(chatId, phone, contactUserId);
  }

  private User saveUser(String email, String password, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setId(UUID.randomUUID().toString());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setActive(true);
    user.setEmailVerified(true);
    user.setDeleted(false);
    return userRepository.save(Objects.requireNonNull(user));
  }

  private Session login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(
                        Objects.requireNonNull(
                            objectMapper.writeValueAsString(
                                new LoginRequest(TestEnterprise.CODE, email, password)))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode root = objectMapper.readTree(responseBody(result));
    return new Session(root.path("accessToken").asText());
  }

  @NonNull
  private static MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private static String responseBody(@NonNull MvcResult result) throws Exception {
    return Objects.requireNonNull(result.getResponse().getContentAsString());
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

  private record Session(String access) {}
}
