package com.geosun.tms.freight.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.reference.domain.Currency;
import com.geosun.tms.reference.domain.CurrencyNbuRate;
import com.geosun.tms.reference.repository.CurrencyNbuRateRepository;
import com.geosun.tms.reference.repository.CurrencyRepository;
import com.geosun.tms.support.TestEnterprise;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
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
class FreightNumericPricingApiIntegrationTest {
  private static final LocalDate RATE_DATE = LocalDate.of(2026, 5, 20);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private CurrencyNbuRateRepository nbuRateRepository;

  @BeforeEach
  void seedNbuRates() {
    nbuRateRepository.deleteAll();
    seedRate("UAH", BigDecimal.ONE, 1);
    seedRate("EUR", new BigDecimal("40"), 1);
    seedRate("USD", new BigDecimal("38"), 1);
  }

  @Test
  void adminCanRunNbuCostPreviewAndCreateQuoteFromCalculation() throws Exception {
    User user = createUser("nbu-user@example.com", "Secret123", Role.USER);
    User admin = createUser("nbu-admin@example.com", "Secret123", Role.ADMIN);
    String userAccess = login(user.getEmail(), "Secret123");
    String adminAccess = login(admin.getEmail(), "Secret123");

    String routeId = createRoute(userAccess, "NBU pricing route");
    long requestId = createRouteRequest(userAccess, routeId);

    String tollSetId = createTollSet(adminAccess);
    createTollRule(adminAccess, tollSetId, "PL", "EUR_PER_KM", "0.12");
    String scenarioId = createNumericScenario(adminAccess, tollSetId);

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/country-breakdown")
                .header("Authorization", bearer(adminAccess))
                .contentType(jsonContentType())
                .content(toJson(Map.of("scenarioId", scenarioId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.countryDistances.length()").value(2));

    MvcResult previewResult =
        mockMvc
            .perform(
                post("/api/v1/admin/route-requests/" + requestId + "/cost-preview")
                    .header("Authorization", bearer(adminAccess))
                    .contentType(jsonContentType())
                    .content(
                        toJson(
                            Map.of(
                                "scenarioId",
                                scenarioId,
                                "calculationDate",
                                RATE_DATE.toString()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.proposalCurrency").value("EUR"))
            .andExpect(jsonPath("$.calculationSummary").isNotEmpty())
            .andExpect(jsonPath("$.totalProposalAmount").isNumber())
            .andReturn();

    String calculationId =
        objectMapper
            .readTree(previewResult.getResponse().getContentAsString())
            .get("calculationId")
            .asText();

    mockMvc
        .perform(
            post("/api/v1/admin/route-requests/" + requestId + "/quotes")
                .header("Authorization", bearer(adminAccess))
                .header("Idempotency-Key", "nbu-quote-create-1")
                .contentType(jsonContentType())
                .content(toJson(Map.of("fromCostCalculationId", calculationId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.freightCostCalculationId").value(calculationId))
        .andExpect(jsonPath("$.status").value("DRAFT"));

    mockMvc
        .perform(
            get("/api/v1/admin/route-requests/" + requestId + "/cost-calculations")
                .header("Authorization", bearer(adminAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(calculationId));

    mockMvc
        .perform(
            delete(
                    "/api/v1/admin/route-requests/"
                        + requestId
                        + "/cost-calculations/"
                        + calculationId)
                .header("Authorization", bearer(adminAccess)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/admin/route-requests/" + requestId + "/cost-calculations")
                .header("Authorization", bearer(adminAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private String createTollSet(String adminAccess) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/toll-tariff-sets")
                    .header("Authorization", bearer(adminAccess))
                    .contentType(jsonContentType())
                    .content(toJson(Map.of("name", "Test EU", "description", "integration"))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void createTollRule(
      String adminAccess, String setId, String country, String tollType, String rate)
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/toll-tariff-sets/" + setId + "/country-toll-rules")
                .header("Authorization", bearer(adminAccess))
                .contentType(jsonContentType())
                .content(
                    toJson(
                        Map.of(
                            "countryCode",
                            country,
                            "tollType",
                            tollType,
                            "rate",
                            new BigDecimal(rate),
                            "isActive",
                            true))))
        .andExpect(status().isCreated());
  }

  private String createNumericScenario(String adminAccess, String tollSetId) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Integration NBU");
    body.put("description", "test");
    body.put("isActive", true);
    body.put("fuelConsumptionEmptyLPer100km", 35);
    body.put("fuelConsumptionLoadedNonWinterLPer100km", 38);
    body.put("fuelConsumptionLoadedWinterLPer100km", 40);
    body.put("seasonMode", "AUTO");
    body.put("fuelPricePerLiter", 81.5);
    body.put("driverSalaryPercentOfFreight", 15);
    body.put("perDiemAmountPerDay", 10);
    body.put("perDiemRouteDivisorKm", 600);
    body.put("perDiemFixedExtraDays", 2);
    body.put("marginType", "PERCENT_OF_COST_BEFORE_MARGIN");
    body.put("marginPercent", 30);
    body.put("proposalCurrency", "EUR");
    body.put("tollTariffSetId", tollSetId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/admin/freight-numeric-scenarios")
                    .header("Authorization", bearer(adminAccess))
                    .contentType(jsonContentType())
                    .content(toJson(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void seedRate(@NonNull String code, BigDecimal rate, int units) {
    Currency currency =
        currencyRepository
            .findById(code)
            .orElseGet(
                () -> {
                  Currency created = new Currency();
                  created.setCode(code);
                  created.setNumericCode(978);
                  created.setNameUk(code);
                  created.setNameEn(code);
                  created.setNbuUnits(units);
                  created.setMinorUnits(2);
                  created.setActive(true);
                  return currencyRepository.save(created);
                });
    currency.setActive(true);
    currencyRepository.save(currency);

    CurrencyNbuRate row = new CurrencyNbuRate();
    row.setCurrencyCode(currency.getCode());
    row.setRateDate(RATE_DATE);
    row.setRate(rate);
    row.setNbuUnits(units);
    row.setRatePerUnit(rate.divide(BigDecimal.valueOf(units), 6, java.math.RoundingMode.HALF_UP));
    row.setFetchedAt(Instant.parse("2026-05-20T12:00:00Z"));
    nbuRateRepository.save(row);
  }

  private long createRouteRequest(String access, String routeId) throws Exception {
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
                                "2026-06-15",
                                "comment",
                                "NBU",
                                "cargo",
                                Map.of("type", "food", "weightKg", 1000.0, "volumeM3", 10.0)))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(requestResult.getResponse().getContentAsString())
        .get("id")
        .asLong();
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
    return objectMapper.readTree(saveResult.getResponse().getContentAsString()).get("id").asText();
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
    return objectMapper
        .readTree(loginResult.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
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
        "nbu",
        "points",
        List.of(startPoint, finishPoint),
        "hereRouteMeta",
        Map.of("provider", "HERE", "routeHandle", "r-handle", "apiVersion", "v8"));
  }

  private @NonNull String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  private @NonNull MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }
}
