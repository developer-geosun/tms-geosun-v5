package com.geosun.tms.enterprise.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.ratelimit.RateLimitService;
import com.geosun.tms.auth.repository.UserRepository;
import com.geosun.tms.enterprise.domain.PlatformOperator;
import com.geosun.tms.enterprise.dto.CreateEnterpriseRequest;
import com.geosun.tms.enterprise.dto.PlatformLoginRequest;
import com.geosun.tms.enterprise.repository.PlatformOperatorRepository;
import com.geosun.tms.support.TestEnterprise;
import jakarta.mail.internet.MimeMessage;
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

/** Вхід оператора платформи, керування підприємствами і ізоляція входу співробітників. */
@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformApiIntegrationTest {

  private static final String OPERATOR_EMAIL = "operator-it@example.com";
  private static final String OPERATOR_PASSWORD = "Operator1";
  private static final String PASSWORD = "Secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private PlatformOperatorRepository operatorRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RateLimitService rateLimitService;

  @MockBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    rateLimitService.resetForTests();
    when(javaMailSender.createMimeMessage())
        .thenReturn(new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
    doNothing().when(javaMailSender).send(anyMailMessage());

    PlatformOperator operator = new PlatformOperator();
    operator.setEmail(OPERATOR_EMAIL);
    operator.setPasswordHash(passwordEncoder.encode(OPERATOR_PASSWORD));
    operator.setActive(true);
    operatorRepository.save(operator);
  }

  @Test
  void operatorLogin_returnsTokens_andWrongPasswordIs401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/platform/auth/login")
                .contentType(jsonType())
                .content(toJson(new PlatformLoginRequest(OPERATOR_EMAIL, OPERATOR_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.operator.email").value(OPERATOR_EMAIL));

    mockMvc
        .perform(
            post("/api/v1/platform/auth/login")
                .contentType(jsonType())
                .content(toJson(new PlatformLoginRequest(OPERATOR_EMAIL, "WrongPass99"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void operatorRefresh_rotates_logoutRevokes_andReuseIsRejected() throws Exception {
    JsonNode first = operatorLogin();
    String access = first.get("accessToken").asText();
    String refresh = first.get("refreshToken").asText();

    MvcResult rotated =
        mockMvc
            .perform(
                post("/api/v1/platform/auth/refresh")
                    .contentType(jsonType())
                    .content(toJson(new RefreshRequest(refresh))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode second = objectMapper.readTree(body(rotated));
    assertThat(second.get("refreshToken").asText()).isNotEqualTo(refresh);

    // Сесія прив'язана до refresh-токена: після ротації старий access більше не діє
    mockMvc
        .perform(get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + access))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SESSION"));
    String secondAccess = second.get("accessToken").asText();
    mockMvc
        .perform(
            get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + secondAccess))
        .andExpect(status().isOk());

    // Повторне використання відкликаного refresh закриває всі сесії оператора
    mockMvc
        .perform(
            post("/api/v1/platform/auth/refresh")
                .contentType(jsonType())
                .content(toJson(new RefreshRequest(refresh))))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + secondAccess))
        .andExpect(status().isUnauthorized());

    String freshAccess = operatorLogin().get("accessToken").asText();
    mockMvc
        .perform(
            post("/api/v1/platform/auth/logout").header("Authorization", "Bearer " + freshAccess))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    mockMvc
        .perform(
            get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + freshAccess))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_SESSION"));
  }

  @Test
  void platformApi_requiresOperatorToken() throws Exception {
    mockMvc.perform(get("/api/v1/platform/enterprises")).andExpect(status().isUnauthorized());

    saveUser(TestEnterprise.ID, "plain-it@example.com", Role.ADMIN);
    String employeeAccess = employeeLogin(TestEnterprise.CODE, "plain-it@example.com");
    mockMvc
        .perform(
            get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + employeeAccess))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLATFORM_SCOPE_REQUIRED"));

    String operatorAccess = operatorLogin().get("accessToken").asText();
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + operatorAccess))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ENTERPRISE_SCOPE_REQUIRED"));
  }

  @Test
  void createEnterprise_firstAdminCanLogin_andSameEmailWorksInTwoEnterprises() throws Exception {
    String access = operatorLogin().get("accessToken").asText();

    createEnterprise(access, "Acme", "Acme-One", "boss@example.com")
        .andExpect(status().isCreated());
    createEnterprise(access, "Beta", "beta-two", "boss@example.com")
        .andExpect(status().isCreated());

    MvcResult login =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonType())
                    .content(toJson(new LoginRequest("ACME-one", "boss@example.com", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.enterpriseCode").value("acme-one"))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andReturn();
    assertThat(objectMapper.readTree(body(login)).get("accessToken").asText()).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonType())
                .content(toJson(new LoginRequest("beta-two", "boss@example.com", PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.enterpriseCode").value("beta-two"));

    mockMvc
        .perform(get("/api/v1/platform/enterprises").header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void createEnterprise_rejectsDuplicateReservedAndOperatorEmail() throws Exception {
    String access = operatorLogin().get("accessToken").asText();
    createEnterprise(access, "Acme", "acme", "a@example.com").andExpect(status().isCreated());

    createEnterprise(access, "Acme 2", "ACME", "b@example.com")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENTERPRISE_CODE_EXISTS"));
    createEnterprise(access, "Reserved", "platform", "c@example.com")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ENTERPRISE_CODE"));
    createEnterprise(access, "Collision", "collision", OPERATOR_EMAIL)
        .andExpect(status().isConflict());
  }

  @Test
  void suspend_blocksLogin_andExistingToken_activateRestores() throws Exception {
    String access = operatorLogin().get("accessToken").asText();
    MvcResult created =
        createEnterprise(access, "Gamma", "gamma", "admin@gamma.example.com")
            .andExpect(status().isCreated())
            .andReturn();
    String enterpriseId = objectMapper.readTree(body(created)).get("id").asText();
    String employeeAccess = employeeLogin("gamma", "admin@gamma.example.com");

    mockMvc
        .perform(
            post("/api/v1/platform/enterprises/" + enterpriseId + "/suspend")
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonType())
                .content(toJson(new LoginRequest("gamma", "admin@gamma.example.com", PASSWORD))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ENTERPRISE_SUSPENDED"));
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonType())
                .content(
                    toJson(new LoginRequest("gamma", "admin@gamma.example.com", "WrongPass99"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + employeeAccess))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ENTERPRISE_SUSPENDED"));

    mockMvc
        .perform(
            post("/api/v1/platform/enterprises/" + enterpriseId + "/activate")
                .header("Authorization", "Bearer " + access))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + employeeAccess))
        .andExpect(status().isOk());
  }

  @Test
  void employeeLogin_wrongEnterpriseOrUnknownCode_isInvalidCredentials() throws Exception {
    saveUser(TestEnterprise.ID, "only-geosun@example.com", Role.USER);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonType())
                .content(toJson(new LoginRequest("no-such", "only-geosun@example.com", PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

    String access = operatorLogin().get("accessToken").asText();
    createEnterprise(access, "Delta", "delta", "admin@delta.example.com")
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(jsonType())
                .content(toJson(new LoginRequest("delta", "only-geosun@example.com", PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void register_unknownOrSuspendedEnterprise_isUnavailable_andOperatorEmailConflicts()
      throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonType())
                .content(toJson(new RegisterRequest("ghost", "new-user@example.com", PASSWORD))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ENTERPRISE_UNAVAILABLE"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonType())
                .content(
                    toJson(new RegisterRequest(TestEnterprise.CODE, OPERATOR_EMAIL, PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(jsonType())
                .content(
                    toJson(
                        new RegisterRequest(
                            TestEnterprise.CODE, "new-user@example.com", PASSWORD))))
        .andExpect(status().isCreated());
    User created =
        userRepository
            .findByEnterpriseIdAndEmailAndDeletedFalse(TestEnterprise.ID, "new-user@example.com")
            .orElseThrow();
    assertThat(created.getEnterpriseId()).isEqualTo(TestEnterprise.ID);
  }

  @Test
  void adminUsers_areScopedToCallerEnterprise() throws Exception {
    String access = operatorLogin().get("accessToken").asText();
    createEnterprise(access, "Eps", "eps", "admin@eps.example.com").andExpect(status().isCreated());
    User foreign = saveUser(TestEnterprise.ID, "foreign-user@example.com", Role.USER);
    String epsAdmin = employeeLogin("eps", "admin@eps.example.com");

    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + epsAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].email").value("admin@eps.example.com"));

    mockMvc
        .perform(
            get("/api/v1/admin/users/" + foreign.getId())
                .header("Authorization", "Bearer " + epsAdmin))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/v1/admin/users/" + foreign.getId() + "/restore")
                .header("Authorization", "Bearer " + epsAdmin))
        .andExpect(status().isNotFound());
  }

  private org.springframework.test.web.servlet.ResultActions createEnterprise(
      String access, String name, String code, String adminEmail) throws Exception {
    return mockMvc.perform(
        post("/api/v1/platform/enterprises")
            .header("Authorization", "Bearer " + access)
            .contentType(jsonType())
            .content(toJson(new CreateEnterpriseRequest(name, code, adminEmail, PASSWORD))));
  }

  private JsonNode operatorLogin() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/platform/auth/login")
                    .contentType(jsonType())
                    .content(toJson(new PlatformLoginRequest(OPERATOR_EMAIL, OPERATOR_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(body(result));
  }

  private String employeeLogin(String enterpriseCode, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(jsonType())
                    .content(toJson(new LoginRequest(enterpriseCode, email, PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(body(result)).get("accessToken").asText();
  }

  private User saveUser(String enterpriseId, String email, Role role) {
    User user = new User();
    user.setEnterpriseId(enterpriseId);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(PASSWORD));
    user.setRole(role);
    user.setEmailVerified(true);
    user.setActive(true);
    return userRepository.save(user);
  }

  @NonNull
  private MediaType jsonType() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  @NonNull
  private static String body(@NonNull MvcResult result) throws Exception {
    return Objects.requireNonNull(result.getResponse().getContentAsString());
  }

  /** Mockito any() не анотований @NonNull — обгортаємо для null-analysis. */
  @SuppressWarnings("null")
  @NonNull
  private static MimeMessage anyMailMessage() {
    return any(MimeMessage.class);
  }
}
