package com.geosun.tms.reference.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import com.geosun.tms.reference.domain.VehicleType;
import com.geosun.tms.reference.dto.request.CreateDriverRequest;
import com.geosun.tms.reference.dto.request.CreateVehicleCombinationRequest;
import com.geosun.tms.reference.dto.request.CreateVehicleRequest;
import com.geosun.tms.reference.dto.request.LinkDriverUserRequest;
import com.geosun.tms.support.TestEnterprise;
import java.time.LocalDate;
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
class AdminDriverAndCombinationIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @MockBean private JavaMailSender javaMailSender;

  @Test
  void user_forbiddenOnDrivers() throws Exception {
    User user = saveUser("user-drv@example.com", "Secret123", Role.USER);
    String token = login(Objects.requireNonNull(user.getEmail()), "Secret123");
    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DRIVERS_BASE).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void manager_driverCrudDocumentsAndLinkUser() throws Exception {
    User manager = saveUser("manager-drv@example.com", "Secret123", Role.MANAGER);
    User linkable = saveUser("driver-link@example.com", "Secret123", Role.USER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    CreateDriverRequest create =
        new CreateDriverRequest(
            "Іваненко",
            "Іван",
            "Іванович",
            "+380501112233",
            "AAA123456",
            "CE",
            LocalDate.now().plusYears(2),
            "note");

    MvcResult created =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_DRIVERS_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(json())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.licenseNumber").value("AAA123456"))
            .andExpect(jsonPath("$.documentCompliance").value("PROBLEM"))
            .andReturn();
    String driverId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DRIVERS_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(toJson(create)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LICENSE_ALREADY_EXISTS"));

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/linkable-users")
                .param("email", "driver-link@example.com")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(linkable.getId()));

    mockMvc
        .perform(
            put(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId + "/user")
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(
                    toJson(new LinkDriverUserRequest(Objects.requireNonNull(linkable.getId())))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(linkable.getId()));

    MockMultipartFile file =
        new MockMultipartFile("file", "passport.jpg", "image/jpeg", jpegBytes());
    mockMvc
        .perform(
            multipart(
                    ReferenceApiPaths.ADMIN_DRIVERS_BASE
                        + "/"
                        + driverId
                        + "/documents/passport/front")
                .file(file)
                .param("validFrom", LocalDate.now().minusDays(1).toString())
                .param("validTo", LocalDate.now().plusYears(5).toString())
                .header("Authorization", "Bearer " + token)
                .with(
                    request -> {
                      request.setMethod("POST");
                      return request;
                    }))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId + "/documents")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documents.length()").value(4));

    mockMvc
        .perform(
            delete(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_DRIVERS_BASE + "/" + driverId + "/restore")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(false));
  }

  @Test
  void manager_vehicleCombinationCrud() throws Exception {
    User manager = saveUser("manager-comb@example.com", "Secret123", Role.MANAGER);
    String token = login(Objects.requireNonNull(manager.getEmail()), "Secret123");

    String tractorId =
        createVehicle(token, "BB1111CC", "WVWZZZ1JZYW111111", VehicleType.SEMI_TRACTOR);
    String trailerId =
        createVehicle(token, "BB2222DD", "WVWZZZ1JZYW222222", VehicleType.SEMI_TRAILER);

    CreateVehicleCombinationRequest create =
        new CreateVehicleCombinationRequest("Склад 1", tractorId, trailerId);
    MvcResult created =
        mockMvc
            .perform(
                post(ReferenceApiPaths.ADMIN_VEHICLE_COMBINATIONS_BASE)
                    .header("Authorization", "Bearer " + token)
                    .contentType(json())
                    .content(toJson(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tractorId").value(tractorId))
            .andExpect(jsonPath("$.trailerId").value(trailerId))
            .andReturn();
    String id =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post(ReferenceApiPaths.ADMIN_VEHICLE_COMBINATIONS_BASE)
                .header("Authorization", "Bearer " + token)
                .contentType(json())
                .content(toJson(create)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMBINATION_PAIR_EXISTS"));

    mockMvc
        .perform(
            delete(ReferenceApiPaths.ADMIN_VEHICLE_COMBINATIONS_BASE + "/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
  }

  private String createVehicle(String token, String plate, String vin, VehicleType type)
      throws Exception {
    CreateVehicleRequest req =
        new CreateVehicleRequest(
            plate, vin, "Volvo", "FH", (short) 2021, "Owner", "АА", plate, type, false);
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

  private static byte[] jpegBytes() {
    return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
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
