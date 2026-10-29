package com.geosun.tms.trips.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.geosun.tms.reference.api.ReferenceApiPaths;
import com.geosun.tms.reference.domain.Currency;
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.dto.request.CreateDriverRequest;
import com.geosun.tms.reference.dto.request.CreateVehicleRequest;
import com.geosun.tms.reference.dto.request.LinkDriverUserRequest;
import com.geosun.tms.reference.repository.CurrencyRepository;
import com.geosun.tms.support.TestEnterprise;
import com.geosun.tms.trips.api.TripsApiPaths;
import com.geosun.tms.trips.domain.TripExpenseCategory;
import com.geosun.tms.trips.domain.TripStatus;
import com.geosun.tms.trips.dto.request.CreateTripRequest;
import com.geosun.tms.trips.dto.request.ReplaceTripExpenseLinesRequest;
import com.geosun.tms.trips.dto.request.ReplaceTripExpenseLinesRequest.TripExpenseLineInput;
import com.geosun.tms.trips.dto.request.ReviewTripExpenseReportRequest;
import com.geosun.tms.trips.dto.request.UpdateTripStatusRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TmsGeosunBackendJavaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripAndExpenseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private CurrencyRepository currencyRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void manager_createTripAssignPlanAndExpenseWorkflow() throws Exception {
    User manager = saveUser("manager-trip@example.com", "Secret123", Role.MANAGER);
    User driverUser = saveUser("driver-trip@example.com", "Secret123", Role.USER);
    String managerToken = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    String tractorId =
        createVehicle(managerToken, "CC1111EE", "WVWZZZ1JZYW333333", VehicleType.SEMI_TRACTOR);
    String trailerId =
        createVehicle(managerToken, "CC2222FF", "WVWZZZ1JZYW444444", VehicleType.SEMI_TRAILER);
    String driverId = createDriver(managerToken, "Петренко", "LIC-TRIP-1");

    mockMvc
        .perform(
            put(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId + "/user")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(
                    toJson(new LinkDriverUserRequest(Objects.requireNonNull(driverUser.getId())))))
        .andExpect(status().isOk());

    Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.DAYS);
    CreateTripRequest create =
        new CreateTripRequest(
            null,
            "Test trip",
            null,
            "Kyiv",
            "Lviv",
            start,
            end,
            driverId,
            null,
            tractorId,
            trailerId);

    MvcResult created =
        mockMvc
            .perform(
                post(TripsApiPaths.ADMIN_TRIPS_BASE)
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(json())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.expenseReportStatus").value("DRAFT"))
            .andReturn();
    String tripId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            patch(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(toJson(new UpdateTripStatusRequest(TripStatus.PLANNED))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PLANNED"));

    mockMvc
        .perform(
            patch(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(toJson(new UpdateTripStatusRequest(TripStatus.IN_PROGRESS))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    ensureUah();

    ReplaceTripExpenseLinesRequest lines =
        new ReplaceTripExpenseLinesRequest(
            List.of(
                new TripExpenseLineInput(
                    null,
                    TripExpenseCategory.FUEL,
                    new BigDecimal("1500.00"),
                    "UAH",
                    LocalDate.now(),
                    "Fuel")));

    mockMvc
        .perform(
            put(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/expense-report/lines")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(toJson(lines)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(1));

    mockMvc
        .perform(
            post(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/expense-report/submit")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));

    mockMvc
        .perform(
            post(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/expense-report/review")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(toJson(new ReviewTripExpenseReportRequest(true, "ok"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));

    String driverToken = login(Objects.requireNonNull(driverUser.getEmail()), "Secret123");
    mockMvc
        .perform(get(TripsApiPaths.MY_TRIPS_BASE).header("Authorization", "Bearer " + driverToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));
  }

  @Test
  void planned_usesLicenseDocumentExpiryWhenProfileFieldIsStale() throws Exception {
    User manager = saveUser("manager-license@example.com", "Secret123", Role.MANAGER);
    String managerToken = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    String tractorId =
        createVehicle(managerToken, "DD1111EE", "WVWZZZ1JZYW555555", VehicleType.SEMI_TRACTOR);
    String trailerId =
        createVehicle(managerToken, "DD2222FF", "WVWZZZ1JZYW666666", VehicleType.SEMI_TRAILER);
    String driverId =
        createDriver(managerToken, "Сидоренко", "LIC-STALE", LocalDate.now().minusDays(1));

    uploadDriverDocument(
        managerToken,
        driverId,
        "driver-license/front",
        LocalDate.now().minusYears(1),
        LocalDate.now().plusYears(2));
    uploadDriverDocument(
        managerToken,
        driverId,
        "driver-license/back",
        LocalDate.now().minusYears(1),
        LocalDate.now().plusYears(3));

    Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(2, ChronoUnit.DAYS);
    CreateTripRequest create =
        new CreateTripRequest(
            null,
            "License sync trip",
            null,
            "Kyiv",
            "Lviv",
            start,
            end,
            driverId,
            null,
            tractorId,
            trailerId);

    MvcResult created =
        mockMvc
            .perform(
                post(TripsApiPaths.ADMIN_TRIPS_BASE)
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(json())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andReturn();
    String tripId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            patch(TripsApiPaths.ADMIN_TRIPS_BASE + "/" + tripId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(json())
                .content(toJson(new UpdateTripStatusRequest(TripStatus.PLANNED))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PLANNED"));
  }

  private void ensureUah() {
    if (!currencyRepository.existsById("UAH")) {
      Currency uah = new Currency();
      uah.setCode("UAH");
      uah.setNumericCode(980);
      uah.setNameUk("Гривня");
      uah.setNameEn("Hryvnia");
      uah.setNbuUnits(1);
      uah.setMinorUnits(2);
      uah.setActive(true);
      uah.setDisplayOrder(0);
      currencyRepository.save(uah);
    }
  }

  private String createDriver(String token, String lastName, String license) throws Exception {
    return createDriver(token, lastName, license, LocalDate.now().plusYears(3));
  }

  private String createDriver(
      String token, String lastName, String license, LocalDate licenseExpiresOn) throws Exception {
    CreateDriverRequest req =
        new CreateDriverRequest(
            lastName, "Ім'я", null, "+380671112233", license, "CE", licenseExpiresOn, null);
    MvcResult result =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_DRIVERS_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(json())
                    .content(toJson(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private void uploadDriverDocument(
      String token, String driverId, String path, LocalDate validFrom, LocalDate validTo)
      throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "license.jpg", "image/jpeg", jpegBytes());
    mockMvc
        .perform(
            multipart(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId + "/documents/" + path)
                .file(file)
                .param("validFrom", validFrom.toString())
                .param("validTo", validTo.toString())
                .header("Authorization", "Bearer " + token)
                .with(
                    request -> {
                      request.setMethod("POST");
                      return request;
                    }))
        .andExpect(status().isCreated());
  }

  private static byte[] jpegBytes() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
  }

  private String createVehicle(String token, String plate, String vin, VehicleType type)
      throws Exception {
    CreateVehicleRequest req =
        new CreateVehicleRequest(
            plate, vin, "Scania", "R500", (short) 2022, "Owner", "ББ", plate, type, false);
    MvcResult result =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_VEHICLES_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(json())
                    .content(toJson(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @NonNull
  private static MediaType json() {
    return Objects.requireNonNull(MediaType.APPLICATION_JSON);
  }

  @NonNull
  private String toJson(@NonNull Object value) throws Exception {
    return Objects.requireNonNull(objectMapper.writeValueAsString(value));
  }

  private User saveUser(String email, String password, Role role) {
    User u = new User();
    u.setEnterpriseId(TestEnterprise.ID);
    u.setEmail(email);
    u.setPasswordHash(passwordEncoder.encode(password));
    u.setRole(role);
    u.setEmailVerified(true);
    u.setActive(true);
    return userRepository.save(u);
  }

  private String login(@NonNull String email, @NonNull String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(json())
                    .content(toJson(new LoginRequest(TestEnterprise.CODE, email, password))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode n = objectMapper.readTree(result.getResponse().getContentAsString());
    return n.get("accessToken").asText();
  }
}
