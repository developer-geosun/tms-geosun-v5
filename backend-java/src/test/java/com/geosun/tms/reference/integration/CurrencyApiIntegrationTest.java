package com.geosun.tms.reference.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geosun.tms.auth.TmsGeosunBackendJavaApplication;
import com.geosun.tms.auth.domain.user.Role;
import com.geosun.tms.auth.domain.user.User;
import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.dto.PlatformLoginRequest;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.reference.client.NbuApiClient;
import com.geosun.tms.reference.client.NbuRateRow;
import com.geosun.tms.reference.domain.Currency;
import com.geosun.tms.reference.repository.CurrencyRepository;
import com.geosun.tms.support.TestEnterprise;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurrencyApiIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private PlatformOperatorRepository operatorRepository;

  @MockBean private NbuApiClient nbuApiClient;

  @BeforeEach
  void seedCurrencies() {
    currencyRepository.deleteAll();
    saveCurrency("UAH", 980, true, 0);
    saveCurrency("USD", 840, true, 10);
    saveCurrency("EUR", 978, true, 20);
    saveCurrency("PLN", 985, false, null);
  }

  @Test
  void adminCanListCurrencies() throws Exception {
    User admin = createUser("curr-admin@example.com", Role.ADMIN);
    String access = login(admin.getEmail());

    mockMvc
        .perform(get("/api/v1/admin/currencies").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].code").value("UAH"));
  }

  @Test
  void adminCannotWriteCurrenciesOrSyncNbu() throws Exception {
    User admin = createUser("curr-admin-write@example.com", Role.ADMIN);
    String access = login(admin.getEmail());

    int patchStatus =
        mockMvc
            .perform(
                patch("/api/v1/admin/currencies/PLN")
                    .header("Authorization", bearer(access))
                    .contentType(jsonContentType())
                    .content(toJson(Map.of("isActive", true))))
            .andReturn()
            .getResponse()
            .getStatus();
    int syncStatus =
        mockMvc
            .perform(
                post("/api/v1/admin/currencies/nbu-rates/sync")
                    .header("Authorization", bearer(access)))
            .andReturn()
            .getResponse()
            .getStatus();

    assertThat(patchStatus).isNotEqualTo(200);
    assertThat(syncStatus).isNotEqualTo(200);
    Currency pln = Objects.requireNonNull(currencyRepository.findById("PLN").orElseThrow());
    assertThat(pln.isActive()).isFalse();
  }

  @Test
  void employeeCannotUsePlatformCurrencyEndpoints() throws Exception {
    User admin = createUser("curr-admin-platform@example.com", Role.ADMIN);
    String access = login(admin.getEmail());

    mockMvc
        .perform(
            patch("/api/v1/platform/currencies/PLN")
                .header("Authorization", bearer(access))
                .contentType(jsonContentType())
                .content(toJson(Map.of("isActive", true))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/platform/currencies/nbu-rates/sync")
                .header("Authorization", bearer(access)))
        .andExpect(status().isForbidden());
  }

  @Test
  void operatorCanListAndPatchCurrency() throws Exception {
    String access = operatorLogin();

    mockMvc
        .perform(get("/api/v1/platform/currencies").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));

    mockMvc
        .perform(
            patch("/api/v1/platform/currencies/PLN")
                .header("Authorization", bearer(access))
                .contentType(jsonContentType())
                .content(toJson(Map.of("isActive", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PLN"))
        .andExpect(jsonPath("$.isActive").value(true));
  }

  @Test
  void deactivationResetsDisplayOrder() throws Exception {
    String access = operatorLogin();

    mockMvc
        .perform(
            patch("/api/v1/platform/currencies/USD")
                .header("Authorization", bearer(access))
                .contentType(jsonContentType())
                .content(toJson(Map.of("isActive", false, "displayOrder", 10))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isActive").value(false))
        .andExpect(jsonPath("$.displayOrder").isEmpty());

    Currency stored = Objects.requireNonNull(currencyRepository.findById("USD").orElseThrow());
    assertThat(stored.getDisplayOrder()).isNull();
  }

  @Test
  void userCannotAccessAdminCurrencies() throws Exception {
    User user = createUser("curr-user@example.com", Role.USER);
    String access = login(user.getEmail());

    mockMvc
        .perform(get("/api/v1/admin/currencies").header("Authorization", bearer(access)))
        .andExpect(status().isForbidden());
  }

  @Test
  void operatorCanSync_andAdminCanReadNbuRates() throws Exception {
    LocalDate rateDate = LocalDate.of(2026, 5, 22);
    when(nbuApiClient.fetchRatesForDate(any()))
        .thenReturn(
            List.of(
                new NbuRateRow("USD", new BigDecimal("44.2341"), rateDate, "N"),
                new NbuRateRow("EUR", new BigDecimal("51.3027"), rateDate, null),
                new NbuRateRow("PLN", new BigDecimal("12.0753"), rateDate, null)));

    String operatorAccess = operatorLogin();
    mockMvc
        .perform(
            post("/api/v1/platform/currencies/nbu-rates/sync")
                .header("Authorization", bearer(operatorAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateDate").value("2026-05-22"))
        .andExpect(jsonPath("$.syncedCount").value(3));

    User admin = createUser("curr-nbu@example.com", Role.ADMIN);
    String access = login(admin.getEmail());
    mockMvc
        .perform(get("/api/v1/admin/currencies/nbu-rates").header("Authorization", bearer(access)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rateDate").value("2026-05-22"))
        .andExpect(jsonPath("$.rates.length()").value(3))
        .andExpect(jsonPath("$.rates[?(@.currencyCode=='UAH')].ratePerUnit").value(1.0));
  }

  private String operatorLogin() throws Exception {
    PlatformOperator operator = new PlatformOperator();
    operator.setEmail("curr-operator@example.com");
    operator.setPasswordHash(passwordEncoder.encode("Operator1"));
    operator.setActive(true);
    operatorRepository.save(operator);
    var result =
        mockMvc
            .perform(
                post("/api/v1/platform/auth/login")
                    .contentType(jsonContentType())
                    .content(
                        toJson(new PlatformLoginRequest("curr-operator@example.com", "Operator1"))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  private void saveCurrency(String code, int numericCode, boolean active, Integer order) {
    Currency currency = new Currency();
    currency.setCode(code);
    currency.setNumericCode(numericCode);
    currency.setNameUk(code);
    currency.setNameEn(code);
    currency.setNameRu(code);
    currency.setNbuUnits(1);
    currency.setMinorUnits(2);
    currency.setActive(active);
    currency.setDisplayOrder(order);
    currencyRepository.save(currency);
  }

  private User createUser(String email, Role role) {
    User user = new User();
    user.setEnterpriseId(TestEnterprise.ID);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode("Secret123"));
    user.setRole(role);
    user.setActive(true);
    user.setEmailVerified(true);
    return userRepository.save(user);
  }

  private String login(String email) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonContentType())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, "Secret123"))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  @NonNull
  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @NonNull
  private static MediaType jsonContentType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }
}
