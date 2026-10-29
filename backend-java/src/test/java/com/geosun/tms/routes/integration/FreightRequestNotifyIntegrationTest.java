package com.geosun.tms.routes.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.config.FreightRequestCreatedNotifyProperties;
import com.geosun.tms.auth.domain.profile.PersonType;
import com.geosun.tms.auth.domain.profile.UserProfile;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.EmailVerificationTokenRepository;
import com.geosun.tms.auth.repository.PasswordResetTokenRepository;
import com.geosun.tms.auth.repository.RefreshTokenRepository;
import com.geosun.tms.auth.repository.UserContactPhoneRepository;
import com.geosun.tms.auth.repository.UserProfileRepository;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.chatbot.client.TelegramApiClient;
import com.geosun.tms.chatbot.domain.BotIdentity;
import com.geosun.tms.chatbot.domain.BotIdentityStatus;
import com.geosun.tms.chatbot.domain.BotMessageLog;
import com.geosun.tms.chatbot.domain.ChatbotChannel;
import com.geosun.tms.chatbot.repository.BotIdentityRepository;
import com.geosun.tms.chatbot.repository.BotLinkCodeRepository;
import com.geosun.tms.chatbot.repository.BotMessageLogRepository;
import com.geosun.tms.chatbot.service.ChatbotOutboundNotifier;
import com.geosun.tms.enterprise.domain.Enterprise;
import com.geosun.tms.enterprise.repository.EnterpriseRepository;
import com.geosun.tms.routes.repository.RouteCountryDistanceRepository;
import com.geosun.tms.routes.repository.RouteRepository;
import com.geosun.tms.routes.repository.RouteRequestRepository;
import com.geosun.tms.routes.repository.RouteRequestStatusHistoryRepository;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Сповіщення ADMIN після створення заявки на фрахт (без {@code @Transactional}, щоб спрацював
 * afterCommit).
 */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FreightRequestNotifyIntegrationTest {

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
  @Autowired private RouteRequestStatusHistoryRepository routeRequestStatusHistoryRepository;
  @Autowired private RouteCountryDistanceRepository routeCountryDistanceRepository;
  @Autowired private RouteRequestRepository routeRequestRepository;
  @Autowired private RouteRepository routeRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private FreightRequestCreatedNotifyProperties notifyProperties;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EnterpriseRepository enterpriseRepository;

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
    notifyProperties.setEnabled(true);
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doNothing().when(javaMailSender).send(anyMailMessage());
    doNothing().when(telegramApiClient).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
    clearData();
  }

  @AfterEach
  void tearDown() {
    clearData();
    notifyProperties.setEnabled(true);
  }

  @Test
  void createRouteRequest_notifiesAdminWithMessengersAndTelegram() throws Exception {
    User admin = saveUser("fr-admin@example.com", "Admin123!", Role.ADMIN);
    saveMessengersProfile(Objects.requireNonNull(admin.getId()));
    saveTelegramIdentity(Objects.requireNonNull(admin.getId()), "900001", "ua");

    User owner = saveUser("fr-owner@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "Notify route");

    long requestId = createRouteRequest(access, routeId);

    verify(telegramApiClient, times(1))
        .sendMessage(eqChatId("900001"), containsText("новий запит"), anyBoolean());
    verify(telegramApiClient, times(1))
        .sendMessage(eqChatId("900001"), containsText("requestId=" + requestId), anyBoolean());

    assertThat(countFreightLogs()).isEqualTo(1);
    List<BotMessageLog> logs =
        botMessageLogRepository.findAll().stream()
            .filter(
                l -> ChatbotOutboundNotifier.EVENT_FREIGHT_REQUEST_CREATED.equals(l.getEventType()))
            .toList();
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getIdempotencyKey())
        .isEqualTo("FREIGHT_REQUEST_CREATED|" + requestId + "|" + admin.getId() + "|TELEGRAM");
  }

  @Test
  void createRouteRequest_skipsAuthorEvenIfAdmin() throws Exception {
    User adminAuthor = saveUser("fr-author-admin@example.com", "Admin123!", Role.ADMIN);
    saveMessengersProfile(Objects.requireNonNull(adminAuthor.getId()));
    saveTelegramIdentity(Objects.requireNonNull(adminAuthor.getId()), "900002", "ua");

    // Для USER-only ендпоінту потрібна роль USER у available (або активна USER).
    adminAuthor.replaceAvailableRoles(
        Objects.requireNonNull(java.util.EnumSet.of(Role.ADMIN, Role.USER)));
    adminAuthor.setRole(Role.USER);
    userRepository.save(Objects.requireNonNull(adminAuthor));

    String access = login(Objects.requireNonNull(adminAuthor.getEmail()), "Admin123!");
    String routeId = createRoute(access, "Author admin route");
    createRouteRequest(access, routeId);

    verify(telegramApiClient, never()).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void createRouteRequest_flagDisabled_skipsMessenger() throws Exception {
    User admin = saveUser("fr-admin-off@example.com", "Admin123!", Role.ADMIN);
    saveMessengersProfile(Objects.requireNonNull(admin.getId()));
    saveTelegramIdentity(Objects.requireNonNull(admin.getId()), "900003", "ua");
    notifyProperties.setEnabled(false);

    User owner = saveUser("fr-owner-off@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "Flag off route");
    createRouteRequest(access, routeId);

    verify(telegramApiClient, never()).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void createRouteRequest_withoutMessengersFlag_skips() throws Exception {
    User admin = saveUser("fr-admin-email@example.com", "Admin123!", Role.ADMIN);
    UserProfile profile = new UserProfile();
    profile.setUserId(Objects.requireNonNull(admin.getId()));
    profile.setLastName("Адмін");
    profile.setFirstName("Email");
    profile.setPersonType(PersonType.INDIVIDUAL);
    profile.setContactViaEmail(true);
    profile.setContactViaPhone(false);
    profile.setContactViaMessengers(false);
    userProfileRepository.save(profile);
    saveTelegramIdentity(Objects.requireNonNull(admin.getId()), "900004", "ua");

    User owner = saveUser("fr-owner-email@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "No messengers route");
    createRouteRequest(access, routeId);

    verify(telegramApiClient, never()).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void createTwoRequests_sendsTwoMessages() throws Exception {
    User admin = saveUser("fr-admin-two@example.com", "Admin123!", Role.ADMIN);
    saveMessengersProfile(Objects.requireNonNull(admin.getId()));
    saveTelegramIdentity(Objects.requireNonNull(admin.getId()), "900005", "en");

    User owner = saveUser("fr-owner-two@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "Two requests route");

    long firstId = createRouteRequest(access, routeId);
    long secondId = createRouteRequest(access, routeId);

    assertThat(firstId).isNotEqualTo(secondId);
    verify(telegramApiClient, times(2))
        .sendMessage(eqChatId("900005"), containsText("new freight"), anyBoolean());
    assertThat(countFreightLogs()).isEqualTo(2);
  }

  @Test
  void createRouteRequest_managerWithoutAdmin_notNotified() throws Exception {
    User manager = saveUser("fr-manager@example.com", "Manager123!", Role.MANAGER);
    saveMessengersProfile(Objects.requireNonNull(manager.getId()));
    saveTelegramIdentity(Objects.requireNonNull(manager.getId()), "900006", "ua");

    User owner = saveUser("fr-owner-mgr@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "Manager skip route");
    createRouteRequest(access, routeId);

    verify(telegramApiClient, never()).sendMessage(anyChatId(), anyMessageText(), anyBoolean());
  }

  @Test
  void createRouteRequest_adminOfAnotherEnterprise_notNotified() throws Exception {
    Enterprise other = new Enterprise();
    other.setCode("fr-other-ent");
    other.setName("Other");
    other.setActive(true);
    String otherId = Objects.requireNonNull(enterpriseRepository.saveAndFlush(other).getId());
    try {
      User foreignAdmin = saveUser("fr-foreign-admin@example.com", "Admin123!", Role.ADMIN);
      foreignAdmin.setEnterpriseId(otherId);
      userRepository.saveAndFlush(foreignAdmin);
      saveMessengersProfile(Objects.requireNonNull(foreignAdmin.getId()));
      saveTelegramIdentity(Objects.requireNonNull(foreignAdmin.getId()), "900099", "ua");

      User ownAdmin = saveUser("fr-own-admin@example.com", "Admin123!", Role.ADMIN);
      saveMessengersProfile(Objects.requireNonNull(ownAdmin.getId()));
      saveTelegramIdentity(Objects.requireNonNull(ownAdmin.getId()), "900098", "ua");

      User owner = saveUser("fr-owner-scope@example.com", "Secret123", Role.USER);
      String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
      String routeId = createRoute(access, "Scope route");
      createRouteRequest(access, routeId);

      verify(telegramApiClient, times(1))
          .sendMessage(eqChatId("900098"), anyMessageText(), anyBoolean());
      verify(telegramApiClient, never())
          .sendMessage(eqChatId("900099"), anyMessageText(), anyBoolean());
    } finally {
      clearData();
      enterpriseRepository.deleteById(otherId);
    }
  }

  @Test
  void createRouteRequest_sendFail_stillCreatesRequest() throws Exception {
    User admin = saveUser("fr-admin-fail@example.com", "Admin123!", Role.ADMIN);
    saveMessengersProfile(Objects.requireNonNull(admin.getId()));
    saveTelegramIdentity(Objects.requireNonNull(admin.getId()), "900007", "ua");
    org.mockito.Mockito.doThrow(new RuntimeException("telegram down"))
        .when(telegramApiClient)
        .sendMessage(anyChatId(), anyMessageText(), anyBoolean());

    User owner = saveUser("fr-owner-fail@example.com", "Secret123", Role.USER);
    String access = login(Objects.requireNonNull(owner.getEmail()), "Secret123");
    String routeId = createRoute(access, "Fail send route");
    long requestId = createRouteRequest(access, routeId);

    assertThat(routeRequestRepository.findById(requestId)).isPresent();
    verify(telegramApiClient, atLeastOnce())
        .sendMessage(anyChatId(), anyMessageText(), anyBoolean());
    assertThat(countFreightLogs()).isEqualTo(1);
    assertThat(
            botMessageLogRepository.findAll().stream()
                .filter(
                    l ->
                        ChatbotOutboundNotifier.EVENT_FREIGHT_REQUEST_CREATED.equals(
                            l.getEventType()))
                .findFirst()
                .orElseThrow()
                .getStatus()
                .name())
        .isEqualTo("FAILED");
  }

  private long countFreightLogs() {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from bot_message_log where event_type = ?",
            Long.class,
            ChatbotOutboundNotifier.EVENT_FREIGHT_REQUEST_CREATED);
    return count == null ? 0L : count;
  }

  private void clearData() {
    botMessageLogRepository.deleteAll();
    botLinkCodeRepository.deleteAll();
    botIdentityRepository.deleteAll();
    routeRequestStatusHistoryRepository.deleteAll();
    routeCountryDistanceRepository.deleteAll();
    routeRequestRepository.deleteAll();
    routeRepository.deleteAll();
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
    user.ensureRoleAssigned(Objects.requireNonNull(role));
    user.setEmailVerified(true);
    user.setActive(true);
    return userRepository.save(Objects.requireNonNull(user));
  }

  private void saveMessengersProfile(@NonNull String userId) {
    UserProfile profile = new UserProfile();
    profile.setUserId(userId);
    profile.setLastName("Адмін");
    profile.setFirstName("Тест");
    profile.setPersonType(PersonType.INDIVIDUAL);
    profile.setContactViaEmail(false);
    profile.setContactViaPhone(false);
    profile.setContactViaMessengers(true);
    userProfileRepository.save(profile);
  }

  private void saveTelegramIdentity(
      @NonNull String userId, @NonNull String chatId, @NonNull String locale) {
    BotIdentity identity = new BotIdentity();
    identity.setUserId(userId);
    identity.setChannel(ChatbotChannel.TELEGRAM);
    identity.setExternalUserId(chatId);
    identity.setStatus(BotIdentityStatus.ACTIVE);
    identity.setLocale(locale);
    identity.setLinkedAt(Instant.now());
    botIdentityRepository.save(identity);
  }

  private String createRoute(String access, String title) throws Exception {
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", "Bearer " + access)
                    .contentType(jsonContentType())
                    .content(toJson(routePayload(title))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(saveResult.getResponse().getContentAsString());
    return json.get("id").asText();
  }

  private long createRouteRequest(String access, String routeId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/route-requests")
                    .header("Authorization", "Bearer " + access)
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            Map.of(
                                "routeId",
                                routeId,
                                "preferredStartDate",
                                "2026-05-12",
                                "comment",
                                "Notify test",
                                "cargo",
                                Map.of("type", "food", "weightKg", 1000.0, "volumeM3", 10.0)))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private String login(String email, String password) throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, password))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
    return json.get("accessToken").asText();
  }

  private Map<String, Object> routePayload(String title) {
    Map<String, Object> startPoint = new HashMap<>();
    startPoint.put("order", 1);
    startPoint.put("type", "START");
    startPoint.put("address", "Kyiv");
    startPoint.put("lat", 50.4501);
    startPoint.put("lng", 30.5234);
    startPoint.put("country", "UA");
    startPoint.put("isBorder", false);
    startPoint.put("segmentDistanceKmToNext", 120.5);
    startPoint.put("operations", List.of("LOADING"));

    Map<String, Object> finishPoint = new HashMap<>();
    finishPoint.put("order", 2);
    finishPoint.put("type", "FINISH");
    finishPoint.put("address", "Warsaw");
    finishPoint.put("lat", 52.2297);
    finishPoint.put("lng", 21.0122);
    finishPoint.put("country", "PL");
    finishPoint.put("isBorder", false);
    finishPoint.put("segmentDistanceKmToNext", null);
    finishPoint.put("operations", List.of("UNLOADING"));

    return Map.of(
        "title",
        title,
        "routingProfile",
        "truck",
        "routingMode",
        "fast",
        "routePolyline",
        "BFoz5xJ67i1B1B7PzIhaxL7Y",
        "distanceKm",
        812.34,
        "durationMin",
        742,
        "routeComment",
        "notify",
        "points",
        List.of(startPoint, finishPoint),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  @NonNull
  private String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static MimeMessage anyMailMessage() {
    return org.mockito.ArgumentMatchers.any(MimeMessage.class);
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

  /** Mockito eq() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static String eqChatId(@NonNull String chatId) {
    return eq(chatId);
  }

  /** Mockito contains() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static String containsText(@NonNull String fragment) {
    return contains(fragment);
  }
}
