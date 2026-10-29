package com.geosun.tms.routes.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RouteApiIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void saveAndReadMyRoute_success() throws Exception {
    User user = createUser("routes-owner@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    String body = toJson(routePayload("Kyiv -> Warsaw"));
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Kyiv -> Warsaw"))
            .andExpect(jsonPath("$.points.length()").value(2))
            .andReturn();

    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/routes/my").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(routeId));

    mockMvc
        .perform(get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(routeId))
        .andExpect(jsonPath("$.points.length()").value(2));
  }

  @Test
  void saveRoute_responseHasCreatedAtEqualToUpdatedAt() throws Exception {
    User user = createUser("routes-created-updated-eq@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("New route timestamps"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode json = objectMapper.readTree(saveResult.getResponse().getContentAsString());
    assertThat(json.hasNonNull("createdAt")).isTrue();
    assertThat(json.hasNonNull("updatedAt")).isTrue();
    assertThat(json.get("updatedAt").asText()).isEqualTo(json.get("createdAt").asText());

    String routeId = json.get("id").asText();
    MvcResult listResult =
        mockMvc
            .perform(get("/api/v1/routes/my").header("Authorization", bearer(access)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
    JsonNode summary = null;
    for (JsonNode node : list) {
      if (routeId.equals(node.get("id").asText())) {
        summary = node;
        break;
      }
    }
    assertThat(summary).isNotNull();
    JsonNode row = Objects.requireNonNull(summary);
    assertThat(row.get("updatedAt").asText()).isEqualTo(row.get("createdAt").asText());
  }

  @Test
  void getMyRouteById_repeatedOpen_doesNotChangeUpdatedAt() throws Exception {
    User user = createUser("routes-open-stamp@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("Stamp check"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult firstOpen =
        mockMvc
            .perform(get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
            .andExpect(status().isOk())
            .andReturn();
    String updatedAtFirst =
        objectMapper
            .readTree(firstOpen.getResponse().getContentAsString())
            .get("updatedAt")
            .asText();

    Thread.sleep(50);

    MvcResult secondOpen =
        mockMvc
            .perform(get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
            .andExpect(status().isOk())
            .andReturn();
    String updatedAtSecond =
        objectMapper
            .readTree(secondOpen.getResponse().getContentAsString())
            .get("updatedAt")
            .asText();

    assertThat(updatedAtSecond).isEqualTo(updatedAtFirst);
  }

  @Test
  void getRouteOfAnotherUser_returns404() throws Exception {
    User owner = createUser("routes-owner-2@example.com", "Secret123");
    User intruder = createUser("routes-intruder@example.com", "Secret123");
    String ownerAccess = login(owner.getEmail(), "Secret123");
    String intruderAccess = login(intruder.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(ownerAccess))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("Private route"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(intruderAccess)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void deleteMyRoute_hidesRouteFromList() throws Exception {
    User user = createUser("routes-delete@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("Delete me"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(delete("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/routes/my").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void updateMyRoute_replacesTitleAndPoints() throws Exception {
    User user = createUser("routes-update@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("Initial title"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    Map<String, Object> updated = new HashMap<>(routePayload("Updated title"));
    updated.put("routeComment", "edited");
    mockMvc
        .perform(
            put("/api/v1/routes/my/" + routeId)
                .header("Authorization", bearer(access))
                .contentType(jsonMediaType())
                .content(toJson(updated)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(routeId))
        .andExpect(jsonPath("$.title").value("Updated title"))
        .andExpect(jsonPath("$.routeComment").value("edited"))
        .andExpect(jsonPath("$.points.length()").value(2));

    mockMvc
        .perform(get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated title"))
        .andExpect(jsonPath("$.points.length()").value(2));
  }

  @Test
  void updateMyRoute_forbidsAccessToAnotherUserRoute() throws Exception {
    User owner = createUser("routes-update-owner@example.com", "Secret123");
    User intruder = createUser("routes-update-intruder@example.com", "Secret123");
    String ownerAccess = login(owner.getEmail(), "Secret123");
    String intruderAccess = login(intruder.getEmail(), "Secret123");

    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(ownerAccess))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("Owner route"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            put("/api/v1/routes/my/" + routeId)
                .header("Authorization", bearer(intruderAccess))
                .contentType(jsonMediaType())
                .content(toJson(routePayload("Hacked title"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void routesWithoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/routes/my"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void saveRouteWithOperations_roundTrip() throws Exception {
    User user = createUser("routes-ops-owner@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    String body = toJson(routePayloadWithBorderAndCustoms("Kyiv -> EU"));
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.points.length()").value(4))
            .andExpect(jsonPath("$.points[0].operations[0]").value("LOADING"))
            .andExpect(jsonPath("$.points[1].operations[0]").value("EXPORT_CUSTOMS"))
            .andExpect(jsonPath("$.points[3].operations.length()").value(2))
            .andExpect(jsonPath("$.points[3].operations[0]").value("IMPORT_CUSTOMS"))
            .andExpect(jsonPath("$.points[3].operations[1]").value("UNLOADING"))
            .andReturn();

    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.points.length()").value(4))
        .andExpect(jsonPath("$.points[0].operations[0]").value("LOADING"))
        .andExpect(jsonPath("$.points[3].operations[1]").value("UNLOADING"));
  }

  @Test
  void saveRouteWithCustomsButNoBorder_returns400() throws Exception {
    User user = createUser("routes-ops-bad@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    String body = toJson(routePayloadCustomsWithoutBorder("Bad route"));
    mockMvc
        .perform(
            post("/api/v1/routes")
                .header("Authorization", bearer(access))
                .contentType(jsonMediaType())
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROUTE_OPERATIONS_CUSTOMS_WITHOUT_BORDER"));
  }

  @Test
  void saveRouteWithThreeOpsOnSinglePoint_succeeds() throws Exception {
    User user = createUser("routes-ops-three@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");

    String body = toJson(routePayloadWithThreeOpsOnExportPoint("Kyiv -> Border"));
    mockMvc
        .perform(
            post("/api/v1/routes")
                .header("Authorization", bearer(access))
                .contentType(jsonMediaType())
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.points[0].operations.length()").value(3));
  }

  @Test
  void putMyRoute_afterRouteRequest_returns409() throws Exception {
    User user = createUser("routes-lock@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");
    String body = toJson(routePayload("Lock me"));
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    Map<String, Object> rq = new HashMap<>();
    rq.put("routeId", routeId);
    rq.put("preferredStartDate", "");
    rq.put("comment", "");
    rq.put("cargo", null);
    mockMvc
        .perform(
            post("/api/v1/route-requests")
                .header("Authorization", bearer(access))
                .contentType(jsonMediaType())
                .content(toJson(rq)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            put("/api/v1/routes/my/" + routeId)
                .header("Authorization", bearer(access))
                .contentType(jsonMediaType())
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROUTE_LOCKED_BY_REQUEST"));
  }

  @Test
  void duplicateMyRoute_returns201WithNewId() throws Exception {
    User user = createUser("routes-dup@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");
    String body = toJson(routePayload("Original"));
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult dupResult =
        mockMvc
            .perform(
                post("/api/v1/routes/my/" + routeId + "/duplicate")
                    .header("Authorization", bearer(access)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Original (копія)"))
            .andReturn();
    String newId =
        objectMapper.readTree(dupResult.getResponse().getContentAsString()).get("id").asText();
    assertThat(newId).isNotEqualTo(routeId);
    mockMvc
        .perform(get("/api/v1/routes/my/" + newId).header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockedByRequest").value(false));
  }

  @Test
  void getMyRoutes_viewDeletedAndRestore() throws Exception {
    User user = createUser("routes-view-del@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");
    MvcResult saveResult =
        mockMvc
            .perform(
                post("/api/v1/routes")
                    .header("Authorization", bearer(access))
                    .contentType(jsonMediaType())
                    .content(toJson(routePayload("To delete"))))
            .andExpect(status().isCreated())
            .andReturn();
    String routeId =
        objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(delete("/api/v1/routes/my/" + routeId).header("Authorization", bearer(access)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/routes/my").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(get("/api/v1/routes/my?view=deleted").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(routeId));

    mockMvc
        .perform(
            post("/api/v1/routes/my/" + routeId + "/restore")
                .header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(routeId));

    mockMvc
        .perform(
            post("/api/v1/routes/my/" + routeId + "/restore")
                .header("Authorization", bearer(access)))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/routes/my").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void getMyRoutes_invalidView_returns400() throws Exception {
    User user = createUser("routes-bad-view@example.com", "Secret123");
    String access = login(user.getEmail(), "Secret123");
    mockMvc
        .perform(get("/api/v1/routes/my?view=trash").header("Authorization", bearer(access)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_VIEW"));
  }

  private User createUser(String email, String password) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.USER);
    user.setEmailVerified(true);
    user.setActive(true);
    return userRepository.save(user);
  }

  private String login(String email, String password) throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonMediaType())
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
        "phase1",
        "points",
        List.of(startPoint, finishPoint),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  private Map<String, Object> routePayloadWithBorderAndCustoms(String title) {
    Map<String, Object> start =
        pointWithOps(1, "START", "Kyiv", 50.4501, 30.5234, "UA", false, 120.0, List.of("LOADING"));
    Map<String, Object> exportStop =
        pointWithOps(
            2,
            "STOP",
            "Lviv warehouse",
            49.8397,
            24.0297,
            "UA",
            false,
            80.0,
            List.of("EXPORT_CUSTOMS"));
    Map<String, Object> border =
        pointWithOps(3, "BORDER", "Krakovets", 49.9425, 23.1745, "UA", true, 350.0, List.of());
    Map<String, Object> finish =
        pointWithOps(
            4,
            "FINISH",
            "Warsaw",
            52.2297,
            21.0122,
            "PL",
            false,
            null,
            List.of("IMPORT_CUSTOMS", "UNLOADING"));

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
        "with-customs",
        "points",
        List.of(start, exportStop, border, finish),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  private Map<String, Object> routePayloadCustomsWithoutBorder(String title) {
    Map<String, Object> start =
        pointWithOps(1, "START", "Kyiv", 50.4501, 30.5234, "UA", false, 100.0, List.of("LOADING"));
    Map<String, Object> bogus =
        pointWithOps(
            2, "STOP", "Phantom customs", 49.0, 24.0, "UA", false, 50.0, List.of("EXPORT_CUSTOMS"));
    Map<String, Object> finish =
        pointWithOps(
            3, "FINISH", "Warsaw", 52.2297, 21.0122, "PL", false, null, List.of("UNLOADING"));

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
        500.0,
        "durationMin",
        500,
        "routeComment",
        "bad-customs",
        "points",
        List.of(start, bogus, finish),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  private Map<String, Object> routePayloadWithThreeOpsOnExportPoint(String title) {
    Map<String, Object> start =
        pointWithOps(
            1,
            "START",
            "Ternopil",
            49.5535,
            25.5948,
            "UA",
            false,
            60.0,
            List.of("LOADING", "EXPORT_CUSTOMS", "UNLOADING"));
    Map<String, Object> border =
        pointWithOps(2, "BORDER", "Krakovets", 49.9425, 23.1745, "UA", true, 100.0, List.of());
    Map<String, Object> finish =
        pointWithOps(
            3,
            "FINISH",
            "Przemysl",
            49.7833,
            22.7667,
            "PL",
            false,
            null,
            List.of("IMPORT_CUSTOMS", "UNLOADING"));

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
        180.0,
        "durationMin",
        220,
        "routeComment",
        "three-ops",
        "points",
        List.of(start, border, finish),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  private Map<String, Object> pointWithOps(
      int order,
      String type,
      String address,
      double lat,
      double lng,
      String country,
      boolean isBorder,
      Double segmentDistanceKm,
      List<String> operations) {
    Map<String, Object> point = new HashMap<>();
    point.put("order", order);
    point.put("type", type);
    point.put("address", address);
    point.put("lat", lat);
    point.put("lng", lng);
    point.put("country", country);
    point.put("isBorder", isBorder);
    point.put("segmentDistanceKmToNext", segmentDistanceKm);
    point.put("operations", operations);
    return point;
  }

  @NonNull
  private String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private static MediaType jsonMediaType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }
}
