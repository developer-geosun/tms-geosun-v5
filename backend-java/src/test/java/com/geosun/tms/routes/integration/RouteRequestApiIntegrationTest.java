package com.geosun.tms.routes.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RouteRequestApiIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doNothing().when(javaMailSender).send(anyMailMessage());
  }

  @Test
  void userCanCreateAndReadOwnRouteRequest() throws Exception {
    User user = createUser("rq-user@example.com", "Secret123", Role.USER);
    String access = login(user.getEmail(), "Secret123");
    String routeId = createRoute(access, "RQ owner route");

    MvcResult requestResult =
        mockMvc
            .perform(
                post("/api/v1/route-requests")
                    .header("Authorization", bearer(access))
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            Map.of(
                                "routeId",
                                routeId,
                                "preferredStartDate",
                                "2026-05-12",
                                "comment",
                                "Need reefer",
                                "cargo",
                                Map.of("type", "food", "weightKg", 18000.0, "volumeM3", 78.0)))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.routeId").value(routeId))
            .andExpect(jsonPath("$.status").value("NEW"))
            .andReturn();

    long requestId =
        objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(get("/api/v1/route-requests/my").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(requestId));

    mockMvc
        .perform(
            get("/api/v1/route-requests/my/" + requestId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(requestId))
        .andExpect(jsonPath("$.route.points.length()").value(2))
        .andExpect(jsonPath("$.route.lockedByRequest").value(true))
        .andExpect(jsonPath("$.countryDistances.length()").value(0));

    User admin = createUser("rq-admin-breakdown@example.com", "Secret123", Role.ADMIN);
    String adminAccess = login(admin.getEmail(), "Secret123");

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/country-breakdown")
                .header("Authorization", bearer(adminAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(requestId))
        .andExpect(jsonPath("$.countryDistances.length()").value(2))
        .andExpect(jsonPath("$.countryDistances[0].countryCode").value("UA"))
        .andExpect(jsonPath("$.countryDistances[0].alongRouteOrder").value(0))
        .andExpect(jsonPath("$.countryDistances[0].distanceMeters").value(60250))
        .andExpect(jsonPath("$.countryDistances[1].countryCode").value("PL"))
        .andExpect(jsonPath("$.countryDistances[1].alongRouteOrder").value(1))
        .andExpect(jsonPath("$.countryDistances[1].distanceMeters").value(60250));
  }

  @Test
  void userCannotCreateRequestForForeignRoute() throws Exception {
    User owner = createUser("rq-owner@example.com", "Secret123", Role.USER);
    User intruder = createUser("rq-intruder@example.com", "Secret123", Role.USER);
    String ownerAccess = login(owner.getEmail(), "Secret123");
    String intruderAccess = login(intruder.getEmail(), "Secret123");
    String routeId = createRoute(ownerAccess, "Private route");

    Map<String, Object> requestPayload = new HashMap<>();
    requestPayload.put("routeId", routeId);
    requestPayload.put("preferredStartDate", "");
    requestPayload.put("comment", "");
    requestPayload.put("cargo", null);

    mockMvc
        .perform(
            post("/api/v1/route-requests")
                .header("Authorization", bearer(intruderAccess))
                .contentType(jsonContentType())
                .content(toJson(requestPayload)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void adminAndManagerCanReadAdminQueueButUserCannot() throws Exception {
    User user = createUser("rq-user2@example.com", "Secret123", Role.USER);
    User admin = createUser("rq-admin@example.com", "Secret123", Role.ADMIN);
    User manager = createUser("rq-manager@example.com", "Secret123", Role.MANAGER);

    String userAccess = login(user.getEmail(), "Secret123");
    String adminAccess = login(admin.getEmail(), "Secret123");
    String managerAccess = login(manager.getEmail(), "Secret123");

    String routeId = createRoute(userAccess, "Queue route");
    Map<String, Object> requestPayload = new HashMap<>();
    requestPayload.put("routeId", routeId);
    requestPayload.put("preferredStartDate", "");
    requestPayload.put("comment", "q");
    requestPayload.put("cargo", null);

    MvcResult requestResult =
        mockMvc
            .perform(
                post("/api/v1/route-requests")
                    .header("Authorization", bearer(userAccess))
                    .contentType(jsonContentType())
                    .content(toJson(requestPayload)))
            .andExpect(status().isCreated())
            .andReturn();
    long requestId =
        objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(get("/api/v1/admin/route-requests").header("Authorization", bearer(adminAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(requestId));

    mockMvc
        .perform(
            get("/api/v1/admin/route-requests/" + requestId)
                .header("Authorization", bearer(managerAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(requestId));

    mockMvc
        .perform(get("/api/v1/admin/route-requests").header("Authorization", bearer(userAccess)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void adminCanCreateAndSendQuoteWithIdempotencyAndManagerCanReadHistory() throws Exception {
    User user = createUser("quote-user@example.com", "Secret123", Role.USER);
    User admin = createUser("quote-admin@example.com", "Secret123", Role.ADMIN);
    User manager = createUser("quote-manager@example.com", "Secret123", Role.MANAGER);

    String userAccess = login(user.getEmail(), "Secret123");
    String adminAccess = login(admin.getEmail(), "Secret123");
    String managerAccess = login(manager.getEmail(), "Secret123");

    String routeId = createRoute(userAccess, "Quote route");
    MvcResult requestResult =
        mockMvc
            .perform(
                post("/api/v1/route-requests")
                    .header("Authorization", bearer(userAccess))
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            Map.of(
                                "routeId",
                                routeId,
                                "preferredStartDate",
                                "2026-06-10",
                                "comment",
                                "Need quote",
                                "cargo",
                                Map.of("type", "steel", "weightKg", 15000.0, "volumeM3", 52.0)))))
            .andExpect(status().isCreated())
            .andReturn();
    long requestId =
        objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asLong();

    String createIdempotencyKey = "create-quote-key-1";
    MvcResult createDraft =
        mockMvc
            .perform(
                post("/api/v1/admin/route-requests/" + requestId + "/quotes")
                    .header("Authorization", bearer(adminAccess))
                    .header("Idempotency-Key", createIdempotencyKey)
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            Map.of(
                                "currency", "EUR",
                                "totalAmount", 3200.50,
                                "transitDaysMin", 2,
                                "transitDaysMax", 4,
                                "validUntil", "2026-06-30",
                                "publicNote", "Draft offer",
                                "internalNote", "Internal note"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();
    String quoteId =
        objectMapper.readTree(createDraft.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/quotes")
                .header("Authorization", bearer(adminAccess))
                .header("Idempotency-Key", createIdempotencyKey)
                .contentType(jsonContentType())
                .content(
                    toJson(
                        Map.of(
                            "currency", "EUR",
                            "totalAmount", 3200.50,
                            "transitDaysMin", 2,
                            "transitDaysMax", 4,
                            "validUntil", "2026-06-30",
                            "publicNote", "Draft offer",
                            "internalNote", "Internal note"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(quoteId));

    String sendIdempotencyKey = "send-quote-key-1";
    mockMvc
        .perform(
            post("/api/v1/admin/quotes/" + quoteId + "/send")
                .header("Authorization", bearer(adminAccess))
                .header("Idempotency-Key", sendIdempotencyKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(quoteId))
        .andExpect(jsonPath("$.status").value("SENT"));

    mockMvc
        .perform(
            post("/api/v1/admin/quotes/" + quoteId + "/send")
                .header("Authorization", bearer(adminAccess))
                .header("Idempotency-Key", sendIdempotencyKey))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(quoteId))
        .andExpect(jsonPath("$.status").value("SENT"));

    mockMvc
        .perform(
            get("/api/v1/admin/route-requests/" + requestId + "/quotes")
                .header("Authorization", bearer(managerAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(quoteId));

    mockMvc
        .perform(
            get("/api/v1/route-requests/my/" + requestId)
                .header("Authorization", bearer(userAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentQuote.id").value(quoteId))
        .andExpect(jsonPath("$.currentQuote.status").value("SENT"));

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/quotes")
                .header("Authorization", bearer(managerAccess))
                .header("Idempotency-Key", "manager-create-key")
                .contentType(jsonContentType())
                .content(
                    toJson(
                        Map.of(
                            "currency",
                            "EUR",
                            "totalAmount",
                            3000.0,
                            "transitDaysMin",
                            2,
                            "transitDaysMax",
                            3))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  private String createRoute(String access, String title) throws Exception {
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonContentType())
                    .content(toJson(routePayload(title))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(saveResult.getResponse().getContentAsString());
    return json.get("id").asText();
  }

  private User createUser(String email, String password, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setEmailVerified(true);
    user.setActive(true);
    return userRepository.save(user);
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

  private static String bearer(String access) {
    return "Bearer " + access;
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
        "phase2",
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
    return any(MimeMessage.class);
  }
}
