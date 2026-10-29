package com.geosun.tms.routes.integration;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
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
import com.geosun.tms.routes.service.PolylineDecoder;
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

@SpringBootTest(
    classes = TmsGeosunBackendJavaApplication.class,
    properties = {"app.country-breakdown.provider=geojson"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RouteRequestGeoJsonCountryBreakdownIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void geoJsonProviderCalculatesTransitCountries() throws Exception {
    User user = createUser("rq-geojson-user@example.com", "Secret123", Role.USER);
    User admin = createUser("rq-geojson-admin@example.com", "Secret123", Role.ADMIN);
    String userAccess = login(user.getEmail(), "Secret123");
    String adminAccess = login(admin.getEmail(), "Secret123");

    String routeId = createRoute(userAccess, "GeoJSON transit route");

    MvcResult requestResult =
        mockMvc
            .perform(
                post("/api/v1/route-requests")
                    .header("Authorization", bearer(userAccess))
                    .contentType(jsonContentType())
                    .content(toJson(Map.of("routeId", routeId))))
            .andExpect(status().isCreated())
            .andReturn();

    long requestId =
        objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/country-breakdown")
                .header("Authorization", bearer(adminAccess)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.countryDistances.length()")
                .value(Objects.requireNonNull(greaterThanOrEqualTo(4))))
        .andExpect(jsonPath("$.countryDistances[0].countryCode").value("UA"))
        .andExpect(
            jsonPath(
                "$.countryDistances[*].countryCode",
                Objects.requireNonNull(hasItems("UA", "PL", "DE", "FR"))));
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
    List<PolylineDecoder.LatLng> path =
        List.of(
            new PolylineDecoder.LatLng(49.8419, 24.0315), // Lviv, UA
            new PolylineDecoder.LatLng(50.0647, 19.9450), // Krakow, PL
            new PolylineDecoder.LatLng(52.5200, 13.4050), // Berlin, DE
            new PolylineDecoder.LatLng(48.8566, 2.3522) // Paris, FR
            );
    String routePolyline = PolylineDecoder.encode(path);

    Map<String, Object> startPoint = new HashMap<>();
    startPoint.put("order", 1);
    startPoint.put("type", "START");
    startPoint.put("address", "Lviv");
    startPoint.put("lat", 49.8419);
    startPoint.put("lng", 24.0315);
    startPoint.put("country", "UA");
    startPoint.put("isBorder", false);
    startPoint.put("segmentDistanceKmToNext", 1500.0);
    startPoint.put("operations", List.of("LOADING"));

    Map<String, Object> finishPoint = new HashMap<>();
    finishPoint.put("order", 2);
    finishPoint.put("type", "FINISH");
    finishPoint.put("address", "Paris");
    finishPoint.put("lat", 48.8566);
    finishPoint.put("lng", 2.3522);
    finishPoint.put("country", "FR");
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
        routePolyline,
        "distanceKm",
        1560.0,
        "durationMin",
        1080,
        "routeComment",
        "geojson",
        "points",
        List.of(startPoint, finishPoint));
  }

  @NonNull
  private String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }
}
